#!/usr/bin/env python3
"""Heap limits as the Pixel 5 profile reports them (hello-heap, android:largeHeap): Runtime.maxMemory 512 MB (heapsize; 256 without largeHeap), getMemoryClass 256,
getLargeMemoryClass 512, and the app still gets to hold its 700 MB: there is no hard heap cap (docs/runtime-identity-matrix.md)."""
import os
from pathlib import Path
import re
import signal
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance

run = None
try:
    run = subprocess.Popen(['bin/apklayer', 'run', 'hello.heap'], env=instance.ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    out = ''
    for line in run.stdout:  # ends when the app ends (it finishes itself after "[heap] END"); the runner's own limit kills a hang
        out += line.decode(errors='replace')
        if '[heap] END' in out:
            break
    print(out[out.index('[heap]'):] if '[heap]' in out else out, flush=True)
    m = re.search(r'\[heap\] maxMemory (\d+) MB, memoryClass (\d+), largeMemoryClass (\d+)', out)
    assert m, 'no heap line'
    assert m.groups() == ('512', '256', '512'), m.groups()
    assert '[heap] PASS held 704 MB' in out, 'the app could not hold its 700 MB'
    print('PASS heap limits', flush=True)
finally:
    instance.stop()
    if run is not None:
        if run.poll() is None:
            os.killpg(run.pid, signal.SIGKILL)
        run.wait()
    instance.assert_clean()
