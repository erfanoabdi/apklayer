// android.view.AttachedSurfaceControl -- the SurfaceControl a ViewRootImpl /
// SurfaceView root is attached to. Mac.apk composites its one window through
// ANGLE/Metal and has no SurfaceControl transaction pipeline (see
// SurfaceControl's own header), so every operation below answers the way a root
// whose transaction was applied immediately, and whose buffer is never
// transformed, answers on a device. API 30+.
// Source: frameworks/base core/java/android/view/AttachedSurfaceControl.java
// (android16-release) -- AOSP declares all but buildReparentTransaction as
// interface DEFAULTS, and so does this.
package android.view;

public interface AttachedSurfaceControl {
    /** AOSP: defer a transaction until the next draw. There is no compositor to
     *  defer to, so nothing is deferred and AOSP's "not applied" answer stands. */
    default boolean applyTransactionOnDraw(SurfaceControl.Transaction t) { return false; }

    /** AOSP AttachedSurfaceControl.getBufferTransformHint(): the transform the
     *  compositor would like the app to pre-apply to its buffers, so a rotation
     *  costs no extra composition pass. Mac.apk never asks for one -- the window
     *  does not rotate under the app -- so the hint is Surface.ROTATION_0, the
     *  same value an unrotated device reports. */
    default int getBufferTransformHint() { return Surface.ROTATION_0; }

    /** AOSP AttachedSurfaceControl.OnBufferTransformHintChangedListener. */
    interface OnBufferTransformHintChangedListener {
        void onBufferTransformHintChanged(int hint);
    }
    /** AOSP add/removeOnBufferTransformHintChangedListener: the hint never
     *  changes here (see getBufferTransformHint), so nothing is ever delivered
     *  and nothing needs to be retained. */
    default void addOnBufferTransformHintChangedListener(
            OnBufferTransformHintChangedListener listener) {}
    default void removeOnBufferTransformHintChangedListener(
            OnBufferTransformHintChangedListener listener) {}

    /** AOSP AttachedSurfaceControl.setTouchableRegion(Region): restrict which
     *  part of the window accepts touch, null meaning "the whole window". Mac.apk
     *  routes every Cocoa mouse event into the view hierarchy and has no
     *  window-manager input region to narrow, so the window stays fully
     *  touchable -- the default state on a device too. */
    default void setTouchableRegion(android.graphics.Region r) {}

    /** AOSP AttachedSurfaceControl.setChildBoundingInsets(Rect): tells the
     *  compositor which part of a child surface is real content. Nothing
     *  composites a child surface here. */
    default void setChildBoundingInsets(android.graphics.Rect insets) {}

    /** AOSP AttachedSurfaceControl.buildReparentTransaction(SurfaceControl):
     *  a transaction that would re-parent the given child under this root.
     *  AOSP returns NULL when the root is not attached to a valid surface --
     *  which is the honest answer here, since there is no root SurfaceControl to
     *  parent anything to (see View.getRootSurfaceControl). Returning an empty
     *  transaction instead would let the caller believe a re-parent had been
     *  queued. */
    default SurfaceControl.Transaction buildReparentTransaction(SurfaceControl child) {
        return null;
    }

    /** AOSP AttachedSurfaceControl.registerOnJankDataListener(Executor,
     *  OnJankDataListener) (API 36): subscribe to per-frame jank classification
     *  from the compositor. Mac.apk has no SurfaceFlinger frame timeline to
     *  classify against, so the registration is live but never reports -- the
     *  same as a device whose layer has produced no frames yet. */
    default SurfaceControl.OnJankDataListenerRegistration registerOnJankDataListener(
            java.util.concurrent.Executor executor,
            SurfaceControl.OnJankDataListener listener) {
        return new SurfaceControl.OnJankDataListenerRegistration();
    }
}
