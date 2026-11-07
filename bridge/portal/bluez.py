#!/usr/bin/env python3
"""BlueZ (org.bluez on the system bus) for one app's Bluetooth session, started by the hub (HubBluetooth) with stdin/stdout handed to the app (HostBluetooth).
Speaks JSON lines: commands {"op": ...} on stdin, events {"ev": ...} on stdout. Ends when stdin closes (BlueZ then stops this client's discovery and notifications).
`bluez.py rfcomm <address> <uuid>`: an RFCOMM connection to the device's service through BlueZ's Profile API; prints `OK` or `ERR <why>` on its own line, then
relays the connection's bytes between stdin/stdout and the socket."""
import json
import os
import sys
import threading
import warnings
from gi.repository import Gio, GLib

BLUEZ = 'org.bluez'
ADAPTER, DEVICE, SERVICE, CHAR, DESC = (BLUEZ + '.' + n for n in ('Adapter1', 'Device1', 'GattService1', 'GattCharacteristic1', 'GattDescriptor1'))
PROPS = 'org.freedesktop.DBus.Properties'
warnings.simplefilter('ignore', DeprecationWarning)  # Gio's register_object (the closure form): the one PyGObject call that takes a handler
bus = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
objects = {}  # path -> {interface: {property: python value}}, as GetManagedObjects and the signals tell
loop = GLib.MainLoop()


def emit(**event):
    sys.stdout.write(json.dumps(event) + '\n')
    sys.stdout.flush()


def call(path, interface, method, args=None, signature=None, done=None):
    """A BlueZ method call; with `done`, asynchronous: done(result or None, error message or None)."""
    params = GLib.Variant(signature, args) if signature else None
    if done is None:
        return bus.call_sync(BLUEZ, path, interface, method, params, None, Gio.DBusCallFlags.NONE, -1, None).unpack()

    def finished(source, result):
        try:
            done(source.call_finish(result).unpack(), None)
        except GLib.Error as error:
            done(None, error.message)
    bus.call(BLUEZ, path, interface, method, params, None, Gio.DBusCallFlags.NONE, 60000, None, finished)


def adapter():
    return next((path for path, ifaces in sorted(objects.items()) if ADAPTER in ifaces), None)


def device_path(address):
    return next((path for path, ifaces in objects.items() if ifaces.get(DEVICE, {}).get('Address', '').upper() == address.upper()), None)


def hexbytes(value):
    return bytes(value).hex()


def device(path):
    p = objects[path][DEVICE]
    advertised = bool(p.get('ManufacturerData') or p.get('ServiceData') or p.get('AdvertisingFlags'))
    kind = 'le' if p.get('AddressType') == 'random' or 'Class' not in p else 'dual' if advertised else 'bredr'
    return {'address': p.get('Address'), 'name': p.get('Name'), 'alias': p.get('Alias'), 'type': kind, 'rssi': p.get('RSSI'), 'tx': p.get('TxPower'),
            'uuids': p.get('UUIDs', []), 'paired': p.get('Paired', False), 'connected': p.get('Connected', False), 'class': p.get('Class'),
            'mfr': {str(k): hexbytes(v) for k, v in dict(p.get('ManufacturerData') or {}).items()},
            'sdata': {k: hexbytes(v) for k, v in dict(p.get('ServiceData') or {}).items()}}


def emit_adapter():
    path = adapter()
    if path is None:
        emit(ev='adapter', present=False)
        return
    p = objects[path][ADAPTER]
    bonded = [device(d) for d, ifaces in objects.items() if ifaces.get(DEVICE, {}).get('Paired') and d.startswith(path + '/')]
    emit(ev='adapter', present=True, powered=p.get('Powered', False), name=p.get('Alias') or p.get('Name'), address=p.get('Address'),
         discovering=p.get('Discovering', False), bonded=bonded)


def services(address):
    """The device's GATT database in handle order, as BlueZ resolved it (ServicesResolved)."""
    root = device_path(address)
    tree = []
    for path in sorted(objects):
        ifaces = objects[path]
        if not path.startswith(root + '/'):
            continue
        if SERVICE in ifaces:
            tree.append({'uuid': ifaces[SERVICE]['UUID'], 'path': path, 'primary': ifaces[SERVICE].get('Primary', True), 'chars': []})
        elif CHAR in ifaces and tree:
            tree[-1]['chars'].append({'uuid': ifaces[CHAR]['UUID'], 'path': path, 'flags': ifaces[CHAR].get('Flags', []), 'descs': []})
        elif DESC in ifaces and tree and tree[-1]['chars']:
            tree[-1]['chars'][-1]['descs'].append({'uuid': ifaces[DESC]['UUID'], 'path': path})
    emit(ev='services', address=address, services=tree)


scanning = False
notifying = set()
waiting_services = set()


