#!/usr/bin/env python3
"""Own two JVMs, observe actual Binder calls/death, and reap every child."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

os.chdir(Path.home() / 'apklayer-m1')
ENV = dict(os.environ, XDG_RUNTIME_DIR='/run/user/1000', APKLAYER_NATIVE=str(Path.home() / 'arm64-native'),
    APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400',
    APKLAYER_JOPTS='-Dapklayer.realbinder=true -Dapklayer.runms=60000')
hub_mode = sys.argv[1:] == ['hub']
if hub_mode: ENV['APKLAYER_JOPTS'] += ' -Dapklayer.instancehub=true'
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so'


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['pkill', '-9', '-x', 'tail'], check=False)


stop()
children, logs, pending = {}, {}, {}
with selectors.DefaultSelector() as events:
    def start(name, package=None):
        command = ['bin/apklayer', 'hub'] if package is None else ['bin/apklayer', 'run', package]
        proc = subprocess.Popen(command, env=ENV, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, start_new_session=True)
        children[name] = proc
        logs[name], pending[name] = [], b''
        events.register(proc.stdout, selectors.EVENT_READ, name)
        return proc

    def wait_for(name, needle, seconds=30):
        end = time.monotonic() + seconds
        while time.monotonic() < end:
            found = next((line for line in logs[name] if needle in line), None)
            if found is not None:
                return found
            ready = events.select(min(3, max(0, end - time.monotonic())))
            assert ready, 'Binder test stalled before ' + needle + '\n' + '\n'.join(logs[name][-25:])
            for key, _ in ready:
                source = key.data
                data = os.read(key.fd, 65536)
                if not data:
                    events.unregister(key.fileobj)
                    if source == name:
                        raise AssertionError('JVM ended before ' + needle + '\n' + '\n'.join(logs[name][-25:]))
                    continue
                parts = (pending[source] + data).split(b'\n')
                pending[source] = parts.pop()
                for raw in parts:
                    line = raw.decode(errors='replace')
                    logs[source].append(line)
                    if '[hello-binder' in line or '[runtime]' in line or '[apklayer-hub]' in line:
                        print(line, flush=True)
                    assert '[hello-binder' not in line or 'FAIL' not in line, line
        raise AssertionError('Binder deadline before ' + needle)

    try:
        if hub_mode:
            start('hub')
            wait_for('hub', '[apklayer-hub] READY pid=')
        start('server', 'hello.binderserver')
        published = wait_for('server', '[hello-binder-server] published=true')
        server_pid = int(published.split('pid=')[1])
        start('client', 'hello.binderclient')
        wait_for('client', '[hello-binder-client] PASS')
        os.kill(server_pid, signal.SIGKILL)
        wait_for('client', '[hello-binder-client] binderDied')
        wait_for('client', 'finished hello.binderclient.Main depth=0')
        if hub_mode:
            wait_for('hub', '[apklayer-hub] died hello.binderserver')
            wait_for('hub', '[apklayer-hub] died hello.binderclient')
            print('PASS hub process registration, native caller identities and death cleanup', flush=True)
        print('PASS two JVMs: real Binder call/callback, caller identities and death notification', flush=True)
    finally:
        for proc in children.values():
            if proc.poll() is None:
                os.killpg(proc.pid, signal.SIGTERM)
        stop()
        for name, proc in children.items():
            try:
                proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait()
            try:
                os.killpg(proc.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            proc.stdout.close()
            Path('/tmp/apklayer-binder-' + name + '.out').write_text('\n'.join(logs[name]))
        left = subprocess.run(['pgrep', '-af', '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell'], capture_output=True, text=True)
        assert left.returncode != 0, left.stdout
