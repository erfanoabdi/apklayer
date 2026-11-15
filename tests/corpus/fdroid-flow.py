#!/usr/bin/env python3
"""Runs on the test VM in an instance where the real F-Droid client (org.fdroid.fdroid 2.0.x) is installed, offline: its update and uninstall flows for an app it
installed once, against a one-app repository served from this host (tests/corpus/fdroid-repo.py: index-v2 and an entry.jar signed with a throw-away key,
http://127.0.0.1:<port>/repo?fingerprint=...). The app is hello.installer (version 1 installed, version 2 in the repository: the APK embedded in hello-installer).
Steps: the default repository off -> Add repository (URL typed, fingerprint checked) -> the app's page shows "Update" -> Update: F-Droid downloads the APK and installs it
through a PackageInstaller session (the hub asks twice: may F-Droid install apps, update the app) -> versionCode 2 -> the page's menu "Uninstall" (the hub asks) -> the
package is gone. Screens are read from the host's `views` step (tests/corpus/uidrive.py), buttons are tapped at the places of a 448x797 window.
Usage (instance environment, repo root): python3 tests/corpus/fdroid-flow.py. Files: instance.OUT/fdroid-*.png, org.fdroid.fdroid.out, srv.log (the repository's requests)."""
import socket
import subprocess
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import uidrive  # noqa: E402
from uidrive import instance  # noqa: E402

PKG, APP, LABEL = 'org.fdroid.fdroid', 'hello.installer', 'Hello Installer'
W = Path(instance.OUT)
V1 = instance.ROOT / '.tests/apks/hello-installer.apk'  # the group's app: version 1, its assets hold version 2


def apklayer(*args):
    return subprocess.run(['bin/apklayer', *args], env=instance.ENV, capture_output=True, text=True).stdout


def code():
    return dict(l.split('\t', 1) for l in apklayer('info', APP).splitlines() if '\t' in l).get('versionCode')


def step(name, text, seconds=60):
    print('STEP', name, flush=True)
    try: return app.text(text, seconds)
    except AssertionError as e:
        app.shot('fdroid-failed')
        raise AssertionError('F-Droid step "%s" failed: %s' % (name, e))


def tap(x, y):
    app.tap(x, y)
    app.settle(5)


def answer(count):
    """`count` answerers of the hub's dialogs (tests/vm/answer-dialog.py: started before the click that makes the hub ask): the n-th answers the n-th new question yes."""
    return [subprocess.Popen(['python3', 'tests/vm/answer-dialog.py', 'yes', '120', str(skip)]) for skip in range(count)]


# the repository: version 2 of the test app, on a free port of this host
with socket.socket() as s:
    s.bind(('127.0.0.1', 0))
    port = s.getsockname()[1]
(W / 'new.apk').write_bytes(zipfile.ZipFile(V1).read('assets/hello-installer2.apk'))
address = 'http://127.0.0.1:%d/repo' % port
finger = subprocess.run(['python3', 'tests/corpus/fdroid-repo.py', str(W / 'srv'), str(W / 'new.apk'), APP, '2', '2.0', LABEL, address], check=True,
                        capture_output=True, text=True).stdout.strip()
apklayer('install', '-d', str(V1))  # version 1 (also after an earlier run of this script took it away)
apklayer('clear-data', PKG)
apklayer('stop')
assert code() == '1', 'version 1 of %s is not installed: %s' % (APP, apklayer('info', APP))
server = subprocess.Popen(['python3', '-m', 'http.server', str(port), '--bind', '127.0.0.1', '-d', str(W / 'srv')], stdout=subprocess.DEVNULL, stderr=(W / 'srv.log').open('w'))
app = uidrive.App(PKG, 420)
helpers = []
try:
    assert app.settle(90), 'F-Droid drew no frame'
    tap(323, 31)  # Repositories
    try: app.text('Got it', 8); tap(336, 501)  # the first start's hint (shown once per data)
    except AssertionError: pass
    tap(406, 108)  # the F-Droid repository's switch: off (its index is not fetched: offline, and its apps would hide ours)
    step('disable the default repository', 'Disable repository', 10)
    tap(322, 506)
    tap(388, 737)  # the add button
    step('add repository', 'Enter repository URL manually', 15)
    tap(224, 308)
    tap(224, 380)  # the URL field
    instance.inject(PKG, 'text:%s?fingerprint=%s' % (address, finger))
    app.settle(3)
    tap(394, 464)  # Add
    step('the repository was read and verified', 'Included apps:', 60)
    app.settle(5)
    tap(360, 184)  # Add repository
    if 'Got it' in app.until(lambda t: 'Got it' in t or LABEL in t, 'the repository\'s app list', 30): tap(369, 200)  # the filter hint (once per data), over the list
    step('the repository\'s app list', LABEL, 30)
    tap(200, 108)  # the app
    texts = [t for _, t in step('app page', 'Update', 30)]
    assert 'Open' in texts, 'the page of the installed app has no Open button: %s' % texts
    app.shot('fdroid-before-update')
    helpers = answer(2)  # "Allow F-Droid to install apps?" (only until the hub remembers the answer), "Update Hello Installer?"
    app.tap(330, 280)  # Update
    step('installed (the page shows no Update)', 'Open', 60)
    app.until(lambda t: 'Update' not in t, 'the end of the update (the page still shows "Update")', 60)
    assert helpers[0].wait(5) == 0, 'the hub asked no question during the update (hub.log: dialog shown)'
    assert code() == '2', 'after the update %s is at version code %s, not 2' % (APP, code())
    assert 'GET /repo/%s_2.apk' % APP in (W / 'srv.log').read_text(), 'F-Droid did not download the APK from the repository: ' + (W / 'srv.log').read_text()
    app.shot('fdroid-updated')
    # uninstall: the page's menu
    for h in helpers: h.kill()
    helpers = answer(1)
    tap(420, 32)  # more options
    app.shot('fdroid-menu')
    step('menu', 'Uninstall', 10)
    app.tap(300, 232)  # Uninstall: the hub asks "Uninstall Hello Installer?"
    assert helpers[0].wait(60) == 0, 'the hub asked no question about the uninstall (hub.log: dialog shown)'
    app.until(lambda t: 'Open' not in t, 'the end of the uninstall (the page still shows "Open")', 60)
    assert APP not in apklayer('list'), '%s is still installed after the uninstall' % APP
    app.shot('fdroid-uninstalled')
finally:
    for h in helpers: h.kill()
    app.stop()
    server.kill()
print('PASS F-Droid: offline repository added, Update installed version 2 of %s through the hub, Uninstall removed it' % APP)
