package com.google.firebase.auth;

import android.app.Activity;

import java.util.concurrent.TimeUnit;

// PhoneAuthProvider: phone-number verification factory. Faithful
// surface from the Firebase Android ref
// (firebase.google.com/docs/reference/android/com/google/firebase/auth/PhoneAuthProvider).
//
// Honest-negative behavior: phone verification needs Firebase's SMS
// backend, which is absent on a de-Googled device. So verifyPhoneNumber
// does NOT silently succeed (that would impersonate a delivered SMS);
// it fires the callbacks' onVerificationFailed with a
// FirebaseAuthException. getCredential still constructs an opaque
// PhoneAuthCredential from caller-supplied data (apps that already hold
// a verification id + sms code can build one), but it is never
// validated.
public class PhoneAuthProvider {
    public static final String PROVIDER_ID = "phone";
    public static final String PHONE_SIGN_IN_METHOD = "phone";

    private static final PhoneAuthProvider INSTANCE = new PhoneAuthProvider();

    private PhoneAuthProvider() {}

    public static PhoneAuthProvider getInstance() { return INSTANCE; }
    public static PhoneAuthProvider getInstance(FirebaseAuth firebaseAuth) { return INSTANCE; }

    public static PhoneAuthCredential getCredential(String verificationId, String smsCode) {
        return new PhoneAuthCredential(verificationId, smsCode);
    }

    // Classic instance overloads.
    public void verifyPhoneNumber(String phoneNumber, long timeout, TimeUnit unit,
                                  Activity activity,
                                  OnVerificationStateChangedCallbacks callbacks) {
        failVerification(callbacks);
    }

    public void verifyPhoneNumber(String phoneNumber, long timeout, TimeUnit unit,
                                  Activity activity,
                                  OnVerificationStateChangedCallbacks callbacks,
                                  ForceResendingToken forceResendingToken) {
        failVerification(callbacks);
    }

    public void verifyPhoneNumber(String autoRetrievedSmsCodeForTesting, String phoneNumber) {
        // Integration-test hook on the real SDK; no backend here.
    }

    // Modern static overload (PhoneAuthOptions builder path).
    public static void verifyPhoneNumber(PhoneAuthOptions options) {
        if (options != null) failVerification(options.getCallbacks());
    }

    private static void failVerification(OnVerificationStateChangedCallbacks callbacks) {
        if (callbacks == null) return;
        callbacks.onVerificationFailed(new FirebaseAuthException(
            "ERROR_INVALID_APP_CREDENTIAL",
            "Phone verification is unavailable (no Firebase backend)."));
    }

    public static class ForceResendingToken {
        public ForceResendingToken() {}
    }

    public abstract static class OnVerificationStateChangedCallbacks {
        public abstract void onVerificationCompleted(PhoneAuthCredential credential);
        public abstract void onVerificationFailed(com.google.firebase.FirebaseException e);
        public void onCodeSent(String verificationId, ForceResendingToken token) {}
        public void onCodeAutoRetrievalTimeOut(String verificationId) {}
    }
}
