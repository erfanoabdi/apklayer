#!/usr/bin/env python3
# stackscan.py PID TID [WORDS]: a crude native backtrace of a blocked thread without ptrace: its pc and sp from
# /proc/PID/task/TID/syscall, then every stack word that points into an executable file mapping (candidate return
# addresses), printed as library+offset (offset = address - mapping start + file offset), ready for addr2line.
import sys

pid, tid = sys.argv[1], sys.argv[2]
words = int(sys.argv[3]) if len(sys.argv) > 3 else 4096
maps = []
for line in open(f"/proc/{pid}/maps"):
    p = line.split()
    lo, hi = (int(x, 16) for x in p[0].split("-"))
    maps.append((lo, hi, p[1], int(p[2], 16), p[5] if len(p) > 5 else ""))


def where(a):
    for lo, hi, perm, off, path in maps:
        if lo <= a < hi:
            return perm, path, a - lo + off
    return None


sc = open(f"/proc/{pid}/task/{tid}/syscall").read().split()
print("syscall", " ".join(sc))
sp, pc = int(sc[-2], 16), int(sc[-1], 16)
w = where(pc)
print(f"pc {pc:#x} {w[1]}+{w[2]:#x}" if w else f"pc {pc:#x} ?")
mem = open(f"/proc/{pid}/mem", "rb", 0)
mem.seek(sp)
data = b""
try:
    data = mem.read(words * 8)
except OSError:
    for n in (2048, 1024, 512, 256):
        try:
            mem.seek(sp); data = mem.read(n * 8); break
        except OSError:
            pass
for i in range(0, len(data) - 7, 8):
    a = int.from_bytes(data[i:i + 8], "little")
    w = where(a)
    if w and "x" in w[0] and w[1].startswith("/"):
        print(f"  sp+{i:#06x} {w[1]}+{w[2]:#x}")
