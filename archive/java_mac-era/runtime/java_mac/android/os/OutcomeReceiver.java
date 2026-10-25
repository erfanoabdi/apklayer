// android.os.OutcomeReceiver -- a generic async result callback (API 31+) used
// by Credentials, AdServices, BiometricPrompt, and other modern async APIs.
// Pure interface; onResult / onError are app-supplied. The default onError is
// empty, matching AOSP's default-method definition.
//
// AOSP ref: frameworks/base/core/java/android/os/OutcomeReceiver.java
package android.os;

public interface OutcomeReceiver<R, E extends Throwable> {
    void onResult(R result);

    default void onError(E error) {}
}
