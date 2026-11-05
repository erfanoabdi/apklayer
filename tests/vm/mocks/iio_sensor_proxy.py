"""python-dbusmock template: iio-sensor-proxy (net.hadess.SensorProxy, system bus) with an orientation, a lux light sensor, proximity and
a compass (which the backend does not list), for tests/vm/providers-host.sh. Values change with org.freedesktop.DBus.Mock.UpdateProperties (PropertiesChanged, as the proxy)."""
import dbus

BUS_NAME = 'net.hadess.SensorProxy'
MAIN_OBJ = '/net/hadess/SensorProxy'
MAIN_IFACE = 'net.hadess.SensorProxy'
SYSTEM_BUS = True


def load(mock, parameters):
    mock.AddProperties(MAIN_IFACE, dbus.Dictionary({
        'HasAccelerometer': True, 'AccelerometerOrientation': 'right-up',
        'HasAmbientLight': True, 'LightLevelUnit': 'lux', 'LightLevel': dbus.Double(123.0),
        'HasProximity': True, 'ProximityNear': True}, signature='sv'))
    mock.AddMethods(MAIN_IFACE, [(m + s, '', '', '') for m in ('Claim', 'Release') for s in ('Accelerometer', 'Light', 'Proximity')])
    mock.AddObject(MAIN_OBJ + '/Compass', MAIN_IFACE + '.Compass', {'HasCompass': True, 'CompassHeading': dbus.Double(42.0)},
                   [('ClaimCompass', '', '', ''), ('ReleaseCompass', '', '', '')])
