#!/usr/bin/env python3
"""First divergence of the syscall sequences of the game's threads, native vs translated (ace-sysdiff-*.bt output).
usage: sysdiff.py native.txt translated.txt [min_len=30]   Threads are paired by the paths of their first path-bearing syscalls
(identical first 3 paths); per pair the (nr, return class ok/-errno) sequences are compared, the first divergences are printed."""
import sys, collections

def load(fn):
    th = collections.OrderedDict(); pend = {}
    for line in open(fn, errors="replace"):
        f = line.rstrip("\n").split(" ", 7)
        if f[0] == "E" and len(f) >= 7:
            tid = f[2]; rec = [int(f[1]), int(f[3]), f[4:7], f[7] if len(f) > 7 else "", None]
            th.setdefault(tid, []).append(rec); pend[tid] = rec
        elif f[0] == "P" and len(f) >= 3 and f[1] in pend:
            pend[f[1]][3] = " ".join(f[2:])
        elif f[0] == "X" and len(f) >= 3 and f[1] in pend:
            try: pend[f[1]][4] = int(f[2])
            except ValueError: pass
    return th

def cls(r):
    if r is None: return "none"
    return "ok" if r >= 0 or r < -4095 else "E%d" % -r  # big unsigned pointers print negative: treated as ok when below -4095

def key(rec): return (rec[1], cls(rec[4]))

def sig(seq): return tuple(r[3] for r in seq if r[3])[:3]

def main():
    nat, tr = load(sys.argv[1]), load(sys.argv[2]); minlen = int(sys.argv[3]) if len(sys.argv) > 3 else 30
    bysig = collections.defaultdict(list)
    for t, s in tr.items():
        if len(s) >= minlen and sig(s): bysig[sig(s)].append(t)
    for t, s in nat.items():
        if len(s) < minlen or not sig(s) or sig(s) not in bysig: continue
        t2 = bysig[sig(s)].pop(0); s2 = tr[t2]
        n = min(len(s), len(s2)); shown = 0
        print("== native tid %s (%d) <-> translated tid %s (%d) first paths %s" % (t, len(s), t2, len(s2), sig(s)))
        for i in range(n):
            if key(s[i]) != key(s2[i]):
                print("  #%d native nr %d ret %s a %s %s | translated nr %d ret %s a %s %s" % (i, s[i][1], cls(s[i][4]), s[i][2], s[i][3], s2[i][1], cls(s2[i][4]), s2[i][2], s2[i][3]))
                shown += 1
                if shown >= 5: break
        if not shown: print("  identical for %d syscalls" % n)

main()
