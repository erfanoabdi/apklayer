"""Method bodies for the bluetooth group's mock BlueZ (tests/vm/bluez-mock.py), run inside python-dbusmock (its methods exec this file).
connect_profile: Device1.ConnectProfile of the classic device as BlueZ does it, the registered profile's NewConnection with one end of a socketpair
standing in for the RFCOMM socket; the other end echoes what it reads, prefixed "echo:". pair: Device1.Pair of a legacy device that asks the agent
for its PIN (RequestPinCode) and pairs only with 1234."""
import re
import socket
import threading

import dbus


def client(bus, path):
    """The bus name that serves `path`: bridge/portal/bluez.py names its objects after its pid (/apklayer/rfcomm<pid>, /apklayer/agent<pid>)."""
    pid = int(re.search(r'(\d+)$', path).group(1))
    daemon = dbus.Interface(bus.get_object('org.freedesktop.DBus', '/org/freedesktop/DBus'), 'org.freedesktop.DBus')
    return next(n for n in daemon.ListNames() if n.startswith(':') and daemon.GetConnectionUnixProcessID(n) == pid)


def connect_profile(device, uuid):
    profile = getattr(objects['/org/bluez'], 'profiles', {}).get(uuid.lower())
    if profile is None:
        raise dbus.exceptions.DBusException('no profile for ' + uuid, name='org.bluez.Error.NotAvailable')
    ours, theirs = socket.socketpair()

    def echo():
        while data := ours.recv(4096):
            ours.sendall(b'echo:' + data)
        ours.close()
    threading.Thread(target=echo, daemon=True).start()
    dbus.Interface(device.connection.get_object(client(device.connection, profile), profile), 'org.bluez.Profile1').NewConnection(
        dbus.ObjectPath(device.__dbus_object_path__), dbus.types.UnixFd(theirs), dbus.Dictionary({}, signature='sv'))
    theirs.close()


def pair(device):
    agents = objects['/org/bluez'].agent_paths
    if not agents:
        raise dbus.exceptions.DBusException('no agent', name='org.bluez.Error.AuthenticationFailed')
    agent = agents[-1]
    pin = dbus.Interface(device.connection.get_object(client(device.connection, agent), agent), 'org.bluez.Agent1').RequestPinCode(
        dbus.ObjectPath(device.__dbus_object_path__), timeout=120)
    if pin != '1234':
        raise dbus.exceptions.DBusException('wrong PIN ' + pin, name='org.bluez.Error.AuthenticationFailed')
    device.PairDevice('hci0', device.props['org.bluez.Device1']['Address'])
