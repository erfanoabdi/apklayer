#!/usr/bin/env python3
"""Runs on the test host, in one instance (tests/lib/vm.py's instance_env): the corpus runner's end of an app (run.py end_run), repeated.
soak-sigterm.py <app.apk | package> <rounds>   (an .apk is installed first; a package name continues a soak without reinstalling)
Each round: launch, the activity's "started ... depth=1" line, the first GPU frame and QUIET (3 s) without frames (first-frame.py), a screenshot,
SIGTERM to every pid in the instance's run.* records (the app JVM, its window sink, the spare and its holder), the second launch as soon as the first
`apklayer run` returns, the same end, then `apklayer stop` (the runner's per-app stop). For every SIGTERMed process the seconds to its exit
(pidfd: no polling) go to $APKLY_OUT/soak.jsonl. A process alive 5 s after its SIGTERM is captured before the 10 s exit deadline
(/proc status with SigBlk/SigIgn/SigCgt, every thread's state and wchan, kernel stacks where sudo -n works, jcmd Thread.print, the instance's
logs) into $APKLY_OUT/keep/; one alive after 12 s is captured again, SIGKILLed and fails the soak (exit 1). Prints the distribution of soak.jsonl."""
import json
import os
import re
import selectors
import shutil
import signal
import statistics
import subprocess
import sys
import threading
import time
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import instance  # noqa: E402
from instance import ENV, OUT, STATE  # noqa: E402

LIMIT, LOOK, PATIENCE, QUIET = 12, 5, 90, 3
RESULTS = OUT / 'soak.jsonl'
KEEP = OUT / 'keep'
lock = threading.Lock()


def record(**row):
    with lock, open(RESULTS, 'a') as f: f.write(json.dumps(row) + '\n')


def capture(pid, why):
    """What holds a process that has not ended: before the deadline (why=look) and when it outlived LIMIT (why=late)."""
    d = KEEP / ('%s-%d-%s' % (time.strftime('%H%M%S'), pid, why))
    d.mkdir(parents=True)
    task = Path('/proc/%d/task' % pid)
    try:
        (d / 'status').write_text(Path('/proc/%d/status' % pid).read_text())
        (d / 'threads').write_text(''.join('%s %s %s wchan=%s\n' % (t.name, (t / 'comm').read_text().strip(), (t / 'stat').read_text().rsplit(')', 1)[1].split()[0],
                                                                    (t / 'wchan').read_text()) for t in task.iterdir()))
        (d / 'sigmasks').write_text(''.join('%s %s\n' % (t.name, ' '.join(l for l in (t / 'status').read_text().splitlines() if l.startswith('Sig')))
                                            for t in task.iterdir()))
    except OSError as e: (d / 'gone').write_text(str(e))
    with open(d / 'kstacks', 'w') as f:
        subprocess.run(['sudo', '-n', 'sh', '-c', 'for t in /proc/%d/task/*; do echo "== $t"; cat $t/stack; done' % pid], stdout=f, stderr=subprocess.STDOUT)
    with open(d / 'jstack', 'w') as f:
        try: subprocess.run(['jcmd', str(pid), 'Thread.print', '-l'], stdout=f, stderr=subprocess.STDOUT, timeout=4)
        except subprocess.TimeoutExpired: f.write('jcmd: no answer in 4 s\n')
    for log in STATE.glob('*.log'): shutil.copy(log, d)
    print('[soak] captured %d (%s) into %s' % (pid, why, d), flush=True)


def track(rnd, launch, began, procs, failed):
    """Waits for the SIGTERMed processes (pid: (pidfd, comm)) to exit; records each one's seconds."""
    sel = selectors.DefaultSelector()
    for pid, (fd, comm) in procs.items(): sel.register(fd, selectors.EVENT_READ, pid)
    looked = False
    while sel.get_map():
        left = (LIMIT if looked else LOOK) - (time.monotonic() - began)
        events = sel.select(max(0, left))
        for key, _ in events:
            pid = key.data
            sel.unregister(key.fd); os.close(key.fd)
            record(round=rnd, launch=launch, pid=pid, comm=procs[pid][1], took=round(time.monotonic() - began, 3))
        if events or not sel.get_map(): continue
        if not looked:
            looked = True
            for key in list(sel.get_map().values()): capture(key.data, 'look')
            continue
        for key in list(sel.get_map().values()):  # outlived LIMIT: capture, end it, fail
            pid = key.data
            capture(pid, 'late')
            signal.pidfd_send_signal(key.fd, signal.SIGKILL)
            sel.unregister(key.fd); os.close(key.fd)
            record(round=rnd, launch=launch, pid=pid, comm=procs[pid][1], took=None, late=True)
            failed.append(pid)
    sel.close()


