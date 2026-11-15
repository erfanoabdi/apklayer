package apklayer.host;

import android.content.Context;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Android's security providers, in Android's order: AndroidNSSP, AndroidOpenSSL (the framework's Conscrypt over its native libjavacrypto.so, BoringSSL),
 * CertPathProvider, AndroidKeyStoreBCWorkaround, BC, HarmonyJSSE (the default SSLContext, TrustManagerFactory, KeyManagerFactory and the AndroidCAStore
 * KeyStore over /system/etc/security/cacerts), AndroidKeyStore. The JDK's own providers (SUN, SunJSSE ...) are not in Android's list and leave it.
 * Conscrypt's Java classes are the framework's; its natives load through NativeLoader like any guest library (runtime/patches NativeCryptoJni replaces
 * the System.loadLibrary HotSpot cannot satisfy: the library is bionic's). If Conscrypt cannot load, the providers stay as they were (HostTrust's), with one warning.
 */
final class HostProviders {
    private HostProviders() {}

    private static Boolean early;  // installEarly ran: true with Android's providers, false with the JDK's (HostTrust)

    /**
     * Android's providers before the app's code, as a device has them from zygote: an Application's onCreate that installs a provider of its own
     * (Play services' ProviderInstaller: GmsCore_OpenSSL) takes its default trust manager then, and with the JDK's providers it kept SunJSSE's
     * X509TrustManagerImpl, whose checkServerTrusted(chain, authType, socket) Conscrypt calls reflectively and java.base does not open: every TLS
     * handshake of Play services failed (no check-in, no sign-in). AppHost and ComponentHost call it before the Application is created.
     */
    static synchronized void installEarly() {
        if (early != null) return;
        Provider conscrypt;
        try {
            conscrypt = new com.android.org.conscrypt.OpenSSLProvider();  // Conscrypt.checkAvailability: loads libjavacrypto
        } catch (Throwable unavailable) {
            System.err.println("[apklayer] warning: Android's Conscrypt (AndroidOpenSSL) is not available (" + unavailable + "); keeping the JDK security providers");
            HostTrust.install();
            early = false;
            return;
        }
        order(conscrypt);
        early = true;
    }

    /** The app's network_security_config (AndroidNSSP, first, as handleBindApplication installs it), after the providers (installEarly). */
    static void install(Context app) {
        installEarly();
        if (!early) return;
        try {
            android.security.net.config.NetworkSecurityConfigProvider.install(app);  // the app's network_security_config; inserts AndroidNSSP first
        } catch (Throwable noConfig) {
            System.err.println("[apklayer] warning: AndroidNSSP not installed (" + noConfig + ")");
        }
        if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] security providers: " + java.util.Arrays.stream(Security.getProviders()).map(Provider::getName).toList());
    }

    private static void order(Provider conscrypt) {
        Map<String, Provider> have = new LinkedHashMap<>();
        for (Provider p : Security.getProviders()) have.put(p.getName(), p);
        Provider sun = have.get("SUN");
        have.put(conscrypt.getName(), conscrypt);
        if (sun != null) have.put("CertPathProvider", new CertPath(sun));
        try { have.put("HarmonyJSSE", new HarmonyJsse(have.get("SunEC"), have.get("SunRsaSign"))); } catch (Throwable none) { System.err.println("[apklayer] warning: HarmonyJSSE: " + none); }
        List<Provider> order = new ArrayList<>();
        for (String name : new String[] {"AndroidOpenSSL", "CertPathProvider", "AndroidKeyStoreBCWorkaround", "BC", "HarmonyJSSE", "AndroidKeyStore"}) {
            Provider p = have.get(name);
            if (p != null) order.add(p);
        }
        // SunJSSE's XDHKeyExchange casts the key pair of KeyPairGenerator "XDH" to java.security.interfaces.XEC*: Conscrypt's X25519 keys are not, so the JDK's SunEC serves XDH/X25519 (not a registered provider: HarmonyJsse serves them)
        for (Object key : new ArrayList<>(conscrypt.keySet())) if (key.toString().contains("XDH") || key.toString().contains("X25519")) conscrypt.remove(key);
        // libcore's security.properties: the JDK's defaults (SunX509 key managers, PKCS12) name algorithms Android's providers do not have
        Security.setProperty("ssl.KeyManagerFactory.algorithm", "PKIX");
        Security.setProperty("ssl.TrustManagerFactory.algorithm", "PKIX");
        Security.setProperty("keystore.type", "BKS");
        // Android verifies SHA-1 signed jars (F-Droid's index.jar, Droid-ify: "must be signed by a single code signer"); the JDK denies SHA1 after 2019
        Security.setProperty("jdk.jar.disabledAlgorithms", "MD2, MD5, RSA keySize < 1024, DSA keySize < 1024");
        for (Provider p : Security.getProviders()) Security.removeProvider(p.getName());
        for (Provider p : order) Security.addProvider(p);
        // libcore's HttpsURLConnection verifies host names with okhttp's verifier; the JDK's default one refuses every name (it relies on its own TLS stack's check)
        try { javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(com.android.okhttp.internal.tls.OkHostnameVerifier.INSTANCE); }
        catch (Throwable none) { System.err.println("[apklayer] warning: default hostname verifier: " + none); }
    }

    /** Android's CertPathProvider (libcore's sun.security.provider.CertPathProvider): CertPathBuilder/Validator PKIX and the Collection CertStore, which are the JDK's own certpath classes. */
    static final class CertPath extends Provider {
        CertPath(Provider jdk) {
            super("CertPathProvider", "1.0", "Provider of CertPathBuilder and CertPathValidator");
            for (String[] s : new String[][] {{"CertPathBuilder", "PKIX"}, {"CertPathValidator", "PKIX"}, {"CertStore", "Collection"}, {"CertStore", "com.sun.security.IndexedCollection"}}) {
                Provider.Service real = jdk.getService(s[0], s[1]);
                if (real == null) continue;
                putService(new Provider.Service(this, s[0], s[1], real.getClassName(), null, null) {
                    @Override public Object newInstance(Object parameter) throws java.security.NoSuchAlgorithmException { return real.newInstance(parameter); }
                    @Override public boolean supportsParameter(Object parameter) { return real.supportsParameter(parameter); }
                });
            }
        }
    }

    /** HarmonyJSSE (libcore's JSSEProvider, final: its services are forwarded) that also serves SunEC's XDH/X25519 and SunRsaSign's RSASSA-PSS, so that they stay out of Security.getProviders() (Android has none).
     *  SunJSSE (an app's own TLS stack) offers the rsa_pss_* signature schemes only if RSASSA-PSS resolves: without it a server with an RSA certificate (f-droid.org) cannot do TLS 1.3 and answers handshake_failure. */
    static final class HarmonyJsse extends Provider {
        HarmonyJsse(Provider ec, Provider rsa) {
            super("HarmonyJSSE", "1.0", new com.android.org.conscrypt.JSSEProvider().getInfo());
            java.util.List<Provider.Service> real = new ArrayList<>(new com.android.org.conscrypt.JSSEProvider().getServices());
            if (ec != null) for (String type : new String[] {"KeyPairGenerator", "KeyAgreement", "KeyFactory"}) for (String alg : new String[] {"XDH", "X25519"}) real.add(ec.getService(type, alg));
            if (rsa != null) for (String type : new String[] {"Signature", "KeyFactory", "KeyPairGenerator", "AlgorithmParameters"}) real.add(rsa.getService(type, "RSASSA-PSS"));
            for (Provider.Service r : real) {
                if (r == null) continue;
                putService(new Provider.Service(this, r.getType(), r.getAlgorithm(), r.getClassName(), null, null) {
                    @Override public Object newInstance(Object parameter) throws java.security.NoSuchAlgorithmException { return r.newInstance(parameter); }
                    @Override public boolean supportsParameter(Object parameter) { return r.supportsParameter(parameter); }
                });
            }
        }
    }
}
