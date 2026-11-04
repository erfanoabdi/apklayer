#!/usr/bin/env python3
"""A stand-in lomiri-location-service on $DBUS_SYSTEM_BUS_ADDRESS for tests/vm/gnss.sh (python-dbusmock cannot see a caller, which this
protocol needs): CreateSessionForCriteria(Criteria flattened, bbbbdbbb) -> /sessions/N; StartPositionUpdates there calls UpdatePosition back
on the caller at that path with one Update<Position> as dbus-cpp encodes it (lat, lon, then the Optionals altitude, horizontal and vertical
accuracy, each a boolean and the value when true, then the time), then UpdateVelocity and UpdateHeading. Usage: lomiri-location-mock.py LAT LON ACCURACY"""
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
        now = GLib.get_real_time() * 1000
        # as the Ubuntu Touch phone's service sends them: altitude and horizontal accuracy present, then velocity and heading unasked
        for member, update in (('UpdatePosition', GLib.Variant('(ddbdbdbx)', (lat, lon, True, 35.0, True, accuracy, False, now))),
                               ('UpdateVelocity', GLib.Variant('(dx)', (1.5, now))), ('UpdateHeading', GLib.Variant('(dx)', (90.0, now)))):
            connection.call(sender, path, SERVICE + '.Session', member, update, None, Gio.DBusCallFlags.NONE, 1000, None,
                            lambda c, result, m=member: print('%s answered: %s' % (m, c.call_finish(result)), flush=True))


bus.register_object('/com/lomiri/location/Service', info.interfaces[0], on_call, None, None)
Gio.bus_own_name_on_connection(bus, SERVICE, Gio.BusNameOwnerFlags.NONE, None, None)
GLib.MainLoop().run()
