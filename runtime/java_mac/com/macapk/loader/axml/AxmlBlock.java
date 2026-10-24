// com.macapk.loader.axml.AxmlBlock -- a COMPILED XML document (aapt "AXML"),
// parsed once into an immutable tree that any number of parsers can walk.
//
// This is the Mac.apk analogue of AOSP's android.content.res.XmlBlock plus the
// native ResXMLTree it wraps (frameworks/base core/java/android/content/res/
// XmlBlock.java; libs/androidfw/ResourceTypes.cpp ResXMLTree / ResXMLParser):
// the block owns the bytes' decoded structure, newParser() hands out a cursor.
// Like AOSP's block it is safe to share across threads -- nothing here mutates
// after construction -- which is what lets PlatformXml cache one block per
// framework-res entry for the life of the process (framework-res is immutable,
// the same reason AssetManager keeps its XmlBlock cache).
//
// FORMAT (libs/androidfw/include/androidfw/ResourceTypes.h, aosp main; the
// same layout com.macapk.loader.BinaryXmlParser documents and reads):
//   ResChunk_header        { u16 type; u16 headerSize; u32 size; }
//   RES_XML_TYPE 0x0003    the file header
//   RES_STRING_POOL 0x0001 ResStringPool_header { hdr; u32 stringCount,
//                          styleCount, flags (UTF8_FLAG = 1<<8), stringsStart,
//                          stylesStart; } then stringCount u32 offsets
//   RES_XML_RESOURCE_MAP 0x0180  u32 attr resource id per attribute-NAME string
//                          index (ResXMLTree::mResIds)
//   ResXMLTree_node        { hdr; u32 lineNumber; ResStringPool_ref comment; }
//     START_NAMESPACE 0x0100 / END_NAMESPACE 0x0101: namespaceExt { prefix, uri }
//     START_ELEMENT 0x0102:  attrExt { ns, name; u16 attributeStart,
//                            attributeSize, attributeCount, idIndex, classIndex,
//                            styleIndex }  (attributeStart is relative to the
//                            attrExt; id/class/style indexes are 1-BASED, 0 = none)
//     END_ELEMENT 0x0103:    endElementExt { ns, name }
//     CDATA 0x0104:          cdataExt { ResStringPool_ref data; Res_value typedData }
//   ResXMLTree_attribute   { ns, name, rawValue; Res_value typedValue
//                            { u16 size; u8 res0; u8 dataType; u32 data } }
//
// Only the events AOSP's XmlBlock.Parser reports are kept: START_TAG, END_TAG
// and TEXT. android_util_XmlBlock.cpp nativeNext loops past every other
// ResXMLParser code (the namespace chunks) without returning them.
package com.macapk.loader.axml;

public final class AxmlBlock {
    // ResChunk types (ResourceTypes.h).
    static final int RES_STRING_POOL_TYPE    = 0x0001;
    static final int RES_XML_TYPE            = 0x0003;
    static final int RES_XML_START_NAMESPACE = 0x0100;
    static final int RES_XML_END_NAMESPACE   = 0x0101;
    static final int RES_XML_START_ELEMENT   = 0x0102;
    static final int RES_XML_END_ELEMENT     = 0x0103;
    static final int RES_XML_CDATA           = 0x0104;
    static final int RES_XML_RESOURCE_MAP    = 0x0180;

    // XmlPullParser event codes, which ResXMLParser shares numerically for
    // the three kinds kept here.
    static final int EV_START_TAG = 2;
    static final int EV_END_TAG   = 3;
    static final int EV_TEXT      = 4;

    /** The document's string pool (ResXMLTree::mStrings), decoded. */
    final String[] strings;
    /** attr resource id per attribute-name string index (ResXMLTree::mResIds). */
    final int[] resIds;

    // One row per reported event, in document order.
    final int[] evType;
    final int[] evLine;
    final int[] evNs;       // element namespace string index, -1 = none
    final int[] evName;     // element name string index (-1 for TEXT)
    final int[] evText;     // CDATA data string index (TEXT only, else -1)
    final int[] evAttrFirst;   // index into the attr arrays (START_TAG only)
    final int[] evAttrCount;
    final int[] evIdIndex;     // 1-based, 0 = none (START_TAG only)
    final int[] evClassIndex;
    final int[] evStyleIndex;

    // Attributes, flattened across every START_TAG.
    final int[] atNs;
    final int[] atName;
    final int[] atRaw;
    final int[] atType;
    final int[] atData;

    /** The number of events (START_TAG + END_TAG + TEXT). */
    public int eventCount() { return evType.length; }

