#!/usr/bin/env python3
"""Background push, alarms, jobs and boot broadcasts with every app process gone (docs/PLAN.md "Background push"), one phase per run: `push.py <phase>`.
  push      always-on instance: an app is closed, the fake push service (hello-pushgms, as microG's McsService) delivers com.google.android.c2dm.intent.RECEIVE,
            the hub starts the app's process, its receiver posts a notification that reaches the desktop
  alarm     an alarm set by an app that is then closed fires its PendingIntent (the hub holds it: HubScheduler)
  job       a JobScheduler job of a closed app runs
  jobs      JobWorkItems dequeued in order, onStopJob when the app cancels a running job, a job held back by its network constraint, one run at its deadline
  retry     WorkManager's retry pattern: the backoff (hub-held attempt count) grows across process ends, no run before its time
  boot      an always-on instance's fresh stack: the hub sends LOCKED_BOOT_COMPLETED then BOOT_COMPLETED to the app that was never started; a restarted hub sends them no more
  autostart `apklayer instance autostart`: the marker, the unit, the instances row, off again
Every JVM is owned and reaped (this instance's stop)."""
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
from instance import ENV, LOGDIR, stop  # noqa: E402

PHASE = sys.argv[1]
LOGS = Path(LOGDIR)
APP, GMS = 'hello.push', 'hello.pushgms'
MARKER = LOGS / 'autostart'
# the VM has other apps with boot receivers: only the boot phase wakes them (APKLAYER_BOOT_BROADCASTS=0 for the others)
MICROG = 'com.google.android.gms'
env = dict(ENV, APKLAYER_BOOT_BROADCASTS={'boot': '1', 'mcs': MICROG}.get(PHASE, '0'), APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_HOST_LIBS=instance.HOST_LIBS)
children, cleanups = [], []
HUB_OUT = instance.OUT / 'hub-stdout.log'  # a hub started by `apklayer hub` prints to its launcher's stdout (the stack's start empties the state dir's logs)


def cli(*args, **kw):
    return subprocess.run(['bin/apklayer', *args], env=kw.pop('env', env), capture_output=True, text=True, **kw)


