package android.service.autofill;

/**
 * Marker for a rule that derives the text/image of a child view in a
 * CustomDescription from the value of an autofilled field.
 *
 * Empty in AOSP too -- the platform matches on the concrete subtype
 * (CharSequenceTransformation, ImageTransformation, DateTransformation) rather
 * than calling a method through the interface, which is why AOSP declares it
 * with no members and forbids third-party implementations.
 *
 * Source: frameworks/base/core/java/android/service/autofill/Transformation.java
 */
public interface Transformation {
}
