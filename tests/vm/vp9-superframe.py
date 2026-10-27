#!/usr/bin/env python3
"""Bundle genuine VP9 pictures with a superframe index, retaining IVF PTS."""
import struct
import sys
from pathlib import Path
source = Path(sys.argv[1]).read_bytes()
assert source[:4] == b'DKIF' and source[8:12] == b'VP90'
mode = sys.argv[3] if len(sys.argv) > 3 else 'hidden'
selected = 2 if mode == 'multi-last' else 0
offset = 32
output = bytearray(source[:32])
for packet in range(3):
    size, pts = struct.unpack_from('<IQ', source, offset)
    frame = source[offset + 12:offset + 12 + size]
    assert len(frame) == size
    offset += 12 + size
    if packet == selected:
        assert frame[0] & 0x02
        first = bytes([frame[0] & ~0x02]) + frame[1:] if mode == 'hidden' else frame
        magnitude = max(1, (size.bit_length() + 7) // 8)
        marker = bytes([0xc1 | (magnitude - 1) << 3])
        index = marker + size.to_bytes(magnitude, 'little') * 2 + marker
        frame = first + frame + index
    output += struct.pack('<IQ', len(frame), pts) + frame
assert offset == len(source)
Path(sys.argv[2]).write_bytes(output)
print('VP9 superframe fixture: ' + mode, flush=True)
