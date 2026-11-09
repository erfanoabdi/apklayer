#!/usr/bin/env python3
"""BlueZ (org.bluez on the system bus) for one app's Bluetooth session, started by the hub (HubBluetooth) with stdin/stdout handed to the app (HostBluetooth).
Speaks JSON lines: commands {"op": ...} on stdin, events {"ev": ...} on stdout. Ends when stdin closes (BlueZ then stops this client's discovery and notifications).
`bluez.py rfcomm <address> <uuid>|<channel> [secure|insecure]`: an RFCOMM connection to the device's service through BlueZ's Profile API (or straight to the channel, an
AF_BLUETOOTH socket); `bluez.py listen <uuid> <name> [secure|insecure] [<app>]`: the service registered (BlueZ's Profile API, server role, through the app's dispatcher:
`serve`), `LISTEN` once it is, then the next incoming connection. Both print `OK [address]` or `ERR <why>` on its own line, then relay the connection's bytes between stdin/stdout and the socket."""
import array
import ctypes
import json
import os
import select
import socket
import struct
import subprocess
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
libc = ctypes.CDLL(None, use_errno=True)


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


def mtu(address):
    """The ATT MTU BlueZ exchanged with the device (its characteristics' MTU property, BlueZ 5.62+; else the default 23)."""
    root = device_path(address) or '-'
    emit(ev='mtu', address=address, mtu=max([i[CHAR].get('MTU', 23) for p, i in objects.items() if p.startswith(root + '/') and CHAR in i] or [23]))


scanning = False
notifying = set()
waiting_services = set()
waiting_mtu = set()


def on_properties(path, interface, changed):
    known = objects.setdefault(path, {}).setdefault(interface, {})
    known.update(changed)
    if interface == ADAPTER:
        emit_adapter()
    elif interface == DEVICE:
        address = known.get('Address')
        if scanning and ({'RSSI', 'ManufacturerData', 'ServiceData'} & changed.keys()):
            emit(ev='found', device=device(path))
        if 'RSSI' in changed:
            seen(path)
        if 'Connected' in changed:
            emit(ev='conn', address=address, connected=changed['Connected'], status=0)
        if 'Paired' in changed:
            emit(ev='bond', address=address, state=12 if changed['Paired'] else 10)
            emit_adapter()
        if changed.get('ServicesResolved') and address in waiting_services:
            waiting_services.discard(address)
            services(address)
        if changed.get('ServicesResolved') and address in waiting_mtu:
            waiting_mtu.discard(address)
            mtu(address)
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
        if DEVICE in args[1]:
            seen(args[0])
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
pin_requests = {}  # address -> (method, invocation): BlueZ waits for the answer the app or the person gives (op "pin")
VARIANTS = {'RequestPinCode': 0, 'RequestPasskey': 1, 'RequestConfirmation': 2, 'DisplayPasskey': 4, 'DisplayPinCode': 5}  # BluetoothDevice.PAIRING_VARIANT_*


def on_agent(connection, sender, path, interface, method, parameters, invocation):
    # the app's side answers: its ACTION_PAIRING_REQUEST receiver (setPin) or the hub's dialog; a key to show is told and answered at once
    if method in VARIANTS:
        args = parameters.unpack()
        address = objects.get(args[0], {}).get(DEVICE, {}).get('Address') or args[0].rsplit('dev_', 1)[-1].replace('_', ':')
        if method.startswith('Request'):
            pin_requests[address.upper()] = (method, invocation)
        emit(ev='pin', address=address, name=objects.get(args[0], {}).get(DEVICE, {}).get('Alias'), variant=VARIANTS[method],
             key=None if len(args) < 2 else str(args[1]) if method == 'DisplayPinCode' else '%06d' % args[1])
        if not method.startswith('Request'):
            invocation.return_value(None)
    else:
        if method == 'Cancel':  # the pairing ended without the answer (timeout, the device gave up): a question about it closes
            pin_requests.clear()
            emit(ev='cancel')
        invocation.return_value(None)


def ensure_agent():
    global agent
    if agent is None:
        agent = '/apklayer/agent%d' % os.getpid()
        bus.register_object(agent, Gio.DBusNodeInfo.new_for_xml(AGENT_XML).interfaces[0], on_agent, None, None)
        call('/org/bluez', BLUEZ + '.AgentManager1', 'RegisterAgent', (agent, 'DisplayYesNo'), '(os)')  # Android's: numeric comparison where both sides show it


