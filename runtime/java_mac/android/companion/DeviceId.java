// android.companion.DeviceId -- the app-supplied identity (a custom id and/or
// a MAC address) that can be tagged onto an association.
//
// A REAL, complete value type built by its own Builder: nothing in it depends
// on hardware, so an app that builds one and reads it back behaves the same as
// on Android. What is absent is an ASSOCIATION to tag with it -- see
// CompanionDeviceManager.setDeviceId.
//
// Mirrors frameworks/base/core/java/android/companion/DeviceId.java; public
// surface confirmed against frameworks/base/core/api/current.txt.
package android.companion;

import android.net.MacAddress;
import android.os.Parcel;
import android.os.Parcelable;

public final class DeviceId implements Parcelable {

    private final String mCustomId;
    private final MacAddress mMacAddress;

    private DeviceId(String customId, MacAddress macAddress) {
        mCustomId = customId;
        mMacAddress = macAddress;
    }

    public String getCustomId() { return mCustomId; }

    public MacAddress getMacAddress() { return mMacAddress; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCustomId);
        // android.net.MacAddress is not Parcelable in this tree, so it travels
        // as its canonical text form -- lossless, since MacAddress.fromString
        // is the exact inverse of toString.
        dest.writeString(mMacAddress == null ? null : mMacAddress.toString());
    }

    public static final Creator<DeviceId> CREATOR = new Creator<DeviceId>() {
        @Override public DeviceId createFromParcel(Parcel source) {
            String customId = source.readString();
            String mac = source.readString();
            return new DeviceId(customId, mac == null ? null : MacAddress.fromString(mac));
        }

        @Override public DeviceId[] newArray(int size) {
            return new DeviceId[size];
        }
    };

    public static final class Builder {
        private String mCustomId;
        private MacAddress mMacAddress;

        public Builder() {}

        public Builder setCustomId(String customId) {
            mCustomId = customId;
            return this;
        }

        public Builder setMacAddress(MacAddress macAddress) {
            mMacAddress = macAddress;
            return this;
        }

        /** AOSP rejects a DeviceId that identifies nothing. */
        public DeviceId build() {
            if (mCustomId == null && mMacAddress == null) {
                throw new IllegalArgumentException(
                        "At least one of customId and macAddress must be non-null.");
            }
            return new DeviceId(mCustomId, mMacAddress);
        }
    }
}