    private AxmlBlock(String[] strings, int[] resIds, IntList ev, IntList at) {
        this.strings = strings;
        this.resIds = resIds;
        int n = ev.size() / EV_STRIDE;
        evType = new int[n]; evLine = new int[n]; evNs = new int[n]; evName = new int[n];
        evText = new int[n]; evAttrFirst = new int[n]; evAttrCount = new int[n];
        evIdIndex = new int[n]; evClassIndex = new int[n]; evStyleIndex = new int[n];
        for (int i = 0; i < n; i++) {
            int b = i * EV_STRIDE;
            evType[i] = ev.get(b);      evLine[i] = ev.get(b + 1);
            evNs[i] = ev.get(b + 2);    evName[i] = ev.get(b + 3);
            evText[i] = ev.get(b + 4);  evAttrFirst[i] = ev.get(b + 5);
            evAttrCount[i] = ev.get(b + 6); evIdIndex[i] = ev.get(b + 7);
            evClassIndex[i] = ev.get(b + 8); evStyleIndex[i] = ev.get(b + 9);
        }
        int m = at.size() / AT_STRIDE;
        atNs = new int[m]; atName = new int[m]; atRaw = new int[m]; atType = new int[m]; atData = new int[m];
        for (int i = 0; i < m; i++) {
            int b = i * AT_STRIDE;
            atNs[i] = at.get(b);   atName[i] = at.get(b + 1); atRaw[i] = at.get(b + 2);
            atType[i] = at.get(b + 3); atData[i] = at.get(b + 4);
        }
    }

    /** True when `bytes` begin with the RES_XML_TYPE chunk header
     *  (u16 type = 0x0003, u16 headerSize = 0x0008, little-endian): the same
     *  4-byte discrimination AssetManager.openXmlBlockAsset and our own
     *  Resources.getXml make between compiled and text XML. */
    public static boolean isCompiled(byte[] bytes) {
        return bytes != null && bytes.length >= 8
            && bytes[0] == 0x03 && bytes[1] == 0x00 && bytes[2] == 0x08 && bytes[3] == 0x00;
    }

    /** Parse a compiled XML document. Throws IllegalArgumentException on
     *  anything that is not well-formed AXML -- AOSP's ResXMLTree::setTo
     *  rejects the same inputs (BAD_TYPE), and a half-read tree is worse than
     *  none. */
    public static AxmlBlock parse(byte[] d) {
        if (!isCompiled(d)) {
            throw new IllegalArgumentException("not compiled XML (no RES_XML_TYPE header)");
        }
        final int total = Math.min(i32(d, 4), d.length);
        if (total < 8) throw new IllegalArgumentException("truncated XML header");
        String[] pool = null;
        int[] resIds = new int[0];
        IntList ev = new IntList(64 * EV_STRIDE);
        IntList at = new IntList(128 * AT_STRIDE);
        int depth = 0;
        boolean sawRoot = false;
        int off = u16(d, 2);   // the file header's own headerSize
        while (off + 8 <= total) {
            final int type = u16(d, off);
            final int headerSize = u16(d, off + 2);
            final int size = i32(d, off + 4);
            if (size < 8 || headerSize < 8 || headerSize > size || off + size > total) {
                throw new IllegalArgumentException("bad chunk at 0x" + Integer.toHexString(off)
                    + " (type 0x" + Integer.toHexString(type) + ", size " + size + ")");
            }
            switch (type) {
                case RES_STRING_POOL_TYPE:
                    if (pool == null) pool = readPool(d, off, headerSize, size);
                    break;
                case RES_XML_RESOURCE_MAP: {
                    int n = (size - headerSize) / 4;
                    resIds = new int[n];
                    for (int i = 0; i < n; i++) resIds[i] = i32(d, off + headerSize + i * 4);
                    break;
                }
                case RES_XML_START_ELEMENT: {
                    if (headerSize < 16) throw new IllegalArgumentException("short node header");
                    final int line = i32(d, off + 8);
                    final int ext = off + headerSize;
                    if (ext + 20 > off + size) throw new IllegalArgumentException("short attrExt");
                    final int ns = i32(d, ext);
                    final int name = i32(d, ext + 4);
                    final int attrStart = u16(d, ext + 8);
                    final int attrSize = u16(d, ext + 10);
                    final int attrCount = u16(d, ext + 12);
                    final int idIndex = u16(d, ext + 14);
                    final int classIndex = u16(d, ext + 16);
                    final int styleIndex = u16(d, ext + 18);
                    final int first = at.size() / AT_STRIDE;
                    int a = ext + attrStart;
                    for (int i = 0; i < attrCount; i++, a += attrSize) {
                        if (attrSize < 20 || a + 20 > off + size) {
                            throw new IllegalArgumentException("attribute " + i + " out of its chunk");
                        }
                        at.add(i32(d, a));            // ns
                        at.add(i32(d, a + 4));        // name
                        at.add(i32(d, a + 8));        // rawValue
                        at.add(d[a + 15] & 0xff);     // typedValue.dataType
                        at.add(i32(d, a + 16));       // typedValue.data
                    }
                    ev.add(EV_START_TAG); ev.add(line); ev.add(ns); ev.add(name); ev.add(-1);
                    ev.add(first); ev.add(attrCount); ev.add(idIndex); ev.add(classIndex); ev.add(styleIndex);
                    depth++;
                    sawRoot = true;
                    break;
                }
                case RES_XML_END_ELEMENT: {
                    if (headerSize < 16) throw new IllegalArgumentException("short node header");
                    final int line = i32(d, off + 8);
                    final int ext = off + headerSize;
                    if (ext + 8 > off + size) throw new IllegalArgumentException("short endElementExt");
                    ev.add(EV_END_TAG); ev.add(line); ev.add(i32(d, ext)); ev.add(i32(d, ext + 4)); ev.add(-1);
                    ev.add(0); ev.add(0); ev.add(0); ev.add(0); ev.add(0);
                    depth--;
                    break;
                }
                case RES_XML_CDATA: {
                    if (headerSize < 16) throw new IllegalArgumentException("short node header");
                    final int line = i32(d, off + 8);
                    final int ext = off + headerSize;
                    if (ext + 4 > off + size) throw new IllegalArgumentException("short cdataExt");
                    ev.add(EV_TEXT); ev.add(line); ev.add(-1); ev.add(-1); ev.add(i32(d, ext));
                    ev.add(0); ev.add(0); ev.add(0); ev.add(0); ev.add(0);
                    break;
                }
                case RES_XML_START_NAMESPACE:
                case RES_XML_END_NAMESPACE:
                default:
                    // Namespace chunks carry nothing XmlBlock.Parser reports
                    // (its getNamespace*() calls throw "not supported"); the
                    // attribute and element ns refs already hold the URI.
                    break;
            }
            off += size;
        }
        if (pool == null) throw new IllegalArgumentException("no string pool");
        if (!sawRoot) throw new IllegalArgumentException("no root element");
        if (depth != 0) throw new IllegalArgumentException("unbalanced elements (depth " + depth + ")");
        return new AxmlBlock(pool, resIds, ev, at);
    }

