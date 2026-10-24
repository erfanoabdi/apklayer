// android.bluetooth.le.ScanFilter -- a predicate applied to BLE scan results.
//
// A REAL, complete value type with a REAL matches(). Mac.apk never scans (see
// BluetoothLeScanner), but a ScanFilter is pure data plus pure comparison
// logic -- no radio is involved in either -- so both are implemented properly.
// An app that builds a filter and tests it against a ScanResult it constructed
// itself gets the same answers as on Android.
//
// (The previous version DROPPED the manufacturer-data and service-data
// criteria on the floor: the Builder accepted them and stored nothing, so
// getManufacturerData() would have reported the filter as unrestricted. A
// value type that silently forgets what it was told is the exact failure the
// no-shortcuts rule exists to prevent, and nothing about the missing radio
// required it.)
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/le/ScanFilter.java.
package android.bluetooth.le;

import android.bluetooth.BluetoothAdapter;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

public final class ScanFilter implements Parcelable {

    private final String mDeviceName;
    private final String mDeviceAddress;
    private final ParcelUuid mServiceUuid;
    private final ParcelUuid mServiceUuidMask;
    private final ParcelUuid mServiceSolicitationUuid;
    private final ParcelUuid mServiceSolicitationUuidMask;
    private final ParcelUuid mServiceDataUuid;
    private final byte[] mServiceData;
    private final byte[] mServiceDataMask;
    private final int mManufacturerId;
    private final byte[] mManufacturerData;
    private final byte[] mManufacturerDataMask;

    private ScanFilter(Builder b) {
        mDeviceName = b.mDeviceName;
        mDeviceAddress = b.mDeviceAddress;
        mServiceUuid = b.mServiceUuid;
        mServiceUuidMask = b.mUuidMask;
        mServiceSolicitationUuid = b.mServiceSolicitationUuid;
        mServiceSolicitationUuidMask = b.mServiceSolicitationUuidMask;
        mServiceDataUuid = b.mServiceDataUuid;
        mServiceData = b.mServiceData;
        mServiceDataMask = b.mServiceDataMask;
        mManufacturerId = b.mManufacturerId;
        mManufacturerData = b.mManufacturerData;
        mManufacturerDataMask = b.mManufacturerDataMask;
    }

    public String getDeviceName() { return mDeviceName; }

    public String getDeviceAddress() { return mDeviceAddress; }

    public ParcelUuid getServiceUuid() { return mServiceUuid; }

    public ParcelUuid getServiceUuidMask() { return mServiceUuidMask; }

    public ParcelUuid getServiceSolicitationUuid() { return mServiceSolicitationUuid; }

    public ParcelUuid getServiceSolicitationUuidMask() { return mServiceSolicitationUuidMask; }

    public ParcelUuid getServiceDataUuid() { return mServiceDataUuid; }

    public byte[] getServiceData() { return mServiceData; }

    public byte[] getServiceDataMask() { return mServiceDataMask; }

    /** AOSP returns -1 when no manufacturer criterion was set. */
    public int getManufacturerId() { return mManufacturerId; }

    public byte[] getManufacturerData() { return mManufacturerData; }

    public byte[] getManufacturerDataMask() { return mManufacturerDataMask; }

    /**
     * AOSP's predicate, reproduced: a null criterion matches anything; a
     * non-null one must match, and a MASK (when present) selects which bits
     * are compared. Pure logic, no radio.
     */
    public boolean matches(ScanResult scanResult) {
        if (scanResult == null) {
            return false;
        }
        android.bluetooth.BluetoothDevice device = scanResult.getDevice();
        if (mDeviceAddress != null
                && (device == null || !mDeviceAddress.equals(device.getAddress()))) {
            return false;
        }
        ScanRecord record = scanResult.getScanRecord();
        if (record == null
                && (mDeviceName != null || mServiceUuid != null || mManufacturerData != null
                        || mServiceData != null || mServiceSolicitationUuid != null)) {
            return false;
        }
        if (mDeviceName != null
                && !mDeviceName.equals(record.getDeviceName())) {
            return false;
        }
        if (mServiceUuid != null
                && !matchesServiceUuids(mServiceUuid, mServiceUuidMask, record.getServiceUuids())) {
            return false;
        }
        if (mServiceSolicitationUuid != null
                && !matchesServiceUuids(mServiceSolicitationUuid, mServiceSolicitationUuidMask,
                        record.getServiceSolicitationUuids())) {
            return false;
        }
        if (mServiceDataUuid != null
                && !matchesPartialData(mServiceData, mServiceDataMask,
                        record.getServiceData(mServiceDataUuid))) {
            return false;
        }
        if (mManufacturerId >= 0
                && !matchesPartialData(mManufacturerData, mManufacturerDataMask,
                        record.getManufacturerSpecificData(mManufacturerId))) {
            return false;
        }
        return true;
    }

