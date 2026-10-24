package android.service.autofill;

/**
 * Marker for a predicate over autofilled values, used by
 * SaveInfo.Builder.setValidator to suppress a save prompt for input the
 * service knows is wrong.
 *
 * Empty in AOSP too; the platform matches on the concrete subtype.
 *
 * Source: frameworks/base/core/java/android/service/autofill/Validator.java
 */
public interface Validator {
}