pending = {}  # address -> what to do once BlueZ sees the device: it connects and pairs only devices it knows (Android's stack pages any address); a discovery runs meanwhile
seeking = False


def connect(address, auto):
    target = device_path(address)
    if target is None:  # a direct connection gives up after about 30 s, as Android's (GATT_ERROR 133); autoConnect waits
        seek(address, lambda: connect(address, auto), None if auto else lambda: emit(ev='conn', address=address, connected=False, status=133))
    elif objects[target][DEVICE].get('Connected'):  # BlueZ says AlreadyConnected: the app's connection is there
        emit(ev='conn', address=address, connected=True, status=0)
    else:  # autoConnect: a device out of reach is connected when it shows up again
        call(target, DEVICE, 'Connect', done=lambda r, e: e and (seek(address, lambda: connect(address, True)) if auto else
                                                              emit(ev='conn', address=address, connected=False, status=133, message=e)))


def bond(address):
    target = device_path(address)
    if target is None:
        seek(address, lambda: bond(address), lambda: emit(ev='bond', address=address, state=10, message='not found'))
        return
    call(target, DEVICE, 'Pair', done=lambda r, e: e and emit(ev='bond', address=address, state=10, message=e))


def seek(address, then, give_up=None):
    global seeking
    key = address.upper()
    pending[key] = then

    def expire():
        if pending.get(key) is then:
            pending.pop(key)
            stop_seeking()
            give_up()
        return False
    if give_up:
        GLib.timeout_add_seconds(30, expire)
    if not seeking and not scanning:
        seeking = True
        call(adapter(), ADAPTER, 'SetDiscoveryFilter', ({'Transport': GLib.Variant('s', 'auto'), 'DuplicateData': GLib.Variant('b', True)},), '(a{sv})', lambda r, e: None)
        call(adapter(), ADAPTER, 'StartDiscovery', done=lambda r, e: None)


def stop_seeking():
    global seeking
    if seeking and not pending and not scanning:
        seeking = False
        call(adapter(), ADAPTER, 'StopDiscovery', done=lambda r, e: None)


def seen(path):
    then = pending.pop(objects[path][DEVICE].get('Address', '').upper(), None)
    if then:
        stop_seeking()
        then()


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
        connect(address, c.get('auto', False))
    elif op == 'disconnect':
        if pending.pop(address.upper(), None) is not None:
            stop_seeking()
        target = device_path(address)
        if target:
            call(target, DEVICE, 'Disconnect', done=lambda r, e: e and emit(ev='conn', address=address, connected=False, status=0))
    elif op == 'services':
        target = device_path(address)
        if target and objects[target][DEVICE].get('ServicesResolved'):
            services(address)
        else:
            waiting_services.add(address)
    elif op == 'mtu':
        target = device_path(address)
        if target and objects[target][DEVICE].get('ServicesResolved'):
            mtu(address)
        else:
            waiting_mtu.add(address)
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
        try:
            ensure_agent()
        except GLib.Error as error:  # Sailfish lets only its privileged group register agents: BlueZ asks its own agent then (the system's pairing UI)
            emit(ev='error', message='pairing without our agent: ' + error.message)
        emit(ev='bond', address=address, state=11)
        bond(address)
    elif op == 'pin':  # the answer to a waiting request (a PIN, a passkey, "yes" to a comparison); none: the pairing is refused
        method, invocation = pin_requests.pop(address.upper(), (None, None))
        if invocation is None:
            return
        pin = c.get('pin')
        if not pin or method == 'RequestPasskey' and not (pin.isdigit() and int(pin) <= 999999):
            invocation.return_dbus_error('org.bluez.Error.Rejected', 'refused')
        else:
            invocation.return_value(GLib.Variant('(s)', (pin,)) if method == 'RequestPinCode' else GLib.Variant('(u)', (int(pin),)) if method == 'RequestPasskey' else None)
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


def security(secure):
    """RegisterProfile's options for an Android secure (authenticated, encrypted) or insecure (neither) service; no per-service question to the person."""
    return {'RequireAuthentication': GLib.Variant('b', secure), 'RequireAuthorization': GLib.Variant('b', False)}


