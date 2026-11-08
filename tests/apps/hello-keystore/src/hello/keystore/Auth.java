package hello.keystore;

import android.app.Activity;
import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * -Dhello.ks=auth, driven by tests/vm/keystore-auth.py (it answers the hub's password dialogs and locks the desktop session): a key that needs the
 * credential for every use (timeout 0) through BiometricPrompt with DEVICE_CREDENTIAL (cancelled once, then a wrong and the right password),
 * KeyguardManager's confirm intent, and an UNLOCKED_DEVICE_REQUIRED key refused while the desktop is locked.
 */
final class Auth {
    static final CompletableFuture<Integer> RESULT = new CompletableFuture<>();  // Main.onActivityResult
    private static void require(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    private Auth() {}

    static void run(Activity activity) throws Exception {
        BiometricManager biometrics = activity.getSystemService(BiometricManager.class);
        require(biometrics.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS, "device credential available");
        require(biometrics.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, "no biometric hardware");
        KeyGenerator aes = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        aes.init(new KeyGenParameterSpec.Builder("perop", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true).setUserAuthenticationParameters(0, KeyProperties.AUTH_DEVICE_CREDENTIAL).build());
        SecretKey perOp = aes.generateKey();
        Cipher unauthenticated = Cipher.getInstance("AES/GCM/NoPadding");
        unauthenticated.init(Cipher.ENCRYPT_MODE, perOp);
        boolean refused = false;
        try { unauthenticated.doFinal("x".getBytes()); } catch (Exception expected) { refused = true; }
        require(refused, "a timeout-0 key worked without authentication");
        System.out.println("[ks] per-operation key refused without authentication");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, perOp);
        System.out.println("[ks] prompt 1 (cancel)");
        require(prompt(activity, cipher) == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, "a cancelled prompt reports USER_CANCELED");
        boolean noPassword = Boolean.getBoolean("hello.ks.nopass");  // the test knows no login password: it cancels where it would type it
        System.out.println("[ks] prompt 2 (wrong, then " + (noPassword ? "cancel)" : "right password)"));
        int second = prompt(activity, cipher);
        require(second == (noPassword ? BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED : -1), "the second prompt: " + second);
        if (!noPassword) cipher.doFinal("per operation".getBytes());
        System.out.println("[ks] per-operation key " + (noPassword ? "not authenticated, as cancelled" : "used after BiometricPrompt"));

        KeyguardManager keyguard = activity.getSystemService(KeyguardManager.class);
        android.content.Intent confirm = keyguard.createConfirmDeviceCredentialIntent("Unlock keystore-check", "The test confirms the device credential.");
        require(confirm != null, "a confirm intent");
        System.out.println("[ks] confirm intent");
        activity.runOnUiThread(() -> activity.startActivityForResult(confirm, 7));
        require(RESULT.get(60, TimeUnit.SECONDS) == (noPassword ? Activity.RESULT_CANCELED : Activity.RESULT_OK), "confirm intent's result");
        System.out.println("[ks] device credential confirmed through the intent");

        aes.init(new KeyGenParameterSpec.Builder("unlocked", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUnlockedDeviceRequired(true).build());
        SecretKey unlocked = aes.generateKey();
        require(!keyguard.isDeviceLocked() && use(unlocked), "an UNLOCKED_DEVICE_REQUIRED key works while unlocked");
        System.out.println("[ks] lock now");
        await(keyguard, true);
        require(!use(unlocked), "an UNLOCKED_DEVICE_REQUIRED key worked while locked");
        System.out.println("[ks] locked: isDeviceLocked, the key refused; unlock now");
        await(keyguard, false);
        require(use(unlocked), "the UNLOCKED_DEVICE_REQUIRED key after the unlock");
        System.out.println("[ks] PASS auth: per-operation key, confirm intent, locked state");
    }

    /** BiometricPrompt with DEVICE_CREDENTIAL for the cipher: -1 on success, else the error code. */
    private static int prompt(Activity activity, Cipher cipher) throws Exception {
        CompletableFuture<Integer> done = new CompletableFuture<>();
        BiometricPrompt prompt = new BiometricPrompt.Builder(activity).setTitle("Use the per-operation key")
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();
        prompt.authenticate(new BiometricPrompt.CryptoObject(cipher), new CancellationSignal(), activity.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                done.complete(result.getAuthenticationType() == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL ? -1 : -2);
            }
            @Override public void onAuthenticationError(int code, CharSequence message) { done.complete(code); }
        });
        return done.get(60, TimeUnit.SECONDS);
    }

    private static boolean use(SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.doFinal("while unlocked".getBytes());
            return true;
        } catch (Exception refused) {
            System.out.println("[ks] key refused: " + refused);
            return false;
        }
    }

    /** Until KeyguardManager reports the desktop's lock state (the test changes logind's LockedHint), at most 20 s. */
    private static void await(KeyguardManager keyguard, boolean locked) throws InterruptedException {
        for (int i = 0; i < 200 && keyguard.isDeviceLocked() != locked; i++) Thread.sleep(100);
        require(keyguard.isDeviceLocked() == locked, "isDeviceLocked " + locked);
    }
}
