// android.security.NetworkSecurityPolicy -- the runtime view of the
// app's Network Security Config. Previously an empty stub, so any call
// (OkHttp, Conscrypt, WebView, analytics SDKs all probe
// isCleartextTrafficPermitted before an HTTP request) threw
// NoSuchMethodError.
//
// Mac.apk does not enforce a Network Security Config, and the games /
// apps we host predate the API-28 cleartext-by-default-off change or
// declare cleartext explicitly. Mirroring the pre-Android-9 platform
// default (and the permissive behavior our HTTP path already has), we
// report cleartext as permitted. AOSP API surface mirrored exactly.
package android.security;

public class NetworkSecurityPolicy {
    private static final NetworkSecurityPolicy INSTANCE = new NetworkSecurityPolicy();

    public static NetworkSecurityPolicy getInstance() { return INSTANCE; }

    public NetworkSecurityPolicy() {}

    /** Whether cleartext (HTTP) traffic is permitted application-wide. */
    public boolean isCleartextTrafficPermitted() { return true; }

    /** Whether cleartext traffic is permitted for the given host. */
    public boolean isCleartextTrafficPermitted(String hostname) { return true; }

    /** Whether Certificate Transparency verification is required for the given
     *  hostname (API 36, android.security.certificate_transparency_configuration).
     *  AOSP reads the app's Network Security Config; ours declares no CT policy
     *  and the platform default is that CT is NOT required, so false is the
     *  faithful answer. Note this reports what the app's CONFIG demands -- it
     *  does not weaken any verification that the TLS stack performs anyway. */
    public boolean isCertificateTransparencyVerificationRequired(String hostname) {
        return false;
    }

    /** Deprecated test hook on real Android; accept and ignore. */
    public void setCleartextTrafficPermitted(boolean permitted) {}
}