def profile(uuid, options, on_connection):
    """Registers an RFCOMM profile: on_connection(device path, fd) for each connected socket BlueZ hands over (NewConnection)."""
    path = '/apklayer/rfcomm%d' % os.getpid()

    def on_profile(connection, sender, path, interface, method, parameters, invocation):
        if method == 'NewConnection':
            on_connection(parameters.unpack()[0], invocation.get_message().get_unix_fd_list().get(parameters.unpack()[1]))
        invocation.return_value(None)
    bus.register_object(path, Gio.DBusNodeInfo.new_for_xml(PROFILE_XML).interfaces[0], on_profile, None, None)
    call('/org/bluez', BLUEZ + '.ProfileManager1', 'RegisterProfile', (path, uuid, options), '(osa{sv})')
    return path


def relay(sock, status):
    os.set_blocking(sock, True)  # BlueZ hands its sockets over non-blocking: the first read with nothing yet (EAGAIN) ended the connection
    print(status, flush=True)

    def pump(source, sink):
        while True:
            data = os.read(source, 65536)
            if not data:
                break
            os.write(sink, data)

    def outgoing():  # the app closed the socket: the connection ends (also when the device sends nothing more)
        try:
            pump(0, sock)
        except OSError:
            pass
        connection = socket.socket(fileno=sock)
        connection.shutdown(socket.SHUT_RDWR)
        connection.detach()
    threading.Thread(target=outgoing, daemon=True).start()
    try:
        pump(sock, 1)
    except OSError:
        pass
    return 0


def rfcomm(address, target, secure):
    """One RFCOMM connection: to a channel, a socket of our own; to a service, BlueZ connects the profile (SDP lookup of its channel) and hands the socket over."""
    if target.isdigit():  # BluetoothDevice.createRfcommSocket(channel), the hidden call serial apps fall back to
        try:
            sock = socket.socket(31, socket.SOCK_STREAM, 3)  # AF_BLUETOOTH, BTPROTO_RFCOMM
            sock.setsockopt(274, 4, bytes([2 if secure else 1, 0]))  # SOL_BLUETOOTH, BT_SECURITY {level: medium (authenticated, encrypted) or low, key size}
            # connect(2) with a sockaddr_rc of our own: Sailfish's Python has no AF_BLUETOOTH addresses (built without the Bluetooth headers)
            rc = struct.pack('<H6sBx', 31, bytes.fromhex(address.replace(':', ''))[::-1], int(target))
            if libc.connect(sock.fileno(), rc, len(rc)) != 0:
                raise OSError(ctypes.get_errno(), os.strerror(ctypes.get_errno()))
        except (OSError, AttributeError, ValueError) as error:
            print('ERR ' + (getattr(error, 'strerror', None) or str(error)), flush=True)
            return 1
        return relay(sock.detach(), 'OK')
    connected = {}
    for path, ifaces in call('/', 'org.freedesktop.DBus.ObjectManager', 'GetManagedObjects')[0].items():
        objects[path] = ifaces
    device = device_path(address)
    try:
        if device is None:
            raise RuntimeError('unknown device ' + address)
        profile(target, {'Role': GLib.Variant('s', 'client'), **security(secure)}, lambda device, fd: (connected.update(fd=fd), loop.quit()))
        call(device, DEVICE, 'ConnectProfile', (target,), '(s)', lambda r, e: e and (connected.update(error=e), loop.quit()))
    except (GLib.Error, RuntimeError) as error:
        print('ERR ' + str(error), flush=True)
        return 1
    loop.run()
    if 'fd' not in connected:
        print('ERR ' + connected.get('error', 'no connection'), flush=True)
        return 1
    return relay(connected['fd'], 'OK')


def address_of(device):
    try:
        return call(device, PROPS, 'Get', (DEVICE, 'Address'), '(ss)')[0]
    except GLib.Error:
        return device.rsplit('dev_', 1)[-1].replace('_', ':')


