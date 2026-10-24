package android.media;

/**
 * The unchecked counterpart of MediaCasException: a CAS plugin failure raised
 * from a method that does not declare a checked exception.
 *
 * Source: frameworks/base/media/java/android/media/MediaCasStateException.java
 */
public class MediaCasStateException extends IllegalStateException {

    private final String mDiagnosticInfo;

    MediaCasStateException(int err, String detailMessage) {
        super(detailMessage);
        mDiagnosticInfo = "android.media.MediaCasStateException: error " + err;
    }

    /** @NonNull in AOSP: a vendor-defined string identifying the failure. */
    public String getDiagnosticInfo() { return mDiagnosticInfo; }
}
