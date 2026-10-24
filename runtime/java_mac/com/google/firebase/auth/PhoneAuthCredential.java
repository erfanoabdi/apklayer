package com.google.firebase.auth;

// PhoneAuthCredential: the AuthCredential produced by
// PhoneAuthProvider.getCredential(verificationId, smsCode) or handed
// to OnVerificationStateChangedCallbacks.onVerificationCompleted on
// instant verification. We carry the verification id + sms code so
// apps that re-read them behave; we never verify them (no backend).
// Faithful surface from the Firebase ref.
public final class PhoneAuthCredential extends AuthCredential {
    private final String verificationId;
    private final String smsCode;

    PhoneAuthCredential(String verificationId, String smsCode) {
        this.verificationId = verificationId;
        this.smsCode = smsCode;
    }

    @Override public String getProvider() { return PhoneAuthProvider.PROVIDER_ID; }
    @Override public String getSignInMethod() { return PhoneAuthProvider.PHONE_SIGN_IN_METHOD; }

    public String getSmsCode() { return smsCode; }
}
