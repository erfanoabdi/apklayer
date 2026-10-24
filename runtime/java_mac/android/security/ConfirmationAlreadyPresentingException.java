// android.security.ConfirmationAlreadyPresentingException -- thrown by
// ConfirmationPrompt.presentPrompt when a prompt is already on screen.
// AOSP source: frameworks/base/core/java/android/security/
// ConfirmationAlreadyPresentingException.java
package android.security;

public class ConfirmationAlreadyPresentingException extends Exception {
    public ConfirmationAlreadyPresentingException() {
        super("Trusted Confirmation prompt already presenting");
    }
    public ConfirmationAlreadyPresentingException(String message) { super(message); }
}
