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

    static void install(Context app) {
        Provider conscrypt;
        try {
            conscrypt = new com.android.org.conscrypt.OpenSSLProvider();  // Conscrypt.checkAvailability: loads libjavacrypto
        } catch (Throwable unavailable) {
            System.err.println("[apklayer] warning: Android's Conscrypt (AndroidOpenSSL) is not available (" + unavailable + "); keeping the JDK security providers");
            HostTrust.install();
            return;
        }
        try {
            android.security.net.config.NetworkSecurityConfigProvider.install(app);  // the app's network_security_config; inserts AndroidNSSP first
        } catch (Throwable noConfig) {
            System.err.println("[apklayer] warning: AndroidNSSP not installed (" + noConfig + ")");
        }
        Map<String, Provider> have = new LinkedHashMap<>();
        for (Provider p : Security.getProviders()) have.put(p.getName(), p);
        Provider sun = have.get("SUN");
        have.put(conscrypt.getName(), conscrypt);
        if (sun != null) have.put("CertPathProvider", new CertPath(sun));
        try { have.put("HarmonyJSSE", new com.android.org.conscrypt.JSSEProvider()); } catch (Throwable none) { System.err.println("[apklayer] warning: HarmonyJSSE: " + none); }
        List<Provider> order = new ArrayList<>();
        for (String name : new String[] {"AndroidNSSP", "AndroidOpenSSL", "CertPathProvider", "AndroidKeyStoreBCWorkaround", "BC", "HarmonyJSSE", "AndroidKeyStore"}) {
            Provider p = have.get(name);
            if (p != null) order.add(p);
        }
        // SunJSSE's XDHKeyExchange casts the key pair of KeyPairGenerator "XDH" to java.security.interfaces.XEC*: Conscrypt's X25519 keys are not, so the JDK's SunEC serves XDH/X25519 (last in the order)
        for (Object key : new ArrayList<>(conscrypt.keySet())) if (key.toString().contains("XDH") || key.toString().contains("X25519")) conscrypt.remove(key);
        // ... served under Conscrypt's name, so Security.getProviders() stays Android's list (SunEC itself is not registered)
        Provider ec = have.get("SunEC");
        if (ec != null) for (String type : new String[] {"KeyPairGenerator", "KeyAgreement", "KeyFactory"}) for (String alg : new String[] {"XDH", "X25519"}) {
            Provider.Service real = ec.getService(type, alg);
            if (real == null) continue;
            conscrypt.putService(new Provider.Service(conscrypt, type, alg, real.getClassName(), null, null) {
                @Override public Object newInstance(Object parameter) throws java.security.NoSuchAlgorithmException { return real.newInstance(parameter); }
                @Override public boolean supportsParameter(Object parameter) { return real.supportsParameter(parameter); }
            });
        }
        // libcore's security.properties: the JDK's defaults (SunX509 key managers, PKCS12) name algorithms Android's providers do not have
        Security.setProperty("ssl.KeyManagerFactory.algorithm", "PKIX");
        Security.setProperty("ssl.TrustManagerFactory.algorithm", "PKIX");
        Security.setProperty("keystore.type", "BKS");
        for (Provider p : Security.getProviders()) Security.removeProvider(p.getName());
        for (Provider p : order) Security.addProvider(p);
        // libcore's HttpsURLConnection verifies host names with okhttp's verifier; the JDK's default one refuses every name (it relies on its own TLS stack's check)
        try { javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(com.android.okhttp.internal.tls.OkHostnameVerifier.INSTANCE); }
        catch (Throwable none) { System.err.println("[apklayer] warning: default hostname verifier: " + none); }
        if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] security providers: " + order.stream().map(Provider::getName).toList());
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
}
