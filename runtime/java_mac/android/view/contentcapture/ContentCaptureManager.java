// android.view.contentcapture.ContentCaptureManager -- Context.CONTENT_CAPTURE_SERVICE.
// Source: frameworks/base
// core/java/android/view/contentcapture/ContentCaptureManager.java (android16-release).
//
// Mac.apk bridges NO content-capture service. Every answer below is the one AOSP
// gives when no service is bound -- which is what every device reports unless a
// system content-capture provider is installed and enabled -- rather than a
// stand-in for "unimplemented". In particular shareData() REJECTS through the
// adapter rather than dropping it, so a caller is never left waiting.
package android.view.contentcapture;

public final class ContentCaptureManager {
    public static final int DATA_SHARE_ERROR_UNKNOWN = 1;
    public static final int DATA_SHARE_ERROR_CONCURRENT_REQUEST = 2;
    public static final int DATA_SHARE_ERROR_TIMEOUT_INTERRUPTED = 3;

    private boolean mEnabled;

    public ContentCaptureManager() {}

    /** AOSP isContentCaptureEnabled(): true only when a service is bound AND the
     *  app has not disabled it. No service is bound. */
    public boolean isContentCaptureEnabled() { return false; }

    /** AOSP setContentCaptureEnabled(boolean): the APP's own opt-out. Recorded
     *  (an app that disables then re-enables must not be told it failed), but
     *  isContentCaptureEnabled() stays false because the service half of AOSP's
     *  own condition is not met. */
    public void setContentCaptureEnabled(boolean enabled) { mEnabled = enabled; }

    /** AOSP getServiceComponentName(): the bound service, null when none is. */
    public android.content.ComponentName getServiceComponentName() { return null; }

    /** AOSP getContentCaptureConditions(): the loci the service is interested
     *  in. AOSP returns NULL when the service has not restricted them -- which
     *  is different from an empty set (that would mean "interested in nothing"),
     *  and null is the honest answer with no service at all. */
    public java.util.Set<ContentCaptureCondition> getContentCaptureConditions() { return null; }

    /** AOSP removeData(DataRemovalRequest): ask the service to forget. There is
     *  nothing captured and no service to ask. */
    public void removeData(DataRemovalRequest request) {
        java.util.Objects.requireNonNull(request);
    }

    /** AOSP shareData(DataShareRequest, Executor, DataShareWriteAdapter): offer
     *  a blob to the service. With no service bound the offer cannot be
     *  accepted, and AOSP's outcome for that is onRejected() -- delivered on the
     *  caller's Executor, never dropped. */
    public void shareData(DataShareRequest request, java.util.concurrent.Executor executor,
            DataShareWriteAdapter adapter) {
        java.util.Objects.requireNonNull(request);
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(adapter);
        executor.execute(adapter::onRejected);
    }
}
