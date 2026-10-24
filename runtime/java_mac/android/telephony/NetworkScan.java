package android.telephony;

/**
 * Handle for an in-flight network scan.
 *
 * Mac.apk never starts one (no radio), so TelephonyManager.requestNetworkScan
 * reports ERROR_UNSUPPORTED to the callback and returns null, which AOSP
 * documents as the failure result. This class exists so a caller that stores
 * the handle and later calls stopScan() links.
 *
 * Source: frameworks/base/telephony/java/android/telephony/NetworkScan.java
 */
public class NetworkScan {

    public static final int SUCCESS = 0;
    public static final int ERROR_MODEM_ERROR = 1;
    public static final int ERROR_INVALID_SCAN = 2;
    public static final int ERROR_MODEM_UNAVAILABLE = 3;
    public static final int ERROR_UNSUPPORTED = 4;
    public static final int ERROR_RADIO_INTERFACE_ERROR = 10000;
    public static final int ERROR_INVALID_SCANID = 10001;
    public static final int ERROR_INTERRUPTED = 10002;

    private final int mScanId;
    private final int mSubId;

    /** @hide */
    public NetworkScan(int scanId, int subId) {
        mScanId = scanId;
        mSubId = subId;
    }

    /** @hide */ public int getScanId() { return mScanId; }
    /** @hide */ public int getSubId() { return mSubId; }

    /** No scan is ever running here, so stopping one is a genuine no-op. */
    public void stopScan() { }
}
