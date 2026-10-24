package android.security.keystore;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

// MacApkKeyStoreBackend: the Mac.apk stand-in for Android's keystore
// service. On a device, AndroidKeyStoreSpi/KeyGeneratorSpi talk to the
// system keymaster daemon, which persists key blobs under
// /data/misc/keystore and survives app restarts. macOS has no keymaster,
// so this backend persists raw key material in an app-private file under
// the app's persistent data dir. The OBSERVABLE contract this preserves,
// and the one the androidx.security/Tink ecosystem depends on, is:
//   - keys generated via KeyGenerator("AES","AndroidKeyStore") are bound
//     to their KeyGenParameterSpec alias and visible to every
//     KeyStore.getInstance("AndroidKeyStore") instance in the process;
//   - keys PERSIST across launches (EncryptedSharedPreferences wraps its
//     Tink keyset with the master key at first run and must unwrap it
//     with the SAME key on every later run -- an in-memory-only store
//     would brick every encrypted pref file on the second launch);
//   - deleting the app's data dir removes the keys together with the
//     ciphertext they protect (the Android uninstall analogue).
// What it does NOT preserve is hardware-backed non-extractability; the
// returned keys are plain SecretKeySpec objects so the stock JCE
// providers can operate on them (which is precisely what lets Tink's
// AndroidKeystoreAesGcm Cipher.getInstance("AES/GCM/NoPadding") work
// without an Android keymaster-routing Cipher provider).
//
// All public methods are synchronized on the singleton: JCE hands out a
// fresh KeyStoreSpi per KeyStore.getInstance and the EOS/Tink path uses
// several concurrently.
final class MacApkKeyStoreBackend {
    private static final int MAGIC = 0x4D414B53;   // "MAKS"
    private static final int VERSION = 1;
    // Key PAIRS live in a SEPARATE file with its own magic/version. Deliberate:
    // keys.dat already holds every app's AES/HMAC material -- most importantly
    // the EncryptedSharedPreferences master key that wraps a Tink keyset -- and
    // widening its record format would force a version bump whose mismatch path
    // DISCARDS the file. Bricking every encrypted pref file is not an acceptable
    // price for adding a feature next to it, so key pairs get their own store.
    private static final int PAIR_MAGIC = 0x4D414B50;   // "MAKP"
    private static final int PAIR_VERSION = 1;

    private static final class Entry {
        final String algorithm;
        final byte[] key;
        final long created;
        Entry(String algorithm, byte[] key, long created) {
            this.algorithm = algorithm;
            this.key = key;
            this.created = created;
        }
    }

    /** A generated key pair: the PKCS#8 private key and the self-signed X.509
     *  certificate AOSP's keystore mints alongside it. */
    private static final class PairEntry {
        final String algorithm;     // "EC" / "RSA"
        final byte[] pkcs8;
        final byte[] certDer;
        final long created;
        PairEntry(String algorithm, byte[] pkcs8, byte[] certDer, long created) {
            this.algorithm = algorithm;
            this.pkcs8 = pkcs8;
            this.certDer = certDer;
            this.created = created;
        }
    }

    private final Map<String, PairEntry> pairs = new LinkedHashMap<>();
    private boolean pairsLoaded = false;

    private static final MacApkKeyStoreBackend INSTANCE =
        new MacApkKeyStoreBackend();
    private static volatile File sStorageDir;   // set by Boot via macapkConfigure

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean loaded = false;
    private boolean warnedUnconfigured = false;

    private MacApkKeyStoreBackend() {}

    static MacApkKeyStoreBackend get() { return INSTANCE; }

    static void configure(File dataDir) {
        sStorageDir = new File(dataDir, ".androidkeystore");
    }

    private File storageFile() {
        File dir = sStorageDir;
        if (dir == null) return null;
        return new File(dir, "keys.dat");
    }

    private File pairStorageFile() {
        File dir = sStorageDir;
        if (dir == null) return null;
        return new File(dir, "keypairs.dat");
    }

