#!/usr/bin/env python3
"""bt-peer.py <seconds> [agent] [rfcomm:<channel>] [gatt] (each [@<seconds>]: started that much later): the far side of hello.bluetooth's real-device roles (Real.java) on another machine's BlueZ, for
<seconds>: agent: the default pairing agent (DisplayYesNo), saying yes to every comparison and service and printing the passkey (the app's side must show the
same); rfcomm:<channel>: an RFCOMM socket listening on that channel, answering each line with "echo:<line>"; gatt: an LE peripheral (BlueZ's GattManager1 and
LEAdvertisingManager1) with one service: a readable characteristic ("apklayer"), a writable one (prints what comes), a notifying one (a counter each second
while subscribed). One line per event on stdout."""
import socket
import subprocess
import sys
import threading
import warnings
from gi.repository import Gio, GLib

warnings.simplefilter('ignore', DeprecationWarning)
bus = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
loop = GLib.MainLoop()
SERVICE, READ, WRITE, NOTIFY = ('a1b2c3d4-%04x-1000-8000-00805f9b34fb' % n for n in range(4))


def say(*words):
    print(*words, flush=True)


def call(path, interface, method, args, signature):
    bus.call_sync('org.bluez', path, interface, method, GLib.Variant(signature, args), None, Gio.DBusCallFlags.NONE, -1, None)


def export(path, xml, on_call, properties=None):
    info = Gio.DBusNodeInfo.new_for_xml(xml).interfaces[0]
    bus.register_object(path, info, on_call, (lambda c, s, p, i, name: properties[name]) if properties else None, None)


def agent():
    xml = ('<node><interface name="org.bluez.Agent1">' + ''.join('<method name="%s">%s</method>' % (m, ''.join('<arg type="%s" direction="%s"/>' % a for a in args)) for m, args in {
        'Release': [], 'Cancel': [], 'RequestPinCode': [('o', 'in'), ('s', 'out')], 'DisplayPinCode': [('o', 'in'), ('s', 'in')], 'RequestPasskey': [('o', 'in'), ('u', 'out')],
        'DisplayPasskey': [('o', 'in'), ('u', 'in'), ('q', 'in')], 'RequestConfirmation': [('o', 'in'), ('u', 'in')], 'RequestAuthorization': [('o', 'in')],
        'AuthorizeService': [('o', 'in'), ('s', 'in')]}.items()) + '</interface></node>')

    def on_call(connection, sender, path, interface, method, parameters, invocation):
        args = parameters.unpack()
        say('agent', method, *('%06d' % a if isinstance(a, int) else a for a in args))
        invocation.return_value(GLib.Variant('(s)', ('0000',)) if method == 'RequestPinCode' else GLib.Variant('(u)', (0,)) if method == 'RequestPasskey' else None)
    export('/apklayer/peeragent', xml, on_call)
    call('/org/bluez', 'org.bluez.AgentManager1', 'RegisterAgent', ('/apklayer/peeragent', 'DisplayYesNo'), '(os)')
    call('/org/bluez', 'org.bluez.AgentManager1', 'RequestDefaultAgent', ('/apklayer/peeragent',), '(o)')
    say('agent ready')


def rfcomm(channel):
    server = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    server.bind((socket.BDADDR_ANY, channel))
    server.listen(1)
    say('rfcomm listening', channel)

    def serve():
        while True:
            peer, address = server.accept()
            line = peer.recv(256).decode(errors='replace').strip()
            link = subprocess.run(['hcitool', 'con'], capture_output=True, text=True).stdout  # the link's security: AUTH, ENCRYPT
            say('rfcomm', address[0], 'got', line, 'link:', ' '.join(l.split(' lm ')[-1] for l in link.splitlines() if address[0] in l))
            peer.send(('echo:' + line + '\n').encode())
            peer.recv(256)
            peer.close()
    threading.Thread(target=serve, daemon=True).start()


