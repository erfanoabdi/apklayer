// android.net.Ikev2VpnProfile
//
// An IKEv2/IPsec VPN profile built via its Builder.
//
// A REAL, COMPLETE value type: every accessor below returns exactly what the
// app's own Builder put in, so an app that configures a profile and reads it
// back behaves the same on Mac.apk as on Android. None of that is a device
// capability -- it is ordinary object state.
//
// What is honestly absent is the VPN SUBSYSTEM that would act on the profile:
// VpnManager.provisionVpnProfile stores nothing and
// startProvisionedVpnProfileSession() throws, because there is no tunnel
// interface on this host (see VpnManager for the reasoning and the AOSP
// citation). So a profile can be built and inspected, and cannot be started.
//
// One deliberate gap, and it is not ours to close here: the
// Builder(IkeTunnelConnectionParams) path. AOSP derives the server address and
// user identity from the IKE session params; android.net.ipsec.ike is NOT
// implemented in this tree (it is an AOSP mainline module -- see that
// package's IkeSessionParams header), so a profile built that way reports a
// null server address rather than an invented one.
//
// Mirrors frameworks/base/core/java/android/net/Ikev2VpnProfile.java; public
// surface and Builder defaults confirmed against the same file and against
// frameworks/base/core/api/current.txt.
package android.net;

