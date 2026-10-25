// android.security.ConfirmationCallback -- the callback a ConfirmationPrompt
// reports its outcome on. AOSP source: frameworks/base/core/java/android/
// security/ConfirmationCallback.java (android16-release); every method has an
// EMPTY body there, so a caller overrides only what it cares about.
package android.security;

public abstract class ConfirmationCallback {
    public ConfirmationCallback() {}

    /** The user accepted the prompt. dataThatWasConfirmed is a CBOR map. */
    public void onConfirmed(byte[] dataThatWasConfirmed) {}

    /** The user dismissed the prompt without accepting. */
    public void onDismissed() {}

    /** The app cancelled the prompt. */
    public void onCanceled() {}

    /** A low-level error ended the prompt. */
    public void onError(Throwable e) {}
}
