package android.media;

/**
 * Base for the conditional-access (CAS) failures MediaCas reports.
 *
 * Source: frameworks/base/media/java/android/media/MediaCasException.java
 */
public class MediaCasException extends Exception {

    MediaCasException(String detailMessage) { super(detailMessage); }

    /** The device has no plugin for the requested CA system. */
    public static final class UnsupportedCasException extends MediaCasException {
        UnsupportedCasException(String detailMessage) { super(detailMessage); }
    }

    /** The CAS plugin needs provisioning before it can be used. */
    public static final class NotProvisionedException extends MediaCasException {
        NotProvisionedException(String detailMessage) { super(detailMessage); }
    }

    /** The plugin's resources are in use by another client. */
    public static final class ResourceBusyException extends MediaCasException {
        ResourceBusyException(String detailMessage) { super(detailMessage); }
    }

    /** The plugin could not allocate what the request needed. */
    public static final class InsufficientResourceException extends MediaCasException {
        InsufficientResourceException(String detailMessage) { super(detailMessage); }
    }

    /** The provisioning server refused the request. */
    public static final class DeniedByServerException extends MediaCasException {
        DeniedByServerException(String detailMessage) { super(detailMessage); }
    }
}
