#!/usr/bin/env python3
"""xdg-desktop-portal FileChooser for the app host: one D-Bus connection both makes the request and receives the Response signal (a unicast signal to the caller).
Usage: file-chooser.py open|save|directory [--multiple] [--name NAME]. Prints `request <handle>`, then `cancelled` or one `uri <file-uri>` per chosen file.
Without a portal, `open` goes to Lomiri Content Hub (Ubuntu Touch) when it is on the bus: content-hub.qml beside this file, run by Qt 5's qmlscene.
`unavailable <why>` (status 3): neither."""
import os
import subprocess
import sys
from gi.repository import Gio, GLib

mode = sys.argv[1]
multiple = '--multiple' in sys.argv
name = sys.argv[sys.argv.index('--name') + 1] if '--name' in sys.argv else None
bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
loop = GLib.MainLoop()
status = [2]


def on_response(connection, sender, path, interface, signal, parameters, data):
    code, results = parameters.unpack()
    if code == 0:
        for uri in results.get('uris', []):
            print('uri ' + uri, flush=True)
        status[0] = 0
    else:
        print('cancelled', flush=True)
        status[0] = 1
    loop.quit()


options = {'handle_token': GLib.Variant('s', 'apklayer%d' % GLib.random_int_range(0, 1 << 30))}
if mode == 'directory': options['directory'] = GLib.Variant('b', True)
if multiple: options['multiple'] = GLib.Variant('b', True)
if mode == 'save' and name: options['current_name'] = GLib.Variant('s', name)
# subscribe before the call: the request path is predictable from our unique name and the token
sender = bus.get_unique_name()[1:].replace('.', '_')
handle = '/org/freedesktop/portal/desktop/request/%s/%s' % (sender, options['handle_token'].get_string())
bus.signal_subscribe('org.freedesktop.portal.Desktop', 'org.freedesktop.portal.Request', 'Response', handle, None, Gio.DBusSignalFlags.NONE, on_response, None)


def content_hub(bus):
    """Content Hub's peer picker for `open`; returns only when it is not there. Imports land in ~/.cache/$APP_ID/HubIncoming/<n>/ (the caller
    removes them): the requesting app is the one APP_ID names (lomiri-app-launch sets it; unconfined apps are trusted to say it)."""
    name = 'com.lomiri.content.dbus.Service'
    names = [bus.call_sync('org.freedesktop.DBus', '/org/freedesktop/DBus', 'org.freedesktop.DBus', m, None, None, Gio.DBusCallFlags.NONE, -1, None).unpack()[0]
             for m in ('ListNames', 'ListActivatableNames')]
    qmlscene = next((p for p in ('/usr/lib/qt5/bin/qmlscene', '/usr/lib/' + os.uname().machine + '-linux-gnu/qt5/bin/qmlscene') if os.access(p, os.X_OK)), None)
    if name not in names[0] + names[1] or not qmlscene:
        return
    env = dict(os.environ, QT_MESSAGE_PATTERN='%{message}', QML_DISABLE_DISK_CACHE='1')
    env.setdefault('APP_ID', 'apklayer-manager')  # a terminal's apklayer: the manager's identity (its desktop file is a Lomiri app)
    if 'QT_QPA_PLATFORM' not in env: env['QT_QPA_PLATFORM'] = 'ubuntumirclient;wayland-egl;xcb'
    qml = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'content-hub.qml')
    run = subprocess.run([qmlscene, qml] + (['multiple'] if multiple else []), env=env, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                         stderr=subprocess.PIPE, text=True)
    lines = [l for l in run.stderr.splitlines() if l.startswith('uri ') or l == 'cancelled']
    if not lines:
        return
    print('request content-hub', flush=True)
    for l in lines:
        print(l, flush=True)
    sys.exit(1 if lines == ['cancelled'] else 0)


method = 'SaveFile' if mode == 'save' else 'OpenFile'
try:
    bus.call_sync('org.freedesktop.portal.Desktop', '/org/freedesktop/portal/desktop', 'org.freedesktop.portal.FileChooser', method,
                  GLib.Variant('(ssa{sv})', ('', 'apklayer', options)), None, Gio.DBusCallFlags.NONE, -1, None)
except GLib.Error as error:
    if mode == 'open':
        content_hub(bus)
    print('unavailable ' + error.message, flush=True)
    sys.exit(3)
print('request ' + handle, flush=True)
loop.run()
sys.exit(status[0])
