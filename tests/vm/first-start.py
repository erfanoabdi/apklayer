#!/usr/bin/env python3
"""The first start on a fresh data home: install the providers and DocumentsUI as a new user does (docs/INSTALL.md), then start DocumentsUI.
The hub writes the boot settings into the just-installed SettingsProvider (HubBootSettings: boot count 1) and DocumentsUI stays up and draws.
Once logged `boot settings not written: NullPointerException` and DocumentsUI closed; the second start worked."""
import os
from pathlib import Path
import platform
import re
import selectors
import shutil
import signal
import subprocess
import sys
import time

OUT = Path(os.environ.get('APKLY_OUT', '/tmp/apklayer-test-first-start'))
FRESH = OUT / 'fresh'
shutil.rmtree(FRESH, ignore_errors=True)
os.environ.update(XDG_DATA_HOME=str(FRESH / 'data'), XDG_STATE_HOME=str(FRESH / 'state'))  # before instance: its STATE follows them
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
import pixels  # noqa: E402

PKG = 'com.android.documentsui'
APKS = Path(os.environ.get('APKLAYER_PREBUILTS', Path.home() / 'prebuilts')) / {'aarch64': 'arm64'}.get(platform.machine(), platform.machine()) / 'system-apps'
instance.stop()
for name in ('SettingsProvider', 'MediaProvider', 'ContactsProvider', 'CalendarProvider', 'DownloadProvider', 'UserDictionaryProvider',
             'ExternalStorageProvider', 'TelephonyProvider', 'DocumentsUI'):
    out = subprocess.run(['bin/apklayer', 'install', str(APKS / (name + '.apk'))], env=instance.ENV, capture_output=True, text=True, errors='replace')
    assert 'installed' in out.stdout + out.stderr, 'installing %s failed:\n%s' % (name, (out.stdout + out.stderr)[-2000:])
print('providers and DocumentsUI installed into a fresh data home', flush=True)

app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=instance.ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
hub = instance.STATE / 'hub.log'
try:
    selector = selectors.DefaultSelector()
    selector.register(app.stdout, selectors.EVENT_READ)
    end, boot, settled = time.monotonic() + 90, None, None
    with open(OUT / 'first-start.log', 'wb') as log:
        while time.monotonic() < (settled or end):  # the boot settings, then 10 s more for DocumentsUI to load its roots and draw
            if selector.select(1):
                data = os.read(app.stdout.fileno(), 65536)
                assert data, 'DocumentsUI closed on its first start (hub: %s)' % boot
                log.write(data)
            if boot is None and hub.exists():
                m = re.search(r'boot count \d+|boot settings not written: .*', hub.read_text(errors='replace'))
                if m: boot, settled = m.group(0), time.monotonic() + 10
    print('hub: %s' % boot, flush=True)
    assert boot and boot.startswith('boot count'), 'the boot settings were not written: %s' % boot
    shot = instance.capture('documentsui-first-start', PKG)
    shades = pixels.colors(shot)
    assert app.poll() is None, 'DocumentsUI closed'
    assert shades > 8, 'DocumentsUI shows no drawn UI (%d colours)' % shades
    print('PASS first start: the providers installed into a fresh data home, boot settings written (%s), DocumentsUI up and drawn (%d colours, %s)'
          % (boot, shades, shot), flush=True)
finally:
    instance.stop()
    if app.poll() is None: os.killpg(app.pid, signal.SIGKILL)
    app.wait()
    left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    if left:  # the spare JVM can outlive the first stop (PLAN "Startup and memory")
        instance.stop()
        left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
    assert not left, left
