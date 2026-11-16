#!/usr/bin/env python3
"""Compare ACE's call-gate sequences, translated (BERBERIS_TRACE_GATE dir, patch 0071) vs native (perf script of tools/ace/pgr-gate-native.sh).
  gate-diff.py native.txt <translated dir> [thread tid]      native.txt = perf script -F comm,pid,tid,time,event,uregs | sed 's/ABI:2 *//'
Per thread: the sequence of (callee - libanogs base, x3, return class); prints counts, the first divergence of the busiest threads, and per-callee counts."""
import sys, re, glob, struct, collections, os
B_NATIVE = 0x4000000000

def rclass(v, base, lo, hi):
    if v == 0: return '0'
    if v >= 0xfffffffffffff000: return 'neg%d' % (-(v - (1 << 64)))  # -errno / -1
    if v < 0x10000: return 's%d' % v if v < 16 else 'small'
    if base <= v < base + 0x600000: return 'anogs'
    return 'ptr'  # heap/stack pointers incl. sign-extended 32-bit ones: the address spaces differ per route

def native(path):
    th = collections.defaultdict(list); st = collections.defaultdict(list)
    for l in open(path, errors='replace'):
        m = re.match(r'\s*(.+?)\s+\d+/(\d+)\s+([\d.]+): mem:0x([0-9a-f]+):x:\s+x0:0x([0-9a-f]+)\s+x1:0x([0-9a-f]+)\s+x2:0x([0-9a-f]+)\s+x3:0x([0-9a-f]+)\s+lr:0x([0-9a-f]+)', l)
        if not m: continue
        comm, tid, ts, pc, x0, x1, x2, x3, lr = m.groups(); tid = int(tid); x0, x1, x2, x3, lr = (int(v, 16) for v in (x0, x1, x2, x3, lr))
        if pc.endswith('5200'): st[tid].append((float(ts), x1 - B_NATIVE, x3, x2, lr - B_NATIVE))
        elif st[tid]:
            s = st[tid].pop(); th[tid].append((s[0], s[1], s[2], rclass(x0, B_NATIVE, 0, 0), s[3], x0, s[4], comm))
    return th

def translated(d):
    th = collections.defaultdict(list); st = collections.defaultdict(list); names = {}; comm = {}
    base = {}
    for p in sorted(glob.glob(d + '/maps-*.txt')):
        pid = int(p.split('-')[-2]); b = None
        for l in open(p):
            if 'libanogs.so' in l and int(l.split()[2], 16) == 0:
                a = int(l.split('-')[0], 16); b = a if b is None else min(b, a)
        if b: base[pid] = b
    for p in sorted(glob.glob(d + '/gate-*.bin')):
        pid = int(p.split('-')[-2]); B = base.get(pid) or min(base.values())
        data = open(p, 'rb').read()
        for i in range(0, len(data) - 127, 128):
            ts, kind, tid, x0, x1, x2, x3, x30, x29 = struct.unpack_from('<QIIQQQQQQ', data, i); mem = data[i + 64:i + 128]
            if kind == 2: comm[tid] = mem.split(b'\0')[0].decode(errors='replace')
            elif kind == 3: names[x1 - B] = mem.split(b'\0')[0].decode(errors='replace')
            elif kind == 0: st[tid].append((ts / 1e9, x1 - B, x3, x2, x30 - B, mem))
            elif st[tid]:
                s = st[tid].pop(); th[tid].append((s[0], s[1], s[2], rclass(x0, B, 0, 0), s[3], x0, s[4], comm.get(tid, '?')))
    return th, names, base

def main():
    nat = native(sys.argv[1]); tr, names, base = translated(sys.argv[2])
    print('native threads:', {t: len(v) for t, v in nat.items()})
    print('translated threads:', {t: (len(v), v[0][7]) for t, v in tr.items()}, 'bases', {k: hex(v) for k, v in base.items()})
    ns = max(nat.values(), key=len)
    key = lambda e: (e[1], e[2])
    for t, v in sorted(tr.items(), key=lambda kv: -len(kv[1]))[:3]:
        print('--- thread', t, v[0][7], len(v), 'events vs native', len(ns))
        n = 0
        for a, b in zip(ns, v):
            if key(a) != key(b) or a[3] != b[3]: break
            n += 1
        print('first divergence at index', n, 'time native %.2fs translated %.2fs' % (ns[min(n, len(ns) - 1)][0] - ns[0][0], v[min(n, len(v) - 1)][0] - v[0][0]))
        for j in range(max(0, n - 3), n + 4):
            if j < len(ns) and j < len(v):
                a, b = ns[j], v[j]
                print(' %5d  N callee=+%x x3=%x ret=%s | T callee=+%x(%s) x3=%x ret=%s' % (j, a[1], a[2], a[3], b[1], names.get(b[1], ''), b[2], b[3]))
if __name__ == '__main__': main()
