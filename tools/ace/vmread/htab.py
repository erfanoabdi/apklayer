#!/usr/bin/env python3
# find the VM context (ctx+0x178 == VM memory base == ctx+0x1a0), its module object and handler table; dump them. /proc/pid/mem read only.
import os, sys, time, glob, struct
out, T, pre = sys.argv[1], float(sys.argv[2]), int(sys.argv[3]); os.makedirs(out, exist_ok=True)
def findpid():
    for m in glob.glob('/proc/[0-9]*/maps'):
        try:
            if int(m.split('/')[2]) > pre and 'libanogs.so' in open(m).read(): return int(m.split('/')[2])
        except OSError: pass
pid = None
while not pid: pid = findpid()
t0 = time.time(); print('pid', pid, flush=True); last = 0
while time.time() - t0 < T:
    if time.time() - last > 5: print('t=%.0f' % (time.time() - t0), flush=True); last = time.time()
    time.sleep(0.2)
maps = open('/proc/%d/maps' % pid).read(); open(out + '/maps.txt', 'w').write(maps)
mem = open('/proc/%d/mem' % pid, 'rb', 0)
def rd(a, n):
    mem.seek(a); return mem.read(n)
rows = []
for l in maps.splitlines():
    p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]; rows.append((a, b, p[1], len(p) > 5))
vms = [a for a, b, pm, nm in rows if b - a == 0x44000 and pm == 'rw-p' and not nm]
print('vm regions', [hex(x) for x in vms], flush=True)
for vm in vms:
    pat = struct.pack('<Q', vm)
    for a, b, pm, nm in rows:
        if nm or pm != 'rw-p' or b - a > 64 << 20 or b - a == 0x44000: continue
        try: d = rd(a, b - a)
        except (OSError, ValueError): continue
        i = d.find(pat)
        while i >= 0:
            if i % 8 == 0 and i >= 0x178 and i + 0x30 <= len(d) and struct.unpack_from('<Q', d, i + 0x28)[0] == vm:
                ctx = a + i - 0x178; print('ctx', hex(ctx), 'vm', hex(vm), flush=True)
                open('%s/ctx-%x.bin' % (out, ctx), 'wb').write(rd(ctx, 0x300))
                mod = struct.unpack_from('<Q', d, i + 0x18)[0]
                try: md = rd(mod, 0x200); open('%s/mod-%x.bin' % (out, mod), 'wb').write(md)
                except (OSError, ValueError): continue
                tab = struct.unpack_from('<Q', md, 0x68)[0]; n = struct.unpack_from('<I', md, 0x60)[0]
                print('module', hex(mod), 'tab', hex(tab), 'n', hex(n), flush=True)
                try: open('%s/tab-%x.bin' % (out, tab), 'wb').write(rd(tab, 0x88000))
                except (OSError, ValueError) as e: print('tab fail', e)
            i = d.find(pat, i + 1)
print('done', flush=True)
