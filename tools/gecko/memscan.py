"""root: find the guest ucontext of Gecko's first SIGSEGV (saved pc = libxul+0x2b0c1ec) and print the frame chain mapped to library offsets.
Usage: sudo python3 memscan.py <pid>"""
import struct, sys
pid = sys.argv[1]
maps = []
for l in open('/proc/%s/maps' % pid):
    p = l.split(); a, b = [int(x, 16) for x in p[0].split('-')]
    maps.append((a, b, p[1], int(p[2], 16), p[5] if len(p) > 5 else ''))
base = next(a for a, b, pr, off, n in maps if n.endswith('/libxul.so') and off == 0)
want = base + 0x2b0c1ec
mem = open('/proc/%s/mem' % pid, 'rb', 0)


def rd(addr, n):
    try:
        mem.seek(addr); return mem.read(n)
    except (OSError, ValueError, OverflowError): return b''


def name(x):
    for a, b, pr, off, n in maps:
        if a <= x < b and n: return '%s+0x%x' % (n.rsplit('/', 1)[-1], x - a + off)
    return None


print('libxul base %x pc %x' % (base, want))
needle = struct.pack('<Q', want)
for a, b, pr, off, n in maps:
    if pr[:2] != 'rw' or n or b - a > 64 << 20: continue
    data = rd(a, b - a)
    i = data.find(needle)
    while i >= 0:
        pos = a + i
        regs = rd(pos - 8 * 32, 8 * 34)  # regs[0..30], sp, pc
        if len(regs) == 8 * 34:
            r = struct.unpack('<34Q', regs)
            print('hit at %x: x0=%x x1=%x x2=%x x8=%x x19=%x x20=%x x29=%x x30=%s sp=%x' % (pos, r[0], r[1], r[2], r[8], r[19], r[20], r[29], name(r[30]), r[31]))
            fp = r[29]; lr = r[30]
            print('  pc', name(want), 'lr', name(lr))
            stk = rd(r[31], 0x3000)
            for k in range(0, len(stk) - 7, 8):
                v = struct.unpack_from('<Q', stk, k)[0]
                nm = name(v)
                if nm and ('.so' in nm) and 'libberberis' not in nm and 'libc.so' not in nm.split('+')[0] + 'x' or (nm and 'libc.so' in nm and 'x86' not in nm): print('  stack+0x%x %s' % (k, nm))
            break
            for k in range(14):
                f = rd(fp, 16)
                if len(f) < 16: break
                nfp, ret = struct.unpack('<QQ', f)
                print('  frame', name(ret) or hex(ret))
                if nfp <= fp: break
                fp = nfp
        i = data.find(needle, i + 8)
# the variable the faulting code read: libxul+0x97197c0 (bss) and neighbours
print('var 0x97197c0 =', rd(base + 0x97197c0, 16).hex())
