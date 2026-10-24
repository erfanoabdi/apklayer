// android.net.PlatformVpnProfile
//
// Abstract base of the platform VPN profile types (Ikev2VpnProfile). Pure
// immutable value type -- every accessor here reports what the app's own
// Builder put in, so all of it behaves exactly as on Android. What Mac.apk
// lacks is a VPN subsystem to ACT on the profile; see VpnManager.
//
// Mirrors frameworks/base/core/java/android/net/PlatformVpnProfile.java; the
// public surface (including getTypeString() being FINAL on the base rather
// than abstract) is confirmed against frameworks/base/core/api/current.txt.
package android.net;

public abstract class PlatformVpnProfile {

    public static final int TYPE_IKEV2_IPSEC_USER_PASS = 6;
    public static final int TYPE_IKEV2_IPSEC_PSK = 7;
    public static final int TYPE_IKEV2_IPSEC_RSA = 8;

    /** AOSP's default tunnel MTU (PlatformVpnProfile.java:59). @hide there. */
    public static final int MAX_MTU_DEFAULT = 1360;

    private final int mType;
    private final boolean mExcludeLocalRoutes;
    private final boolean mRequiresInternetValidation;

    PlatformVpnProfile(int type, boolean excludeLocalRoutes, boolean requiresValidation) {
        mType = type;
        mExcludeLocalRoutes = excludeLocalRoutes;
        mRequiresInternetValidation = requiresValidation;
    }

    public final int getType() { return mType; }

    /** Whether traffic to the local network bypasses the VPN. */
    public final boolean areLocalRoutesExcluded() { return mExcludeLocalRoutes; }

    /** Whether the platform should validate Internet reachability over the VPN. */
    public final boolean isInternetValidationRequired() { return mRequiresInternetValidation; }

    /**
     * FINAL on AOSP, and the strings below are AOSP's verbatim
     * (PlatformVpnProfile.java:109-120) -- an app that displays this in its own
     * UI shows the same text it would on Android. It was abstract here, which
     * would have let a subclass return something else.
     */
    public final String getTypeString() {
        switch (mType) {
            case TYPE_IKEV2_IPSEC_USER_PASS:
                return "IKEv2/IPsec Username/Password";
            case TYPE_IKEV2_IPSEC_PSK:
                return "IKEv2/IPsec Preshared key";
            case TYPE_IKEV2_IPSEC_RSA:
                return "IKEv2/IPsec RSA Digital Signature";
            default:
                return "Unknown VPN profile type";
        }
    }
}
