#!/usr/bin/env python3
"""Runs on the test VM in an instance where real Fennec (org.mozilla.fennec_fdroid) is installed: Gecko (not Android WebView) opens a local page,
its JavaScript reports back after three animation frames (value=42), and `apklayer screenshot` must show the page's red square (content painted,
not only the toolbar). Uses a test-only onboarding-done profile (preferences as tests/vm/fennec-media.sh sets them).
Usage (instance environment, repo root): python3 tests/corpus/gecko-page.py. Files: instance.OUT/gecko-page.{log,png}."""
import http.server
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402
from PIL import Image  # noqa: E402

pkg = 'org.mozilla.fennec_fdroid'
PATIENCE = int(os.environ.get('GECKO_PAGE_PATIENCE', 70))  # seconds until the beacon: x86 translation's first start needs more
info = subprocess.check_output(['bin/apklayer', 'info', pkg], env=instance.ENV, text=True)
data = Path(dict(line.split('\t', 1) for line in info.splitlines() if '\t' in line)['dataDir'])
assert '/%s/app-data/' % instance.INSTANCE in str(data), data
prefs = data / 'app/shared_prefs'
prefs.mkdir(parents=True, exist_ok=True)
for file, entries in {
        'fenix.onboarding.xml': [('int', 'fenix.onboarding.last_version', '1')],
        'fenix_preferences.xml': [('boolean', 'pref_key_terms_accepted', 'true'), ('int', 'pref_key_terms_accepted_version', '5'),
                                  ('long', 'pref_key_terms_accepted_date', str(int(time.time() * 1000))),
                                  ('long', 'pref_key_onboarding_completed_timestamp', str(int(time.time() * 1000))),
                                  ('boolean', 'pref_key_is_first_run', 'false')]}.items():
    path = prefs / file
    tree = ET.parse(path) if path.exists() else ET.ElementTree(ET.Element('map'))
    for kind, name, value in entries:
        for old in list(tree.getroot()):
            if old.attrib.get('name') == name: tree.getroot().remove(old)
        ET.SubElement(tree.getroot(), kind, name=name, value=value)
    tree.write(path, encoding='utf-8', xml_declaration=True)
seen = []
page = (b'<!doctype html><meta name="viewport" content="width=device-width"><body style="margin:0;background:#fff">'
        b'<div style="background:rgb(255,0,0);width:200px;height:200px"></div><h1>GECKO ENVIRONMENT PASS</h1>'
        b'<style>@font-face{font-family:Served;src:url(/font.ttf)}</style><h1 style="font-family:Served;color:#0000ff">SERVED FONT</h1>'
        b"<script>requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>fetch('/rendered?value='+(6*7)))));</script>")


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        seen.append(self.path)
        print('FETCH', self.path, flush=True)
        self.send_response(200)
        self.send_header('Content-Type', 'text/html')
        self.end_headers()
        font = instance.NATIVE / 'android-root/system/fonts/Roboto-Regular.ttf'  # a web font: tells font discovery from glyph drawing
        self.wfile.write(page if self.path.startswith('/page') else font.read_bytes() if self.path == '/font.ttf' else b'ok')

    def log_message(self, *args): pass


def threads():
    """Diagnosis of a page that never loads: Gecko's busiest threads per process (name, state, CPU seconds, kernel wait channel)."""
    for line in instance.mine('[f]ennec'):
        pid = line.split()[0]
        rows = []
        for task in Path('/proc/%s/task' % pid).glob('*'):
            try:
                stat = (task / 'stat').read_text().rsplit(')', 1)[1].split()
                rows.append(((int(stat[11]) + int(stat[12])) / os.sysconf('SC_CLK_TCK'), (task / 'comm').read_text().strip(), stat[0],
                             (task / 'wchan').read_text().strip()))
            except (OSError, IndexError, ValueError): pass
        name = Path('/proc/%s/cmdline' % pid).read_bytes().split(b'\0')[0].decode(errors='replace')[:60]
        print('THREADS %s %s: ' % (pid, name) + ', '.join('%s %s %.1fs %s' % (n, st, cpu, w) for cpu, n, st, w in sorted(rows, reverse=True)[:8]), flush=True)


