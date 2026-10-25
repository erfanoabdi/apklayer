// com.macapk.gms.PlatformSignature -- Mac.apk's own platform signing identity.
//
// WHY THIS EXISTS AT ALL. When the GMS layer is on, Mac.apk reports a
// com.android.vending package as installed, because it really does host a
// Play-Licensing service. Any package a PackageManager reports has a signing
// certificate, and an app is entitled to ask for it. There are exactly three
// things we could hand back and only one of them is acceptable:
//
//   1. Google's real certificate bytes. FORBIDDEN. CLAUDE.md's scope section is
//      explicit, and it stays forbidden even though those bytes are public and
//      no private key is involved: presenting Google's signing identity for a
//      package whose code is OURS is the forging line, whatever the provenance
//      of the bytes. This is the whole reason signature-gated apps are a
//      principled ceiling for us rather than a TODO.
//   2. An arbitrary non-certificate byte array. Not a forgery, but a lie of a
//      different kind: `CertificateFactory.generateCertificate` on it throws,
//      so an app that inspects signatures the ordinary way gets a crash instead
//      of an answer, and the answer it should get -- "signed by someone who is
//      not Google" -- is one we are perfectly happy to give.
//   3. A REAL self-signed certificate that says Mac.apk. This one.
//
// So we generate an ordinary RSA-2048 / SHA256withRSA self-signed X.509v3
// certificate, once, and persist it. It parses, it chains to itself, its subject
// says CN=Mac.apk Platform, and it will never equal Google's. An app that
// byte-compares against Google's embedded cert correctly concludes this is not
// Google -- which is the honest answer, and the one that keeps us on the right
// side of the line.
//
// WHY WE HAND-ROLL THE DER. The JDK exposes no public certificate-generation
// API (sun.security.x509 is internal and --release 21 will not link it), and
// BouncyCastle's builder lives in bcpkix, which we do not bundle -- bcprov alone
// only offers the long-deprecated org.bouncycastle.x509 generator. A v3
// self-signed certificate is a small, fully specified structure, so rung 3 of
// the decision ladder ("build the tooling") costs less than vendoring a jar we
// would then have to bundle and audit. RFC 5280 s4.1 is the whole spec:
//
//   Certificate  ::= SEQUENCE { tbsCertificate, signatureAlgorithm, signatureValue }
//   TBSCertificate ::= SEQUENCE { [0] version, serialNumber, signature, issuer,
//                                 validity, subject, subjectPublicKeyInfo,
//                                 [3] extensions }
//
// The one piece we do NOT hand-encode is SubjectPublicKeyInfo: PublicKey
// .getEncoded() on an RSA key returns exactly that structure in DER already
// (X.509 encoding is its documented format), so it is spliced in verbatim.
package com.macapk.gms;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;

public final class PlatformSignature {

    private static final String SUBJECT_CN = "Mac.apk Platform";
    private static final String SUBJECT_O  = "Mac.apk";

    private static final Object LOCK = new Object();
    private static byte[] sCertDer;

    private PlatformSignature() {}

    /** The platform certificate in DER, generating it on first use. Never null:
     *  a generation failure would leave a reported package with no signature at
     *  all, and PackageInfo.signatures being null is the exact NPE trap the
     *  browser-package comment in MacApkPackageManager warns about. */
    public static byte[] certificateDer() {
        synchronized (LOCK) {
            if (sCertDer != null) return sCertDer;
            byte[] der = load();
            if (der == null) {
                der = generateAndPersist();
            }
            sCertDer = der;
            return der;
        }
    }

    /** The DER wrapped as the type PackageManager hands out. */
    public static android.content.pm.Signature signature() {
        return new android.content.pm.Signature(certificateDer());
    }

    // ===================================================================
    // Persistence
    // ===================================================================

    private static File certFile() { return new File(GmsStore.dir(), "platform.cer"); }
    private static File keyFile()  { return new File(GmsStore.dir(), "platform.key"); }

    private static byte[] load() {
        try {
            File c = certFile();
            if (c.isFile() && c.length() > 0) return Files.readAllBytes(c.toPath());
        } catch (Throwable ignored) {}
        return null;
    }

    private static byte[] generateAndPersist() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
            KeyPair kp = kpg.generateKeyPair();
            byte[] der = selfSign(kp);

