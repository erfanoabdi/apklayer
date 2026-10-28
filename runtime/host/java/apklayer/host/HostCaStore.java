package apklayer.host;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Enumeration;

/** KeyStore "AndroidCAStore": the system's trusted CA certificates (here the JDK's cacerts), read only. */
public final class HostCaStore extends KeyStoreSpi {
    private final KeyStore store;

    public HostCaStore() {
        KeyStore loaded = null;
        try {
            loaded = KeyStore.getInstance(KeyStore.getDefaultType());
            File cacerts = new File(System.getProperty("java.home"), "lib/security/cacerts");
            try (InputStream in = new FileInputStream(cacerts)) { loaded.load(in, "changeit".toCharArray()); }
        } catch (Exception unavailable) { System.out.println("[apklayer] AndroidCAStore: no system CA certificates: " + unavailable); }
        store = loaded;
    }

    @Override public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException { return null; }
    @Override public Certificate[] engineGetCertificateChain(String alias) { return null; }
    @Override public Certificate engineGetCertificate(String alias) { try { return store == null ? null : store.getCertificate(alias); } catch (KeyStoreException e) { return null; } }
    @Override public Date engineGetCreationDate(String alias) { try { return store == null ? null : store.getCreationDate(alias); } catch (KeyStoreException e) { return null; } }
    @Override public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException { throw new KeyStoreException("read only"); }
    @Override public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException { throw new KeyStoreException("read only"); }
    @Override public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException { throw new KeyStoreException("read only"); }
    @Override public void engineDeleteEntry(String alias) throws KeyStoreException { throw new KeyStoreException("read only"); }
    @Override public Enumeration<String> engineAliases() { try { return store == null ? java.util.Collections.emptyEnumeration() : store.aliases(); } catch (KeyStoreException e) { return java.util.Collections.emptyEnumeration(); } }
    @Override public boolean engineContainsAlias(String alias) { try { return store != null && store.containsAlias(alias); } catch (KeyStoreException e) { return false; } }
    @Override public int engineSize() { try { return store == null ? 0 : store.size(); } catch (KeyStoreException e) { return 0; } }
    @Override public boolean engineIsKeyEntry(String alias) { return false; }
    @Override public boolean engineIsCertificateEntry(String alias) { return engineContainsAlias(alias); }
    @Override public String engineGetCertificateAlias(Certificate cert) { try { return store == null ? null : store.getCertificateAlias(cert); } catch (KeyStoreException e) { return null; } }
    @Override public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException { /* read only */ }
    @Override public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException { /* always loaded */ }
}
