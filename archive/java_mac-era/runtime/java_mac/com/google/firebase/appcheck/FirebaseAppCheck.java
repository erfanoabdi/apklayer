package com.google.firebase.appcheck;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;

// FirebaseAppCheck deliberately FAILS. Per the strategy doc: App
// Check is hardware-attestation-based anti-abuse; we will NOT spoof
// it (legal risk + we can't actually do it). Apps that gate features
// behind App Check tokens will see the failure and either gracefully
// degrade or refuse to run -- both are acceptable outcomes by design.
public final class FirebaseAppCheck {
    private static final FirebaseAppCheck INSTANCE = new FirebaseAppCheck();
    private FirebaseAppCheck() {}

    public static FirebaseAppCheck getInstance()                { return INSTANCE; }
    public static FirebaseAppCheck getInstance(FirebaseApp app) { return INSTANCE; }

    public Task<AppCheckToken> getAppCheckToken(boolean forceRefresh) {
        return Tasks.forException(new IllegalStateException(
            "Mac.apk does not provide Firebase App Check; this is by design. "
          + "App Check requires hardware attestation we cannot legitimately produce."));
    }
    public Task<AppCheckToken> getLimitedUseAppCheckToken() {
        return Tasks.forException(new IllegalStateException(
            "Mac.apk does not provide Firebase App Check tokens."));
    }
    public void installAppCheckProviderFactory(Object factory) {}
    public void setTokenAutoRefreshEnabled(boolean enabled) {}
    public void addAppCheckListener(AppCheckListener listener) {}
    public void removeAppCheckListener(AppCheckListener listener) {}

    public interface AppCheckListener {
        void onAppCheckTokenChanged(AppCheckToken token);
    }
}
