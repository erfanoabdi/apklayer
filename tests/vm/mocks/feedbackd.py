"""python-dbusmock template: feedbackd (org.sigxcpu.Feedback, session bus) for tests/vm/providers-host.sh. With the haptic interface
(org.sigxcpu.Feedback.Haptic.Vibrate) unless the parameter {"haptic": false} asks for an older daemon with events only. The test reads the
calls from dbusmock's call log (-l)."""
import dbus

BUS_NAME = 'org.sigxcpu.Feedback'
MAIN_OBJ = '/org/sigxcpu/Feedback'
MAIN_IFACE = 'org.sigxcpu.Feedback'
SYSTEM_BUS = False


def load(mock, parameters):
    mock.AddProperties(MAIN_IFACE, dbus.Dictionary({'Profile': 'full'}, signature='sv'))
    mock.AddMethods(MAIN_IFACE, [('TriggerFeedback', 'ssa{sv}i', 'u', 'self.last_id = getattr(self, "last_id", 0) + 1; ret = self.last_id'),
                                 ('EndFeedback', 'u', '', '')])
    if parameters.get('haptic', True):
        mock.AddMethod(MAIN_IFACE + '.Haptic', 'Vibrate', 'sa(du)', 'b', 'ret = True')
