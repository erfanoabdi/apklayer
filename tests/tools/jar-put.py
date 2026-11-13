#!/usr/bin/env python3
"""Selected framework overlays preserve entries, inputs and atomic failure behavior."""
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

script = Path(__file__).resolve().parents[2] / 'tools/gen/jar-put.py'
with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    original, overlay, output, native = [root / name for name in ('in.jar', 'overlay.jar', 'out.jar', 'native.so')]
    with zipfile.ZipFile(original, 'w') as jar:
        jar.writestr('dalvik/system/PathClassLoader.class', b'old')
        jar.writestr('keep', b'original')
    with zipfile.ZipFile(overlay, 'w') as jar:
        jar.writestr('dalvik/system/PathClassLoader.class', b'new')
        jar.writestr('private', b'not selected')
    native.write_bytes(b'native')
    before = original.read_bytes()
    command = [sys.executable, str(script), str(original), str(output)]
    subprocess.run(command + ['dalvik/system/PathClassLoader.class=@' + str(overlay), 'native.so=' + str(native)], check=True)
    with zipfile.ZipFile(output) as jar:
        assert len(jar.namelist()) == 3
        assert jar.read('dalvik/system/PathClassLoader.class') == b'new'
        assert jar.read('keep') == b'original' and jar.read('native.so') == b'native'
    assert original.read_bytes() == before
    good = output.read_bytes()
    failed = subprocess.run(command + ['missing=@' + str(overlay)], capture_output=True)
    assert failed.returncode != 0 and output.read_bytes() == good
print('PASS jar-put: selected overlay, regular files, original input and atomic output')
