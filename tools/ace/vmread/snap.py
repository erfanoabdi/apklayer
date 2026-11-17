#!/usr/bin/env python3
# snapshot the VM memory (anon rw-p 0x44000 regions) of the game every STEP s for TOTAL s after libanogs appears. /proc/pid/mem read only.
import os, sys, time, glob
out, step, total, pre = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), int(sys.argv[4])  # pre: ignore pids <= pre
os.makedirs(out, exist_ok=True)
def findpid():
    for m in glob.glob('/proc/[0-9]*/maps'):
        try:
            if int(m.split('/')[2]) > pre and 'libanogs.so' in open(m).read(): return int(m.split('/')[2])
        except OSError: pass
pid = None
while not pid: pid = findpid()
t0 = time.time(); print('pid', pid, flush=True); n = 0
mem = open('/proc/%d/mem' % pid, 'rb', 0)
def snap(tag, sizes):
    maps = open('/proc/%d/maps' % pid).read()
    for l in maps.splitlines():
        p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]
        if len(p) < 6 and p[1] == 'rw-p' and b - a in sizes:
            try: mem.seek(a); d = mem.read(b - a)
            except (OSError, ValueError) as e: print('fail', hex(a), e, flush=True); continue
            open('%s/s%s-%x-%x.bin' % (out, tag, a, b), 'wb').write(d)
    return maps
while time.time() - t0 < total:
    t = time.time() - t0
    snap('%05.1f' % t, (0x44000,)); n += 1
    nxt = t0 + n * step
    while time.time() < nxt: time.sleep(0.05)
maps = snap('final', (0x44000,))
open(out + '/maps.txt', 'w').write(maps)
for l in maps.splitlines():
    p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]
    if len(p) < 6 and p[1] == 'rw-p' and 5700000 <= b - a <= 5800000:
        mem.seek(a); open('%s/big-%x-%x.bin' % (out, a, b), 'wb').write(mem.read(b - a))
print('done', n, flush=True)
