#!/usr/bin/env python3
"""NFC (HubNfc, HubNfcMock; docs/NFC.md): tests/apps/hello-nfc against mock tags dropped into the instance's apklayer/nfc-mock directory, no reader needed.
First a run without the directory: no NFC feature, getDefaultAdapter() is null. Then, answering the app's "[nfc] ready" and result lines: reader mode with a
Type 2 tag (Ndef, NfcA, MifareUltralight, a write) and a Type 4 tag (IsoDep APDUs), a foreground dispatch (an NDEF text/plain tag goes to its PendingIntent), and the
system's dispatch: an empty Type 2 tag is TECH_DISCOVERED (a text record would be NDEF_DISCOVERED with type text/plain, as on Android), a URI tag NDEF_DISCOVERED, a Type 4
tag TECH_DISCOVERED (the second tech-list), a bare NfcA tag TAG_DISCOVERED. Steps are the app's `[nfc] PASS|FAIL <step>` lines."""
import os
import selectors
import shutil
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from instance import ENV, LOGDIR, stop  # noqa: E402

STEPS = ['adapter', 't2 cached ndef', 't2 ndef read', 't2 NfcA transceive', 't2 MifareUltralight readPages', 't2 ndef write', 't2 written pages',
         't4 cached ndef', 't4 IsoDep select and CC', 't4 IsoDep NDEF file', 't4 canned APDU', 't4 IsoDep info']
TAGS = {
    't2': 'type=t2\nuid=04a1b2c3d4e5f6\nndef.text=en:Hello\n',
    't4': 'type=t4\nuid=08a1b2c3\nndef.text=en:Card\napdu=FFCA000000 DEADBEEF9000\n',
    'mime': 'type=t2\nuid=04000000000001\nndef.mime=text/plain:hi\n',
    't2empty': 'type=t2\nuid=04000000000002\n',
    'uri': 'type=t2\nuid=04000000000003\nndef.uri=https://example.org/x\n',
    't4empty': 'type=t4\nuid=08000004\n',
    'bare': 'type=nfca\nuid=04000000000005\n',
}
log = Path(LOGDIR)
data = next(l.split('=', 1)[1] for l in subprocess.run(['bin/apklayer', 'env'], env=ENV, capture_output=True, text=True).stdout.split('\n') if l.startswith('APKLAYER_DATA='))
mock = Path(data) / 'apklayer/nfc-mock'
shutil.rmtree(mock, ignore_errors=True)


def put(name):  # written elsewhere in the directory, then renamed: the watcher reads it whole
    (mock / (name + '.tmp')).write_text(TAGS[name])
    os.rename(mock / (name + '.tmp'), mock / (name + '.tag'))


def take(name):
    (mock / (name + '.tag')).unlink()


# (a line of the app's output, what to do when it comes)
SCRIPT = [
    ('[nfc] ready reader', lambda: put('t2')),
    ('[nfc] reader done 04a1b2c3d4e5f6', lambda: (take('t2'), put('t4'))),
    ('[nfc] ready foreground', lambda: (take('t4'), put('mime'))),
    ('type=text/plain fg=true id=04000000000001 ', lambda: (take('mime'), put('t2empty'))),
    ('TECH_DISCOVERED data=null type=null fg=false id=04000000000002 ', lambda: (take('t2empty'), put('uri'))),
    ('NDEF_DISCOVERED data=https://example.org/x type=null fg=false id=04000000000003 ', lambda: (take('uri'), put('t4empty'))),
    ('TECH_DISCOVERED data=null type=null fg=false id=08000004 ', lambda: (take('t4empty'), put('bare'))),
    ('TAG_DISCOVERED data=null type=null fg=false id=04000000000005 ', lambda: take('bare')),
]


def run(runms):
    return subprocess.Popen(['bin/apklayer', 'run', 'hello.nfc'], env=dict(ENV, APKLAYER_JOPTS='-Dapklayer.runms=%d' % runms),
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)


stop()
proc = run(12000)  # no source of tags: a host without NFC
bare = proc.communicate()[0].decode(errors='replace')
stop()
print('\n'.join(l for l in bare.split('\n') if '[nfc]' in l))
no_nfc = '[nfc] no adapter feature=false' in bare

mock.mkdir(parents=True)
(log / 'hub.log').unlink(missing_ok=True)
lines, at = [], 0
proc = run(120000)
try:
    end, pending = time.monotonic() + 100, b''
    with selectors.DefaultSelector() as events:
        events.register(proc.stdout, selectors.EVENT_READ)
        while time.monotonic() < end and at < len(SCRIPT):
            if not events.select(0.5): continue
            chunk = os.read(proc.stdout.fileno(), 65536)
            if not chunk: break
            parts = (pending + chunk).split(b'\n'); pending = parts.pop()
            for raw in parts:
                line = raw.decode(errors='replace'); lines.append(line)
                if '[nfc]' in line or 'FATAL' in line or 'Exception' in line: print(line, flush=True)
                if at < len(SCRIPT) and SCRIPT[at][0] in line:
                    SCRIPT[at][1](); at += 1
finally:
    stop()
    proc.wait()
    shutil.rmtree(mock, ignore_errors=True)
hub = (log / 'hub.log').read_text(errors='replace') if (log / 'hub.log').exists() else ''
print('\n'.join(l for l in hub.split('\n') if 'nfc' in l), flush=True)
passed = {l.split('[nfc] PASS ')[1].rsplit(' (', 1)[0] for l in lines if '[nfc] PASS ' in l}
failed = [l for l in lines if '[nfc] FAIL ' in l]
print('%s no NFC without a source' % ('PASS' if no_nfc else 'FAIL'))
for step in STEPS: print('%s %s' % ('PASS' if step in passed else 'FAIL', step))
print('%s dispatch script (%d of %d lines seen)' % ('PASS' if at == len(SCRIPT) else 'FAIL', at, len(SCRIPT)))
if failed: print('FAIL lines: %s' % failed)
ok = no_nfc and not failed and all(s in passed for s in STEPS) and at == len(SCRIPT)
print('nfc: PASS' if ok else 'nfc: FAIL')
sys.exit(0 if ok else 1)
