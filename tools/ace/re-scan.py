#!/usr/bin/env python3
"""STORM MODE (WATCHDIR is a file): trigger = a new "no glibc chunk" line in it; dumps every stack_and_tls region, the code-dense anonymous regions whole
(<= 16 MB), libanogs data and +-256 KB around each refused pointer of the new lines.
PGR static-RE helper (agent RE): find arm64 code in a running game's anonymous memory (ACE's payload for its own arm64 interpreter) and dump it.
  sudo python3 re-scan.py OUTDIR WATCHDIR [N]   event driven like re-dump.py (inotify of WATCHDIR = ACE's files/ano_tmp; OUTDIR/stop ends it).
On each event (>= 3 s apart, N times): every anonymous mapping of 4 KB..256 MB is read, pages with aarch64 code signatures (ret, stp x29/x30 prologues,
ldp epilogues, many bl) are merged (gap <= 8 pages) into ranges and written as OUTDIR/<n>/<start>-<end>.bin, with maps.txt and ranges.txt;
plus the libanogs data segments (the interpreter's context lives there or in the heap). Reads /proc/<pid>/mem only."""
import ctypes, os, struct, sys, time, glob
out, watch = sys.argv[1], sys.argv[2]; maxd = int(sys.argv[3]) if len(sys.argv) > 3 else 3
libc = ctypes.CDLL(None, use_errno=True)
fd = libc.inotify_init(); os.makedirs(out, exist_ok=True)
libc.inotify_add_watch(fd, watch.encode(), 0x100 | 0x80 | 0x2 | 0x8); libc.inotify_add_watch(fd, out.encode(), 0x100)
isf = os.path.isfile(watch); pos = os.path.getsize(watch) if isf else 0; ptrs = []
print('READY', flush=True)
def findpid():
    for m in glob.glob('/proc/[0-9]*/maps'):
        try:
            if 'libanogs.so' in open(m).read(): return int(m.split('/')[2])
        except OSError: pass
def score(pg):
    w = struct.unpack('<1024I', pg); s = 0; bl = 0
    for x in w:
        if x == 0xd65f03c0 or (x & 0xffc07fff) == 0xa9807bfd or (x & 0xffc07fff) == 0xa8c07bfd: s += 1
        elif x >> 26 == 0x25: bl += 1
    return s >= 2 and bl >= 4 or s >= 4
def dump(pid, d, ptrs=()):
    os.makedirs(d, exist_ok=True)
    maps = open('/proc/%d/maps' % pid).read(); open(d + '/maps.txt', 'w').write(maps)
    mem = open('/proc/%d/mem' % pid, 'rb', 0); rng = []; tot = 0
    base = min(int(l.split('-')[0], 16) for l in maps.splitlines() if 'libanogs.so' in l)
    for l in maps.splitlines():
        p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]; nm = p[5].strip() if len(p) > 5 else ''
        anon = nm == '' or nm.startswith('[anon') or nm.startswith('/memfd')
        if 'libanogs.so' in nm and p[1].startswith('rw') and b - a < 32 << 20:
            mem.seek(a); open('%s/libanogs-%x-%x.data' % (d, a, b), 'wb').write(mem.read(b - a)); continue
        if isf and 'stack_and_tls' in nm and p[1][0] == 'r':
            mem.seek(a); open('%s/stack-%x-%x.bin' % (d, a, b), 'wb').write(mem.read(b - a)); continue
        if not anon or p[1][0] != 'r' or b - a > 256 << 20 or b - a < 4096 or 'stack_and_tls' in nm: continue
        if isf and not (abs(a - base) < 64 << 20 and 1 << 20 <= b - a <= 16 << 20): continue
        try: mem.seek(a); data = mem.read(b - a)
        except (OSError, ValueError): continue
        if isf:  # storm: no scoring (too slow for the 6 s window); the payload region sits within 64 MB of libanogs
            if abs(a - base) < 64 << 20 and 1 << 20 <= b - a <= 16 << 20: open('%s/region-%x-%x.bin' % (d, a, b), 'wb').write(data)
            continue
        hit = [a + i for i in range(0, len(data) - 4095, 4096) if score(data[i:i + 4096])]
        for h in hit:
            if rng and h - rng[-1][1] <= 8 * 4096: rng[-1][1] = h + 4096
            else: rng.append([h, h + 4096])
    for q in ptrs:
        try: mem.seek(q - (256 << 10)); open('%s/heap-%x.bin' % (d, q), 'wb').write(mem.read(512 << 10))
        except (OSError, ValueError): pass
    open(d + '/ranges.txt', 'w').write(''.join('%x-%x\n' % (a, b) for a, b in rng))
    for a, b in rng:
        if b - a < 3 * 4096 or tot > 300 << 20: continue
        try: mem.seek(a); open('%s/%x-%x.bin' % (d, a, b), 'wb').write(mem.read(b - a)); tot += b - a
        except (OSError, ValueError): pass
    print('scan', d, 'pid', pid, 'ranges', len(rng), 'bytes', tot, flush=True)
n = 0; last = 0
while n < maxd:
    os.read(fd, 4096)
    if os.path.exists(out + '/stop'): break
    if isf:
        f = open(watch, 'rb'); f.seek(pos); new = f.read(); pos += len(new)
        import re as _re
        ptrs = [int(x, 16) for x in _re.findall(rb'free\((0x[0-9a-f]+)\) of no glibc chunk', new)][:3]
        if not ptrs: continue
    if time.time() - last < 3: continue
    pid = findpid()
    if pid: dump(pid, '%s/%d' % (out, n), ptrs); n += 1; last = time.time()