    private void ensurePairsLoaded() {
        if (pairsLoaded) return;
        pairsLoaded = true;
        File f = pairStorageFile();
        if (f == null || !f.isFile()) return;
        try (FileInputStream fis = new FileInputStream(f);
             DataInputStream in = new DataInputStream(fis)) {
            if (in.readInt() != PAIR_MAGIC || in.readInt() != PAIR_VERSION) return;
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                String alias = in.readUTF();
                String alg = in.readUTF();
                long created = in.readLong();
                byte[] pk = new byte[in.readInt()]; in.readFully(pk);
                byte[] cert = new byte[in.readInt()]; in.readFully(cert);
                pairs.put(alias, new PairEntry(alg, pk, cert, created));
            }
        } catch (IOException e) {
            System.err.println("[AndroidKeyStore] failed to read " + f + ": " + e);
            pairs.clear();
        }
    }

    private void persistPairs() {
        File f = pairStorageFile();
        if (f == null) return;
        File dir = f.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        restrictPermissions(dir, true);
        File tmp = new File(dir, "keypairs.dat.tmp");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp);
                 DataOutputStream out = new DataOutputStream(fos)) {
                out.writeInt(PAIR_MAGIC);
                out.writeInt(PAIR_VERSION);
                out.writeInt(pairs.size());
                for (Map.Entry<String, PairEntry> e : pairs.entrySet()) {
                    PairEntry p = e.getValue();
                    out.writeUTF(e.getKey());
                    out.writeUTF(p.algorithm);
                    out.writeLong(p.created);
                    out.writeInt(p.pkcs8.length);   out.write(p.pkcs8);
                    out.writeInt(p.certDer.length); out.write(p.certDer);
                }
                out.flush();
                fos.getFD().sync();
            }
            restrictPermissions(tmp, false);
            if (!tmp.renameTo(f)) {
                Files.move(tmp.toPath(), f.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[AndroidKeyStore] failed to write " + f + ": " + e);
            tmp.delete();
        }
    }

    synchronized void putKeyPair(String alias, String algorithm,
                                 byte[] pkcs8, byte[] certDer) {
        ensurePairsLoaded();
        pairs.put(alias, new PairEntry(algorithm, pkcs8.clone(), certDer.clone(),
                                       System.currentTimeMillis()));
        persistPairs();
    }

    synchronized boolean containsPair(String alias) {
        ensurePairsLoaded();
        return pairs.containsKey(alias);
    }

    synchronized PrivateKey getPrivateKey(String alias) {
        ensurePairsLoaded();
        PairEntry p = pairs.get(alias);
        if (p == null) return null;
        try {
            return KeyFactory.getInstance(p.algorithm)
                .generatePrivate(new PKCS8EncodedKeySpec(p.pkcs8));
        } catch (Exception e) {
            System.err.println("[AndroidKeyStore] cannot rebuild private key for "
                + alias + ": " + e);
            return null;
        }
    }

    synchronized X509Certificate getCertificate(String alias) {
        ensurePairsLoaded();
        PairEntry p = pairs.get(alias);
        if (p == null) return null;
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(p.certDer));
        } catch (Exception e) {
            System.err.println("[AndroidKeyStore] cannot rebuild certificate for "
                + alias + ": " + e);
            return null;
        }
    }

    synchronized Date getPairCreationDate(String alias) {
        ensurePairsLoaded();
        PairEntry p = pairs.get(alias);
        return p == null ? null : new Date(p.created);
    }

    // Lazy-load so the order of configure() vs the first JCE call does not
    // matter. Unconfigured (no Boot wiring, e.g. a unit harness) degrades
    // to a functional in-memory store with a one-time warning rather than
    // failing the caller.
    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File f = storageFile();
        if (f == null) {
            if (!warnedUnconfigured) {
                warnedUnconfigured = true;
                System.err.println("[AndroidKeyStore] storage dir not "
                    + "configured; keys will not persist across launches");
            }
            return;
        }
        if (!f.isFile()) return;
        try (DataInputStream in =
                 new DataInputStream(new FileInputStream(f))) {
            if (in.readInt() != MAGIC) {
                System.err.println("[AndroidKeyStore] " + f
                    + ": bad magic, starting empty");
                return;
            }
            int ver = in.readInt();
            if (ver != VERSION) {
                System.err.println("[AndroidKeyStore] " + f
                    + ": unknown version " + ver + ", starting empty");
                return;
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String alias = in.readUTF();
                String algorithm = in.readUTF();
                long created = in.readLong();
                int len = in.readInt();
                if (len < 0 || len > 1 << 20) {
                    throw new IOException("corrupt key length " + len);
                }
                byte[] key = new byte[len];
                in.readFully(key);
                entries.put(alias, new Entry(algorithm, key, created));
            }
        } catch (IOException e) {
            System.err.println("[AndroidKeyStore] failed to read " + f
                + ": " + e + "; starting empty");
            entries.clear();
        }
    }

    // Atomic persist: write to a temp sibling, fsync, rename over. Owner-only
    // permissions on both the directory and the file (the Android analogue:
    // keystore blobs are not readable by other uids).
    private void persist() {
        File f = storageFile();
        if (f == null) return;
        File dir = f.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            System.err.println("[AndroidKeyStore] cannot create " + dir);
            return;
        }
        restrictPermissions(dir, true);
        File tmp = new File(dir, "keys.dat.tmp");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp);
                 DataOutputStream out = new DataOutputStream(fos)) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(entries.size());
                for (Map.Entry<String, Entry> e : entries.entrySet()) {
                    out.writeUTF(e.getKey());
                    out.writeUTF(e.getValue().algorithm);
                    out.writeLong(e.getValue().created);
                    out.writeInt(e.getValue().key.length);
                    out.write(e.getValue().key);
                }
                out.flush();
                fos.getFD().sync();
            }
            restrictPermissions(tmp, false);
            if (!tmp.renameTo(f)) {
                Files.move(tmp.toPath(), f.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[AndroidKeyStore] failed to write " + f
                + ": " + e);
            tmp.delete();
        }
    }

    private static void restrictPermissions(File f, boolean dir) {
        try {
            Set<PosixFilePermission> perms = dir
                ? EnumSet.of(PosixFilePermission.OWNER_READ,
                             PosixFilePermission.OWNER_WRITE,
                             PosixFilePermission.OWNER_EXECUTE)
                : EnumSet.of(PosixFilePermission.OWNER_READ,
                             PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(f.toPath(), perms);
        } catch (Exception ignored) {
            // Non-POSIX filesystem: nothing to restrict.
        }
    }

    synchronized boolean containsAlias(String alias) {
        ensureLoaded();
        ensurePairsLoaded();
        return entries.containsKey(alias) || pairs.containsKey(alias);
    }

    synchronized SecretKey getKey(String alias) {
        ensureLoaded();
        Entry e = entries.get(alias);
        if (e == null) return null;
        return new SecretKeySpec(e.key.clone(), e.algorithm);
    }

    synchronized Date getCreationDate(String alias) {
        ensureLoaded();
        Entry e = entries.get(alias);
        if (e == null) return null;
        return new Date(e.created);
    }

    synchronized void putKey(String alias, String algorithm, byte[] key) {
        ensureLoaded();
        entries.put(alias,
            new Entry(algorithm, key.clone(), System.currentTimeMillis()));
        persist();
    }

    synchronized void deleteEntry(String alias) {
        ensureLoaded();
        ensurePairsLoaded();
        if (entries.remove(alias) != null) persist();
        if (pairs.remove(alias) != null) persistPairs();
    }

    synchronized java.util.List<String> aliases() {
        ensureLoaded();
        ensurePairsLoaded();
        // One namespace, as the device presents it. LinkedHashSet so a key that
        // somehow exists in both stores appears once, in insertion order.
        java.util.LinkedHashSet<String> all =
            new java.util.LinkedHashSet<>(entries.keySet());
        all.addAll(pairs.keySet());
        return new java.util.ArrayList<>(all);
    }

    synchronized int size() {
        return aliases().size();
    }
}
