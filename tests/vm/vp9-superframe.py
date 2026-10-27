#!/usr/bin/env python3
"""Bundle a hidden keyframe and its visible copy using VP9's real superframe index."""
import struct
import sys
from pathlib import Path
source = Path(sys.argv[1]).read_bytes()
assert source[:4] == b'DKIF' and source[8:12] == b'VP90'
size, pts = struct.unpack_from('<IQ', source, 32)
frame = source[44:44 + size]
assert len(frame) == size and frame[0] & 0x02
hidden = bytes([frame[0] & ~0x02]) + frame[1:]
magnitude = max(1, (size.bit_length() + 7) // 8)
marker = bytes([0xc1 | (magnitude - 1) << 3])
index = marker + size.to_bytes(magnitude, 'little') * 2 + marker
packet = hidden + frame + index
Path(sys.argv[2]).write_bytes(source[:32] + struct.pack('<IQ', len(packet), pts) + packet + source[44 + size:])
print('VP9 superframe fixture: hidden + visible picture', flush=True)
