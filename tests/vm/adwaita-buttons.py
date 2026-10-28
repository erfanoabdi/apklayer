#!/usr/bin/env python3
"""Mac side: start PKG in the `adwaita` instance, screenshot, click a title bar button (permille x,y), screenshot, stop.
Usage: adwaita-buttons.py DIR PKG [x,y ...]   (each click is followed by a screenshot DIR/<n>.png)"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config
import vmcapture

ssh = ['sshpass', '-p', config.PASS, 'ssh', '-o', 'StrictHostKeyChecking=no', config.VM]
out, pkg = sys.argv[1], sys.argv[2]
run = lambda c: subprocess.run(ssh + [c], capture_output=True, text=True).stdout
A = 'cd ~/apklayer-m1-adw; bin/apklayer --instance adwaita '
run(A + 'stop >/dev/null 2>&1; sleep 2; (setsid bin/apklayer --instance adwaita run %s >/tmp/adwshot.out 2>&1 &); sleep 12' % pkg)
vmcapture.capture(os.path.join(out, 'start.png'))
for i, xy in enumerate(sys.argv[3:]):
    print(run('sudo -n /tmp/uinput-keys abs:%s click; sleep 1.5; tail -2 ~/.local/state/apklayer/adwaita/shell.log' % xy))
    vmcapture.capture(os.path.join(out, 'click%d.png' % i))
run(A + 'stop >/dev/null 2>&1')
