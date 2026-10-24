// android.hardware.fingerprint.FingerprintManager -- the deprecated
// fingerprint API (pre-BiometricPrompt). Mac.apk has no fingerprint
// hardware: isHardwareDetected/hasEnrolledFingerprints return false and
// authenticate() reports FINGERPRINT_ERROR_HW_NOT_PRESENT. The nested
// callback + CryptoObject types must exist for callers to compile.
package android.hardware.fingerprint;

import android.os.CancellationSignal;
import android.os.Handler;

public class FingerprintManager {
    public static final int FINGERPRINT_ERROR_HW_UNAVAILABLE = 1;
    public static final int FINGERPRINT_ERROR_UNABLE_TO_PROCESS = 2;
    public static final int FINGERPRINT_ERROR_TIMEOUT = 3;
    public static final int FINGERPRINT_ERROR_NO_SPACE = 4;
    public static final int FINGERPRINT_ERROR_CANCELED = 5;
    public static final int FINGERPRINT_ERROR_LOCKOUT = 7;
    public static final int FINGERPRINT_ERROR_USER_CANCELED = 10;
    public static final int FINGERPRINT_ERROR_NO_FINGERPRINTS = 11;
    public static final int FINGERPRINT_ERROR_HW_NOT_PRESENT = 12;

    public FingerprintManager() {}

    public boolean isHardwareDetected() { return false; }
    public boolean hasEnrolledFingerprints() { return false; }

    /** AOSP's real authenticate() (frameworks/base/core/java/android/hardware/
     *  fingerprint/FingerprintManager.java, useHandler()): "If a handler is
     *  provided, its looper is wrapped... If null, the main looper is used" --
     *  every AuthenticationCallback method arrives on THAT looper via a
     *  binder callback, never synchronously inside the call to authenticate().
     *  Calling back inline (the old behavior here) broke that contract: a
     *  caller on a background thread that expected the error on the main
     *  thread instead got it reentrantly on its own thread, and a caller that
     *  writes state right after authenticate() returns could observe the
     *  callback nested inside that same call frame. Post it instead, matching
     *  the async-post discipline the rest of this audit's fixes use. */
    public void authenticate(CryptoObject crypto, CancellationSignal cancel, int flags,
            AuthenticationCallback callback, Handler handler) {
        if (callback == null) return;
        android.os.Looper looper = (handler != null) ? handler.getLooper() : null;
        if (looper == null) looper = android.os.Looper.getMainLooper();
        new Handler(looper).post(() -> {
            try {
                callback.onAuthenticationError(FINGERPRINT_ERROR_HW_NOT_PRESENT,
                        "No fingerprint hardware");
            } catch (Throwable ignored) {}
        });
    }

    public static abstract class AuthenticationCallback {
        public void onAuthenticationError(int errorCode, CharSequence errString) {}
        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {}
        public void onAuthenticationSucceeded(AuthenticationResult result) {}
        public void onAuthenticationFailed() {}
    }

    // AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
    // mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
    // app compiled against real Android that subclasses this fails to resolve
    // against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
    public static class AuthenticationResult {
        private final CryptoObject mCryptoObject;
        AuthenticationResult(CryptoObject crypto) { mCryptoObject = crypto; }
        public CryptoObject getCryptoObject() { return mCryptoObject; }
    }

    public static final class CryptoObject {
        private final Object mCrypto;
        public CryptoObject(javax.crypto.Cipher cipher) { mCrypto = cipher; }
        public CryptoObject(java.security.Signature signature) { mCrypto = signature; }
        public CryptoObject(javax.crypto.Mac mac) { mCrypto = mac; }
        public javax.crypto.Cipher getCipher() {
            return (mCrypto instanceof javax.crypto.Cipher) ? (javax.crypto.Cipher) mCrypto : null;
        }
        public java.security.Signature getSignature() {
            return (mCrypto instanceof java.security.Signature)
                    ? (java.security.Signature) mCrypto : null;
        }
        public javax.crypto.Mac getMac() {
            return (mCrypto instanceof javax.crypto.Mac) ? (javax.crypto.Mac) mCrypto : null;
        }
    }
}
