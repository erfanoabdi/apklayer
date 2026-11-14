#!/usr/bin/env python3
"""Every printable ASCII symbol and the capitals as the keyboard sends them (evdev codes, Shift as a modifier key: the path of RDP and any real
keyboard through the shell's xkb) into a native EditText (hello.input) and a WebView <input> (hello.wvprobe -Dhello.wvsymbols): the field must
hold exactly what was typed. usage: symbol-keys.py [webview]"""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
WEB = sys.argv[1:] == ['webview']
PKG, PREFIX = ('hello.wvprobe', '[wv] value=') if WEB else ('hello.input', '[hello-input] text=')
FIELD = 'tap:30,150' if WEB else 'click:240,82'
# US layout, evdev code per character: the unshifted rows, then the same keys with Shift
ROWS = [(2, '1234567890-=', '!@#$%^&*()_+'), (16, 'wertyuiop[]', 'WERTYUIOP{}'), (30, "asdfghjkl;'", 'ASDFGHJKL:"'), (41, '`', '~'),
        (43, '\\zxcvbnm,./', '|ZXCVBNM<>?')]  # q is left out (Q too): the row starts at w
KEYS = {}
for first, plain, shifted in ROWS:
    base = first + 1 if first == 16 else first
    for i, (a, b) in enumerate(zip(plain, shifted)): KEYS[a], KEYS[b] = 'key:%d' % (base + i), 'combo:shift+%d' % (base + i)
CHARS = list(KEYS)  # typed one by one, each followed by a space: a lost or doubled key shows at its position
STEPS = [s for c in CHARS for s in (KEYS[c], 'key:57')]
WANT = ' '.join(CHARS) + ' é€'  # the last two: keys without an Android keycode (script uchar), committed as IME text
ENV = dict(instance.ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='600', APKLAYER_DEBUG_INPUT='1', APKLAYER_LOGCAT='1')
if WEB: ENV.update(APKLAYER_WEBVIEW_PROVIDER='com.android.webview', APKLAYER_HOST_LIBS=instance.HOST_LIBS)
ENV['APKLAYER_JOPTS'] = '-Dapklayer.runms=110000 -Dapklayer.debugkeys=true %s -Dapklayer.script=' % ('-Dhello.wvinput=1 -Dhello.wvsymbols=1' if WEB else '') + '+'.join(
    ['%d:hover:%d,0' % (t * 500, t) for t in range(1, 200)] + ['60000:uchar:233', '60500:uchar:8364'])
instance.stop()
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
state, ticks, sent, last, loaded, focused = 'wait', 0, 0, None, False, False
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    pending, done = b'', False
    with open(instance.OUT / ('symbols-%s.log' % ('webview' if WEB else 'input')), 'wb') as log:
        while not done:
            assert selector.select(10), 'the app is silent in state ' + state
            data = os.read(app.stdout.fileno(), 65536)
            assert data, 'app ended in state ' + state
            log.write(data)
            pending += data
            *lines, pending = pending.split(b'\n')
            for line in (raw.decode(errors='replace') for raw in lines):
                if PREFIX in line: last = line[line.index(PREFIX) + len(PREFIX):]
                loaded |= '[wv] page finished' in line
                focused |= '[wv] focused' in line
                if '[wv] key=' in line: print(line, flush=True)
                if 'script: hover' not in line: continue
                ticks += 1
                if state == 'wait' and (loaded if WEB else ticks >= 6):
                    instance.inject(PKG, 'focus', FIELD)
                    state, sent = 'focus', ticks
                elif state == 'focus' and ticks >= sent + 2 and (focused or not WEB):
                    instance.inject(PKG, *STEPS)
                    state, sent = 'typed', ticks
                elif state == 'typed' and (last == WANT or ticks > 130): done = True
    if last != WANT:
        got = (last or '').split(' ')
        print('typed %d keys, the field holds %r' % (len(CHARS), last))
        print('FAIL lost or wrong:', ' '.join(c for c, g in zip(CHARS, got + [None] * len(CHARS)) if c != g), flush=True)
        sys.exit(1)
    print('PASS all %d ASCII symbols, digits and capitals typed into the %s' % (len(CHARS), 'WebView <input>' if WEB else 'EditText'), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
