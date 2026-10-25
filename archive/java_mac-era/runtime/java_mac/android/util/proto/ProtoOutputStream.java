// android.util.proto.ProtoOutputStream -- a REAL protobuf wire-format writer.
//
// AOSP splits this across ProtoStream (the constants + the fieldId/token bit
// packing) and ProtoOutputStream (the encoder). ProtoStream is @hide, and
// metalava therefore flattens its public statics INTO ProtoOutputStream in
// core/api/current.txt -- so the public API really is one class, and that is
// what we declare here.
//   AOSP: frameworks/base/core/java/android/util/proto/ProtoStream.java
//         frameworks/base/core/java/android/util/proto/ProtoOutputStream.java
//         (android16-release)
//
// This is NOT a stub. An app that serialises a proto through this class and
// hands the bytes to a real parser must get bytes a real parser accepts, so
// every encoding rule below is transcribed from AOSP:
//   * a SINGLE-count field whose value equals the proto default (0 / false /
//     empty) writes NOTHING; a REPEATED/PACKED one always writes.
//   * int32 and enum sign-extend to 64 bits before varint encoding, so a
//     negative int32 occupies ten bytes (this is protobuf's own rule, not an
//     Android quirk, and getting it wrong produces a value a real parser
//     decodes as a huge positive number).
//   * sint32/sint64 zig-zag; fixed*/sfixed* are little-endian.
//
// ONE INTERNAL DIVERGENCE, deliberate and invisible on the wire: AOSP writes
// nested messages into a single flat EncodedBuffer, reserving a fixed-width
// slot for each length and then COMPACTING the buffer at the end to shrink
// those slots. We push a child buffer per nested message and splice it into
// its parent at end(), so lengths are exact the first time and no compaction
// pass is needed. The emitted bytes are identical -- protobuf length prefixes
// are minimal varints either way -- and every public observable (getBytes,
// getRawSize, the token bits) is preserved. The token's "offset" field still
// carries the parent offset at which the object's header begins, which is what
// AOSP puts there and what token2String prints.
package android.util.proto;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public final class ProtoOutputStream {

    public static final String TAG = "ProtoOutputStream";

    // ---------------------------------------------------------------- wire types
    public static final int WIRE_TYPE_VARINT = 0;
    public static final int WIRE_TYPE_FIXED64 = 1;
    public static final int WIRE_TYPE_LENGTH_DELIMITED = 2;
    public static final int WIRE_TYPE_START_GROUP = 3;
    public static final int WIRE_TYPE_END_GROUP = 4;
    public static final int WIRE_TYPE_FIXED32 = 5;
    public static final int WIRE_TYPE_MASK = 0x07;

    /** Bits 0..2 of a tag are the wire type; the field number starts at bit 3. */
    public static final int FIELD_ID_SHIFT = 3;

    // ---------------------------------------------------------------- field types
    public static final int FIELD_TYPE_SHIFT = 32;
    public static final long FIELD_TYPE_MASK = 0x0ffL << FIELD_TYPE_SHIFT;

    public static final long FIELD_TYPE_UNKNOWN = 0;
    public static final long FIELD_TYPE_DOUBLE   = 1L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_FLOAT    = 2L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_INT64    = 3L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_UINT64   = 4L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_INT32    = 5L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_FIXED64  = 6L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_FIXED32  = 7L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_BOOL     = 8L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_STRING   = 9L << FIELD_TYPE_SHIFT;
    // 10 is GROUP: deprecated in protobuf, reserved here so the index math and
    // the FIELD_TYPE_NAMES table stay aligned with AOSP's.
    public static final long FIELD_TYPE_MESSAGE  = 11L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_BYTES    = 12L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_UINT32   = 13L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_ENUM     = 14L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_SFIXED32 = 15L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_SFIXED64 = 16L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_SINT32   = 17L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_SINT64   = 18L << FIELD_TYPE_SHIFT;

    private static final String[] FIELD_TYPE_NAMES = new String[]{
            "Double", "Float", "Int64", "UInt64", "Int32", "Fixed64", "Fixed32",
            "Bool", "String", "Group", "Message", "Bytes", "UInt32", "Enum",
            "SFixed32", "SFixed64", "SInt32", "SInt64",
    };

    // ---------------------------------------------------------------- field counts
    public static final int FIELD_COUNT_SHIFT = 40;
    public static final long FIELD_COUNT_MASK = 0x0fL << FIELD_COUNT_SHIFT;
    public static final long FIELD_COUNT_UNKNOWN  = 0;
    public static final long FIELD_COUNT_SINGLE   = 1L << FIELD_COUNT_SHIFT;
    public static final long FIELD_COUNT_REPEATED = 2L << FIELD_COUNT_SHIFT;
    public static final long FIELD_COUNT_PACKED   = 5L << FIELD_COUNT_SHIFT;

    // ---------------------------------------------------------------- state
    /** Buffer stack: index 0 is the root message, each start() pushes a child. */
    private final ArrayList<ByteArrayOutputStream> mStack = new ArrayList<>();
    /** Parallel to mStack (minus the root): the field id each open object belongs to. */
    private final ArrayList<Integer> mOpenIds = new ArrayList<>();
    /** Parallel to mStack (minus the root): the token handed out by start(). */
    private final ArrayList<Long> mOpenTokens = new ArrayList<>();

    private final OutputStream mStream;
    private int mDepth;
    /** AOSP counts DOWN from -1 so an id is unlikely to alias a real size. */
    private int mNextObjectId = -1;
    private boolean mCompacted;
    private byte[] mCompactedBytes;

    public ProtoOutputStream() {
        this((OutputStream) null);
    }

    /**
     * AOSP's int constructor names an internal chunk size. Our buffers grow on
     * demand, so the value is a capacity HINT only -- and honouring it as a
     * hint is closer to AOSP than ignoring it, because a caller that passes a
     * large size is telling us it expects a large message.
     */
    public ProtoOutputStream(int chunkSize) {
        mStream = null;
        mStack.add(new ByteArrayOutputStream(chunkSize > 0 ? chunkSize : 32));
    }

    public ProtoOutputStream(OutputStream stream) {
        mStream = stream;
        mStack.add(new ByteArrayOutputStream());
    }

    /** @hide AOSP convenience ctor. */
    public ProtoOutputStream(FileDescriptor fd) {
        this(new FileOutputStream(fd));
    }

    // ---------------------------------------------------------------- fieldId helpers

    public static long makeFieldId(int id, long fieldFlags) {
        return fieldFlags | (((long) id) & 0x0ffffffffL);
    }

    public static int checkFieldId(long fieldId, long expectedFlags) {
        final long fieldCount = fieldId & FIELD_COUNT_MASK;
        final long fieldType = fieldId & FIELD_TYPE_MASK;
        final long expectedCount = expectedFlags & FIELD_COUNT_MASK;
        final long expectedType = expectedFlags & FIELD_TYPE_MASK;
        if (((int) fieldId) == 0) {
            throw new IllegalArgumentException("Invalid proto field " + (int) fieldId
                    + " fieldId=" + Long.toHexString(fieldId));
        }
        if (fieldType != expectedType
                || !((fieldCount == expectedCount)
                    || (fieldCount == FIELD_COUNT_PACKED
                        && expectedCount == FIELD_COUNT_REPEATED))) {
            final String countString = getFieldCountString(fieldCount);
            final String typeString = getFieldTypeString(fieldType);
            final StringBuilder sb = new StringBuilder();
            if (typeString != null && countString != null) {
                sb.append(expectedType == FIELD_TYPE_MESSAGE ? "start" : "write");
                sb.append(getFieldCountString(expectedCount));
                sb.append(getFieldTypeString(expectedType));
                sb.append(" called for field ");
                sb.append((int) fieldId);
                sb.append(" which should be used with ");
                sb.append(fieldType == FIELD_TYPE_MESSAGE ? "start" : "write");
                sb.append(countString);
                sb.append(typeString);
                if (fieldCount == FIELD_COUNT_PACKED) {
                    sb.append(" or writeRepeated");
                    sb.append(typeString);
                }
                sb.append('.');
            } else {
                sb.append(expectedType == FIELD_TYPE_MESSAGE ? "start" : "write");
                sb.append(getFieldCountString(expectedCount));
                sb.append(getFieldTypeString(expectedType));
                sb.append(" called with an invalid fieldId: 0x");
                sb.append(Long.toHexString(fieldId));
                sb.append(". The proto field ID might be ");
                sb.append((int) fieldId);
                sb.append('.');
            }
            throw new IllegalArgumentException(sb.toString());
        }
        return (int) fieldId;
    }

    public static String getFieldTypeString(long fieldType) {
        int index = ((int) ((fieldType & FIELD_TYPE_MASK) >>> FIELD_TYPE_SHIFT)) - 1;
        if (index >= 0 && index < FIELD_TYPE_NAMES.length) {
            return FIELD_TYPE_NAMES[index];
        }
        return null;
    }

    public static String getFieldCountString(long fieldCount) {
        if (fieldCount == FIELD_COUNT_SINGLE) {
            return "";
        } else if (fieldCount == FIELD_COUNT_REPEATED) {
            return "Repeated";
        } else if (fieldCount == FIELD_COUNT_PACKED) {
            return "Packed";
        }
        return null;
    }

    public static String getWireTypeString(int wireType) {
        switch (wireType) {
            case WIRE_TYPE_VARINT:            return "Varint";
            case WIRE_TYPE_FIXED64:           return "Fixed64";
            case WIRE_TYPE_LENGTH_DELIMITED:  return "Length Delimited";
            case WIRE_TYPE_START_GROUP:       return "Start Group";
            case WIRE_TYPE_END_GROUP:         return "End Group";
            case WIRE_TYPE_FIXED32:           return "Fixed32";
            default:                          return null;
        }
    }

    public static String getFieldIdString(long fieldId) {
        final long fieldCount = fieldId & FIELD_COUNT_MASK;
        String countString = getFieldCountString(fieldCount);
        if (countString == null) {
            countString = "fieldCount=" + fieldCount;
        }
        if (countString.length() > 0) {
            countString += " ";
        }
        final long fieldType = fieldId & FIELD_TYPE_MASK;
        String typeString = getFieldTypeString(fieldType);
        if (typeString == null) {
            typeString = "fieldType=" + fieldType;
        }
        return countString + typeString + " tag=" + ((int) fieldId)
                + " fieldId=0x" + Long.toHexString(fieldId);
    }

    // ---------------------------------------------------------------- tokens

    /**
     * Bits 61-63 tag size, bit 60 repeated, bits 51-59 depth,
     * bits 32-50 objectId, bits 0-31 offset. AOSP's exact layout.
     */
    public static long makeToken(int tagSize, boolean repeated, int depth, int objectId,
            int offset) {
        return ((0x07L & (long) tagSize) << 61)
                | (repeated ? (1L << 60) : 0)
                | (0x01ffL & (long) depth) << 51
                | (0x07ffffL & (long) objectId) << 32
                | (0x0ffffffffL & (long) offset);
    }

    /** @hide */
    public static int getTagSizeFromToken(long token) { return (int) (0x7 & (token >> 61)); }
    /** @hide */
    public static boolean getRepeatedFromToken(long token) { return (0x1 & (token >> 60)) != 0; }
    /** @hide */
    public static int getDepthFromToken(long token) { return (int) (0x01ff & (token >> 51)); }
    /** @hide */
    public static int getObjectIdFromToken(long token) { return (int) (0x07ffff & (token >> 32)); }
    /** @hide */
    public static int getOffsetFromToken(long token) { return (int) token; }
    /** @hide */
    public static int convertObjectIdToOrdinal(int objectId) { return (-1 & 0x07ffff) - objectId; }

    public static String token2String(long token) {
        if (token == 0L) {
            return "Token(0)";
        }
        return "Token(val=0x" + Long.toHexString(token)
                + " depth=" + getDepthFromToken(token)
                + " object=" + convertObjectIdToOrdinal(getObjectIdFromToken(token))
                + " tagSize=" + getTagSizeFromToken(token)
                + " offset=" + getOffsetFromToken(token)
                + ')';
    }

    // ---------------------------------------------------------------- nesting

    public long start(long fieldId) {
        assertNotCompacted();
        final long count = fieldId & FIELD_COUNT_MASK;
        final boolean repeated = (count == FIELD_COUNT_REPEATED);
        checkFieldId(fieldId, FIELD_TYPE_MESSAGE | (repeated ? FIELD_COUNT_REPEATED
                                                             : FIELD_COUNT_SINGLE));
        final int id = (int) fieldId;
        final int offset = current().size();
        final long token = makeToken(getTagSize(id), repeated, mDepth, mNextObjectId, offset);

        mDepth++;
        mNextObjectId--;
        mOpenIds.add(id);
        mOpenTokens.add(token);
        mStack.add(new ByteArrayOutputStream());
        return token;
    }

    public void end(long token) {
        if (mOpenTokens.isEmpty()) {
            throw new IllegalArgumentException("end() called with no matching start()."
                    + " token=" + token2String(token));
        }
        final long expected = mOpenTokens.get(mOpenTokens.size() - 1);
        if (token != expected) {
            throw new IllegalArgumentException("end() called with unexpected token."
                    + " token=" + token2String(token)
                    + " expectedToken=" + token2String(expected));
        }
        final ByteArrayOutputStream child = mStack.remove(mStack.size() - 1);
        final int id = mOpenIds.remove(mOpenIds.size() - 1);
        mOpenTokens.remove(mOpenTokens.size() - 1);
        mDepth--;

        final byte[] body = child.toByteArray();
        // AOSP emits the header even for an empty SINGLE message (an empty
        // sub-message is meaningful in protobuf -- it distinguishes "present
        // and empty" from "absent"), so this is unconditional.
        writeKnownLengthHeader(id, body.length);
        writeRawBuffer(body);
    }

    // ---------------------------------------------------------------- output

    public byte[] getBytes() {
        if (mCompacted) {
            return mCompactedBytes;
        }
        if (mDepth != 0) {
            // AOSP's compaction cannot run with objects open either; matching
            // its behaviour (return what the root holds) rather than throwing.
            return mStack.get(0).toByteArray();
        }
        mCompactedBytes = mStack.get(0).toByteArray();
        mCompacted = true;
        return mCompactedBytes;
    }

    /**
     * The size of the data written so far, after full protobuf encoding.
     * With objects still open, that includes each open object's own header
     * (tag + length varint) plus its body as written so far -- exact, not an
     * estimate, for the bytes that exist at this instant.
     */
    public int getRawSize() {
        if (mCompacted) {
            return mCompactedBytes.length;
        }
        int total = 0;
        for (int i = 0; i < mStack.size(); i++) {
            final int body = mStack.get(i).size();
            total += body;
            if (i > 0) {
                total += getTagSize(mOpenIds.get(i - 1)) + getRawVarint32Size(body);
            }
        }
        return total;
    }

    public void flush() {
        if (mStream == null) {
            return;
        }
        if (mDepth != 0) {
            // AOSP: cannot compact with objects open, so it writes nothing.
            return;
        }
        if (mCompacted) {
            return;
        }
        final byte[] data = getBytes();
        try {
            mStream.write(data);
            mStream.flush();
        } catch (IOException ex) {
            throw new RuntimeException("Error flushing proto to stream", ex);
        }
    }

    public void dump(String tag) {
        Log.d(tag, "ProtoOutputStream( depth=" + mDepth
                + " rawSize=" + getRawSize()
                + " compacted=" + mCompacted + " )");
    }

    // ---------------------------------------------------------------- write()

    public void write(long fieldId, double val) {
        assertNotCompacted();
        final int id = (int) fieldId;
        final boolean single = isSingle(fieldId);
        switch (typeOf(fieldId)) {
            case (int) (FIELD_TYPE_DOUBLE   >> FIELD_TYPE_SHIFT): writeDouble(id, val, single); break;
            case (int) (FIELD_TYPE_FLOAT    >> FIELD_TYPE_SHIFT): writeFloat(id, (float) val, single); break;
            case (int) (FIELD_TYPE_INT32    >> FIELD_TYPE_SHIFT): writeInt32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_INT64    >> FIELD_TYPE_SHIFT): writeInt64(id, (long) val, single); break;
            case (int) (FIELD_TYPE_UINT32   >> FIELD_TYPE_SHIFT): writeUInt32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_UINT64   >> FIELD_TYPE_SHIFT): writeUInt64(id, (long) val, single); break;
            case (int) (FIELD_TYPE_SINT32   >> FIELD_TYPE_SHIFT): writeSInt32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_SINT64   >> FIELD_TYPE_SHIFT): writeSInt64(id, (long) val, single); break;
            case (int) (FIELD_TYPE_FIXED32  >> FIELD_TYPE_SHIFT): writeFixed32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_FIXED64  >> FIELD_TYPE_SHIFT): writeFixed64(id, (long) val, single); break;
            case (int) (FIELD_TYPE_SFIXED32 >> FIELD_TYPE_SHIFT): writeFixed32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_SFIXED64 >> FIELD_TYPE_SHIFT): writeFixed64(id, (long) val, single); break;
            case (int) (FIELD_TYPE_BOOL     >> FIELD_TYPE_SHIFT): writeBool(id, val != 0, single); break;
            case (int) (FIELD_TYPE_ENUM     >> FIELD_TYPE_SHIFT): writeInt32(id, (int) val, single); break;
            default:
                throw new IllegalArgumentException("Attempt to call write(long, double) with "
                        + getFieldIdString(fieldId));
        }
    }

    public void write(long fieldId, float val)   { write(fieldId, (double) val); }

    public void write(long fieldId, int val) {
        assertNotCompacted();
        final int id = (int) fieldId;
        final boolean single = isSingle(fieldId);
        switch (typeOf(fieldId)) {
            case (int) (FIELD_TYPE_DOUBLE   >> FIELD_TYPE_SHIFT): writeDouble(id, val, single); break;
            case (int) (FIELD_TYPE_FLOAT    >> FIELD_TYPE_SHIFT): writeFloat(id, val, single); break;
            case (int) (FIELD_TYPE_INT32    >> FIELD_TYPE_SHIFT): writeInt32(id, val, single); break;
            case (int) (FIELD_TYPE_INT64    >> FIELD_TYPE_SHIFT): writeInt64(id, val, single); break;
            case (int) (FIELD_TYPE_UINT32   >> FIELD_TYPE_SHIFT): writeUInt32(id, val, single); break;
            case (int) (FIELD_TYPE_UINT64   >> FIELD_TYPE_SHIFT): writeUInt64(id, val, single); break;
            case (int) (FIELD_TYPE_SINT32   >> FIELD_TYPE_SHIFT): writeSInt32(id, val, single); break;
            case (int) (FIELD_TYPE_SINT64   >> FIELD_TYPE_SHIFT): writeSInt64(id, val, single); break;
            case (int) (FIELD_TYPE_FIXED32  >> FIELD_TYPE_SHIFT): writeFixed32(id, val, single); break;
            case (int) (FIELD_TYPE_FIXED64  >> FIELD_TYPE_SHIFT): writeFixed64(id, val, single); break;
            case (int) (FIELD_TYPE_SFIXED32 >> FIELD_TYPE_SHIFT): writeFixed32(id, val, single); break;
            case (int) (FIELD_TYPE_SFIXED64 >> FIELD_TYPE_SHIFT): writeFixed64(id, val, single); break;
            case (int) (FIELD_TYPE_BOOL     >> FIELD_TYPE_SHIFT): writeBool(id, val != 0, single); break;
            case (int) (FIELD_TYPE_ENUM     >> FIELD_TYPE_SHIFT): writeInt32(id, val, single); break;
            default:
                throw new IllegalArgumentException("Attempt to call write(long, int) with "
                        + getFieldIdString(fieldId));
        }
    }

    public void write(long fieldId, long val) {
        assertNotCompacted();
        final int id = (int) fieldId;
        final boolean single = isSingle(fieldId);
        switch (typeOf(fieldId)) {
            case (int) (FIELD_TYPE_DOUBLE   >> FIELD_TYPE_SHIFT): writeDouble(id, val, single); break;
            case (int) (FIELD_TYPE_FLOAT    >> FIELD_TYPE_SHIFT): writeFloat(id, val, single); break;
            case (int) (FIELD_TYPE_INT32    >> FIELD_TYPE_SHIFT): writeInt32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_INT64    >> FIELD_TYPE_SHIFT): writeInt64(id, val, single); break;
            case (int) (FIELD_TYPE_UINT32   >> FIELD_TYPE_SHIFT): writeUInt32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_UINT64   >> FIELD_TYPE_SHIFT): writeUInt64(id, val, single); break;
            case (int) (FIELD_TYPE_SINT32   >> FIELD_TYPE_SHIFT): writeSInt32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_SINT64   >> FIELD_TYPE_SHIFT): writeSInt64(id, val, single); break;
            case (int) (FIELD_TYPE_FIXED32  >> FIELD_TYPE_SHIFT): writeFixed32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_FIXED64  >> FIELD_TYPE_SHIFT): writeFixed64(id, val, single); break;
            case (int) (FIELD_TYPE_SFIXED32 >> FIELD_TYPE_SHIFT): writeFixed32(id, (int) val, single); break;
            case (int) (FIELD_TYPE_SFIXED64 >> FIELD_TYPE_SHIFT): writeFixed64(id, val, single); break;
            case (int) (FIELD_TYPE_BOOL     >> FIELD_TYPE_SHIFT): writeBool(id, val != 0, single); break;
            case (int) (FIELD_TYPE_ENUM     >> FIELD_TYPE_SHIFT): writeInt32(id, (int) val, single); break;
            default:
                throw new IllegalArgumentException("Attempt to call write(long, long) with "
                        + getFieldIdString(fieldId));
        }
    }

    public void write(long fieldId, boolean val) {
        assertNotCompacted();
        final int id = (int) fieldId;
        final boolean single = isSingle(fieldId);
        switch (typeOf(fieldId)) {
            case (int) (FIELD_TYPE_BOOL >> FIELD_TYPE_SHIFT): writeBool(id, val, single); break;
            default:
                throw new IllegalArgumentException("Attempt to call write(long, boolean) with "
                        + getFieldIdString(fieldId));
        }
    }

    public void write(long fieldId, String val) {
        assertNotCompacted();
        final int id = (int) fieldId;
        final boolean single = isSingle(fieldId);
        switch (typeOf(fieldId)) {
            case (int) (FIELD_TYPE_STRING >> FIELD_TYPE_SHIFT): writeString(id, val, single); break;
            default:
                throw new IllegalArgumentException("Attempt to call write(long, String) with "
                        + getFieldIdString(fieldId));
        }
    }

    public void write(long fieldId, byte[] val) {
        assertNotCompacted();
        final int id = (int) fieldId;
        final boolean single = isSingle(fieldId);
        switch (typeOf(fieldId)) {
            case (int) (FIELD_TYPE_BYTES >> FIELD_TYPE_SHIFT):
            // AOSP also accepts a MESSAGE field here: the byte[] is an
            // already-encoded sub-message body, which is length-delimited on
            // the wire exactly like bytes.
            case (int) (FIELD_TYPE_MESSAGE >> FIELD_TYPE_SHIFT):
                writeBytes(id, val, single);
                break;
            default:
                throw new IllegalArgumentException("Attempt to call write(long, byte[]) with "
                        + getFieldIdString(fieldId));
        }
    }

    public void writeTag(int id, int wireType) {
        writeRawVarint32((id << FIELD_ID_SHIFT) | wireType);
    }

    // ---------------------------------------------------------------- encoders

    private static int typeOf(long fieldId) {
        return (int) ((fieldId & FIELD_TYPE_MASK) >>> FIELD_TYPE_SHIFT);
    }

    /** SINGLE-count fields elide default values; REPEATED/PACKED never do. */
    private static boolean isSingle(long fieldId) {
        final long count = fieldId & FIELD_COUNT_MASK;
        return count != FIELD_COUNT_REPEATED && count != FIELD_COUNT_PACKED;
    }

    private void writeDouble(int id, double val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_FIXED64);
        writeRawFixed64(Double.doubleToLongBits(val));
    }

    private void writeFloat(int id, float val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_FIXED32);
        writeRawFixed32(Float.floatToIntBits(val));
    }

    private void writeInt32(int id, int val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_VARINT);
        // protobuf sign-extends int32 to 64 bits, so a negative value is ten
        // bytes on the wire. Writing it as a 32-bit varint would decode as a
        // large positive number in any real parser.
        writeRawVarint64((long) val);
    }

    private void writeInt64(int id, long val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_VARINT);
        writeRawVarint64(val);
    }

    private void writeUInt32(int id, int val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_VARINT);
        writeRawVarint32(val);
    }

    private void writeUInt64(int id, long val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_VARINT);
        writeRawVarint64(val);
    }

    private void writeSInt32(int id, int val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_VARINT);
        writeRawVarint32((val << 1) ^ (val >> 31));
    }

    private void writeSInt64(int id, long val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_VARINT);
        writeRawVarint64((val << 1) ^ (val >> 63));
    }

    private void writeFixed32(int id, int val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_FIXED32);
        writeRawFixed32(val);
    }

    private void writeFixed64(int id, long val, boolean single) {
        if (single && val == 0) return;
        writeTag(id, WIRE_TYPE_FIXED64);
        writeRawFixed64(val);
    }

    private void writeBool(int id, boolean val, boolean single) {
        if (single && !val) return;
        writeTag(id, WIRE_TYPE_VARINT);
        writeRawByte((byte) (val ? 1 : 0));
    }

    private void writeString(int id, String val, boolean single) {
        if (val == null) return;
        if (single && val.length() == 0) return;
        final byte[] utf8 = val.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeKnownLengthHeader(id, utf8.length);
        writeRawBuffer(utf8);
    }

    private void writeBytes(int id, byte[] val, boolean single) {
        if (val == null) return;
        if (single && val.length == 0) return;
        writeKnownLengthHeader(id, val.length);
        writeRawBuffer(val);
    }

    private void writeKnownLengthHeader(int id, int size) {
        writeTag(id, WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarint32(size);
    }

    // ---------------------------------------------------------------- raw buffer

    private ByteArrayOutputStream current() {
        return mStack.get(mStack.size() - 1);
    }

    private void writeRawByte(byte b) { current().write(b); }

    private void writeRawBuffer(byte[] b) { current().write(b, 0, b.length); }

    private void writeRawVarint32(int val) {
        final ByteArrayOutputStream buf = current();
        while (true) {
            if ((val & ~0x7F) == 0) {
                buf.write(val);
                return;
            }
            buf.write((val & 0x7F) | 0x80);
            val >>>= 7;
        }
    }

    private void writeRawVarint64(long val) {
        final ByteArrayOutputStream buf = current();
        while (true) {
            if ((val & ~0x7FL) == 0) {
                buf.write((int) val);
                return;
            }
            buf.write(((int) val & 0x7F) | 0x80);
            val >>>= 7;
        }
    }

    private void writeRawFixed32(int val) {
        final ByteArrayOutputStream buf = current();
        buf.write(val);
        buf.write(val >> 8);
        buf.write(val >> 16);
        buf.write(val >> 24);
    }

    private void writeRawFixed64(long val) {
        final ByteArrayOutputStream buf = current();
        for (int i = 0; i < 8; i++) {
            buf.write((int) (val >> (8 * i)));
        }
    }

    private static int getRawVarint32Size(int val) {
        if ((val & (0xffffffff << 7)) == 0) return 1;
        if ((val & (0xffffffff << 14)) == 0) return 2;
        if ((val & (0xffffffff << 21)) == 0) return 3;
        if ((val & (0xffffffff << 28)) == 0) return 4;
        return 5;
    }

    private static int getTagSize(int id) {
        return getRawVarint32Size(id << FIELD_ID_SHIFT);
    }

    private void assertNotCompacted() {
        if (mCompacted) {
            throw new IllegalArgumentException("write called after getBytes()");
        }
    }
}
