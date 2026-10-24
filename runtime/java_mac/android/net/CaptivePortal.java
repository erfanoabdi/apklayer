// android.net.CaptivePortal
//
// Handed to apps in the captive-portal sign-in intent so they can report the
// portal result. Mac.apk never detects a captive portal, so an instance is not
// produced, but the type + report methods must resolve for code that declares
// it. The report methods are no-ops (no ConnectivityService behind them).
// Mirrors frameworks/base/core/java/android/net/CaptivePortal.java.
package android.net;

public class CaptivePortal {

    public static final int APP_RETURN_DISMISSED = 0;
    public static final int APP_RETURN_UNWANTED = 1;
    public static final int APP_RETURN_WANTED_AS_IS = 2;

    public CaptivePortal() {}

    public void reportCaptivePortalDismissed() {}
    public void ignoreNetwork() {}
    public void useNetwork() {}
    public void reportNetworkConnectivity(boolean connectivity) {}
}
