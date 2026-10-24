// android.media.MediaCryptoException -- thrown when a MediaCrypto session cannot
// be created or updated. AOSP frameworks/base
// media/java/android/media/MediaCryptoException.java extends
// java.lang.Exception. The type must resolve because MediaCrypto's constructor
// and setMediaDrmSession declare it in their throws clauses.
package android.media;

public final class MediaCryptoException extends Exception implements MediaDrmThrowable {
    public MediaCryptoException(String detailMessage) { super(detailMessage); }
}
