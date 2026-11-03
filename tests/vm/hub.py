#!/usr/bin/env python3
"""Cross-app tests through the instance hub, one phase per run: `hub.py <test> <phase>`. The test app prints `[<tag>] PASS|FAIL|KILL <pid>`;
every JVM is owned and reaped. Tests: providers (basic denied death observe files grant), services (start selfstop sticky private filters late),
activities (result explicit again private), pendingintents (broadcast targets cancel), notifications (click), webview (probe). HUB_VERBOSE=1: all lines."""
import os
import selectors
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, HOST_LIBS, LOGDIR, INSTANCE, SESSION, capture, stop  # noqa: E402

TEST, PHASE = sys.argv[1], sys.argv[2]
# test: (package (or {phase: package}), -D property of the phase, tag of the verdict lines, other tags shown, seconds)
TESTS = {
    'providers': ({'denied': 'hello.cpdenied', 'grant': 'hello.cpdenied'}, 'hello.cp', 'cpc', ('[cpp]',), 120),
    'services': ('hello.svccaller', 'hello.svc', 'svc', ('[svc-t]',), 120),
    'activities': ('hello.actcaller', 'hello.act', 'act', ('[act-t]',), 120),
    'pendingintents': ('hello.picreator', 'hello.pi', 'pi', ('[pi-h]',), int(os.environ.get('APKLY_PATIENCE', '120'))),
    'notifications': ('hello.ntposter', 'hello.nt', 'nt', ('[nt-h]',), 120),
    'webview': ('hello.wvprobe', None, 'wv', ('Exception', '\tat ', '[apklayer]'), 25),
}
package, prop, tag, shown, seconds = TESTS[TEST]
if isinstance(package, dict): package = package.get(PHASE, 'hello.cpclient')
shown = ('[%s]' % tag, '[apklayer-hub]') + shown
jopts = '-Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=%d' % (90000 if TEST == 'webview' else 60000)
env = dict(ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_HOST_LIBS=HOST_LIBS,
           APKLAYER_JOPTS=jopts + (' -D%s=%s' % (prop, PHASE) if prop else ' ' + os.environ.get('WV_JOPTS', '')))
if TEST == 'webview':
    env.update(APKLAYER_WEBVIEW_PROVIDER='com.android.webview', APKLAYER_LOGCAT='1')
    if os.environ.get('WV_RTDEBUG'): env['APKLAYER_RUNTIME_DEBUG'] = '1'
log = Path(LOGDIR)


def notification_clicks():
    """The hub's desktop popup for the posted notification: its default action, its first action button, then dismissed (as the desktop would)."""
    popup = None
    for _ in range(15):
        text = (log / 'hub.log').read_text(errors='replace') if (log / 'hub.log').exists() else ''
        found = [l for l in text.split('\n') if 'notification popup=' in l]
        if found: popup = found[-1].split('popup=')[1].strip(); break
        time.sleep(1)
    assert popup and popup != '0', 'the hub showed no desktop notification: ' + str(popup)
    print('[nt] desktop popup id ' + popup, flush=True)
    bus = dict(os.environ, DBUS_SESSION_BUS_ADDRESS='unix:path=%s/bus' % SESSION)
    for member, args in (('ActionInvoked', ['uint32 ' + popup, "'default'"]), ('ActionInvoked', ['uint32 ' + popup, "'action0'"]), ('NotificationClosed', ['uint32 ' + popup, 'uint32 2'])):
        subprocess.run(['gdbus', 'emit', '--session', '--object-path', '/org/freedesktop/Notifications', '--signal', 'org.freedesktop.Notifications.' + member] + args, env=bus, check=True)
        time.sleep(1)


stop()
if TEST == 'services' and PHASE == 'late':  # installed only while the test runs
    subprocess.run(['bin/apklayer', 'uninstall', 'hello.svclate'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
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
                if line.startswith('[nt] POSTED'): notification_clicks()
                if line.startswith('[%s] INSTALL ' % tag):  # a package installed while the instance runs (services late)
                    apk = Path(os.environ.get('APKLY_APKS', '.tests/apks')) / (line.split()[2] + '.apk')
                    subprocess.run(['bin/apklayer', 'install', str(apk)], env=ENV, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
                if line.startswith('[%s] KILL ' % tag) and not killed:
                    killed = True
                    os.kill(int(line.split()[2]), signal.SIGKILL)
                if '[%s] PASS' % tag in line:
                    target = log / 'app-hello.acttarget.log'
                    if TEST == 'activities' and PHASE == 'private' and target.exists():
                        assert 'PRIVATE STARTED' not in target.read_text(errors='replace'), 'private activity started'
                    if TEST == 'webview': capture('webview')  # what the WebView drew, for a look afterwards
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