    private static boolean matchesServiceUuids(ParcelUuid uuid, ParcelUuid mask,
            java.util.List<ParcelUuid> uuids) {
        if (uuid == null) return true;
        if (uuids == null) return false;
        for (ParcelUuid u : uuids) {
            if (matchesServiceUuid(uuid.getUuid(), mask == null ? null : mask.getUuid(),
                    u.getUuid())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesServiceUuid(java.util.UUID uuid, java.util.UUID mask,
            java.util.UUID data) {
        if (mask == null) {
            return uuid.equals(data);
        }
        return (uuid.getLeastSignificantBits() & mask.getLeastSignificantBits())
                        == (data.getLeastSignificantBits() & mask.getLeastSignificantBits())
                && (uuid.getMostSignificantBits() & mask.getMostSignificantBits())
                        == (data.getMostSignificantBits() & mask.getMostSignificantBits());
    }

    private static boolean matchesPartialData(byte[] data, byte[] dataMask, byte[] parsedData) {
        if (data == null) return true;
        if (parsedData == null || parsedData.length < data.length) return false;
        if (dataMask == null) {
            for (int i = 0; i < data.length; i++) {
                if (parsedData[i] != data[i]) return false;
            }
            return true;
        }
        for (int i = 0; i < data.length; i++) {
            if ((dataMask[i] & parsedData[i]) != (dataMask[i] & data[i])) return false;
        }
        return true;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ScanFilter other = (ScanFilter) obj;
        return Objects.equals(mDeviceName, other.mDeviceName)
                && Objects.equals(mDeviceAddress, other.mDeviceAddress)
                && mManufacturerId == other.mManufacturerId
                && Arrays.equals(mManufacturerData, other.mManufacturerData)
                && Arrays.equals(mManufacturerDataMask, other.mManufacturerDataMask)
                && Objects.equals(mServiceDataUuid, other.mServiceDataUuid)
                && Arrays.equals(mServiceData, other.mServiceData)
                && Arrays.equals(mServiceDataMask, other.mServiceDataMask)
                && Objects.equals(mServiceUuid, other.mServiceUuid)
                && Objects.equals(mServiceUuidMask, other.mServiceUuidMask)
                && Objects.equals(mServiceSolicitationUuid, other.mServiceSolicitationUuid)
                && Objects.equals(mServiceSolicitationUuidMask,
                        other.mServiceSolicitationUuidMask);
    }

    @Override public int hashCode() {
        return Objects.hash(mDeviceName, mDeviceAddress, mManufacturerId,
                Arrays.hashCode(mManufacturerData), Arrays.hashCode(mManufacturerDataMask),
                mServiceDataUuid, Arrays.hashCode(mServiceData),
                Arrays.hashCode(mServiceDataMask), mServiceUuid, mServiceUuidMask);
    }

    @Override public String toString() {
        return "BluetoothLeScanFilter [mDeviceName=" + mDeviceName
                + ", mDeviceAddress=" + mDeviceAddress
                + ", mUuid=" + mServiceUuid + ", mUuidMask=" + mServiceUuidMask
                + ", mServiceDataUuid=" + mServiceDataUuid
                + ", mManufacturerId=" + mManufacturerId + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDeviceName);
        dest.writeString(mDeviceAddress);
        writeUuid(dest, mServiceUuid);
        writeUuid(dest, mServiceUuidMask);
        writeUuid(dest, mServiceSolicitationUuid);
        writeUuid(dest, mServiceSolicitationUuidMask);
        writeUuid(dest, mServiceDataUuid);
        dest.writeByteArray(mServiceData);
        dest.writeByteArray(mServiceDataMask);
        dest.writeInt(mManufacturerId);
        dest.writeByteArray(mManufacturerData);
        dest.writeByteArray(mManufacturerDataMask);
    }

    private static void writeUuid(Parcel dest, ParcelUuid uuid) {
        dest.writeString(uuid == null ? null : uuid.toString());
    }

    private static ParcelUuid readUuid(Parcel in) {
        String s = in.readString();
        return s == null ? null : ParcelUuid.fromString(s);
    }

    public static final Creator<ScanFilter> CREATOR = new Creator<ScanFilter>() {
        @Override public ScanFilter createFromParcel(Parcel in) {
            Builder b = new Builder();
            b.setDeviceName(in.readString());
            b.setDeviceAddress(in.readString());
            ParcelUuid uuid = readUuid(in);
            ParcelUuid uuidMask = readUuid(in);
            if (uuid != null) {
                b.setServiceUuid(uuid, uuidMask);
            }
            ParcelUuid solicit = readUuid(in);
            ParcelUuid solicitMask = readUuid(in);
            if (solicit != null) {
                b.setServiceSolicitationUuid(solicit, solicitMask);
            }
            ParcelUuid serviceDataUuid = readUuid(in);
            byte[] serviceData = in.createByteArray();
            byte[] serviceDataMask = in.createByteArray();
            if (serviceDataUuid != null) {
                b.setServiceData(serviceDataUuid, serviceData, serviceDataMask);
            }
            int manufacturerId = in.readInt();
            byte[] manufacturerData = in.createByteArray();
            byte[] manufacturerDataMask = in.createByteArray();
            if (manufacturerId >= 0) {
                b.setManufacturerData(manufacturerId, manufacturerData, manufacturerDataMask);
            }
            return b.build();
        }

        @Override public ScanFilter[] newArray(int size) {
            return new ScanFilter[size];
        }
    };

    public static final class Builder {
        private String mDeviceName;
        private String mDeviceAddress;
        private ParcelUuid mServiceUuid;
        private ParcelUuid mUuidMask;
        private ParcelUuid mServiceSolicitationUuid;
        private ParcelUuid mServiceSolicitationUuidMask;
        private ParcelUuid mServiceDataUuid;
        private byte[] mServiceData;
        private byte[] mServiceDataMask;
        /** AOSP's "unset" sentinel; getManufacturerId() reports it verbatim. */
        private int mManufacturerId = -1;
        private byte[] mManufacturerData;
        private byte[] mManufacturerDataMask;

        public Builder() {}

        public Builder setDeviceName(String deviceName) {
            mDeviceName = deviceName;
            return this;
        }

        /** AOSP validates the address format rather than storing a filter that
         *  could never match. */
        public Builder setDeviceAddress(String deviceAddress) {
            if (deviceAddress != null && !BluetoothAdapter.checkBluetoothAddress(deviceAddress)) {
                throw new IllegalArgumentException("invalid device address " + deviceAddress);
            }
            mDeviceAddress = deviceAddress;
            return this;
        }

        public Builder setServiceUuid(ParcelUuid serviceUuid) {
            mServiceUuid = serviceUuid;
            mUuidMask = null;
            return this;
        }

        /** AOSP rejects a mask with no UUID -- it would mask nothing. */
        public Builder setServiceUuid(ParcelUuid serviceUuid, ParcelUuid uuidMask) {
            if (uuidMask != null && serviceUuid == null) {
                throw new IllegalArgumentException("uuid is null while uuidMask is not null!");
            }
            mServiceUuid = serviceUuid;
            mUuidMask = uuidMask;
            return this;
        }

        public Builder setServiceSolicitationUuid(ParcelUuid serviceSolicitationUuid) {
            mServiceSolicitationUuid = serviceSolicitationUuid;
            if (serviceSolicitationUuid == null) {
                mServiceSolicitationUuidMask = null;
            }
            return this;
        }

        public Builder setServiceSolicitationUuid(ParcelUuid serviceSolicitationUuid,
                ParcelUuid solicitationUuidMask) {
            if (solicitationUuidMask != null && serviceSolicitationUuid == null) {
                throw new IllegalArgumentException(
                        "SolicitationUuid is null while SolicitationUuidMask is not null!");
            }
            mServiceSolicitationUuid = serviceSolicitationUuid;
            mServiceSolicitationUuidMask = solicitationUuidMask;
            return this;
        }

        public Builder setServiceData(ParcelUuid serviceDataUuid, byte[] serviceData) {
            if (serviceDataUuid == null) {
                throw new IllegalArgumentException("serviceDataUuid is null");
            }
            mServiceDataUuid = serviceDataUuid;
            mServiceData = serviceData;
            mServiceDataMask = null;
            return this;
        }

        /** AOSP requires the mask to be the same length as the data. */
        public Builder setServiceData(ParcelUuid serviceDataUuid, byte[] serviceData,
                byte[] serviceDataMask) {
            if (serviceDataUuid == null) {
                throw new IllegalArgumentException("serviceDataUuid is null");
            }
            if (serviceDataMask != null) {
                if (serviceData == null) {
                    throw new IllegalArgumentException(
                            "serviceData is null while serviceDataMask is not null");
                }
                if (serviceData.length != serviceDataMask.length) {
                    throw new IllegalArgumentException(
                            "size mismatch for service data and service data mask");
                }
            }
            mServiceDataUuid = serviceDataUuid;
            mServiceData = serviceData;
            mServiceDataMask = serviceDataMask;
            return this;
        }

        public Builder setManufacturerData(int manufacturerId, byte[] manufacturerData) {
            if (manufacturerData != null && manufacturerId < 0) {
                throw new IllegalArgumentException("invalid manufacture id");
            }
            mManufacturerId = manufacturerId;
            mManufacturerData = manufacturerData;
            mManufacturerDataMask = null;
            return this;
        }

        /** AOSP requires the mask to be the same length as the data. */
        public Builder setManufacturerData(int manufacturerId, byte[] manufacturerData,
                byte[] manufacturerDataMask) {
            if (manufacturerData != null && manufacturerId < 0) {
                throw new IllegalArgumentException("invalid manufacture id");
            }
            if (manufacturerDataMask != null) {
                if (manufacturerData == null) {
                    throw new IllegalArgumentException(
                            "manufacturerData is null while manufacturerDataMask is not null");
                }
                if (manufacturerData.length != manufacturerDataMask.length) {
                    throw new IllegalArgumentException(
                            "size mismatch for manufacturerData and manufacturerDataMask");
                }
            }
            mManufacturerId = manufacturerId;
            mManufacturerData = manufacturerData;
            mManufacturerDataMask = manufacturerDataMask;
            return this;
        }

        public ScanFilter build() {
            return new ScanFilter(this);
        }
    }
}
