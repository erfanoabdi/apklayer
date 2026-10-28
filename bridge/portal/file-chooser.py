#!/usr/bin/env python3
"""xdg-desktop-portal FileChooser for the app host: one D-Bus connection both makes the request and receives the Response signal (a unicast signal to the caller).
Usage: file-chooser.py open|save|directory [--multiple] [--name NAME]. Prints `request <handle>`, then `cancelled` or one `uri <file-uri>` per chosen file."""
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
method = 'SaveFile' if mode == 'save' else 'OpenFile'
try:
    bus.call_sync('org.freedesktop.portal.Desktop', '/org/freedesktop/portal/desktop', 'org.freedesktop.portal.FileChooser', method,
                  GLib.Variant('(ssa{sv})', ('', 'apklayer', options)), None, Gio.DBusCallFlags.NONE, -1, None)
except GLib.Error as error:
    print('unavailable ' + error.message, flush=True)
    sys.exit(3)
print('request ' + handle, flush=True)
loop.run()
sys.exit(status[0])
