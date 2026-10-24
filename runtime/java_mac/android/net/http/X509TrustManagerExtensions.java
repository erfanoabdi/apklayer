// android.net.http.X509TrustManagerExtensions -- AOSP API-17+
// wrapper around an X509TrustManager that adds APIs OkHttp uses for
// cert-chain cleaning (the AndroidPlatform / Android10Platform path).
// AOSP source: frameworks/base/core/java/android/net/http/
//   X509TrustManagerExtensions.java
//
// OkHttp's AndroidCertificateChainCleaner.buildIfSupported instantiates
// this with the platform's default X509TrustManager and then calls
// checkServerTrusted(chain, authType, host) to get back the cleaned
// chain it feeds to CertificatePinner. Without the class linking, every
// OkHttp client construction throws NoSuchMethodError before any HTTPS
// request ever happens.
//
// We are ON this path for every guest, not just Android-targeted ones:
// OkHttp selects its Android platform from
// `"Dalvik" == System.getProperty("java.vm.name")`, and Boot.java:407
// deliberately sets exactly that (for libGDX's SharedLibraryLoader --
// see the comment there). So this class is the chain cleaner for every
// OkHttp guest we run.
//
// =========================================================================
// WHY THIS IS A PKIX PATH BUILD AND NOT `tm.checkServerTrusted(chain,
// authType)` (rewritten 2026-08-13; the delegating version was WRONG and
// broke every certificate-pinning app on Mac.apk)
// =========================================================================
// Two divergences from Android, both of which broke pinning:
//
// 1. THE authType IS A PLACEHOLDER, NOT A FACT ABOUT THE CONNECTION.
//    OkHttp hardcodes it:
//      okhttp/src/androidMain/kotlin/okhttp3/internal/platform/android/
//      AndroidCertificateChainCleaner.kt
//        return x509TrustManagerExtensions.checkServerTrusted(
//            certificates, "RSA", hostname)
//    It can do that because Conscrypt IGNORES authType. In
//    conscrypt/common/src/main/java/org/conscrypt/TrustManagerImpl.java
//    the parameter is null-checked and then handed to
//    ExtendedKeyUsagePKIXCertPathChecker; it is never compared against
//    the end-entity certificate's KeyUsage.
//    The JDK is the opposite. sun.security.validator.EndEntityChecker:
//        private static final Collection<String> KU_SERVER_ENCRYPTION
//            = List.of("RSA");
//    and checkTLSServer() requires KU_KEY_ENCIPHERMENT for that authType,
//    throwing ValidatorException("KeyUsage does not allow key
//    encipherment"). Modern certificates are ECDSA with
//    `KeyUsage: critical, Digital Signature` and nothing else -- TLS 1.3
//    and every ECDHE suite only ever SIGN -- so delegating OkHttp's
//    fictional "RSA" into the JDK validator rejects a perfectly good
//    chain. MEASURED 2026-08-13 against auroraoss.com (EC leaf, GTS WE1):
//    delegating threw that exact ValidatorException, OkHttp's clean()
//    turned it into SSLPeerUnverifiedException, and CertificatePinner
//    reported "Certificate pinning failure! Peer certificate chain:"
//    with an EMPTY chain -- a validation failure wearing a pinning
//    failure's clothes.
//
// 2. AOSP RETURNS THE CHAIN INCLUDING THE TRUST ANCHOR; WE RETURNED THE
//    CHAIN AS PRESENTED. AOSP's javadoc: "the properly ordered chain used
//    for verification as a list of X509Certificates", and Conscrypt builds
//    it as `wholeChain` = the untrusted certs PLUS the trust anchors it
//    validated up to. A server is not required to send its root, and most
//    do not -- so an app that pins the ROOT (the common, rotation-proof
//    choice) could never match a pin against a chain that stops at the
//    intermediate. Returning Arrays.asList(chain) silently broke those
//    apps even when authType happened not to bite.
//
// So this implementation does what Conscrypt does: build and validate the
// certification path against the platform trust anchors with PKIX, and
// return leaf..anchor. That is not a relaxation of security:
//   - the real TLS validation, with the REAL key-exchange authType,
//     already ran inside JSSE during the handshake, before OkHttp ever
//     calls a chain cleaner; this call is the cleaning step;
//   - PKIX still verifies signatures, validity windows, basic
//     constraints, name constraints, CA KeyUsage and the JDK's disabled
//     -algorithm constraints (PKIXCertPathValidator installs
//     AlgorithmChecker itself), and we additionally enforce serverAuth
//     EKU on the leaf the way Conscrypt's
//     ExtendedKeyUsagePKIXCertPathChecker does;
//   - the ONLY check dropped is the authType->KeyUsage end-entity rule,
//     which cannot be applied correctly here because the caller does not
//     tell us the truth about authType, and which Android does not apply.
// Revocation is disabled to match Android (Conscrypt does no CRL/OCSP
// fetch on this path); leaving the JDK default ON would put a blocking
// network fetch inside every pinned TLS connection.
package android.net.http;

