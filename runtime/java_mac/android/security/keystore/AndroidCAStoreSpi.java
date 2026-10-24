package android.security.keystore;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * The {@code "AndroidCAStore"} KeyStore type: the device's SYSTEM CA trust store.
 *
 * On a device this is Conscrypt's TrustedCertificateKeyStoreSpi, registered under
 * the literal type name "AndroidCAStore", and apps reach it as
 * {@code KeyStore.getInstance("AndroidCAStore")} to enumerate or pin the system
 * root CAs. Without it that call throws
 * {@code KeyStoreException: AndroidCAStore not found}.
 *
 * MEASURED on Instagram 445: Facebook's lacrima crash reporter builds its TLS
 * stack on the Lacrima_sender thread and does exactly that lookup; the throw was
 * wrapped in an AssertionError, uncaught, so the app's AOSP-style
 * KillApplicationHandler ended the process with System.exit(10). Generic -- this
 * is the standard way to read the system trust anchors, so any app doing cert
 * pinning or trust inspection needs it.
 *
 * We back it with the HOST's real trust anchors rather than inventing a set: the
 * default {@link TrustManagerFactory} initialised with a null KeyStore yields the
 * JDK's own CA list, which on this platform IS the machine's trusted set. That
 * keeps the answer honest (the app sees the CAs that will actually validate its
 * connections) instead of an empty store that would silently fail every pin.
 *
 * READ-ONLY, like the device's: the system trust store is not app-writable, so
 * the mutating operations throw {@link KeyStoreException} as Conscrypt's does.
 */
public class AndroidCAStoreSpi extends KeyStoreSpi {

    /** Built once on first use; an in-memory store holding the host CA set. */
    private KeyStore delegate;

    private synchronized KeyStore delegate() {
        if (delegate != null) return delegate;
        KeyStore ks;
        try {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);          // null => the platform default CAs
            int n = 0;
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (!(tm instanceof X509TrustManager)) continue;
                for (X509Certificate c : ((X509TrustManager) tm).getAcceptedIssuers()) {
                    // Conscrypt aliases system anchors "system:<n>"; the exact form
                    // is not contractual, only that each anchor has a stable alias.
                    ks.setCertificateEntry("system:" + (n++), c);
                }
            }
            com.macapk.loader.NativeLog.i("AndroidCAStore",
                    "system trust store built from the host's CA set (" + n + " anchors)");
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.w("AndroidCAStore",
                    "could not build the system trust store (" + t + "); serving it empty");
            try { ks = KeyStore.getInstance(KeyStore.getDefaultType()); ks.load(null, null); }
            catch (Throwable t2) { ks = null; }
        }
        delegate = ks;
        return delegate;
    }

    @Override public Key engineGetKey(String alias, char[] password) { return null; }

    @Override public Certificate[] engineGetCertificateChain(String alias) {
        Certificate c = engineGetCertificate(alias);
        return c == null ? null : new Certificate[] { c };
    }

    @Override public Certificate engineGetCertificate(String alias) {
        KeyStore d = delegate();
        if (d == null || alias == null) return null;
        try { return d.getCertificate(alias); } catch (Throwable t) { return null; }
    }

    @Override public Date engineGetCreationDate(String alias) {
        KeyStore d = delegate();
        if (d == null || alias == null) return null;
        try { return d.getCreationDate(alias); } catch (Throwable t) { return null; }
    }

    @Override public Enumeration<String> engineAliases() {
        KeyStore d = delegate();
        if (d == null) return Collections.emptyEnumeration();
        try { return d.aliases(); } catch (Throwable t) { return Collections.emptyEnumeration(); }
    }

    @Override public boolean engineContainsAlias(String alias) {
        KeyStore d = delegate();
        if (d == null || alias == null) return false;
        try { return d.containsAlias(alias); } catch (Throwable t) { return false; }
    }

    @Override public int engineSize() {
        KeyStore d = delegate();
        if (d == null) return 0;
        try { return d.size(); } catch (Throwable t) { return 0; }
    }

    @Override public boolean engineIsKeyEntry(String alias) { return false; }

    @Override public boolean engineIsCertificateEntry(String alias) {
        return engineContainsAlias(alias);
    }

    @Override public String engineGetCertificateAlias(Certificate cert) {
        KeyStore d = delegate();
        if (d == null || cert == null) return null;
        try { return d.getCertificateAlias(cert); } catch (Throwable t) { return null; }
    }

    // ---- mutation: the system trust store is not app-writable ----
    @Override public void engineSetKeyEntry(String a, Key k, char[] p, Certificate[] c)
            throws java.security.KeyStoreException {
        throw new java.security.KeyStoreException("AndroidCAStore is read-only");
    }

    @Override public void engineSetKeyEntry(String a, byte[] k, Certificate[] c)
            throws java.security.KeyStoreException {
        throw new java.security.KeyStoreException("AndroidCAStore is read-only");
    }

    @Override public void engineSetCertificateEntry(String a, Certificate c)
            throws java.security.KeyStoreException {
        throw new java.security.KeyStoreException("AndroidCAStore is read-only");
    }

    @Override public void engineDeleteEntry(String a) throws java.security.KeyStoreException {
        throw new java.security.KeyStoreException("AndroidCAStore is read-only");
    }

    /** load/store are no-ops: the anchors come from the platform, not a stream. */
    @Override public void engineStore(OutputStream stream, char[] password) { }

    @Override public void engineLoad(InputStream stream, char[] password) { delegate(); }
}
