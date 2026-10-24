// android.bluetooth.le.ScanRecord
//
// Parsed advertisement payload from a ScanResult. Mac.apk never produces one,
// but ScanResult.getScanRecord can be referenced, so the type + getters must
// resolve. Mirrors
// frameworks/base/core/java/android/bluetooth/le/ScanRecord.java.
package android.bluetooth.le;

import android.os.ParcelUuid;

import java.util.List;
import java.util.Map;

public final class ScanRecord {

    ScanRecord() {}

    public int getAdvertiseFlags() { return -1; }
    public List<ParcelUuid> getServiceUuids() { return null; }
    public Map<ParcelUuid, byte[]> getServiceData() { return null; }
    public byte[] getServiceData(ParcelUuid serviceDataUuid) { return null; }
    /** AOSP: every manufacturer-specific block in the record, keyed by
     *  company id. Nothing is ever parsed here, so it is empty rather than
     *  null -- a caller iterating it must not NPE. */
    public android.util.SparseArray<byte[]> getManufacturerSpecificData() {
        return new android.util.SparseArray<>();
    }

    public byte[] getManufacturerSpecificData(int manufacturerId) { return null; }

    /** AOSP: the service-solicitation UUIDs advertised in the record. */
    public List<ParcelUuid> getServiceSolicitationUuids() { return null; }
    public String getDeviceName() { return null; }
    public int getTxPowerLevel() { return Integer.MIN_VALUE; }
    public byte[] getBytes() { return null; }

    @Override public String toString() {
        return "ScanRecord [mAdvertiseFlags=-1, mServiceUuids=null, mServiceData=null,"
                + " mDeviceName=null, mTxPowerLevel=" + Integer.MIN_VALUE + "]";
    }
}
