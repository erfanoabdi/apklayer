#!/usr/bin/env python3
"""Widget input of one instance without the desktop (instance.inject: the shell's test input, bridge/shell/testinput.c): focus, a click into
the field, typed keys, Ctrl+A, IME preedit and commit into the proxied field, a touch tap on a button. Runs in parallel with other
groups; the real seat (uinput, IBus, the desktop clipboard) is tests/vm/input.py (group input-desktop, alone)."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import pixels  # noqa: E402
PKG = 'hello.input'
ENV = dict(instance.ENV, APKLAYER_RUNTIME_DEBUG='1', APKLAYER_DEBUG_INPUT='1', APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600')
FIELD, COPY = (240, 82), (240, 154)  # content pixels of hello.input's field and "Copy Android text" button (below the action bar)
WORD = (20, 82)  # on the field's text: a long press there selects a word
TICK_MS = 500  # the script's ticks, the app's clock: a step runs at the first tick its precondition holds (ticks queued while the app was busy come at once)
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=120000 -Dapklayer.debugkeys=true -Dapklayer.script=' + '+'.join(
    '%d:hover:%d,0' % (i * TICK_MS, i) for i in range(1, 230))
STALL = float(os.environ.get('APKLY_INPUT_STALL', '0'))  # seconds the app's JVM is stopped right after each step (a loaded host's stall)
seen = []
tick = [0]  # the last tick's number
mark = {}  # step name -> len(seen) when it ran


def saw(text, since=0):
    return any(text in line for line in seen[since:])


def count(text):
    return sum(text in line for line in seen)


def texts():
    return [line[line.index('text=') + 5:] for line in seen if '[hello-input] text=' in line]


def after(name):
    return seen[mark[name]:]


def ticks_since(name):
    return sum('script: hover:' in line for line in after(name))


def screenshot():
    shot = instance.capture('verified input', PKG)
    assert pixels.colors(shot) > 50, 'flat app window'


def empty_done_kept():
    later = after('third press')
    assert not any('text=' in l or 'action mode finished' in l for l in later), 'an empty IME done replaced the selection: ' + str(later)


# (name, what it waits for, test input, a check run before the input): each step's precondition is the previous step's effect, never a time,
# so a stalled app that bursts its ticks only delays the steps (docs/PLAN.md). The finger of a long press stays down until the long click ran.
STEPS = [
    ('type', lambda: tick[0] * TICK_MS >= 3000, ['focus', 'click:%d,%d' % FIELD, 'key:35', 'key:23'], None),  # h, i
    ('select all', lambda: saw('[hello-input] text=hi'), ['combo:ctrl+30'], None),
    # its own step, once Ctrl+A's key went in and a tick passed (the key runs on the main thread, IME text applies at the next input poll)
    ('ime', lambda: saw('[apklayer] key action=1 code=29', mark['select all']) and ticks_since('select all') >= 2, ['preedit:ni', 'text:你好'], None),
    ('tap', lambda: saw('[hello-input] text=你好', mark['ime']), ['tap:%d,%d' % COPY], None),
    # a preedit the IME then cancels (an empty done) leaves the field as it was
    ('cancelled preedit', lambda: saw('[hello-input] clip=android-copy'), ['preedit:q', 'text:'], screenshot),
    # three long presses: the selection's floating toolbar (a PopupWindow, FLAG_NOT_FOCUSABLE) must leave keys and the IME on the field
    ('first press', lambda: texts()[-2:] == ['你好q', '你好'], ['press:%d,%d' % WORD], None),
    ('first release', lambda: count('[hello-input] long press') >= 1, ['release:%d,%d' % WORD], None),
    ('key in selection', lambda: count('[hello-input] action mode started') >= 1, ['key:45'], None),  # x replaces the selected word
    ('second press', lambda: saw('[hello-input] text=x', mark['key in selection']), ['press:%d,%d' % WORD], None),
    ('second release', lambda: count('[hello-input] long press') >= 2, ['release:%d,%d' % WORD], None),
    # IME commit (past the one letter: the insertion toolbar): text input stays on for the field
    ('ime in selection', lambda: count('[hello-input] action mode started') >= 2, ['text:yz'], None),
    ('third press', lambda: saw('[hello-input] text=xyz', mark['ime in selection']), ['press:%d,%d' % WORD], None),
    ('third release', lambda: count('[hello-input] long press') >= 3, ['release:%d,%d' % WORD], None),
    # an empty done (the desktop's IME sends one when the field's text input is enabled) must keep the selection and its action mode
    ('empty done', lambda: count('[hello-input] action mode started') >= 3, ['text:'], None),
    ('back', lambda: count('[apklayer] ime applied') > sum('[apklayer] ime applied' in l for l in seen[:mark['empty done']]),
     ['key:1'], empty_done_kept),  # Esc is Back: it ends the action mode
    ('end', lambda: saw('[hello-input] action mode finished', mark['back']), [], None),
]


def stall():
    """The app's JVM stopped for STALL seconds: its ticks and the effects of the step just sent then come at once."""
    pid = int(next(line.rsplit('pid=', 1)[1] for line in seen if 'hub registered %s pid=' % PKG in line))
    os.kill(pid, signal.SIGSTOP)
    try: time.sleep(STALL)
    finally: os.kill(pid, signal.SIGCONT)


instance.stop()
subprocess.run(['bin/apklayer', 'ime', 'disable', 'hello.system'], env=ENV, stdout=subprocess.DEVNULL)  # an ime-bind run that failed may leave it on
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 100
    pending = b''
    step = 0
    with open(instance.OUT / 'input-inject.log', 'wb') as log:
        while step < len(STEPS):
            # the hub's start is silent and takes 2 to 6 s on a loaded VM; once the app's packages are registered, 5 s of silence is a stall
            assert selector.select(5 + STALL if any('packages registered' in l for l in seen) else 15) and time.monotonic() < end, \
                'input test stalled at step %s' % STEPS[step][0]
            data = os.read(app.stdout.fileno(), 65536)
            assert data, 'input app ended at step %s' % STEPS[step][0]
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[apklayer]' not in line and '[hello-input]' not in line: continue
                seen.append(line)
                print(line, flush=True)
                if 'script: hover:' not in line or step == len(STEPS): continue
                tick[0] = int(line.split('script: hover:')[1].split(',')[0])
                name, ready, steps, check = STEPS[step]
                if not ready(): continue
                if check: check()
                mark[name] = len(seen)
                step += 1
                if steps:
                    instance.inject(PKG, *steps)
                    if STALL: stall()
    assert app.poll() is None, 'input app ended'
    assert saw('[hello-input] text=ni'), 'IME preedit did not reach the field'
    print('PASS touch, keys, Ctrl+A, IME preedit/commit, a touch tap, and keys, IME and Back during a long press selection toolbar through the shell test input'
          + (' (the app stopped %.1f s after each step)' % STALL if STALL else ''), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
