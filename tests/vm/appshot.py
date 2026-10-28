#!/usr/bin/env python3
"""Mac side: for each package start it on the VM, wait, take a screenshot into DIR, stop it. Usage: appshot.py DIR SECS pkg..."""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config
import vmcapture

ssh = ['sshpass', '-p', config.PASS, 'ssh', '-o', 'StrictHostKeyChecking=no', config.VM]
out, secs = sys.argv[1], sys.argv[2]
for pkg in sys.argv[3:]:
    subprocess.run(ssh + ['cd ~/apklayer-m1; bin/apklayer stop >/dev/null 2>&1; (setsid bin/apklayer run %s >/tmp/appshot-%s.out 2>&1 &); sleep %s' % (pkg, pkg, secs)])
    vmcapture.capture(os.path.join(out, pkg + '.png'))
    subprocess.run(ssh + ['cd ~/apklayer-m1; bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail'])
    print('shot', pkg, flush=True)
