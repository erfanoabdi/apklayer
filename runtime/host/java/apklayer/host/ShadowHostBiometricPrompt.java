package apklayer.host;

import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.PromptInfo;
import android.os.CancellationSignal;
import java.util.concurrent.Executor;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

/**
 * BiometricPrompt as AuthService answers it on a device without biometric sensors: a prompt that allows DEVICE_CREDENTIAL asks for the desktop's
 * login password in the hub's dialog (HubLockScreen.confirm, the credential page of BiometricPrompt); with a CryptoObject the credential is
 * verified with the operation's id as the challenge, so a key that needs authentication for every use (timeout 0) works for that operation.
 * A biometric-only prompt fails as without hardware. Cancelling through the CancellationSignal closes the dialog.
 */
@Implements(BiometricPrompt.class)
public class ShadowHostBiometricPrompt {
    @RealObject private BiometricPrompt real;

    @Implementation protected void authenticate(BiometricPrompt.CryptoObject crypto, CancellationSignal cancel, Executor executor, BiometricPrompt.AuthenticationCallback callback) {
        ask(crypto, cancel, executor, callback);
    }
    @Implementation protected void authenticate(CancellationSignal cancel, Executor executor, BiometricPrompt.AuthenticationCallback callback) {
        ask(null, cancel, executor, callback);
    }

    private void ask(BiometricPrompt.CryptoObject crypto, CancellationSignal cancel, Executor executor, BiometricPrompt.AuthenticationCallback callback) {
        PromptInfo info = ReflectionHelpers.getField(real, "mPromptInfo");
        boolean credential = info.isDeviceCredentialAllowed() || (info.getAuthenticators() & BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0;
        if (!credential) { executor.execute(() -> callback.onAuthenticationError(BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT, "No biometric hardware")); return; }
        if (!secure()) { executor.execute(() -> callback.onAuthenticationError(BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL, "No device credential")); return; }
        // CryptoObject.getOpId's own path (Robolectric's ShadowCryptoObject answers 0): keystore2's operation challenge of the primitive
        Object primitive = crypto == null ? null : crypto.getCipher() != null ? crypto.getCipher() : crypto.getSignature() != null ? crypto.getSignature() : crypto.getMac();
        long challenge = primitive == null ? 0 : android.security.keystore2.AndroidKeyStoreProvider.getKeyStoreOperationHandle(primitive);
        String title = info.getTitle() == null ? null : info.getTitle().toString();
        CharSequence about = info.getDescription() != null ? info.getDescription() : info.getSubtitle();
        if (cancel != null) cancel.setOnCancelListener(() -> new Thread(() -> {  // the hub's password dialog goes with it
            try { if (HubClient.connected()) HubClient.current().cancelCredential(); } catch (RuntimeException noHub) { /* nothing open */ }
        }, "biometric-cancel").start());
        Thread asking = new Thread(() -> {
            boolean confirmed;
            try { confirmed = HubClient.connected() && HubClient.current().confirmCredential(title, about == null ? null : about.toString(), challenge); }
            catch (RuntimeException noHub) { confirmed = false; }
            System.out.println("[apklayer] BiometricPrompt: device credential " + (confirmed ? "confirmed" : "not confirmed") + (crypto == null ? "" : challenge == 0 ? ", no operation challenge" : ", for the operation"));
            if (cancel != null && cancel.isCanceled()) return;
            if (confirmed) executor.execute(() -> callback.onAuthenticationSucceeded(new BiometricPrompt.AuthenticationResult(crypto, BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL)));
            else executor.execute(() -> callback.onAuthenticationError(BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, "Authentication canceled by user"));
        }, "biometric-prompt");
        asking.setDaemon(true);
        asking.start();
    }

    static boolean secure() { return RuntimeEnvironment.getApplication().getSystemService(KeyguardManager.class).isDeviceSecure(); }

    /** BiometricManager without biometric sensors: DEVICE_CREDENTIAL is there when the instance has its credential (HubLockScreen), biometrics never. */
    @Implements(BiometricManager.class)
    public static class Manager {
        @Implementation protected int canAuthenticate() { return BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE; }  // BIOMETRIC_WEAK only
        @Implementation protected int canAuthenticate(int authenticators) { return canAuthenticate(0, authenticators); }
        @Implementation protected int canAuthenticate(int userId, int authenticators) {
            if ((authenticators & BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0)
                return secure() ? BiometricManager.BIOMETRIC_SUCCESS : BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
            return BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE;
        }
    }
}
