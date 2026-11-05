"""python-dbusmock template of GeoClue 2 (tests/vm/gnss.sh): `python3 -m dbusmock --system --template tests/vm/geoclue2-mock.py -p '{...}'`.
Manager.GetClient makes a client; its Start sets a Location object at the configured point (parameters latitude, longitude, accuracy) and
emits LocationUpdated, as GeoClue does once its sources answer. Parameter deny=true: Start fails as when the desktop's agent refuses the
DesktopId (GeoClue answers AccessDenied)."""
import dbus
from dbusmock import mockobject

BUS_NAME = 'org.freedesktop.GeoClue2'
MAIN_OBJ = '/org/freedesktop/GeoClue2/Manager'
MAIN_IFACE = 'org.freedesktop.GeoClue2.Manager'
SYSTEM_BUS = True
CLIENT_IFACE = 'org.freedesktop.GeoClue2.Client'
LOCATION_IFACE = 'org.freedesktop.GeoClue2.Location'
clients = 0
point = {}


def load(mock, parameters):
    point.update(latitude=float(parameters.get('latitude', 52.52)), longitude=float(parameters.get('longitude', 13.405)),
                 accuracy=float(parameters.get('accuracy', 4.0)), deny=bool(parameters.get('deny', False)))
    mock.AddMethods(MAIN_IFACE, [('GetClient', '', 'o', GetClient), ('DeleteClient', 'o', '', 'self.RemoveObject(args[0])')])
    mock.AddProperties(MAIN_IFACE, {'InUse': False, 'AvailableAccuracyLevel': dbus.UInt32(8)})


@dbus.service.method(MAIN_IFACE, in_signature='', out_signature='o')
def GetClient(self):
    global clients
    clients += 1
    path = '/org/freedesktop/GeoClue2/Client/%d' % clients
    self.AddObject(path, CLIENT_IFACE, {'DesktopId': '', 'RequestedAccuracyLevel': dbus.UInt32(0), 'DistanceThreshold': dbus.UInt32(0),
                                        'TimeThreshold': dbus.UInt32(0), 'Location': dbus.ObjectPath('/'), 'Active': False},
                   [('Start', '', '', Start), ('Stop', '', '', 'self.props["%s"]["Active"] = False' % CLIENT_IFACE)])
    return dbus.ObjectPath(path)


@dbus.service.method(CLIENT_IFACE, in_signature='', out_signature='')
def Start(self):
    props = self.props[CLIENT_IFACE]
    if point['deny'] or not props['DesktopId']:
        raise dbus.exceptions.DBusException("'%s' disallowed, no agent for UID 1000" % props['DesktopId'], name='org.freedesktop.DBus.Error.AccessDenied')
    location = self.path.replace('/Client/', '/Location/')
    if location not in mockobject.objects:
        self.AddObject(location, LOCATION_IFACE, {'Latitude': point['latitude'], 'Longitude': point['longitude'], 'Accuracy': point['accuracy'],
                                                  'Altitude': -1.7976931348623157e308, 'Speed': -1.0, 'Heading': -1.0, 'Description': 'mock',
                                                  'Timestamp': dbus.Struct((dbus.UInt64(0), dbus.UInt64(0)), signature='tt')}, [])
    props['Active'] = True
    props['Location'] = dbus.ObjectPath(location)
    self.EmitSignal(CLIENT_IFACE, 'LocationUpdated', 'oo', [dbus.ObjectPath('/'), dbus.ObjectPath(location)])
