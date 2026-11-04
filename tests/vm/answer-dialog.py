#!/usr/bin/env python3
"""answer-dialog.py yes|no [seconds]: answers the next question the instance hub shows in a dialog (HubInstallDialog: "dialog shown:" in hub.log with
its buttons' positions) by a click of the test input into that window; exits 1 when none is shown in time. Start it before what makes the hub ask."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import LOGDIR, inject  # noqa: E402

answer, seconds = sys.argv[1], float(sys.argv[2]) if len(sys.argv) > 2 else 60
log = Path(LOGDIR) / 'hub.log'
shown = lambda: [l for l in (log.read_text(errors='replace') if log.exists() else '').split('\n') if 'dialog shown:' in l]
before, end = len(shown()), time.monotonic() + seconds
while time.monotonic() < end:
    lines = shown()
    if len(lines) > before:
        question = lines[before]
        xy = question.split(answer + '=')[1].split()[0]
        print('ANSWER %s %s' % (answer, question.split('dialog shown: ')[1]), flush=True)
        for attempt in range(20):  # the window takes input once the compositor configured it
            try: inject('apklayer.installer', 'click:' + xy); sys.exit(0)
            except AssertionError as missing:
                if 'no such window' not in str(missing) or attempt == 19: raise
                time.sleep(0.5)
    time.sleep(0.5)
print('no dialog shown within %d s' % seconds, flush=True)
sys.exit(1)
