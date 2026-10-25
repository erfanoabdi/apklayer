package android.location;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A GNSS navigation-message frame as received from a satellite.
 *
 * A Mac has no GNSS receiver, so LocationManager never delivers one and
 * registerGnssNavigationMessageCallback reports STATUS_NOT_SUPPORTED -- the
 * value AOSP uses for a device whose chipset cannot produce navigation
 * messages, which is common on real phones too. The data holder itself is
 * faithful, including the parcel field order.
 *
 * Source: frameworks/base/location/java/android/location/GnssNavigationMessage.java
 */
public final class GnssNavigationMessage implements Parcelable {

    private static final byte[] EMPTY_ARRAY = new byte[0];

    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_GPS_L1CA = 0x0101;
    public static final int TYPE_GPS_L2CNAV = 0x0102;
    public static final int TYPE_GPS_L5CNAV = 0x0103;
    public static final int TYPE_SBS = 0x0201;
    public static final int TYPE_GPS_CNAV2 = 0x0104;
    public static final int TYPE_GLO_L1CA = 0x0301;
    public static final int TYPE_QZS_L1CA = 0x0401;
    public static final int TYPE_BDS_D1 = 0x0501;
    public static final int TYPE_BDS_D2 = 0x0502;
    public static final int TYPE_BDS_CNAV1 = 0x0503;
    public static final int TYPE_BDS_CNAV2 = 0x0504;
    public static final int TYPE_GAL_I = 0x0601;
    public static final int TYPE_GAL_F = 0x0602;
    public static final int TYPE_IRN_L5CA = 0x0701;
    public static final int TYPE_IRN_L5 = 0x0702;
    public static final int TYPE_IRN_L1 = 0x0703;

    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_PARITY_PASSED = (1 << 0);
    public static final int STATUS_PARITY_REBUILT = (1 << 1);

    private int mType;
    private int mSvid;
    private int mMessageId;
    private int mSubmessageId;
    private byte[] mData;
    private int mStatus;

    /** AOSP marks this @hide @TestApi. */
    public GnssNavigationMessage() { initialize(); }

    public void set(GnssNavigationMessage navigationMessage) {
        mType = navigationMessage.mType;
        mSvid = navigationMessage.mSvid;
        mMessageId = navigationMessage.mMessageId;
        mSubmessageId = navigationMessage.mSubmessageId;
        mData = navigationMessage.mData;
        mStatus = navigationMessage.mStatus;
    }

    public void reset() { initialize(); }

    public int getType() { return mType; }
    public void setType(int value) { mType = value; }

    public int getSvid() { return mSvid; }
    public void setSvid(int value) { mSvid = value; }

    public int getMessageId() { return mMessageId; }
    public void setMessageId(int value) { mMessageId = value; }

    public int getSubmessageId() { return mSubmessageId; }
    public void setSubmessageId(int value) { mSubmessageId = value; }

    public byte[] getData() { return mData; }
    public void setData(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Data must be a non-null array");
        }
        mData = value;
    }

    public int getStatus() { return mStatus; }
    public void setStatus(int value) { mStatus = value; }

    public static final Creator<GnssNavigationMessage> CREATOR =
            new Creator<GnssNavigationMessage>() {
        @Override public GnssNavigationMessage createFromParcel(Parcel parcel) {
            GnssNavigationMessage navigationMessage = new GnssNavigationMessage();
            navigationMessage.setType(parcel.readInt());
            navigationMessage.setSvid(parcel.readInt());
            navigationMessage.setMessageId(parcel.readInt());
            navigationMessage.setSubmessageId(parcel.readInt());
            int dataLength = parcel.readInt();
            byte[] data = new byte[dataLength];
            parcel.readByteArray(data);
            navigationMessage.setData(data);
            navigationMessage.setStatus(parcel.readInt());
            return navigationMessage;
        }
        @Override public GnssNavigationMessage[] newArray(int size) {
            return new GnssNavigationMessage[size];
        }
    };

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mType);
        parcel.writeInt(mSvid);
        parcel.writeInt(mMessageId);
        parcel.writeInt(mSubmessageId);
        parcel.writeInt(mData.length);
        parcel.writeByteArray(mData);
        parcel.writeInt(mStatus);
    }

    @Override public int describeContents() { return 0; }

    @Override public String toString() {
        return "GnssNavigationMessage[type=" + mType + " svid=" + mSvid
                + " messageId=" + mMessageId + " submessageId=" + mSubmessageId
                + " status=" + mStatus + ']';
    }

    private void initialize() {
        mType = TYPE_UNKNOWN;
        mSvid = 0;
        mMessageId = -1;
        mSubmessageId = -1;
        mData = EMPTY_ARRAY;
        mStatus = STATUS_UNKNOWN;
    }

    /** AOSP declares Callback as an abstract class apps subclass, not an interface. */
    public abstract static class Callback {
        /** @deprecated AOSP deprecated the status callback in API 30. */
        @Deprecated public static final int STATUS_NOT_SUPPORTED = 0;
        /** @deprecated see {@link #STATUS_NOT_SUPPORTED}. */
        @Deprecated public static final int STATUS_READY = 1;
        /** @deprecated see {@link #STATUS_NOT_SUPPORTED}. */
        @Deprecated public static final int STATUS_LOCATION_DISABLED = 2;

        public void onGnssNavigationMessageReceived(GnssNavigationMessage event) { }

        /** @deprecated AOSP deprecated this in API 30. */
        @Deprecated public void onStatusChanged(int status) { }
    }
}
