#!/usr/bin/env python3
"""Server and client app JVMs over the real binder: the client finds the server's binder, calls it, is called back, and sees it die (kill -9)."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import time

os.chdir(Path.home() / 'apklayer-m1')
ENV = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000', APKLAYER_NATIVE=str(Path.home() / 'arm64-native'),
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400',
    APKLAYER_JOPTS='-Dapklayer.realbinder=true -Dapklayer.runms=60000')
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so'


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)


def start(package):
    return subprocess.Popen(['bin/apklayer', 'run', package], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)


def wait_for(proc, needle, log, seconds):
    """Read the app's output until a line contains the needle."""
    end = time.monotonic() + seconds
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end:
            for _ in events.select(1):
                line = proc.stdout.readline()
                if not line:
                    raise AssertionError('process ended before: ' + needle + '\n' + '\n'.join(log[-25:]))
                text = line.decode(errors='replace').rstrip()
                log.append(text)
                if '[hello-binder' in text and 'FAIL' in text:
                    raise AssertionError(text + '\n' + '\n'.join(log[-30:]))
                if needle in text:
                    return text
    raise AssertionError('timed out waiting for: ' + needle + '\n' + '\n'.join(log[-15:]))


stop()
server = client = None
logs = []
try:
    server = start('hello.binderserver')
    published = wait_for(server, '[hello-binder-server] published=true', logs, 60)
    server_pid = int(published.split('pid=')[1])
    client = start('hello.binderclient')
    server_log = []
    passed = wait_for(client, '[hello-binder-client] PASS', logs, 60)
    print(passed, flush=True)
    os.kill(server_pid, signal.SIGKILL)  # the server JVM dies without any goodbye
    wait_for(client, '[hello-binder-client] binderDied', logs, 15)
    print('PASS two JVMs over the real binder: published service, call, callback, caller identities, death notification', flush=True)
finally:
    for proc in (client, server):
        if proc is not None and proc.poll() is None:
            os.killpg(proc.pid, signal.SIGTERM)
    stop()
