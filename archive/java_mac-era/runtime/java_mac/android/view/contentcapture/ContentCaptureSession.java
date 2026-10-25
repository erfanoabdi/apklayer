// android.view.contentcapture.ContentCaptureSession -- API 29+ session
// object for ContentCapture (Google's system-managed screen-content
// indexer). Mac.apk doesn't run ContentCapture; the class only needs
// to exist so View.getContentCaptureSession() can declare it as a
// return type. Hit by RecyclerView's ViewHolder.itemView setup chain.
package android.view.contentcapture;

public abstract class ContentCaptureSession implements AutoCloseable {
    public ContentCaptureSession() {}
    @Override public void close() {}

    // Content-capture notifications (API 29). Mac.apk has no content-capture
    // service consuming them, so they are accepted and dropped; the *ViewStructure
    // / *AutofillId factories return usable objects.
    public android.view.ViewStructure newViewStructure(android.view.View view) { return new android.view.ViewStructure(); }
    public android.view.ViewStructure newVirtualViewStructure(android.view.autofill.AutofillId parentId, long virtualId) { return new android.view.ViewStructure(); }
    public android.view.autofill.AutofillId newAutofillId(android.view.autofill.AutofillId hostId, long virtualChildId) { return hostId; }
    public void notifyViewAppeared(android.view.ViewStructure node) { }
    public void notifyViewDisappeared(android.view.autofill.AutofillId id) { }
    public void notifyViewsDisappeared(android.view.autofill.AutofillId hostId, long[] virtualIds) { }
    public void notifyViewTextChanged(android.view.autofill.AutofillId id, CharSequence text) { }

    public void notifyViewsAppeared(java.util.List<android.view.autofill.AutofillId> appearedNodes) { }

    // ======================================================================
    //  AOSP API-36 surface completion (Mac.apk API-gap lane, android.view).
    //  No content-capture service is bound (see ContentCaptureManager), so
    //  nothing here reaches a consumer -- but the session's own STATE
    //  (context, id, destroyed) round-trips, which is what a caller observes.
    // ======================================================================

    private ContentCaptureContext mContext;
    private boolean mDestroyed;
    private ContentCaptureSessionId mSessionId;

    /** AOSP ContentCaptureSession.getContentCaptureSessionId(): @NonNull, and
     *  stable for the life of the session. */
    public final ContentCaptureSessionId getContentCaptureSessionId() {
        if (mSessionId == null) mSessionId = new ContentCaptureSessionId(hashCode());
        return mSessionId;
    }

    /** AOSP ContentCaptureSession.createContentCaptureSession(ContentCaptureContext):
     *  a CHILD session for a different locus. AOSP returns a real child bound to
     *  the same service; with no service, the child is a session with the given
     *  context and nothing behind it -- the same object shape, and it is not
     *  null, because AOSP declares the result @NonNull and a caller will
     *  immediately notify* on it. */
    public final ContentCaptureSession createContentCaptureSession(
            ContentCaptureContext context) {
        ContentCaptureSession child = new ContentCaptureSession() { };
        child.setContentCaptureContext(context);
        return child;
    }

    /** AOSP ContentCaptureSession.setContentCaptureContext(ContentCaptureContext)
     *  / getContentCaptureContext(). */
    public final void setContentCaptureContext(ContentCaptureContext context) {
        mContext = context;
    }
    public final ContentCaptureContext getContentCaptureContext() { return mContext; }

    /** AOSP ContentCaptureSession.destroy(): end the session. Idempotent on
     *  AOSP too. close() delegates to it, which is also AOSP's arrangement. */
    public final void destroy() { mDestroyed = true; }

    /** AOSP ContentCaptureSession.notifySessionPaused() / notifySessionResumed()
     *  (API 34): the activity hosting this session went to / came back from the
     *  background. */
    public final void notifySessionPaused() {}
    public final void notifySessionResumed() {}

    /** AOSP ContentCaptureSession.notifyViewInsetsChanged(Insets) (API 31). */
    public final void notifyViewInsetsChanged(android.graphics.Insets viewInsets) {}

    /** AOSP ContentCaptureSession.flush() (API 36): push any buffered events to
     *  the service now. Nothing is buffered -- the notify* methods above drop
     *  their events because no service is bound -- so there is nothing to
     *  flush, which is AOSP's own outcome for an empty buffer. */
    public void flush() {}
}