def listen(uuid, name, secure, app):
    """One incoming connection to the service (BluetoothServerSocket.accept): taken from the server socket's dispatcher (`serve`, started by the first
    listener), the one registration of the service, which queues what BlueZ hands over until an accept takes it (a registration per accept left a gap
    after each connection in which the next one was refused; BlueZ registers a UUID once). Ends without a connection when stdin closes (the server
    socket closed)."""
    key = 'apklayer-bt-%s-%s' % (app, uuid)  # one dispatcher per app process and service (an abstract socket's name)
    taker = socket.socket(socket.AF_UNIX, socket.SOCK_SEQPACKET)
    try:
        taker.connect('\0' + key)
    except OSError:
        dispatcher = subprocess.Popen([sys.executable, os.path.abspath(__file__), 'serve', uuid, name, 'secure' if secure else 'insecure', key],
                                      stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, text=True)
        status = dispatcher.stdout.readline().strip()
        dispatcher.stdout.close()
        try:
            taker.connect('\0' + key)
        except OSError:
            print('ERR ' + (status[4:] if status.startswith('ERR ') else 'the service could not be registered'), flush=True)
            return 1
    print('LISTEN', flush=True)
    if taker not in select.select([taker, 0], [], [])[0]:
        return 1  # stdin closed first: the server socket closed (the dispatcher sees this taker go)
    fds = array.array('i')
    message, ancillary, flags, _ = taker.recvmsg(256, socket.CMSG_LEN(fds.itemsize))
    for level, kind, data in ancillary:
        if level == socket.SOL_SOCKET and kind == socket.SCM_RIGHTS:
            fds.frombytes(data[:fds.itemsize])
    if not fds:
        return 1
    return relay(fds[0], 'OK ' + message.decode())


def serve(uuid, name, secure, key):
    """The dispatcher of a server socket: the service registered (Channel 0: BlueZ picks a free RFCOMM channel; without it a custom UUID's record names
    none), each connection handed to the waiting listener (in order; queued while none waits). Ends when a waiting listener goes untaken: the app closed
    the server socket (its shadow starts the next listener as an accept returns, so one waits while it is open)."""
    server = socket.socket(socket.AF_UNIX, socket.SOCK_SEQPACKET)
    try:
        server.bind('\0' + key)
    except OSError:  # another listener's dispatcher came first
        print('BUSY', flush=True)
        return 0
    server.listen(8)
    queue, takers = [], []  # (fd, device path) no accept took yet; (listener, its watch) waiting

    def hand():
        while queue and takers:
            (taker, watch), (fd, device) = takers.pop(0), queue.pop(0)
            GLib.source_remove(watch)
            taker.sendmsg([address_of(device).encode()], [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array('i', [fd]))])
            os.close(fd)
            taker.close()

    def on_taker(source, condition):
        taker = server.accept()[0]
        if struct.unpack('3i', taker.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))[1] != os.getuid():
            taker.close()
            return True
        takers.append((taker, GLib.io_add_watch(taker.fileno(), GLib.PRIORITY_DEFAULT, GLib.IOCondition.IN | GLib.IOCondition.HUP, lambda *a: loop.quit())))
        hand()
        return True
    try:
        profile(uuid, {'Role': GLib.Variant('s', 'server'), 'Name': GLib.Variant('s', name), 'Channel': GLib.Variant('q', 0), **security(secure)},
                lambda device, fd: (queue.append((fd, device)), GLib.idle_add(hand)))  # after the reply: BlueZ waits for it
    except GLib.Error as error:
        print('ERR ' + error.message, flush=True)
        return 1
    print('READY', flush=True)
    sys.stdout.close()
    GLib.io_add_watch(server.fileno(), GLib.PRIORITY_DEFAULT, GLib.IOCondition.IN, on_taker)
    loop.run()
    return 0


if len(sys.argv) == 6 and sys.argv[1] == 'serve':
    sys.exit(serve(sys.argv[2], sys.argv[3], sys.argv[4] != 'insecure', sys.argv[5]))
if len(sys.argv) >= 4 and sys.argv[1] == 'rfcomm':  # [secure|insecure]: secure when not said
    sys.exit(rfcomm(sys.argv[2], sys.argv[3], sys.argv[4:5] != ['insecure']))
if len(sys.argv) >= 4 and sys.argv[1] == 'listen':  # [secure|insecure] [the app's process: one dispatcher per app and service]
    sys.exit(listen(sys.argv[2], sys.argv[3], sys.argv[4:5] != ['insecure'], sys.argv[5] if len(sys.argv) > 5 else str(os.getppid())))
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
