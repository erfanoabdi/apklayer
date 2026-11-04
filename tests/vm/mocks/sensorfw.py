"""python-dbusmock template: sensorfw (com.nokia.SensorService, system bus) and its data socket ($SENSORFW_SOCKET_PATH/var/run/sensord.sock)
for tests/vm/providers-host.sh: an accelerometer at 100 Hz (500, 1000, -250 mG), a light sensor (321 lux) and proximity (near), as the
daemon's frames (a uint32 count, then its sample structs). The other plugins do not load, as on a device without them."""
import os
import socket
import struct
import threading
import time

BUS_NAME = 'com.nokia.SensorService'
MAIN_OBJ = '/SensorManager'
MAIN_IFACE = 'local.SensorManager'
SYSTEM_BUS = True

SESSIONS = {'accelerometersensor': 1, 'alssensor': 2, 'proximitysensor': 3}
IFACES = {'accelerometersensor': 'local.AccelerometerSensor', 'alssensor': 'local.ALSSensor', 'proximitysensor': 'local.ProximitySensor'}
RANGES = {'accelerometersensor': (0.0, 8000.0, 1.0), 'alssensor': (0.0, 65535.0, 1.0), 'proximitysensor': (0.0, 1.0, 1.0)}


def frame(session):
    now = int(time.monotonic() * 1e6)
    if session == 1:  # TimedXyzData: timestamp, x, y, z (mG), padding
        return struct.pack('<IQiii4x', 1, now, 500, 1000, -250)
    if session == 2:  # TimedUnsigned (lux)
        return struct.pack('<IQI4x', 1, now, 321)
    return struct.pack('<IQIB3x', 1, now, 0, 1)  # ProximityData: value, withinProximity


def serve(connection):
    with connection:
        try:
            session = struct.unpack('<i', connection.recv(4, socket.MSG_WAITALL))[0]
            connection.sendall(b'\n')
            connection.sendall(frame(session))
            while session == 1:  # the on-change sensors send once
                time.sleep(0.01)
                connection.sendall(frame(session))
            connection.recv(1)  # until the client goes
        except OSError:
            pass


def listen():
    path = os.environ['SENSORFW_SOCKET_PATH'] + '/var/run/sensord.sock'
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path):
        os.unlink(path)
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(path)
    server.listen()
    while True:
        threading.Thread(target=serve, args=(server.accept()[0],), daemon=True).start()


def load(mock, parameters):
    mock.AddMethods(MAIN_IFACE, [
        ('availableSensorPlugins', '', 'as', 'ret = ["accelerometersensor", "alssensor", "proximitysensor", "rotationsensor"]'),
        ('loadPlugin', 's', 'b', 'ret = args[0] in %r' % list(SESSIONS)),
        ('requestSensor', 'sx', 'i', 'ret = %r.get(args[0], -1)' % SESSIONS),
        ('releaseSensor', 'six', 'b', 'ret = True')])
    for name, iface in IFACES.items():
        mock.AddObject(MAIN_OBJ + '/' + name, iface, {}, [
            ('getCurrentDataRange', '', '(ddd)', 'ret = dbus.Struct(%r, signature="ddd")' % (RANGES[name],)),
            ('getAvailableIntervals', '', 'a(ddd)', 'ret = [dbus.Struct((5.0, 1000.0, 0.0), signature="ddd")]'),
            ('setInterval', 'ii', '', ''), ('start', 'i', '', ''), ('stop', 'i', '', '')])
    threading.Thread(target=listen, daemon=True).start()
