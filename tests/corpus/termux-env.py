#!/usr/bin/env python3
"""Runs on the test VM in an instance where real Termux (com.termux, converted, bootstrapped) is installed: the environment of Termux's shell and its
children, after bionic_translation 0068 (native exec uses the guest environment). Types `export PROBE_X=...; . ~/envprobe.sh` into Termux's
terminal through the shell's test input (key events: TerminalView reads keys, not an input connection), the sourced script writes `env` of the
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

# evdev codes (linux/input-event-codes.h) of the characters typed; shifted ones through combo:shift+
ROWS = {'1234567890-=': 2, 'qwertyuiop[]': 16, "asdfghjkl;'`": 30, '\\zxcvbnm,./': 43}
SHIFTED = {'1234567890-=': '!@#$%^&*()_+', '[]': '{}', ";'`": ':"~', '\\': '|', ',./': '<>?'}
CODES = {c: base + i for row, base in ROWS.items() for i, c in enumerate(row)}
CODES[' '] = 57


def keys(text):
    steps = []
    for c in text:
        if c in CODES: steps.append('key:%d' % CODES[c])
        elif c.isupper(): steps.append('combo:shift+%d' % CODES[c.lower()])
        else: steps.append('combo:shift+%d' % next(CODES[plain[shifted.index(c)]] for plain, shifted in SHIFTED.items() if c in shifted))
    return steps


env = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=90000')
shell = instance.STATE / 'shell.log'
instance.assert_clean()  # a start of its own: the shell's log begins with this run (rotated here as the start rotates it, so no old frame counts)
if shell.exists(): shell.rename(shell.with_name('shell.log.1'))
process = None
try:
    with open(out / 'termux-env.log', 'w') as log:
        process = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=log, stderr=subprocess.STDOUT)
        subprocess.run(['python3', 'tests/corpus/first-frame.py', str(shell), '0', '60', '1', '2'], check=True)
        instance.inject(PKG, 'focus', *keys('export PROBE_X=%s; . ~/envprobe.sh' % VALUE), 'key:28')
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
bad = ['%s: %s' % (n, line) for n, lines in sections.items() for line in lines if LEAK.search(line)]
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