def on_properties(path, interface, changed):
    known = objects.setdefault(path, {}).setdefault(interface, {})
    known.update(changed)
    if interface == ADAPTER:
        emit_adapter()
    elif interface == DEVICE:
        address = known.get('Address')
        if scanning and ({'RSSI', 'ManufacturerData', 'ServiceData'} & changed.keys()):
            emit(ev='found', device=device(path))
        if 'Connected' in changed:
            emit(ev='conn', address=address, connected=changed['Connected'], status=0)
        if 'Paired' in changed:
            emit(ev='bond', address=address, state=12 if changed['Paired'] else 10)
            emit_adapter()
        if changed.get('ServicesResolved') and address in waiting_services:
            waiting_services.discard(address)
            services(address)
    elif interface == CHAR and 'Value' in changed and path in notifying:
        emit(ev='changed', path=path, value=hexbytes(changed['Value']))


def on_signal(connection, sender, path, interface, signal, parameters, data):
    args = parameters.unpack()
    if signal == 'PropertiesChanged':
        on_properties(path, args[0], args[1])
    elif signal == 'InterfacesAdded':
        objects.setdefault(args[0], {}).update(args[1])
        if DEVICE in args[1] and scanning:
            emit(ev='found', device=device(args[0]))
        if ADAPTER in args[1]:
            emit_adapter()
    elif signal == 'InterfacesRemoved':
        for interface in args[1]:
            objects.get(args[0], {}).pop(interface, None)
        if ADAPTER in args[1]:
            emit_adapter()


def status(error):
    return 0 if error is None else 257  # GATT_FAILURE


AGENT_XML = '''<node><interface name="org.bluez.Agent1"><method name="Release"/><method name="Cancel"/>
<method name="RequestPinCode"><arg type="o" direction="in"/><arg type="s" direction="out"/></method>
<method name="DisplayPinCode"><arg type="o" direction="in"/><arg type="s" direction="in"/></method>
<method name="RequestPasskey"><arg type="o" direction="in"/><arg type="u" direction="out"/></method>
<method name="DisplayPasskey"><arg type="o" direction="in"/><arg type="u" direction="in"/><arg type="q" direction="in"/></method>
<method name="RequestConfirmation"><arg type="o" direction="in"/><arg type="u" direction="in"/></method>
<method name="RequestAuthorization"><arg type="o" direction="in"/></method>
<method name="AuthorizeService"><arg type="o" direction="in"/><arg type="s" direction="in"/></method></interface></node>'''
agent = None


def on_agent(connection, sender, path, interface, method, parameters, invocation):
    # shortcut: the app asked to bond, so confirmations are accepted (Android would show its pairing dialog); PIN and passkey entry are refused: needs a dialog
    if method in ('RequestPinCode', 'RequestPasskey'):
        invocation.return_dbus_error('org.bluez.Error.Rejected', 'no PIN entry in apklayer yet')
    else:
        invocation.return_value(None)


def ensure_agent():
    global agent
    if agent is None:
        agent = '/apklayer/agent%d' % os.getpid()
        bus.register_object(agent, Gio.DBusNodeInfo.new_for_xml(AGENT_XML).interfaces[0], on_agent, None, None)
        call('/org/bluez', BLUEZ + '.AgentManager1', 'RegisterAgent', (agent, 'NoInputNoOutput'), '(os)')


def command(line):
    global scanning
    c = json.loads(line)
    op, address, path = c.get('op'), c.get('address'), c.get('path')
    if op == 'adapter':
        emit_adapter()
    elif op == 'power':
        call(adapter(), PROPS, 'Set', (ADAPTER, 'Powered', GLib.Variant('b', c['on'])), '(ssv)', lambda r, e: e and emit(ev='error', op=op, message=e))
    elif op == 'scan':
        scan_filter = {'Transport': GLib.Variant('s', c.get('transport', 'auto')), 'DuplicateData': GLib.Variant('b', True)}
        if c.get('uuids'):
            scan_filter['UUIDs'] = GLib.Variant('as', c['uuids'])
        try:
            call(adapter(), ADAPTER, 'SetDiscoveryFilter', (scan_filter,), '(a{sv})')
            call(adapter(), ADAPTER, 'StartDiscovery')
        except GLib.Error as error:
            emit(ev='scan', ok=False, message=error.message)
            return
        scanning = True
        emit(ev='scan', ok=True)
        for p, ifaces in list(objects.items()):  # what BlueZ saw recently (it keeps RSSI only for devices it currently hears)
            if DEVICE in ifaces and ifaces[DEVICE].get('RSSI') is not None:
                emit(ev='found', device=device(p))
    elif op == 'stopscan':
        scanning = False
        call(adapter(), ADAPTER, 'StopDiscovery', done=lambda r, e: None)
    elif op == 'connect':
        target = device_path(address)
        if target is None:
            emit(ev='conn', address=address, connected=False, status=133)
            return
        if objects[target][DEVICE].get('Connected'):  # BlueZ says AlreadyConnected: the app's connection is there
            emit(ev='conn', address=address, connected=True, status=0)
            return
        call(target, DEVICE, 'Connect', done=lambda r, e: e and emit(ev='conn', address=address, connected=False, status=133, message=e))
    elif op == 'disconnect':
        target = device_path(address)
        if target:
            call(target, DEVICE, 'Disconnect', done=lambda r, e: e and emit(ev='conn', address=address, connected=False, status=0))
    elif op == 'services':
        target = device_path(address)
        if target and objects[target][DEVICE].get('ServicesResolved'):
            services(address)
        else:
            waiting_services.add(address)
    elif op == 'read':
        call(path, DESC if DESC in objects.get(path, {}) else CHAR, 'ReadValue', ({},), '(a{sv})',
             lambda r, e: emit(ev='read', path=path, status=status(e), value=hexbytes(r[0]) if r else ''))
    elif op == 'write':
        options = {} if DESC in objects.get(path, {}) else {'type': GLib.Variant('s', c.get('type', 'request'))}
        call(path, DESC if DESC in objects.get(path, {}) else CHAR, 'WriteValue', (list(bytes.fromhex(c['value'])), options), '(aya{sv})',
             lambda r, e: emit(ev='write', path=path, status=status(e)))
    elif op == 'notify':
        (notifying.add if c['on'] else notifying.discard)(path)
        call(path, CHAR, 'StartNotify' if c['on'] else 'StopNotify', done=lambda r, e: emit(ev='notify', path=path, status=status(e)))
    elif op == 'bond':
        target = device_path(address)
        if target is None:
            emit(ev='bond', address=address, state=10)
            return
        ensure_agent()
        emit(ev='bond', address=address, state=11)
        call(target, DEVICE, 'Pair', done=lambda r, e: e and emit(ev='bond', address=address, state=10, message=e))
    elif op == 'unbond':
        target = device_path(address)
        if target:
            call(adapter(), ADAPTER, 'RemoveDevice', (target,), '(o)', lambda r, e: None)


