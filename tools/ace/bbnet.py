#!/usr/bin/env python3
"""bbnet.py TRACE...: the sendto/recvfrom memory dumps ('m') and AES rings ('a') of patch 0049's trace (see bbtrace.py).
For each AES ring: the AESE/AESD (Vd, Vn) of the last <=512 ops before the call; a block's first op shows the plaintext-side
block in Vd (ECB) and the round-0 key in Vn (xor key schedule: round keys follow in later Vn of the same block series).
Prints hex + ascii of each dump; and, with KEY=<32 hex> (the AES-128 key), decrypts the dump as ECB/CBC(iv=0) for a look."""
import os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bbtrace.py')).read().replace('\nmain()\n', '\n')
ns = {}
exec(src, ns)


def asc(b):
    return ''.join(chr(c) if 32 <= c < 127 else '.' for c in b)


for f in sys.argv[1:]:
    lo, hi, r = ns['load'](f)
    for x in r:
        if x[0] == 'm':
            print('== %s mem dump %d bytes after %d branches' % (os.path.basename(f), len(x[1]), x[2]))
            for i in range(0, len(x[1]), 16):
                print('%04x  %s  %s' % (i, x[1][i:i + 16].hex(' '), asc(x[1][i:i + 16])))
        elif x[0] == 'a':
            print('== %s AES ring %d ops' % (os.path.basename(f), len(x[1])))
            for k, (vd, vn) in enumerate(x[1][:64]):
                print('  %3d Vd=%s Vn=%s' % (k, vd.hex(), vn.hex()))
