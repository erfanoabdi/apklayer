package apklayer.host;

import android.net.Uri;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSliceManager;

/**
 * System services Play services' intent operations call at module init, where Robolectric's stand-in throws something Android never does. Play services' uncaught-exception handler ends its whole process on such a throw, so it restarted again and again and Play Store's binds to it timed out.
 */
public final class ShadowHostSystemGaps {
    private ShadowHostSystemGaps() {}

    /** SliceManagerService records a grant for any package name; the stand-in looks the package up and throws for one that is not installed (Files by Google). */
    @Implements(className = "android.app.slice.SliceManager", isInAndroidSdk = false)
    public static class SliceManager extends ShadowSliceManager {
        @Override @Implementation protected synchronized void grantSlicePermission(String toPackage, Uri uri) {
            try { super.grantSlicePermission(toPackage, uri); } catch (RuntimeException notInstalled) { /* the grant is not recorded */ }
        }

        @Override @Implementation protected synchronized void revokeSlicePermission(String toPackage, Uri uri) {
            try { super.revokeSlicePermission(toPackage, uri); } catch (RuntimeException notInstalled) { /* nothing to revoke */ }
        }
    }
    /**
     * TelephonyManager.createForSubscriptionId always returns a manager on Android; Robolectric's returns null for a subscription nobody registered, and Play services
     * (SIM operator lookups while it creates its databases) then threw a NullPointerException that ended its process.
     */
    @Implements(className = "android.telephony.TelephonyManager", isInAndroidSdk = false)
    public static class TelephonyManager extends org.robolectric.shadows.ShadowTelephonyManager {
        @org.robolectric.annotation.RealObject private android.telephony.TelephonyManager real;

        @Override @Implementation protected android.telephony.TelephonyManager createForSubscriptionId(int subscriptionId) {
            android.telephony.TelephonyManager registered = super.createForSubscriptionId(subscriptionId);
            return registered != null ? registered : real;
        }
    }
}
