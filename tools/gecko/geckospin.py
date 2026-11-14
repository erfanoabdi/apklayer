"""PC: start Fennec in this instance, wait until its Gecko thread has burnt CPU, perf-sample that thread, copy berberis's perf map, stop.
Usage (repo root, instance env): python3 geckospin.py <out dir> [cpu seconds]"""
import os, subprocess, sys, time, shutil
from pathlib import Path
sys.path.insert(0, str(Path.cwd() / 'tests/lib'))
import instance
pkg = 'org.mozilla.fennec_fdroid'
out = Path(sys.argv[1]); out.mkdir(exist_ok=True)
want = float(sys.argv[2]) if len(sys.argv) > 2 else 20
instance.assert_clean()
env = dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=200000 -Dapklayer.logcat=true')
p = None
bt = None
if os.environ.get('BT'):
    script = Path(os.environ['BT']).read_text().replace('@BRIDGE@', os.path.realpath(str(instance.NATIVE / 'android-libs/libberberis_arm64.so')))
    (out / 'run.bt').write_text(script)
    bt = subprocess.Popen(['sudo', '-n', 'env', 'BPFTRACE_MAX_STRLEN=120', 'bpftrace', '-o', str(out / 'bt.txt'), str(out / 'run.bt')], stdout=subprocess.DEVNULL, stderr=open(out / 'bt.err', 'w'))
    time.sleep(3)


def gecko():
    for line in instance.mine('[f]ennec'):
        pid = line.split()[0]
        try:
            if Path('/proc/%s/cmdline' % pid).read_bytes().split(b'\0')[0] != pkg.encode(): continue
        except OSError: continue
        for task in Path('/proc/%s/task' % pid).glob('*'):
            try:
                if (task / 'comm').read_text().strip() == 'Gecko':
                    st = (task / 'stat').read_text().rsplit(')', 1)[1].split()
                    return pid, task.name, (int(st[11]) + int(st[12])) / os.sysconf('SC_CLK_TCK')
            except OSError: pass
    return None


try:
    with open(out / 'app.log', 'w') as log:
        p = subprocess.Popen(['bin/apklayer', 'run', pkg], env=env, stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 150
        g = None
        while time.monotonic() < deadline:
            assert p.poll() is None, 'exited'
            g = gecko()
            if g and g[2] >= want: break
            time.sleep(1)
        print('gecko', g, flush=True)
        if g:
            pid, tid, cpu = g
            subprocess.run(['sudo', '-n', 'perf', 'record', '-F', '1000', '--call-graph', 'dwarf,16384', '-t', tid, '-o', str(out / 'perf.data'), '--', 'sleep', '4'],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.run('sudo -n chown $USER %s' % (out / 'perf.data'), shell=True)
            subprocess.run(['sudo', '-n', 'perf', 'trace', '-s', '-t', tid, '-o', str(out / 'trace.txt'), '--', 'sleep', '2'],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.run(['sudo', '-n', 'perf', 'trace', '--max-events', '400', '-t', tid, '-o', str(out / 'trace-ev.txt')],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            with open(out / 'memscan.txt', 'w') as t:
                subprocess.run(['sudo', '-n', 'python3', str(Path(out).parent / 'memscan.py'), pid], stdout=t, stderr=subprocess.STDOUT)
            shutil.copy('/proc/%s/maps' % pid, out / 'maps.txt')
            for f in Path(env.get('XDG_DATA_HOME')).rglob('perf-%s.map' % pid):
                f.chmod(0o600); shutil.copy(f, out / 'perf.map'); print('map', f)
            with open(out / 'perf.txt', 'w') as t:
                subprocess.run(['perf', 'script', '-i', str(out / 'perf.data'), '-F', 'ip,sym,dso'], stdout=t, stderr=subprocess.DEVNULL)
            print('cpu now', gecko(), flush=True)
finally:
    if bt:
        subprocess.run(['sudo', '-n', 'pkill', '-INT', '-P', str(bt.pid)])
        bt.wait()
    instance.stop()
    if p: p.wait()
    instance.assert_clean()
