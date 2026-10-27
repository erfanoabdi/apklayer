#!/bin/sh
# Thin tree patch: retain virtual mapping for non-DMA-heap linear allocations.
set -eu
python3 - "$1" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'frameworks/av/media/codec2/vndk/C2Buffer.cpp'
s = p.read_text()
old = 'if (android::C2HandleIon::IsUsed()) {'
new = '''// apklayer patch: BLOB and other linear allocators own their mapping.
    if (android::C2HandleIon::IsUsed() ||
            !android::C2DmaBufAllocator::CheckHandle(mAllocation->handle())) {'''
if old in s:
    assert s.count(old) == 2, 'review changed C2 linear mapping code'
    p.write_text(s.replace(old, new))
else:
    assert s.count(new) == 2, 'review changed C2 linear mapping code'
PY
