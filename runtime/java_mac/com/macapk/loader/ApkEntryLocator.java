// ApkEntryLocator -- where an entry's DATA sits inside an APK on disk.
//
// java.util.zip.ZipFile can read an entry but never says where its bytes are,
// and that offset is exactly what AssetManager.openFd / openNonAssetFd have to
// answer: on a device an AssetFileDescriptor for a STORED asset is the
// installed APK's own fd plus (startOffset, length) -- libandroidfw
// _FileAsset::openFileDescriptor, which Unity mmaps and MediaPlayer/SoundPool
// seek into -- and a COMPRESSED asset cannot be opened that way at all
// (AssetManager.openFd throws "This file can not be opened as a file
// descriptor; it is probably compressed"). Before v1.0.1586 we spilled every
// such asset into a /tmp file, which answered both cases with a shape no
// device presents.
//
// This is the same walk the bionic shim's native asset index does in C++
// (bionic_shim.cpp nz_index): the End Of Central Directory record (searched
// back from the file's end, ZIP64 locator honoured), then every central
// directory record for name / method / sizes / local-header offset, and the
// local header's own name+extra lengths for the data offset (the two can
// differ; APPNOTE 4.4.x). Indexed once per apk (keyed by path + size + mtime)
// and cached; the data offset is resolved lazily on first ask.
package com.macapk.loader;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class ApkEntryLocator {
    private ApkEntryLocator() {}

    /** One entry's placement. {@code method} is the zip method (0 STORED,
     *  8 DEFLATED); {@code dataOffset} is the absolute file offset of the
     *  first data byte; sizes are the central directory's. */
    public static final class Location {
        public final String apkPath;
        public final long   dataOffset;
        public final long   compressedSize;
        public final long   size;
        public final int    method;
        Location(String apkPath, long dataOffset, long compressedSize, long size, int method) {
            this.apkPath = apkPath; this.dataOffset = dataOffset;
            this.compressedSize = compressedSize; this.size = size; this.method = method;
        }
        public boolean isStored() { return method == 0; }
    }

    private static final class Raw {
        long lho, csize, usize; int method; long dataOffset = -1;
    }
    private static final class Index {
        final String key;
        final Map<String, Raw> entries = new HashMap<>();
        Index(String key) { this.key = key; }
    }
    private static final Map<String, Index> sCache = new HashMap<>();

    /** The placement of {@code entryName} in {@code apkPath}, or null when the
     *  apk has no such entry (or is not a readable zip). */
    public static Location locate(String apkPath, String entryName) throws IOException {
        Index ix = indexFor(apkPath);
        if (ix == null) return null;
        Raw r;
        synchronized (ix) { r = ix.entries.get(entryName); }
        if (r == null) return null;
        synchronized (r) {
            if (r.dataOffset < 0) {
                try (RandomAccessFile f = new RandomAccessFile(apkPath, "r")) {
                    byte[] lh = new byte[30];
                    f.seek(r.lho);
                    f.readFully(lh);
                    if (le32(lh, 0) != 0x04034b50L) return null;
                    r.dataOffset = r.lho + 30 + le16(lh, 26) + le16(lh, 28);
                }
            }
        }
        return new Location(apkPath, r.dataOffset, r.csize, r.usize, r.method);
    }

    private static Index indexFor(String apkPath) throws IOException {
        File f = new File(apkPath);
        if (!f.isFile()) return null;
        String key = f.getAbsolutePath() + "|" + f.length() + "|" + f.lastModified();
        synchronized (sCache) {
            Index ix = sCache.get(f.getAbsolutePath());
            if (ix != null && ix.key.equals(key)) return ix;
        }
        Index ix = new Index(key);
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long fsz = raf.length();
            if (fsz < 22) return null;
            int tail = (int) Math.min(fsz, 65557L);
            byte[] buf = new byte[tail];
            raf.seek(fsz - tail);
            raf.readFully(buf);
            int eocd = -1;
            for (int i = tail - 22; i >= 0; i--) {
                if (le32(buf, i) == 0x06054b50L) { eocd = i; break; }
            }
            if (eocd < 0) return null;
            long cdCount = le16(buf, eocd + 10);
            long cdSize  = le32(buf, eocd + 12);
            long cdOff   = le32(buf, eocd + 16);
            if (cdCount == 0xFFFFL || cdSize == 0xFFFFFFFFL || cdOff == 0xFFFFFFFFL) {
                if (eocd < 20 || le32(buf, eocd - 20) != 0x07064b50L) return null;
                long z64 = le64(buf, eocd - 20 + 8);
                byte[] rec = new byte[56];
                raf.seek(z64);
                raf.readFully(rec);
                if (le32(rec, 0) != 0x06064b50L) return null;
                cdCount = le64(rec, 32); cdSize = le64(rec, 40); cdOff = le64(rec, 48);
            }
            if (cdOff + cdSize > fsz || cdSize > Integer.MAX_VALUE) return null;
            byte[] cd = new byte[(int) cdSize];
            raf.seek(cdOff);
            raf.readFully(cd);
            int p = 0;
            for (long i = 0; i < cdCount && p + 46 <= cd.length; i++) {
                if (le32(cd, p) != 0x02014b50L) break;
                Raw r = new Raw();
                r.method = le16(cd, p + 10);
                r.csize  = le32(cd, p + 20);
                r.usize  = le32(cd, p + 24);
                int nlen = le16(cd, p + 28), xlen = le16(cd, p + 30), clen = le16(cd, p + 32);
                r.lho    = le32(cd, p + 42);
                if (p + 46 + nlen + xlen + clen > cd.length) break;
                String name = new String(cd, p + 46, nlen, StandardCharsets.UTF_8);
                if (r.usize == 0xFFFFFFFFL || r.csize == 0xFFFFFFFFL || r.lho == 0xFFFFFFFFL) {
                    // ZIP64 extra field 0x0001: only the saturated fields are
                    // present, in the order usize, csize, lho (APPNOTE 4.5.3).
                    int q = p + 46 + nlen, qend = q + xlen;
                    while (q + 4 <= qend) {
                        int id = le16(cd, q), sz = le16(cd, q + 2);
                        int rr = q + 4;
                        if (id == 0x0001) {
                            if (r.usize == 0xFFFFFFFFL && rr + 8 <= qend) { r.usize = le64(cd, rr); rr += 8; }
                            if (r.csize == 0xFFFFFFFFL && rr + 8 <= qend) { r.csize = le64(cd, rr); rr += 8; }
                            if (r.lho   == 0xFFFFFFFFL && rr + 8 <= qend) { r.lho   = le64(cd, rr); rr += 8; }
                            break;
                        }
                        q += 4 + sz;
                    }
                }
                if (!name.isEmpty() && !name.endsWith("/")) ix.entries.put(name, r);
                p += 46 + nlen + xlen + clen;
            }
        }
        synchronized (sCache) { sCache.put(f.getAbsolutePath(), ix); }
        return ix;
    }

    private static int le16(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }
    private static long le32(byte[] b, int o) {
        return (b[o] & 0xffL) | ((b[o + 1] & 0xffL) << 8) | ((b[o + 2] & 0xffL) << 16) | ((b[o + 3] & 0xffL) << 24);
    }
    private static long le64(byte[] b, int o) {
        return le32(b, o) | (le32(b, o + 4) << 32);
    }
}
