// android.hardware.biometrics.PromptContentView -- marker interface for the
// customisable content area of a BiometricPrompt, set through
// BiometricPrompt.Builder.setContentView. AOSP's public form is empty.
//
// Mirrors frameworks/base/core/java/android/hardware/biometrics/PromptContentView.java
// (android16-release).
//
// Nothing here renders a prompt (see BiometricPrompt: authenticate() reports
// BIOMETRIC_ERROR_HW_NOT_PRESENT rather than showing a dialog), so a content
// view is never displayed. The types must exist and behave correctly anyway:
// an app builds its content view BEFORE it calls authenticate(), and AOSP's
// builders validate as they go, so a broken builder here would fail the app in
// a completely different place from the honest no-hardware error it should get.
package android.hardware.biometrics;

public interface PromptContentView {
}
