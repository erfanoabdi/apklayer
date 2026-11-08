#!/usr/bin/env python3
"""The hub's question in the mobile window style (HubInstallDialog, bridge/shell/mobile.c): a phone simulated on the VM's compositor (a 6.1" 1080x2340
panel, as tests/vm/mobile.py), tests/apps/hello-permissions asks for the camera. The question is a full-screen page: its window has the size of
the app's maximized window (the work area), its Allow button is a full-width button at the bottom, and a finger's tap on it (wl_touch through
the shell's test input) answers Allow."""
import os
import re
import signal
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, INSTANCE, STATE, capture, inject, mine, stop, wait_file  # noqa: E402

PKG = 'hello.permissions'
data = Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer' / ('0' if INSTANCE == 'default' else INSTANCE)
stop()
for name in ('permissions-granted', 'permissions-revoked'):  # nothing answered yet for the app: the camera is asked
    f = data / name
    if f.exists(): f.write_text(''.join(l + '\n' for l in f.read_text().split('\n') if l and not l.startswith(PKG + '\t')))
for log in ('hub.log', 'shell.log'): (STATE / log).unlink(missing_ok=True)
env = dict(ENV, APKLAYER_OUTPUT_MM='66x143', APKLAYER_OUTPUT_PX='1080x2340', APKLAYER_PERMISSIONS='ask', APKLAYER_JOPTS='-Dapklayer.runms=40000')
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
try:
    hub = wait_file(STATE / 'hub.log', 'take pictures and record video', 60)
    shown = re.search(r'dialog shown: .*take pictures.* yes=(\d+),(\d+) no=(\d+),(\d+) size=(\d+)x(\d+) (\w+)', hub)
    assert shown, 'no camera question:\n' + hub[-1500:]
    yx, yy, nx, ny, w, h, style = int(shown[1]), int(shown[2]), int(shown[3]), int(shown[4]), int(shown[5]), int(shown[6]), shown[7]
    assert style == 'mobile', 'the question is not in the mobile style: %s' % shown[0]
    shell = (STATE / 'shell.log').read_text(errors='replace')
    sizes = {m[1]: (int(m[2]), int(m[3])) for m in re.finditer(r'\[shell\] (\S+): configure content (\d+)x(\d+) .*mobile style', shell)}
    work = [s for p, s in sizes.items() if p != 'apklayer.installer']
    assert work and (w, h) == work[-1], 'question %dx%d, the app\'s maximized window %s (%s)' % (w, h, work, sizes)
    assert yx == nx == w // 2 and h // 2 < yy < ny < h, 'buttons not stacked full width at the bottom: yes %d,%d no %d,%d in %dx%d' % (yx, yy, nx, ny, w, h)
    print('OK question %dx%d = the work area, buttons at the bottom (Allow %d,%d, Don\'t allow %d,%d)' % (w, h, yx, yy, nx, ny), flush=True)
    try: capture('permission-mobile')  # the desktop with the page over the app (a human check of its look)
    except Exception as no_capture: print('no desktop screenshot: %s' % no_capture, flush=True)
    inject('apklayer.installer', 'tap:%d,%d' % (yx, yy))
    wait_file(STATE / 'hub.log', 'permission android.permission.CAMERA of %s: allowed' % PKG, 20)
    assert re.search(r'dialog answered: ".*take pictures.*" Allow$', (STATE / 'hub.log').read_text(errors='replace'), re.M), 'not answered Allow'
    print('PASS the mobile question fills the work area and a tap on Allow answers it', flush=True)
finally:
    stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
