"""python-dbusmock template of GeoClue 1 (geoclue 0.12's master, as Sailfish OS runs it on the session bus) for tests/vm/gnss.sh:
`python3 -m dbusmock --template tests/vm/geoclue1-mock.py -p '{"latitude": .., "longitude": .., "accuracy": ..}'`. Master.Create makes a
master client; its PositionStart emits PositionChanged with the configured point (fields latitude|longitude, detailed level)."""
import dbus
from dbusmock import mockobject

BUS_NAME = 'org.freedesktop.Geoclue.Master'
MAIN_OBJ = '/org/freedesktop/Geoclue/Master'
MAIN_IFACE = 'org.freedesktop.Geoclue.Master'
SYSTEM_BUS = False
POSITION_IFACE = 'org.freedesktop.Geoclue.Position'
clients = 0
point = {}


def position():
    return [dbus.Int32(3), dbus.Int32(0), dbus.Double(point['latitude']), dbus.Double(point['longitude']), dbus.Double(0.0),
            dbus.Struct((dbus.Int32(6), dbus.Double(point['accuracy']), dbus.Double(0.0)), signature='idd')]


def load(mock, parameters):
    point.update(latitude=float(parameters.get('latitude', 60.17)), longitude=float(parameters.get('longitude', 24.94)),
                 accuracy=float(parameters.get('accuracy', 8.0)))
    mock.AddMethods(MAIN_IFACE, [('Create', '', 'o', Create)])


@dbus.service.method(MAIN_IFACE, in_signature='', out_signature='o')
def Create(self):
    global clients
    clients += 1
    path = '/org/freedesktop/Geoclue/Master/client%d' % clients
    self.AddObject(path, 'org.freedesktop.Geoclue.MasterClient', {},
                   [('SetRequirements', 'iibi', '', ''), ('PositionStart', '', '', PositionStart), ('AddressStart', '', '', '')])
    mockobject.objects[path].AddMethods(POSITION_IFACE, [('GetPosition', '', 'iiddd(idd)', 'ret = tuple(%r)' % (position(),))])
    return dbus.ObjectPath(path)


@dbus.service.method('org.freedesktop.Geoclue.MasterClient', in_signature='', out_signature='')
def PositionStart(self):
    self.EmitSignal(POSITION_IFACE, 'PositionChanged', 'iiddd(idd)', position())
