#!/usr/bin/env python3
"""Runs on the test VM in an instance where real Telegram FOSS (org.telegram.messenger, arm64 or through the native bridge) is installed: its intro screen
("Start Messaging", drawn with its own views) and, after a scripted tap on that button, the "Your Phone" page (country, phone number, the round
next button). No login: nothing is typed, and Telegram's own MTProto sockets only connect (no result is waited for). Checks the screenshots'
pixels (the intro is white with a blue button, the login page has no button at that place and a round blue button in its corner).
Usage (instance environment, repo root): python3 tests/corpus/telegram-intro.py. Files: instance.OUT/telegram-{intro,phone}.png."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import uidrive  # noqa: E402
from PIL import Image  # noqa: E402

PKG = 'org.telegram.messenger'
START = (0.5, 0.872)  # the "Start Messaging" button, as fractions of the window (223,695 of 448x797)
NEXT = (0.862, 0.947)  # the round next button of the phone page, left of its white arrow (386,755 of 448x797)


def at(image, frac):
    w, h = image.size
    return image.convert('RGB').getpixel((int(w * frac[0]), int(h * frac[1])))


def blue(rgb):
    r, g, b = rgb
    return b > 180 and b - r > 60 and g > 100  # Telegram's accent (84,169,235) on any background


app = uidrive.App(PKG, 120, dumps=False)
try:
    assert app.settle(90), 'Telegram drew no frame'
    intro = Image.open(app.shot('telegram-intro'))
    assert blue(at(intro, START)), 'no "Start Messaging" button on the intro screen: %s' % (at(intro, START),)
    assert not blue(at(intro, NEXT)), 'the phone page is up before the tap'
    app.tap(START[0] * intro.size[0], START[1] * intro.size[1])
    app.settle(30)
    phone = Image.open(app.shot('telegram-phone'))
    assert not blue(at(phone, START)), 'the intro button is still there after the tap'
    assert blue(at(phone, NEXT)), 'no next button on the phone page: %s' % (at(phone, NEXT),)
finally:
    app.stop()
print('PASS Telegram: intro screen -> Start Messaging -> the "Your Phone" page (country, number field, next button)')
