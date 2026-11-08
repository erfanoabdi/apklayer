#!/usr/bin/env python3
"""Fences are real sync_files: glbench's window (clear, draws) through apklayer, then the instance's logs must hold no
"sync_file_info returned NULL" (libui's Fence::getSignalTime on an fd that is no sync_file) and no "Invalid present fence" (SurfaceFlinger).
Prints the counts per log, their rate per 10 s of the run, and glbench's result lines (frame times); each scene must get its frame timestamps."""
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)

NEEDLES = ('sync_file_info returned NULL', 'Invalid present fence')
seconds = sys.argv[1] if len(sys.argv) > 1 else '5'

instance.stop()
logs = {p: p.stat().st_size for p in instance.STATE.glob('*.log')}
start = time.monotonic()
DUMP = instance.OUT / 'frametimeline'  # SurfaceFlinger's frame timeline at the end: present times from the present fences
for old in DUMP.parent.glob(DUMP.name + '.*'): old.unlink()
env = dict(instance.ENV, GLBENCH_DUMP=str(DUMP), GLBENCH_DUMP_ARGS='--frametimeline -all')
run = subprocess.run([sys.executable, 'tests/bench/glbench/run-android.py', 'window', 'clear,draws', seconds], env=env,
                     capture_output=True, text=True)
elapsed = time.monotonic() - start
print(run.stdout, end='', flush=True)
instance.stop()

counts = {}
for log in sorted(instance.STATE.glob('*.log')):
    with open(log, 'rb') as f:
        f.seek(logs.get(log, 0))
        text = f.read().decode(errors='replace')
    for needle in NEEDLES:
        n = text.count(needle)
        if n:
            counts[(log.name, needle)] = n
            sample = next(l for l in text.splitlines() if needle in l)
            print('%s: %d x "%s" (%.1f per 10 s), e.g. %s' % (log.name, n, needle, n * 10 / elapsed, sample.strip()[:160]), flush=True)
# a present fence's time before its frame's start (a stale timestamp) makes the frame timeline drop it: jank "Unknown", present = end time
dumps = sorted(DUMP.parent.glob(DUMP.name + '.*'), key=lambda p: int(p.suffix[1:]))
frames = dumps[-1].read_text(errors='replace').split('Display Frame ')[1:] if dumps else []
unknown = sum('Unknown jank' in f.split('Present Metadata')[0] for f in frames)
print('frame timeline: %d display frames, %d with an unusable present time ("Unknown jank")' % (len(frames), unknown), flush=True)
assert 'glbench: done' in run.stdout, 'glbench did not finish'
assert frames and unknown * 4 < len(frames), 'present fences carry no usable time'
assert not counts, 'fences that are no sync_file (%.0f s run)' % elapsed
# EGL_ANDROID_get_frame_timestamps (Swappy, FrameMetrics): SurfaceFlinger's latch and composition times reach the app through the EGL
stamps = [l for l in run.stdout.splitlines() if ' timestamps ' in l]
assert stamps and all(' timestamps ok ' in l and 'queued_to_latch_ms=-1.00' not in l for l in stamps), 'no frame timestamps: %s' % stamps
print('fences: no fence log lines in %.0f s' % elapsed)