def gatt():
    adapter = next(p for p, i in bus.call_sync('org.bluez', '/', 'org.freedesktop.DBus.ObjectManager', 'GetManagedObjects', None, None, 0, -1, None).unpack()[0].items()
                   if 'org.bluez.Adapter1' in i)
    root, counter, subscribed = '/apklayer/gatt', [0], [False]
    chars = {READ: ['read'], WRITE: ['write'], NOTIFY: ['read', 'notify']}
    objects = {root + '/s0': {'org.bluez.GattService1': {'UUID': GLib.Variant('s', SERVICE), 'Primary': GLib.Variant('b', True)}}}
    for n, (uuid, flags) in enumerate(chars.items()):
        objects[root + '/s0/c%d' % n] = {'org.bluez.GattCharacteristic1': {'UUID': GLib.Variant('s', uuid), 'Service': GLib.Variant('o', root + '/s0'),
                                                                           'Flags': GLib.Variant('as', flags)}}
    manager = '<node><interface name="org.freedesktop.DBus.ObjectManager"><method name="GetManagedObjects"><arg type="a{oa{sa{sv}}}" direction="out"/></method></interface></node>'
    export(root, manager, lambda c, s, p, i, m, params, inv: inv.return_value(GLib.Variant('(a{oa{sa{sv}}})', (objects,))))
    export(root + '/s0', '<node><interface name="org.bluez.GattService1"><property name="UUID" type="s" access="read"/><property name="Primary" type="b" access="read"/></interface></node>',
           None, {k: v for k, v in objects[root + '/s0']['org.bluez.GattService1'].items()})
    char_xml = ('<node><interface name="org.bluez.GattCharacteristic1"><method name="ReadValue"><arg type="a{sv}" direction="in"/><arg type="ay" direction="out"/></method>'
                '<method name="WriteValue"><arg type="ay" direction="in"/><arg type="a{sv}" direction="in"/></method><method name="StartNotify"/><method name="StopNotify"/>'
                '<property name="UUID" type="s" access="read"/><property name="Service" type="o" access="read"/><property name="Flags" type="as" access="read"/></interface></node>')
    notify_path = root + '/s0/c2'

    def on_char(connection, sender, path, interface, method, parameters, invocation):
        say('gatt', path.rsplit('/', 1)[1], method, *[bytes(a).decode(errors='replace') if isinstance(a, list) else dict(a) for a in parameters.unpack()])
        if method == 'ReadValue':
            invocation.return_value(GLib.Variant('(ay)', (b'apklayer' if path.endswith('c0') else str(counter[0]).encode(),)))
            return
        subscribed[0] = method == 'StartNotify' or subscribed[0] and method != 'StopNotify'
        invocation.return_value(None)
    for n in range(3):
        export(root + '/s0/c%d' % n, char_xml, on_char, dict(objects[root + '/s0/c%d' % n]['org.bluez.GattCharacteristic1']))

    def tick():
        counter[0] += 1
        if subscribed[0]:
            bus.emit_signal(None, notify_path, 'org.freedesktop.DBus.Properties', 'PropertiesChanged', GLib.Variant('(sa{sv}as)', (
                'org.bluez.GattCharacteristic1', {'Value': GLib.Variant('ay', str(counter[0]).encode())}, [])))
        return True
    GLib.timeout_add_seconds(1, tick)
    ad = '/apklayer/advert'
    export(ad, '<node><interface name="org.bluez.LEAdvertisement1"><method name="Release"/><property name="Type" type="s" access="read"/>'
               '<property name="ServiceUUIDs" type="as" access="read"/><property name="LocalName" type="s" access="read"/></interface></node>',
           lambda *a: a[6].return_value(None), {'Type': GLib.Variant('s', 'peripheral'), 'ServiceUUIDs': GLib.Variant('as', [SERVICE]),
                                                 'LocalName': GLib.Variant('s', 'apklayer-peer')})
    for interface, method, path in (('GattManager1', 'RegisterApplication', root), ('LEAdvertisingManager1', 'RegisterAdvertisement', ad)):  # BlueZ calls back: async
        bus.call('org.bluez', adapter, 'org.bluez.' + interface, method, GLib.Variant('(oa{sv})', (path, {})), None, 0, -1, None,
                 lambda source, result, m=method: say('gatt', m, 'ok' if source.call_finish(result) is not None else 'failed'))


def start(role):
    try:
        {'agent': agent, 'gatt': gatt}[role]() if ':' not in role else rfcomm(int(role.split(':')[1]))
    except (GLib.Error, OSError) as error:
        say(role, 'failed', getattr(error, 'message', None) or error)
    return False


for role in sys.argv[2:]:  # <role>@<seconds>: started that much later (a device coming into reach: autoConnect)
    GLib.timeout_add_seconds(int(role.split('@')[1]), start, role.split('@')[0]) if '@' in role else start(role)
GLib.timeout_add_seconds(int(sys.argv[1]), loop.quit)
loop.run()
say('peer done')
