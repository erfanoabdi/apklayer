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
    private static final java.util.Map<String, android.content.pm.SigningDetails> byPackage = new java.util.HashMap<>();

    /** The current certificate of an installed package's base.apk (v3, v2 or v1 signature block); the placeholder when it cannot be read. */
    static Signature[] of(String packageName) { return details(packageName).getSignatures(); }

    /** The signers of an installed package's base.apk with its v3 rotation history (SigningDetails as the package manager keeps them); the placeholder when unreadable. */
    static synchronized android.content.pm.SigningDetails details(String packageName) {
        return byPackage.computeIfAbsent(packageName, name -> {
            String apps = System.getProperty("apklayer.apps");
            java.io.File apk = apps == null ? null : new java.io.File(new java.io.File(apps, name), "base.apk");
            android.content.pm.SigningDetails read = apk == null || !apk.isFile() ? null : details(apk);
            return read != null ? read : new android.content.pm.SigningDetails(signatures(), android.content.pm.SigningDetails.SignatureSchemeVersion.JAR, null, null);
        });
    }

    /**
     * PackageInfo.signatures, PackageInfoUtils.getDeprecatedSignatures: for an APK that rotated its key (v3 lineage) the oldest certificate, so callers unaware
     * of rotation keep matching the key they always knew (microG's list of Google apps); else the current signers.
     */
    static Signature[] legacy(android.content.pm.SigningDetails details) {
        return details.hasPastSigningCertificates() ? new Signature[] {details.getPastSigningCertificates()[0]} : details.getSignatures();
    }

    /** The current certificate an APK is signed with (v3, v2 or v1 signature block); null when it is unsigned or unreadable. */
    static Signature[] read(java.io.File apk) {
        android.content.pm.SigningDetails details = details(apk);
        return details == null ? null : details.getSignatures();
    }

    /** The signers of an APK and its rotation history (v3, v2 or v1 signature block); null when it is unsigned or unreadable. */
    private static android.content.pm.SigningDetails details(java.io.File apk) {
        String why;
        try {
            android.content.pm.parsing.result.ParseResult<android.content.pm.SigningDetails> read = android.util.apk.ApkSignatureVerifier.unsafeGetCertsWithoutVerification(
                    android.content.pm.parsing.result.ParseTypeImpl.forParsingWithoutPlatformCompat(), apk.getPath(), android.content.pm.SigningDetails.SignatureSchemeVersion.JAR);
            if (read.isSuccess() && read.getResult().getSignatures() != null && read.getResult().getSignatures().length > 0) return read.getResult();
            why = read.getErrorMessage();
        } catch (RuntimeException | LinkageError failure) { why = failure.toString(); }
        Signature[] v1 = jarSigner(apk);  // the framework's v1 path (StrictJarFile) needs libcore's natives
        if (v1 != null) return new android.content.pm.SigningDetails(v1, android.content.pm.SigningDetails.SignatureSchemeVersion.JAR, null, null);
        System.out.println("[apklayer] signing certificate of " + apk + " not readable (" + why + "): the placeholder");
        return null;
    }

    /** The SHA-256 of an APK's signing certificates, "" for an unsigned one (what install compares on an update). */
    static String digest(java.io.File apk) {
        Signature[] signers = read(apk);
        if (signers == null) return "";
        StringBuilder out = new StringBuilder();
        for (Signature s : signers) out.append(out.length() == 0 ? "" : ",").append(android.util.PackageUtils.computeSha256Digest(s.toByteArray()));
        return out.toString();
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

    /** Whether two installed packages are signed with the same certificate; an unsigned or unreadable APK (the placeholder) matches none. */
    static boolean sameSigner(String a, String b) {
        Signature[] first = of(a), second = of(b);
        return first != signatures() && second != signatures() && java.util.Arrays.equals(first, second);
    }

    /** The platform certificate: one per instance (every process of it sees the same "android" signer), made once in the instance's directory (beside accounts.json). */
    static synchronized Signature[] signatures() {
        if (signatures == null) {
            String storage = System.getProperty("apklayer.storage");
            java.nio.file.Path file = storage == null ? null : java.nio.file.Path.of(storage).resolveSibling("platform-certificate.der");
            try {
                if (file == null) return signatures = new Signature[] {new Signature(generate())};
                if (!java.nio.file.Files.isRegularFile(file)) {
                    // only the certificate is kept (nothing is signed with its key); a private 0600 file, linked into place so a racing process keeps the first one
                    java.nio.file.Path tmp = java.nio.file.Files.createTempFile(file.getParent(), "platform-certificate", ".tmp",
                            java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
                    try {
                        java.nio.file.Files.write(tmp, generate());
                        java.nio.file.Files.createLink(file, tmp);
                    } catch (java.nio.file.FileAlreadyExistsException another) { /* another process made it first */ }
                    finally { java.nio.file.Files.deleteIfExists(tmp); }
                }
                signatures = new Signature[] {new Signature(java.nio.file.Files.readAllBytes(file))};
            } catch (Exception failure) {
                throw new IllegalStateException("cannot make the package certificate " + file, failure);
            }
        }
        return signatures;
    }

    /** A real parseable self-signed certificate (EC P-256). */
    private static byte[] generate() throws Exception {
        sun.security.tools.keytool.CertAndKeyGen generator = new sun.security.tools.keytool.CertAndKeyGen("EC", "SHA256withECDSA");
        generator.generate(256);
        return generator.getSelfCertificate(new sun.security.x509.X500Name("CN=apklayer"), 25L * 365 * 24 * 3600).getEncoded();
    }
}
