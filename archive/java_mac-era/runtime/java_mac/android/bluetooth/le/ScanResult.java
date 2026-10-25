// android.bluetooth.le.ScanResult
//
// A single BLE advertisement observation. Mac.apk never produces one (no
// radio), but the type + getters must resolve for scan-result-handling code to
// compile. Mirrors
// frameworks/base/core/java/android/bluetooth/le/ScanResult.java.
package android.bluetooth.le;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;

public final class ScanResult implements Parcelable {

    /** AOSP's "not present" sentinels for the extended-advertising fields. */
    public static final int SID_NOT_PRESENT = 0xFF;
    public static final int TX_POWER_NOT_PRESENT = 0x7F;
    public static final int PERIODIC_INTERVAL_NOT_PRESENT = 0;
    public static final int PHY_UNUSED = 0x00;

    public static final int DATA_COMPLETE = 0x00;
    public static final int DATA_TRUNCATED = 0x02;

    private final BluetoothDevice mDevice;
    private final ScanRecord mScanRecord;
    private final int mRssi;
    private final long mTimestampNanos;
    private final int mEventType;
    private final int mPrimaryPhy;
    private final int mSecondaryPhy;
    private final int mAdvertisingSid;
    private final int mTxPower;
    private final int mPeriodicAdvertisingInterval;

    /** @deprecated on AOSP in favour of the extended-advertising constructor
     *  below; kept because it is still public API. */
    @Deprecated
    public ScanResult(BluetoothDevice device, ScanRecord scanRecord, int rssi,
            long timestampNanos) {
        this(device, DATA_COMPLETE, PHY_UNUSED, PHY_UNUSED, SID_NOT_PRESENT,
                TX_POWER_NOT_PRESENT, rssi, PERIODIC_INTERVAL_NOT_PRESENT, scanRecord,
                timestampNanos);
    }

    /** The API-26 extended-advertising constructor. Pure value assignment. */
    public ScanResult(BluetoothDevice device, int eventType, int primaryPhy, int secondaryPhy,
            int advertisingSid, int txPower, int rssi, int periodicAdvertisingInterval,
            ScanRecord scanRecord, long timestampNanos) {
        mDevice = device;
        mEventType = eventType;
        mPrimaryPhy = primaryPhy;
        mSecondaryPhy = secondaryPhy;
        mAdvertisingSid = advertisingSid;
        mTxPower = txPower;
        mRssi = rssi;
        mPeriodicAdvertisingInterval = periodicAdvertisingInterval;
        mScanRecord = scanRecord;
        mTimestampNanos = timestampNanos;
    }

    public BluetoothDevice getDevice() { return mDevice; }
    public ScanRecord getScanRecord() { return mScanRecord; }
    public int getRssi() { return mRssi; }
    public long getTimestampNanos() { return mTimestampNanos; }
    /** AOSP derives these from the event-type bits, so they are real. */
    public boolean isConnectable() { return (mEventType & 0x01) != 0; }
    public boolean isLegacy() { return (mEventType & 0x10) != 0; }
    public int getDataStatus() { return (mEventType >> 5) & 0x03; }

    public int getTxPower() { return mTxPower; }
    public int getPrimaryPhy() { return mPrimaryPhy; }
    public int getSecondaryPhy() { return mSecondaryPhy; }
    public int getAdvertisingSid() { return mAdvertisingSid; }
    public int getPeriodicAdvertisingInterval() { return mPeriodicAdvertisingInterval; }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ScanResult other = (ScanResult) obj;
        return java.util.Objects.equals(mDevice, other.mDevice)
                && mRssi == other.mRssi
                && mTimestampNanos == other.mTimestampNanos
                && mEventType == other.mEventType
                && mPrimaryPhy == other.mPrimaryPhy
                && mSecondaryPhy == other.mSecondaryPhy
                && mAdvertisingSid == other.mAdvertisingSid
                && mTxPower == other.mTxPower
                && mPeriodicAdvertisingInterval == other.mPeriodicAdvertisingInterval;
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(mDevice, mRssi, mTimestampNanos, mEventType,
                mPrimaryPhy, mSecondaryPhy, mAdvertisingSid, mTxPower,
                mPeriodicAdvertisingInterval);
    }

    @Override public String toString() {
        return "ScanResult{device=" + mDevice + ", rssi=" + mRssi + "}";
    }

    // PARCELABLE on AOSP. It is not decoration: android.companion's
    // DeviceFilter<D extends Parcelable> is declared over the scan-result
    // types, so BluetoothLeDeviceFilter cannot even be DECLARED unless this
    // type satisfies the bound.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mDevice, flags);
        dest.writeInt(mEventType);
        dest.writeInt(mPrimaryPhy);
        dest.writeInt(mSecondaryPhy);
        dest.writeInt(mAdvertisingSid);
        dest.writeInt(mTxPower);
        dest.writeInt(mPeriodicAdvertisingInterval);
        // ScanRecord has no public constructor and no marshalling of its own
        // (it is parsed from raw advertising bytes we never receive), so it
        // does not travel; the field reads back null.
        dest.writeInt(mRssi);
        dest.writeLong(mTimestampNanos);
    }

    public static final Creator<ScanResult> CREATOR = new Creator<ScanResult>() {
        @Override public ScanResult createFromParcel(Parcel source) {
            BluetoothDevice device = source.readParcelable(
                    BluetoothDevice.class.getClassLoader());
            int eventType = source.readInt();
            int primaryPhy = source.readInt();
            int secondaryPhy = source.readInt();
            int advertisingSid = source.readInt();
            int txPower = source.readInt();
            int periodicInterval = source.readInt();
            return new ScanResult(device, eventType, primaryPhy, secondaryPhy, advertisingSid,
                    txPower, source.readInt(), periodicInterval, null, source.readLong());
        }

        @Override public ScanResult[] newArray(int size) {
            return new ScanResult[size];
        }
    };
}
