#!/usr/bin/env python3
"""Cross-app tests through the instance hub, one phase per run: `hub.py <test> <phase>`. The test app prints `[<tag>] PASS|FAIL|KILL <pid>`;
every JVM is owned and reaped. Tests: providers (basic denied death observe files grant idle: basic, then the provider's host ends once idle), services (start selfstop sticky private filters own late oneway respawn rebind framework),
activities (result explicit again private up), processes (kill: an activity in android:process ":remote"), pendingintents (broadcast targets cancel forresult), notifications (click cancel muted channels), webview (probe). HUB_VERBOSE=1: all lines."""
import os
import selectors
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402
from instance import ENV, HOST_LIBS, LOGDIR, INSTANCE, SESSION, capture, stop  # noqa: E402

TEST, PHASE = sys.argv[1], sys.argv[2]
# test: (package (or {phase: package}), -D property of the phase, tag of the verdict lines, other tags shown, seconds)
TESTS = {
    'providers': ({'denied': 'hello.cpdenied', 'grant': 'hello.cpdenied'}, 'hello.cp', 'cpc', ('[cpp]',), 120),
    'services': ('hello.svccaller', 'hello.svc', 'svc', ('[svc-t]',), 120),
    'activities': ('hello.actcaller', 'hello.act', 'act', ('[act-t]',), 120),
    'processes': ('hello.actprocess', None, 'ap', (), 120),
    'pendingintents': ('hello.picreator', 'hello.pi', 'pi', ('[pi-h]',), int(os.environ.get('APKLY_PATIENCE', '120'))),
    'notifications': ('hello.ntposter', 'hello.nt', 'nt', ('[nt-h]',), 120),
    'webview': ('hello.wvprobe', None, 'wv', ('Exception', '\tat ', '[apklayer]'), 60),  # cold renderer plus two configuration contexts
}
package, prop, tag, shown, seconds = TESTS[TEST]
if isinstance(package, dict): package = package.get(PHASE, 'hello.cpclient')
shown = ('[%s]' % tag, '[apklayer-hub]') + shown
jopts = '-Dapklayer.runms=%d' % (90000 if TEST == 'webview' else 60000)
env = dict(ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_HOST_LIBS=HOST_LIBS,
           APKLAYER_JOPTS=jopts + (' -D%s=%s' % (prop, 'basic' if PHASE == 'idle' else PHASE) if prop else ' ' + os.environ.get('WV_JOPTS', '')))
if PHASE == 'idle': env['APKLAYER_JOPTS'] += ' -Dapklayer.hostidlems=4000'  # the hub's hosts get the launch's options (the hub inherits its environment)
if TEST == 'webview':
    env.update(APKLAYER_WEBVIEW_PROVIDER='com.android.webview', APKLAYER_LOGCAT='1')
    if os.environ.get('WV_RTDEBUG'): env['APKLAYER_RUNTIME_DEBUG'] = '1'
log = Path(LOGDIR)
def hub_wait(needle, seconds): return instance.wait_file(log / 'hub.log', needle, seconds)  # the stack's logs start empty


def wait_popup():
    """The id of the hub's desktop popup for the posted notification."""
    found = hub_wait('notification popup=', 15)
    popup = [l for l in found.split('\n') if 'notification popup=' in l][-1].split('popup=')[1].strip()
    assert popup and popup != '0', 'the hub showed no desktop notification: ' + str(popup)
    return popup


def notification_clicks():
    """The hub's desktop popup for the posted notification: its default action, its first action button, then dismissed (as the desktop would)."""
    popup = wait_popup()
    print('[nt] desktop popup id ' + popup, flush=True)
    bus = dict(os.environ, DBUS_SESSION_BUS_ADDRESS='unix:path=%s/bus' % SESSION)
    for member, args in (('ActionInvoked', ['uint32 ' + popup, "'default'"]), ('ActionInvoked', ['uint32 ' + popup, "'action0'"]), ('NotificationClosed', ['uint32 ' + popup, 'uint32 2'])):
        subprocess.run(['gdbus', 'emit', '--session', '--object-path', '/org/freedesktop/Notifications', '--signal', 'org.freedesktop.Notifications.' + member] + args, env=bus, check=True)
        time.sleep(1)


def own_data_dir(pkg):
    """The app's data (its WebView profile) is in this instance's directory, not one every instance of the data home shares (cli/layout appData)."""
    info = dict(l.split('\t', 1) for l in subprocess.run(['bin/apklayer', 'info', pkg], env=ENV, capture_output=True, text=True, check=True).stdout.splitlines() if '\t' in l)
    want = '/apklayer/%s/app-data/%s' % ('0' if INSTANCE == 'default' else INSTANCE, pkg)
    assert info['dataDir'].endswith(want), 'app data of %s not per instance: %s' % (pkg, info['dataDir'])
    profile = Path(info['dataDir'], 'app/app_webview')
    # sandboxed (APKLAYER_SANDBOX=1) the data is the app uid's own, 0700: looked at as that uid, in its view (no sudo: Parallels has none without a password)
    sandboxed = ENV.get('APKLAYER_SANDBOX', os.environ.get('APKLAYER_SANDBOX')) == '1'
    assert (subprocess.run(['bin/apklayer', 'sandbox-exec', pkg, '--', 'test', '-d', str(profile)], env=ENV).returncode == 0 if sandboxed
            else profile.is_dir()), 'no WebView profile in ' + info['dataDir']
    # an apps/<pkg>/data of before 2026-11-04 may stay: it is the default instance's, moved there at that instance's next run (cli/apps.cpp appDataDir)
    print('[wv] app data per instance: ' + info['dataDir'], flush=True)


