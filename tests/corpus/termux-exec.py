#!/usr/bin/env python3
"""Runs on the test VM in an instance where real Termux (com.termux, bootstrapped) is installed: a failed exec in a forked child must not hang the
terminal. Types into Termux's terminal (shell test input, as termux-env.py) a line that runs a missing command by absolute path, a script whose
shebang names a missing interpreter and `sh -c 'exec /missing'`, each followed by a marker written to ~/exec-probe.txt; all markers (DONE last)
must arrive within 30 s (the forked child's exit() hung before native_loader.c's atfork on_exit hook).
Usage (instance environment, repo root): python3 tests/corpus/termux-exec.py [out-dir]."""
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402

PKG = 'com.termux'
out = Path(sys.argv[1]) if len(sys.argv) > 1 else instance.OUT
out.mkdir(parents=True, exist_ok=True)
info = subprocess.check_output(['bin/apklayer', 'info', PKG], env=instance.ENV, text=True)
data = Path(dict(line.split('\t', 1) for line in info.splitlines() if '\t' in line)['dataDir'])
assert '/%s/app-data/' % instance.INSTANCE in str(data), data
home = data / 'app/files/home'
assert (data / 'app/files/usr/bin/bash').exists(), 'Termux is not bootstrapped in ' + str(data)
result = home / 'exec-probe.txt'
result.unlink(missing_ok=True)
line = ("/data/data/com.termux/files/usr/bin/missing-cmd; echo A1 >> ~/exec-probe.txt; "
        "printf '#!/missing/interpreter\\necho hi\\n' > ~/bad.sh; chmod +x ~/bad.sh; ./bad.sh; echo A2 >> ~/exec-probe.txt; "
        "sh -c 'exec /missing/cmd'; echo A3 >> ~/exec-probe.txt; echo DONE >> ~/exec-probe.txt")
env = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=120000')
shell = instance.STATE / 'shell.log'
instance.assert_clean()
if shell.exists(): shell.rename(shell.with_name('shell.log.1'))
process = None
try:
    with open(out / 'termux-exec.log', 'w') as log:
        process = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=log, stderr=subprocess.STDOUT)
        subprocess.run(['python3', 'tests/corpus/first-frame.py', str(shell), '0', '60', '1', '2'], check=True)
        start = time.monotonic()
        instance.inject(PKG, 'focus', 'text:' + line, 'key:28')
        try: text = instance.wait_file(result, 'DONE', 30)
        finally: instance.capture('termux-exec', PKG)
        took = time.monotonic() - start
finally:
    instance.stop()
    if process: process.wait()
    instance.assert_clean()
assert text.split() == ['A1', 'A2', 'A3', 'DONE'], text
print('PASS Termux: failed execs in forked children (missing command, bad shebang, exec of a missing file) return to the prompt in %.1f s' % took)
