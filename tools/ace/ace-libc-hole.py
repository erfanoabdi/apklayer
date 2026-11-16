#!/usr/bin/env python3
"""Who removes the start of libc's text (PGR/ACE, team lead B)? Input: the run's ace-mem.bt output (extra-N.txt), a served-maps dump
(berberis.dump_maps, any file of the run: libc's lines) and optionally the app log with the 'berberis: FAULT' blocks.
Prints every mmap/munmap/mprotect/madvise/mincore/msync syscall whose range overlaps libc's first text pages, in time order.
Usage: ace-libc-hole.py extra-N.txt maps-<pid>-<k>.txt [lo_off=0x58000 hi_off=0x70000]"""
import re, sys

extra, maps = sys.argv[1], sys.argv[2]
lo_off = int(sys.argv[3], 16) if len(sys.argv) > 3 else 0x58000
hi_off = int(sys.argv[4], 16) if len(sys.argv) > 4 else 0x70000
base = None
for l in open(maps, errors='replace'):
    m = re.match(r'([0-9a-f]+)-([0-9a-f]+) (\S+) ([0-9a-f]+) \S+ \d+\s+(\S*libc\.so)$', l.strip())
    if m and int(m.group(4), 16) == 0:
        base = int(m.group(1), 16)
        break
if base is None:
    sys.exit('libc.so (offset 0) not in ' + maps)
lo, hi = base + lo_off, base + hi_off
print('libc base %#x, window %#x-%#x' % (base, lo, hi))
pat = re.compile(r'^(\d+) (mmap|munmap|mprotect|madvise|mincore|msync) tid (\d+) addr (0x[0-9a-f]+) len (0x[0-9a-f]+)(.*)')
for l in open(extra, errors='replace'):
    m = pat.match(l)
    if not m:
        if ' FAULT ' in l or 'RTSQ' in l or 'EXIT' in l or ' open tid' in l:
            print(l.rstrip()[:160])
        continue
    t, call, tid, a, n, rest = m.groups()
    a, n = int(a, 16), int(n, 16)
    if a < hi and a + n > lo:
        print('%6s ms %-8s tid %s %#x+%#x (%+#x)%s' % (t, call, tid, a, n, a - base, rest.rstrip()))