def end_run(rnd, launch, failed):
    """run.py's end: SIGTERM to every pid the instance records (only this instance's processes); a thread times each exit."""
    procs = {}
    for f in STATE.glob('run.*'):
        try: pids = [int(x) for x in f.read_text().split()]
        except (OSError, ValueError): continue
        for pid in pids:
            if pid in procs or not instance.belongs(pid): continue
            try: procs[pid] = (os.pidfd_open(pid), Path('/proc/%d/comm' % pid).read_text().strip())
            except OSError: pass
    began = time.monotonic()
    for fd, _ in procs.values():
        try: signal.pidfd_send_signal(fd, signal.SIGTERM)
        except ProcessLookupError: pass
    t = threading.Thread(target=track, args=(rnd, launch, began, procs, failed))
    t.start()
    return t, len(procs)


def drain(run, lines, started):
    for line in run.stdout:
        lines.append(line.decode(errors='replace'))
        if 'started ' in lines[-1] and 'depth=1' in lines[-1]: started.set()
    started.set()


def launch(pkg):
    """One launch to a settled picture; the `run` process and its output (read on a thread)."""
    shell = STATE / 'shell.log'
    offset = shell.stat().st_size if shell.exists() else 0
    run = subprocess.Popen(['bin/apklayer', 'run', pkg], env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    lines, started = [], threading.Event()
    reader = threading.Thread(target=drain, args=(run, lines, started), daemon=True)
    reader.start()
    ok = started.wait(PATIENCE) and run.poll() is None
    if ok:
        subprocess.run(['python3', 'tests/corpus/first-frame.py', str(shell), str(offset), '30', '1', str(QUIET)], stdout=subprocess.DEVNULL)
        subprocess.run(['bin/apklayer', 'screenshot', pkg, str(OUT / 'soak-shot.png')], env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return run, lines, reader, ok


def report():
    rows = [json.loads(l) for l in RESULTS.read_text().splitlines()] if RESULTS.exists() else []
    runs = [r for r in rows if 'rc' in r]
    rows = [r for r in rows if 'rc' not in r]
    if not rows: return print('[soak] no records')
    took = sorted(r['took'] for r in rows if r['took'] is not None)
    late = [r for r in rows if r['took'] is None]
    by = {}
    for r in rows: by.setdefault(r['comm'], []).append(r['took'] or 99)
    print('[soak] rounds %d, processes %d, late %d, exit after SIGTERM: median %.2f s, max %.2f s, over %d s: %d' % (
        len({r['round'] for r in rows}), len(rows), len(late), statistics.median(took), took[-1], LOOK, sum(t > LOOK for t in took)), flush=True)
    print('[soak] launches %d, not started %d, run exits %s' % (len(runs), sum(not r['started'] for r in runs), dict(sorted(Counter(r['rc'] for r in runs).items(), key=str))), flush=True)
    for comm, t in sorted(by.items()): print('[soak]   %-16s n=%-4d median %.2f max %.2f' % (comm, len(t), statistics.median(t), max(t)), flush=True)


def main():
    target, rounds = sys.argv[1], int(sys.argv[2])
    OUT.mkdir(parents=True, exist_ok=True)
    instance.stop()
    if target.endswith('.apk'):
        out = subprocess.run(['bin/apklayer', 'install', target], env=ENV, capture_output=True, text=True).stdout
        target = re.search(r'installed (\S+)', out).group(1)
        print('[soak] installed ' + target, flush=True)
    first = max((json.loads(l)['round'] for l in RESULTS.read_text().splitlines()), default=0) + 1 if RESULTS.exists() else 1
    failed, trackers = [], []
    try:
        for rnd in range(first, first + rounds):
            for n in (1, 2):
                run, lines, reader, ok = launch(target)
                t, count = end_run(rnd, n, failed)
                trackers.append(t)
                try: rc = run.wait(LIMIT + 3)
                except subprocess.TimeoutExpired: rc = None; failed.append('run'); os.killpg(run.pid, signal.SIGKILL); run.wait()
                reader.join()
                deadline = sum('exit deadline' in l for l in lines)
                record(round=rnd, launch=n, pid=run.pid, comm='apklayer-run', took=0, rc=rc, started=ok, deadline=deadline)
                print('[soak] round %d launch %d: %s, %d pids SIGTERMed, run exit %s%s' % (rnd, n, 'settled' if ok else 'NOT STARTED', count, rc,
                                                                                         ', exit deadline hit' if deadline else ''), flush=True)
                if not ok: print(''.join(lines[-15:]), flush=True)
            for t in trackers: t.join()
            trackers.clear()
            instance.stop()
    finally:
        for t in trackers: t.join()
        instance.stop()
    report()
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
