// android.view.ViewOutlineProvider -- AOSP class for supplying an
// outline to a View's elevation shadow / clip path. AppCompat's
// AppBarLayout calls View.setOutlineProvider(ViewOutlineProvider) on
// API 21+, and the three static instances are also compared by identity
// (e.g. `if (view.getOutlineProvider() == BACKGROUND)`).
//
// The three bodies used to be empty. That predated the two consumers
// that now READ the produced Outline and were simply being starved of
// data: View.draw() builds a clip path from getOutline() when
// clipToOutline is set, and View.macapkShadowRadiusPx() /
// ViewGroup.macapkDrawElevationShadow() derive the elevation shadow
// from a non-empty round-rect outline. With an always-empty Outline
// both short-circuited, so a view that opted into the framework-default
// provider (the overwhelmingly common case -- apps rarely hand-write a
// ViewOutlineProvider) got neither corner clipping nor a shadow.
//
// Bodies are AOSP verbatim:
// frameworks/base/core/java/android/view/ViewOutlineProvider.java
package android.view;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;

public abstract class ViewOutlineProvider {
    /** Default outline provider for Views, which queries the Outline from the
     *  View's background, or generates a 0 alpha, rectangular Outline the size
     *  of the View if a background isn't present. The alpha-0 rectangle is
     *  AOSP's sentinel for "shaped like the view, but do not cast a shadow
     *  from it". */
    public static final ViewOutlineProvider BACKGROUND = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            Drawable background = view.getBackground();
            if (background != null) {
                background.getOutline(outline);
            } else {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
                outline.setAlpha(0.0f);
            }
        }
    };

    /** Maintains the outline of the View to match its rectangular bounds, at
     *  1.0f alpha. Lets a View that is opaque but has no background cast a
     *  shadow. */
    public static final ViewOutlineProvider BOUNDS = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            outline.setRect(0, 0, view.getWidth(), view.getHeight());
        }
    };

    /** Maintains the outline of the View to match its rectangular PADDED
     *  bounds, at 1.0f alpha. */
    public static final ViewOutlineProvider PADDED_BOUNDS = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            outline.setRect(view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getWidth() - view.getPaddingRight(),
                    view.getHeight() - view.getPaddingBottom());
        }
    };

    /** Called by a View when its owned Drawables are invalidated, when the
     *  View's size changes, or when View.invalidateOutline() is called. The
     *  input outline is empty and has an alpha of 1.0f. */
    public abstract void getOutline(View view, Outline outline);
}
