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
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=26000 -Dapklayer.debugkeys=true -Dapklayer.script=' + '+'.join(
    '%d:hover:%d,0' % (t, step) for t, step in ((3000, 1), (6000, 2), (7000, 3), (9000, 4), (11000, 5), (13000, 6), (14000, 7), (16000, 8), (17000, 9), (18000, 10), (20000, 11), (21000, 12), (23000, 13), (24000, 14)))
seen = []
checks = set()
held = {}  # the long press under way: {'lp': the long clicks seen once it ran, 'up': released}
deferred = []  # script steps that came during it


def saw(text):
    return any(text in line for line in seen)


def handle(line):
    """What the test does on one line of the app."""
    if 'script: hover:1,0' in line:
        instance.inject(PKG, 'focus', 'click:%d,%d' % FIELD, 'key:35', 'key:23')  # h, i
    elif 'script: hover:2,0' in line:
        assert saw('[hello-input] text=hi'), 'touch focus and keys did not type hi'
        checks.add('typing')
        instance.inject(PKG, 'combo:ctrl+30')  # select all
    elif 'script: hover:3,0' in line:
        # a step of its own: the app applies IME text on the shell's reader thread but keys on its main thread, so text sent
        # right behind a key can overtake it (docs/PLAN.md)
        instance.inject(PKG, 'preedit:ni', 'text:你好')
    elif 'script: hover:4,0' in line:
        assert saw('[hello-input] text=你好'), 'IME commit did not replace the selection'
        checks.add('ime')
        instance.inject(PKG, 'tap:%d,%d' % COPY)
    elif 'script: hover:5,0' in line:
        assert saw('[hello-input] clip=android-copy'), 'touch tap did not reach the button'
        checks.add('tap')
        shot = instance.capture('verified input', PKG)
        assert pixels.colors(shot) > 50, 'flat app window'
        instance.inject(PKG, 'preedit:q', 'text:')  # a preedit the IME then cancels (an empty done) leaves the field as it was
    # a long press: the selection's floating toolbar (a PopupWindow, FLAG_NOT_FOCUSABLE) must leave keys and the IME on the field (follow)
    elif 'script: hover:6,0' in line or 'script: hover:9,0' in line or 'script: hover:12,0' in line:
        if 'hover:6,0' in line:
            texts = [l for l in seen if '[hello-input] text=' in l]
            assert texts[-2:] == ['[hello-input] text=你好q', '[hello-input] text=你好'], 'a cancelled preedit: ' + str(texts[-3:])
        if 'hover:12,0' in line: assert saw('[hello-input] text=xyz'), 'IME text during the selection toolbar did not reach the field'
        instance.inject(PKG, 'press:%d,%d' % WORD)
        held.update(lp=sum('[hello-input] long press' in l for l in seen) + 1, up=False)
    elif 'script: hover:8,0' in line:
        assert saw('[hello-input] action mode started'), 'a long press started no selection action mode'
        instance.inject(PKG, 'key:45')  # x replaces the selected word
    elif 'script: hover:11,0' in line:
        assert saw('[hello-input] text=x'), 'a key during the selection toolbar did not reach the field'
        assert sum('action mode started' in l for l in seen) >= 2, 'the second long press started no action mode'
        instance.inject(PKG, 'text:yz')  # IME commit (past the one letter: the insertion toolbar): text input stays on for the field
    elif 'script: hover:13,0' in line:
        instance.inject(PKG, 'text:')  # an empty done (the desktop's IME sends one when the field's text input is enabled)
    elif 'script: hover:14,0' in line:
        assert sum('action mode started' in l for l in seen) >= 3, 'the third long press started no action mode'
        after = seen[max(i for i, l in enumerate(seen) if 'hover:12,0' in l):]
        assert not any('text=' in l or 'action mode finished' in l for l in after), 'an empty IME done replaced the selection: ' + str(after)
        instance.inject(PKG, 'key:1')  # Esc is Back: it ends the action mode


def follow(line):
    """Whether handle() takes the line now. The finger stays down until the long click ran (a stalled app emitted two steps at once and its
    press and release came back to back, a tap) and the steps the script emits meanwhile wait for its action mode, as a person goes on once
    the toolbar shows. No step is the long click's deadline, the app's end is: the steps follow the app's clock, which runs on through a
    stall, and under parallel load the app stalled for seconds around a press (its DOWN came after the next step), a held press taken for a
    missed long click (input-inject's third long press, docs/PLAN.md)."""
    if not held: return True
    if '[hello-input] long press' in line and not held['up'] and sum('[hello-input] long press' in l for l in seen) >= held['lp']:
        instance.inject(PKG, 'release:%d,%d' % WORD)
        held['up'] = True
    elif 'script: hover:' in line: deferred.append(line)
    if held['up'] and '[hello-input] action mode started' in line:
        steps = deferred[:]
        deferred.clear()
        held.clear()
        for step in steps:
            if follow(step): handle(step)
    return False


instance.stop()
subprocess.run(['bin/apklayer', 'ime', 'disable', 'hello.system'], env=ENV, stdout=subprocess.DEVNULL)  # an ime-bind run that failed may leave it on
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end = time.monotonic() + 40
    pending = b''
    with open(instance.OUT / 'input-inject.log', 'wb') as log:
        while True:
            # the hub's start is silent and takes 2 to 6 s on a loaded VM; once the app's packages are registered, 5 s of silence is a stall
            assert selector.select(5 if any('packages registered' in l for l in seen) else 15) and time.monotonic() < end, 'input test stalled'
            data = os.read(app.stdout.fileno(), 65536)
            if not data: break
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for raw in lines:
                line = raw.decode(errors='replace')
                if '[apklayer]' not in line and '[hello-input]' not in line: continue
                seen.append(line)
                print(line, flush=True)
                if follow(line): handle(line)
    assert not held, ('a long press started no action mode: ' if held['up'] else 'a long press on the field ran no long click: ') + str(deferred)
    assert app.wait() == 0, 'input app failed'
    assert saw('[hello-input] text=ni'), 'IME preedit did not reach the field'
    assert any('action mode finished' in l for l in seen[max(i for i, l in enumerate(seen) if 'hover:14,0' in l):]), 'Back did not end the action mode'
    checks.add('long-press')
    assert checks == {'typing', 'ime', 'tap', 'long-press'}, checks
    print('PASS touch, keys, Ctrl+A, IME preedit/commit, a touch tap, and keys, IME and Back during a long press selection toolbar through the shell test input', flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
