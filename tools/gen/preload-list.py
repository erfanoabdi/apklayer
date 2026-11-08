#!/usr/bin/env python3
"""preload-list.py <app log> <spare log> [<app log> <spare log>...] > runtime/host/resources/preloaded-classes: the framework and JDK classes
apps load after a prepared spare's point (an app JVM's -Xlog:class+load minus a prepared spare's), in first-seen order, which the spare loads
while it waits (apklayer.host.AppSpare, zygote's preloaded-classes); how many apps load each on stderr. The logs: each app started cold with
APKLAYER_JOPTS="-Dapklayer.runms=15000 -Xlog:class+load=info:file=<dir>/<pkg>.%p.log" (one log of the app's JVM, one of the spare it left).
The list of 2026-11-08: NewPipe 0.29.1, Calculator (opencalculator), UiBench on Parallels."""
import re, sys, collections
LINE = re.compile(r'\[class,load\] (\S+) source: (.*)$')
OK = ('shared objects file', 'jrt:/', '__JVM_DefineClass__')
count, order = collections.Counter(), {}
for app, spare in zip(sys.argv[1::2], sys.argv[2::2]):
    seen = {m.group(1) for m in map(LINE.search, open(spare, errors='replace')) if m}
    mine = set()
    for line in open(app, errors='replace'):
        m = LINE.search(line)
        if not m or m.group(1) in seen or m.group(1) in mine: continue
        name, src = m.groups()
        if not src.startswith(OK) or '$$Lambda' in name or 'LambdaForm$' in name or '/0x' in name: continue
        mine.add(name)
        order.setdefault(name, len(order))
    count.update(mine)
for name in sorted(count, key=order.get):
    print(name)
print('%d classes; in all %d logs: %d' % (len(count), len(sys.argv) // 2, sum(1 for c in count.values() if c == len(sys.argv) // 2)), file=sys.stderr)
