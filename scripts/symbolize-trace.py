#!/usr/bin/env python3
"""symbolize-trace: turns the "[runtime-trace]" lines printed by bridge/native/runtime_shim.c (-Dapklayer.runtimetrace=true)
into library + nearest exported function names. Usage: symbolize-trace.py <log file>
Frames inside libraries loaded by bionic_translation have no debug info; the nearest preceding *exported* symbol is shown
(static functions appear as <that symbol>+offset)."""
import re, subprocess, sys, bisect
log = open(sys.argv[1], errors="replace").read()
maps = []  # (start, end, offset, path)
for m in re.finditer(r"^([0-9a-f]+)-([0-9a-f]+) \S+ ([0-9a-f]+) \S+ \d+\s+(\S+\.so\S*)", log, re.M):
    maps.append((int(m[1], 16), int(m[2], 16), int(m[3], 16), m[4]))
cache = {}
def symbols(path):
    if path not in cache:
        out = subprocess.run(["readelf", "-W", "--dyn-syms", path], capture_output=True, text=True).stdout.splitlines()
        syms = sorted((int(p[1], 16), p[7]) for p in (l.split() for l in out) if len(p) >= 8 and p[3] == "FUNC" and p[1] != "0000000000000000")
        cache[path] = ([s[0] for s in syms], syms)
    return cache[path]
def where(addr):
    for start, end, off, path in maps:
        if start <= addr < end:
            fo = addr - start + off
            keys, syms = symbols(path)
            i = bisect.bisect_right(keys, fo) - 1
            name = syms[i][1] if i >= 0 else "?"
            demangled = subprocess.run(["c++filt", name], capture_output=True, text=True).stdout.strip()
            return "%s+0x%x in %s" % (demangled[:90], fo - (syms[i][0] if i >= 0 else 0), path.split("/")[-1])
    return "(not in an Android library)"
for m in re.finditer(r"\[runtime-trace\] (?:signal \d+ pc|#\d+ lr)=0x([0-9a-f]+)", log):
    print(m[0].split("]")[1].strip().split("=")[0].ljust(10), where(int(m[1], 16)))
