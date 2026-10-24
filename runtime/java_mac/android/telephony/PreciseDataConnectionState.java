package android.telephony;

import android.net.LinkProperties;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.data.ApnSetting;

import java.util.Objects;

/**
 * A detailed snapshot of one data connection.
 *
 * No modem on a Mac, so no data connection exists and
 * TelephonyCallback.PreciseDataConnectionStateListener never fires. Faithful
 * value type.
 *
 * Source: frameworks/base/telephony/java/android/telephony/PreciseDataConnectionState.java
 */
public final class PreciseDataConnectionState implements Parcelable {

    public static final int NETWORK_VALIDATION_UNSUPPORTED = 0;
    public static final int NETWORK_VALIDATION_NOT_REQUESTED = 1;
    public static final int NETWORK_VALIDATION_IN_PROGRESS = 2;
    public static final int NETWORK_VALIDATION_SUCCESS = 3;
    public static final int NETWORK_VALIDATION_FAILURE = 4;

    private final int mTransportType;
    private final int mId;
    private final int mNetworkType;
    private final int mState;
    private final int mFailCause;
    private final LinkProperties mLinkProperties;
    private final ApnSetting mApnSetting;
    private final int mNetworkValidationStatus;

    /** @hide */
    public PreciseDataConnectionState(int transportType, int id, int state, int networkType,
            LinkProperties linkProperties, int failCause, ApnSetting apnSetting,
            int networkValidationStatus) {
        mTransportType = transportType;
        mId = id;
        mState = state;
        mNetworkType = networkType;
        mLinkProperties = linkProperties;
        mFailCause = failCause;
        mApnSetting = apnSetting;
        mNetworkValidationStatus = networkValidationStatus;
    }

    private PreciseDataConnectionState(Parcel in) {
        mTransportType = in.readInt();
        mId = in.readInt();
        mState = in.readInt();
        mNetworkType = in.readInt();
        // ⚠ DEVIATION, stated: AOSP parcels the LinkProperties here, but
        // android.net.LinkProperties does not implement Parcelable in this tree
        // (it is another package's file). We skip it, so an unparcelled state
        // reports a null LinkProperties -- which AOSP also documents as possible
        // (@Nullable). Everything else round-trips exactly.
        mLinkProperties = null;
        mFailCause = in.readInt();
        mApnSetting = in.readParcelable(ApnSetting.class.getClassLoader(), ApnSetting.class);
        mNetworkValidationStatus = in.readInt();
    }

    public int getState() { return mState; }
    public int getNetworkType() { return mNetworkType; }
    public int getTransportType() { return mTransportType; }
    public int getId() { return mId; }
    public int getLastCauseCode() { return mFailCause; }
    public LinkProperties getLinkProperties() { return mLinkProperties; }
    public ApnSetting getApnSetting() { return mApnSetting; }
    public int getNetworkValidationStatus() { return mNetworkValidationStatus; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mTransportType);
        out.writeInt(mId);
        out.writeInt(mState);
        out.writeInt(mNetworkType);
        // See the CREATOR: LinkProperties is not parcelled here.
        out.writeInt(mFailCause);
        out.writeParcelable(mApnSetting, flags);
        out.writeInt(mNetworkValidationStatus);
    }

    public static final Creator<PreciseDataConnectionState> CREATOR =
            new Creator<PreciseDataConnectionState>() {
        @Override public PreciseDataConnectionState createFromParcel(Parcel in) {
            return new PreciseDataConnectionState(in);
        }
        @Override public PreciseDataConnectionState[] newArray(int size) {
            return new PreciseDataConnectionState[size];
        }
    };

    @Override public int hashCode() {
        return Objects.hash(mTransportType, mId, mState, mNetworkType, mFailCause,
                mLinkProperties, mApnSetting, mNetworkValidationStatus);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PreciseDataConnectionState)) return false;
        PreciseDataConnectionState other = (PreciseDataConnectionState) obj;
        return mTransportType == other.mTransportType && mId == other.mId
                && mState == other.mState && mNetworkType == other.mNetworkType
                && mFailCause == other.mFailCause
                && Objects.equals(mLinkProperties, other.mLinkProperties)
                && Objects.equals(mApnSetting, other.mApnSetting)
                && mNetworkValidationStatus == other.mNetworkValidationStatus;
    }

    @Override public String toString() {
        return "PreciseDataConnectionState: {transport=" + mTransportType + " id=" + mId
                + " state=" + mState + " networkType=" + mNetworkType + "}";
    }
}