import java.security.InvalidAlgorithmParameterException;
import java.security.GeneralSecurityException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.X509TrustManager;

public class X509TrustManagerExtensions {
    /** OID 1.3.6.1.5.5.7.3.1 -- id-kp-serverAuth. */
    private static final String EKU_SERVER_AUTH = "1.3.6.1.5.5.7.3.1";
    /** OID 2.5.29.37.0 -- anyExtendedKeyUsage. */
    private static final String EKU_ANY = "2.5.29.37.0";

    /** The wrapped TrustManager. Supplies the trust anchors. Null only via
     *  the deprecated no-arg form, which AOSP does not have at all. */
    private final X509TrustManager tm;

    /** AOSP API-17 ctor. AOSP throws IllegalArgumentException when the
     *  underlying TM does not expose Conscrypt's TrustManagerImpl "extras"
     *  surface; OkHttp catches that and falls back to its own
     *  BasicCertificateChainCleaner. We accept any X509TrustManager,
     *  because the PKIX build below only needs its trust anchors. */
    public X509TrustManagerExtensions(X509TrustManager tm) {
        if (tm == null) {
            throw new IllegalArgumentException("X509TrustManager must not be null");
        }
        this.tm = tm;
    }

    /** Legacy no-arg form kept for the v1.0.92 auto-stub callers. AOSP has
     *  no such constructor. */
    public X509TrustManagerExtensions() {
        this.tm = null;
    }

