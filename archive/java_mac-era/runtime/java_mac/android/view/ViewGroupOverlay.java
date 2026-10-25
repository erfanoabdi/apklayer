// android.view.ViewGroupOverlay -- the ViewGroup specialization of
// ViewOverlay that additionally accepts real View children (not just
// Drawables), used by Material's Snackbar dismiss-animation view and drag
// shadows that need to render a view outside the normal hierarchy. AOSP:
//   frameworks/base/core/java/android/view/ViewGroupOverlay.java
//   public class ViewGroupOverlay extends ViewOverlay
//
// Mac.apk v1.0.92 shipped this as a bare 3-line stub that did not even
// extend ViewOverlay, and nothing in the tree ever constructed it. This
// turn makes the CLASS ITSELF correct (real extends, real View storage,
// drawn on top of the inherited Drawable layer) -- but it is still dead
// code until ViewGroup.getOverlay() (runtime/java/android/view/ViewGroup.java,
// not owned by this change) stops delegating to View's plain
// getOverlay()/ViewOverlay and instead lazily constructs and returns THIS
// type, matching AOSP ViewGroup.java's covariant override. See
// Current_State.md / the audit finding view-core-07 for the remaining
// ViewGroup.java wiring.
package android.view;

public class ViewGroupOverlay extends ViewOverlay {
    private final java.util.List<android.view.View> mViews = new java.util.ArrayList<>();

    public ViewGroupOverlay() { super(); }
    public ViewGroupOverlay(android.view.View host) { super(host); }

    /** AOSP: adds a view to the overlay. The view renders on top of the
     *  host's normal content without being attached to its hierarchy (no
     *  layout pass, no input dispatch) -- visual-only, matching AOSP's
     *  documented contract. Replaces ViewOverlay's inherited no-op. */
    @Override public void add(android.view.View view) {
        if (view != null && !mViews.contains(view)) mViews.add(view);
    }
    @Override public void remove(android.view.View view) {
        if (view != null) mViews.remove(view);
    }

    /** Paints the overlay views on top of the Drawable layer ViewOverlay
     *  already paints (AOSP draw order: overlay views above overlay
     *  drawables). Each view draws at its own left/top within the canvas
     *  space macapkDraw is called in, matching how ViewOverlay positions
     *  Drawables via their own bounds. Defensive per-view try/catch mirrors
     *  the existing ViewOverlay Drawable loop so one bad view can't blank
     *  the rest of the overlay. */
    @Override public void macapkDraw(android.graphics.Canvas canvas) {
        super.macapkDraw(canvas);
        if (canvas == null || mViews.isEmpty()) return;
        for (android.view.View v : mViews) {
            try {
                canvas.save();
                canvas.translate(v.getLeft(), v.getTop());
                v.draw(canvas);
                canvas.restore();
            } catch (Throwable ignored) {}
        }
    }
}
