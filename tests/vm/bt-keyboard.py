#!/usr/bin/env python3
"""bt-keyboard.py <seconds> <fifo>: this machine's BlueZ as a Bluetooth LE keyboard (HID over GATT, HOGP) for checking a phone's keyboard path with
a real Bluetooth device. Registers the HID service 0x1812 (HID Information, a boot keyboard's Report Map, one input Report with its Report Reference,
Protocol Mode, Control Point) through GattManager1, advertises it as a keyboard (appearance 0x03C1, discoverable) through LEAdvertisingManager1, a
NoInputNoOutput agent (just works), and makes the adapter pairable for the run (its old Pairable value comes back at the end). Each line written to
<fifo> is typed as key reports (letters, digits, space, a literal \\n for Enter; Shift for capitals), one notification per press and release. Ends after <seconds>, or at
the line "quit". One line per event on stdout. The same GLib/Gio style as bt-peer.py (python3-gi, run as a user on the system bus)."""
import os
import sys
import warnings
from gi.repository import Gio, GLib

warnings.simplefilter('ignore', DeprecationWarning)
bus = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
loop = GLib.MainLoop()
ROOT, AD, AGENT = '/apklayer/kbd', '/apklayer/kbdadvert', '/apklayer/kbdagent'
# a boot keyboard without LEDs and without report ids: modifiers, reserved, six key codes
REPORT_MAP = bytes.fromhex('05010906a101050719e029e71500250175019508810295017508810195067508150025650507190029658100c0')
U = '0000%04x-0000-1000-8000-00805f9b34fb'
CHARS = {  # path: uuid, flags, value
    'c0': (U % 0x2a4a, ['read'], bytes([0x11, 0x01, 0x00, 0x02])),  # HID Information: bcdHID 1.11, country 0, normally connectable
    'c1': (U % 0x2a4b, ['read'], REPORT_MAP),
    'c2': (U % 0x2a4d, ['read', 'notify'], bytes(8)),  # the input Report
    'c3': (U % 0x2a4e, ['read', 'write-without-response'], bytes([1])),  # Protocol Mode: report
    'c4': (U % 0x2a4c, ['write-without-response'], b''),  # HID Control Point
}
REPORT, REPORT_REF = ROOT + '/s0/c2', ROOT + '/s0/c2/d0'


def say(*words):
    print(*words, flush=True)


def call(path, interface, method, args, signature):
    return bus.call_sync('org.bluez', path, interface, method, GLib.Variant(signature, args) if signature else None, None, Gio.DBusCallFlags.NONE, -1, None)


def export(path, xml, on_call, properties=None):
    info = Gio.DBusNodeInfo.new_for_xml(xml).interfaces[0]
    bus.register_object(path, info, on_call, (lambda c, s, p, i, name: properties[name]) if properties else None, None)


def key(c):
    """(modifier, usage) of a character on a US layout."""
    if c.isalpha():
        return (0x02 if c.isupper() else 0, 0x04 + ord(c.lower()) - ord('a'))
    if c.isdigit():
        return (0, 0x27 if c == '0' else 0x1e + int(c) - 1)
    return {' ': (0, 0x2c), '\n': (0, 0x28)}[c]


def notify(report):
    bus.emit_signal(None, REPORT, 'org.freedesktop.DBus.Properties', 'PropertiesChanged', GLib.Variant('(sa{sv}as)', (
        'org.bluez.GattCharacteristic1', {'Value': GLib.Variant('ay', report)}, [])))


def type_text(text):
    reports = []
    for c in text:
        mod, usage = key(c)
        reports += [bytes([mod, 0, usage, 0, 0, 0, 0, 0]), bytes(8)]
    say('type', repr(text), len(reports), 'reports', 'subscribed' if subscribed[0] else 'NOT subscribed')

    def step():
        notify(reports.pop(0))
        return bool(reports)
    GLib.timeout_add(30, step)


subscribed = [False]


