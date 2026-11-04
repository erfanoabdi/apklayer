#!/usr/bin/env python3
"""GPU per instance (cli/gpus.cpp): `apklayer gpus` lists the render nodes, `gpu set <id>` makes that node the instance's (the environment of
its Android processes names it), `gpu unset` returns to the compositor's GPU, an unknown id is refused. Rendering on the choice: the gpu and
gpu-window groups with APKLAYER_GPU=<id>."""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)


def cli(*args, check=True):
    r = subprocess.run(['bin/apklayer', *args], env=instance.ENV, capture_output=True, text=True)
    assert not check or r.returncode == 0, '%s: %s' % (' '.join(args), r.stderr)
    return r


def node_in_env():
    return next((l.split('=', 1)[1] for l in cli('env').stdout.splitlines() if l.startswith('APKLAYER_RENDER_NODE=')), None)


rows = [l.split('\t') for l in cli('gpus').stdout.splitlines()]
assert rows and rows[0][:6] == ['id', 'node', 'driver', 'vendor:device', 'compositor', 'instance'], rows[:1]
gpus = rows[1:]
assert gpus, 'no render node listed'
assert sum(g[5] == 'yes' for g in gpus) == 1 and sum(g[4] == 'yes' for g in gpus) <= 1, gpus
default = next((g for g in gpus if g[4] == 'yes'), gpus[0])
try:
    for g in gpus:
        cli('gpu', 'set', g[0])
        assert node_in_env() == g[1], 'gpu set %s: the environment names %s' % (g[0], node_in_env())
        assert [r[5] for r in (l.split('\t') for l in cli('gpus').stdout.splitlines()[1:]) if r[0] == g[0]] == ['yes']
    assert cli('gpu', 'set', 'no-such-gpu', check=False).returncode != 0, 'an unknown GPU was accepted'
finally:
    cli('gpu', 'unset')
assert node_in_env() == default[1], 'unset: %s, not the default %s' % (node_in_env(), default[1])
print('PASS GPU choice: %d GPU(s), default %s (%s)' % (len(gpus), default[0], default[1]))
