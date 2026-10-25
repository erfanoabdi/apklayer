// android.view.ScrollCaptureSession -- the per-capture handle a
// ScrollCaptureCallback receives: the Surface to render into, the scrollable
// bounds it may render, and where those bounds sit in the window.
// Source: frameworks/base core/java/android/view/ScrollCaptureSession.java
// (android16-release).
//
// Mac.apk runs no scroll-capture (long-screenshot) host, so the framework never
// constructs one -- but an app that implements ScrollCaptureCallback compiles
// against these getters, and a test can build a session itself with the public
// constructor AOSP provides.
package android.view;

public class ScrollCaptureSession {
    private final Surface mSurface;
    private final android.graphics.Rect mScrollBounds;
    private final android.graphics.Point mPositionInWindow;

    /** @hide -- Mac.apk keeps the no-arg form the tree already handed out. */
    public ScrollCaptureSession() {
        this(null, new android.graphics.Rect(), new android.graphics.Point());
    }

    /** AOSP ScrollCaptureSession(Surface, Rect, Point). */
    public ScrollCaptureSession(Surface surface, android.graphics.Rect scrollBounds,
            android.graphics.Point positionInWindow) {
        mSurface = surface;
        mScrollBounds = scrollBounds;
        mPositionInWindow = positionInWindow;
    }

    /** AOSP ScrollCaptureSession.getSurface(): the consumer-side Surface the
     *  callback renders each captured tile into. */
    public Surface getSurface() { return mSurface; }

    /** AOSP ScrollCaptureSession.getScrollBounds(): the scrollable area, in the
     *  containing view's local coordinates. */
    public android.graphics.Rect getScrollBounds() { return mScrollBounds; }

    /** AOSP ScrollCaptureSession.getPositionInWindow(): where getScrollBounds()
     *  sits in window coordinates. */
    public android.graphics.Point getPositionInWindow() { return mPositionInWindow; }
}
