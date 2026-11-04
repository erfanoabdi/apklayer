"""python-dbusmock template: Ubuntu Touch's hfd-service (com.lomiri.hfd, system bus) for tests/vm/providers-host.sh; the test reads the
vibrate calls from dbusmock's call log (-l)."""

BUS_NAME = 'com.lomiri.hfd'
MAIN_OBJ = '/com/lomiri/hfd'
MAIN_IFACE = 'com.lomiri.hfd.Vibrator'
SYSTEM_BUS = True


def load(mock, parameters):
    mock.AddMethods(MAIN_IFACE, [('vibrate', 'i', '', ''), ('rumble', 'ii', '', '')])
