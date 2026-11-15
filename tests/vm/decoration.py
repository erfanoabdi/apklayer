#!/usr/bin/env python3
"""decoration: under a compositor that offers xdg-decoration (sway, headless, a private socket; or the desktop session itself, KDE) the window
gets the compositor's title bar and none of ours: the shell says "server-side decorations" and configures the content with a 0 px bar. GNOME
(no such global) keeps ours: gpu-window, window-geometry. Sway when it is installed, else the session's compositor (also APKLY_DECORATION=session:
KDE Plasma); the session must then offer zxdg_decoration_manager_v1 (wayland-info), or the test says it cannot run."""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402

PKG = 'com.android.test.uibench'
session = os.environ.get('APKLY_DECORATION') == 'session' or not shutil.which('sway')
instance.stop()
sway = None
run_dir = None
try:
    if session:
        app_env = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=6000')
        if shutil.which('wayland-info'):
            info = subprocess.run(['wayland-info'], env=app_env, capture_output=True, text=True).stdout
            if 'zxdg_decoration_manager_v1' not in info: sys.exit('SKIP the session compositor has no xdg-decoration (GNOME keeps our title bar)')
        sway_log = None
    else:
        run_dir = Path(tempfile.mkdtemp(prefix='apklayer-sway-', dir=instance.SESSION))  # beside the desktop's socket: the stack's processes see the session dir
        sway_log = run_dir / 'sway.log'
        env = dict(os.environ, XDG_RUNTIME_DIR=str(run_dir), WLR_BACKENDS='headless', WLR_LIBINPUT_NO_DEVICES='1', WLR_HEADLESS_OUTPUTS='1')
        for k in ('DISPLAY', 'WAYLAND_DISPLAY'): env.pop(k, None)  # sway names its own socket (wayland-N) in its runtime dir
        (run_dir / 'config').write_text('output HEADLESS-1 resolution 1280x800\nexec echo ready > %s/ready\n' % run_dir)  # exec: once its socket is up
        sway = subprocess.Popen(['sway', '-c', str(run_dir / 'config')], env=env, stdout=subprocess.DEVNULL, stderr=sway_log.open('wb'), start_new_session=True)
        instance.wait_file(run_dir / 'ready', 'ready', 10)
        socket = next(p for p in run_dir.glob('wayland-*') if not p.name.endswith('.lock'))
        app_env = dict(instance.ENV, WAYLAND_DISPLAY=str(socket), APKLAYER_JOPTS='-Dapklayer.runms=6000')
    out = subprocess.run(['bin/apklayer', 'run', PKG], env=app_env, capture_output=True, text=True, timeout=60)
    shell = (Path(instance.LOGDIR) / 'shell.log').read_text(errors='replace')
    assert 'server-side decorations' in shell, 'no server-side decorations:\n' + shell[-2000:] + out.stdout[-1500:] + (sway_log.read_text(errors='replace')[-1500:] if sway_log else '')
    configures = [l for l in shell.splitlines() if 'configure content' in l]
    assert configures and all('title bar 0 logical' in l for l in configures), 'our title bar is still there:\n' + '\n'.join(configures)
    assert 'dmabuf frame 2 ' in shell, 'the app drew no GPU frames'
    print('PASS server-side decorations under %s: %s' % ('the session compositor' if session else 'sway', configures[-1].split('configure ', 1)[1]), flush=True)
finally:
    instance.stop()
    if sway:
        os.killpg(sway.pid, 15)
        sway.wait()
    if run_dir: shutil.rmtree(run_dir, ignore_errors=True)