            try {
                File dir = GmsStore.dir();
                if (!dir.isDirectory()) Files.createDirectories(dir.toPath());
                Files.write(certFile().toPath(), der);
                // The PRIVATE key is a credential: 0600, and written after the
                // file exists so it is never briefly world-readable. We keep it
                // only so the identity is STABLE across runs -- an app that
                // remembers a signature must keep seeing the same one, exactly
                // as it would for a real installed package.
                File k = keyFile();
                if (!k.exists()) Files.createFile(k.toPath());
                Files.setPosixFilePermissions(k.toPath(), java.util.EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
                Files.write(k.toPath(), kp.getPrivate().getEncoded());
            } catch (Throwable t) {
                warn("generated a platform certificate but could not persist it: " + t
                     + " -- it will differ on the next launch");
            }
            return der;
        } catch (Throwable t) {
            warn("could not generate a platform certificate: " + t);
            // Last resort. NOT a certificate and deliberately not shaped like
            // one: a parser must fail cleanly rather than half-succeed, and the
            // string says exactly what happened if it ever shows up in a dump.
            return "Mac.apk platform certificate unavailable".getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Reload the persisted private key, for a caller that needs to sign with
     *  the platform identity later (nothing does yet; kept because generating a
     *  key and discarding the ability to use it would be the strange choice). */
    public static PrivateKey privateKey() {
        try {
            File k = keyFile();
            if (!k.isFile()) return null;
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(k.toPath())));
        } catch (Throwable t) {
            return null;
        }
    }

    // ===================================================================
    // DER
    // ===================================================================

    private static byte[] selfSign(KeyPair kp) throws Exception {
        // Serial: positive, random, 64 bits. RFC 5280 s4.1.2.2 requires a
        // non-negative integer; a leading 0x00 is added by der() when the high
        // bit is set, which is what keeps it from encoding as negative.
        byte[] serial = new byte[8];
        new SecureRandom().nextBytes(serial);
        serial[0] &= 0x7F;
        if (serial[0] == 0) serial[0] = 1;

        long now = System.currentTimeMillis();
        byte[] validity = seq(
                utcTime(new Date(now - 24L * 3600 * 1000)),          // yesterday: clock skew
                utcTime(new Date(now + 30L * 365 * 24 * 3600 * 1000))); // ~30 years

        byte[] name = seq(
                set(seq(oid(OID_CN), utf8(SUBJECT_CN))),
                set(seq(oid(OID_O),  utf8(SUBJECT_O))));

        byte[] sigAlg = seq(oid(OID_SHA256_RSA), nul());
        byte[] spki   = kp.getPublic().getEncoded();   // already a DER SubjectPublicKeyInfo

        // basicConstraints: CA:TRUE, critical. A self-signed certificate that is
        // its own issuer must assert this or a strict path validator rejects it
        // (RFC 5280 s6.1.4) -- and a validator rejecting our certificate would
        // look to an app like a malformed package rather than a foreign signer.
        byte[] basicConstraints = seq(
                oid(OID_BASIC_CONSTRAINTS),
                bool(true),                                  // critical
                octet(seq(bool(true))));                     // BasicConstraints{cA=TRUE}
        byte[] extensions = tagged(3, seq(basicConstraints));

        byte[] tbs = seq(
                tagged(0, der(0x02, new byte[]{2})),         // version v3 (=2)
                der(0x02, serial),
                sigAlg,
                name,                                        // issuer == subject
                validity,
                name,
                spki,
                extensions);

        java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
        signer.initSign(kp.getPrivate());
        signer.update(tbs);
        byte[] sig = signer.sign();

        return seq(tbs, sigAlg, bitString(sig));
    }

    // OIDs, pre-encoded as OID CONTENT bytes (the 0x06 tag + length is added by oid()).
    // 2.5.4.3 (CN) and 2.5.4.10 (O): first byte is 40*2+5 = 0x55.
    private static final byte[] OID_CN = { 0x55, 0x04, 0x03 };
    private static final byte[] OID_O  = { 0x55, 0x04, 0x0A };
    // 2.5.29.19 basicConstraints
    private static final byte[] OID_BASIC_CONSTRAINTS = { 0x55, 0x1D, 0x13 };
    // 1.2.840.113549.1.1.11 sha256WithRSAEncryption: 40*1+2 = 0x2A, then base-128.
    private static final byte[] OID_SHA256_RSA =
            { 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B };

    /** tag + definite length + content. Long form kicks in at 128 bytes, which
     *  every real field here (the 2048-bit key, the signature) exceeds. */
    private static byte[] der(int tag, byte[] content) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(content.length + 8);
        o.write(tag);
        int len = content.length;
        if (len < 0x80) {
            o.write(len);
        } else {
            int n = (len < 0x100) ? 1 : (len < 0x10000) ? 2 : (len < 0x1000000) ? 3 : 4;
            o.write(0x80 | n);
            for (int i = n - 1; i >= 0; i--) o.write((len >>> (8 * i)) & 0xFF);
        }
        o.write(content, 0, content.length);
        return o.toByteArray();
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    private static byte[] seq(byte[]... items) { return der(0x30, cat(items)); }
    private static byte[] set(byte[]... items) { return der(0x31, cat(items)); }
    private static byte[] oid(byte[] content)  { return der(0x06, content); }
    private static byte[] nul()                { return new byte[] { 0x05, 0x00 }; }
    private static byte[] bool(boolean b)      { return new byte[] { 0x01, 0x01, (byte)(b ? 0xFF : 0x00) }; }
    private static byte[] octet(byte[] c)      { return der(0x04, c); }
    private static byte[] utf8(String s)       { return der(0x0C, s.getBytes(StandardCharsets.UTF_8)); }

    /** Context-specific, CONSTRUCTED ([n] EXPLICIT). 0xA0 = context | constructed. */
    private static byte[] tagged(int n, byte[] content) { return der(0xA0 | n, content); }

    /** BIT STRING with a leading "0 unused bits" octet, per X.690 s8.6.2.2. */
    private static byte[] bitString(byte[] bits) {
        byte[] c = new byte[bits.length + 1];
        c[0] = 0;
        System.arraycopy(bits, 0, c, 1, bits.length);
        return der(0x03, c);
    }

    /** UTCTime YYMMDDHHMMSSZ, always UTC. RFC 5280 s4.1.2.5.1 mandates UTCTime
     *  for dates through 2049 and GeneralizedTime after; our ~30-year validity
     *  from 2026 lands in 2056, so the notAfter is clamped to 2049-12-31 rather
     *  than silently encoding a year that UTCTime reads as 1956. */
    private static byte[] utcTime(Date d) {
        java.util.Calendar cal = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(d);
        if (cal.get(java.util.Calendar.YEAR) > 2049) {
            cal.set(2049, java.util.Calendar.DECEMBER, 31, 23, 59, 59);
        }
        String s = String.format("%02d%02d%02d%02d%02d%02dZ",
                cal.get(java.util.Calendar.YEAR) % 100,
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND));
        return der(0x17, s.getBytes(StandardCharsets.US_ASCII));
    }

    private static void warn(String m) {
        try { com.macapk.loader.NativeLog.w("Mac.apk.GMS", "PlatformSignature: " + m); }
        catch (Throwable t) { System.err.println("[PlatformSignature] " + m); }
    }

    /** Self-test: generate, then parse back with the JDK's own CertificateFactory.
     *  Run by debug/gms_selftest.sh. A certificate we cannot parse is worse than
     *  no certificate, and the failure would otherwise only surface inside an app. */
    public static String selfTest() {
        try {
            byte[] der = certificateDer();
            java.security.cert.CertificateFactory cf =
                    java.security.cert.CertificateFactory.getInstance("X.509");
            java.security.cert.X509Certificate cert =
                    (java.security.cert.X509Certificate) cf.generateCertificate(
                            new java.io.ByteArrayInputStream(der));
            cert.verify(cert.getPublicKey());     // self-signed: verifies against itself
            return "OK subject=" + cert.getSubjectX500Principal()
                 + " sigalg=" + cert.getSigAlgName()
                 + " serial=" + cert.getSerialNumber()
                 + " notAfter=" + cert.getNotAfter()
                 + " der=" + der.length + " bytes";
        } catch (Throwable t) {
            return "FAIL " + t;
        }
    }
}
