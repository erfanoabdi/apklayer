#!/usr/bin/env python3
"""Lomiri's notification server closes a popup when the connection that sent it leaves the bus (NotificationServer::serviceUnregistered), so a Notify
from a short-lived `gdbus call` vanished within 30 ms. This helper keeps one session-bus connection for the hub (DesktopNotifications) and answers lines on stdin:
  notify <GVariant text of (susssasa{sv}i)>   -> `uint32 <id>`
  close <id>                                  -> `ok`
(`error <why>` on a failure). Ends with its stdin."""
import sys
from gi.repository import Gio, GLib

bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
NAME, PATH = 'org.freedesktop.Notifications', '/org/freedesktop/Notifications'


def call(method, args):
    return bus.call_sync(NAME, PATH, NAME, method, args, None, Gio.DBusCallFlags.NONE, 5000, None)


for line in sys.stdin:
    verb, _, rest = line.rstrip('\n').partition(' ')
    try:
        if verb == 'notify':
            print('uint32 %d' % call('Notify', GLib.Variant.parse(GLib.VariantType('(susssasa{sv}i)'), rest)).unpack()[0], flush=True)
        elif verb == 'close':
            call('CloseNotification', GLib.Variant('(u)', (int(rest),)))
            print('ok', flush=True)
        else:
            print('error unknown ' + verb, flush=True)
    except (GLib.Error, ValueError) as e:
        print('error ' + str(e).replace('\n', ' '), flush=True)
