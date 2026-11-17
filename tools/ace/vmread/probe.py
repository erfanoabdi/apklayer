#!/usr/bin/env python3
# native perturbation probe: at T0 s after libanogs appears do ONE thing to the game, then only watch. MODE: maps (read /proc/pid/maps) | smaps | scan (read all anon rw regions <=64MB) | vm (read the 0x44000 regions only) | none
import os, sys, time, glob
mode, T0, pre = sys.argv[1], float(sys.argv[2]), int(sys.argv[3])
def findpid():
    for m in glob.glob('/proc/[0-9]*/maps'):
        try:
            if int(m.split('/')[2]) > pre and 'libanogs.so' in open(m).read(): return int(m.split('/')[2])
        except OSError: pass
pid = None
while not pid: pid = findpid()
t0 = time.time()
while time.time() - t0 < T0: time.sleep(0.05)
maps = open('/proc/%d/maps' % pid).read()
open(sys.argv[4] if len(sys.argv) > 4 else '/dev/null', 'w').write(maps)
if mode == 'smaps': n = len(open('/proc/%d/smaps' % pid).read()); print('smaps bytes', n)
if mode in ('abs16', 'absnot16', 'abs16one', 'abs16two'):
    import struct
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY); pm = os.open('/proc/%d/pagemap' % pid, os.O_RDONLY); n = 0; k = 0
    for l in maps.splitlines():
        p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]
        if len(p) > 5 or p[1] != 'rw-p' or b - a > 0x40000: continue
        is16 = (b - a) == 0x10000
        v = struct.unpack('<%dQ' % ((b - a) // 4096), os.pread(pm, (b - a) // 4096 * 8, a // 4096 * 8))
        if any(x >> 63 & 1 for x in v) and is16: continue
        if is16: k += 1
        if (mode.startswith('abs16') and not is16) or (mode == 'absnot16' and is16): continue
        if mode == 'abs16one' and k != 1: continue
        if mode == 'abs16two' and k > 2: continue
        for i, x in enumerate(v):
            if not (x >> 63 & 1):
                try: os.pread(fd, 4096, a + i * 4096); n += 1
                except OSError: pass
    print(mode, 'pages read', n, 'absent-16 regions seen', k)
if mode.startswith('asl:'):
    import struct, re
    want = set()
    for part in mode[4:].split(','):
        if part:
            x, y = part.split('-'); want.update(range(int(x), int(y) + 1))
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY); pm = os.open('/proc/%d/pagemap' % pid, os.O_RDONLY); n = 0
    for blk in re.split(r'\n(?=[0-9a-f]+-[0-9a-f]+ )', open('/proc/%d/smaps' % pid).read()):
        h = blk.split('\n')[0].split()
        a, b = [int(x, 16) for x in h[0].split('-')]
        if len(h) > 5 or h[1] != 'rw-p' or b - a > 0x40000 or re.search(r'VmFlags:.* gu', blk): continue
        v = struct.unpack('<%dQ' % ((b - a) // 4096), os.pread(pm, (b - a) // 4096 * 8, a // 4096 * 8))
        for i, x in enumerate(v):
            if not (x >> 63 & 1):
                if n in want:
                    print('page', n, hex(a + i * 4096), 'region', hex(a), hex(b - a))
                    try: os.pread(fd, 4096, a + i * 4096)
                    except OSError: print('  EIO')
                n += 1
    print('absent pages total', n)
if mode.startswith('asr:'):
    import struct, re
    _, lo, hi = mode.split(':'); lo, hi = int(lo), int(hi)
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY); pm = os.open('/proc/%d/pagemap' % pid, os.O_RDONLY); n = 0
    for blk in re.split(r'\n(?=[0-9a-f]+-[0-9a-f]+ )', open('/proc/%d/smaps' % pid).read()):
        h = blk.split('\n')[0].split()
        a, b = [int(x, 16) for x in h[0].split('-')]
        if len(h) > 5 or h[1] != 'rw-p' or b - a > 0x40000 or re.search(r'VmFlags:.* gu', blk): continue
        v = struct.unpack('<%dQ' % ((b - a) // 4096), os.pread(pm, (b - a) // 4096 * 8, a // 4096 * 8))
        for i, x in enumerate(v):
            if not (x >> 63 & 1):
                if lo <= n < hi:
                    print('page', n, hex(a + i * 4096), 'region', hex(a), hex(b - a))
                    try: os.pread(fd, 4096, a + i * 4096)
                    except OSError: print('  EIO')
                n += 1
    print('absent pages total', n)
if mode == 'smaps16':
    import re
    for blk in re.split(r'\n(?=[0-9a-f]+-[0-9a-f]+ )', open('/proc/%d/smaps' % pid).read()):
        h = blk.split('\n')[0].split()
        a, b = [int(x, 16) for x in h[0].split('-')]
        if h[1] == 'rw-p' and b - a == 0x10000 and len(h) == 5: print(blk)
if mode.startswith('as:'):
    import struct
    _, lo, hi = mode.split(':'); lo, hi = int(lo), int(hi)
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY); pm = os.open('/proc/%d/pagemap' % pid, os.O_RDONLY); n = 0
    for l in maps.splitlines():
        p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]
        if len(p) > 5 or p[1] != 'rw-p' or b - a > 0x40000: continue
        v = struct.unpack('<%dQ' % ((b - a) // 4096), os.pread(pm, (b - a) // 4096 * 8, a // 4096 * 8))
        for i, x in enumerate(v):
            if not (x >> 63 & 1):
                if lo <= n < hi:
                    print('page', n, hex(a + i * 4096), 'region', hex(a), hex(b - a))
                    try: os.pread(fd, 4096, a + i * 4096)
                    except OSError as e: print('  EIO')
                n += 1
    print('absent pages total', n)
if mode in ('absent', 'present', 'absent_small'):
    import struct
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY); pm = os.open('/proc/%d/pagemap' % pid, os.O_RDONLY); n = 0; regs = 0
    for l in maps.splitlines():
        p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]
        if len(p) > 5 or p[1] != 'rw-p' or b - a > 64 << 20: continue
        if mode == 'absent_small' and b - a > 0x40000: continue
        v = struct.unpack('<%dQ' % ((b - a) // 4096), os.pread(pm, (b - a) // 4096 * 8, a // 4096 * 8)); hit = 0
        for i, x in enumerate(v):
            ispres = x >> 63 & 1
            if (mode == 'present') == bool(ispres):
                try: os.pread(fd, 4096, a + i * 4096); n += 1; hit = 1
                except OSError: pass
        regs += hit
    print(mode, 'pages read', n, 'regions', regs)
if mode.startswith('sz'):
    pg = [int(x) for x in mode[2:].split(',')]
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY); tot = 0; n = 0
    for l in maps.splitlines():
        p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]
        if len(p) > 5 or p[1] != 'rw-p' or (b - a) // 4096 not in pg: continue
        try: tot += len(os.pread(fd, b - a, a)); n += 1
        except OSError: pass
    print('sz', pg, 'regions', n, 'bytes', tot)
if mode.startswith('rwx'):
    lines = maps.splitlines(); k = int(mode[3:])
    ri = [i for i, l in enumerate(lines) if l.split()[1] == 'rwxp' and len(l.split()) == 5 and int(l.split()[0].split('-')[1], 16) - int(l.split()[0].split('-')[0], 16) == 0x1000]
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY)
    print('rwx pages', len(ri))
    for d in (-1, 1):
        l = lines[ri[k] + d]; a, b = [int(x, 16) for x in l.split()[0].split('-')]
        print('read', d, l[:70]); print(len(os.pread(fd, b - a, a)) if l.split()[1] == 'rw-p' else 'skip')
if mode.startswith('anch'):
    lines = maps.splitlines(); ai = [i for i, l in enumerate(lines) if 'android-all-16-robolectric' in l][0]
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY)
    for d in [int(x) for x in mode.split(':')[1].split(',')]:
        l = lines[ai + d]; a, b = [int(x, 16) for x in l.split()[0].split('-')]
        print('read', d, l[:60]); print(len(os.pread(fd, b - a, a)))
if mode.startswith('idx'):
    _, lo, hi = mode.split(':'); lo, hi = int(lo), int(hi)
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY); tot = 0; i = 0
    for l in maps.splitlines():
        p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]
        if len(p) > 5 or p[1] != 'rw-p' or b - a > 64 << 20: continue
        if lo <= i < hi:
            print('reg', i, hex(a), hex(b - a))
            try: tot += len(os.pread(fd, b - a, a))
            except OSError: pass
        i += 1
    print('regions', i, 'read bytes', tot)
if mode in ('scan', 'vm'):
    fd = os.open('/proc/%d/mem' % pid, os.O_RDONLY); tot = 0
    for l in maps.splitlines():
        p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]
        if len(p) > 5 or p[1] != 'rw-p' or (mode == 'scan' and b - a > 64 << 20) or (mode == 'vm' and b - a != 0x44000): continue
        try: tot += len(os.pread(fd, b - a, a))
        except OSError: pass
    print('read bytes', tot)
print('probe', mode, 'done at t=%.1f' % (time.time() - t0), flush=True)
