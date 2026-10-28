#!/usr/bin/env python3
"""Reads the framework's AndroidManifest.xml on stdin and prints `name<TAB>level` for every permission, level = normal | dangerous | appop | signature (appop: the user grants it in Settings on a device, so the desktop grants it at install; signature covers
signature|privileged and the like). Staged as android-root/system/etc/apklayer-permission-levels.tsv by scripts/collect-android-root.sh: normal permissions are granted
at install, signature ones go to platform-signed system apps, dangerous ones are asked for."""
import sys
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
for p in sorted(ET.parse(sys.stdin).getroot().iter('permission'), key=lambda p: p.get(ANDROID + 'name') or ''):
    level = p.get(ANDROID + 'protectionLevel') or 'normal'
    print('%s\t%s' % (p.get(ANDROID + 'name'), 'appop' if 'appop' in level else 'signature' if 'signature' in level else 'dangerous' if 'dangerous' in level else 'normal'))
