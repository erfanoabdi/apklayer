#!/usr/bin/env python3
"""The package installer through the hub (HubInstaller): tests/apps/hello-installer installs the payload APKs from its assets with PackageInstaller
sessions (base + config split, an update that keeps the app's data, another signer and a downgrade refused, abandon, uninstall) and prints
`[inst] PASS|FAIL <step>`. Every confirmation is a hub dialog in this instance's shell: the first one (the install source) is answered Cancel, every
later one with its yes button, by test input at the coordinates the hub logs. The payload, launched after the install and after the update, tells
the installer the string only its split defines and the data it kept from before the update."""
import os
import selectors
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, HOST_LIBS, INSTANCE, LOGDIR, inject, stop  # noqa: E402

STEPS = ['abandon', 'cancel', 'install', 'split resource', 'update', 'data kept', 'mismatch', 'downgrade', 'uninstall', 'source allowed', 'view apk', 'delete intent']
data = Path(ENV.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'apklayer'
log = Path(LOGDIR)
env = dict(ENV, APKLAYER_DISPLAY_WIDTH='480', APKLAYER_DISPLAY_HEIGHT='400', APKLAYER_HOST_LIBS=HOST_LIBS,
           APKLAYER_JOPTS='-Dapklayer.realbinder=true -Dapklayer.instancehub=true -Dapklayer.runms=400000')

stop()
subprocess.run(['bin/apklayer', 'uninstall', 'hello.payload'], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
(data / INSTANCE / 'install-sources').unlink(missing_ok=True)
(log / 'hub.log').unlink(missing_ok=True)
passed, answered, lines = set(), 0, []
proc = subprocess.Popen(['bin/apklayer', 'run', 'hello.installer'], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
try:
    end = time.monotonic() + 420
    pending = b''
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end and not any('[inst] DONE' in l for l in lines):
            if events.select(1):
                chunk = os.read(proc.stdout.fileno(), 65536)
                if not chunk: break
                parts = (pending + chunk).split(b'\n'); pending = parts.pop()
                for raw in parts:
                    line = raw.decode(errors='replace'); lines.append(line)
                    if '[inst]' in line or 'FATAL' in line or '[apklayer] package ' in line: print(line, flush=True)
                    if '[inst] PASS ' in line: passed.add(line.split('[inst] PASS ')[1].strip())
            hub = (log / 'hub.log').read_text(errors='replace').split('\n') if (log / 'hub.log').exists() else []
            assert not any('A fatal error' in l for l in hub), 'the hub crashed:\n' + '\n'.join(hub[-40:])
            shown = [l for l in hub if 'dialog shown:' in l]
            if len(shown) > answered:  # a new question: Cancel for the first, yes for the rest
                question = shown[answered]
                field = 'no=' if answered == 0 else 'yes='
                xy = question.split(field)[1].split()[0]
                print('ANSWER %s %s' % (field[:-1], question.split('dialog shown: ')[1]), flush=True)
                for attempt in range(20):  # the window takes input once the compositor configured it
                    try: inject('apklayer.installer', 'click:' + xy); break
                    except AssertionError as missing:
                        if 'no such window' not in str(missing) or attempt == 19: raise
                        time.sleep(0.5)
                answered += 1
finally:
    stop()
    proc.wait()

checks = {s: s in passed for s in STEPS}
for name, ok in checks.items(): print('%s %s' % ('PASS' if ok else 'FAIL', name))
sys.exit(0 if all(checks.values()) else 1)