    /** String `idx` of the pool, or null for -1 / out of range (a
     *  ResStringPool_ref of 0xffffffff means "no string"). */
    String str(int idx) {
        return (idx >= 0 && idx < strings.length) ? strings[idx] : null;
    }

    // ---- string pool (ResStringPool) -------------------------------------
    private static String[] readPool(byte[] d, int off, int headerSize, int size) {
        final int count = i32(d, off + 8);
        final int flags = i32(d, off + 16);
        final int stringsStart = i32(d, off + 20);
        final boolean utf8 = (flags & 0x100) != 0;   // ResStringPool_header::UTF8_FLAG
        if (count < 0 || headerSize + (long) count * 4 > size) {
            throw new IllegalArgumentException("string pool index out of its chunk");
        }
        final String[] out = new String[count];
        final int idxBase = off + headerSize;
        final int strBase = off + stringsStart;
        final int end = off + size;
        for (int i = 0; i < count; i++) {
            int s = strBase + i32(d, idxBase + i * 4);
            if (s < strBase || s >= end) { out[i] = ""; continue; }
            out[i] = utf8 ? readUtf8(d, s, end) : readUtf16(d, s, end);
        }
        return out;
    }

    /** UTF-16 entry: u16 length (high bit set = a second u16 extends it),
     *  then that many UTF-16 units. ResStringPool::stringAt. */
    private static String readUtf16(byte[] d, int p, int end) {
        if (p + 2 > end) return "";
        int len = u16(d, p);
        p += 2;
        if ((len & 0x8000) != 0) {
            if (p + 2 > end) return "";
            len = ((len & 0x7fff) << 16) | u16(d, p);
            p += 2;
        }
        if (p + (long) len * 2 > end) return "";
        char[] c = new char[len];
        for (int i = 0; i < len; i++) c[i] = (char) u16(d, p + i * 2);
        return new String(c);
    }

    /** UTF-8 entry: the UTF-16 length then the UTF-8 byte length, each one
     *  byte (high bit set = a second byte extends it), then the bytes.
     *  ResStringPool::string8At / decodeLength. */
    private static String readUtf8(byte[] d, int p, int end) {
        if (p + 2 > end) return "";
        int u16len = d[p++] & 0xff;
        if ((u16len & 0x80) != 0) p++;
        int blen = d[p++] & 0xff;
        if ((blen & 0x80) != 0) {
            if (p >= end) return "";
            blen = ((blen & 0x7f) << 8) | (d[p++] & 0xff);
        }
        if (p + blen > end) return "";
        return new String(d, p, blen, java.nio.charset.StandardCharsets.UTF_8);
    }

    static int u16(byte[] d, int o) { return (d[o] & 0xff) | ((d[o + 1] & 0xff) << 8); }
    static int i32(byte[] d, int o) {
        return (d[o] & 0xff) | ((d[o + 1] & 0xff) << 8) | ((d[o + 2] & 0xff) << 16) | ((d[o + 3] & 0xff) << 24);
    }

    private static final int EV_STRIDE = 10;
    private static final int AT_STRIDE = 5;

    /** A growable int array, so a parse allocates a handful of arrays rather
     *  than one boxed object per attribute. */
    private static final class IntList {
        private int[] a;
        private int n;
        IntList(int cap) { a = new int[Math.max(cap, 16)]; }
        void add(int v) {
            if (n == a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
            a[n++] = v;
        }
        int get(int i) { return a[i]; }
        int size() { return n; }
    }
}