server = http.server.HTTPServer(('127.0.0.1', 0), Handler)
server.timeout = 2
intent = instance.OUT / 'gecko-page.intent'
intent.write_text('action=android.intent.action.VIEW\ndata=http://127.0.0.1:%d/page?nonce=%d\ncomponent=org.mozilla.fenix.IntentReceiverActivity\n'
                  % (server.server_port, time.time_ns()))
env = dict(instance.ENV, APKLAYER_INTENT_FILE=str(intent), APKLAYER_JOPTS='-Dapklayer.runms=%d -Dapklayer.logcat=true' % (1000 * (PATIENCE + 60)))
shot = instance.OUT / 'gecko-page.png'
process = None
try:
    with open(instance.OUT / 'gecko-page.log', 'w') as log:
        process = subprocess.Popen(['bin/apklayer', 'run', pkg], env=env, stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + PATIENCE
        while not any(p.startswith('/rendered?value=42') for p in seen):
            assert process.poll() is None, 'Fennec exited'
            if time.monotonic() >= deadline:
                threads()
                raise AssertionError('No rendered-page beacon: ' + repr(seen))
            server.handle_request()
        shell = instance.STATE / 'shell.log'
        subprocess.run(['python3', 'tests/corpus/first-frame.py', str(shell), str(shell.stat().st_size), '15', '1', '1'])
        red = 0
        for attempt in range(int(sys.argv[1]) if len(sys.argv) > 1 else 3):  # a page is composited some frames after its JavaScript ran
            shot.unlink(missing_ok=True)
            subprocess.run(['bin/apklayer', 'screenshot', pkg, str(shot)], env=env, check=True)
            red = sum(r > 200 and g < 40 and b < 40 for r, g, b in Image.open(shot).convert('RGB').getdata())
            print('screenshot %d: %d red pixels' % (attempt, red), flush=True)
            if red > 2000: break
            subprocess.run(['python3', 'tests/corpus/first-frame.py', str(shell), str(shell.stat().st_size), '5', '1', '1'])
        assert red > 2000, 'Local page not drawn: red pixels=%d (%s)' % (red, shot)
        # the heading below the square: dark text pixels in the band under it (informational: fonts in Gecko's content process)
        image = Image.open(shot).convert('RGB')
        top = next(y for y in range(image.height) if image.getpixel((10, y))[0] > 200 and image.getpixel((10, y))[1] < 40) + 200
        text = sum(1 for y in range(top, min(top + 60, image.height)) for x in range(image.width) if sum(image.getpixel((x, y))) < 240)
        blue = sum(1 for r, g, b in image.getdata() if b > 150 and r < 120 and g < 120)
        print('heading text pixels under the square: %d, served-font (blue) pixels: %d' % (text, blue), flush=True)
        for line in instance.mine('[f]ennec|[G]ecko'):  # which of Gecko's processes have font files mapped (FreeType reads them by mmap)
            pid = line.split()[0]
            try: fonts = {m.split()[-1] for m in Path('/proc/%s/maps' % pid).read_text().splitlines() if m.endswith(('.ttf', '.otf', '.ttc'))}
            except OSError: continue
            print('fonts mapped: %3d  %s' % (len(fonts), Path('/proc/%s/cmdline' % pid).read_bytes().split(b'\0')[0].decode(errors='replace')[:90]), flush=True)
        print('PASS Gecko fetched the localhost page, ran JS=42, drew %d red pixels (%s)' % (red, shot), flush=True)
finally:
    server.server_close()
    instance.stop()
    if process: process.wait()
    instance.assert_clean()
