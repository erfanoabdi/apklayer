#!/usr/bin/env python3
"""The desktop's settings for the instance hub (HubHostSettings): the xdg Settings portal (org.freedesktop.appearance, and GNOME's
org.gnome.desktop.interface through it), the system time zone (timedate1) and the session locale. Prints `<key> <value>` lines: every
known setting, then `ready`, then one line per change (the portal's SettingChanged, timedate1's PropertiesChanged).
Keys: color-scheme (0 none, 1 dark, 2 light), accent-color (r,g,b in 0..1), contrast (0, 1 high), reduced-motion (0, 1 reduced),
enable-animations (true|false), text-scaling-factor, clock-format (12h|24h), timezone, locale (the session's LANG)."""
import os
from gi.repository import Gio, GLib

PORTAL, PATH, SETTINGS = 'org.freedesktop.portal.Desktop', '/org/freedesktop/portal/desktop', 'org.freedesktop.portal.Settings'
KEYS = {('org.freedesktop.appearance', k) for k in ('color-scheme', 'accent-color', 'contrast', 'reduced-motion')} | \
       {('org.gnome.desktop.interface', k) for k in ('enable-animations', 'text-scaling-factor', 'clock-format')}


def show(key, value):
    if isinstance(value, bool):
        value = 'true' if value else 'false'
    elif isinstance(value, tuple):
        value = ','.join('%.4f' % c for c in value)
    print(key, value, flush=True)


def on_setting(connection, sender, path, interface, signal, parameters, data):
    namespace, key, value = parameters.unpack()
    if (namespace, key) in KEYS:
        show(key, value)


def on_timedate(connection, sender, path, interface, signal, parameters, data):
    changed = parameters.unpack()[1]
    if 'Timezone' in changed:
        show('timezone', changed['Timezone'])


session = Gio.bus_get_sync(Gio.BusType.SESSION, None)
session.signal_subscribe(PORTAL, SETTINGS, 'SettingChanged', PATH, None, Gio.DBusSignalFlags.NONE, on_setting, None)
try:
    every = session.call_sync(PORTAL, PATH, SETTINGS, 'ReadAll', GLib.Variant('(as)', (sorted({n for n, _ in KEYS}),)),
                              None, Gio.DBusCallFlags.NONE, 5000, None).unpack()[0]
    for namespace, values in every.items():
        for key, value in values.items():
            if (namespace, key) in KEYS:
                show(key, value)
except GLib.Error as error:
    print('unavailable portal ' + error.message, flush=True)  # no portal: the system's settings stay as they are
try:
    system = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
    system.signal_subscribe('org.freedesktop.timedate1', 'org.freedesktop.DBus.Properties', 'PropertiesChanged', '/org/freedesktop/timedate1',
                            None, Gio.DBusSignalFlags.NONE, on_timedate, None)
    zone = system.call_sync('org.freedesktop.timedate1', '/org/freedesktop/timedate1', 'org.freedesktop.DBus.Properties', 'Get',
                            GLib.Variant('(ss)', ('org.freedesktop.timedate1', 'Timezone')), None, Gio.DBusCallFlags.NONE, 5000, None)
    show('timezone', zone.unpack()[0])
except GLib.Error as error:
    print('unavailable timedate1 ' + error.message, flush=True)
locale = os.environ.get('LC_ALL') or os.environ.get('LC_MESSAGES') or os.environ.get('LANG')
if locale:
    show('locale', locale)
print('ready', flush=True)
GLib.MainLoop().run()
