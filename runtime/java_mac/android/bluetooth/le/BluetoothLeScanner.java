// android.bluetooth.le.BluetoothLeScanner
//
// The BLE scan entry point obtained from BluetoothAdapter.getBluetoothLeScanner.
// Mac.apk has no radio, so a scan never finds anything; startScan reports
// SCAN_FAILED_INTERNAL_ERROR to the callback so apps stop waiting, and stopScan
// is a no-op. The class is reachable only if an adapter exists, which on
// Mac.apk it generally does not, but the type must resolve for fields/casts.
// Mirrors frameworks/base/core/java/android/bluetooth/le/BluetoothLeScanner.java.
package android.bluetooth.le;

import java.util.List;

public final class BluetoothLeScanner {

    /** The extras the PendingIntent-based scan API delivers its results in.
     *  Plain strings, mirrored verbatim; no broadcast is ever sent here. */
    public static final String EXTRA_CALLBACK_TYPE = "android.bluetooth.le.extra.CALLBACK_TYPE";
    public static final String EXTRA_ERROR_CODE = "android.bluetooth.le.extra.ERROR_CODE";
    public static final String EXTRA_LIST_SCAN_RESULT =
            "android.bluetooth.le.extra.LIST_SCAN_RESULT";

    public BluetoothLeScanner() {}

    public void startScan(ScanCallback callback) {
        if (callback != null) callback.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR);
    }

    public void startScan(List<ScanFilter> filters, ScanSettings settings,
            ScanCallback callback) {
        if (callback != null) callback.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR);
    }

    /**
     * The PendingIntent-based scan (API 26). AOSP returns a status int:
     * SCAN_FAILED_INTERNAL_ERROR is the honest one here for the same reason
     * the callback-based overloads report it -- there is no radio, and a
     * success code would leave the app waiting on a broadcast that cannot
     * arrive. Note it CANNOT report through the callback path, because there
     * is no ScanCallback on this overload; the return value is the whole
     * channel, which is exactly why it must not be 0.
     */
    public int startScan(List<ScanFilter> filters, ScanSettings settings,
            android.app.PendingIntent callbackIntent) {
        return ScanCallback.SCAN_FAILED_INTERNAL_ERROR;
    }

    public void stopScan(ScanCallback callback) {}

    /** A removal: no scan is running, so there is nothing to stop. */
    public void stopScan(android.app.PendingIntent callbackIntent) {}

    public void flushPendingScanResults(ScanCallback callback) {}
}
