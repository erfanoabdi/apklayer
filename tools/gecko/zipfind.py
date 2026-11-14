"""root, on a process frozen by zipstop.py (KEEP=1): <pid>: find the omni.ja nsZipHandle (mFileData,mLen at +0x50) and the nsZipArchive that owns it
(mFd at +8, 256-entry name hash table at +0x50), walk the table and look up some names the way nsZipArchive::GetItem does (h = h*37+c, & 0xff)."""
import struct, sys, zipfile
pid = sys.argv[1]; apk = sys.argv[2]
maps = []
for l in open('/proc/%s/maps' % pid):
    p = l.split(); a, b = [int(x, 16) for x in p[0].split('-')]
    maps.append((a, b, p[1], int(p[2], 16), p[5] if len(p) > 5 else ''))
lo = next(a for a, b, pr, off, n in maps if n.endswith('/base.apk') and off == 0)
zi = zipfile.ZipFile(apk).getinfo('assets/omni.ja')
f = open(apk, 'rb'); f.seek(zi.header_offset); nl, el = struct.unpack('<HH', f.read(30)[26:30])
omni = lo + zi.header_offset + 30 + nl + el; olen = zi.file_size
mem = open('/proc/%s/mem' % pid, 'rb', 0)
def rd(a, n):
    try: mem.seek(a); return mem.read(n)
    except (OSError, ValueError, OverflowError): return b''
def scan(needle, align=8):
    for a, b, pr, off, n in maps:
        if pr[:2] != 'rw' or (n and not n.startswith('[anon') and n != '[heap]') or b - a > (1 << 30): continue
        d = rd(a, b - a); i = d.find(needle)
        while i >= 0:
            if i % align == 0: yield a + i, d
            i = d.find(needle, i + 1)
print('omni data %x len %d' % (omni, olen))
handles = [a - 0x50 for a, d in scan(struct.pack('<Q', omni)) if struct.unpack_from('<I', d, a - 0 and (a - next(m[0] for m in maps if m[0] <= a < m[1])) + 8)[0] == olen]
print('handles (ptr at +0x50, len at +0x58):', [hex(h) for h in handles])
for h in handles:
    print(' handle %x: ' % h, ' '.join('%x' % q for q in struct.unpack('<12Q', rd(h, 96))))
    for a, d in scan(struct.pack('<Q', h)):
        A = a - 8
        tab = struct.unpack('<256Q', rd(A + 0x50, 2048))
        nz = [t for t in tab if t]
        print('  archive? %x (holds handle %x): buckets used %d; head %s' % (A, h, len(nz), ' '.join('%x' % q for q in struct.unpack('<12Q', rd(A, 96)))))
        if len(nz) < 4: continue
        names = []
        for t in nz:
            q = t; hops = 0
            while q and hops < 100000:
                hops += 1
                r = rd(q, 18)
                if len(r) < 18: print('  bad item ptr %x' % q); break
                nx, hdr, nlen = struct.unpack('<QQH', r); names.append((rd(hdr + 0x2e, nlen), hdr, q)); q = nx
        print('  items', len(names), 'first', names[:3], 'header range', hex(min(x[1] for x in names)), hex(max(x[1] for x in names)))
        def h37(s):
            v = 0
            for c in s: v = (v * 37 + c) & 0xffffffff
            return v & 0xff
        for want in (b'greprefs.js', b'defaults/pref/geckoview-prefs.js', b'chrome.manifest'):
            hh = h37(want); q = tab[hh]; chain = []
            while q and len(chain) < 50 and len(rd(q, 18)) == 18:
                nx, hdr, nlen = struct.unpack('<QQH', rd(q, 18)); chain.append(rd(hdr + 0x2e, nlen)); q = nx
            print('  %s bucket %d chain %s; in file %s' % (want, hh, chain[:5], want.decode() in zipfile.ZipFile(f.name).namelist() if False else 'n/a'))
