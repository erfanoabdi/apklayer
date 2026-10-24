// android.nfc.NdefRecord
//
// One record inside an NDEF message.
//
// FULLY REAL, AND IT HAS TO BE. Mac.apk has no NFC radio, but NDEF is a
// PUBLISHED BYTE FORMAT (NFC Forum "NDEF 1.0", section 3.2) -- parsing and
// serializing it needs no hardware at all, only the spec. Apps read NDEF
// bytes from places that have nothing to do with a tag: a saved file, a
// Bluetooth handover payload, a push message, a unit test. So the wire
// encoder/decoder below is a real implementation, not a stub, and it
// round-trips against AOSP's own byte layout.
//
// (This file previously stopped at the create* helpers, and NdefMessage's
// byte[] constructor threw "NDEF parsing not supported" while its
// toByteArray() returned an EMPTY array -- a silent wrong answer of exactly
// the shape CLAUDE.md's no-shortcuts rule exists to prevent, since nothing
// about the format required a device.)
//
// Mirrors frameworks/base/core/java/android/nfc/NdefRecord.java.
package android.nfc;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class NdefRecord implements Parcelable {

    public static final short TNF_EMPTY = 0x00;
    public static final short TNF_WELL_KNOWN = 0x01;
    public static final short TNF_MIME_MEDIA = 0x02;
    public static final short TNF_ABSOLUTE_URI = 0x03;
    public static final short TNF_EXTERNAL_TYPE = 0x04;
    public static final short TNF_UNKNOWN = 0x05;
    public static final short TNF_UNCHANGED = 0x06;
    public static final short TNF_RESERVED = 0x07;

    public static final byte[] RTD_TEXT = { 0x54 };       // "T"
    public static final byte[] RTD_URI = { 0x55 };        // "U"
    public static final byte[] RTD_SMART_POSTER = { 0x53, 0x70 }; // "Sp"
    public static final byte[] RTD_ALTERNATIVE_CARRIER = { 0x61, 0x63 }; // "ac"
    public static final byte[] RTD_HANDOVER_CARRIER = { 0x48, 0x63 };    // "Hc"
    public static final byte[] RTD_HANDOVER_REQUEST = { 0x48, 0x72 };    // "Hr"
    public static final byte[] RTD_HANDOVER_SELECT = { 0x48, 0x73 };     // "Hs"

    /** AOSP: the MIME type an Android Application Record advertises. */
    public static final String RTD_ANDROID_APP = "android.com:pkg";

    // NDEF 1.0 header bits (section 3.2). MB = message begin, ME = message
    // end, CF = chunk flag, SR = short record (1-byte payload length), IL = an
    // ID field is present, and the low 3 bits are the TNF.
    private static final byte FLAG_MB = (byte) 0x80;
    private static final byte FLAG_ME = (byte) 0x40;
    private static final byte FLAG_CF = (byte) 0x20;
    private static final byte FLAG_SR = (byte) 0x10;
    private static final byte FLAG_IL = (byte) 0x08;

    /**
     * The RTD-URI prefix abbreviation table (NFC Forum RTD-URI 1.0, table 3).
     * Index into this array is the payload's first byte. Order is normative --
     * it is what makes a record we write readable by any other NDEF reader.
     */
    private static final String[] URI_PREFIX_MAP = new String[] {
            "",                      // 0x00
            "http://www.",           // 0x01
            "https://www.",          // 0x02
            "http://",               // 0x03
            "https://",              // 0x04
            "tel:",                  // 0x05
            "mailto:",               // 0x06
            "ftp://anonymous:anonymous@", // 0x07
            "ftp://ftp.",            // 0x08
            "ftps://",               // 0x09
            "sftp://",               // 0x0A
            "smb://",                // 0x0B
            "nfs://",                // 0x0C
            "ftp://",                // 0x0D
            "dav://",                // 0x0E
            "news:",                 // 0x0F
            "telnet://",             // 0x10
            "imap:",                 // 0x11
            "rtsp://",               // 0x12
            "urn:",                  // 0x13
            "pop:",                  // 0x14
            "sip:",                  // 0x15
            "sips:",                 // 0x16
            "tftp:",                 // 0x17
            "btspp://",              // 0x18
            "btl2cap://",            // 0x19
            "btgoep://",             // 0x1A
            "tcpobex://",            // 0x1B
            "irdaobex://",           // 0x1C
            "file://",               // 0x1D
            "urn:epc:id:",           // 0x1E
            "urn:epc:tag:",          // 0x1F
            "urn:epc:pat:",          // 0x20
            "urn:epc:raw:",          // 0x21
            "urn:epc:",              // 0x22
            "urn:nfc:",              // 0x23
    };

    private final short mTnf;
    private final byte[] mType;
    private final byte[] mId;
    private final byte[] mPayload;

    public NdefRecord(short tnf, byte[] type, byte[] id, byte[] payload) {
        mTnf = tnf;
        mType = type != null ? type : new byte[0];
        mId = id != null ? id : new byte[0];
        mPayload = payload != null ? payload : new byte[0];
    }

    public short getTnf() { return mTnf; }
    public byte[] getType() { return mType; }
    public byte[] getId() { return mId; }
    public byte[] getPayload() { return mPayload; }

    /** AOSP createTextRecord: a Well-Known "T" record with a language code +
     *  UTF-8 text. Pure byte construction, faithful to the NFC Forum RTD-Text
     *  layout (status byte = language length, no UTF-16 bit). */
    public static NdefRecord createTextRecord(String languageCode, String text) {
        byte[] langBytes = (languageCode == null ? "en" : languageCode)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] textBytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + langBytes.length + textBytes.length];
        payload[0] = (byte) (langBytes.length & 0x3f);
        System.arraycopy(langBytes, 0, payload, 1, langBytes.length);
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.length, textBytes.length);
        return new NdefRecord(TNF_WELL_KNOWN, RTD_TEXT, new byte[0], payload);
    }

    /**
     * AOSP createUri(String): a Well-Known "U" record.
     *
     * <p>It ABBREVIATES using the RTD-URI prefix table (longest match wins),
     * which this now does too. The previous version always wrote prefix code
     * 0x00; that decodes correctly but produces different BYTES from AOSP for
     * the same input, so a record written here did not match one written on a
     * device -- visible to anything that compares or hashes the raw record.
     */
    public static NdefRecord createUri(String uriString) {
        if (uriString == null) {
            throw new NullPointerException("uri is null");
        }
        byte prefix = 0;
        // Start at 1: index 0 is the empty prefix, the fallback.
        for (int i = 1; i < URI_PREFIX_MAP.length; i++) {
            if (uriString.startsWith(URI_PREFIX_MAP[i])
                    && URI_PREFIX_MAP[i].length()
                            > URI_PREFIX_MAP[prefix & 0xff].length()) {
                prefix = (byte) i;
            }
        }
        byte[] uriBytes = uriString.substring(URI_PREFIX_MAP[prefix & 0xff].length())
                .getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + uriBytes.length];
        payload[0] = prefix;
        System.arraycopy(uriBytes, 0, payload, 1, uriBytes.length);
        return new NdefRecord(TNF_WELL_KNOWN, RTD_URI, new byte[0], payload);
    }

    /** AOSP createUri(Uri): normalizes the scheme, then delegates. */
    public static NdefRecord createUri(Uri uri) {
        if (uri == null) {
            throw new NullPointerException("uri is null");
        }
        return createUri(uri.toString());
    }

    public static NdefRecord createMime(String mimeType, byte[] mimeData) {
        byte[] type = (mimeType == null ? "" : mimeType).getBytes(StandardCharsets.US_ASCII);
        return new NdefRecord(TNF_MIME_MEDIA, type, new byte[0], mimeData);
    }

    public static NdefRecord createExternal(String domain, String type, byte[] data) {
        String full = (domain == null ? "" : domain.toLowerCase()) + ":"
                + (type == null ? "" : type.toLowerCase());
        return new NdefRecord(TNF_EXTERNAL_TYPE,
                full.getBytes(StandardCharsets.US_ASCII), new byte[0], data);
    }

    public static NdefRecord createApplicationRecord(String packageName) {
        return new NdefRecord(TNF_EXTERNAL_TYPE,
                "android.com:pkg".getBytes(StandardCharsets.US_ASCII), new byte[0],
                (packageName == null ? "" : packageName).getBytes(StandardCharsets.UTF_8));
    }

    public String toMimeType() {
        if (mTnf == TNF_MIME_MEDIA) return new String(mType, StandardCharsets.US_ASCII);
        return null;
    }

    /** Best-effort UTF-8/ASCII decode of the payload (parity with reading apps). */
    public String toString(Charset charset) {
        return new String(mPayload, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    /**
     * AOSP toUri(): the URI this record represents, or null when it does not
     * represent one. Handles the three forms AOSP does -- a Well-Known "U"
     * record (prefix byte + remainder), an absolute-URI record (the whole type
     * field IS the URI), and an external-type record (rendered as
     * "urn:nfc:ext:<type>").
     */
    public Uri toUri() {
        switch (mTnf) {
            case TNF_WELL_KNOWN:
                if (!Arrays.equals(mType, RTD_URI) || mPayload.length < 1) {
                    return null;
                }
                int prefixIndex = mPayload[0] & 0xff;
                if (prefixIndex >= URI_PREFIX_MAP.length) {
                    return null;
                }
                String prefix = URI_PREFIX_MAP[prefixIndex];
                byte[] rest = Arrays.copyOfRange(mPayload, 1, mPayload.length);
                return Uri.parse(prefix + new String(rest, StandardCharsets.UTF_8));
            case TNF_ABSOLUTE_URI:
                return Uri.parse(new String(mType, StandardCharsets.UTF_8));
            case TNF_EXTERNAL_TYPE:
                return Uri.parse("urn:nfc:ext:"
                        + new String(mType, StandardCharsets.US_ASCII));
            default:
                return null;
        }
    }

    // ---- The NDEF wire format. Spec: NFC Forum NDEF 1.0, section 3.2. ----

    /**
     * Parses ONE self-contained record (MB and ME both set). AOSP offers the
     * same single-record constructor; multi-record parsing lives in
     * NdefMessage, which is the unit the format actually delimits.
     */
    public NdefRecord(byte[] data) throws FormatException {
        if (data == null) {
            throw new NullPointerException("data is null");
        }
        NdefRecord[] parsed = parse(ByteBuffer.wrap(data), false);
        if (parsed.length != 1) {
            throw new FormatException("expected exactly one NDEF record, found "
                    + parsed.length);
        }
        NdefRecord r = parsed[0];
        mTnf = r.mTnf;
        mType = r.mType;
        mId = r.mId;
        mPayload = r.mPayload;
    }

    /** The serialized length of this record, so a message can size its buffer
     *  without building it twice -- AOSP exposes the same on NdefMessage. */
    int wireLength(boolean mb, boolean me) {
        boolean shortRecord = mPayload.length < 256;
        boolean hasId = mId.length > 0;
        return 3 + (shortRecord ? 0 : 3) + (hasId ? 1 : 0)
                + mType.length + mId.length + mPayload.length;
    }

    /** Appends this record's wire bytes, with the given message-position flags. */
    void writeTo(ByteArrayOutputStream out, boolean mb, boolean me) {
        boolean shortRecord = mPayload.length < 256;
        boolean hasId = mId.length > 0;
        byte flags = (byte) ((mb ? FLAG_MB : 0) | (me ? FLAG_ME : 0)
                | (shortRecord ? FLAG_SR : 0) | (hasId ? FLAG_IL : 0) | mTnf);
        out.write(flags);
        out.write(mType.length);
        if (shortRecord) {
            out.write(mPayload.length);
        } else {
            out.write((mPayload.length >>> 24) & 0xff);
            out.write((mPayload.length >>> 16) & 0xff);
            out.write((mPayload.length >>> 8) & 0xff);
            out.write(mPayload.length & 0xff);
        }
        if (hasId) {
            out.write(mId.length);
        }
        out.write(mType, 0, mType.length);
        out.write(mId, 0, mId.length);
        out.write(mPayload, 0, mPayload.length);
    }

    /** AOSP toByteArray(): this single record, MB and ME both set. */
    public byte[] toByteArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(wireLength(true, true));
        writeTo(out, true, true);
        return out.toByteArray();
    }

    /**
     * Decodes records from `buffer` until the ME flag is seen (or, when
     * `ignoreMbMe` is set, until the buffer runs out).
     *
     * <p>CHUNKING is handled the way the spec defines it and AOSP implements
     * it: a record with CF set is continued by following records whose TNF is
     * TNF_UNCHANGED and which carry no type or id; their payloads concatenate
     * onto the first chunk's. Dropping chunk support would silently mangle a
     * legal message, so it is implemented rather than rejected.
     */
    static NdefRecord[] parse(ByteBuffer buffer, boolean ignoreMbMe)
            throws FormatException {
        java.util.List<NdefRecord> records = new java.util.ArrayList<>();
        try {
            ByteArrayOutputStream chunks = null;
            short chunkTnf = -1;
            byte[] chunkType = null;
            byte[] chunkId = null;
            boolean inChunk = false;
            boolean sawMe = false;

            while (buffer.hasRemaining()) {
                byte flag = buffer.get();
                boolean mb = (flag & FLAG_MB) != 0;
                boolean me = (flag & FLAG_ME) != 0;
                boolean cf = (flag & FLAG_CF) != 0;
                boolean sr = (flag & FLAG_SR) != 0;
                boolean il = (flag & FLAG_IL) != 0;
                short tnf = (short) (flag & 0x07);

                if (!mb && records.isEmpty() && !inChunk && !ignoreMbMe) {
                    throw new FormatException("expected MB flag on the first record");
                }
                if (mb && (!records.isEmpty() || inChunk) && !ignoreMbMe) {
                    throw new FormatException("unexpected MB flag mid-message");
                }
                if (inChunk && il) {
                    throw new FormatException("unexpected ID field in a chunk");
                }
                if (cf && me) {
                    throw new FormatException("CF and ME set on the same record");
                }
                if (inChunk && tnf != TNF_UNCHANGED) {
                    throw new FormatException("expected TNF_UNCHANGED in a chunk");
                }
                if (!inChunk && tnf == TNF_UNCHANGED) {
                    throw new FormatException("unexpected TNF_UNCHANGED outside a chunk");
                }

                int typeLength = buffer.get() & 0xff;
                long payloadLength = sr ? (buffer.get() & 0xff)
                        : (buffer.getInt() & 0xffffffffL);
                int idLength = il ? (buffer.get() & 0xff) : 0;

                if (inChunk && typeLength != 0) {
                    throw new FormatException("unexpected type field in a chunk");
                }
                if (payloadLength > buffer.remaining()) {
                    throw new FormatException("payload length " + payloadLength
                            + " exceeds the " + buffer.remaining() + " bytes available");
                }

                byte[] type = new byte[typeLength];
                buffer.get(type);
                byte[] id = new byte[idLength];
                buffer.get(id);
                byte[] payload = new byte[(int) payloadLength];
                buffer.get(payload);

                if (cf && !inChunk) {
                    // First chunk: remember the identity, start accumulating.
                    chunks = new ByteArrayOutputStream();
                    chunkTnf = tnf;
                    chunkType = type;
                    chunkId = id;
                    inChunk = true;
                }
                if (cf || inChunk) {
                    chunks.write(payload, 0, payload.length);
                }
                if (!cf && inChunk) {
                    // Last chunk: emit the reassembled record.
                    payload = chunks.toByteArray();
                    tnf = chunkTnf;
                    type = chunkType;
                    id = chunkId;
                    inChunk = false;
                    chunks = null;
                }
                if (!cf) {
                    validateTnf(tnf, type, id, payload);
                    records.add(new NdefRecord(tnf, type, id, payload));
                }
                if (me && !ignoreMbMe) {
                    sawMe = true;
                    break;
                }
            }
            if (inChunk) {
                throw new FormatException("truncated: a chunked record was never terminated");
            }
            if (!sawMe && !ignoreMbMe) {
                throw new FormatException("truncated: no record carried the ME flag");
            }
        } catch (java.nio.BufferUnderflowException e) {
            throw new FormatException("truncated NDEF data", e);
        }
        return records.toArray(new NdefRecord[0]);
    }

    /** AOSP's per-TNF field constraints (NdefRecord.validateTnf). */
    private static void validateTnf(short tnf, byte[] type, byte[] id, byte[] payload)
            throws FormatException {
        switch (tnf) {
            case TNF_EMPTY:
                if (type.length != 0 || id.length != 0 || payload.length != 0) {
                    throw new FormatException("TNF_EMPTY record must have no type, id or payload");
                }
                return;
            case TNF_WELL_KNOWN:
            case TNF_MIME_MEDIA:
            case TNF_ABSOLUTE_URI:
            case TNF_EXTERNAL_TYPE:
            case TNF_UNKNOWN:
            case TNF_RESERVED:
                return;
            case TNF_UNCHANGED:
                throw new FormatException("TNF_UNCHANGED is only valid inside a chunk");
            default:
                throw new FormatException("unexpected TNF " + tnf);
        }
    }

    // ---- Value semantics + Parcelable. ----

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NdefRecord)) return false;
        NdefRecord other = (NdefRecord) obj;
        return mTnf == other.mTnf
                && Arrays.equals(mType, other.mType)
                && Arrays.equals(mId, other.mId)
                && Arrays.equals(mPayload, other.mPayload);
    }

    @Override public int hashCode() {
        int result = 31 + mTnf;
        result = 31 * result + Arrays.hashCode(mType);
        result = 31 * result + Arrays.hashCode(mId);
        result = 31 * result + Arrays.hashCode(mPayload);
        return result;
    }

    @Override public String toString() {
        StringBuilder b = new StringBuilder("NdefRecord tnf=").append(mTnf);
        if (mType.length > 0) {
            b.append(" type=").append(hex(mType));
        }
        if (mId.length > 0) {
            b.append(" id=").append(hex(mId));
        }
        if (mPayload.length > 0) {
            b.append(" payload=").append(hex(mPayload));
        }
        return b.toString();
    }

    private static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) {
            b.append(Character.forDigit((x >> 4) & 0x0f, 16));
            b.append(Character.forDigit(x & 0x0f, 16));
        }
        return b.toString();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTnf);
        dest.writeInt(mType.length);
        dest.writeByteArray(mType);
        dest.writeInt(mId.length);
        dest.writeByteArray(mId);
        dest.writeInt(mPayload.length);
        dest.writeByteArray(mPayload);
    }

    public static final Creator<NdefRecord> CREATOR = new Creator<NdefRecord>() {
        @Override public NdefRecord createFromParcel(Parcel in) {
            short tnf = (short) in.readInt();
            in.readInt();
            byte[] type = in.createByteArray();
            in.readInt();
            byte[] id = in.createByteArray();
            in.readInt();
            byte[] payload = in.createByteArray();
            return new NdefRecord(tnf, type, id, payload);
        }

        @Override public NdefRecord[] newArray(int size) {
            return new NdefRecord[size];
        }
    };
}
