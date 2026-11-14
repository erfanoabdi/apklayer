#!/usr/bin/env python3
"""Runs on the test VM in an instance where real Termux (com.termux, converted, bootstrapped) is installed: the environment of Termux's shell and its
children, after bionic_translation 0068 (native exec uses the guest environment). Types `export PROBE_X=...; . ~/envprobe.sh` into Termux's
terminal through the shell's test input (text into TerminalView's input connection, then Enter), the sourced script writes `env` of the
shell, its children (sh, /system/bin/sh) and grandchildren into ~/envprobe.txt in Termux's home; read back from the instance's app data.
Usage (instance environment, repo root): python3 tests/corpus/termux-env.py [out-dir]. Fails on a host, apklayer or backend variable in any view."""
import re
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402

PKG = 'com.termux'
VALUE = 'apklayer42'
out = Path(sys.argv[1]) if len(sys.argv) > 1 else instance.OUT
out.mkdir(parents=True, exist_ok=True)
info = subprocess.check_output(['bin/apklayer', 'info', PKG], env=instance.ENV, text=True)
data = Path(dict(line.split('\t', 1) for line in info.splitlines() if '\t' in line)['dataDir'])
assert '/%s/app-data/' % instance.INSTANCE in str(data), data
home = data / 'app/files/home'
assert (data / 'app/files/usr/bin/bash').exists(), 'Termux is not bootstrapped in ' + str(data)
probe, result = home / 'envprobe.sh', home / 'envprobe.txt'
result.unlink(missing_ok=True)
probe.write_text('''{
echo "== shell /proc/\\$\\$/environ (initial bytes)"; tr '\\0' '\\n' < /proc/$$/environ | sort
echo "== shell variables"; echo "HOME=$HOME"; echo "PATH=$PATH"; echo "TMPDIR=$TMPDIR"; echo "PREFIX=$PREFIX"; echo "PROBE_X=$PROBE_X"
echo "== child: env"; env | sort
echo "== child: sh -c env"; sh -c env | sort
echo "== child: /system/bin/sh -c env"; /system/bin/sh -c env | sort
echo "== grandchild: sh -c 'sh -c env'"; sh -c 'sh -c env' | sort
echo "== grandchild: /system/bin/sh -c '/system/bin/sh -c env'"; /system/bin/sh -c '/system/bin/sh -c env' | sort
echo "== grandchild: /system/bin/sh -c \\$PREFIX/bin/env"; /system/bin/sh -c "$PREFIX/bin/env" | sort
} > ~/envprobe.txt 2>&1; echo PROBE_DONE >> ~/envprobe.txt
''')

env = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=90000')
shell = instance.STATE / 'shell.log'
instance.assert_clean()  # a start of its own: the shell's log begins with this run (rotated here as the start rotates it, so no old frame counts)
if shell.exists(): shell.rename(shell.with_name('shell.log.1'))
process = None
try:
    with open(out / 'termux-env.log', 'w') as log:
        process = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=log, stderr=subprocess.STDOUT)
        subprocess.run(['python3', 'tests/corpus/first-frame.py', str(shell), '0', '60', '1', '2'], check=True)
        # text through Termux's input connection: its key path drops punctuation (key events of - = ; . / ~ type nothing, 2026-11-13)
        instance.inject(PKG, 'focus', 'text:export PROBE_X=%s; . ~/envprobe.sh' % VALUE, 'key:28')
        try: text = instance.wait_file(result, 'PROBE_DONE', 60)
        finally: instance.capture('termux-env', PKG)  # the terminal as typed into (also when the probe never ran)
finally:
    instance.stop()
    if process: process.wait()
    instance.assert_clean()
(out / 'termux-env.txt').write_text(text)
print(text)
# what must not reach Android processes: apklayer/backend controls, the host session, the JDK, host library paths and the host home
LEAK = re.compile(r'^(APKLAYER_|APKLY_|XDG_|BIONIC_|JAVA_|JDK_|_JAVA|LD_LIBRARY_PATH=|LD_PRELOAD=|WAYLAND_|DBUS_|PULSE_|PIPEWIRE_|MESA_|GST_|SSH_|LC_TERMINAL|INVOCATION_ID|LOGNAME=|USER=)'
                  r'|^[A-Z_]+=.*(/home/|/usr/lib/jvm)')
sections, name = {}, None
for line in text.splitlines():
    if line.startswith('== '): name = line[3:]; sections[name] = []
    elif name: sections[name].append(line)
# not exec-boundary leaks (reported, not failed): Termux derives TERMUX_APP__DATA_DIR from ApplicationInfo.dataDir, a host path here (Java identity
# gap, docs/PLAN.md); Termux's own LD_PRELOAD (its termux-exec library under /data) is the app's value, as on Android
KNOWN = re.compile(r'^TERMUX_APP__DATA_DIR=')
bad = ['%s: %s' % (n, line) for n, lines in sections.items() for line in lines
       if LEAK.search(line) and not KNOWN.search(line) and not line.startswith('LD_PRELOAD=/data/data/com.termux/')]
known = sorted({line for lines in sections.values() for line in lines if KNOWN.search(line) and LEAK.search(line)})
print('KNOWN GAP (Java ApplicationInfo.dataDir, not exec): ' + ', '.join(known) if known else 'no known gaps seen')
print('shell /proc/$$/environ lines: %d' % len(sections.get('shell /proc/$$/environ (initial bytes)', [])))
want = {'HOME': '/data/data/com.termux/files/home', 'PREFIX': '/data/data/com.termux/files/usr', 'TMPDIR': '/data/data/com.termux/files/usr/tmp',
        'PROBE_X': VALUE}
for n, lines in sections.items():
    if n.startswith(('child', 'grandchild')) or n == 'shell variables':
        values = dict(line.split('=', 1) for line in lines if '=' in line)
        bad += ['%s: %s=%r, want %r' % (n, k, values.get(k), v) for k, v in want.items() if values.get(k) != v]
        path = values.get('PATH', '')
        if not path.startswith('/data/data/com.termux/files/usr/bin'): bad.append('%s: PATH=%r' % (n, path))
assert len(sections) == 8, 'probe sections: %s' % list(sections)
assert not bad, 'environment leaks or wrong values:\n' + '\n'.join(bad)
print('PASS Termux shell, children and grandchildren see the Termux environment (%d views), no host/apklayer/backend variables' % len(sections))
