package android.media;

/**
 * Thrown when a DRM resource is in use by another session.
 * Source: frameworks/base/media/java/android/media/ResourceBusyException.java
 */
public final class ResourceBusyException extends MediaDrmException {
    public ResourceBusyException(String detailMessage) { super(detailMessage); }
}
