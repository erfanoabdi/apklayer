#!/usr/bin/env python3
"""apklayer logs / report (cli/logs.cpp): a launch keeps its log in <logs>/app-<package>.log (the JVM's output, android.util.Log, native logs;
the previous launch's as .1) without APKLAYER_LOGCAT, `apklayer logs` prints it and `apklayer report` packs it with the versions and service logs."""
import os
import re
import subprocess
import sys
import tarfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402

PKG = 'hello.input'
ENV = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=8000')
ENV.pop('APKLAYER_LOGCAT', None)
log = Path(instance.LOGDIR) / ('app-%s.log' % PKG)
try:
    instance.stop()
    for launch in (1, 2):  # the second launch moves the first one's log to .1
        subprocess.run(['bin/apklayer', 'run', PKG], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
    text = log.read_text(errors='replace')
    assert '[apklayer] started hello.input.Main' in text, 'the JVM output is not in ' + str(log) + '\n' + text[-2000:]
    assert re.search(r'^[VDIWEA]/\S+', text, re.M), 'no android.util.Log line in ' + str(log) + '\n' + text[-2000:]
    assert Path(str(log) + '.1').is_file(), 'the previous launch log is gone'
    shown = subprocess.run(['bin/apklayer', 'logs', PKG], env=ENV, capture_output=True, text=True, check=True).stdout
    assert shown == text, 'apklayer logs printed something else than the file'
    # a log at its size cap: whole lines, a note at the cap, and the count of what was left out at the end of the launch
    capped = subprocess.run(['bin/apklayer', 'run', PKG], env=dict(ENV, APKLAYER_APP_LOG_CAP='3000'), capture_output=True, text=True, timeout=60)
    small = log.read_text(errors='replace')
    assert 'reached its size limit (3000 bytes)' in small and re.search(r'\[apklayer\] \d+ lines \(\d+ bytes\) of the launch.s output were left out', small), small[-1500:]
    assert len(small.encode()) < 3600 and small.split('\n[apklayer] this log reached')[0].endswith('\n'), 'the cut is not at a line end or the file is over the cap: %d' % len(small)
    out = instance.OUT / 'report.tar.gz'
    subprocess.run(['bin/apklayer', 'report', PKG, str(out)], env=ENV, capture_output=True, text=True, check=True, timeout=120)
    with tarfile.open(out) as t:
        names = {Path(n).name for n in t.getnames()}
        about = t.extractfile(next(m for m in t.getmembers() if m.name.endswith('about.txt'))).read().decode()
    for want in ('about.txt', 'app-%s.log' % PKG, 'app-%s.log.1' % PKG, 'hub.log', 'shell.log'):
        assert want in names, want + ' missing from the report: ' + str(sorted(names))
    assert 'converterAbi' in about and 'package\t' + PKG in about, 'about.txt lacks the version or the app info:\n' + about[:1500]
    print('PASS logs: a launch keeps its log (JVM output and android.util.Log without APKLAYER_LOGCAT, the previous one as .1); logs prints it; '
          'report packs about.txt, both launch logs and the service logs (%d files)' % len(names), flush=True)
finally:
    instance.stop()
