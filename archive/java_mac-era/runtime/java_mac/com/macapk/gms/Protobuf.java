// com.macapk.gms.Protobuf -- a minimal protocol-buffers wire codec.
//
// WHY WE HAND-ROLL ONE. Google's private Play/checkin APIs speak protobuf, and
// the messages we need are a handful of scalar fields in a shallow tree. Pulling
// in protobuf-java would mean vendoring a ~1.7 MB dependency (and bundling it,
// per CLAUDE.md "Runtime freshness") to read four integers and a string. The
// wire format itself is small, stable since proto2, and fully specified -- so
// rung 3 of the decision ladder ("if neither, BUILD THE TOOLING") is cheaper
// here than rung 2, and it keeps the bundle honest.
//
// SPEC: https://protobuf.dev/programming-guides/encoding/
//   tag        = (field_number << 3) | wire_type      (itself a varint)
//   wire_type  0 = VARINT          int32/int64/uint32/uint64/sint*/bool/enum
//              1 = I64             fixed64/sfixed64/double
//              2 = LEN             string/bytes/embedded message/packed repeated
//              5 = I32             fixed32/sfixed32/float
//   wire types 3 and 4 (start/end group) are proto2-only and deprecated; we
//   reject them rather than pretend to skip them, because a group we cannot
//   parse means every subsequent field offset is garbage and a silent skip
//   would hand the caller confidently wrong values.
//
// DELIBERATELY NOT A FULL IMPLEMENTATION: no descriptors, no reflection, no
// unknown-field retention, no maps. The caller names the field numbers it wants
// because it read them out of the .proto. Anything unrecognised is SKIPPED, per
// the protobuf forward-compatibility rule -- which is also what lets a Google
// server add fields without breaking us.
package com.macapk.gms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class Protobuf {

    public static final int WIRE_VARINT = 0;
    public static final int WIRE_I64    = 1;
    public static final int WIRE_LEN    = 2;
    public static final int WIRE_I32    = 5;

    private Protobuf() {}

    // ===================================================================
    // Writer
    // ===================================================================

    /** Builds one message. Fields may be emitted in any order (the spec allows
     *  it), but we emit in ascending field number because that is what every
     *  real encoder does and it makes a hexdump diff against a captured request
     *  readable. */
    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(256);

        private void tag(int field, int wire) { varint((long) field << 3 | wire); }

        private void varint(long v) {
            // Unsigned LEB128. A negative int32 is sign-extended to 64 bits
            // first, which is why proto encodes negative int32 in TEN bytes --
            // that is the spec's behaviour, not a bug to "fix" here.
            while (true) {
                if ((v & ~0x7FL) == 0) { out.write((int) v); return; }
                out.write(((int) v & 0x7F) | 0x80);
                v >>>= 7;
            }
        }

        public Writer int32 (int field, int v)    { tag(field, WIRE_VARINT); varint(v); return this; }
        public Writer int64 (int field, long v)   { tag(field, WIRE_VARINT); varint(v); return this; }
        public Writer uint32(int field, int v)    { tag(field, WIRE_VARINT); varint(v & 0xFFFFFFFFL); return this; }
        public Writer bool  (int field, boolean v){ tag(field, WIRE_VARINT); varint(v ? 1 : 0); return this; }

        public Writer fixed64(int field, long v) {
            tag(field, WIRE_I64);
            for (int i = 0; i < 8; i++) out.write((int) (v >>> (8 * i)) & 0xFF);
            return this;
        }

        public Writer fixed32(int field, int v) {
            tag(field, WIRE_I32);
            for (int i = 0; i < 4; i++) out.write((v >>> (8 * i)) & 0xFF);
            return this;
        }

        /** A null string is OMITTED, not written as empty. On the wire those are
         *  different things to a proto3 reader (absent vs present-and-empty), and
         *  sending an empty fingerprint is not the same claim as sending none. */
        public Writer string(int field, String v) {
            if (v == null) return this;
            return bytes(field, v.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        public Writer bytes(int field, byte[] v) {
            if (v == null) return this;
            tag(field, WIRE_LEN);
            varint(v.length);
            out.write(v, 0, v.length);
            return this;
        }

        /** Embed a sub-message. Null is omitted, same reasoning as string(). */
        public Writer message(int field, Writer sub) {
            return sub == null ? this : bytes(field, sub.toByteArray());
        }

        public byte[] toByteArray() { return out.toByteArray(); }
        public int size() { return out.size(); }
    }

    public static Writer writer() { return new Writer(); }

    // ===================================================================
    // Reader
    // ===================================================================

    /** A cursor over one message's bytes. Single-pass by design: the caller
     *  loops readTag() and dispatches on the field number, exactly the shape of
     *  a generated parser's mergeFrom(). */
    public static final class Reader {
        private final byte[] buf;
        private int pos;
        private final int end;

        public Reader(byte[] b) { this(b, 0, b.length); }
        public Reader(byte[] b, int off, int len) { buf = b; pos = off; end = off + len; }

        public boolean hasMore() { return pos < end; }
        public int position() { return pos; }

        private int u8() throws IOException {
            if (pos >= end) throw new IOException("protobuf: truncated at " + pos);
            return buf[pos++] & 0xFF;
        }

        public long varint() throws IOException {
            long result = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                int b = u8();
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return result;
            }
            throw new IOException("protobuf: varint longer than 10 bytes");
        }

        /** @return the raw tag, or -1 at end of message. */
        public int readTag() throws IOException {
            if (!hasMore()) return -1;
            int tag = (int) varint();
            if (tag == 0) throw new IOException("protobuf: zero tag (field 0 is illegal)");
            return tag;
        }

        public static int fieldOf(int tag) { return tag >>> 3; }
        public static int wireOf (int tag) { return tag & 7; }

        public long fixed64() throws IOException {
            long v = 0;
            for (int i = 0; i < 8; i++) v |= (long) u8() << (8 * i);
            return v;
        }

        public int fixed32() throws IOException {
            int v = 0;
            for (int i = 0; i < 4; i++) v |= u8() << (8 * i);
            return v;
        }

        public byte[] bytes() throws IOException {
            int len = (int) varint();
            if (len < 0 || pos + len > end) throw new IOException("protobuf: bad length " + len);
            byte[] b = new byte[len];
            System.arraycopy(buf, pos, b, 0, len);
            pos += len;
            return b;
        }

        public String string() throws IOException {
            return new String(bytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        /** A LEN field re-read as a nested message, without copying. */
        public Reader message() throws IOException {
            int len = (int) varint();
            if (len < 0 || pos + len > end) throw new IOException("protobuf: bad msg length " + len);
            Reader sub = new Reader(buf, pos, len);
            pos += len;
            return sub;
        }

        /** Skip a field of the given wire type. Groups are rejected rather than
         *  guessed at -- see the file header. */
        public void skip(int wire) throws IOException {
            switch (wire) {
                case WIRE_VARINT: varint(); break;
                case WIRE_I64:    fixed64(); break;
                case WIRE_I32:    fixed32(); break;
                case WIRE_LEN: {
                    int len = (int) varint();
                    if (len < 0 || pos + len > end) throw new IOException("protobuf: bad skip length " + len);
                    pos += len;
                    break;
                }
                default:
                    throw new IOException("protobuf: unsupported wire type " + wire
                        + " at offset " + pos + " (groups are not supported)");
            }
        }
    }

    /**
     * Walk a nested message tree by field number and return the LAST field on
     * the path, or null if any step is absent.
     *
     * Every step except the last is read as an embedded message; the last is
     * returned raw so the caller decides how to interpret it. This is the whole
     * reason the codec exists: the answers we want out of Google's responses are
     * one or two scalars at the bottom of a 3-4 level tree
     * (payload -> licenseV2Response -> license -> jwt), and writing a
     * hand-parser per level would be four times the code for the same answer.
     *
     * REPEATED FIELDS: returns the LAST occurrence, matching the protobuf rule
     * that a later scalar wins. If you need every occurrence, loop readTag()
     * yourself.
     */
    public static byte[] path(byte[] msg, int... fields) {
        if (msg == null || fields.length == 0) return null;
        byte[] cur = msg;
        for (int level = 0; level < fields.length; level++) {
            byte[] found = null;
            try {
                Reader r = new Reader(cur);
                int tag;
                while ((tag = r.readTag()) != -1) {
                    if (Reader.fieldOf(tag) == fields[level] && Reader.wireOf(tag) == WIRE_LEN) {
                        found = r.bytes();
                    } else {
                        r.skip(Reader.wireOf(tag));
                    }
                }
            } catch (IOException e) {
                return null;    // malformed below this point -- absent, not throwing
            }
            if (found == null) return null;
            cur = found;
        }
        return cur;
    }

    /** {@link #path} with the leaf decoded as a UTF-8 string. */
    public static String pathString(byte[] msg, int... fields) {
        byte[] b = path(msg, fields);
        return b == null ? null : new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** A scalar varint / fixed64 field directly inside `msg`, or `def`.
     *  Handles both encodings because Google's checkin response carries
     *  android_id as fixed64 in some versions and varint in others, and a reader
     *  that assumes one silently returns 0 for the other. */
    public static long scalar(byte[] msg, int field, long def) {
        if (msg == null) return def;
        long result = def;
        try {
            Reader r = new Reader(msg);
            int tag;
            while ((tag = r.readTag()) != -1) {
                int w = Reader.wireOf(tag);
                if (Reader.fieldOf(tag) == field) {
                    if (w == WIRE_VARINT)   { result = r.varint(); continue; }
                    if (w == WIRE_I64)      { result = r.fixed64(); continue; }
                    if (w == WIRE_I32)      { result = r.fixed32() & 0xFFFFFFFFL; continue; }
                }
                r.skip(w);
            }
        } catch (IOException e) {
            return result;
        }
        return result;
    }

    /** Hex dump, for logging a response we failed to parse. Bounded so a 23 MB
     *  body cannot land in a log file. */
    public static String hex(byte[] b, int max) {
        if (b == null) return "(null)";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(b.length, max);
        for (int i = 0; i < n; i++) sb.append(String.format("%02x", b[i]));
        if (b.length > n) sb.append("... (").append(b.length).append(" bytes)");
        return sb.toString();
    }
}
