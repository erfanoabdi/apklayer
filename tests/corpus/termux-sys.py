#!/usr/bin/env python3
"""Runs on the test VM in an instance where real Termux (com.termux, bootstrapped) is installed: what a Termux shell sees of Android's system side
(owner names of the app uid, /system/bin/sh, getprop) and punctuation typed as hardware keys (evdev, the path of a real keyboard).
Usage (instance environment, repo root): python3 tests/corpus/termux-sys.py [out-dir]."""
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402

PKG = 'com.termux'
out = Path(sys.argv[1]) if len(sys.argv) > 1 else instance.OUT
out.mkdir(parents=True, exist_ok=True)
info = subprocess.check_output(['bin/apklayer', 'info', PKG], env=instance.ENV, text=True)
data = Path(dict(line.split('\t', 1) for line in info.splitlines() if '\t' in line)['dataDir'])
home = data / 'app/files/home'
assert (data / 'app/files/usr/bin/bash').exists(), 'Termux is not bootstrapped in ' + str(data)
probe, result, keyed = home / 'sysprobe.sh', home / 'sysprobe.txt', home / 'keys.txt'
result.unlink(missing_ok=True)
keyed.unlink(missing_ok=True)
probe.write_text('''{
echo "id: $(id)"; echo "uname: $(id -un)"; echo "gname: $(id -gn)"; echo "ls: $(ls -ld ~)"
echo "sysgetprop: $(/system/bin/getprop ro.build.version.release)"; echo "getprop: $(getprop ro.build.version.release 2>&1)"
echo "dump: $(/system/bin/getprop | head -3)"; echo "toolbox: $(/system/bin/toolbox getprop ro.build.version.sdk 2>&1; echo rc=$?)"
echo "syssh: $(/system/bin/sh -c 'echo ok')"; echo "sh: $(sh -c 'echo ok')"
} > ~/sysprobe.txt 2>&1; echo SYS_DONE >> ~/sysprobe.txt
''')
# evdev codes (US layout) of `echo a.b-c/d:e | tee ~/keys.txt`, one key each (':' is Shift+';', '|' Shift+'\')
CODE = {'a': 30, 'b': 48, 'c': 46, 'd': 32, 'e': 18, 'h': 35, 'o': 24, 't': 20, 'k': 37, 's': 31, 'y': 21, 'x': 45,' ': 57, '.': 52, '-': 12, '/': 53,
        '~': 'combo:shift+41', ':': 'combo:shift+39', '|': 'combo:shift+43'}
TYPED = 'echo a.b-c/d:e | tee ~/keys.txt'
steps = [CODE[c] if isinstance(CODE[c], str) else 'key:%d' % CODE[c] for c in TYPED] + ['key:28']
env = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=90000')
shell = instance.STATE / 'shell.log'
instance.assert_clean()
if shell.exists(): shell.rename(shell.with_name('shell.log.1'))
process = None
try:
    with open(out / 'termux-sys.log', 'w') as log:
        process = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=log, stderr=subprocess.STDOUT)
        subprocess.run(['python3', 'tests/corpus/first-frame.py', str(shell), '0', '60', '1', '2'], check=True)
        instance.inject(PKG, 'focus', 'text:. ~/sysprobe.sh', 'key:28')
        text = instance.wait_file(result, 'SYS_DONE', 60)
        instance.inject(PKG, 'focus', *steps)
        try: typed = instance.wait_file(keyed, 'd:e', 30)
        finally: instance.capture('termux-sys', PKG)
finally:
    instance.stop()
    if process: process.wait()
    instance.assert_clean()
print(text)
print('hardware keys typed %r, file holds %r' % (TYPED, typed))
values = dict(line.split(': ', 1) for line in text.splitlines() if ': ' in line)
bad = []
if not re.search(r'\bu0_a\d+\b', values.get('uname', '')) or 'u0_a' not in values.get('gname', ''): bad.append('owner names: %r %r' % (values.get('uname'), values.get('gname')))
for key, want in (('sysgetprop', None), ('syssh', 'ok'), ('sh', 'ok')):
    if not values.get(key) or (want and values[key] != want): bad.append('%s: %r' % (key, values.get(key)))
if typed.strip() != 'a.b-c/d:e': bad.append('hardware punctuation: %r' % typed)
assert not bad, 'FAIL ' + '; '.join(bad)
print('PASS app uid names, /system/bin/sh and getprop, hardware punctuation keys')
