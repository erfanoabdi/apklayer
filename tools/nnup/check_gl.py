#!/usr/bin/env python3
"""check_gl.py SHARPEN_TEST WEIGHTS.bin [scale]: (NNUP_REF=student.pt: the reference is that student, else waifu2x) the GLES neural pass (sharpen-test with APKLAYER_SHARPEN_NN=1) against the PyTorch reference (pretrained.Waifu) on one held-out
crop: PSNR of the pass against the original, of torch against the original, and between the two (they should agree to about 50 dB), and the pass's time."""
import sys, os, glob, subprocess, tempfile, time, torch, numpy as np
from pathlib import Path
from PIL import Image
from pretrained import Waifu
from distill import Student
exe, wbin = sys.argv[1], sys.argv[2]; s = float(sys.argv[3]) if len(sys.argv) > 3 else 0.6; W, H = 1280, 720
f = sorted(glob.glob(os.path.expanduser('~/Pictures/Schermafdrukken/*.png')))
f = [x for x in f if min(Image.open(x).size) >= 400][-1]; im = Image.open(f).convert('RGB'); hr = im.crop((200, 100, 200 + W, 100 + H))
small = hr.resize((round(W * s), round(H * s)), Image.BOX)
with tempfile.TemporaryDirectory() as t:
    Path(t + '/i.raw').write_bytes(small.convert('RGBA').tobytes())
    e = dict(os.environ, APKLAYER_SHARPEN_NN='1', APKLAYER_NN_WEIGHTS=wbin, APKLAYER_SHARPEN_BACKEND='gles')
    args = [exe, t + '/i.raw', str(small.width), str(small.height), t + '/o.raw', str(W), str(H), '0.8706'] + ([os.environ['SHARPEN_MODIFIER']] if os.environ.get('SHARPEN_MODIFIER') else [])
    t0 = time.time(); r = subprocess.run(args, capture_output=True, text=True, env=e, timeout=120); dt = time.time() - t0
    print(r.stderr.strip()[-400:])
    if r.returncode: sys.exit('failed')
    gl = np.array(Image.frombytes('RGBA', (W, H), Path(t + '/o.raw').read_bytes()).convert('RGB')).astype(np.float32) / 255
if os.environ.get('NNUP_REF'):  # the student the weights file was made from
    d = torch.load(os.environ['NNUP_REF']); w = Student(d['ch']).cuda(); w.load_state_dict(d['sd'])
else: w = Waifu(os.path.expanduser('~/apklayer-nnup-work/w/up7_photo.json')).to('cuda')
x = torch.from_numpy(np.array(small)).permute(2, 0, 1).float().unsqueeze(0).cuda() / 255
with torch.no_grad(): ref = w(x, (H, W))[0].permute(1, 2, 0).cpu().numpy()
o = np.array(hr).astype(np.float32) / 255
p = lambda a, b: 10 * np.log10(1 / np.mean((a - b) ** 2))
print('scale %.2f: GL vs original %.2f dB, torch vs original %.2f dB, GL vs torch %.2f dB, process %.2f s' % (s, p(gl, o), p(ref, o), p(gl, ref), dt))
