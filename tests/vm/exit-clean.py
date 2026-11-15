#!/usr/bin/env python3
"""A host JVM that loaded libapklayer_egl (glbench's window: a native app on the EGL wrapper, still drawing) ends cleanly at `apklayer stop`:
exit status 0 (the app's `apklayer run`, which passes its JVM's status on) and no hs_err_pid*.log (the JVM's exit-time callbacks must not fault).
EXIT_CLEAN_REPLACE=cp|mv (with EXIT_CLEAN_OWN=<directory prefix of a private copy>) replaces the mapped libapklayer_egl.so first, by an in-place copy (cp: O_TRUNC discards the live process's relocated
GOT/data pages, the PGR hs_err files on AWS) or by a rename (mv: the mapping keeps its inode); `cp` is expected to fault, `mv` to stay clean."""
import os
import selectors
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

REPLACE = os.environ.get('EXIT_CLEAN_REPLACE', '')
DIRS = {Path.home(), Path.cwd(), Path(instance.ENV.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer' / 'apps'}  # where JVMs write hs_err
instance.stop()
start = time.time()
intent = tempfile.NamedTemporaryFile('w', suffix='.properties', delete=False)
intent.write('component=hello.glbench.Main\nextra.modes=window\nextra.scenes=clear,draws\nextra.seconds=60\n')
intent.close()
env = dict(instance.ENV, APKLAYER_INTENT_FILE=intent.name, APKLAYER_JOPTS='-Dapklayer.runms=120000')
app = subprocess.Popen(['bin/apklayer', 'run', 'hello.glbench'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, start_new_session=True)
frames = 0
code = None
try:
    with selectors.DefaultSelector() as sel:
        sel.register(app.stdout, selectors.EVENT_READ)
        end = time.monotonic() + 100
        while frames < 1 and time.monotonic() < end and sel.select(20):
            line = app.stdout.readline()
            if not line: break
            if 'glbench' in line: print(line.rstrip(), flush=True)
            if 'fps=' in line or 'GL_RENDERER' in line: frames += 1
    if REPLACE:
        pid = instance.mine('java', exact=True)[0].split()[0]
        lib = next(l.split()[-1] for l in open('/proc/%s/maps' % pid) if l.endswith('libapklayer_egl.so\n'))
        assert os.stat(lib).st_nlink == 1 and lib.startswith(os.environ['EXIT_CLEAN_OWN']), 'not a private copy under $EXIT_CLEAN_OWN: %s' % lib  # never write into a library others use
        def dirty():  # Private_Dirty of the library's mappings (the relocated GOT and data)
            total, mine = 0, False
            for l in open('/proc/%s/smaps' % pid):
                if l[0] in '0123456789abcdef' and '-' in l.split()[0]: mine = 'libapklayer_egl.so' in l
                elif mine and l.startswith('Private_Dirty'): total += int(l.split()[1])
            return total
        before = dirty()
        if REPLACE == 'cp': shutil.copyfile(lib, lib + '.same'), shutil.copyfile(lib + '.same', lib)
        else: shutil.copyfile(lib, lib + '.new'), os.rename(lib + '.new', lib)
        print('replace %s: Private_Dirty of the first library segment %d kB before, %d kB after' % (REPLACE, before, dirty()), flush=True)
finally:
    instance.stop()  # while it draws
    try: app.stdout.read()
    except OSError: pass
    code = app.wait()
    Path(intent.name).unlink(missing_ok=True)
assert frames, 'glbench never drew'
faults = [p for d in DIRS for p in d.glob('hs_err_pid*.log') if p.stat().st_mtime >= start]
for p in faults:
    print('fault note', p, '\n'.join(l for l in p.read_text(errors='replace').splitlines() if 'SIG' in l or 'libapklayer' in l)[:600])
print('exit status %s, hs_err files: %s' % (code, [str(p) for p in faults]))
if REPLACE == 'cp': sys.exit(0)  # the demonstration: the outcome is printed, not asserted
assert not faults, 'hs_err files at stop: %s' % faults
assert code in (0, -15, 143), 'the app ended with status %s at stop' % code
print('PASS exit-clean')