    /** AOSP: validates {@code chain} against the platform truststore and
     *  returns "the properly ordered chain used for verification", i.e.
     *  leaf first through the trust anchor. Throws CertificateException if
     *  no such path exists -- OkHttp converts that into
     *  SSLPeerUnverifiedException.
     *
     *  {@code authType} is accepted and NOT used to select an end-entity
     *  KeyUsage rule; see the class header for why (Conscrypt ignores it
     *  too, and OkHttp passes a hardcoded "RSA"). {@code host} is likewise
     *  unused: Android consults it only for per-host network-security-config
     *  and CertPinManager entries, neither of which Mac.apk has, and
     *  hostname VERIFICATION is a separate step the caller already did. */
    public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,
                                                    String authType,
                                                    String host)
            throws CertificateException {
        if (tm == null) {
            // Was `return emptyList()`. That is the worst possible answer:
            // an empty "verified chain" reads to CertificatePinner as
            // "nothing matched", so a missing TrustManager surfaced as a
            // pinning failure instead of a trust failure. Fail loudly.
            throw new CertificateException(
                "X509TrustManagerExtensions constructed without an X509TrustManager");
        }
        if (chain == null || chain.length == 0) {
            // Matches the JDK/Conscrypt contract for an empty chain.
            throw new IllegalArgumentException("null or zero-length certificate chain");
        }
        try {
            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate ca : tm.getAcceptedIssuers()) {
                anchors.add(new TrustAnchor(ca, null));
            }
            if (anchors.isEmpty()) {
                // An app-supplied TrustManager (private CA, or a deliberately
                // permissive one) commonly reports NO accepted issuers, and we
                // cannot path-build without anchors. Its own policy is the
                // authority in that case, so delegate and hand back the chain
                // as presented -- exactly the pre-2026-08-13 behaviour, kept
                // so this rewrite cannot regress such an app. AOSP reaches the
                // same outcome by a different route: its constructor throws
                // IllegalArgumentException for a non-Conscrypt TrustManager
                // and OkHttp falls back to its own BasicCertificateChainCleaner.
                // The authType bug this class exists to fix cannot bite here,
                // because a TrustManager with no anchors is not running the
                // JDK's EndEntityChecker against the platform truststore.
                tm.checkServerTrusted(chain, authType);
                return new ArrayList<>(Arrays.asList(chain));
            }
            // Part of path validation, so it belongs on the PKIX branch only:
            // a custom TrustManager's own policy stays fully authoritative
            // above, which is what makes the fallback regression-free.
            checkServerEku(chain[0]);

            X509CertSelector target = new X509CertSelector();
            target.setCertificate(chain[0]);

            PKIXBuilderParameters params = new PKIXBuilderParameters(anchors, target);
            // Android does not fetch CRLs/OCSP here; the JDK default is ON,
            // which would add a blocking network round trip to every pinned
            // connection (and fail closed on a soft-fail responder).
            params.setRevocationEnabled(false);
            params.addCertStore(CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(Arrays.asList((Certificate[]) chain))));

            CertPathBuilderResult built =
                CertPathBuilder.getInstance("PKIX").build(params);

            List<X509Certificate> out = new ArrayList<>();
            for (Certificate c : built.getCertPath().getCertificates()) {
                out.add((X509Certificate) c);
            }
            // AOSP/Conscrypt return the anchor as part of the chain. The
            // CertPath deliberately excludes it, so append it -- this is what
            // lets an app that pins its ROOT match when the server sent only
            // leaf+intermediate.
            if (built instanceof PKIXCertPathBuilderResult) {
                TrustAnchor anchor = ((PKIXCertPathBuilderResult) built).getTrustAnchor();
                X509Certificate anchorCert = anchor != null ? anchor.getTrustedCert() : null;
                if (anchorCert != null && !out.contains(anchorCert)) out.add(anchorCert);
            }
            return out;
        } catch (CertificateException e) {
            throw e;
        } catch (InvalidAlgorithmParameterException e) {
            throw new CertificateException("PKIX parameters rejected: " + e, e);
        } catch (GeneralSecurityException e) {
            // CertPathBuilderException lands here: no path to a trust anchor.
            throw new CertificateException("could not build a trusted chain: " + e, e);
        }
    }

    /** Conscrypt applies ExtendedKeyUsagePKIXCertPathChecker to the
     *  end-entity certificate; PKIX path building does not check EKU on its
     *  own. Mirror the relevant half: if the leaf declares an EKU it must
     *  permit TLS server auth. A leaf with no EKU extension is unrestricted,
     *  which is what both Conscrypt and the JDK conclude. */
    private static void checkServerEku(X509Certificate leaf) throws CertificateException {
        List<String> eku = leaf.getExtendedKeyUsage();   // null == no extension
        if (eku == null || eku.isEmpty()) return;
        if (eku.contains(EKU_SERVER_AUTH) || eku.contains(EKU_ANY)) return;
        throw new CertificateException(
            "end-entity certificate is not valid for TLS server auth: " + eku);
    }

    /** AOSP API-34+ (@FlaggedApi FLAG_X509_EXTENSIONS_CERTIFICATE_TRANSPARENCY):
     *  the OCSP-stapling / Certificate-Transparency-SCT-aware overload.
     *  frameworks/base/core/java/android/net/http/X509TrustManagerExtensions.java
     *  folds ocspData/tlsSctData into the underlying TrustManagerImpl's
     *  verification when the conscrypt mainline module supports it. Mac.apk
     *  has no independent OCSP/SCT validation, so we ignore ocspData/tlsSctData
     *  and delegate to the 3-arg logic. */
    public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,
                                                    byte[] ocspData,
                                                    byte[] tlsSctData,
                                                    String authType,
                                                    String host)
            throws CertificateException {
        return checkServerTrusted(chain, authType, host);
    }

    /** AOSP API-23+: true if the same trust config (network-security-
     *  config) applies to both hostnames. We have no per-host trust
     *  configuration, so always-true matches the "default config for
     *  all hosts" semantic on JDK. */
    public boolean isSameTrustConfiguration(String hostname1, String hostname2) {
        return true;
    }

    /** AOSP API-24+: true if the given cert was installed by the
     *  user (vs. shipped in the system truststore). We don't track
     *  user-installed certs on Mac.apk; always false. */
    public boolean isUserAddedCertificate(X509Certificate cert) {
        return false;
    }
}
