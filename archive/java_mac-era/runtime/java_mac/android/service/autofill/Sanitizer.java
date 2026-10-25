package android.service.autofill;

/**
 * Marker for a rule that normalises a field's value before the platform
 * decides whether it changed enough to warrant a save prompt (for example,
 * stripping the dashes out of a credit-card number).
 *
 * Empty in AOSP too; the platform matches on the concrete subtype.
 *
 * Source: frameworks/base/core/java/android/service/autofill/Sanitizer.java
 */
public interface Sanitizer {
}
