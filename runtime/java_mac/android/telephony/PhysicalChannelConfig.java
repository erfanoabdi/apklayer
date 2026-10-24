package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * One physical channel the modem has configured.
 *
 * No modem on a Mac, so TelephonyCallback.PhysicalChannelConfigListener never
 * fires and this is never produced. Faithful value type; every field defaults
 * to AOSP's own UNKNOWN sentinel rather than 0.
 *
 * Source: frameworks/base/telephony/java/android/telephony/PhysicalChannelConfig.java
 */
public final class PhysicalChannelConfig implements Parcelable {

    public static final int BAND_UNKNOWN = 0;
    public static final int CELL_BANDWIDTH_UNKNOWN = 0;
    public static final int CHANNEL_NUMBER_UNKNOWN = 2147483647;
    /** @deprecated */ @Deprecated
    public static final int CONNECTION_PRIMARY_SERVING = 1;
    /** @deprecated */ @Deprecated
    public static final int CONNECTION_SECONDARY_SERVING = 2;
    /** @deprecated */ @Deprecated
    public static final int CONNECTION_UNKNOWN = -1;
    public static final int FREQUENCY_UNKNOWN = -1;
    public static final int PHYSICAL_CELL_ID_MAXIMUM_VALUE = 1007;
    public static final int PHYSICAL_CELL_ID_UNKNOWN = -1;

    private final int mConnectionStatus;
    private final int mCellBandwidthDownlinkKhz;
    private final int mCellBandwidthUplinkKhz;
    private final int mNetworkType;
    private final int mDownlinkFrequency;
    private final int mUplinkFrequency;
    private final int mDownlinkChannelNumber;
    private final int mUplinkChannelNumber;
    private final int mBand;
    private final int mPhysicalCellId;

    /** @hide */
    public PhysicalChannelConfig(int connectionStatus, int cellBandwidthDownlinkKhz,
            int cellBandwidthUplinkKhz, int networkType, int downlinkFrequency,
            int uplinkFrequency, int downlinkChannelNumber, int uplinkChannelNumber, int band,
            int physicalCellId) {
        mConnectionStatus = connectionStatus;
        mCellBandwidthDownlinkKhz = cellBandwidthDownlinkKhz;
        mCellBandwidthUplinkKhz = cellBandwidthUplinkKhz;
        mNetworkType = networkType;
        mDownlinkFrequency = downlinkFrequency;
        mUplinkFrequency = uplinkFrequency;
        mDownlinkChannelNumber = downlinkChannelNumber;
        mUplinkChannelNumber = uplinkChannelNumber;
        mBand = band;
        mPhysicalCellId = physicalCellId;
    }

    private PhysicalChannelConfig(Parcel in) {
        mConnectionStatus = in.readInt();
        mCellBandwidthDownlinkKhz = in.readInt();
        mCellBandwidthUplinkKhz = in.readInt();
        mNetworkType = in.readInt();
        mDownlinkFrequency = in.readInt();
        mUplinkFrequency = in.readInt();
        mDownlinkChannelNumber = in.readInt();
        mUplinkChannelNumber = in.readInt();
        mBand = in.readInt();
        mPhysicalCellId = in.readInt();
    }

    public int getConnectionStatus() { return mConnectionStatus; }
    public int getCellBandwidthDownlinkKhz() { return mCellBandwidthDownlinkKhz; }
    public int getCellBandwidthUplinkKhz() { return mCellBandwidthUplinkKhz; }
    public int getNetworkType() { return mNetworkType; }
    public int getDownlinkFrequencyKhz() { return mDownlinkFrequency; }
    public int getUplinkFrequencyKhz() { return mUplinkFrequency; }
    public int getDownlinkChannelNumber() { return mDownlinkChannelNumber; }
    public int getUplinkChannelNumber() { return mUplinkChannelNumber; }
    public int getBand() { return mBand; }
    public int getPhysicalCellId() { return mPhysicalCellId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mConnectionStatus);
        dest.writeInt(mCellBandwidthDownlinkKhz);
        dest.writeInt(mCellBandwidthUplinkKhz);
        dest.writeInt(mNetworkType);
        dest.writeInt(mDownlinkFrequency);
        dest.writeInt(mUplinkFrequency);
        dest.writeInt(mDownlinkChannelNumber);
        dest.writeInt(mUplinkChannelNumber);
        dest.writeInt(mBand);
        dest.writeInt(mPhysicalCellId);
    }

    public static final Creator<PhysicalChannelConfig> CREATOR =
            new Creator<PhysicalChannelConfig>() {
        @Override public PhysicalChannelConfig createFromParcel(Parcel in) {
            return new PhysicalChannelConfig(in);
        }
        @Override public PhysicalChannelConfig[] newArray(int size) {
            return new PhysicalChannelConfig[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhysicalChannelConfig)) return false;
        PhysicalChannelConfig c = (PhysicalChannelConfig) o;
        return mConnectionStatus == c.mConnectionStatus
                && mCellBandwidthDownlinkKhz == c.mCellBandwidthDownlinkKhz
                && mCellBandwidthUplinkKhz == c.mCellBandwidthUplinkKhz
                && mNetworkType == c.mNetworkType
                && mDownlinkFrequency == c.mDownlinkFrequency
                && mUplinkFrequency == c.mUplinkFrequency
                && mDownlinkChannelNumber == c.mDownlinkChannelNumber
                && mUplinkChannelNumber == c.mUplinkChannelNumber
                && mBand == c.mBand
                && mPhysicalCellId == c.mPhysicalCellId;
    }

    @Override public int hashCode() {
        return Objects.hash(mConnectionStatus, mCellBandwidthDownlinkKhz, mCellBandwidthUplinkKhz,
                mNetworkType, mDownlinkFrequency, mUplinkFrequency, mDownlinkChannelNumber,
                mUplinkChannelNumber, mBand, mPhysicalCellId);
    }

    @Override public String toString() {
        return "PhysicalChannelConfig{mNetworkType=" + mNetworkType + " mBand=" + mBand + "}";
    }
}
