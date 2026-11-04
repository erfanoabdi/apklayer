#!/usr/bin/env python3
"""The package installer through the hub (HubInstaller): tests/apps/hello-installer installs the payload APKs from its assets with PackageInstaller
sessions (base + config split, an update that keeps the app's data, another signer and a downgrade refused, abandon, uninstall, SessionCallback.onFinished),
the legacy intents (their startActivityForResult result included) and finally an update of itself, and prints `[inst] PASS|FAIL <step>`. Every confirmation
is a hub dialog in this instance's shell: the first one (the install source) is answered Cancel, every later one with its yes button, by test input at the
coordinates the hub logs. The payload, launched after the install and after the update, tells the installer the string only its split defines and the data it
kept from before the update. Before that, `apklayer install` gets the payload as .xapk, .apkm and .apks bundles (the other ABI's split left out) and unsigned
(refused); hello-legacy-receiver (target 25) must get the package broadcasts in its manifest receiver, the installer's own (target 34) must not.
The first run leaves a preapproved session; a second start after the instance stopped (hub and all) finds it and commits it without a question."""
import io
import os
import selectors
import shutil
import subprocess
import sys
import time
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, HOST_LIBS, INSTANCE, LOGDIR, inject, stop  # noqa: E402

STEPS = ['abandon', 'cancel', 'install', 'session finished', 'split resource', 'update', 'data kept', 'mismatch', 'downgrade', 'uninstall', 'source allowed',
         'view apk', 'install result', 'delete intent', 'preapproved', 'self update']
