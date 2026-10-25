// android.net.wifi.WifiEnterpriseConfig -- the 802.1X/EAP half of a
// WifiConfiguration.
//
// A REAL, COMPLETE value type. Like WifiConfiguration (read that header), it
// is built entirely by the app -- EAP method, identity, password, certificates
// -- and every accessor here just returns what was set. No Wi-Fi stack is
// involved in describing an enterprise network; the stack is only needed to
// USE one, and that is WifiManager's problem, not this class's.
//
// The certificates are held as the app's own java.security objects, not
// re-encoded, so getCaCertificate() hands back the identical instance
// setCaCertificate() was given -- which is what AOSP's own getters do.
//
// Mirrors packages/modules/Wifi framework/java/android/net/wifi/
// WifiEnterpriseConfig.java; public surface and constant values confirmed
// against the android.jar API-30 reference in android-emulator/.
package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class WifiEnterpriseConfig implements Parcelable {

    public static final String EXTRA_WAPI_AS_CERTIFICATE_DATA =
            "android.net.wifi.extra.WAPI_AS_CERTIFICATE_DATA";
    public static final String EXTRA_WAPI_AS_CERTIFICATE_NAME =
            "android.net.wifi.extra.WAPI_AS_CERTIFICATE_NAME";
    public static final String EXTRA_WAPI_USER_CERTIFICATE_DATA =
            "android.net.wifi.extra.WAPI_USER_CERTIFICATE_DATA";
    public static final String EXTRA_WAPI_USER_CERTIFICATE_NAME =
            "android.net.wifi.extra.WAPI_USER_CERTIFICATE_NAME";

    public static final String WAPI_AS_CERTIFICATE = "WAPIAS_";
    public static final String WAPI_USER_CERTIFICATE = "WAPIUSR_";

    /** The outer EAP methods. */
    public static final class Eap {
        private Eap() {}

        public static final int NONE = -1;
        public static final int PEAP = 0;
        public static final int TLS = 1;
        public static final int TTLS = 2;
        public static final int PWD = 3;
        public static final int SIM = 4;
        public static final int AKA = 5;
        public static final int AKA_PRIME = 6;
        public static final int UNAUTH_TLS = 7;
        public static final int WAPI_CERT = 8;
    }

    /** The inner (phase-2) authentication methods. */
    public static final class Phase2 {
        private Phase2() {}

        public static final int NONE = 0;
        public static final int PAP = 1;
        public static final int MSCHAP = 2;
        public static final int MSCHAPV2 = 3;
        public static final int GTC = 4;
        public static final int SIM = 5;
        public static final int AKA = 6;
        public static final int AKA_PRIME = 7;
    }

    private int mEapMethod = Eap.NONE;
    private int mPhase2Method = Phase2.NONE;
    private String mIdentity;
    private String mAnonymousIdentity;
    private String mPassword;
    private String mSubjectMatch;
    private String mAltSubjectMatch;
    private String mDomainSuffixMatch;
    private String mRealm;
    private String mPlmn;
    private X509Certificate[] mCaCerts;
    private X509Certificate[] mClientCertificateChain;
    private PrivateKey mClientPrivateKey;

    public WifiEnterpriseConfig() {}

    /** AOSP's copy constructor. The certificate arrays are cloned so the copy
     *  cannot be reordered through the original's reference. */
    public WifiEnterpriseConfig(WifiEnterpriseConfig source) {
        if (source == null) {
            return;
        }
        mEapMethod = source.mEapMethod;
        mPhase2Method = source.mPhase2Method;
        mIdentity = source.mIdentity;
        mAnonymousIdentity = source.mAnonymousIdentity;
        mPassword = source.mPassword;
        mSubjectMatch = source.mSubjectMatch;
        mAltSubjectMatch = source.mAltSubjectMatch;
        mDomainSuffixMatch = source.mDomainSuffixMatch;
        mRealm = source.mRealm;
        mPlmn = source.mPlmn;
        mCaCerts = source.mCaCerts == null ? null : source.mCaCerts.clone();
        mClientCertificateChain = source.mClientCertificateChain == null
                ? null : source.mClientCertificateChain.clone();
        mClientPrivateKey = source.mClientPrivateKey;
    }

    /** AOSP validates the method rather than storing an unusable one. */
    public void setEapMethod(int eapMethod) {
        switch (eapMethod) {
            case Eap.PEAP: case Eap.PWD: case Eap.TTLS: case Eap.SIM:
            case Eap.AKA: case Eap.AKA_PRIME: case Eap.WAPI_CERT:
                mPhase2Method = Phase2.NONE;
                mEapMethod = eapMethod;
                return;
            case Eap.TLS: case Eap.UNAUTH_TLS:
                mEapMethod = eapMethod;
                return;
            default:
                throw new IllegalArgumentException("Unknown EAP method");
        }
    }

    public int getEapMethod() { return mEapMethod; }

    public void setPhase2Method(int phase2Method) {
        switch (phase2Method) {
            case Phase2.NONE: case Phase2.PAP: case Phase2.MSCHAP: case Phase2.MSCHAPV2:
            case Phase2.GTC: case Phase2.SIM: case Phase2.AKA: case Phase2.AKA_PRIME:
                mPhase2Method = phase2Method;
                return;
            default:
                throw new IllegalArgumentException("Unknown Phase 2 method");
        }
    }

    public int getPhase2Method() { return mPhase2Method; }

    public void setIdentity(String identity) { mIdentity = identity; }

    public String getIdentity() { return mIdentity == null ? "" : mIdentity; }

    public void setAnonymousIdentity(String anonymousIdentity) {
        mAnonymousIdentity = anonymousIdentity;
    }

    public String getAnonymousIdentity() {
        return mAnonymousIdentity == null ? "" : mAnonymousIdentity;
    }

    public void setPassword(String password) { mPassword = password; }

    public String getPassword() { return mPassword == null ? "" : mPassword; }

    /** AOSP rejects a non-CA certificate here rather than storing one that
     *  could never validate a server. */
    public void setCaCertificate(X509Certificate cert) {
        if (cert == null) {
            mCaCerts = null;
            return;
        }
        if (cert.getBasicConstraints() < 0) {
            throw new IllegalArgumentException("Not a CA certificate");
        }
        mCaCerts = new X509Certificate[] { cert };
    }

    public X509Certificate getCaCertificate() {
        return mCaCerts != null && mCaCerts.length > 0 ? mCaCerts[0] : null;
    }

    public void setCaCertificates(X509Certificate[] certs) {
        if (certs == null) {
            mCaCerts = null;
            return;
        }
        for (X509Certificate cert : certs) {
            if (cert == null || cert.getBasicConstraints() < 0) {
                throw new IllegalArgumentException("Not a CA certificate");
            }
        }
        mCaCerts = certs.clone();
    }

    public X509Certificate[] getCaCertificates() { return mCaCerts; }

    public void setClientKeyEntry(PrivateKey privateKey, X509Certificate clientCertificate) {
        setClientKeyEntryWithCertificateChain(privateKey,
                clientCertificate == null ? null : new X509Certificate[] { clientCertificate });
    }

    /** AOSP requires the leaf to be an END-ENTITY certificate and the rest of
     *  the chain to be CAs; storing a malformed chain would fail later and
     *  further from the mistake. */
    public void setClientKeyEntryWithCertificateChain(PrivateKey privateKey,
            X509Certificate[] clientCertificateChain) {
        if (clientCertificateChain != null && clientCertificateChain.length > 0) {
            if (clientCertificateChain[0].getBasicConstraints() != -1) {
                throw new IllegalArgumentException(
                        "First certificate in the chain must be a client end certificate");
            }
            for (int i = 1; i < clientCertificateChain.length; i++) {
                if (clientCertificateChain[i].getBasicConstraints() == -1) {
                    throw new IllegalArgumentException(
                            "All certificates following the first must be CA certificates");
                }
            }
        }
        mClientPrivateKey = privateKey;
        mClientCertificateChain = clientCertificateChain == null
                ? null : clientCertificateChain.clone();
    }

    public X509Certificate getClientCertificate() {
        return mClientCertificateChain != null && mClientCertificateChain.length > 0
                ? mClientCertificateChain[0] : null;
    }

    public X509Certificate[] getClientCertificateChain() { return mClientCertificateChain; }

    public PrivateKey getClientPrivateKey() { return mClientPrivateKey; }

    /** @deprecated on AOSP in favour of setDomainSuffixMatch. */
    @Deprecated
    public void setSubjectMatch(String subjectMatch) { mSubjectMatch = subjectMatch; }

    /** @deprecated on AOSP. */
    @Deprecated
    public String getSubjectMatch() { return mSubjectMatch == null ? "" : mSubjectMatch; }

    public void setAltSubjectMatch(String altSubjectMatch) { mAltSubjectMatch = altSubjectMatch; }

    public String getAltSubjectMatch() {
        return mAltSubjectMatch == null ? "" : mAltSubjectMatch;
    }

    public void setDomainSuffixMatch(String domain) { mDomainSuffixMatch = domain; }

    public String getDomainSuffixMatch() {
        return mDomainSuffixMatch == null ? "" : mDomainSuffixMatch;
    }

    public void setRealm(String realm) { mRealm = realm; }

    public String getRealm() { return mRealm == null ? "" : mRealm; }

    public void setPlmn(String plmn) { mPlmn = plmn; }

    public String getPlmn() { return mPlmn == null ? "" : mPlmn; }

    /** AOSP: the SIM-backed EAP methods. Pure classification of mEapMethod --
     *  note this says nothing about whether a SIM EXISTS (a Mac has none); it
     *  answers only "is this configuration SIM-based". */
    public boolean isAuthenticationSimBased() {
        return mEapMethod == Eap.SIM || mEapMethod == Eap.AKA || mEapMethod == Eap.AKA_PRIME;
    }

    @Override public String toString() {
        return "WifiEnterpriseConfig[eap=" + mEapMethod + " phase2=" + mPhase2Method
                + " identity=" + getIdentity() + "]";
    }

    @Override public int describeContents() { return 0; }

    /**
     * The certificates and the private key do NOT travel. AOSP marshals them
     * through the system keystore, which this tree has no equivalent of, and
     * writing a serialized PRIVATE KEY into a Parcel would be a worse answer
     * than omitting it. Nothing in this tree round-trips an enterprise config
     * (there is no Wi-Fi stack to send it to), so the omission is unobservable
     * -- but it is named here rather than left to be discovered.
     */
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEapMethod);
        dest.writeInt(mPhase2Method);
        dest.writeString(mIdentity);
        dest.writeString(mAnonymousIdentity);
        dest.writeString(mPassword);
        dest.writeString(mSubjectMatch);
        dest.writeString(mAltSubjectMatch);
        dest.writeString(mDomainSuffixMatch);
        dest.writeString(mRealm);
        dest.writeString(mPlmn);
    }

    public static final Creator<WifiEnterpriseConfig> CREATOR =
            new Creator<WifiEnterpriseConfig>() {
        @Override public WifiEnterpriseConfig createFromParcel(Parcel in) {
            WifiEnterpriseConfig c = new WifiEnterpriseConfig();
            c.mEapMethod = in.readInt();
            c.mPhase2Method = in.readInt();
            c.mIdentity = in.readString();
            c.mAnonymousIdentity = in.readString();
            c.mPassword = in.readString();
            c.mSubjectMatch = in.readString();
            c.mAltSubjectMatch = in.readString();
            c.mDomainSuffixMatch = in.readString();
            c.mRealm = in.readString();
            c.mPlmn = in.readString();
            return c;
        }

        @Override public WifiEnterpriseConfig[] newArray(int size) {
            return new WifiEnterpriseConfig[size];
        }
    };
}
