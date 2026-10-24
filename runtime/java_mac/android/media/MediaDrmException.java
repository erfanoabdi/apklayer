package android.media;

/**
 * Base for the MediaDrm checked exceptions.
 * Source: frameworks/base/media/java/android/media/MediaDrmException.java
 */
public class MediaDrmException extends Exception implements MediaDrmThrowable {
    public MediaDrmException(String detailMessage) { super(detailMessage); }
}
