#!/usr/bin/env python3
"""GeoClue for the app host: the client lives as long as this process's D-Bus connection, so one process creates it, starts it and prints what it learns.
Prints `unavailable <why>` and exits 3 without GeoClue; otherwise `ready`, then `location <latitude> <longitude> <accuracy-metres>` per update."""
import sys
from gi.repository import Gio, GLib

bus = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
GEOCLUE = 'org.freedesktop.GeoClue2'


def call(path, interface, method, args, signature, name=GEOCLUE):
    return bus.call_sync(name, path, interface, method, GLib.Variant(signature, args) if signature else None, None, Gio.DBusCallFlags.NONE, -1, None)


try:
    client = call('/org/freedesktop/GeoClue2/Manager', GEOCLUE + '.Manager', 'GetClient', None, None).unpack()[0]
    call(client, 'org.freedesktop.DBus.Properties', 'Set', (GEOCLUE + '.Client', 'DesktopId', GLib.Variant('s', 'apklayer')), '(ssv)')
    call(client, 'org.freedesktop.DBus.Properties', 'Set', (GEOCLUE + '.Client', 'RequestedAccuracyLevel', GLib.Variant('u', 8)), '(ssv)')
except GLib.Error as error:
    print('unavailable ' + error.message, flush=True)
    sys.exit(3)


def on_update(connection, sender, path, interface, signal, parameters, data):
    location = parameters.unpack()[1]
    values = call(location, 'org.freedesktop.DBus.Properties', 'GetAll', (GEOCLUE + '.Location',), '(s)').unpack()[0]
    print('location %r %r %r' % (values['Latitude'], values['Longitude'], values['Accuracy']), flush=True)


bus.signal_subscribe(GEOCLUE, GEOCLUE + '.Client', 'LocationUpdated', client, None, Gio.DBusSignalFlags.NONE, on_update, None)
try:
    call(client, GEOCLUE + '.Client', 'Start', None, None)
except GLib.Error as error:
    print('unavailable ' + error.message, flush=True)  # e.g. no agent authorised this client
    sys.exit(3)
print('ready', flush=True)
GLib.MainLoop().run()