data = Path(ENV.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer'
log = Path(LOGDIR)
work = Path(ENV.get('APKLY_OUT', log / 'installer-test'))
env = dict(ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_HOST_LIBS=HOST_LIBS,
           APKLAYER_JOPTS='-Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=400000')
passed, failed = set(), []
# the payload has an arm64_v8a and an x86_64 split: the host's is kept, the other left out
HOST_ABI, OTHER_ABI = ('x86_64', 'arm64_v8a') if os.uname().machine == 'x86_64' else ('arm64_v8a', 'x86_64')


def cli(*args):
    return subprocess.run(['bin/apklayer', *args], env=ENV, capture_output=True, text=True)


def bundles():
    """The payload's base and splits (the installer's assets) as the bundle formats stores ship; the other ABI's split is left out, an unsigned APK refused."""
    shutil.rmtree(work, ignore_errors=True); work.mkdir(parents=True)
    with zipfile.ZipFile(data / 'apps/hello.installer/base.apk') as installer:
        apks = {n[7:]: installer.read(n) for n in installer.namelist() if n.startswith('assets/hello-payload') and not n.startswith('assets/hello-payload-other')}
    base, splits = apks['hello-payload.apk'], {n: b for n, b in apks.items() if n.startswith('hello-payload-split_')}
    layouts = {'xapk': {'hello.payload.apk': base, 'manifest.json': b'{"package_name": "hello.payload"}', **{'config.' + n[20:]: b for n, b in splits.items()}},
               'apkm': {'base.apk': base, 'info.json': b'{"pname": "hello.payload"}', **{'split_config.' + n[20:]: b for n, b in splits.items()}},
               'apks': {'splits/base-master.apk': base, 'toc.pb': b'', **{'splits/base-' + n[20:]: b for n, b in splits.items()}}}
    for kind, entries in layouts.items():
        bundle = work / ('payload.' + kind)
        with zipfile.ZipFile(bundle, 'w') as z:
            for name, content in entries.items(): z.writestr(name, content)
        out = cli('install', str(bundle))
        info = (data / 'apps/hello.payload/info.properties')
        props = dict(l.split('=', 1) for l in info.read_text().splitlines() if '=' in l) if info.exists() else {}
        ok = out.returncode == 0 and 'leaving out config.' + OTHER_ABI in out.stdout + out.stderr and props.get('splits') == 'config.%s,config.xxhdpi' % HOST_ABI
        report('bundle ' + kind, ok, 'exit %d splits %s: %s' % (out.returncode, props.get('splits'), (out.stdout + out.stderr)[-600:]))
        cli('uninstall', 'hello.payload')
    unsigned = work / 'unsigned.apk'
    with zipfile.ZipFile(unsigned, 'w') as out_zip, zipfile.ZipFile(io.BytesIO(base)) as signed:
        for item in signed.infolist():
            if not item.filename.startswith('META-INF/'): out_zip.writestr(item, signed.read(item.filename))
    out = cli('install', str(unsigned))
    report('unsigned refused', out.returncode == 5 and 'INSTALL_PARSE_FAILED_NO_CERTIFICATES' in out.stderr and not (data / 'apps/hello.payload').exists(),
           'exit %d: %s' % (out.returncode, (out.stdout + out.stderr)[-400:]))


def report(step, ok, detail):
    print('[inst] %s %s%s' % ('PASS' if ok else 'FAIL', step, '' if ok else ': ' + detail), flush=True)
    (passed.add if ok else failed.append)(step)


stop()
cli('uninstall', 'hello.payload')
# the installer updates itself at the end: its first version goes back afterwards (the next run installs that one again)
first = work.with_name('hello-installer-v1.apk')
first.parent.mkdir(parents=True, exist_ok=True)
shutil.copy(data / 'apps/hello.installer/base.apk', first)
bundles()
STEPS += ['bundle xapk', 'bundle apkm', 'bundle apks', 'unsigned refused', 'legacy receiver']
(data / INSTANCE / 'install-sources').unlink(missing_ok=True)
shutil.rmtree(data / INSTANCE / 'install-sessions', ignore_errors=True)
for name in ('hub.log', 'component-hello.installer.log', 'component-hello.legacyrx.log', 'app-hello.installer.log'): (log / name).unlink(missing_ok=True)


def own_logs():
    return [l for name in ('component-hello.installer.log', 'app-hello.installer.log') if (log / name).exists() for l in (log / name).read_text(errors='replace').split('\n')]


def run_installer(done, first_no):
    """`apklayer run hello.installer` until done(its output and its later processes' logs) or the deadline, answering the hub's dialogs (the first
    with Cancel when first_no); its output lines and the questions asked."""
    lines, questions = [], []
    proc = subprocess.Popen(['bin/apklayer', 'run', 'hello.installer'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        end = time.monotonic() + 480
        pending = b''
        with selectors.DefaultSelector() as events:
            events.register(proc.stdout, selectors.EVENT_READ)
            open_out = True
            while time.monotonic() < end and not done(lines + own_logs()):
                if not open_out: time.sleep(0.5)  # its output ended (the self update stopped it): the new version's log tells
                elif events.select(1):
                    chunk = os.read(proc.stdout.fileno(), 65536)
                    if not chunk: events.unregister(proc.stdout); open_out = False; continue
                    parts = (pending + chunk).split(b'\n'); pending = parts.pop()
                    for raw in parts:
                        line = raw.decode(errors='replace'); lines.append(line)
                        if '[inst]' in line or 'FATAL' in line or '[apklayer] package ' in line: print(line, flush=True)
                hub = (log / 'hub.log').read_text(errors='replace').split('\n') if (log / 'hub.log').exists() else []
                assert not any('A fatal error' in l for l in hub), 'the hub crashed:\n' + '\n'.join(hub[-40:])
                shown = [l for l in hub if 'dialog shown:' in l]
                if len(shown) > len(questions):  # a new question: Cancel for the first, yes for the rest
                    question = shown[len(questions)]
                    field = 'no=' if first_no and not questions else 'yes='
                    xy = question.split(field)[1].split()[0]
                    questions.append(question.split('dialog shown: ')[1])
                    print('ANSWER %s %s' % (field[:-1], questions[-1]), flush=True)
                    for attempt in range(20):  # the window takes input once the compositor configured it
                        try: inject('apklayer.installer', 'click:' + xy); break
                        except AssertionError as missing:
                            if 'no such window' not in str(missing) or attempt == 19: raise
                            time.sleep(0.5)
    finally:
        stop()
        proc.wait()
    return lines, questions


try:
    # until the updated installer reports (its first version is stopped by its own update, its output then ends)
    lines, _ = run_installer(lambda out: any('self update' in l for l in out), True)
    later = own_logs()  # before the restore below: its stack start begins the instance's logs anew
    legacy = (log / 'component-hello.legacyrx.log').read_text(errors='replace') if (log / 'component-hello.legacyrx.log').exists() else ''
finally:
    restored = cli('install', '-d', str(first))
    print('restored the installer\'s first version: exit %d' % restored.returncode, flush=True)
# a new start of the instance (hub and all): the session the first run prepared and had preapproved is committed without a question
STEPS += ['session kept', 'preapproval kept', 'no question']
again, questions = run_installer(lambda out: any('[inst] DONE' in l for l in out), False)
report('no question', not any(q.startswith(('"Install', '"Update')) for q in questions), 'asked %s' % questions)
lines += again
for line in lines + later:
    if '[inst] PASS ' in line: passed.add(line.split('[inst] PASS ')[1].strip())
    elif '[inst] FAIL ' in line: failed.append(line.split('[inst] FAIL ')[1].strip()); print(line, flush=True)
report('legacy receiver', '[legacy] android.intent.action.PACKAGE_ADDED hello.payload' in legacy and '[legacy] android.intent.action.PACKAGE_REMOVED hello.payload' in legacy,
       'its log: ' + legacy[-600:])
checks = {s: s in passed for s in STEPS}
for name, ok in checks.items(): print('%s %s' % ('PASS' if ok else 'FAIL', name))
if failed: print('FAIL lines: %s' % failed)
sys.exit(0 if all(checks.values()) and not failed else 1)