def main():
    seconds, fifo = int(sys.argv[1]), sys.argv[2]
    adapter = next(p for p, i in call('/', 'org.freedesktop.DBus.ObjectManager', 'GetManagedObjects', None, None).unpack()[0].items()
                   if 'org.bluez.Adapter1' in i)
    props = 'org.freedesktop.DBus.Properties'
    pairable = call(adapter, props, 'Get', ('org.bluez.Adapter1', 'Pairable'), '(ss)').unpack()[0]
    call(adapter, props, 'Set', ('org.bluez.Adapter1', 'Pairable', GLib.Variant('b', True)), '(ssv)')
    say('adapter', adapter, 'pairable was', pairable)

    # agent: just works
    agent_xml = ('<node><interface name="org.bluez.Agent1">' + ''.join('<method name="%s">%s</method>' % (m, ''.join('<arg type="%s" direction="%s"/>' % a for a in args)) for m, args in {
        'Release': [], 'Cancel': [], 'RequestConfirmation': [('o', 'in'), ('u', 'in')], 'RequestAuthorization': [('o', 'in')],
        'AuthorizeService': [('o', 'in'), ('s', 'in')]}.items()) + '</interface></node>')

    def on_agent(c, s, p, i, method, parameters, invocation):
        say('agent', method, *parameters.unpack())
        invocation.return_value(None)
    export(AGENT, agent_xml, on_agent)
    call('/org/bluez', 'org.bluez.AgentManager1', 'RegisterAgent', (AGENT, 'NoInputNoOutput'), '(os)')

    # GATT application
    objects = {ROOT + '/s0': {'org.bluez.GattService1': {'UUID': GLib.Variant('s', U % 0x1812), 'Primary': GLib.Variant('b', True)}}}
    for name, (uuid, flags, _) in CHARS.items():
        objects[ROOT + '/s0/' + name] = {'org.bluez.GattCharacteristic1': {'UUID': GLib.Variant('s', uuid), 'Service': GLib.Variant('o', ROOT + '/s0'),
                                                                            'Flags': GLib.Variant('as', flags)}}
    objects[REPORT_REF] = {'org.bluez.GattDescriptor1': {'UUID': GLib.Variant('s', U % 0x2908), 'Characteristic': GLib.Variant('o', REPORT),
                                                         'Flags': GLib.Variant('as', ['read'])}}
    manager = '<node><interface name="org.freedesktop.DBus.ObjectManager"><method name="GetManagedObjects"><arg type="a{oa{sa{sv}}}" direction="out"/></method></interface></node>'
    export(ROOT, manager, lambda c, s, p, i, m, params, inv: inv.return_value(GLib.Variant('(a{oa{sa{sv}}})', (objects,))))
    export(ROOT + '/s0', '<node><interface name="org.bluez.GattService1"><property name="UUID" type="s" access="read"/><property name="Primary" type="b" access="read"/></interface></node>',
           None, dict(objects[ROOT + '/s0']['org.bluez.GattService1']))
    char_xml = ('<node><interface name="org.bluez.GattCharacteristic1"><method name="ReadValue"><arg type="a{sv}" direction="in"/><arg type="ay" direction="out"/></method>'
                '<method name="WriteValue"><arg type="ay" direction="in"/><arg type="a{sv}" direction="in"/></method><method name="StartNotify"/><method name="StopNotify"/>'
                '<property name="UUID" type="s" access="read"/><property name="Service" type="o" access="read"/><property name="Flags" type="as" access="read"/></interface></node>')

    def on_char(c, s, path, i, method, parameters, invocation):
        name = path.rsplit('/', 1)[1]
        say('gatt', name, method, *[bytes(a).hex() if isinstance(a, list) else dict(a) for a in parameters.unpack()])
        if method == 'ReadValue':
            offset = parameters.unpack()[0].get('offset', 0)
            invocation.return_value(GLib.Variant('(ay)', (CHARS[name][2][offset:],)))
            return
        subscribed[0] = method == 'StartNotify' or subscribed[0] and method != 'StopNotify'
        invocation.return_value(None)
    for name in CHARS:
        export(ROOT + '/s0/' + name, char_xml, on_char, dict(objects[ROOT + '/s0/' + name]['org.bluez.GattCharacteristic1']))
    export(REPORT_REF, '<node><interface name="org.bluez.GattDescriptor1"><method name="ReadValue"><arg type="a{sv}" direction="in"/><arg type="ay" direction="out"/></method>'
                       '<property name="UUID" type="s" access="read"/><property name="Characteristic" type="o" access="read"/><property name="Flags" type="as" access="read"/></interface></node>',
           lambda c, s, p, i, m, params, inv: inv.return_value(GLib.Variant('(ay)', (bytes([0, 1]),))),  # report id 0, input report
           dict(objects[REPORT_REF]['org.bluez.GattDescriptor1']))
    export(AD, '<node><interface name="org.bluez.LEAdvertisement1"><method name="Release"/><property name="Type" type="s" access="read"/>'
               '<property name="ServiceUUIDs" type="as" access="read"/><property name="LocalName" type="s" access="read"/>'
               '<property name="Appearance" type="q" access="read"/><property name="Discoverable" type="b" access="read"/></interface></node>',
           lambda *a: a[6].return_value(None), {'Type': GLib.Variant('s', 'peripheral'), 'ServiceUUIDs': GLib.Variant('as', ['1812']),
                                                 'LocalName': GLib.Variant('s', 'apklayer-kbd'), 'Appearance': GLib.Variant('q', 0x03c1),
                                                 'Discoverable': GLib.Variant('b', True)})
    for interface, method, path in (('GattManager1', 'RegisterApplication', ROOT), ('LEAdvertisingManager1', 'RegisterAdvertisement', AD)):  # BlueZ calls back: async
        bus.call('org.bluez', adapter, 'org.bluez.' + interface, method, GLib.Variant('(oa{sv})', (path, {})), None, 0, -1, None,
                 lambda source, result, m=method: say('gatt', m, 'ok' if source.call_finish(result) is not None else 'failed'))

    # typed text from the fifo (opened read-write: a writer never blocks, end of file never comes)
    if not os.path.exists(fifo):
        os.mkfifo(fifo)
    fd = os.open(fifo, os.O_RDWR | os.O_NONBLOCK)
    buffer = [b'']

    def on_fifo(*_):
        buffer[0] += os.read(fd, 4096)
        *lines, buffer[0] = buffer[0].split(b'\n')
        for line in lines:
            if line == b'quit':
                loop.quit()
            elif line:
                type_text(line.decode().replace("\\n", "\n"))  # a literal \n: Enter
        return True
    GLib.io_add_watch(fd, GLib.IO_IN, on_fifo)
    GLib.timeout_add_seconds(seconds, loop.quit)
    for sig in (2, 15):  # SIGINT, SIGTERM: the cleanup below still runs
        GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, sig, lambda: loop.quit() or False)
    say('keyboard ready, type into', fifo)
    try:
        loop.run()
    finally:
        os.close(fd)
        os.unlink(fifo)
        steps = [(adapter, props, 'Set', ('org.bluez.Adapter1', 'Pairable', GLib.Variant('b', pairable)), '(ssv)')]  # first: later it can be Busy
        steps += [('/org/bluez' if i == 'AgentManager1' else adapter, 'org.bluez.' + i, m, (p,), '(o)') for i, m, p in (
            ('LEAdvertisingManager1', 'UnregisterAdvertisement', AD), ('GattManager1', 'UnregisterApplication', ROOT), ('AgentManager1', 'UnregisterAgent', AGENT))]
        for step in steps:
            try:
                call(*step)
            except GLib.Error as error:
                say(step[2], 'failed', error.message)
        say('keyboard done, pairable', pairable)


assert key('A') == (2, 4) and key('b') == (0, 5) and key('1') == (0, 0x1e) and key('0') == (0, 0x27)
main()
