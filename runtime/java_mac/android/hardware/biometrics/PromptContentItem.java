// android.hardware.biometrics.PromptContentItem -- marker interface for one row
// of a BiometricPrompt vertical-list content view. AOSP's public form is empty;
// the real content lives in the two concrete implementations
// (PromptContentItemPlainText / PromptContentItemBulletedText) and in a @hide
// parcelable sub-interface the system UI consumes.
//
// Mirrors frameworks/base/core/java/android/hardware/biometrics/PromptContentItem.java
// (android16-release).
package android.hardware.biometrics;

public interface PromptContentItem {
}
