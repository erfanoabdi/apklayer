"""root, on a process frozen by zipstop.py (KEEP=1): <pid> <hex address> [n qwords]: dump memory as qwords; resolve pointers to maps."""
import struct, sys
pid, addr = sys.argv[1], int(sys.argv[2], 16); n = int(sys.argv[3]) if len(sys.argv) > 3 else 32
maps = []
for l in open('/proc/%s/maps' % pid):
    p = l.split(); a, b = [int(x, 16) for x in p[0].split('-')]
    maps.append((a, b, p[1], int(p[2], 16), p[5] if len(p) > 5 else ''))
mem = open('/proc/%s/mem' % pid, 'rb', 0)
def nm(x):
    for a, b, pr, off, n_ in maps:
        if a <= x < b: return '%s+0x%x' % (n_.rsplit('/', 1)[-1] or 'anon', x - a + off)
for i in range(n):
    mem.seek(addr + 8 * i); v = struct.unpack('<Q', mem.read(8))[0]
    print('%x: %016x %s' % (addr + 8 * i, v, nm(v) or ''))
