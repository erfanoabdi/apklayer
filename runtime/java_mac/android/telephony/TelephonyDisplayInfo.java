package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The network type a device should DISPLAY (which can differ from the
 * technical type, e.g. showing "5G" while on LTE with NR carrier aggregation).
 *
 * No cellular radio on a Mac, so both are NETWORK_TYPE_UNKNOWN with no
 * override -- the same values AOSP reports on a Wi-Fi-only tablet.
 *
 * Source: frameworks/base/telephony/java/android/telephony/TelephonyDisplayInfo.java
 */
public final class TelephonyDisplayInfo implements Parcelable {

    public static final int OVERRIDE_NETWORK_TYPE_NONE = 0;
    public static final int OVERRIDE_NETWORK_TYPE_LTE_CA = 1;
    public static final int OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO = 2;
    public static final int OVERRIDE_NETWORK_TYPE_NR_NSA = 3;
    public static final int OVERRIDE_NETWORK_TYPE_NR_ADVANCED = 5;

    /** @deprecated AOSP folded mmWave into OVERRIDE_NETWORK_TYPE_NR_ADVANCED. */
    @Deprecated
    public static final int OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE = 4;

    private final int mNetworkType;
    private final int mOverrideNetworkType;
    private final boolean mIsRoaming;

    public TelephonyDisplayInfo(int networkType, int overrideNetworkType) {
        this(networkType, overrideNetworkType, false);
    }

    /** @hide */
    public TelephonyDisplayInfo(int networkType, int overrideNetworkType, boolean isRoaming) {
        mNetworkType = networkType;
        mOverrideNetworkType = overrideNetworkType;
        mIsRoaming = isRoaming;
    }

    /** No network at all, so never roaming on one. */
    public boolean isRoaming() { return mIsRoaming; }

    public TelephonyDisplayInfo() {
        this(TelephonyManager.NETWORK_TYPE_UNKNOWN, OVERRIDE_NETWORK_TYPE_NONE);
    }

    public int getNetworkType() { return mNetworkType; }

    public int getOverrideNetworkType() { return mOverrideNetworkType; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TelephonyDisplayInfo)) return false;
        TelephonyDisplayInfo t = (TelephonyDisplayInfo) o;
        return mNetworkType == t.mNetworkType && mOverrideNetworkType == t.mOverrideNetworkType
                && mIsRoaming == t.mIsRoaming;
    }

    @Override public int hashCode() {
        return (mNetworkType * 31 + mOverrideNetworkType) * 31 + (mIsRoaming ? 1 : 0);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mNetworkType);
        dest.writeInt(mOverrideNetworkType);
        dest.writeBoolean(mIsRoaming);
    }

    public static final Creator<TelephonyDisplayInfo> CREATOR =
            new Creator<TelephonyDisplayInfo>() {
        @Override public TelephonyDisplayInfo createFromParcel(Parcel in) {
            return new TelephonyDisplayInfo(in.readInt(), in.readInt(), in.readBoolean());
        }
        @Override public TelephonyDisplayInfo[] newArray(int size) {
            return new TelephonyDisplayInfo[size];
        }
    };
}
