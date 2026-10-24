package android.service.carrier;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * The raw PDUs of one SMS, in order, as handed to a CarrierMessagingService
 * filter.
 *
 * See the package note on CarrierIdentifier.
 *
 * Source: frameworks/base/telephony/java/android/service/carrier/MessagePdu.java
 */
public final class MessagePdu implements Parcelable {

    private final List<byte[]> mPduList;

    public MessagePdu(List<byte[]> pduList) {
        if (pduList == null || pduList.contains(null)) {
            throw new IllegalArgumentException("pduList must not be null or contain nulls");
        }
        mPduList = pduList;
    }

    public List<byte[]> getPdus() { return mPduList; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPduList.size());
        for (byte[] messagePdu : mPduList) {
            dest.writeByteArray(messagePdu);
        }
    }

    public static final Creator<MessagePdu> CREATOR = new Creator<MessagePdu>() {
        @Override public MessagePdu createFromParcel(Parcel source) {
            final int size = source.readInt();
            final List<byte[]> pduList = new ArrayList<byte[]>(size);
            for (int i = 0; i < size; i++) {
                pduList.add(source.createByteArray());
            }
            return new MessagePdu(pduList);
        }
        @Override public MessagePdu[] newArray(int size) { return new MessagePdu[size]; }
    };
}
