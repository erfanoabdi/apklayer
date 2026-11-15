#!/usr/bin/env python3
"""kde-probe (scratch, not committed): kde-probe.py 'js1' 'js2' ...: starts UiBench in this instance, runs each KWin action in turn (2 s apart) and prints
KWin's window state and the shell's configure after each. For finding the KWin 5.27 way of an action."""
import json
from pathlib import Path
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402
import kwin  # noqa: E402
PKG = 'com.android.test.uibench'
instance.stop()
(instance.STATE / 'shell.log').unlink(missing_ok=True)
app = subprocess.Popen(['bin/apklayer', 'run', PKG], env=dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=40000'), stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT, start_new_session=True)
try:
    for _ in range(40):
        time.sleep(1)
        if kwin.info(PKG, instance.OUT): break
    time.sleep(3)
    for js in [''] + sys.argv[1:]:
        if js.startswith('!'): print(subprocess.run(js[1:], shell=True, env=instance.ENV, capture_output=True, text=True).stdout[-800:])
        elif js: kwin.act(PKG, js, instance.OUT)
        time.sleep(2.5)
        i = kwin.info(PKG, instance.OUT)
        print('%-60s -> tiled=%s max=%s fs=%s frame=%s client=%s shell=%s' % (js[:60], i['tiled'], i['maximize'], i['fullScreen'], i['frame'], i['client'], kwin.configure(instance.STATE / 'shell.log')), flush=True)
finally:
    instance.stop()
