package android.media;

/**
 * Thrown when the device has no DRM plugin for the requested crypto scheme.
 * Source: frameworks/base/media/java/android/media/UnsupportedSchemeException.java
 */
public final class UnsupportedSchemeException extends MediaDrmException {
    public UnsupportedSchemeException(String detailMessage) { super(detailMessage); }
}
