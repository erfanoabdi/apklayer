"""Method bodies for the bluetooth group's mock BlueZ (tests/vm/bluez-mock.py), run inside python-dbusmock (its methods exec this file).
connect_profile: Device1.ConnectProfile of the classic device as BlueZ does it, the registered client profile's NewConnection with one end of a
socketpair standing in for the RFCOMM socket; the other end echoes what it reads, prefixed "echo:", or, when an app registered the service as a server
(RegisterProfile Role=server), goes to that profile's NewConnection: the app's server socket talks to its own client socket. pair: Device1.Pair of a
device that asks the agent (RequestPinCode, RequestConfirmation) and pairs only with the expected answer."""
import re
import socket
import threading

import dbus


def client(bus, path):
    """The bus name that serves `path`: bridge/portal/bluez.py names its objects after its pid (/apklayer/rfcomm<pid>, /apklayer/agent<pid>)."""
    pid = int(re.search(r'(\d+)$', path).group(1))
    daemon = dbus.Interface(bus.get_object('org.freedesktop.DBus', '/org/freedesktop/DBus'), 'org.freedesktop.DBus')
    return next(n for n in daemon.ListNames() if n.startswith(':') and daemon.GetConnectionUnixProcessID(n) == pid)


def new_connection(device, profile, end):
    dbus.Interface(device.connection.get_object(client(device.connection, profile), profile), 'org.bluez.Profile1').NewConnection(
        dbus.ObjectPath(device.__dbus_object_path__), dbus.types.UnixFd(end), dbus.Dictionary({}, signature='sv'))
    end.close()


def connect_profile(device, uuid):
    profiles = getattr(objects['/org/bluez'], 'profiles', {})
    profile, server = profiles.get((uuid.lower(), 'client')), profiles.get((uuid.lower(), 'server'))
    if profile is None:
        raise dbus.exceptions.DBusException('no profile for ' + uuid, name='org.bluez.Error.NotAvailable')
    ours, theirs = socket.socketpair()
    if server is not None:
        new_connection(device, server, ours)
        new_connection(device, profile, theirs)
        return

    def echo():
        while data := ours.recv(4096):
            ours.sendall(b'echo:' + data)
        ours.close()
    threading.Thread(target=echo, daemon=True).start()
    new_connection(device, profile, theirs)


def pair(device, method='RequestPinCode', answer='1234'):
    agents = objects['/org/bluez'].agent_paths
    if not agents:
        raise dbus.exceptions.DBusException('no agent', name='org.bluez.Error.AuthenticationFailed')
    agent = agents[-1]
    asked = getattr(dbus.Interface(device.connection.get_object(client(device.connection, agent), agent), 'org.bluez.Agent1'), method)
    try:  # a comparison is accepted by a plain return
        pin = asked(dbus.ObjectPath(device.__dbus_object_path__), timeout=120) if method == 'RequestPinCode' else \
            asked(dbus.ObjectPath(device.__dbus_object_path__), dbus.UInt32(int(answer)), timeout=120) or answer
    except dbus.exceptions.DBusException as refused:
        raise dbus.exceptions.DBusException('refused: ' + str(refused), name='org.bluez.Error.AuthenticationFailed')
    if pin != answer:
        raise dbus.exceptions.DBusException('wrong PIN ' + pin, name='org.bluez.Error.AuthenticationFailed')
    device.PairDevice('hci0', device.props['org.bluez.Device1']['Address'])
