#!/usr/bin/env python3
# residency of every anon rw/--- region of the game at T s after libanogs appears: pagemap present/exclusive/softdirty counts + smaps Rss/Referenced/AnonHugePages. No page content is read.
import os, sys, time, glob, struct, re
out, T0, pre = sys.argv[1], [float(x) for x in sys.argv[2].split(',')], int(sys.argv[3])
def findpid():
    for m in glob.glob('/proc/[0-9]*/maps'):
        try:
            if int(m.split('/')[2]) > pre and 'libanogs.so' in open(m).read(): return int(m.split('/')[2])
        except OSError: pass
pid = None
while not pid: pid = findpid()
t0 = time.time()
for T in T0:
    while time.time() - t0 < T: time.sleep(0.05)
    smaps = open('/proc/%d/smaps' % pid).read()
    pm = os.open('/proc/%d/pagemap' % pid, os.O_RDONLY)
    f = open('%s-%d.txt' % (out, T), 'w')
    for blk in re.split(r'\n(?=[0-9a-f]+-[0-9a-f]+ )', smaps):
        h = blk.split('\n')[0].split(None, 5)
        a, b = [int(x, 16) for x in h[0].split('-')]; name = h[5] if len(h) > 5 else ''
        kv = dict(re.findall(r'^(\w+):\s+(\d+) kB', blk, re.M))
        n = (b - a) // 4096; pres = ex = sd = 0
        if n <= 1 << 16:
            try:
                d = os.pread(pm, n * 8, (a // 4096) * 8)
                for (v,) in struct.iter_unpack('<Q', d):
                    pres += v >> 63 & 1; ex += v >> 56 & 1; sd += v >> 55 & 1
            except OSError: pass
        f.write('%x %x %s %s rss=%s ref=%s anon=%s ahp=%s pres=%d excl=%d sd=%d thp=%s\n' % (a, b - a, h[1], name, kv.get('Rss'), kv.get('Referenced'), kv.get('Anonymous'), kv.get('AnonHugePages'), pres, ex, sd, re.search(r'THPeligible:\s+(\d)', blk).group(1) if 'THPeligible' in blk else '-'))
    f.close(); print('done', T, flush=True)
