#!/usr/bin/env python3
"""Own two JVMs, observe actual Binder calls/death, and reap every child."""
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
LOGDIR = instance.LOGDIR
ENV = dict(instance.ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400',
    APKLAYER_JOPTS='-Dapklayer.realbinder=true -Dapklayer.testClasses=apklayer.host.RealBinder -Dapklayer.runms=60000')
auto_mode = sys.argv[1:] == ['auto']
bound_mode = auto_mode or sys.argv[1:] == ['bound']
hub_mode = bound_mode or sys.argv[1:] == ['hub']
if hub_mode: ENV['APKLAYER_JOPTS'] += ' -Dapklayer.instancehub=true'
ENV['APKLAYER_HOST_LIBS'] = ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-graphics.so:' + ENV['APKLAYER_NATIVE'] + '/libapklayer-hal-media.so'


def stop():
    subprocess.run(['bin/apklayer', 'stop'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


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
            ready = events.select(min(15, max(0, end - time.monotonic())))
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
                    if '[hello-' in line or '[runtime]' in line or '[apklayer-hub]' in line:
                        print(line, flush=True)
                    assert '[hello-' not in line or 'FAIL' not in line, line
        raise AssertionError('Binder deadline before ' + needle)

    try:
        if hub_mode and not auto_mode:
            start('hub')
            wait_for('hub', '[apklayer-hub] READY pid=')
        if bound_mode:
            start('client', 'hello.hubclient')
            ready = wait_for('client', '[hello-hub-client] READY serverPid=')
            server_pid = int(ready.split('serverPid=')[1])
            displays = instance.mine('[l]ibapklayer_vdisplay.*hello.hubserver')
            assert not displays, 'headless service created a display: ' + '\n'.join(displays)
            os.kill(server_pid, signal.SIGKILL)
            wait_for('client', '[hello-hub-client] BIND PASS')
            wait_for('client', 'finished hello.hubclient.Main depth=0')
            if not auto_mode:
                wait_for('hub', '[apklayer-hub] died hello.hubclient')
            target_log = Path(LOGDIR + '/component-hello.hubserver.log').read_text(errors='replace')
            assert '[hello-hub-server] destroy hello.hubserver.EchoService' in target_log, target_log[-4000:]
            assert not any('displaywindow=' in line for line in target_log.splitlines()), 'service host created an app display'
            print('PASS Context.bindService: generated AIDL/Bundle/callback, permissions, null binding, process restart and unbind', flush=True)
        elif os.environ.get('APKLAYER_SANDBOX') == '1':  # the app runs as its own uid: servicemanager refuses app uids a service name, as on Android
            start('server', 'hello.binderserver')
            wait_for('server', '[hello-binder-server] published=false')
            print('PASS sandbox: an app uid may not add a service', flush=True)
        else:
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
        if not bound_mode and os.environ.get('APKLAYER_SANDBOX') != '1':
            print('PASS two JVMs: real Binder call/callback, caller identities and death notification', flush=True)
    finally:
        for proc in children.values():
            if proc.poll() is None:
                os.killpg(proc.pid, signal.SIGTERM)
        stop()
        if auto_mode:
            for line in logs.get('client', []):
                if '[apklayer] starting hub launcherPid=' in line:
                    try: os.killpg(int(line.split('launcherPid=')[1]), signal.SIGKILL)
                    except ProcessLookupError: pass
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
            Path(os.environ.get('APKLY_OUT', '/tmp') + '/apklayer-binder-' + name + '.out').write_text('\n'.join(logs[name]))
        left = instance.mine('[a]pklayer.host.Main|[b]tload|[a]pklayer-shell')
        assert not left, left
