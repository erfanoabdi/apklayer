#!/usr/bin/env python3
"""OpenGL ES 1 from an app (hello-gles1: GLSurfaceView's default ES 1 context): a red clear and a fixed-function triangle read back, through the host
driver's own libGLESv1_CM (NVIDIA, Mesa builds with ES 1) or libapklayer-gles1's translator. Prints which one answered (GL_RENDERER)."""
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

env = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=8000 ' + instance.ENV.get('APKLAYER_EXTRA_JOPTS', ''))
lines = []
try:
    instance.stop()
    proc = subprocess.run(['bin/apklayer', 'run', 'hello.gles1'], env=env, capture_output=True, text=True)
    lines = [l for l in (proc.stdout + proc.stderr).splitlines() if 'gles1:' in l or 'Exception' in l or 'no OpenGL ES 1' in l]
finally:
    instance.stop()
print('\n'.join(lines))
out = '\n'.join(lines)
# NVIDIA's GL_VERSION: "OpenGL ES 1.1 NVIDIA ...", the translator's and Mesa's: "OpenGL ES-CM 1.1 ..."
assert re.search(r'gles1: OpenGL ES(-CM)? 1\.', out), 'no OpenGL ES 1 context answered'
assert 'center ff00ff corner ff0000ff err 0' in out, 'the triangle or the clear did not read back (green centre, red corner, no GL error)'
print('PASS gles1: an ES 1 app draws and reads back')
