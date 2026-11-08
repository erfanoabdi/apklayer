#!/usr/bin/env python3
"""Fills python-dbusmock's bluez5 template (running on $DBUS_SYSTEM_BUS_ADDRESS) for the bluetooth group: adapter hci0, a paired classic device,
a BLE device advertising the Battery service whose GATT database has Battery Level (read 42; notify sends 43) with its CCC descriptor, RFCOMM on the
paired device (an echo service behind the Serial Port Profile UUID) and a legacy device that pairs only with PIN 1234 (tests/vm/bluez-mock-calls.py)."""
import os

import dbus

bus = dbus.SystemBus()
mock = dbus.Interface(bus.get_object('org.bluez', '/org/bluez'), 'org.bluez.Mock')
MOCK = 'org.freedesktop.DBus.Mock'
mock.AddAdapter('hci0', 'apklayer-mock')
mock.AddDevice('hci0', '11:22:33:44:55:66', 'mock-paired')
mock.PairDevice('hci0', '11:22:33:44:55:66')
mock.AddDevice('hci0', 'C0:FF:EE:00:00:01', 'mock-ble')
mock.AddDevice('hci0', 'C0:FF:EE:00:00:02', 'mock-other')  # filtered out by the app's service UUID filter
mock.AddDevice('hci0', 'AA:BB:CC:00:00:01', 'mock-pin')

CALLS = "exec(open(%r).read(), globals()); " % os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bluez-mock-calls.py')
bus.get_object('org.bluez', '/org/bluez').AddMethod('org.bluez.ProfileManager1', 'RegisterProfile', 'osa{sv}', '',
                                                    "self.profiles = {**getattr(self, 'profiles', {}), args[1].lower(): args[0]}", dbus_interface=MOCK)
bus.get_object('org.bluez', '/org/bluez/hci0/dev_11_22_33_44_55_66').AddMethod('org.bluez.Device1', 'ConnectProfile', 's', '',
                                                                                 CALLS + 'connect_profile(self, args[0])', dbus_interface=MOCK)
bus.get_object('org.bluez', '/org/bluez/hci0/dev_AA_BB_CC_00_00_01').AddMethod('org.bluez.Device1', 'Pair', '', '', CALLS + 'pair(self)', dbus_interface=MOCK)

BATTERY, LEVEL, CCC = ('0000%s-0000-1000-8000-00805f9b34fb' % n for n in ('180f', '2a19', '2902'))
dev = '/org/bluez/hci0/dev_C0_FF_EE_00_00_01'
device = bus.get_object('org.bluez', dev)
device.UpdateProperties('org.bluez.Device1', {'AddressType': 'random', 'UUIDs': dbus.Array([BATTERY], signature='s'), 'RSSI': dbus.Int16(-50)},
                        dbus_interface=MOCK)
# Properties.Set, not UpdateProperties: dbusmock 0.31 cannot re-wrap a dictionary there (Ubuntu 24.04's python3-dbusmock)
device.Set('org.bluez.Device1', 'ManufacturerData', dbus.Dictionary({dbus.UInt16(0xffff): dbus.Array([1, 2, 3], signature='y', variant_level=1)}, signature='qv'),
           dbus_interface='org.freedesktop.DBus.Properties')
bus.get_object('org.bluez', '/org/bluez/hci0/dev_C0_FF_EE_00_00_02').UpdateProperties('org.bluez.Device1', {'AddressType': 'random', 'RSSI': dbus.Int16(-70)},
                                                                                       dbus_interface=MOCK)
# connecting resolves the GATT database at once, as BlueZ does after its discovery
device.AddMethod('org.bluez.Device1', 'Connect', '', '', "self.UpdateProperties('org.bluez.Device1', {'Connected': dbus.Boolean(True), 'ServicesResolved': dbus.Boolean(True)})", dbus_interface=MOCK)
device.AddMethod('org.bluez.Device1', 'Disconnect', '', '', "self.UpdateProperties('org.bluez.Device1', {'Connected': dbus.Boolean(False), 'ServicesResolved': dbus.Boolean(False)})", dbus_interface=MOCK)
root = dbus.Interface(bus.get_object('org.bluez', '/'), MOCK)
root.AddObject(dev + '/service0001', 'org.bluez.GattService1', {'UUID': BATTERY, 'Primary': True, 'Device': dbus.ObjectPath(dev)}, [])
char = dev + '/service0001/char0002'
root.AddObject(char, 'org.bluez.GattCharacteristic1',
               {'UUID': LEVEL, 'Service': dbus.ObjectPath(dev + '/service0001'), 'Flags': dbus.Array(['read', 'notify'], signature='s'),
                'Notifying': False, 'Value': dbus.Array([], signature='y')},
               [('ReadValue', 'a{sv}', 'ay', "ret = dbus.Array([42], signature='y')"),
                ('StartNotify', '', '', "self.UpdateProperties('org.bluez.GattCharacteristic1', {'Notifying': dbus.Boolean(True)}); "
                                        "self.UpdateProperties('org.bluez.GattCharacteristic1', {'Value': dbus.Array([43], signature='y')})"),
                ('StopNotify', '', '', "self.UpdateProperties('org.bluez.GattCharacteristic1', {'Notifying': dbus.Boolean(False)})"),
                ('WriteValue', 'aya{sv}', '', '')])
root.AddObject(char + '/desc0003', 'org.bluez.GattDescriptor1', {'UUID': CCC, 'Characteristic': dbus.ObjectPath(char)},
               [('ReadValue', 'a{sv}', 'ay', "ret = dbus.Array([0, 0], signature='y')"), ('WriteValue', 'aya{sv}', '', '')])
print('bluez mock ready')
