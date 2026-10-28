package apklayer.host;

import android.content.pm.Signature;

/** The one signing certificate every installed package carries: a real, parseable self-signed certificate (apps fingerprint it with CertificateFactory). */
final class HostCertificate {
    private HostCertificate() {}

    private static Signature[] signatures;

    static synchronized Signature[] signatures() {
        if (signatures == null) {
            try {
                sun.security.tools.keytool.CertAndKeyGen generator = new sun.security.tools.keytool.CertAndKeyGen("EC", "SHA256withECDSA");
                generator.generate(256);
                java.security.cert.X509Certificate certificate = generator.getSelfCertificate(new sun.security.x509.X500Name("CN=apklayer"), 25L * 365 * 24 * 3600);
                signatures = new Signature[] {new Signature(certificate.getEncoded())};
            } catch (Exception failure) {
                throw new IllegalStateException("cannot make the package certificate", failure);
            }
        }
        return signatures;
    }
}
