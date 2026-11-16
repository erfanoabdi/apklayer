#!/usr/bin/env python3
"""List of guest-visible paths for tests/apps/hello-filedump (files/list.txt) from ace-fsdiff.bt outputs: fsdiff-list.py extra-1.txt ... > list.txt
Takes every path whose call succeeded (native or translated), /proc/<pid> -> /proc/self, drops fd/task numbers and host-only trees."""
import re, sys
S = set()
for fn in sys.argv[1:]:
    pend = {}
    for l in open(fn, errors='replace'):
        f = l.rstrip('\n').split(' ', 4)
        if f[0] == 'P' and len(f) == 5: pend[f[2]] = f[4]
        elif f[0] == 'X' and len(f) == 4 and f[2] in pend:
            p = pend.pop(f[2])
            if int(f[3]) < 0 and 'ENOENT-too' not in sys.argv: continue
            p = re.sub(r'^/proc/\d+(/|$)', r'/proc/self\1', p)
            if re.match(r'/(proc|sys|dev|system|system_ext|vendor|product|odm|apex|etc|data|storage|sdcard|mnt|cache|config|acct|linkerconfig|d|debug_ramdisk|metadata|oem)(/|$)', p) and '/fd/' not in p and '/task/' not in p and '/map_files/' not in p: S.add(p)
print('\n'.join(sorted(S)))
