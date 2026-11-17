#!/usr/bin/env python3
# find the VM ctx of the running game (ctx+0x178 == ctx+0x1a0 == VM memory base) and sample ctx[0:0x1c8] in a tight loop (/proc/pid/mem read only)
import os, sys, time, glob, struct
out, T0, T1, pre = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), int(sys.argv[4]); os.makedirs(out, exist_ok=True)
def findpid():
    for m in glob.glob('/proc/[0-9]*/maps'):
        try:
            if int(m.split('/')[2]) > pre and 'libanogs.so' in open(m).read(): return int(m.split('/')[2])
        except OSError: pass
pid = None
while not pid: pid = findpid()
t0 = time.time(); print('pid', pid, flush=True)
while time.time() - t0 < T0: time.sleep(0.05)
fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY)
rd = lambda a, n: os.pread(fd, n, a)
maps = open('/proc/%d/maps' % pid).read(); open(out + '/maps.txt', 'w').write(maps)
rows = []
for l in maps.splitlines():
    p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]; rows.append((a, b, p[1], len(p) > 5))
vms = [a for a, b, pm, nm in rows if b - a == 0x44000 and pm == 'rw-p' and not nm]
vm = vms[0]; print('vm', hex(vm), flush=True)
pat = struct.pack('<Q', vm); ctx = None
for a, b, pm, nm in rows:
    if nm or pm != 'rw-p' or b - a > 64 << 20 or b - a == 0x44000: continue
    try: d = rd(a, b - a)
    except OSError: continue
    i = d.find(pat)
    while i >= 0:
        if i % 8 == 0 and i >= 0x178 and i + 0x30 <= len(d) and struct.unpack_from('<Q', d, i + 0x28)[0] == vm: ctx = a + i - 0x178
        i = d.find(pat, i + 1)
    if ctx: break
print('ctx', hex(ctx) if ctx else None, 'at t=%.1f' % (time.time() - t0), flush=True)
if not ctx: sys.exit(1)
last = b''; f = open(out + '/samples.bin', 'wb'); n = 0; ts = time.time()
while time.time() - t0 < T1:
    try: b = rd(ctx, 0x118)
    except OSError: break
    if b != last: f.write(struct.pack('<d', time.time() - t0) + b); n += 1; last = b
    if n > 1000000: break
print('samples', n, 'rate %.0f/s' % (n / (time.time() - ts)), flush=True)
open(out + '/vm.txt', 'w').write('%x %x\n' % (vm, ctx))
mem = open('/proc/%d/mem' % pid, 'rb', 0); mem.seek(vm); open(out + '/vmend.bin', 'wb').write(mem.read(0x44000))
