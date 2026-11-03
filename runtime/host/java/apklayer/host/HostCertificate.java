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
            String why;
            try {
                android.content.pm.parsing.result.ParseResult<android.content.pm.SigningDetails> read = android.util.apk.ApkSignatureVerifier.unsafeGetCertsWithoutVerification(
                        android.content.pm.parsing.result.ParseTypeImpl.forParsingWithoutPlatformCompat(), apk.getPath(), android.content.pm.SigningDetails.SignatureSchemeVersion.JAR);
                if (read.isSuccess() && read.getResult().getSignatures() != null && read.getResult().getSignatures().length > 0) return read.getResult().getSignatures();
                why = read.getErrorMessage();
            } catch (RuntimeException | LinkageError failure) { why = failure.toString(); }
            Signature[] v1 = jarSigner(apk);  // the framework's v1 path (StrictJarFile) needs libcore's natives
            if (v1 != null) return v1;
            System.out.println("[apklayer] signing certificate of " + name + " not readable (" + why + "): the placeholder");
            return signatures();
        });
    }

    /** A v1-only APK: the certificate in its META-INF signature block. shortcut: the block's first certificate (one signer without a chain, as APKs are signed); verify the chain if a multi-signer APK shows up. */
    private static Signature[] jarSigner(java.io.File apk) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apk)) {
            for (java.util.Enumeration<? extends java.util.zip.ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                java.util.zip.ZipEntry entry = e.nextElement();
                if (!entry.getName().matches("META-INF/[^/]+\\.(RSA|DSA|EC)")) continue;
                java.util.Collection<? extends java.security.cert.Certificate> certs = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificates(zip.getInputStream(entry));
                if (!certs.isEmpty()) return new Signature[] {new Signature(certs.iterator().next().getEncoded())};
            }
        } catch (java.io.IOException | java.security.GeneralSecurityException unreadable) { /* the placeholder */ }
        return null;
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
