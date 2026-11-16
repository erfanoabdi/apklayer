#!/usr/bin/env python3
"""Diff what ace-fsdiff.bt recorded:  fsdiff.py native1.txt native2.txt .. -- trans1.txt trans2.txt ..   (the extra-N.txt files)
Lines: "P ns tid nr path" + "X ns tid ret" (first 10 calls per syscall+path), "@c[nr, path]: n" (all calls). Syscalls fold to open/access/stat/readlink/
statfs/getxattr, paths are normalised (digit runs >= 3 -> N, pid/tid dirs). Prints A: native ok / translated fails; A2: the reverse; B1/B2: paths
only one side touched in every run (with call counts); counts: same path, call counts differing by > 3x (ACE loops)."""
import re, sys, collections
FOLD = {'openat':'open','openat2':'open','open':'open','faccessat':'access','faccessat2':'access','access':'access','newfstatat':'stat','statx':'stat','newstat':'stat','newlstat':'stat',
        'readlinkat':'readlink','readlink':'readlink','statfs':'statfs','getxattr':'getxattr','lgetxattr':'getxattr'}
FOLD.update({k: 'create' for k in ('mknodat','mknod','creat','symlinkat','linkat')}); FOLD.update({k: 'mkdir' for k in ('mkdirat','mkdir')}); FOLD.update({k: 'unlink' for k in ('unlinkat','unlink')}); FOLD.update({k: 'rename' for k in ('renameat','renameat2','rename')}); FOLD.update({k: 'attr' for k in ('utimensat','fchmodat','chmod','truncate')})
ARM = {56:'open',437:'open',48:'access',439:'access',79:'stat',291:'stat',78:'readlink',43:'statfs',8:'getxattr',9:'getxattr'}
MAP = [(r'^/home/[^/]+/[^ ]*?/?apklayer/runtime/[^/]+/android-root(/.*)?$', r'\1'),
       (r'^/home/[^/]+/[^ ]*?/?apklayer/[^/]+/app-data/([^/]+)/app(/.*)?$', r'/data/user/0/\1\2'),
       (r'^/home/[^/]+/[^ ]*?/?apklayer/[^/]+/storage/emulated/0(/.*)?$', r'/storage/emulated/0\1'),
       (r'^/home/[^/]+/[^ ]*?/?apklayer/[^/]+/data(/.*)?$', r'/data\1'),
       (r'^/home/[^/]+/[^ ]*?/?apklayer/apps/([^/]+)(/.*)?$', r'/data/app/\1\2'),
       (r'^/data/app/~~[^/]+/([^/]+?)-[A-Za-z0-9_=-]{22}==(/.*)?$', r'/data/app/\1\2'),
       (r'^/(data/user/\d+|data/data|sdcard|storage/self/primary)(/|$)', None)]
COMM = {}
def hostmap(p):
    for a, b in MAP[:-1]: p = re.sub(a, b, p) or '/'
    p = re.sub(r'^/data/(user/\d+|data)(/|$)', r'/data/user/0\2', p)
    return re.sub(r'^/(sdcard|storage/self/primary)(/|$)', r'/storage/emulated/0\2', p)
def norm(p):
    p = hostmap(p)
    p = re.sub(r'/proc/(\d+|self|thread-self)/task/\d+', r'/proc/\1/task/T', p)
    p = re.sub(r'/proc/\d+/', '/proc/PID/', p)
    p = re.sub(r'\d{3,}', 'N', p)
    return re.sub(r'/~~[A-Za-z0-9_=-]+/', '/~~X/', p)
def load(fn, arch, guest=False):
    """guest=True: the translated GP/GX lines (guest svc before the path layer), else the kernel-level P/X lines"""
    ev = []; cnt = collections.defaultdict(int); P, X, C = ('GP', 'GX', '@gc') if guest else ('P', 'X', '@c')
    for l in open(fn, errors='replace'):
        if l.startswith(P + ' ') or l.startswith(X + ' '):
            f = l.rstrip('\n').split(' ', 4); f[0] = f[0][-1]
            if len(f) >= 4 and f[1].isdigit(): ev.append((int(f[1]), f[0], int(f[2]), f[3:], l))
            continue
        m = re.match(re.escape(C) + r'\[(\w+), (.*)\]: (\d+)$', l)
        if m: cnt[((ARM.get(int(m[1])) if m[1].isdigit() else FOLD.get(m[1], m[1])), norm(m[2]))] += int(m[3])
    ev.sort(key=lambda e: e[0]); pend = {}; res = collections.defaultdict(set)
    for ns, kind, tid, rest, _ in ev:
        if kind == 'P':
            pend[tid] = ((ARM.get(int(rest[0])) if rest[0].isdigit() else FOLD.get(rest[0], rest[0])), norm((rest[1] if len(rest) > 1 else '').partition('\t')[0]))
            COMM.setdefault(pend[tid], set()).add((rest[1] if len(rest) > 1 else '').partition('\t')[2])
        elif tid in pend:
            r = int(rest[0]); res[pend.pop(tid)].add(r if r < 0 else 0)
    return res, cnt
def merge(files, arch, guest=False):
    R = collections.defaultdict(set); C = collections.defaultdict(list); per = []
    for f in files:
        r, c = load(f, arch, guest)
        if guest:   # the translated side: the guest's own svc (before the path layer) and what reached the host kernel
            r2, c2 = load(f, arch, False)
            for k, v in r2.items(): r.setdefault(k, set()).update(v)
            for k, v in c2.items(): c[k] = c.get(k, 0) + v
        per.append(set(r))
        for k, v in r.items(): R[k] |= v
        for k, v in c.items(): C[k].append(v)
    return R, C, per
def main():
    a = sys.argv[1:]; i = a.index('--'); G = '--guest' in a; a = [x for x in a if x != '--guest']; i = a.index('--')
    N, NC, nper = merge(a[:i], 'a'); T, TC, tper = merge(a[i+1:], 'x', G)
    print('keys native %d translated %d (per run: native %s, translated %s)' % (len(N), len(T), [len(x) for x in nper], [len(x) for x in tper]))
    print('\n== A: native succeeds, translated fails (never succeeds)')
    for k in sorted(set(N) & set(T)):
        if 0 in N[k] and 0 not in T[k]: print(k, 'native', sorted(N[k]), 'translated', sorted(T[k]))
    print('\n== A2: native fails (never succeeds), translated succeeds')
    for k in sorted(set(N) & set(T)):
        if 0 not in N[k] and 0 in T[k]: print(k, 'native', sorted(N[k]), 'translated', sorted(T[k]))
    print('\n== B1: only native (every native run, no translated run)  [count]')
    for k in sorted(set.intersection(*nper) - set(T)): print(k, sorted(N[k]), NC.get(k), sorted(COMM.get(k, ()))[:3])
    print('\n== B2: only translated (every translated run, no native run)  [count]')
    for k in sorted(set.intersection(*tper) - set(N)): print(k, sorted(T[k]), TC.get(k), sorted(COMM.get(k, ()))[:3])
    print('\n== counts: same key, mean call counts differ > 3x (native/translated)')
    for k in sorted(set(NC) & set(TC)):
        n = sum(NC[k]) / len(NC[k]); t = sum(TC[k]) / len(TC[k])
        if max(n, t) >= 10 and (n > 3 * t or t > 3 * n): print(k, '%.0f / %.0f' % (n, t))
main()
