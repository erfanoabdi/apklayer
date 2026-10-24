package android.telephony;

import java.util.List;

/**
 * Manager type whose only public surface is the NetworkScanCallback an app
 * subclasses and hands to TelephonyManager.requestNetworkScan.
 *
 * Source: frameworks/base/telephony/java/android/telephony/TelephonyScanManager.java
 */
public final class TelephonyScanManager {

    public TelephonyScanManager() { }

    /** AOSP declares this as an abstract static class, not an interface. */
    public abstract static class NetworkScanCallback {
        public void onResults(List<CellInfo> results) { }
        public void onComplete() { }
        public void onError(int error) { }
    }
}
