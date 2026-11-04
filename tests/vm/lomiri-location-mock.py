#!/usr/bin/env python3
"""A stand-in lomiri-location-service on $DBUS_SYSTEM_BUS_ADDRESS for tests/vm/gnss.sh (python-dbusmock cannot see a caller, which this
protocol needs): CreateSessionForCriteria(Criteria flattened, bbbbdbbb) -> /sessions/N; StartPositionUpdates there calls UpdatePosition back
on the caller at that path with one Update<Position> as dbus-cpp encodes it (lat, lon, Optional altitude: absent, Optional horizontal
accuracy: present, Optional vertical: absent, time). Usage: lomiri-location-mock.py LAT LON ACCURACY"""
import sys
from gi.repository import Gio, GLib

SERVICE = 'com.lomiri.location.Service'
lat, lon, accuracy = (float(v) for v in sys.argv[1:4])
XML = '''<node>
<interface name="com.lomiri.location.Service"><method name="CreateSessionForCriteria">
<arg type="b" direction="in"/><arg type="b" direction="in"/><arg type="b" direction="in"/><arg type="b" direction="in"/>
<arg type="d" direction="in"/><arg type="b" direction="in"/><arg type="b" direction="in"/><arg type="b" direction="in"/>
<arg type="o" direction="out"/></method></interface>
<interface name="com.lomiri.location.Service.Session"><method name="StartPositionUpdates"/><method name="StopPositionUpdates"/></interface>
</node>'''
info = Gio.DBusNodeInfo.new_for_xml(XML)
bus = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
sessions = []


def on_call(connection, sender, path, interface, method, parameters, invocation):
    if method == 'CreateSessionForCriteria':
        session = '/sessions/%d' % len(sessions)
        sessions.append(connection.register_object(session, info.interfaces[1], on_call, None, None))
        print('session %s for %s criteria %s' % (session, sender, parameters.unpack()), flush=True)
        invocation.return_value(GLib.Variant('(o)', (session,)))
        return
    invocation.return_value(None)
    if method == 'StartPositionUpdates':
        update = GLib.Variant('(ddbbdbx)', (lat, lon, False, True, accuracy, False, GLib.get_real_time() * 1000))
        connection.call(sender, path, SERVICE + '.Session', 'UpdatePosition', update, None, Gio.DBusCallFlags.NONE, 1000, None,
                        lambda c, result: print('UpdatePosition answered: %s' % (c.call_finish(result),), flush=True))


bus.register_object('/com/lomiri/location/Service', info.interfaces[0], on_call, None, None)
Gio.bus_own_name_on_connection(bus, SERVICE, Gio.BusNameOwnerFlags.NONE, None, None)
GLib.MainLoop().run()
