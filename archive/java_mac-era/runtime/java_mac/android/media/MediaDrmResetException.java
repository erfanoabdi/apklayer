package android.media;

/**
 * Thrown when the DRM stack was reset underneath an in-flight operation and the
 * session must be rebuilt. AOSP makes it an IllegalStateException so existing
 * catch blocks keep working.
 * Source: frameworks/base/media/java/android/media/MediaDrmResetException.java
 */
// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class MediaDrmResetException extends IllegalStateException implements MediaDrmThrowable {
    public MediaDrmResetException(String detailMessage) { super(detailMessage); }
}
