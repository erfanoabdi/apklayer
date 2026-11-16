#!/usr/bin/env python3
# negcheck.py table.inc libs... : count words of the libs' .text matching each table row (a hit in a lib that has no business with the feature = over-broad mask)
import glob, os, re, subprocess, sys, tempfile
BIN = os.path.dirname(sorted(glob.glob(os.path.expanduser('~/Library/Android/sdk/ndk/*/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android26-clang')))[-1])
rows = []
for l in open(sys.argv[1]):
    m = re.match(r'\s*\{0x([0-9a-f]+), 0x([0-9a-f]+)\},\s*// (.*)', l)
    rows.append((int(m.group(1), 16), int(m.group(2), 16), m.group(3)))
tot = [0] * len(rows)
for lib in sys.argv[2:]:
    with tempfile.TemporaryDirectory() as d:
        b = os.path.join(d, 't.bin')
        r = subprocess.run([BIN + '/llvm-objcopy', '-O', 'binary', '--only-section=.text', lib, b], capture_output=True)
        if r.returncode or not os.path.exists(b): continue
        data = open(b, 'rb').read()
    ws = [int.from_bytes(data[i:i + 4], 'little') for i in range(0, len(data) - 3, 4)]
    hits = {}
    for k, (mask, val, nm) in enumerate(rows):
        c = sum(1 for w in ws if (w & mask) == val)
        if c: hits[nm.split()[0]] = hits.get(nm.split()[0], 0) + c
        tot[k] += c
    print(os.path.basename(lib), len(ws), 'words', hits)
