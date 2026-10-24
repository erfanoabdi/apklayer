// android.security.ConfirmationNotAvailableException -- thrown by
// ConfirmationPrompt.presentPrompt on a device with no Trusted UI.
// AOSP source: frameworks/base/core/java/android/security/
// ConfirmationNotAvailableException.java
//
// This is the exception Mac.apk ALWAYS throws from presentPrompt: Android
// Protected Confirmation needs a trusted display path implemented in secure
// hardware, and macOS has none. See ConfirmationPrompt.
package android.security;

public class ConfirmationNotAvailableException extends Exception {
    public ConfirmationNotAvailableException() {
        super("Confirmation not available");
    }
    public ConfirmationNotAvailableException(String message) { super(message); }
}
