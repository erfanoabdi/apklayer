#!/usr/bin/env python3
"""answer-dialog.py yes|no|text:WORDS [seconds [skip]]: answers the next question the instance hub shows in a dialog (HubInstallDialog: "dialog shown:" in hub.log with
its buttons' positions; an always-on instance's hub writes to the journal instead) by a click of the test input into that window (text:WORDS: types them into its field, then Enter); exits 1 when none is shown in time. Start it before what makes the hub ask. skip: answers the question after that many new ones (a second answerer started with the first, one that would start after it may find its question shown already)."""
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import INSTANCE, LOGDIR, inject  # noqa: E402

answer, seconds = sys.argv[1], float(sys.argv[2]) if len(sys.argv) > 2 else 60
skip = int(sys.argv[3]) if len(sys.argv) > 3 else 0
log = Path(LOGDIR) / 'hub.log'
unit = 'apklayer-instance@%s.service' % INSTANCE  # the always-on hub (a phone's instance): its output is in the journal
journal = subprocess.run(['systemctl', '--user', '-q', 'is-active', unit], stderr=subprocess.DEVNULL).returncode == 0
since = time.strftime('%Y-%m-%d %H:%M:%S')
text = lambda: subprocess.run(['journalctl', '--user', '-u', unit, '-o', 'cat', '--since', since], capture_output=True, text=True).stdout if journal \
    else log.read_text(errors='replace') if log.exists() else ''
shown = lambda: [l for l in text().split('\n') if 'dialog shown:' in l]
size = lambda: log.stat().st_size if log.exists() else -1
before, seen, end = len(shown()) + skip, size(), time.monotonic() + seconds
while time.monotonic() < end:
    # a new stack removes hub.log and its hub writes a new one (same first lines, maybe the same inode): gone or shorter, every dialog after is new
    now = size()
    if now < seen: before = skip
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
