package apklayer.host;

import android.content.pm.Signature;

/**
 * Signing certificates. An installed package carries the certificate its APK is signed with, read by the framework's own ApkSignatureVerifier as the
 * package manager does (apps check their own: Device Info HW finishes when it differs). The placeholder, a real parseable self-signed certificate, stands in for
 * the framework package "android" and for a package whose APK is not at hand.
 */
final class HostCertificate {
    private HostCertificate() {}

    private static Signature[] signatures;
    private static final java.util.Map<String, Signature[]> byPackage = new java.util.HashMap<>();

    /** The certificate of an installed package's base.apk (v3, v2 or v1 signature block); the placeholder when it cannot be read. */
    static synchronized Signature[] of(String packageName) {
        return byPackage.computeIfAbsent(packageName, name -> {
            String apps = System.getProperty("apklayer.apps");
            java.io.File apk = apps == null ? null : new java.io.File(new java.io.File(apps, name), "base.apk");
            if (apk == null || !apk.isFile()) return signatures();
            android.content.pm.parsing.result.ParseResult<android.content.pm.SigningDetails> read = android.util.apk.ApkSignatureVerifier.unsafeGetCertsWithoutVerification(
                    android.content.pm.parsing.result.ParseTypeImpl.forParsingWithoutPlatformCompat(), apk.getPath(), android.content.pm.SigningDetails.SignatureSchemeVersion.JAR);
            if (read.isSuccess() && read.getResult().getSignatures() != null && read.getResult().getSignatures().length > 0) return read.getResult().getSignatures();
            System.out.println("[apklayer] signing certificate of " + name + " not readable (" + read.getErrorMessage() + "): the placeholder");
            return signatures();
        });
    }

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
