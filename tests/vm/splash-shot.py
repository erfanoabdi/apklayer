#!/usr/bin/env python3
"""Mac side: for each package, a private apklayer-shell (own XDG_RUNTIME_DIR) + a stand-in app that shows no frame; screenshot
the splash, release the first frame, screenshot again. Usage: splash-shot.py DIR pkg...  (DIR/<pkg>-splash.png, -app.png)"""
import os, subprocess, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config, vmcapture

ssh = ['sshpass', '-p', config.PASS, 'ssh', '-o', 'StrictHostKeyChecking=no', config.VM]
R = '/tmp/rt-splash'
def run(cmd): subprocess.run(ssh + [cmd])
out = sys.argv[1]
for pkg in sys.argv[2:]:
    run('rm -f /tmp/splash-go; mkdir -p %s/apklayer; chmod 700 %s; cd ~/apklayer-m1-splash; '
        '(XDG_RUNTIME_DIR=%s WAYLAND_DISPLAY=/run/user/1000/wayland-0 setsid b/bridge/shell/apklayer-shell >/tmp/splash-shell.log 2>&1 & echo $! >/tmp/splash-shell.pid); '
        '(setsid python3 tests/vm/splash-client.py %s/apklayer/shell.sock %s >/tmp/splash-client.log 2>&1 & echo $! >/tmp/splash-client.pid)' % (R, R, R, R, pkg))
    vmcapture.capture(os.path.join(out, pkg + '-splash.png'))
    run('touch /tmp/splash-go')
    vmcapture.capture(os.path.join(out, pkg + '-app.png'))
    run('kill $(cat /tmp/splash-client.pid /tmp/splash-shell.pid); grep -a splash /tmp/splash-shell.log; rm -f /tmp/splash-go')
    print('shot', pkg, flush=True)
