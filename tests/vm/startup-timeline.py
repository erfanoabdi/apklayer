#!/usr/bin/env python3
"""Start-up timeline of one `apklayer run` (on the VM): reads the run's output (APKLAYER_RUNTIME_DEBUG=1, -Dapklayer.perf=true) and the shell log,
prints every "[apklayer] t=<ms> <step>" relative to the run's start, then the shell's first dmabuf frame after it.
Usage: startup-timeline.py <run output> <shell.log> [hub.log]"""
import re
import sys

steps = []
for line in open(sys.argv[1], errors='replace'):
    m = re.search(r'\[apklayer\] t=(\d+) (.*)', line)
    if m:
        steps.append((int(m.group(1)), m.group(2).strip()))
for path in sys.argv[3:]:  # the hub launcher's lines (its log, not the app's output)
    for line in open(path, errors='replace'):
        m = re.search(r'\[apklayer\] t=(\d+) (hub .*)', line)
        if m:
            steps.append((int(m.group(1)), m.group(2).strip()))
start = next(t for t, s in steps if s.startswith('run '))
steps = sorted((t, s) for t, s in steps if t >= start)
# the shell prints seconds modulo 1000 with milliseconds: the first frame at or after the start
base = start // 1000000 * 1000000
for line in open(sys.argv[2], errors='replace'):
    m = re.search(r'\[shell\] t=(\d+)\.(\d+) dmabuf frame', line)
    if m:
        t = base + int(m.group(1)) * 1000 + int(m.group(2))
        if t < start:
            t += 1000000
        if t >= start and (not steps or t <= steps[-1][0] + 600000):
            steps.append((t, 'first frame (shell)'))
            break
prev = start
for t, s in sorted(steps):
    print('%7d ms  (+%6d)  %s' % (t - start, t - prev, s))
    prev = t
