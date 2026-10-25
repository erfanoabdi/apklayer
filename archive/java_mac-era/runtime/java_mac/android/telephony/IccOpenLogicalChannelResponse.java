package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Result of opening a logical channel to the SIM.
 *
 * There is no SIM on a Mac, so TelephonyManager.iccOpenLogicalChannel() reports
 * STATUS_MISSING_RESOURCE with INVALID_CHANNEL -- AOSP's own answer when the
 * UICC is not present. That is an absence the caller already handles; a
 * fabricated open channel would fail at the first APDU instead.
 *
 * Source: frameworks/base/telephony/java/android/telephony/IccOpenLogicalChannelResponse.java
 */
public class IccOpenLogicalChannelResponse implements Parcelable {

    public static final int INVALID_CHANNEL = -1;

    public static final int STATUS_NO_ERROR = 1;
    public static final int STATUS_MISSING_RESOURCE = 2;
    public static final int STATUS_NO_SUCH_ELEMENT = 3;
    public static final int STATUS_UNKNOWN_ERROR = 4;

    private final int mChannel;
    private final int mStatus;
    private final byte[] mSelectResponse;

    /** @hide */
    public IccOpenLogicalChannelResponse(int channel, int status, byte[] selectResponse) {
        mChannel = channel;
        mStatus = status;
        mSelectResponse = selectResponse;
    }

    private IccOpenLogicalChannelResponse(Parcel in) {
        mChannel = in.readInt();
        mStatus = in.readInt();
        int arrayLength = in.readInt();
        if (arrayLength > 0) {
            mSelectResponse = new byte[arrayLength];
            in.readByteArray(mSelectResponse);
        } else {
            mSelectResponse = null;
        }
    }

    public int getChannel() { return mChannel; }

    public int getStatus() { return mStatus; }

    public byte[] getSelectResponse() { return mSelectResponse; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mChannel);
        out.writeInt(mStatus);
        if (mSelectResponse != null && mSelectResponse.length > 0) {
            out.writeInt(mSelectResponse.length);
            out.writeByteArray(mSelectResponse);
        } else {
            out.writeInt(0);
        }
    }

    public static final Creator<IccOpenLogicalChannelResponse> CREATOR =
            new Creator<IccOpenLogicalChannelResponse>() {
        @Override public IccOpenLogicalChannelResponse createFromParcel(Parcel in) {
            return new IccOpenLogicalChannelResponse(in);
        }
        @Override public IccOpenLogicalChannelResponse[] newArray(int size) {
            return new IccOpenLogicalChannelResponse[size];
        }
    };

    @Override public String toString() {
        return "Channel: " + mChannel + " Status: " + mStatus;
    }
}
