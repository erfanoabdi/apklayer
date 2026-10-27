#!/usr/bin/env python3
"""Temporary IBus engine for the real Mutter -> text-input-v3 integration test.

Real n/i/space keys produce preedit 'ni' and commit '你好'. The test restores
its previous engine and terminates this foreground child on every exit path.
"""
import gi
gi.require_version('IBus', '1.0')
from gi.repository import GLib, IBus


class TestEngine(IBus.Engine):
    __gtype_name__ = 'ApklayerInputTestEngine'

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.preedit = ''

    def do_process_key_event(self, keyval, keycode, state):
        if state & IBus.ModifierType.CONTROL_MASK:
            return False
        if keyval not in (ord('n'), ord('i'), ord(' ')):
            return False
        if state & IBus.ModifierType.RELEASE_MASK:
            return True
        if keyval == ord(' '):
            if not self.preedit:
                return False
            self.commit_text(IBus.Text.new_from_string('你好'))
            self.preedit = ''
            self.hide_preedit_text()
            print('ENGINE committed 你好', flush=True)
        else:
            self.preedit += chr(keyval)
            self.update_preedit_text(IBus.Text.new_from_string(self.preedit), len(self.preedit), True)
            print('ENGINE preedit ' + self.preedit, flush=True)
        return True


IBus.init()
bus = IBus.Bus.new()
assert bus.is_connected()
factory = IBus.Factory.new(bus.get_connection())
factory.add_engine('apklayer-test', TestEngine)
component = IBus.Component.new('org.freedesktop.IBus.ApklayerTest', 'apklayer input regression',
    '1', 'MIT', 'apklayer', '', '', '')
component.add_engine(IBus.EngineDesc.new('apklayer-test', 'apklayer test', 'temporary regression engine',
    'zh', 'MIT', 'apklayer', '', 'us'))
assert bus.register_component(component)
print('ENGINE ready', flush=True)
GLib.MainLoop().run()
