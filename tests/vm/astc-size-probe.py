#!/usr/bin/env python3
"""Large ASTC 4x4 images through the Vulkan HAL emulation (tests/hals/vulkan-astc-probe.cpp): constant-colour (void-extent) blocks whose colour depends on the block position (no encoder needed); the decoded RGBA8 texels are compared with the known colours. Usage: astc-size-probe.py PROBE-BINARY W H [probe args], in an empty directory (writes blocks.bin and out.*). 64x64 .. 2048x1024 and 1920x1080 pass on RADV Renoir: not Endfield's scrambled tiles (CHANGELOG 2026-11-22)."""
import numpy as np, subprocess, sys, os
probe, W, H, extra = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4:]
BX = BY = 4
nbx, nby = (W + BX - 1) // BX, (H + BY - 1) // BY
bx, by = np.meshgrid(np.arange(nbx), np.arange(nby))
R = (bx * 5) & 255; G = (by * 9) & 255; B = (((bx >> 3) ^ (by >> 3)) * 37) & 255; A = np.full_like(R, 255)
blocks = np.zeros((nby, nbx, 16), np.uint8)
hdr = np.uint64(0xFFFFFFFFFFFFFDFC).tobytes()
blocks[..., :8] = np.frombuffer(hdr, np.uint8)
for k, c in enumerate((R, G, B, A)):
    v = (c.astype(np.uint16) * 257)
    blocks[..., 8 + 2 * k] = (v & 255).astype(np.uint8)
    blocks[..., 9 + 2 * k] = (v >> 8).astype(np.uint8)
open('blocks.bin', 'wb').write(blocks.tobytes())
want = np.stack([R, G, B, A], -1).astype(np.uint8)
want = np.repeat(np.repeat(want, BY, 0), BX, 1)[:H, :W]
env = dict(os.environ, APKLAYER_VULKAN_TEXTURE_EMULATION='force')
r = subprocess.run([probe, '157', str(W), str(H), '4', '4', '16', 'out', '1', '1', 'blocks.bin'] + extra, capture_output=True, text=True, env=env)
line = [l for l in r.stdout.splitlines() if l.startswith(('FAIL', 'PASS', 'SKIP', 'device'))]
got = np.fromfile('out.L0M0.rgba', np.uint8).reshape(H, W, 4) if os.path.exists('out.L0M0.rgba') else None
if got is None:
    print(W, H, 'no output', r.returncode, line[:3], r.stderr[-200:]); sys.exit(0)
d = np.abs(got.astype(int) - want.astype(int)).max(axis=2)
bad = d > 2
bb = bad.reshape(nby * BY if False else H, W)
nbad = int(bad.sum())
msg = '%dx%d: %d/%d texels off (%.1f%%)' % (W, H, nbad, W * H, 100.0 * nbad / (W * H))
if nbad:
    ys, xs = np.nonzero(bad)
    msg += ' first at x=%d y=%d (block %d,%d) got %s want %s' % (xs[0], ys[0], xs[0] // 4, ys[0] // 4, got[ys[0], xs[0]].tolist(), want[ys[0], xs[0]].tolist())
print(msg, '|', ' '.join(line[:2]))
os.remove('out.L0M0.rgba')
