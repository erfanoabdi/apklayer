#!/usr/bin/env python3
"""time_gl.py SHARPEN_TEST WEIGHTS.bin: ms per frame of the GLES neural pass at 1080p output for render scales 0.75, 0.6, 0.5, 0.4 (SHARPEN_SEQ 21 frames minus 1,
so the start-up is not counted; the numbers include the readback of each frame, a constant of the test program)."""
import os, sys, subprocess, time, tempfile, glob
from PIL import Image
exe, wbin = sys.argv[1], sys.argv[2]; ow, oh = 1920, 1080
base = Image.open(sorted(glob.glob(os.path.expanduser('~/Pictures/Schermafdrukken/*.png')))[-1]).convert('RGB').crop((0, 0, ow, oh))
for s in (0.75, 0.6, 0.5, 0.4):
    sm = base.resize((round(ow * s), round(oh * s)), Image.BOX); raw = sm.convert('RGBA').tobytes(); ts = {}
    with tempfile.TemporaryDirectory() as t:
        for n in (1, 21):
            open(t + '/i.raw', 'wb').write(raw * n)
            e = dict(os.environ, APKLAYER_SHARPEN_NN=os.environ.get('NN', '1'), APKLAYER_NN_WEIGHTS=wbin, APKLAYER_SHARPEN_BACKEND='gles', SHARPEN_SEQ=str(n))
            t0 = time.time(); r = subprocess.run([exe, t + '/i.raw', str(sm.width), str(sm.height), t + '/o.raw', str(ow), str(oh), '0.87'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else []), env=e, capture_output=True); ts[n] = time.time() - t0
    print('scale %.2f %dx%d -> %dx%d: %.0f ms/frame (rc %d)' % (s, sm.width, sm.height, ow, oh, (ts[21] - ts[1]) / 20 * 1000, r.returncode))
