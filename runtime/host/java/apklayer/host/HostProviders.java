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
        for (Provider p : Security.getProviders()) Security.removeProvider(p.getName());
        for (Provider p : order) Security.addProvider(p);
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
