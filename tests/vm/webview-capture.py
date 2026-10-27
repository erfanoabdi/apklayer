#!/usr/bin/env python3
"""Passes stdin to stdout; on the probe's page-finished line takes a screenshot of the VM's screen (prlctl capture) a few seconds later, so a human or a script can look at what the WebView drew."""
import subprocess
import sys
import time

done = False
for line in sys.stdin:
    print(line, end='', flush=True)
    if not done and '[wv] page finished' in line:
        done = True
        time.sleep(4)
        subprocess.run(['prlctl', 'capture', 'Ubuntu 24.04 (with Rosetta)', '--file', '/tmp/apklayer-webview.png'], check=False)
        print('CAPTURED /tmp/apklayer-webview.png', flush=True)
