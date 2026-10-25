// android.net.wifi.WifiSsid
//
// An 802.11 SSID as raw bytes (may be non-UTF-8). Pure value type. Mac.apk has
// no Wi-Fi scan, but apps reference WifiSsid from WifiInfo/ScanResult, so the
// type + fromBytes/getBytes must resolve. Mirrors AOSP android.net.wifi.WifiSsid
// public API.
package android.net.wifi;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class WifiSsid {

    private final byte[] mBytes;

    private WifiSsid(byte[] bytes) { mBytes = bytes != null ? bytes : new byte[0]; }

    public static WifiSsid fromBytes(byte[] bytes) { return new WifiSsid(bytes); }

    public byte[] getBytes() { return mBytes.clone(); }

    @Override public String toString() {
        // AOSP quotes a UTF-8-decodable SSID, else hex-encodes it. We surface a
        // best-effort UTF-8 decode (faithful for the common case).
        return new String(mBytes, StandardCharsets.UTF_8);
    }

    @Override public boolean equals(Object o) {
        return (o instanceof WifiSsid) && Arrays.equals(mBytes, ((WifiSsid) o).mBytes);
    }
    @Override public int hashCode() { return Arrays.hashCode(mBytes); }
}
