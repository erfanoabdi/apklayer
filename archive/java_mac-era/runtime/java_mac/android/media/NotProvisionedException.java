package android.media;

/**
 * Thrown when the device needs provisioning before a DRM operation can run.
 * Source: frameworks/base/media/java/android/media/NotProvisionedException.java
 */
public final class NotProvisionedException extends MediaDrmException {
    public NotProvisionedException(String detailMessage) { super(detailMessage); }
}
