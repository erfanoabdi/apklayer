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

    private void run(String scenario) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SharedPreferences prefs = getSharedPreferences("ks", MODE_PRIVATE);
        byte[] plain = "attack at dawn".getBytes();
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
            System.out.println("[ks] PASS second: decrypted and verified what the first launch made");
        }
    }
}