def run_app(package, phase=None):
    """`apklayer run`: the app opens (it runs until stopped)."""
    e = dict(env, APKLAYER_JOPTS='-Dapklayer.runms=60000' + (' -Dhello.push=' + phase if phase else ''))
    proc = subprocess.Popen(['bin/apklayer', 'run', package], env=e, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    children.append(proc)
    return proc


def close_app(package):
    """The app's window JVM and its hosts end (`apklayer stop <package>`): nothing of it runs after this."""
    cli('stop', package, check=True)
    assert not instance.mine(package, exact=True), 'processes of %s are left: %s' % (package, instance.mine(package, exact=True))


def reset():
    stop()
    HUB_OUT.unlink(missing_ok=True)
    for name in ('hub.log', 'component-%s.log' % APP, 'app-%s.log' % APP, 'app-%s.log' % GMS, 'autostart'):
        (LOGS / name).unlink(missing_ok=True)


def hub_log(): return (LOGS / 'hub.log').read_text(errors='replace') if (LOGS / 'hub.log').exists() else ''


def wait_hub(needle, seconds): return instance.wait_file(LOGS / 'hub.log', needle, seconds)


def component(needle, seconds): return instance.wait_file(LOGS / ('component-%s.log' % APP), needle, seconds)


def hub_alive():
    return cli('wait', 'hub').returncode == 0


def settle(seconds):
    """An evidence window for something that must not happen (the hub's idle exit is 10 s)."""
    time.sleep(seconds)


reset()
try:
    if PHASE == 'push':
        MARKER.write_text('1\n')  # what `apklayer instance autostart <id> on` writes: the hub never exits when idle
        run_app(APP, 'register')
        instance.wait_file(LOGS / ('app-%s.log' % APP), '[push] REGISTERED', 90)
        close_app(APP)
        settle(14)  # longer than the hub's idle exit: only the always-on mode keeps it
        assert 'idle, exiting' not in hub_log(), 'the always-on hub ended when idle'
        assert hub_alive(), 'no hub'
        before = hub_log().count('notification popup=')
        run_app(GMS)
        instance.wait_file(LOGS / ('app-%s.log' % GMS), '[pushgms] SENT', 90)
        text = component('[push] RECEIVE', 60)
        assert 'message=hello from the other phone' in text and 'from=1234567890' in text, text
        deadline = time.monotonic() + 20
        while hub_log().count('notification popup=') <= before and time.monotonic() < deadline: time.sleep(0.5)
        popups = [l.split('popup=')[1].strip() for l in hub_log().splitlines() if 'notification popup=' in l]
        assert len(popups) > before and popups[-1] not in ('', '0'), 'the hub showed no desktop notification: %s' % popups
        print('PASS push: a closed app woke for com.google.android.c2dm.intent.RECEIVE and its notification reached the desktop (popup %s)' % popups[-1], flush=True)
    elif PHASE == 'alarm':
        MARKER.write_text('1\n')
        run_app(APP, 'alarm')
        instance.wait_file(LOGS / ('app-%s.log' % APP), '[push] ALARM-SET', 90)
        wait_hub('alarm of hello.push in', 10)
        close_app(APP)
        component('[push] ALARM hello.push.ALARM', 60)  # (an immutable PendingIntent takes no EXTRA_ALARM_COUNT fill-in, as on Android)
        wait_hub('alarm of hello.push fired, status 0', 5)
        assert 'idle, exiting' not in hub_log()
        print('PASS alarm: set by an app that was then closed, fired by the hub into a new process', flush=True)
    elif PHASE == 'job':
        MARKER.write_text('1\n')
        run_app(APP, 'job')
        instance.wait_file(LOGS / ('app-%s.log' % APP), '[push] JOB-SET', 90)
        wait_hub('job 7 of hello.push in', 10)
        close_app(APP)
        component('[push] JOB 7', 60)
        print('PASS job: scheduled by an app that was then closed, run by the hub in a new process', flush=True)
    elif PHASE == 'jobs':
        # JobWorkItems, onStopJob, constraints and deadlines (hello.push JobsCheck), in the app's own process
        run_app(APP, 'jobs')
        app = LOGS / ('app-%s.log' % APP)
        instance.wait_file(app, '[push] JOBS-SET', 90)
        text = instance.wait_file(app, '[push] WORK-DONE', 40)
        works = [l.split('[push] WORK ')[1].split()[0] for l in text.splitlines() if '[push] WORK ' in l]
        assert works == ['a', 'b', 'c', 'd'], 'work items: %s\n%s' % (works, text)
        text = instance.wait_file(app, '[push] STOPPED 11', 20)
        assert '[push] STARTED 11' in text and '[push] STOPPED 11 reason=1' in text, text  # JobParameters.STOP_REASON_CANCELLED_BY_APP
        text = instance.wait_file(app, '[push] JOB 13', 20)  # its deadline passed: it runs without the cellular network
        wait_hub('job 12 of hello.push waits for its constraints (7)', 5)  # STOP_REASON_CONSTRAINT_CONNECTIVITY
        settle(3)
        text = app.read_text(errors='replace')
        assert '[push] JOB 12' not in text and text.count('[push] STARTED 11') == 1, 'job 12 ran without its network, or the cancelled 11 again:\n' + text
        print('PASS jobs: work items dequeued in order (one enqueued while running), onStopJob on cancel, a constraint holds a job back until its deadline', flush=True)
    elif PHASE == 'retry':
        # WorkManager's retry: the hub's backoff count outlives the app's process (closed after each run), so no run comes before its time
        MARKER.write_text('1\n')
        run_app(APP, 'retry')
        instance.wait_file(LOGS / ('app-%s.log' % APP), '[push] RETRY-SET', 90)
        wait_hub('job 8 of hello.push again in 10000 ms (attempt 1)', 20)
        close_app(APP)
        component('[push] RUN 8 attempt 2', 40)
        wait_hub('job 8 of hello.push again in 20000 ms (attempt 2)', 10)
        close_app(APP)
        text = component('[push] DONE 8', 50)
        logs = (LOGS / ('app-%s.log' % APP)).read_text(errors='replace') + text
        assert '[push] EARLY' not in logs, 'a run before its backoff:\n' + logs
        print('PASS retry: the backoff grows with the hub-held attempt count across process ends (10 s, 20 s), no run before its time', flush=True)
    elif PHASE == 'boot':
        MARKER.write_text('1\n')  # an always-on instance is the one that boots
        hub = subprocess.Popen(['bin/apklayer', 'hub'], env=env, stdout=open(HUB_OUT, 'a'), stderr=subprocess.STDOUT, start_new_session=True)
        children.append(hub)
        assert hub_alive(), 'no hub'
        text = component('[push] BOOT android.intent.action.BOOT_COMPLETED', 60)
        assert text.index('LOCKED_BOOT_COMPLETED') < text.index('android.intent.action.BOOT_COMPLETED'), 'wrong order:\n' + text
        assert 'user=0' in text and 'sending android.intent.action.BOOT_COMPLETED' in instance.wait_file(HUB_OUT, 'sending android.intent.action.BOOT_COMPLETED', 5)
        # ordered, as UserController sends it: one receiver (one app's process start) after the other
        assert 'broadcast android.intent.action.BOOT_COMPLETED from android ordered' in instance.wait_file(HUB_OUT, 'broadcast android.intent.action.BOOT_COMPLETED from', 5), 'not ordered'
        # the same stack, a new hub (the first ended): not a boot again
        jvm = int((LOGS / 'hub.pid').read_text().split()[0])
        os.kill(jvm, signal.SIGTERM)
        hub.wait(30)
        hub2 = subprocess.Popen(['bin/apklayer', 'hub'], env=env, stdout=open(HUB_OUT, 'a'), stderr=subprocess.STDOUT, start_new_session=True)
        children.append(hub2)
        assert hub_alive(), 'no second hub'
        settle(6)
        log = (LOGS / ('component-%s.log' % APP)).read_text(errors='replace')
        assert log.count('[push] BOOT android.intent.action.BOOT_COMPLETED') == 1 and log.count('[push] BOOT android.intent.action.LOCKED_BOOT_COMPLETED') == 1, 'the boot broadcasts came again:\n' + log
        print('PASS boot: LOCKED_BOOT_COMPLETED then BOOT_COMPLETED woke a closed app once per stack start', flush=True)
    elif PHASE == 'mcs':
        # microG's own push connection with no app open: the always-on instance boots, microG (its /system/etc/microg.xml defaults: device registration
        # and cloud messaging on) checks in with Google and McsService logs in to mtalk.google.com; a dropped connection comes back by itself
        MARKER.write_text('1\n')
        mlog = LOGS / ('component-%s-org.microg.gms.gcm.McsService.log' % MICROG)  # McsService runs in its own process (":persistent")
        mlog.unlink(missing_ok=True)
        # heartbeats each minute (microG's shortest setting) instead of 5: its heartbeat alarm (a service PendingIntent) fires within the test
        prefs = Path(ENV.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer' / instance.INSTANCE / 'app-data' / MICROG / 'app/shared_prefs' / ('%s_preferences.xml' % MICROG)
        text = prefs.read_text() if prefs.exists() else "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>\n"
        for key in ('gcm_network_wifi', 'gcm_network_other'):
            if 'name="%s"' % key not in text: text = text.replace('</map>', '    <string name="%s">1</string>\n</map>' % key)
        prefs.parent.mkdir(parents=True, exist_ok=True)
        prefs.write_text(text)
        hub = subprocess.Popen(['bin/apklayer', 'hub'], env=dict(env, APKLAYER_LOGCAT='1'), stdout=open(HUB_OUT, 'a'), stderr=subprocess.STDOUT, start_new_session=True)
        children.append(hub)
        assert hub_alive(), 'no hub'
        text = instance.wait_file(mlog, 'GmsGcmMcsSvc: Logged in', 150)
        assert 'Logged in' in text, 'McsService never logged in:\n' + text[-3000:]
        print('mcs: logged in to mtalk.google.com with no app open', flush=True)
        # an app registers for cloud messages as Firebase does: microG asks Google (c2dm register3) and answers through the app's Messenger
        run_app(APP, 'c2dm')
        answer = instance.wait_file(LOGS / ('app-%s.log' % APP), '[push] C2DM ', 90)
        print('mcs: registration answered: ' + answer.split('[push] C2DM ')[1].splitlines()[0], flush=True)
        close_app(APP)
        text = instance.wait_file(mlog, 'Outgoing message: HeartbeatPing', 100)
        print('mcs: heartbeat sent from its alarm', flush=True)
        # a network change (an address on the default interface): the hub announces CONNECTIVITY_ACTION, microG's TriggerReceiver gets it
        dev = subprocess.run(['ip', '-o', 'route', 'show', 'default'], capture_output=True, text=True).stdout.split(' dev ')[1].split()[0]
        subprocess.run(['sudo', '-n', 'ip', 'addr', 'add', '10.254.254.1/32', 'dev', dev], check=True)
        try: instance.wait_file(mlog, 'act=android.net.conn.CONNECTIVITY_CHANGE', 30)
        finally: subprocess.run(['sudo', '-n', 'ip', 'addr', 'del', '10.254.254.1/32', 'dev', dev], check=False)
        print('mcs: CONNECTIVITY_CHANGE reached microG', flush=True)
        # the connection drops (as a network change or a resume ends it): microG reconnects on its own (its reconnect alarm, held by HubScheduler)
        killed = subprocess.run(['sudo', '-n', 'ss', '-K', 'dport', '=', '5228'], capture_output=True, text=True)
        assert killed.returncode == 0, killed.stderr
        deadline = time.monotonic() + 150
        while mlog.read_text(errors='replace').count('GmsGcmMcsSvc: Logged in') < 2 and time.monotonic() < deadline: time.sleep(1)
        text = mlog.read_text(errors='replace')
        assert text.count('GmsGcmMcsSvc: Logged in') >= 2, 'no new login after the connection dropped:\n' + text[-3000:]
        print('PASS mcs: McsService logged in with no app open, sent its heartbeat, got CONNECTIVITY_CHANGE and logged in again after its connection dropped', flush=True)
    elif PHASE == 'autostart':
        iid = instance.INSTANCE
        # systemctl --user reaches the user's manager through the real session directory, not the instance's own XDG_RUNTIME_DIR
        env = dict(env, XDG_RUNTIME_DIR=instance.SESSION, DBUS_SESSION_BUS_ADDRESS='unix:path=%s/bus' % instance.SESSION)
        assert cli('instance', 'autostart', iid).stdout.strip() == 'off'
        # the unit goes where the user's systemd manager reads it (its own XDG_CONFIG_HOME), not under the test's private XDG_CONFIG_HOME
        shown = subprocess.run(['systemctl', '--user', 'show-environment'], capture_output=True, text=True, env=env).stdout.splitlines()
        config = next((l.split('=', 1)[1] for l in shown if l.startswith('XDG_CONFIG_HOME=')), str(Path.home() / '.config'))
        unit = Path(config) / 'systemd/user/apklayer-instance@.service'
        unit_was = unit.exists()
        on = cli('instance', 'autostart', iid, 'on', '--no-start')
        if not unit_was: cleanups.append(lambda: (unit.unlink(missing_ok=True), subprocess.run(['systemctl', '--user', 'daemon-reload'], env=env)))
        cleanups.append(lambda: cli('instance', 'autostart', iid, 'off'))  # never leave the unit enabled (the owner's desktop)
        assert on.returncode == 0, on.stderr
        assert MARKER.exists() and cli('instance', 'autostart', iid).stdout.strip() == 'on'
        assert 'ExecStart=' in unit.read_text() and ' --instance %i hub' in unit.read_text(), unit.read_text()
        listed = [l.split('\t') for l in cli('instances').stdout.splitlines() if l.split('\t')[0] == iid]
        assert listed and listed[0][4] == '1', listed
        enabled = subprocess.run(['systemctl', '--user', 'is-enabled', 'apklayer-instance@%s.service' % iid], capture_output=True, text=True, env=env)
        assert enabled.stdout.strip() == 'enabled', enabled.stdout + enabled.stderr
        assert cli('instance', 'autostart', iid, 'off').returncode == 0
        assert not MARKER.exists() and cli('instance', 'autostart', iid).stdout.strip() == 'off'
        enabled = subprocess.run(['systemctl', '--user', 'is-enabled', 'apklayer-instance@%s.service' % iid], capture_output=True, text=True, env=env)
        assert enabled.stdout.strip() != 'enabled', enabled.stdout
        print('PASS autostart: marker, unit, instances row, off again', flush=True)
    else:
        raise SystemExit('unknown phase ' + PHASE)
finally:
    for cleanup in reversed(cleanups): cleanup()
    for child in children:
        try: os.killpg(child.pid, signal.SIGTERM)
        except OSError: pass
    MARKER.unlink(missing_ok=True)
    stop()