def on_input(channel, condition):
    line = channel.readline()
    if not line:
        loop.quit()
        return False
    try:
        command(line)
    except Exception as error:  # one bad command (a vanished object, a malformed line) never ends the session
        emit(ev='error', message=repr(error))
    return True


PROFILE_XML = '''<node><interface name="org.bluez.Profile1"><method name="Release"/><method name="RequestDisconnection"><arg type="o" direction="in"/></method>
<method name="NewConnection"><arg type="o" direction="in"/><arg type="h" direction="in"/><arg type="a{sv}" direction="in"/></method></interface></node>'''


def rfcomm(address, uuid):
    """One RFCOMM connection: BlueZ connects the profile (SDP lookup of the service's channel) and hands the socket over in NewConnection."""
    connected = {}
    profile = '/apklayer/rfcomm%d' % os.getpid()

    def on_profile(connection, sender, path, interface, method, parameters, invocation):
        if method == 'NewConnection':
            connected['fd'] = invocation.get_message().get_unix_fd_list().get(parameters.unpack()[1])
            loop.quit()
        invocation.return_value(None)
    bus.register_object(profile, Gio.DBusNodeInfo.new_for_xml(PROFILE_XML).interfaces[0], on_profile, None, None)
    for path, ifaces in call('/', 'org.freedesktop.DBus.ObjectManager', 'GetManagedObjects')[0].items():
        objects[path] = ifaces
    target = device_path(address)
    try:
        if target is None:
            raise RuntimeError('unknown device ' + address)
        call('/org/bluez', BLUEZ + '.ProfileManager1', 'RegisterProfile', (profile, uuid, {'Role': GLib.Variant('s', 'client')}), '(osa{sv})')
        call(target, DEVICE, 'ConnectProfile', (uuid,), '(s)', lambda r, e: e and (connected.update(error=e), loop.quit()))
    except (GLib.Error, RuntimeError) as error:
        print('ERR ' + str(error), flush=True)
        return 1
    loop.run()
    if 'fd' not in connected:
        print('ERR ' + connected.get('error', 'no connection'), flush=True)
        return 1
    print('OK', flush=True)
    sock = connected['fd']

    def pump(source, sink):
        while True:
            data = os.read(source, 65536)
            if not data:
                break
            os.write(sink, data)
    threading.Thread(target=pump, args=(0, sock), daemon=True).start()
    try:
        pump(sock, 1)
    except OSError:
        pass
    return 0


if len(sys.argv) == 4 and sys.argv[1] == 'rfcomm':
    sys.exit(rfcomm(sys.argv[2], sys.argv[3]))
try:  # no auto-start: an activatable BlueZ that cannot start (no adapter) answered only after D-Bus's 25 s timeout, the app's first Bluetooth call waiting on it
    for path, ifaces in bus.call_sync(BLUEZ, '/', 'org.freedesktop.DBus.ObjectManager', 'GetManagedObjects', None, None,
                                      Gio.DBusCallFlags.NO_AUTO_START, -1, None).unpack()[0].items():
        objects[path] = ifaces
except GLib.Error as error:  # no BlueZ: the adapter is absent
    objects.clear()
bus.signal_subscribe(BLUEZ, None, None, None, None, Gio.DBusSignalFlags.NONE, on_signal, None)
emit_adapter()
GLib.io_add_watch(GLib.IOChannel.unix_new(0), GLib.PRIORITY_DEFAULT, GLib.IOCondition.IN | GLib.IOCondition.HUP, on_input)
loop.run()
