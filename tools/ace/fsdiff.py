#!/usr/bin/env python3
"""Diff what ace-fsdiff.bt recorded:  fsdiff.py native1.txt native2.txt .. -- trans1.txt trans2.txt ..   (the extra-N.txt files)
Lines: "P ns tid nr path" + "X ns tid ret" (first 10 calls per syscall+path), "@c[nr, path]: n" (all calls). Syscalls fold to open/access/stat/readlink/
statfs/getxattr, paths are normalised (digit runs >= 3 -> N, pid/tid dirs). Prints A: native ok / translated fails; A2: the reverse; B1/B2: paths
only one side touched in every run (with call counts); counts: same path, call counts differing by > 3x (ACE loops)."""
import re, sys, collections
NAMES = {'a': {56:'open',437:'open',48:'access',439:'access',79:'stat',291:'stat',78:'readlink',43:'statfs',8:'getxattr',9:'getxattr'},
         'x': {2:'open',257:'open',437:'open',21:'access',269:'access',439:'access',4:'stat',6:'stat',262:'stat',332:'stat',89:'readlink',267:'readlink',137:'statfs',191:'getxattr',192:'getxattr'}}
def norm(p):
    p = re.sub(r'/proc/(\d+|self|thread-self)/task/\d+', r'/proc/\1/task/T', p)
    p = re.sub(r'/proc/\d+/', '/proc/PID/', p)
    p = re.sub(r'\d{3,}', 'N', p)
    return re.sub(r'/~~[A-Za-z0-9_=-]+/', '/~~X/', p)
def load(fn, arch):
    ev = []; cnt = collections.defaultdict(int)
    for l in open(fn, errors='replace'):
        if l.startswith('P ') or l.startswith('X '):
            f = l.rstrip('\n').split(' ', 4)
            if len(f) >= 4 and f[1].isdigit(): ev.append((int(f[1]), f[0], int(f[2]), f[3:], l))
            continue
        m = re.match(r'@c\[(\d+), (.*)\]: (\d+)$', l)
        if m: cnt[(NAMES[arch].get(int(m[1]), m[1]), norm(m[2]))] += int(m[3])
    ev.sort(key=lambda e: e[0]); pend = {}; res = collections.defaultdict(set)
    for ns, kind, tid, rest, _ in ev:
        if kind == 'P': pend[tid] = (NAMES[arch].get(int(rest[0]), rest[0]), norm(rest[1] if len(rest) > 1 else ''))
        elif tid in pend:
            r = int(rest[0]); res[pend.pop(tid)].add(r if r < 0 else 0)
    return res, cnt
def merge(files, arch):
    R = collections.defaultdict(set); C = collections.defaultdict(list); per = []
    for f in files:
        r, c = load(f, arch); per.append(set(r))
        for k, v in r.items(): R[k] |= v
        for k, v in c.items(): C[k].append(v)
    return R, C, per
def main():
    a = sys.argv[1:]; i = a.index('--'); N, NC, nper = merge(a[:i], 'a'); T, TC, tper = merge(a[i+1:], 'x')
    print('keys native %d translated %d (per run: native %s, translated %s)' % (len(N), len(T), [len(x) for x in nper], [len(x) for x in tper]))
    print('\n== A: native succeeds, translated fails (never succeeds)')
    for k in sorted(set(N) & set(T)):
        if 0 in N[k] and 0 not in T[k]: print(k, 'native', sorted(N[k]), 'translated', sorted(T[k]))
    print('\n== A2: native fails (never succeeds), translated succeeds')
    for k in sorted(set(N) & set(T)):
        if 0 not in N[k] and 0 in T[k]: print(k, 'native', sorted(N[k]), 'translated', sorted(T[k]))
    print('\n== B1: only native (every native run, no translated run)  [count]')
    for k in sorted(set.intersection(*nper) - set(T)): print(k, sorted(N[k]), NC.get(k))
    print('\n== B2: only translated (every translated run, no native run)  [count]')
    for k in sorted(set.intersection(*tper) - set(N)): print(k, sorted(T[k]), TC.get(k))
    print('\n== counts: same key, mean call counts differ > 3x (native/translated)')
    for k in sorted(set(NC) & set(TC)):
        n = sum(NC[k]) / len(NC[k]); t = sum(TC[k]) / len(TC[k])
        if max(n, t) >= 10 and (n > 3 * t or t > 3 * n): print(k, '%.0f / %.0f' % (n, t))
main()
