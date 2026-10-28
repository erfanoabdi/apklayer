"""On a hang: native stacks (gdb) and memory maps of every app JVM and service, saved under ~/hang-<time>/ before the test cleans up."""
import os
import subprocess
import time


def dump():
    out = os.path.expanduser('~/hang-' + time.strftime('%H%M%S'))
    os.makedirs(out, exist_ok=True)
    pids = subprocess.run(['pgrep', '-f', '[a]pklayer.host.Main'], capture_output=True, text=True).stdout.split()
    for pid in pids:
        try:
            with open(out + '/' + pid + '.maps', 'w') as f: f.write(open('/proc/' + pid + '/maps').read())
            with open(out + '/' + pid + '.gdb', 'w') as f:
                subprocess.run(['sudo', '-n', 'gdb', '-p', pid, '-batch', '-ex', 'thread apply all bt 24'], stdout=f, stderr=subprocess.DEVNULL)
        except OSError:
            pass
    print('HANG dump in ' + out, flush=True)
