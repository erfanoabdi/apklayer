#!/usr/bin/env python3
"""Runs on the test VM in an instance where real Aurora Store (com.aurora.store 4.8.x) is installed and the instance has network: Aurora's first
start (data cleared) -> Welcome -> Next -> Permissions (Grant "install apps": the hub's question is answered yes) -> Finish -> login page ->
Anonymous (HTTPS to Aurora's token service and Google Play) -> the home tabs -> the search button -> typing a query -> the result list. The
screen's texts come from the host's `views` step (tests/corpus/uidrive.py): fails naming the step and what the screen showed instead. This is
the only test that reaches Google: a failing anonymous login (blocked token, no network) fails it at "Anonymous".
Usage (instance environment, repo root): python3 tests/corpus/aurora-search.py. Files: instance.OUT/aurora-*.png, com.aurora.store.out."""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import uidrive  # noqa: E402
from PIL import Image  # noqa: E402
from uidrive import instance  # noqa: E402

PKG, QUERY, TITLE, DEVELOPER = 'com.aurora.store', 'Telegram', 'Telegram', 'Telegram FZ-LLC'  # a result of Google Play's list (F-Droid is not on Play)


def step(name, text, seconds=60):
    print('STEP', name, flush=True)
    try: return app.text(text, seconds)
    except AssertionError as e:
        app.shot('aurora-failed')
        raise AssertionError('Aurora step "%s" failed: %s' % (name, e))


def tap(x, y):
    """x, y: pixels of the 448x797 window these buttons were read from, scaled to the window the host made."""
    w, h = Image.open(app.shot('aurora-size')).size
    app.tap(x * w / 448, y * h / 797)


subprocess.run(['bin/apklayer', 'clear-data', PKG], env=instance.ENV, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
app = uidrive.App(PKG, 240)
try:
    step('first screen', 'Welcome', 120)
    tap(333, 761)  # Next
    texts = [t for _, t in step('onboarding permissions', 'Permissions')]
    if texts[texts.index('Installer permission') + 2] == 'Grant':  # (a hub that answered yes before shows "Granted")
        tap(392, 220)  # Grant "Installer permission": the hub asks whether Aurora may install apps
        app.settle(15)
        instance.inject('apklayer.installer', 'click:369,166')  # its yes button (hub.log: dialog shown ... yes=369,166)
        app.settle(10)
    tap(333, 761)  # Finish
    step('login page', 'Anonymous')
    tap(224, 584)  # Anonymous
    step('anonymous login (the home page)', 'Top charts', 90)
    app.settle(30)
    tap(404, 673)  # the search button
    app.settle(10)
    tap(240, 32)  # the search field
    app.settle(5)
    instance.inject(PKG, 'text:' + QUERY, 'key:28')
    screen = step('search results', DEVELOPER, 90)
    app.shot('aurora-results')
    titles = [t for cls, t in screen if 'EditText' not in cls and 'SearchView' not in cls]
    assert TITLE in titles and DEVELOPER in titles, 'the result list lacks %r by %r: %s' % (TITLE, DEVELOPER, titles)
finally:
    app.stop()
print('PASS Aurora Store: onboarding, anonymous login over HTTPS, search "%s" lists %s (%s); %d texts on the result screen' % (QUERY, TITLE, DEVELOPER, len(titles)))
