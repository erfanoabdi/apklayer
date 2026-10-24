package com.google.firebase.auth;

import android.app.Activity;

import java.util.concurrent.TimeUnit;

// PhoneAuthOptions: the modern builder-based parameter object for
// PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions). Faithful
// surface from the Firebase ref. We store everything the app sets so
// verifyPhoneNumber can read back the callbacks; nothing is validated.
public final class PhoneAuthOptions {
    private final FirebaseAuth firebaseAuth;
    private final String phoneNumber;
    private final long timeout;
    private final TimeUnit unit;
    private final Activity activity;
    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks;
    private final PhoneAuthProvider.ForceResendingToken forceResendingToken;

    private PhoneAuthOptions(Builder b) {
        this.firebaseAuth = b.firebaseAuth;
        this.phoneNumber = b.phoneNumber;
        this.timeout = b.timeout;
        this.unit = b.unit;
        this.activity = b.activity;
        this.callbacks = b.callbacks;
        this.forceResendingToken = b.forceResendingToken;
    }

    public FirebaseAuth getFirebaseAuth() { return firebaseAuth; }
    public String getPhoneNumber() { return phoneNumber; }
    public long getTimeout() { return timeout; }
    public TimeUnit getUnit() { return unit; }
    public Activity getActivity() { return activity; }
    public PhoneAuthProvider.OnVerificationStateChangedCallbacks getCallbacks() { return callbacks; }
    public PhoneAuthProvider.ForceResendingToken getForceResendingToken() { return forceResendingToken; }

    public static Builder newBuilder() { return new Builder(); }
    public static Builder newBuilder(FirebaseAuth firebaseAuth) { return new Builder(firebaseAuth); }

    public static final class Builder {
        private FirebaseAuth firebaseAuth;
        private String phoneNumber;
        private long timeout;
        private TimeUnit unit;
        private Activity activity;
        private PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks;
        private PhoneAuthProvider.ForceResendingToken forceResendingToken;

        Builder() {}
        Builder(FirebaseAuth firebaseAuth) { this.firebaseAuth = firebaseAuth; }

        public Builder setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; return this; }
        public Builder setTimeout(Long timeout, TimeUnit unit) {
            this.timeout = timeout == null ? 0L : timeout;
            this.unit = unit;
            return this;
        }
        public Builder setActivity(Activity activity) { this.activity = activity; return this; }
        public Builder setCallbacks(PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks) {
            this.callbacks = callbacks; return this;
        }
        public Builder setForceResendingToken(PhoneAuthProvider.ForceResendingToken token) {
            this.forceResendingToken = token; return this;
        }
        public Builder requireSmsValidation(boolean enabled) { return this; }

        public PhoneAuthOptions build() { return new PhoneAuthOptions(this); }
    }
}
