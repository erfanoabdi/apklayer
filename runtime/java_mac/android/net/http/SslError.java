package android.net.http;

import java.security.cert.X509Certificate;

// android.net.http.SslError (AOSP API-1).
// Passed to WebViewClient.onReceivedSslError together with an SslErrorHandler.
// The type must resolve so the (P0) onReceivedSslError signature is loadable
// even before a real web engine drives it. Mirrors the public API from
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/net/http/SslError.java
public class SslError {

    public static final int SSL_NOTYETVALID = 0;
    public static final int SSL_EXPIRED = 1;
    public static final int SSL_IDMISMATCH = 2;
    public static final int SSL_UNTRUSTED = 3;
    public static final int SSL_DATE_INVALID = 4;
    public static final int SSL_INVALID = 5;
    @Deprecated
    public static final int SSL_MAX_ERROR = 6;

    // Bitmask of accumulated errors (one bit per SSL_* code).
    private int errors;
    private final SslCertificate certificate;
    private final String url;

    @Deprecated
    public SslError(int error, SslCertificate certificate) {
        this(error, certificate, "");
    }

    @Deprecated
    public SslError(int error, X509Certificate certificate) {
        this(error, new SslCertificate(certificate), "");
    }

    public SslError(int error, SslCertificate certificate, String url) {
        addError(error);
        this.certificate = certificate;
        this.url = url == null ? "" : url;
    }

    public SslError(int error, X509Certificate certificate, String url) {
        this(error, new SslCertificate(certificate), url);
    }

    public SslCertificate getCertificate() { return certificate; }

    public String getUrl() { return url; }

    public boolean addError(int error) {
        boolean valid = (error >= SSL_NOTYETVALID) && (error < SSL_MAX_ERROR);
        if (valid) {
            errors |= (0x1 << error);
        }
        return valid;
    }

    public boolean hasError(int error) {
        boolean valid = (error >= SSL_NOTYETVALID) && (error < SSL_MAX_ERROR);
        if (valid) {
            return (errors & (0x1 << error)) != 0;
        }
        return false;
    }

    // AOSP returns the most-severe error currently set, which is the highest
    // SSL_* code present in the bitmask.
    public int getPrimaryError() {
        if (errors != 0) {
            for (int error = SSL_MAX_ERROR - 1; error >= 0; --error) {
                if ((errors & (0x1 << error)) != 0) {
                    return error;
                }
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        return "primary error: " + getPrimaryError()
                + " certificate: " + getCertificate()
                + " on URL: " + getUrl();
    }
}
