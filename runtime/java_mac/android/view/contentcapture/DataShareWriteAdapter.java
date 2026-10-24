// android.view.contentcapture.DataShareWriteAdapter -- the callback for
// ContentCaptureManager.shareData: the service either accepts the offer (onWrite
// with a pipe to write into) or rejects it.
// Source: frameworks/base
// core/java/android/view/contentcapture/DataShareWriteAdapter.java.
package android.view.contentcapture;

public interface DataShareWriteAdapter {
    /** The service declined the share. */
    void onRejected();
    /** The service accepted; write the payload into this descriptor and close it. */
    void onWrite(android.os.ParcelFileDescriptor destination);
    /** AOSP declares this `default` with an empty body. */
    default void onError(int errorCode) {}
}
