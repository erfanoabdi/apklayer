package hello.keystore;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Android Keystore from an app's point of view: AES-GCM, HMAC, RSA and EC key pairs through KeyGenParameterSpec, aliases, certificates, deletion, and the
 * keys surviving a restart (the second launch decrypts what the first one encrypted). The scenario is -Dhello.ks=first|second.
 */
public final class Main extends Activity {
    private static void require(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try { run(System.getProperty("hello.ks", "first")); }
            catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[ks] FAIL " + failure); }
        }).start();
    }

    // Key attestation: the chain KeyMint signs (leaf, its batch key's certificates, a self-signed root), the challenge in the attestation extension
    private static void attestation() throws Exception {
        byte[] challenge = "apklayer-challenge".getBytes();
        KeyPairGenerator ec = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        ec.initialize(new KeyGenParameterSpec.Builder("attested", KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(challenge).build());
        ec.generateKeyPair();
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        Certificate[] chain = store.getCertificateChain("attested");
        require(chain != null && chain.length >= 2, "attestation chain length " + (chain == null ? 0 : chain.length));
        for (int i = 0; i < chain.length; i++) chain[i].verify(chain[Math.min(i + 1, chain.length - 1)].getPublicKey());  // each by the next, the root by itself
        byte[] extension = ((java.security.cert.X509Certificate) chain[0]).getExtensionValue("1.3.6.1.4.1.11129.2.1.17");
        require(extension != null, "no key attestation extension");
        require(java.util.Collections.indexOfSubList(bytes(extension), bytes(challenge)) >= 0, "challenge not in the attestation");
        store.deleteEntry("attested");
        System.out.println("[ks] attestation chain of " + chain.length + " verified, challenge attested");
    }
    private static java.util.List<Byte> bytes(byte[] array) {
        java.util.List<Byte> list = new java.util.ArrayList<>();
        for (byte b : array) list.add(b);
        return list;
    }

    // KeyInfo of a secret and a private key (SecretKeyFactory / KeyFactory of AndroidKeyStore)
    private static void keyInfo(KeyStore store, SecretKey aes, PrivateKey ec) throws Exception {
        android.security.keystore.KeyInfo secret = (android.security.keystore.KeyInfo) javax.crypto.SecretKeyFactory.getInstance(aes.getAlgorithm(), "AndroidKeyStore")
                .getKeySpec(aes, android.security.keystore.KeyInfo.class);
        require(secret.getKeystoreAlias().equals("aes") && secret.getKeySize() == 256 && !secret.isUserAuthenticationRequired()
                && (secret.getPurposes() & KeyProperties.PURPOSE_DECRYPT) != 0, "aes KeyInfo");
        android.security.keystore.KeyInfo pair = java.security.KeyFactory.getInstance(ec.getAlgorithm(), "AndroidKeyStore")
                .getKeySpec(ec, android.security.keystore.KeyInfo.class);
        require(pair.getKeySize() == 256 && pair.getSecurityLevel() == KeyProperties.SECURITY_LEVEL_SOFTWARE && !pair.isInsideSecureHardware(), "ec KeyInfo "
                + pair.getKeySize() + " level " + pair.getSecurityLevel());
        System.out.println("[ks] KeyInfo: aes 256, ec 256, software level");
    }

    // A key that needs the lock screen credential within 300 s: the instance has one (the hub enrolled it) and verified it at its start
    private void authBound() throws Exception {
        require(getSystemService(android.app.KeyguardManager.class).isDeviceSecure(), "no secure lock screen");
        KeyGenerator aes = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        aes.init(new KeyGenParameterSpec.Builder("auth", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true).setUserAuthenticationParameters(300, KeyProperties.AUTH_DEVICE_CREDENTIAL).build());
        SecretKey key = aes.generateKey();
        android.security.keystore.KeyInfo info = (android.security.keystore.KeyInfo) javax.crypto.SecretKeyFactory.getInstance("AES", "AndroidKeyStore")
                .getKeySpec(key, android.security.keystore.KeyInfo.class);
        require(info.isUserAuthenticationRequired() && info.getUserAuthenticationValidityDurationSeconds() == 300, "auth-bound KeyInfo");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.doFinal("unlocked".getBytes());
        System.out.println("[ks] auth-bound key used within its timeout after the instance's unlock");
    }

    private void run(String scenario) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SharedPreferences prefs = getSharedPreferences("ks", MODE_PRIVATE);
        byte[] plain = "attack at dawn".getBytes();
        if ("other".equals(scenario)) {  // another app (tests/apps/hello-keystore-other): keys are per app uid, so the first app's are not visible here
            java.util.List<String> aliases = Collections.list(store.aliases());
            require(!aliases.contains("rsa") && !aliases.contains("ec"), "another app's keys are visible: " + aliases);
            KeyGenerator aes = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            aes.init(new KeyGenParameterSpec.Builder("aes", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            aes.generateKey();  // the same alias as the first app's key: it must not replace that one
            System.out.println("[ks] PASS other: " + getPackageName() + " (uid " + android.os.Process.myUid() + ") sees only its own keys " + Collections.list(store.aliases()));
            return;
        }
        if ("first".equals(scenario)) {
            for (String alias : Collections.list(store.aliases())) store.deleteEntry(alias);
            KeyGenerator aes = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            aes.init(new KeyGenParameterSpec.Builder("aes", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            SecretKey key = aes.generateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plain);
            prefs.edit().putString("iv", Base64.encodeToString(cipher.getIV(), 0)).putString("data", Base64.encodeToString(encrypted, 0)).commit();
            require(store.containsAlias("aes") && store.isKeyEntry("aes"), "aes alias");
            System.out.println("[ks] aes generated and used");

            KeyGenerator hmac = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore");
            hmac.init(new KeyGenParameterSpec.Builder("hmac", KeyProperties.PURPOSE_SIGN).build());
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmac.generateKey());
            require(mac.doFinal(plain).length == 32, "hmac length");
            System.out.println("[ks] hmac generated and used");

            KeyPairGenerator rsa = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            rsa.initialize(new KeyGenParameterSpec.Builder("rsa", KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).setKeySize(2048).build());
            KeyPair pair = rsa.generateKeyPair();
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initSign(pair.getPrivate());
            sign.update(plain);
            byte[] signature = sign.sign();
            Signature verify = Signature.getInstance("SHA256withRSA");
            verify.initVerify(store.getCertificate("rsa").getPublicKey());
            verify.update(plain);
            require(verify.verify(signature), "rsa verify through the stored certificate");
            prefs.edit().putString("sig", Base64.encodeToString(signature, 0)).commit();
            System.out.println("[ks] rsa pair signed and verified through its certificate");

            KeyPairGenerator ec = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            ec.initialize(new KeyGenParameterSpec.Builder("ec", KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build());
            KeyPair ecPair = ec.generateKeyPair();
            Signature ecSign = Signature.getInstance("SHA256withECDSA");
            ecSign.initSign(ecPair.getPrivate());
            ecSign.update(plain);
            byte[] ecSignature = ecSign.sign();
            Signature ecVerify = Signature.getInstance("SHA256withECDSA");
            ecVerify.initVerify(ecPair.getPublic());
            ecVerify.update(plain);
            require(ecVerify.verify(ecSignature), "ec verify");
            System.out.println("[ks] ec pair signed and verified");

            require(Collections.list(store.aliases()).containsAll(java.util.List.of("aes", "hmac", "rsa", "ec")), "aliases " + Collections.list(store.aliases()));
            store.deleteEntry("hmac");
            require(!store.containsAlias("hmac"), "deleted alias is gone");
            // Android has no module system: apps reflect into libcore's private fields (App Manager reads SecretKeySpec.key)
            java.lang.reflect.Field raw = javax.crypto.spec.SecretKeySpec.class.getDeclaredField("key");
            raw.setAccessible(true);
            require(((byte[]) raw.get(new javax.crypto.spec.SecretKeySpec(plain, "AES"))).length == plain.length, "SecretKeySpec.key by reflection");
            attestation();
            keyInfo(store, key, ecPair.getPrivate());
            authBound();
            System.out.println("[ks] PASS first: keys generated, used, listed, one deleted");
        } else {
            SecretKey key = (SecretKey) store.getKey("aes", null);
            require(key != null, "aes survived the restart");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), 0)));
            require(new String(cipher.doFinal(Base64.decode(prefs.getString("data", ""), 0))).equals("attack at dawn"), "decrypted text");
            PrivateKey rsa = (PrivateKey) store.getKey("rsa", null);
            Certificate certificate = store.getCertificate("rsa");
            require(rsa != null && certificate != null, "rsa pair survived the restart");
            Signature verify = Signature.getInstance("SHA256withRSA");
            verify.initVerify(certificate.getPublicKey());
            verify.update(plain);
            require(verify.verify(Base64.decode(prefs.getString("sig", ""), 0)), "signature of the first run verifies");
            require(!store.containsAlias("hmac"), "deleted alias stayed deleted");
            Cipher auth = Cipher.getInstance("AES/GCM/NoPadding");
            auth.init(Cipher.ENCRYPT_MODE, (SecretKey) store.getKey("auth", null));  // the new hub start verified the credential again: the same secure user id
            auth.doFinal(plain);
            System.out.println("[ks] PASS second: decrypted and verified what the first launch made");
        }
    }
}