stop()
if TEST == 'services' and PHASE == 'late':  # installed only while the test runs
    subprocess.run(['bin/apklayer', 'uninstall', 'hello.svclate'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
MUTE = ['bin/apklayer', 'permissions', '%s', 'hello.ntposter', 'android.permission.POST_NOTIFICATIONS']
if TEST == 'notifications' and PHASE == 'muted':  # the user turns the app's notifications off (granted again afterwards)
    subprocess.run([a.replace('%s', 'revoke') for a in MUTE], env=ENV, check=True)
proc = None
lines = []
try:
    proc = subprocess.Popen(['bin/apklayer', 'run', package], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    end = time.monotonic() + seconds
    pending = b''
    killed = False
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end:
            if not events.select(2): continue
            data = os.read(proc.stdout.fileno(), 65536)
            if not data: raise AssertionError('client ended (exit status %s)\n' % proc.wait() + '\n'.join(lines[-20:]))
            parts = (pending + data).split(b'\n')
            pending = parts.pop()
            for raw in parts:
                line = raw.decode(errors='replace')
                lines.append(line)
                if any(t in line for t in shown) or os.environ.get('HUB_VERBOSE'): print(line, flush=True)
                if '[%s] FAIL' % tag in line:
                    for _ in range(5):
                        if events.select(0.5): lines.extend(os.read(proc.stdout.fileno(), 65536).decode(errors='replace').split('\n'))
                    raise AssertionError(line + '\n' + '\n'.join(l for l in lines[-60:] if 'symbol may' not in l))
                if line.startswith('[nt] POSTED click'): notification_clicks()
                if line.startswith('[nt] CANCELLED'):  # the app cancelled it: the hub closes the desktop popup, the desktop confirms (reason 3: CloseNotification)
                    popup = wait_popup()
                    hub_wait('notification cancelled, popup %s closed' % popup, 10)
                    hub_wait('popup %s closed by the desktop, reason 3' % popup, 10)
                    print('[nt] the app cancelled it: popup %s closed' % popup, flush=True)
                    print('PASS %s %s' % (TEST, PHASE), flush=True)
                    raise SystemExit(0)
                if '[nt] PASS muted' in line:
                    assert 'notification popup=' not in hub_wait('dropped: notifications of hello.ntposter are off', 10), 'a muted app showed a desktop popup'
                if line.startswith('[%s] INSTALL ' % tag):  # a package installed while the instance runs (services late)
                    apk = Path(os.environ.get('APKLY_APKS', '.tests/apks')) / (line.split()[2] + '.apk')
                    subprocess.run(['bin/apklayer', 'install', str(apk)], env=ENV, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
                    # Acknowledge the CLI install in the app's own files, without exposing the host package store.
                    ready = line.split(' ', 3)[3]
                    if ENV.get('APKLAYER_SANDBOX', os.environ.get('APKLAYER_SANDBOX')) == '1':
                        subprocess.run(['bin/apklayer', 'sandbox-exec', package, '--', 'touch', ready], env=ENV, check=True)
                    else:
                        Path(ready).touch()
                if line.startswith('[%s] KILL ' % tag) and not killed:
                    killed = True
                    os.kill(int(line.split()[2]), signal.SIGKILL)
                if '[%s] PASS' % tag in line:
                    target = log / 'app-hello.acttarget.log'
                    if TEST == 'processes':  # the remote activity's process: its own log (the hub started it), ended by its own killProcess
                        remote = (log / 'app-hello.actprocess:remote.log').read_text(errors='replace')
                        assert 'Process.killProcess(myPid): this process ends' in remote, 'no killProcess in the remote log:\n' + remote[-2000:]
                        hub_wait('starting app hello.actprocess#hello.actprocess:remote', 1)
                    if TEST == 'activities' and PHASE == 'private' and target.exists():
                        assert 'PRIVATE STARTED' not in target.read_text(errors='replace'), 'private activity started'
                    if TEST == 'webview':
                        capture('webview', package)  # what the WebView drew, for a look afterwards
                        own_data_dir(package)
                    if PHASE == 'idle':  # no service, no call for 4 s: the provider's host ends on its own (ComponentHost), the client keeps running
                        instance.wait_file(log / 'component-hello.cpprovider.log', 'component-host idle for 4000 ms', 30)
                        hub_wait('host hello.cpprovider exited', 10)
                        print('[cpc] provider host ended once idle', flush=True)
                    print('PASS %s %s' % (TEST, PHASE), flush=True)
                    raise SystemExit(0)
    import hangdump
    hangdump.dump(INSTANCE)
    raise AssertionError('timed out\n' + '\n'.join(lines[-25:]))
finally:
    if TEST == 'webview':
        for name in [log / 'hub.log'] + sorted(log.glob('component-com.android.webview*.log')):
            if name.exists(): print('--- %s (tail)' % name.name, *[l[:200] for l in name.read_text(errors='replace').split('\n') if 'symbol may' not in l][-25:], sep='\n', flush=True)
    if proc is not None and proc.poll() is None: os.killpg(proc.pid, signal.SIGTERM)
    stop()
    if TEST == 'notifications' and PHASE == 'muted': subprocess.run([a.replace('%s', 'grant') for a in MUTE], env=ENV, check=True)
