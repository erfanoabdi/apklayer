"""Helpers of the scripted corpus tests (aurora-search.py, telegram-intro.py), VM side, in this instance (tests/lib/instance.py): start an app with the
host's `views` script step repeating (every window's view tree with the TextViews' texts into the app's output), wait for a screen to settle, wait
for a text on screen (inotify, no polling), tap, screenshot, stop. Screenshot pixels and window coordinates are the same (logical px of the content)."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402

SHELL = instance.STATE / 'shell.log'


class App:
    def __init__(self, pkg, seconds, dumps=True):
        """Starts pkg for `seconds` seconds; dumps: the view tree every 3 s from the 10th second (script steps of the host: ScriptRunner "views")."""
        self.pkg, self.log = pkg, instance.OUT / (pkg + '.out')
        script = '+'.join('%d:views' % (t * 1000) for t in range(10, seconds, 3)) if dumps else ''
        env = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=%d -Dapklayer.script=%s' % (seconds * 1000, script))
        instance.assert_clean()  # a start of its own: the shell's log begins with this run
        if SHELL.exists(): SHELL.rename(SHELL.with_name('shell.log.1'))
        self.out = open(self.log, 'w')
        self.proc = subprocess.Popen(['bin/apklayer', 'run', pkg], env=env, stdout=self.out, stderr=subprocess.STDOUT)

    def settle(self, patience=20, quiet=3):
        """Blocks until the app's window has drawn and stayed still for `quiet` s (at most `patience` s: a tap that changes nothing draws nothing)."""
        off = SHELL.stat().st_size if SHELL.exists() else 0
        done = subprocess.run(['python3', 'tests/corpus/first-frame.py', str(SHELL), str(off), str(patience), '1', str(quiet)],
                              capture_output=True, text=True).stdout
        return 'FRAME' in done and 'NOFRAME' not in done

    def tap(self, x, y, window=None):
        instance.inject(window or self.pkg, 'tap:%d,%d' % (x, y))

    def text(self, needle, seconds=60):
        """Waits until a view dump after now shows the TextView text `needle`; returns the newest complete dump (its TextView texts)."""
        before, end = self.log.stat().st_size, '"%s"' % needle  # an older dump may have shown it: only what is written from now on counts
        fd = instance._libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
        instance._libc.inotify_add_watch(fd, os.fsencode(str(self.log.parent)), 0x102)  # IN_MODIFY | IN_CREATE
        until = time.monotonic() + seconds
        with selectors.DefaultSelector() as events:
            events.register(fd, selectors.EVENT_READ)
            marks = None  # the dumps written when the text showed: one more means the dump holding it is complete
            while True:
                data = self.log.read_bytes()[before:].decode(errors='replace')
                if marks is None and end in data: marks = data.count('script: views')
                if marks is not None and data.count('script: views') > marks: break
                left = until - time.monotonic()
                assert left > 0, 'no "%s" on screen of %s within %d s; the screen shows: %s' % (needle, self.pkg, seconds, self.screen()[-12:])
                if events.select(min(left, 2)): os.read(fd, 65536)
        os.close(fd)
        return self.screen()

    def until(self, test, what, seconds=60):
        """Waits until test(texts of the newest complete dump) holds (a condition `text` cannot name: a text that went away); returns those texts."""
        fd = instance._libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
        instance._libc.inotify_add_watch(fd, os.fsencode(str(self.log.parent)), 0x102)  # IN_MODIFY | IN_CREATE
        until = time.monotonic() + seconds
        with selectors.DefaultSelector() as events:
            events.register(fd, selectors.EVENT_READ)
            while True:
                texts = [t for _, t in self.screen()]
                if texts and test(texts): break
                left = until - time.monotonic()
                assert left > 0, 'no %s of %s within %d s; the screen shows: %s' % (what, self.pkg, seconds, texts[-12:])
                if events.select(min(left, 2)): os.read(fd, 65536)
        os.close(fd)
        return texts

    def screen(self):
        blocks = self.log.read_text(errors='replace').split('[apklayer] script: views')
        block = blocks[-2] if len(blocks) > 2 else blocks[-1]  # the last block may still be written
        return [(line.split()[2], line[line.index('"') + 1:line.rindex('"')]) for line in block.splitlines()
                if line.startswith('[apklayer] view ') and line.rstrip().endswith('"') and '"' in line]  # (class, text)

    def shot(self, name):
        return instance.capture(name, self.pkg)

    def stop(self):
        instance.stop()
        self.proc.wait()
        self.out.close()
        instance.assert_clean()
