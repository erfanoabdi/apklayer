package android.media;

/**
 * Thrown when the license server refuses the request.
 * Source: frameworks/base/media/java/android/media/DeniedByServerException.java
 */
public final class DeniedByServerException extends MediaDrmException {
    public DeniedByServerException(String detailMessage) { super(detailMessage); }
}
