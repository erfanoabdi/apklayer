package apklayer.host;

import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

/**
 * Android's TrustManagerImpl has {@code checkServerTrusted(chain, authType, host)}, which android.net.http.X509TrustManagerExtensions (and so Chromium's X509Util,
 * the WebView's certificate check) requires of the default trust manager; the JDK's has not ("Could not find suitable trust manager": no HTTPS page in a WebView).
 * This provider answers for the default TrustManagerFactory with the JDK's own trust managers wrapped to have that method (same roots, same verification).
 */
final class HostTrust extends Provider {
    private HostTrust() { super("ApklayerTrust", "1", "default trust managers with Android's host-aware check"); put("TrustManagerFactory.PKIX", Factory.class.getName()); put("TrustManagerFactory.X509", Factory.class.getName()); }

    static void install() { Security.insertProviderAt(new HostTrust(), 1); }

    /** The JDK's trust manager with the three-argument check Android's callers look for (reflectively, by name and signature). */
    public static final class Manager implements X509TrustManager {
        private final X509TrustManager delegate;
        Manager(X509TrustManager delegate) { this.delegate = delegate; }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { delegate.checkClientTrusted(chain, authType); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { delegate.checkServerTrusted(chain, authType); }
        public List<X509Certificate> checkServerTrusted(X509Certificate[] chain, String authType, String host) throws CertificateException {
            delegate.checkServerTrusted(chain, "UNKNOWN");  // not the key exchange Chromium names ("RSA" makes the JDK demand keyEncipherment, which Android's check does not)
            return Arrays.asList(chain);
        }
        @Override public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
    }

    public static final class Factory extends TrustManagerFactorySpi {
        private TrustManager[] managers = new TrustManager[0];
        private TrustManagerFactory jdk() throws NoSuchAlgorithmException {
            try { return TrustManagerFactory.getInstance("PKIX", "SunJSSE"); } catch (NoSuchProviderException none) { throw new NoSuchAlgorithmException(none); }
        }
        @Override protected void engineInit(KeyStore store) throws java.security.KeyStoreException {
            try { TrustManagerFactory f = jdk(); f.init(store); wrap(f); } catch (NoSuchAlgorithmException failure) { throw new java.security.KeyStoreException(failure); }
        }
        @Override protected void engineInit(ManagerFactoryParameters parameters) throws java.security.InvalidAlgorithmParameterException {
            try { TrustManagerFactory f = jdk(); f.init(parameters); wrap(f); } catch (NoSuchAlgorithmException failure) { throw new java.security.InvalidAlgorithmParameterException(failure); }
        }
        private void wrap(TrustManagerFactory f) {
            TrustManager[] own = f.getTrustManagers();
            managers = new TrustManager[own.length];
            for (int i = 0; i < own.length; i++) managers[i] = own[i] instanceof X509TrustManager x ? new Manager(x) : own[i];
        }
        @Override protected TrustManager[] engineGetTrustManagers() { return managers.clone(); }
    }
}
