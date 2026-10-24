// android.net.wifi.WpsInfo
//
// Wi-Fi Protected Setup configuration value type. Mac.apk has no Wi-Fi stack,
// but apps construct WpsInfo for (no-op) WPS connect calls, so the type +
// setup-method constants + public fields must resolve. Mirrors AOSP
// android.net.wifi.WpsInfo public API.
package android.net.wifi;

public class WpsInfo {

    public static final int PBC = 0;
    public static final int DISPLAY = 1;
    public static final int KEYPAD = 2;
    public static final int LABEL = 3;
    public static final int INVALID = 4;

    public int setup = INVALID;
    public String BSSID;
    public String pin;

    public WpsInfo() {}
    public WpsInfo(WpsInfo source) {
        if (source != null) {
            setup = source.setup;
            BSSID = source.BSSID;
            pin = source.pin;
        }
    }
}
