// android.nfc.NdefMessage
//
// An ordered list of NDEF records, plus the message-level wire format.
//
// FULLY REAL, for the same reason as NdefRecord (read that file's header):
// NDEF is a published byte format, so parsing and serializing it needs no NFC
// radio. This file used to throw "NDEF parsing not supported" from its byte[]
// constructor and return an EMPTY array from toByteArray() -- a silent wrong
// answer that no missing hardware justified.
//
// Mirrors frameworks/base/core/java/android/nfc/NdefMessage.java.
package android.nfc;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class NdefMessage implements Parcelable {

    private final NdefRecord[] mRecords;

    public NdefMessage(NdefRecord record, NdefRecord... records) {
        if (record == null) throw new NullPointerException("record cannot be null");
        mRecords = new NdefRecord[1 + (records == null ? 0 : records.length)];
        mRecords[0] = record;
        if (records != null) {
            for (int i = 0; i < records.length; i++) {
                if (records[i] == null) {
                    throw new NullPointerException("record cannot be null");
                }
            }
            System.arraycopy(records, 0, mRecords, 1, records.length);
        }
    }

    public NdefMessage(NdefRecord[] records) {
        if (records == null || records.length < 1) {
            throw new IllegalArgumentException("must have at least one record");
        }
        for (NdefRecord r : records) {
            if (r == null) {
                throw new NullPointerException("record cannot be null");
            }
        }
        mRecords = records.clone();
    }

    /**
     * AOSP: parses a raw NDEF binary. Real -- the format is NFC Forum
     * "NDEF 1.0" section 3.2 and the decoder lives in NdefRecord.parse.
     *
     * @throws FormatException if the data is not a well-formed NDEF message.
     */
    public NdefMessage(byte[] data) throws FormatException {
        if (data == null) {
            throw new NullPointerException("data is null");
        }
        mRecords = NdefRecord.parse(ByteBuffer.wrap(data), false);
        if (mRecords.length < 1) {
            throw new FormatException("no records in the NDEF message");
        }
    }

    public NdefRecord[] getRecords() { return mRecords; }

    /**
     * AOSP: the length toByteArray() will produce. It was previously the sum
     * of the payload and type lengths, which omits every header byte and so
     * disagreed with the serializer -- a caller sizing a buffer from it would
     * overflow.
     */
    public int getByteArrayLength() {
        int len = 0;
        for (int i = 0; i < mRecords.length; i++) {
            len += mRecords[i].wireLength(i == 0, i == mRecords.length - 1);
        }
        return len;
    }

    /** AOSP: the full message, with MB on the first record and ME on the last. */
    public byte[] toByteArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(getByteArrayLength());
        for (int i = 0; i < mRecords.length; i++) {
            mRecords[i].writeTo(out, i == 0, i == mRecords.length - 1);
        }
        return out.toByteArray();
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NdefMessage)) return false;
        return Arrays.equals(mRecords, ((NdefMessage) obj).mRecords);
    }

    @Override public int hashCode() {
        return Arrays.hashCode(mRecords);
    }

    @Override public String toString() {
        return "NdefMessage " + Arrays.toString(mRecords);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRecords.length);
        dest.writeTypedArray(mRecords, flags);
    }

    public static final Creator<NdefMessage> CREATOR = new Creator<NdefMessage>() {
        @Override public NdefMessage createFromParcel(Parcel in) {
            int count = in.readInt();
            NdefRecord[] records = new NdefRecord[count];
            in.readTypedArray(records, NdefRecord.CREATOR);
            return new NdefMessage(records);
        }

        @Override public NdefMessage[] newArray(int size) {
            return new NdefMessage[size];
        }
    };
}
