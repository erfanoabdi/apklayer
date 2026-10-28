#!/usr/bin/env python3
"""Passes stdin to stdout; on the probe's page-finished line takes a screenshot of the VM's screen (vmcapture) a few seconds later, so a human or a script can look at what the WebView drew."""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vmcapture

done = False
for line in sys.stdin:
    print(line, end='', flush=True)
    if not done and '[wv] page finished' in line:
        done = True
        time.sleep(4)
        vmcapture.capture('/tmp/apklayer-webview.png')
        print('CAPTURED /tmp/apklayer-webview.png', flush=True)
