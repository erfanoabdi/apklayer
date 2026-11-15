#!/usr/bin/env python3
"""Several GPUs (iGPU + dGPU, PRIME laptops): the instance's GPU against the compositor's. For every hardware GPU `apklayer gpus` lists, the
instance runs on it (APKLAYER_GPU=<id>) and gpu-smoke.py (GLES window frames reach the compositor), gles1.py (ES 1) and vulkan-present.py (an Android swapchain
on that GPU) must pass. A GPU that is not the compositor's is a mixed run: the graphics HAL must say its window buffers are LINEAR
(graphics.c), the compositor imports them across GPUs. One hardware GPU: nothing to compare, skipped."""
import os
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

rows = [l.split('\t') for l in subprocess.run(['bin/apklayer', 'gpus'], env=instance.ENV, capture_output=True, text=True, check=True).stdout.splitlines()[1:]]
gpus = [r for r in rows if r[3] != '-' and 'render' in r[1]]  # PCI devices with a render node: the hardware (not vkms, vgem, llvmpipe)
if len(gpus) < 2:
    print('SKIP gpu-multi: %d hardware GPU(s)' % len(gpus))
    sys.exit(0)
print('GPUs: ' + ', '.join('%s %s (%s)%s' % (g[0], g[1], g[6], ' [compositor]' if g[4] == 'yes' else '') for g in gpus))


def run(script, gpu, *args):
    env = dict(instance.ENV, APKLAYER_GPU=gpu[0])
    r = subprocess.run([sys.executable, '-u', 'tests/vm/' + script, *args], env=env, capture_output=True, text=True)
    out = r.stdout + r.stderr
    assert r.returncode == 0 and 'PASS' in out, '%s on %s (%s) failed (%d):\n%s' % (script, gpu[0], gpu[1], r.returncode, out[-3000:])
    return out


def logs():
    return ''.join(f.read_text(errors='replace') for f in Path(instance.LOGDIR).glob('*.log'))


VENDOR = {'i915': 'Intel', 'xe': 'Intel', 'amdgpu': 'AMD', 'radeon': 'AMD', 'nouveau': 'NVIDIA', 'nvidia': 'NVIDIA'}
for gpu in gpus:
    mixed = gpu[4] != 'yes' and any(g[4] == 'yes' for g in gpus)
    for script in ('gpu-smoke.py', 'gles1.py', 'vulkan-present.py'):
        for f in Path(instance.LOGDIR).glob('*.log'): f.unlink()  # this run's lines only
        out = run(script, gpu)
        text = logs()
        # the app drew on the chosen GPU: its GL renderer (smoke), its Vulkan device filter (present: "<node>: n of m Vulkan devices")
        if script != 'vulkan-present.py':
            renderers = re.findall(r"apklayer.egl: GL version '[^']*' renderer '([^']*)'", text)
            assert renderers and all(VENDOR.get(gpu[2], gpu[2]) in r for r in renderers), '%s: renderers %s, not on %s' % (script, renderers, gpu[2])
        else:
            assert 'apklayer.vulkan: %s: ' % gpu[1] in text, '%s: no Vulkan device of %s' % (script, gpu[1])
        # LINEAR, 256-aligned window buffers exactly where another GPU's compositor imports them (graphics.c)
        assert ("is not the compositor's GPU" in text) == mixed, '%s: %s run, but the HAL %s say the buffers are LINEAR' % (
            script, 'mixed' if mixed else 'same-GPU', 'did not' if mixed else 'did')
        print('PASS %s on %s (%s)%s: %s' % (script, gpu[0], gpu[1], ' mixed with the compositor' if mixed else '', out.strip().splitlines()[-1][:160]))
print('PASS gpu-multi: %d GPUs' % len(gpus))