import android.net.ipsec.ike.IkeTunnelConnectionParams;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Ikev2VpnProfile extends PlatformVpnProfile {

    private final String mServerAddr;
    private final String mUserIdentity;
    private final byte[] mPresharedKey;
    private final X509Certificate mServerRootCaCert;
    private final String mUsername;
    private final String mPassword;
    private final PrivateKey mRsaPrivateKey;
    private final X509Certificate mUserCert;
    private final ProxyInfo mProxyInfo;
    private final List<String> mAllowedAlgorithms;
    private final boolean mIsBypassable;
    private final boolean mIsMetered;
    private final int mMaxMtu;
    private final boolean mAutomaticNattKeepaliveTimerEnabled;
    private final boolean mAutomaticIpVersionSelectionEnabled;
    private final IkeTunnelConnectionParams mIkeTunConnParams;

    private Ikev2VpnProfile(Builder b) {
        super(b.mType, b.mExcludeLocalRoutes, b.mRequiresInternetValidation);
        mServerAddr = b.mServerAddr;
        mUserIdentity = b.mUserIdentity;
        mPresharedKey = b.mPresharedKey == null ? null : b.mPresharedKey.clone();
        mServerRootCaCert = b.mServerRootCaCert;
        mUsername = b.mUsername;
        mPassword = b.mPassword;
        mRsaPrivateKey = b.mRsaPrivateKey;
        mUserCert = b.mUserCert;
        mProxyInfo = b.mProxyInfo;
        mAllowedAlgorithms = Collections.unmodifiableList(new ArrayList<>(b.mAllowedAlgorithms));
        mIsBypassable = b.mIsBypassable;
        mIsMetered = b.mIsMetered;
        mMaxMtu = b.mMaxMtu;
        mAutomaticNattKeepaliveTimerEnabled = b.mAutomaticNattKeepaliveTimerEnabled;
        mAutomaticIpVersionSelectionEnabled = b.mAutomaticIpVersionSelectionEnabled;
        mIkeTunConnParams = b.mIkeTunConnParams;
    }

    public String getServerAddr() { return mServerAddr; }

    public String getUserIdentity() { return mUserIdentity; }

    /** AOSP hands back a COPY so a caller cannot mutate the profile's key. */
    public byte[] getPresharedKey() {
        return mPresharedKey == null ? null : mPresharedKey.clone();
    }

    public X509Certificate getServerRootCaCert() { return mServerRootCaCert; }

    public String getUsername() { return mUsername; }

    public String getPassword() { return mPassword; }

    public PrivateKey getRsaPrivateKey() { return mRsaPrivateKey; }

    public X509Certificate getUserCert() { return mUserCert; }

    public ProxyInfo getProxyInfo() { return mProxyInfo; }

    public List<String> getAllowedAlgorithms() { return mAllowedAlgorithms; }

    public boolean isBypassable() { return mIsBypassable; }

    public boolean isMetered() { return mIsMetered; }

    public int getMaxMtu() { return mMaxMtu; }

    public boolean isAutomaticNattKeepaliveTimerEnabled() {
        return mAutomaticNattKeepaliveTimerEnabled;
    }

    public boolean isAutomaticIpVersionSelectionEnabled() {
        return mAutomaticIpVersionSelectionEnabled;
    }

    /** Null unless the profile was built from IKE tunnel params, matching AOSP. */
    public IkeTunnelConnectionParams getIkeTunnelConnectionParams() { return mIkeTunConnParams; }

    public static final class Builder {
        private final String mServerAddr;
        private final String mUserIdentity;
        private final IkeTunnelConnectionParams mIkeTunConnParams;

        private int mType = -1;
        private byte[] mPresharedKey;
        private X509Certificate mServerRootCaCert;
        private String mUsername;
        private String mPassword;
        private PrivateKey mRsaPrivateKey;
        private X509Certificate mUserCert;
        private ProxyInfo mProxyInfo;
        private List<String> mAllowedAlgorithms = new ArrayList<>();
        // AOSP's Builder defaults, verbatim (Ikev2VpnProfile.java:806-814).
        private boolean mRequiresInternetValidation = false;
        private boolean mIsBypassable = false;
        private boolean mIsMetered = true;
        private int mMaxMtu = PlatformVpnProfile.MAX_MTU_DEFAULT;
        private boolean mExcludeLocalRoutes = false;
        private boolean mAutomaticNattKeepaliveTimerEnabled = false;
        private boolean mAutomaticIpVersionSelectionEnabled = false;

        public Builder(String serverAddr, String identity) {
            java.util.Objects.requireNonNull(serverAddr, "serverAddr was null");
            java.util.Objects.requireNonNull(identity, "identity was null");
            mServerAddr = serverAddr;
            mUserIdentity = identity;
            mIkeTunConnParams = null;
        }

        /**
         * AOSP leaves serverAddr and identity null on this path and derives
         * them from the IKE session params when the profile is used. We cannot
         * derive them (android.net.ipsec.ike is not implemented), so they stay
         * null -- an absent value rather than a made-up server address.
         */
        public Builder(IkeTunnelConnectionParams ikeTunConnParams) {
            java.util.Objects.requireNonNull(ikeTunConnParams, "ikeTunConnParams was null");
            mIkeTunConnParams = ikeTunConnParams;
            mServerAddr = null;
            mUserIdentity = null;
        }

        public Builder setAuthPsk(byte[] psk) {
            java.util.Objects.requireNonNull(psk, "psk was null");
            mType = TYPE_IKEV2_IPSEC_PSK;
            mPresharedKey = psk.clone();
            mUsername = null;
            mPassword = null;
            mRsaPrivateKey = null;
            mUserCert = null;
            mServerRootCaCert = null;
            return this;
        }

        public Builder setAuthUsernamePassword(String user, String pass,
                X509Certificate serverRootCa) {
            java.util.Objects.requireNonNull(user, "user was null");
            java.util.Objects.requireNonNull(pass, "pass was null");
            mType = TYPE_IKEV2_IPSEC_USER_PASS;
            mUsername = user;
            mPassword = pass;
            mServerRootCaCert = serverRootCa;
            mPresharedKey = null;
            mRsaPrivateKey = null;
            mUserCert = null;
            return this;
        }

        public Builder setAuthDigitalSignature(X509Certificate userCert, PrivateKey key,
                X509Certificate serverRootCa) {
            java.util.Objects.requireNonNull(userCert, "userCert was null");
            java.util.Objects.requireNonNull(key, "key was null");
            mType = TYPE_IKEV2_IPSEC_RSA;
            mUserCert = userCert;
            mRsaPrivateKey = key;
            mServerRootCaCert = serverRootCa;
            mPresharedKey = null;
            mUsername = null;
            mPassword = null;
            return this;
        }

        public Builder setBypassable(boolean isBypassable) {
            mIsBypassable = isBypassable;
            return this;
        }

        public Builder setProxy(ProxyInfo proxy) {
            mProxyInfo = proxy;
            return this;
        }

        public Builder setMaxMtu(int mtu) {
            // AOSP enforces the IPv6 minimum link MTU here rather than
            // silently accepting a value that cannot carry a tunnel.
            if (mtu < 1280) {
                throw new IllegalArgumentException("Max MTU must be at least 1280");
            }
            mMaxMtu = mtu;
            return this;
        }

        public Builder setMetered(boolean isMetered) {
            mIsMetered = isMetered;
            return this;
        }

        public Builder setAllowedAlgorithms(List<String> algorithmNames) {
            java.util.Objects.requireNonNull(algorithmNames, "algorithmNames was null");
            mAllowedAlgorithms = new ArrayList<>(algorithmNames);
            return this;
        }

        public Builder setRequiresInternetValidation(boolean requiresValidation) {
            mRequiresInternetValidation = requiresValidation;
            return this;
        }

        public Builder setLocalRoutesExcluded(boolean excludeLocalRoutes) {
            mExcludeLocalRoutes = excludeLocalRoutes;
            return this;
        }

        public Builder setAutomaticNattKeepaliveTimerEnabled(boolean enabled) {
            mAutomaticNattKeepaliveTimerEnabled = enabled;
            return this;
        }

        public Builder setAutomaticIpVersionSelectionEnabled(boolean enabled) {
            mAutomaticIpVersionSelectionEnabled = enabled;
            return this;
        }

        public Ikev2VpnProfile build() {
            return new Ikev2VpnProfile(this);
        }
    }
}
