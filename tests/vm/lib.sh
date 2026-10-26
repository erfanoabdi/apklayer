# Helpers shared by the VM tests (source this file).

# bars_ok <png>: true if the picture shows very different colours at the left and the right of its middle row
# (the colour bars of videotestsrc, i.e. a decoded video frame is on screen).
bars_ok() {
  python3 - "$1" <<'E2'
import struct, zlib, sys
d = open(sys.argv[1], "rb").read(); i = 8; idat = b""
while i < len(d):
    n, t = struct.unpack(">I4s", d[i:i + 8]); b = d[i + 8:i + 8 + n]
    if t == b"IHDR": w, h = struct.unpack(">II", b[:8])
    if t == b"IDAT": idat += b
    i += 12 + n
raw = zlib.decompress(idat); y = h // 2
px = lambda x: raw[y * (w * 4 + 1) + 1 + x * 4:][:3]
a, b2 = px(w // 10), px(w - w // 10)
sys.exit(0 if sum(abs(a[k] - b2[k]) for k in range(3)) > 150 else 1)
E2
}
