#!/usr/bin/env python3
"""OpenTTD (SDL 1.2, GLES1) without a sound server: its SDL audio opens on the HAL's silent output, and after a focus loss its game thread
stops instead of drawing with the textures of the GL context SDL destroyed ("Invalid texture"). OpenTTD installed by hand (tests/run needs)."""
import os
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402  (changes into the repo root)
PACKAGE = 'org.openttd.fdroid'
env = dict(instance.ENV, PIPEWIRE_REMOTE='apklayer-no-server', PULSE_SERVER='unix:/nonexistent/apklayer-no-pulse',
    APKLAYER_JOPTS='-Dapklayer.runms=' + os.environ.get('OPENTTD_RUNMS', '45000'))
logs = Path(instance.LOGDIR)
app_log = logs / ('app-%s.log' % PACKAGE)
if os.environ.get('OPENTTD_APK'):  # the APK on this host (APKLY_VM_ENV), installed into the group's data home
    subprocess.run(['bin/apklayer', 'install', os.environ['OPENTTD_APK']], env=env, check=True, stdout=subprocess.DEVNULL)
app = subprocess.Popen(['bin/apklayer', 'run', PACKAGE], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
try:
    instance.wait_file(app_log, os.environ.get('OPENTTD_READY', 'W/AudioTrack: Use of stream types'), 60)  # SDL opened its audio: the game runs
    before = app_log.read_text(errors='replace').count('Invalid texture')
    if os.environ.get('OPENTTD_BLUR', '1') == '1': instance.inject(PACKAGE, 'blur')
    assert app.wait(timeout=90) == 0, 'OpenTTD failed'
finally:
    if app.poll() is None: os.killpg(app.pid, 15); app.wait(timeout=10)
    subprocess.run(['bin/apklayer', 'stop'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
text = app_log.read_text(errors='replace')
for line in text.splitlines():
    if any(k in line for k in ('AudioTrack', 'SDL audio', 'Audio', 'Invalid texture')) and 'JNI register' not in line: print(line[:200], flush=True)
after = text.count('Invalid texture') - before
audio = (logs / 'audio.log').read_text(errors='replace')
assert 'playback is silent' in audio, 'no silent-output line in audio.log'
assert 'initializing AudioTrack' not in text, 'the AudioTrack still fails'  # without an output: "Error -19 initializing AudioTrack"
assert after == 0, f'"Invalid texture" {after} times after the focus loss'
print('PASS OpenTTD on the silent output: no "Invalid texture" after the focus loss', flush=True)
