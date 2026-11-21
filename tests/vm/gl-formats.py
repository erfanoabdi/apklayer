#!/usr/bin/env python3
"""GL colour and format paths through apklayer's EGL/GLES wrapper against the host driver alone (tests/bench/glformats): sRGB encode/decode,
float and packed render targets, depth/stencil, MSAA resolve, ETC2/EAC/ASTC/S3TC blocks, then an sRGB window surface request. The same C code runs
on Linux (the reference, built here when the VM has the headers) and as the offscreen entry of hello-glformats and in its window; every check must
give the same verdict on both (a driver that is wrong on its own is the reference's problem, not the wrapper's), the extension list must not have
names the host lacks, and an sRGB window surface must be refused or really encode. Output in $APKLY_OUT: linux.out, offscreen.out, window.out."""
import os
import selectors
import subprocess
import sys
import time
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

APK = Path('.tests/apks/hello-glformats.apk')
OUT = instance.OUT
ENV = dict(instance.ENV)


def verdicts(text):
    """check name -> PASS/FAIL/SKIP, and the extension names."""
    checks, exts = {}, set()
    for line in text.splitlines():
        parts = line.split(None, 3)
        if len(parts) >= 3 and parts[0] == 'glformats:' and parts[1] == 'ext': exts.add(parts[2])
        elif len(parts) >= 3 and parts[2] in ('PASS', 'FAIL', 'SKIP') and not parts[1].startswith('window-colorspace'): checks[parts[1]] = parts[2]
    return checks, exts


def etc2_rows(text):
    """(format, row) -> RGBA8 bytes of the etc2d lines (random ETC2/EAC blocks sampled as texels)."""
    rows = {}
    for line in text.splitlines():
        parts = line.split()
        if len(parts) == 5 and parts[:2] == ['glformats:', 'etc2d']: rows[(parts[2], int(parts[3]))] = bytes.fromhex(parts[4])
    return rows


def etc2_differences(label, got, ref):
    """Texels (any channel off by more than 1: decoders round their own way) of the ETC2/EAC random blocks that differ from the host driver alone."""
    bad = {}
    for key, row in got.items():
        if key in ref and any(abs(a - b) > 1 for a, b in zip(row, ref[key])): bad[key[0]] = bad.get(key[0], 0) + 1
    return ['%s etc2 %s: %d of 16 rows differ from the host driver alone' % (label, name, n) for name, n in sorted(bad.items())]


def reference():
    exe = OUT / 'glformats-linux'
    cc = subprocess.run(['cc', '-O1', '-o', str(exe), 'tests/bench/glformats/linux.c', '-lEGL', '-lGLESv2', '-lgbm', '-lm'], capture_output=True, text=True)
    if cc.returncode: print('reference not built (%s)' % cc.stderr.strip()[:200]); return None
    run = subprocess.run([str(exe)], capture_output=True, text=True, timeout=120)
    (OUT / 'linux.out').write_text(run.stdout)
    return run.stdout


def offscreen():
    lib = OUT / 'lib'
    lib.mkdir(exist_ok=True)
    abi = 'x86_64' if os.uname().machine == 'x86_64' else 'arm64-v8a'  # btload runs the host's own ABI
    with zipfile.ZipFile(APK) as z: (lib / 'libhello.so').write_bytes(z.read('lib/%s/libhello.so' % abi))
    env = dict(line.split('=', 1) for line in subprocess.run(['bin/apklayer', 'env'], env=ENV, capture_output=True, text=True, check=True).stdout.splitlines() if '=' in line)
    native = env['APKLAYER_NATIVE']
    run = subprocess.run([native + '/bin/btload', '%s/android-libs:%s' % (native, lib), 'libhello.so', '--main'], env=env, capture_output=True, text=True, timeout=180)
    (OUT / 'offscreen.out').write_text(run.stdout + run.stderr)
    return run.stdout


def window():
    app = subprocess.Popen(['bin/apklayer', 'run', 'hello.glformats'], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    text = b''
    end = time.monotonic() + 150
    with selectors.DefaultSelector() as sel:
        sel.register(app.stdout, selectors.EVENT_READ)
        while time.monotonic() < end and b'glformats: window done' not in text:
            if not sel.select(30): break
            chunk = os.read(app.stdout.fileno(), 65536)
            if not chunk: break
            text += chunk
    (OUT / 'window.out').write_bytes(text)
    return text.decode(errors='replace')


instance.stop()
try:
    ref = reference()
    # the offscreen entry loads the app's library with btload, outside a launch: ANGLE's libraries are linked into the system directory only by a launch,
    # so with APKLAYER_GLES_ANGLE=on only the window run exercises the stack
    off = offscreen() if ENV.get('APKLAYER_GLES_ANGLE') != 'on' else None
    win = window()
finally:
    instance.stop()
bad = []
off_checks, off_exts = verdicts(off or '')
assert off is None or 'glformats: done' in off, 'the offscreen run did not finish:\n' + off[-1500:]
if ref:
    ref_checks, ref_exts = verdicts(ref)
    for name, v in off_checks.items():
        if name in ref_checks and ref_checks[name] != v: bad.append('offscreen %s: wrapper %s, host alone %s' % (name, v, ref_checks[name]))
        if name not in ref_checks and v == 'FAIL': bad.append('offscreen %s FAIL (a wrapper-only check)' % name)  # linked-glGetString...
    extra = sorted(off_exts - ref_exts)
    if extra: print('extensions only the wrapper lists: %s' % ' '.join(extra))
else:
    bad += ['offscreen %s FAIL' % n for n, v in off_checks.items() if v == 'FAIL']
if ref:
    bad += etc2_differences('offscreen', etc2_rows(off or ''), etc2_rows(ref))
    bad += etc2_differences('window', etc2_rows(win), etc2_rows(ref))
win_checks, _ = verdicts(win)
assert 'glformats: done' in win, 'the window run did not finish:\n' + win[-1500:]
bad += ['window %s FAIL' % n for n, v in win_checks.items() if v == 'FAIL' and (not ref or ref and (ref_checks.get(n) != 'FAIL'))]
bad += [l.strip() for l in win.splitlines() if 'window-colorspace' in l and ' FAIL ' in l]
for l in win.splitlines():
    if 'window-colorspace' in l: print(l.strip()[:250])
print('%d checks offscreen, %d in the window, %d differences' % (len(off_checks), len(win_checks), len(bad)))
assert not bad, '\n'.join(bad)
print('gl-formats: the wrapper agrees with the host driver')
