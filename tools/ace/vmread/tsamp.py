#!/usr/bin/env python3
# 1 Hz (or INTERVAL) per-thread sampler: comm, state, utime, stime, blocking syscall, wchan of every task of the game (no memory reads)
import os, sys, time, glob
out, T0, T1, pre, iv = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), int(sys.argv[4]), float(sys.argv[5])
def findpid():
    for m in glob.glob('/proc/[0-9]*/maps'):
        try:
            if int(m.split('/')[2]) > pre and 'libanogs.so' in open(m).read(): return int(m.split('/')[2])
        except OSError: pass
pid = None
while not pid: pid = findpid()
t0 = time.time(); f = open(out, 'w'); print('pid', pid, flush=True)
def rd(p):
    try: return open(p).read().strip()
    except OSError as e: return '-'
n = 0
while time.time() - t0 < T1:
    nxt = t0 + max(T0, 0) + n * iv
    while time.time() < nxt: time.sleep(0.01)
    t = time.time() - t0
    for d in sorted(glob.glob('/proc/%d/task/*' % pid), key=lambda x: int(x.split('/')[-1])):
        tid = d.split('/')[-1]
        st = rd(d + '/stat'); 
        try: rp = st.rindex(')'); comm = st[st.index('(') + 1:rp]; fl = st[rp + 2:].split()
        except ValueError: continue
        f.write('%.2f %s %s %s ut=%s st=%s | %s | %s\n' % (t, tid, comm, fl[0], fl[11], fl[12], rd(d + '/syscall'), rd(d + '/wchan')))
    f.flush(); n += 1
print('done', n, flush=True)
