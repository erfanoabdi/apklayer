#!/usr/bin/env python3
"""answer-dialog.py yes|no|text:WORDS [seconds]: answers the next question the instance hub shows in a dialog (HubInstallDialog: "dialog shown:" in hub.log with
its buttons' positions) by a click of the test input into that window (text:WORDS: types them into its field, then Enter); exits 1 when none is shown in time. Start it before what makes the hub ask."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import LOGDIR, inject  # noqa: E402

answer, seconds = sys.argv[1], float(sys.argv[2]) if len(sys.argv) > 2 else 60
log = Path(LOGDIR) / 'hub.log'
shown = lambda: [l for l in (log.read_text(errors='replace') if log.exists() else '').split('\n') if 'dialog shown:' in l]
size = lambda: log.stat().st_size if log.exists() else -1
before, seen, end = len(shown()), size(), time.monotonic() + seconds
while time.monotonic() < end:
    # a new stack removes hub.log and its hub writes a new one (same first lines, maybe the same inode): gone or shorter, every dialog after is new
    now = size()
    if now < seen: before = 0
    seen = now
    lines = shown()
    if len(lines) > before:
        question = lines[before]
        steps = [answer, 'key:28'] if answer.startswith('text:') else ['click:' + question.split(answer + '=')[1].split()[0]]
        print('ANSWER %s %s' % (answer, question.split('dialog shown: ')[1]), flush=True)
        for attempt in range(20):  # the window takes input once the compositor configured it
            try:
                for step in steps: inject('apklayer.installer', step)
                sys.exit(0)
            except AssertionError as missing:
                if 'no such window' not in str(missing) or attempt == 19: raise
                time.sleep(0.5)
    time.sleep(0.5)
print('no dialog shown within %d s' % seconds, flush=True)
sys.exit(1)
