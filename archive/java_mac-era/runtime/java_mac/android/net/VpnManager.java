// android.net.VpnManager
//
// The API 30+ manager for platform (always-on / app-based) VPN profiles.
//
// Mac.apk has no VPN subsystem: there is no TUN device to hand an app, and
// nothing in the runtime routes packets. So NO profile is ever provisioned and
// NO session ever starts, and this class says so rather than accepting the
// request and going quiet.
//
// The exception used is AOSP's OWN documented one:
// startProvisionedVpnProfileSession() declares "@throws SecurityException
// exception if user or device settings prevent this VPN from being setup, or
// if user consent has not been granted"
// (frameworks/base/core/java/android/net/VpnManager.java:418-419). "Device
// settings prevent this VPN from being setup" describes this device exactly,
// and it is a failure every VPN app already handles, because user consent is
// routinely refused on real Android.
//
// Mirrors frameworks/base/core/java/android/net/VpnManager.java public API.
package android.net;

import android.content.Intent;

public class VpnManager {

    public static final int TYPE_VPN_NONE = -1;
    public static final int TYPE_VPN_SERVICE = 1;
    public static final int TYPE_VPN_PLATFORM = 2;
    public static final int TYPE_VPN_LEGACY = 3;

    public static final String ACTION_VPN_MANAGER_EVENT = "android.net.action.VPN_MANAGER_EVENT";
    public static final String CATEGORY_EVENT_ALWAYS_ON_STATE_CHANGED =
            "android.net.category.EVENT_ALWAYS_ON_STATE_CHANGED";
    public static final String CATEGORY_EVENT_DEACTIVATED_BY_USER =
            "android.net.category.EVENT_DEACTIVATED_BY_USER";
    public static final String CATEGORY_EVENT_IKE_ERROR =
            "android.net.category.EVENT_IKE_ERROR";
    public static final String CATEGORY_EVENT_NETWORK_ERROR =
            "android.net.category.EVENT_NETWORK_ERROR";

    public static final int ERROR_CLASS_NOT_RECOVERABLE = 1;
    public static final int ERROR_CLASS_RECOVERABLE = 2;
    public static final int ERROR_CODE_NETWORK_UNKNOWN_HOST = 0;
    public static final int ERROR_CODE_NETWORK_PROTOCOL_TIMEOUT = 1;
    public static final int ERROR_CODE_NETWORK_LOST = 2;
    public static final int ERROR_CODE_NETWORK_IO = 3;

    public static final String EXTRA_ERROR_CLASS = "android.net.extra.ERROR_CLASS";
    public static final String EXTRA_ERROR_CODE = "android.net.extra.ERROR_CODE";
    public static final String EXTRA_SESSION_KEY = "android.net.extra.SESSION_KEY";
    public static final String EXTRA_TIMESTAMP_MILLIS = "android.net.extra.TIMESTAMP_MILLIS";
    public static final String EXTRA_UNDERLYING_LINK_PROPERTIES =
            "android.net.extra.UNDERLYING_LINK_PROPERTIES";
    public static final String EXTRA_UNDERLYING_NETWORK = "android.net.extra.UNDERLYING_NETWORK";
    public static final String EXTRA_UNDERLYING_NETWORK_CAPABILITIES =
            "android.net.extra.UNDERLYING_NETWORK_CAPABILITIES";
    public static final String EXTRA_VPN_PROFILE_STATE = "android.net.extra.VPN_PROFILE_STATE";

    /** The message every throw below carries, so a log makes the cause obvious. */
    private static final String NO_VPN =
            "This device has no VPN subsystem: there is no tunnel interface to give the "
                    + "profile, so no VPN profile can be provisioned or started.";

    public VpnManager() {}

    /** AOSP provisionVpnProfile(): returns a consent Intent, or null if already
     *  consented. No VPN subsystem -> null. */
    public Intent provisionVpnProfile(PlatformVpnProfile profile) { return null; }

    /** A removal, so it cannot create a false expectation: there is nothing
     *  provisioned to delete, and deleting nothing is genuinely a no-op. */
    public void deleteProvisionedVpnProfile() {}

    /**
     * @throws SecurityException always, per AOSP's own documented failure for
     * "device settings prevent this VPN from being setup". Returning a
     * fabricated session key would tell the app a tunnel is up.
     */
    public String startProvisionedVpnProfileSession() {
        throw new SecurityException(NO_VPN);
    }

    /**
     * @deprecated on AOSP, where it simply delegates to
     * startProvisionedVpnProfileSession() -- so it throws for the same reason.
     * Keeping it silent while its replacement throws would mean an app's
     * choice of overload decided whether it learned the truth.
     */
    @Deprecated
    public void startProvisionedVpnProfile() {
        startProvisionedVpnProfileSession();
    }

    /** A teardown, so a no-op is honest: nothing is running. */
    public void stopProvisionedVpnProfile() {}

    /**
     * AOSP returns null when the calling app has no provisioned profile.
     * Nothing is ever provisioned here, so null is both the truth and AOSP's
     * own answer -- and the method is @Nullable, so callers handle it.
     */
    public VpnProfileState getProvisionedVpnProfileState() { return null; }
}
