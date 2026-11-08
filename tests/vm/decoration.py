#!/usr/bin/env python3
"""decoration: under a compositor that offers xdg-decoration (sway, headless, a private socket) the window gets the compositor's title bar and
none of ours: the shell says "server-side decorations" and configures the content with a 0 px bar. GNOME (no such global) keeps ours: gpu-window,
window-geometry. Needs sway on the VM (apt install sway)."""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402

PKG = 'com.android.test.uibench'
if not shutil.which('sway'): sys.exit('FAIL sway is not installed (apt install sway)')
run_dir = Path(tempfile.mkdtemp(prefix='apklayer-sway-', dir=instance.SESSION))  # beside the desktop's socket: the stack's processes see the session dir
sway_log = run_dir / 'sway.log'
env = dict(os.environ, XDG_RUNTIME_DIR=str(run_dir), WLR_BACKENDS='headless', WLR_LIBINPUT_NO_DEVICES='1',
           WLR_HEADLESS_OUTPUTS='1')
for k in ('DISPLAY', 'WAYLAND_DISPLAY'): env.pop(k, None)  # sway names its own socket (wayland-N) in its runtime dir
(run_dir / 'config').write_text('output HEADLESS-1 resolution 1280x800\nexec echo ready > %s/ready\n' % run_dir)  # exec: once its socket is up
instance.stop()
sway = subprocess.Popen(['sway', '-c', str(run_dir / 'config')], env=env, stdout=subprocess.DEVNULL, stderr=sway_log.open('wb'), start_new_session=True)
try:
    instance.wait_file(run_dir / 'ready', 'ready', 10)
    socket = next(p for p in run_dir.glob('wayland-*') if not p.name.endswith('.lock'))
    app_env = dict(instance.ENV, WAYLAND_DISPLAY=str(socket), APKLAYER_JOPTS='-Dapklayer.runms=6000')
    out = subprocess.run(['bin/apklayer', 'run', PKG], env=app_env, capture_output=True, text=True, timeout=60)
    shell = (Path(instance.LOGDIR) / 'shell.log').read_text(errors='replace')
    assert 'server-side decorations' in shell, 'no server-side decorations under sway:\n' + shell[-2000:] + out.stdout[-1500:] + sway_log.read_text(errors='replace')[-1500:]
    configures = [l for l in shell.splitlines() if 'configure content' in l]
    assert configures and all('title bar 0 logical' in l for l in configures), 'our title bar is still there:\n' + '\n'.join(configures)
    assert 'dmabuf frame 2 ' in shell, 'the app drew no GPU frames under sway'
    print('PASS server-side decorations under sway: ' + configures[-1].split('configure ', 1)[1], flush=True)
finally:
    instance.stop()
    os.killpg(sway.pid, 15)
    sway.wait()
    shutil.rmtree(run_dir, ignore_errors=True)
