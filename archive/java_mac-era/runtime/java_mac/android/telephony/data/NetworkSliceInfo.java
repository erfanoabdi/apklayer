package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * One 5G network slice (S-NSSAI) description.
 *
 * Immutable value type. Mac.apk has no 5G modem, so
 * TelephonyManager.getNetworkSlicingConfiguration() reports an empty config.
 *
 * Source: frameworks/base/telephony/java/android/telephony/data/NetworkSliceInfo.java
 */
public final class NetworkSliceInfo implements Parcelable {

    public static final int SLICE_SERVICE_TYPE_NONE = 0;
    public static final int SLICE_SERVICE_TYPE_EMBB = 1;
    public static final int SLICE_SERVICE_TYPE_URLLC = 2;
    public static final int SLICE_SERVICE_TYPE_MIOT = 3;

    public static final int SLICE_DIFFERENTIATOR_NO_SLICE = -1;
    /** @hide AOSP's MAX_SLICE_DIFFERENTIATOR. */
    public static final int MAX_SLICE_DIFFERENTIATOR = 0xFFFFFE;

    public static final int SLICE_STATUS_UNKNOWN = 0;
    public static final int SLICE_STATUS_CONFIGURED = 1;
    public static final int SLICE_STATUS_ALLOWED = 2;
    public static final int SLICE_STATUS_REJECTED_NOT_AVAILABLE_IN_PLMN = 3;
    public static final int SLICE_STATUS_REJECTED_NOT_AVAILABLE_IN_REGISTERED_AREA = 4;
    public static final int SLICE_STATUS_DEFAULT_CONFIGURED = 5;

    private final int mSliceServiceType;
    private final int mSliceDifferentiator;
    private final int mMappedHplmnSliceServiceType;
    private final int mMappedHplmnSliceDifferentiator;
    private final int mStatus;

    private NetworkSliceInfo(int sliceServiceType, int sliceDifferentiator,
            int mappedHplmnSliceServiceType, int mappedHplmnSliceDifferentiator, int status) {
        mSliceServiceType = sliceServiceType;
        mSliceDifferentiator = sliceDifferentiator;
        mMappedHplmnSliceServiceType = mappedHplmnSliceServiceType;
        mMappedHplmnSliceDifferentiator = mappedHplmnSliceDifferentiator;
        mStatus = status;
    }

    private NetworkSliceInfo(Parcel in) {
        mSliceServiceType = in.readInt();
        mSliceDifferentiator = in.readInt();
        mMappedHplmnSliceServiceType = in.readInt();
        mMappedHplmnSliceDifferentiator = in.readInt();
        mStatus = in.readInt();
    }

    public int getSliceServiceType() { return mSliceServiceType; }
    public int getSliceDifferentiator() { return mSliceDifferentiator; }
    public int getMappedHplmnSliceServiceType() { return mMappedHplmnSliceServiceType; }
    public int getMappedHplmnSliceDifferentiator() { return mMappedHplmnSliceDifferentiator; }
    public int getStatus() { return mStatus; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSliceServiceType);
        dest.writeInt(mSliceDifferentiator);
        dest.writeInt(mMappedHplmnSliceServiceType);
        dest.writeInt(mMappedHplmnSliceDifferentiator);
        dest.writeInt(mStatus);
    }

    public static final Creator<NetworkSliceInfo> CREATOR = new Creator<NetworkSliceInfo>() {
        @Override public NetworkSliceInfo createFromParcel(Parcel in) {
            return new NetworkSliceInfo(in);
        }
        @Override public NetworkSliceInfo[] newArray(int size) { return new NetworkSliceInfo[size]; }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkSliceInfo)) return false;
        NetworkSliceInfo that = (NetworkSliceInfo) o;
        return mSliceServiceType == that.mSliceServiceType
                && mSliceDifferentiator == that.mSliceDifferentiator
                && mMappedHplmnSliceServiceType == that.mMappedHplmnSliceServiceType
                && mMappedHplmnSliceDifferentiator == that.mMappedHplmnSliceDifferentiator
                && mStatus == that.mStatus;
    }

    @Override public int hashCode() {
        return Objects.hash(mSliceServiceType, mSliceDifferentiator, mMappedHplmnSliceServiceType,
                mMappedHplmnSliceDifferentiator, mStatus);
    }

    @Override public String toString() {
        return "SliceInfo{sliceServiceType=" + mSliceServiceType
                + ", sliceDifferentiator=" + mSliceDifferentiator + ", status=" + mStatus + '}';
    }

    public static final class Builder {
        private int mSliceServiceType = SLICE_SERVICE_TYPE_NONE;
        private int mSliceDifferentiator = SLICE_DIFFERENTIATOR_NO_SLICE;
        private int mMappedHplmnSliceServiceType = SLICE_SERVICE_TYPE_NONE;
        private int mMappedHplmnSliceDifferentiator = SLICE_DIFFERENTIATOR_NO_SLICE;
        private int mStatus = SLICE_STATUS_UNKNOWN;

        public Builder() { }

        public Builder setSliceServiceType(int sliceServiceType) {
            mSliceServiceType = sliceServiceType;
            return this;
        }

        public Builder setMappedHplmnSliceServiceType(int mappedHplmnSst) {
            mMappedHplmnSliceServiceType = mappedHplmnSst;
            return this;
        }

        public Builder setSliceDifferentiator(int sliceDifferentiator) {
            checkDifferentiator(sliceDifferentiator);
            mSliceDifferentiator = sliceDifferentiator;
            return this;
        }

        public Builder setMappedHplmnSliceDifferentiator(int mappedHplmnSliceDifferentiator) {
            checkDifferentiator(mappedHplmnSliceDifferentiator);
            mMappedHplmnSliceDifferentiator = mappedHplmnSliceDifferentiator;
            return this;
        }

        public Builder setStatus(int status) {
            mStatus = status;
            return this;
        }

        private static void checkDifferentiator(int v) {
            if (v < SLICE_DIFFERENTIATOR_NO_SLICE || v > MAX_SLICE_DIFFERENTIATOR) {
                throw new IllegalArgumentException("The slice differentiator value is out of range");
            }
        }

        public NetworkSliceInfo build() {
            return new NetworkSliceInfo(mSliceServiceType, mSliceDifferentiator,
                    mMappedHplmnSliceServiceType, mMappedHplmnSliceDifferentiator, mStatus);
        }
    }
}
