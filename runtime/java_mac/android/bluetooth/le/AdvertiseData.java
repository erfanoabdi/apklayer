// android.bluetooth.le.AdvertiseData -- the payload a BLE peripheral puts in
// its advertisement (service UUIDs, service data, manufacturer data, and the
// two "include" flags).
//
// A REAL, complete immutable value type built by its own Builder. Nothing in
// it touches a radio, so an app that builds an advertisement and reads it back
// behaves exactly as on Android. What is absent is a radio to BROADCAST it --
// see BluetoothLeAdvertiser.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/le/AdvertiseData.java; public surface
// confirmed against the android.jar API-30 reference in android-emulator/.
package android.bluetooth.le;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AdvertiseData implements Parcelable {

    private final List<ParcelUuid> mServiceUuids;
    private final SparseArray<byte[]> mManufacturerSpecificData;
    private final Map<ParcelUuid, byte[]> mServiceData;
    private final boolean mIncludeTxPowerLevel;
    private final boolean mIncludeDeviceName;

    private AdvertiseData(List<ParcelUuid> serviceUuids,
            SparseArray<byte[]> manufacturerData, Map<ParcelUuid, byte[]> serviceData,
            boolean includeTxPowerLevel, boolean includeDeviceName) {
        mServiceUuids = serviceUuids;
        mManufacturerSpecificData = manufacturerData;
        mServiceData = serviceData;
        mIncludeTxPowerLevel = includeTxPowerLevel;
        mIncludeDeviceName = includeDeviceName;
    }

    public List<ParcelUuid> getServiceUuids() { return mServiceUuids; }

    public SparseArray<byte[]> getManufacturerSpecificData() {
        return mManufacturerSpecificData;
    }

    public Map<ParcelUuid, byte[]> getServiceData() { return mServiceData; }

    public boolean getIncludeTxPowerLevel() { return mIncludeTxPowerLevel; }

    public boolean getIncludeDeviceName() { return mIncludeDeviceName; }

    @Override public int hashCode() {
        return Objects.hash(mServiceUuids, mServiceData, mIncludeDeviceName,
                mIncludeTxPowerLevel);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AdvertiseData other = (AdvertiseData) obj;
        return Objects.equals(mServiceUuids, other.mServiceUuids)
                && mIncludeDeviceName == other.mIncludeDeviceName
                && mIncludeTxPowerLevel == other.mIncludeTxPowerLevel
                && serviceDataEquals(mServiceData, other.mServiceData);
    }

    private static boolean serviceDataEquals(Map<ParcelUuid, byte[]> a,
            Map<ParcelUuid, byte[]> b) {
        if (a.size() != b.size()) return false;
        for (Map.Entry<ParcelUuid, byte[]> e : a.entrySet()) {
            if (!b.containsKey(e.getKey())) return false;
            if (!Arrays.equals(e.getValue(), b.get(e.getKey()))) return false;
        }
        return true;
    }

    @Override public String toString() {
        return "AdvertiseData [mServiceUuids=" + mServiceUuids
                + ", mIncludeTxPowerLevel=" + mIncludeTxPowerLevel
                + ", mIncludeDeviceName=" + mIncludeDeviceName + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mServiceUuids.size());
        for (ParcelUuid uuid : mServiceUuids) {
            dest.writeString(uuid.toString());
        }
        dest.writeInt(mManufacturerSpecificData.size());
        for (int i = 0; i < mManufacturerSpecificData.size(); i++) {
            dest.writeInt(mManufacturerSpecificData.keyAt(i));
            dest.writeByteArray(mManufacturerSpecificData.valueAt(i));
        }
        dest.writeInt(mServiceData.size());
        for (Map.Entry<ParcelUuid, byte[]> e : mServiceData.entrySet()) {
            dest.writeString(e.getKey().toString());
            dest.writeByteArray(e.getValue());
        }
        dest.writeBoolean(mIncludeTxPowerLevel);
        dest.writeBoolean(mIncludeDeviceName);
    }

    public static final Creator<AdvertiseData> CREATOR = new Creator<AdvertiseData>() {
        @Override public AdvertiseData createFromParcel(Parcel in) {
            Builder b = new Builder();
            int uuidCount = in.readInt();
            for (int i = 0; i < uuidCount; i++) {
                b.addServiceUuid(ParcelUuid.fromString(in.readString()));
            }
            int manufacturerCount = in.readInt();
            for (int i = 0; i < manufacturerCount; i++) {
                b.addManufacturerData(in.readInt(), in.createByteArray());
            }
            int serviceCount = in.readInt();
            for (int i = 0; i < serviceCount; i++) {
                b.addServiceData(ParcelUuid.fromString(in.readString()), in.createByteArray());
            }
            b.setIncludeTxPowerLevel(in.readBoolean());
            b.setIncludeDeviceName(in.readBoolean());
            return b.build();
        }

        @Override public AdvertiseData[] newArray(int size) {
            return new AdvertiseData[size];
        }
    };

    public static final class Builder {
        private final List<ParcelUuid> mServiceUuids = new ArrayList<>();
        private final SparseArray<byte[]> mManufacturerSpecificData = new SparseArray<>();
        private final Map<ParcelUuid, byte[]> mServiceData = new HashMap<>();
        private boolean mIncludeTxPowerLevel;
        private boolean mIncludeDeviceName;

        public Builder() {}

        public Builder addServiceUuid(ParcelUuid serviceUuid) {
            if (serviceUuid == null) {
                throw new IllegalArgumentException("serviceUuid is null");
            }
            mServiceUuids.add(serviceUuid);
            return this;
        }

        public Builder addServiceData(ParcelUuid serviceDataUuid, byte[] serviceData) {
            if (serviceDataUuid == null || serviceData == null) {
                throw new IllegalArgumentException(
                        "serviceDataUuid or serviceDataUuid is null");
            }
            mServiceData.put(serviceDataUuid, serviceData);
            return this;
        }

        /** AOSP rejects a negative manufacturer id -- the field is 16-bit on
         *  the air, so a negative value could not be encoded. */
        public Builder addManufacturerData(int manufacturerId, byte[] manufacturerSpecificData) {
            if (manufacturerId < 0) {
                throw new IllegalArgumentException(
                        "invalid manufacturerId - " + manufacturerId);
            }
            if (manufacturerSpecificData == null) {
                throw new IllegalArgumentException("manufacturerSpecificData is null");
            }
            mManufacturerSpecificData.put(manufacturerId, manufacturerSpecificData);
            return this;
        }

        public Builder setIncludeTxPowerLevel(boolean includeTxPowerLevel) {
            mIncludeTxPowerLevel = includeTxPowerLevel;
            return this;
        }

        public Builder setIncludeDeviceName(boolean includeDeviceName) {
            mIncludeDeviceName = includeDeviceName;
            return this;
        }

        public AdvertiseData build() {
            return new AdvertiseData(
                    Collections.unmodifiableList(new ArrayList<>(mServiceUuids)),
                    mManufacturerSpecificData.clone(),
                    Collections.unmodifiableMap(new HashMap<>(mServiceData)),
                    mIncludeTxPowerLevel, mIncludeDeviceName);
        }
    }
}
