#!/usr/bin/env python3
"""The Flatpak's app launchers through the dynamic launcher portal (org.freedesktop.portal.DynamicLauncher; cli/desktop.cpp), in place of files
in the host's ~/.local/share/applications. Usage:
  launcher.py install <id.desktop> <name> <icon.png> <desktop entry>  the desktop asks the person once (PrepareInstall); an installed one is kept
  launcher.py uninstall <id.desktop>                                   (gone already: nothing to do)
Prints `installed`, `kept`, `cancelled`, `uninstalled` or `unavailable <why>` (status 3)."""
import sys
from gi.repository import Gio, GLib

bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
NAME, PATH, IFACE = 'org.freedesktop.portal.Desktop', '/org/freedesktop/portal/desktop', 'org.freedesktop.portal.DynamicLauncher'


def call(method, args, reply=None):
    return bus.call_sync(NAME, PATH, IFACE, method, args, GLib.VariantType(reply) if reply else None, Gio.DBusCallFlags.NONE, -1, None)


def installed(desktop_id):
    try:
        call('GetDesktopEntry', GLib.Variant('(s)', (desktop_id,)), '(s)')
        return True
    except GLib.Error:
        return False


def prepare(name, icon):
    """PrepareInstall's token, or None: the person said no"""
    loop, result = GLib.MainLoop(), {}
    token = 'apklayer%d' % GLib.random_int_range(0, 1 << 30)
    # subscribe before the call: the request path is predictable from our unique name and the token (as file-chooser.py)
    handle = '/org/freedesktop/portal/desktop/request/%s/%s' % (bus.get_unique_name()[1:].replace('.', '_'), token)

    def on_response(connection, sender, path, interface, signal, parameters, data):
        code, results = parameters.unpack()
        result['token'] = results.get('token') if code == 0 else None
        loop.quit()

    bus.signal_subscribe(NAME, 'org.freedesktop.portal.Request', 'Response', handle, None, Gio.DBusSignalFlags.NONE, on_response, None)
    icon_v = Gio.BytesIcon.new(GLib.Bytes.new(open(icon, 'rb').read())).serialize()  # a square PNG of at most 512 px (AppMetadata: 256)
    call('PrepareInstall', GLib.Variant('(ssva{sv})', ('', name, icon_v, {'handle_token': GLib.Variant('s', token)})))
    loop.run()
    return result['token']


def main():
    try:
        bus.call_sync(NAME, PATH, 'org.freedesktop.DBus.Properties', 'Get', GLib.Variant('(ss)', (IFACE, 'version')), None, Gio.DBusCallFlags.NONE, -1, None)
    except GLib.Error as e:
        print('unavailable ' + e.message, flush=True)
        return 3
    if sys.argv[1] == 'uninstall':
        if installed(sys.argv[2]): call('Uninstall', GLib.Variant('(sa{sv})', (sys.argv[2], {})))
        print('uninstalled', flush=True)
        return 0
    desktop_id, name, icon, entry = sys.argv[2:6]
    if installed(desktop_id):  # shortcut: an app update's new label or icon does not reach a launcher already there (each Install asks the person)
        print('kept', flush=True)
        return 0
    token = prepare(name, icon)
    if not token:
        print('cancelled', flush=True)
        return 1
    call('Install', GLib.Variant('(sssa{sv})', (token, desktop_id, entry, {})))
    print('installed', flush=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
