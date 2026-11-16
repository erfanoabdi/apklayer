#!/usr/bin/env python3
"""Decode and compare the interpreter branch traces of patch 0049 (BERBERIS_TRACE_BB=<dir>, files bb-<pid>-<tid>.bin).

  bbtrace.py dump FILE [N]            first N records as module offsets (src -> tgt; 'X' = outside the module)
  bbtrace.py sys FILE                 the svc records (type 3: x8, x0) with the running branch count
  bbtrace.py diff A B [CTX]           first divergence of two traces (offsets inside [lo,hi), outside collapsed to one token)
  bbtrace.py tail FILE [N]            last N records (the path into a death)
Record: v=(zigzag(src-last_tgt)<<2)|0 + varint zigzag(tgt-src); 1: v>>2 = ns timestamp; 2: v>>2 = pc, 33 raw u64 follow
(x0..x30, sp, flags); 3: v>>2 = x8, varints x0 x1 x2 follow (x8 0xffff: the svc's return in x0; 0xfffe: AES ring, count + (Vd,Vn) pairs; 0xfffd: memory dump, len + bytes).
"""
import struct, sys


def varint(b, i):
    v = s = 0
    while True:
        c = b[i]; i += 1
        v |= (c & 0x7f) << s; s += 7
        if c < 0x80:
            return v, i


def unzz(v):
    return (v >> 1) ^ -(v & 1)


def load(path):
    b = open(path, 'rb').read()
    magic, lo, hi, _ = struct.unpack_from('<4Q', b, 0)
    assert magic == 0x31544242, 'not a trace'
    i, last, out, n = 32, 0, [], 0
    while i < len(b):
        try:
            v, j = varint(b, i)
            t = v & 3
            if t == 0:
                src = (last + unzz(v >> 2)) & (2**64 - 1)
                d, j = varint(b, j)
                tgt = (src + unzz(d)) & (2**64 - 1)
                out.append(('b', src, tgt)); last = tgt; n += 1
            elif t == 1:
                out.append(('t', v >> 2, n))
            elif t == 2:
                regs = struct.unpack_from('<33Q', b, j); j += 264
                out.append(('r', v >> 2, regs))
            else:
                x8 = v >> 2
                if x8 == 0xfffe:    # AES ring: count, then (Vd, Vn) 16-byte pairs
                    c, j = varint(b, j)
                    out.append(('a', [(b[j + 32 * k:j + 32 * k + 16], b[j + 32 * k + 16:j + 32 * k + 32]) for k in range(c)]))
                    j += 32 * c
                elif x8 == 0xfffc:  # memory region: base, len, bytes
                    base, j = varint(b, j)
                    c, j = varint(b, j)
                    out.append(('g', base, b[j:j + c], n))
                    j += c
                elif x8 == 0xfffd:  # memory dump
                    c, j = varint(b, j)
                    out.append(('m', b[j:j + c], n))
                    j += c
                else:
                    x0, j = varint(b, j)
                    x1, j = varint(b, j)
                    x2, j = varint(b, j)
                    out.append(('s', x8, x0, n, x1, x2))
            i = j
        except IndexError:
            break  # a truncated tail
    return lo, hi, out


RNG = {}  # trace path -> [(start, end, pgoff, tag)] from maps-<pid>.txt, when present


def load_maps(path):
    import os, re
    m = re.search(r'bb-(\d+)-', os.path.basename(path))
    f = os.path.join(os.path.dirname(path), 'maps-%s.txt' % m.group(1)) if m else ''
    r = []
    if os.path.exists(f):
        for k, l in enumerate(open(f)):
            p = l.split()
            a, b = (int(x, 16) for x in p[0].split('-'))
            r.append((a, b, int(p[2], 16), k))
    return r


def off(a, lo, hi):
    """File offset of a (with the module's own mapping list when maps-<pid>.txt exists), else offset from lo."""
    for (s0, e0, po, k) in CUR:
        if s0 <= a < e0:
            return po + (a - s0) + (k << 40)  # k<<40: which mapping, see fmt
    return None if CUR else (a - lo if lo <= a < hi else None)


def norm(recs, lo, hi):
    """Branch records as (src_off|None, tgt_off|None); consecutive outside->outside collapsed."""
    r = []
    for x in recs:
        if x[0] != 'b':
            continue
        e = (off(x[1], lo, hi), off(x[2], lo, hi))
        if e == (None, None):
            continue
        r.append(e)
    return r


def fmt(e):
    f = lambda o: 'X' if o is None else ('+%#x' % o if o < 1 << 40 else 'm%d+%#x' % (o >> 40, o & ((1 << 40) - 1)))
    return '%s -> %s' % (f(e[0]), f(e[1]))


CUR = []


def main():
    global CUR
    a = sys.argv
    CUR = load_maps(a[2])
    if a[1] == 'dump':
        lo, hi, r = load(a[2]); k = 0
        for x in r:
            if x[0] == 'b':
                print(k, fmt((off(x[1], lo, hi), off(x[2], lo, hi))), '' if off(x[1], lo, hi) is not None or off(x[2], lo, hi) is not None else hex(x[1])); k += 1
            else:
                print(x[:2])
            if len(a) > 3 and k >= int(a[3]):
                break
    elif a[1] == 'sys':
        lo, hi, r = load(a[2])
        for x in r:
            if x[0] == 's':
                print('branch#%d x8=%d x0=%#x x1=%#x x2=%#x' % (x[3], x[1], x[2], x[4], x[5]))
    elif a[1] == 'tail':
        lo, hi, r = load(a[2]); n = int(a[3]) if len(a) > 3 else 60
        for x in r[-n:]:
            print(fmt((off(x[1], lo, hi), off(x[2], lo, hi))) if x[0] == 'b' else x[:2])
    elif a[1] == 'diff':
        la, ha, ra = load(a[2]); lb, hb, rb = load(a[3]); ctx = int(a[4]) if len(a) > 4 else 8
        na, nb = norm(ra, la, ha), norm(rb, lb, hb)
        print('records', len(na), len(nb))
        k = next((i for i in range(min(len(na), len(nb))) if na[i] != nb[i]), None)
        if k is None:
            print('identical over the common length', min(len(na), len(nb))); return
        print('first divergence at record', k)
        for i in range(max(0, k - ctx), min(k + ctx, len(na), len(nb))):
            print('%7d %s%-40s | %-40s' % (i, '>' if i == k else ' ', fmt(na[i]), fmt(nb[i])))


main()
