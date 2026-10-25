// android.view.ViewOverlay -- AOSP API 18+ overlay layer for adding
// transient drawables/views on top of a host View without modifying
// its hierarchy. Material BadgeDrawable.attachBadgeContentDescription
// uses this to display the badge above the host icon.
//
// v1.0.101: actually renders. Previously stored drawables but never
// drew them. ViewOverlay.draw is invoked from View.draw after the
// view's onDraw + dispatchDraw, so the overlay drawables paint on
// TOP of the host view's content -- the AOSP semantic.
//
// Generic for: Material BadgeDrawable, RecyclerView item-decoration
// overlays, dialog-fragment-driven transient drawables, snackbar's
// view-overlay attachment during the slide-in animation.
package android.view;

import java.util.ArrayList;
import java.util.List;

public class ViewOverlay {
    private final List<android.graphics.drawable.Drawable> drawables = new ArrayList<>();
    /** Optional host view reference -- used so a Material BadgeDrawable
     *  that was added with setBounds=empty can be re-coordinated against
     *  the host's current bounds before draw. */
    private final java.lang.ref.WeakReference<android.view.View> hostRef;

    public ViewOverlay() { this.hostRef = new java.lang.ref.WeakReference<>(null); }
    public ViewOverlay(android.view.View host) {
        this.hostRef = new java.lang.ref.WeakReference<>(host);
    }

    public void add(android.graphics.drawable.Drawable drawable) {
        if (drawable != null && !drawables.contains(drawable)) {
            drawables.add(drawable);
        }
    }
    public void remove(android.graphics.drawable.Drawable drawable) {
        if (drawable != null) drawables.remove(drawable);
    }
    public void clear() { drawables.clear(); }

    /** v1.0.101: invoked from View.draw to paint the overlay drawables.
     *  Canvas is positioned at the host view's local origin (parent
     *  ViewGroup.dispatchDraw already translated). Material BadgeDrawable
     *  stores absolute coordinates relative to the host's right/top
     *  corner via updateBadgeCoordinates(host) -- those work in local
     *  coords too since the host's getRight()/getTop() are in its own
     *  parent's space and the badge anchors relative to the host. */
    public void macapkDraw(android.graphics.Canvas canvas) {
        if (canvas == null || drawables.isEmpty()) return;
        android.view.View host = hostRef.get();
        for (android.graphics.drawable.Drawable d : drawables) {
            try {
                // If the drawable is a BadgeDrawable AND its bounds are
                // empty (Material may attach before measure), have it
                // re-coordinate against the host.
                if (host != null) {
                    String dname = d.getClass().getSimpleName();
                    if (dname.endsWith("BadgeDrawable")) {
                        try {
                            java.lang.reflect.Method ubc = d.getClass().getMethod(
                                "updateBadgeCoordinates", android.view.View.class);
                            ubc.invoke(d, host);
                        } catch (Throwable ignored) {}
                    }
                }
                d.draw(canvas);
            } catch (Throwable ignored) {}
        }
    }

    /** v1.0.101: ViewGroupOverlay is the ViewGroup specialization that
     *  also accepts View children (not just drawables). Material's
     *  Snackbar uses this for its dismiss animation. We just inherit
     *  drawable storage from ViewOverlay -- adding views is a no-op for
     *  now (they'd render via their parent's normal draw pass anyway). */
    public void add(android.view.View view) { /* no-op; views render via tree */ }
    public void remove(android.view.View view) {}
}
