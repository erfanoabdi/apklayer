#!/bin/sh
# procsem: hello-procsem (tests/apps/hello-procsem/procsem.c) under the translator (x86_64 hosts): the multi-record proc files the guest reads (maps, smaps, numa_maps,
# mounts, mountinfo) behave like the kernel's seq_files (read: whole lines, <= 4095 bytes whatever the count; fstat size 0; lseek END/DATA/HOLE EINVAL; svc too),
# and no read of any served file writes past its count (canary guard). bridge/native/seq_read.c. Ground truth: the same code as a static program on real arm64
# (tests/apps/hello-procsem/compare.py <real table> <this table> diffs two runs). Not for arm64 hosts: the native route reads the memfd (known, see docs/PLAN.md).
set -eu
[ "$(uname -m)" = x86_64 ] || { echo "procsem: x86_64 hosts only"; exit 0; }
out=$(mktemp)
trap 'rm -f "$out" "$out.t"; bin/apklayer stop >/dev/null 2>&1 || true' EXIT
APKLAYER_JOPTS=-Dapklayer.runms=60000 bin/apklayer run hello.procsem > "$out" 2>&1 || true
grep -q '\[procsem\] DONE' "$out"
sed -n 's/^.*\[procsem\] //p' "$out" | grep -v '^DONE' > "$out.t"
! grep -E ' MORE|ovr=[1-9]' "$out.t"
python3 - "$out.t" <<'PY'
import re, sys
rows = {}
for l in open(sys.argv[1]):
    f = l.rstrip('\n').split('|', 2)
    if len(f) == 3: rows[(f[0], f[1])] = f[2]
bad = []
for p in ('/proc/self/maps', '/proc/self/smaps', '/proc/self/numa_maps', '/proc/self/mountinfo', '/proc/self/mounts'):
    g = lambda op: rows.get((p, op), '')
    if 'size=0 ' not in g('open'): bad.append(p + ' fstat size ' + g('open'))
    for op in ('lseek(END 0)', 'lseek(DATA 0)'):
        if not g(op).startswith('-1|22'): bad.append(p + ' ' + op + ' ' + g(op))
    if not g('svc-lseek(END 0)').startswith('-22'): bad.append(p + ' svc-lseek ' + g('svc-lseek(END 0)'))
    for op in ('read(65536)', 'svc-read(65536)', 'read(1M)', 'readv(10,4096,10)'):
        n = int(g(op).split('|')[0] or 0)
        if not 0 < n <= 4095: bad.append(p + ' ' + op + ' returned ' + str(n))
    m = re.search(r'max=(\d+) .*badend=(\d+) badstart=(\d+)', g('loop4096'))
    if not m or int(m[1]) > 4095 or m[2] != '0' or m[3] != '0': bad.append(p + ' loop4096 ' + g('loop4096'))
print('\n'.join(bad) or 'procsem: seq_file reads as the kernel\'s')
sys.exit(1 if bad else 0)
PY
