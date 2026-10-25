// android.companion.BluetoothDeviceFilter -- narrows a companion association
// request to classic Bluetooth devices matching an address / name pattern /
// service UUIDs.
//
// A REAL value type built by its own Builder: the criteria are just data the
// app supplies, so building one behaves the same as on Android. Nothing here
// SCANS -- Mac.apk exposes no Bluetooth bridge (see android.bluetooth) and no
// companion pairing UI, so no filter is ever evaluated.
//
// The public API is only the Builder plus Parcelable, which is why there are
// no getters below: AOSP's accessors are @hide (they are read by the system
// companion service, which does not exist here).
//
// Mirrors
// frameworks/base/core/java/android/companion/BluetoothDeviceFilter.java;
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.companion;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class BluetoothDeviceFilter implements DeviceFilter<BluetoothDevice> {

    private final Pattern mNamePattern;
    private final String mAddress;
    private final List<ParcelUuid> mServiceUuids;
    private final List<ParcelUuid> mServiceUuidMasks;

    private BluetoothDeviceFilter(Builder b) {
        mNamePattern = b.mNamePattern;
        mAddress = b.mAddress;
        mServiceUuids = new ArrayList<>(b.mServiceUuids);
        mServiceUuidMasks = new ArrayList<>(b.mServiceUuidMasks);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNamePattern == null ? null : mNamePattern.pattern());
        dest.writeString(mAddress);
    }

    public static final Creator<BluetoothDeviceFilter> CREATOR =
            new Creator<BluetoothDeviceFilter>() {
        @Override public BluetoothDeviceFilter createFromParcel(Parcel source) {
            Builder b = new Builder();
            String pattern = source.readString();
            if (pattern != null) {
                b.setNamePattern(Pattern.compile(pattern));
            }
            b.setAddress(source.readString());
            return b.build();
        }

        @Override public BluetoothDeviceFilter[] newArray(int size) {
            return new BluetoothDeviceFilter[size];
        }
    };

    public static final class Builder {
        private Pattern mNamePattern;
        private String mAddress;
        private final List<ParcelUuid> mServiceUuids = new ArrayList<>();
        private final List<ParcelUuid> mServiceUuidMasks = new ArrayList<>();

        public Builder() {}

        public Builder setNamePattern(Pattern re) {
            mNamePattern = re;
            return this;
        }

        public Builder setAddress(String address) {
            mAddress = address;
            return this;
        }

        /** AOSP keeps the uuid and its mask positionally paired, so a null
         *  mask still occupies a slot rather than shifting the list. */
        public Builder addServiceUuid(ParcelUuid serviceUuid, ParcelUuid serviceUuidMask) {
            mServiceUuids.add(serviceUuid);
            mServiceUuidMasks.add(serviceUuidMask);
            return this;
        }

        public BluetoothDeviceFilter build() {
            return new BluetoothDeviceFilter(this);
        }
    }
}
