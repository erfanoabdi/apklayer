package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

/**
 * A URSP traffic descriptor (DNN and/or OS/App id).
 *
 * Immutable value type; Mac.apk never produces one.
 *
 * Source: frameworks/base/telephony/java/android/telephony/data/TrafficDescriptor.java
 */
public final class TrafficDescriptor implements Parcelable {

    private final String mDnn;
    private final byte[] mOsAppId;

    /** @hide */
    public TrafficDescriptor(String dnn, byte[] osAppId) {
        mDnn = dnn;
        mOsAppId = osAppId;
    }

    private TrafficDescriptor(Parcel in) {
        mDnn = in.readString();
        mOsAppId = in.createByteArray();
    }

    public String getDataNetworkName() { return mDnn; }

    public byte[] getOsAppId() { return mOsAppId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDnn);
        dest.writeByteArray(mOsAppId);
    }

    public static final Creator<TrafficDescriptor> CREATOR = new Creator<TrafficDescriptor>() {
        @Override public TrafficDescriptor createFromParcel(Parcel in) {
            return new TrafficDescriptor(in);
        }
        @Override public TrafficDescriptor[] newArray(int size) { return new TrafficDescriptor[size]; }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrafficDescriptor)) return false;
        TrafficDescriptor that = (TrafficDescriptor) o;
        return Objects.equals(mDnn, that.mDnn) && Arrays.equals(mOsAppId, that.mOsAppId);
    }

    @Override public int hashCode() {
        return Objects.hash(mDnn, Arrays.hashCode(mOsAppId));
    }

    @Override public String toString() {
        return "TrafficDescriptor={mDnn=" + mDnn + ", mOsAppId=" + Arrays.toString(mOsAppId) + "}";
    }

    public static final class Builder {
        private String mDnn = null;
        private byte[] mOsAppId = null;

        public Builder() { }

        public Builder setDataNetworkName(String dnn) {
            mDnn = dnn;
            return this;
        }

        public Builder setOsAppId(byte[] osAppId) {
            mOsAppId = osAppId;
            return this;
        }

        public TrafficDescriptor build() {
            if (mDnn == null && mOsAppId == null) {
                // AOSP requires at least one of the two to be present.
                throw new IllegalArgumentException("DNN and OS App ID are null");
            }
            return new TrafficDescriptor(mDnn, mOsAppId);
        }
    }
}
