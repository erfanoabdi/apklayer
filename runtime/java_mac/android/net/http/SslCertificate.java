package android.net.http;

import android.os.Bundle;

import java.security.cert.X509Certificate;
import java.util.Date;

// android.net.http.SslCertificate (AOSP API-1).
// Companion data type required by SslError, which WebViewClient.onReceivedSslError
// receives. We have no in-process web engine yet, but the type must resolve so
// the SSL error callback signature is loadable. Mirrors the public API from
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/net/http/SslCertificate.java
// AOSP fidelity: the deprecation usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("deprecation")
public class SslCertificate {

    private final DName issuedTo;
    private final DName issuedBy;
    private final Date validNotBefore;
    private final Date validNotAfter;
    private final X509Certificate x509Certificate;

    @Deprecated
    public SslCertificate(String issuedTo, String issuedBy, String validNotBefore, String validNotAfter) {
        this(issuedTo, issuedBy, parseDate(validNotBefore), parseDate(validNotAfter));
    }

    @Deprecated
    public SslCertificate(String issuedTo, String issuedBy, Date validNotBefore, Date validNotAfter) {
        this.issuedTo = new DName(issuedTo);
        this.issuedBy = new DName(issuedBy);
        this.validNotBefore = validNotBefore;
        this.validNotAfter = validNotAfter;
        this.x509Certificate = null;
    }

    public SslCertificate(X509Certificate certificate) {
        this.x509Certificate = certificate;
        if (certificate != null) {
            this.issuedTo = new DName(certificate.getSubjectX500Principal() == null
                    ? null : certificate.getSubjectX500Principal().getName());
            this.issuedBy = new DName(certificate.getIssuerX500Principal() == null
                    ? null : certificate.getIssuerX500Principal().getName());
            this.validNotBefore = certificate.getNotBefore();
            this.validNotAfter = certificate.getNotAfter();
        } else {
            this.issuedTo = new DName(null);
            this.issuedBy = new DName(null);
            this.validNotBefore = null;
            this.validNotAfter = null;
        }
    }

    public DName getIssuedTo() { return issuedTo; }

    public DName getIssuedBy() { return issuedBy; }

    @Deprecated
    public String getValidNotBefore() {
        return validNotBefore == null ? null : validNotBefore.toString();
    }

    public Date getValidNotBeforeDate() { return validNotBefore; }

    @Deprecated
    public String getValidNotAfter() {
        return validNotAfter == null ? null : validNotAfter.toString();
    }

    public Date getValidNotAfterDate() { return validNotAfter; }

    public X509Certificate getX509Certificate() { return x509Certificate; }

    @Override
    public String toString() {
        return "Issued to: " + issuedTo.getDName() + ";\n"
                + "Issued by: " + issuedBy.getDName() + ";\n";
    }

    // AOSP serializes the certificate into a Bundle so save/restoreState can
    // persist it; we provide the same round-trip shape.
    public static Bundle saveState(SslCertificate certificate) {
        if (certificate == null) return null;
        Bundle b = new Bundle();
        b.putString(ISSUED_TO, certificate.getIssuedTo().getDName());
        b.putString(ISSUED_BY, certificate.getIssuedBy().getDName());
        b.putString(VALID_NOT_BEFORE, certificate.getValidNotBefore());
        b.putString(VALID_NOT_AFTER, certificate.getValidNotAfter());
        return b;
    }

    public static SslCertificate restoreState(Bundle bundle) {
        if (bundle == null) return null;
        return new SslCertificate(
                bundle.getString(ISSUED_TO),
                bundle.getString(ISSUED_BY),
                parseDate(bundle.getString(VALID_NOT_BEFORE)),
                parseDate(bundle.getString(VALID_NOT_AFTER)));
    }

    private static final String ISSUED_TO = "issued-to";
    private static final String ISSUED_BY = "issued-by";
    private static final String VALID_NOT_BEFORE = "valid-not-before";
    private static final String VALID_NOT_AFTER = "valid-not-after";

    private static Date parseDate(String s) {
        if (s == null) return null;
        try {
            return new Date(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Distinguished-name helper, public nested type per AOSP. Parses the
    // common CN/O/OU/etc. fields out of an X.500 name string.
    public static class DName {
        private final String dName;
        private final String cName;
        private final String oName;
        private final String uName;

        public DName(String dName) {
            this.dName = dName == null ? "" : dName;
            this.cName = field(this.dName, "CN=");
            this.oName = field(this.dName, "O=");
            this.uName = field(this.dName, "OU=");
        }

        public String getDName() { return dName; }
        public String getCName() { return cName; }
        public String getOName() { return oName; }
        public String getUName() { return uName; }

        private static String field(String dn, String prefix) {
            for (String part : dn.split(",")) {
                String p = part.trim();
                if (p.startsWith(prefix)) {
                    return p.substring(prefix.length());
                }
            }
            return "";
        }
    }
}
