#!/usr/bin/env python3
"""PGR static-RE helper (agent RE): dump the unpacked libanogs code of a running game from /proc/<pid>/mem, event driven (no sleep).
  sudo python3 re-dump.py OUTDIR WATCHDIR [MAXDUMPS]  (exits when OUTDIR/stop exists and an event arrives: touch it, nothing is killed)
If WATCHDIR is a file (the game's stderr log) the trigger is a new 'no glibc chunk' line in it (the invalid-free storm; the thread is
blocked in the diagnostic, so the stacks are the live chain), and every [anon:stack_and_tls] region is dumped too.
Blocks on inotify of WATCHDIR (ACE's files/ano_tmp); on each event (>= 2 s apart) finds the pid whose maps list libanogs.so and writes
OUTDIR/<n>/maps.txt plus every libanogs mapping, every anonymous exec mapping and every anonymous mapping within 32 MB of the libanogs base.
Reading only; the game is not touched."""
import ctypes, os, struct, sys, time, glob
out, watch = sys.argv[1], sys.argv[2]; maxd = int(sys.argv[3]) if len(sys.argv) > 3 else 4
libc = ctypes.CDLL(None, use_errno=True)
fd = libc.inotify_init(); libc.inotify_add_watch(fd, watch.encode(), 0x100 | 0x80 | 0x2 | 0x8)  # create, moved_to, modify, close_write
os.makedirs(out, exist_ok=True); libc.inotify_add_watch(fd, out.encode(), 0x100)
isf = os.path.isfile(watch); pos = os.path.getsize(watch) if isf else 0
print('READY', flush=True)
n = 0; last = 0
def findpid():
    for m in glob.glob('/proc/[0-9]*/maps'):
        try:
            if 'libanogs.so' in open(m).read(): return int(m.split('/')[2])
        except OSError: pass
def dump(pid, d):
    os.makedirs(d, exist_ok=True)
    maps = open('/proc/%d/maps' % pid).read(); open(d + '/maps.txt', 'w').write(maps)
    rows = []
    for l in maps.splitlines():
        p = l.split(None, 5); a, b = [int(x, 16) for x in p[0].split('-')]; rows.append((a, b, p[1], p[5].strip() if len(p) > 5 else ''))
    base = min(a for a, b, pm, nm in rows if 'libanogs.so' in nm)
    mem = open('/proc/%d/mem' % pid, 'rb', 0); tot = 0
    for a, b, pm, nm in rows:
        anon = nm == '' or nm.startswith('[anon') or nm.startswith('/memfd')
        if not ('libanogs.so' in nm or 'stack_and_tls' in nm or (anon and 'x' in pm) or (anon and abs(a - base) < 32 << 20)): continue
        if 'r' not in pm or b - a > 64 << 20 or tot > 200 << 20: continue
        try: mem.seek(a); data = mem.read(b - a)
        except (OSError, ValueError): continue
        open('%s/%x-%x.%s' % (d, a, b, pm), 'wb').write(data); tot += len(data)
    print('dump', d, 'pid', pid, 'base %x' % base, 'bytes', tot, flush=True)
while n < maxd:
    os.read(fd, 4096)
    if os.path.exists(out + '/stop'): break
    if isf:
        f = open(watch, 'rb'); f.seek(pos); new = f.read(); pos += len(new)
        if b'no glibc chunk' not in new: continue
    if time.time() - last < 2: continue
    pid = findpid()
    if pid: dump(pid, '%s/%d' % (out, n)); n += 1; last = time.time()
