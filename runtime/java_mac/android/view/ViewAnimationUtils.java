// android.view.ViewAnimationUtils -- createCircularReveal(). Was absent. Material
// ripples, FAB-to-sheet reveals and many "circular reveal" transitions call this
// and start the returned Animator; a missing class NoClassDefFound'd at load and
// a null return NPE'd at .start().
//
// AOSP returns a RevealAnimator (a ValueAnimator subclass) that animates the
// reveal radius from startRadius to endRadius and clips the view to that circle
// each frame via the hardware renderer. Mac.apk's view draw path does not do
// hardware circular clipping, so we return a real ValueAnimator over the radius
// range that invalidates the target each frame: the Animator's timing,
// getAnimatedValue() (the current radius), and start/end lifecycle are all
// faithful; the view simply appears immediately rather than wiped in by a circle.
// This keeps callers (which chain .setDuration().start() and add end-listeners to
// e.g. remove the view) working correctly. Mirrors
// frameworks/base/core/java/android/view/ViewAnimationUtils.java.
package android.view;

import android.animation.Animator;
import android.animation.ValueAnimator;

public final class ViewAnimationUtils {
    private ViewAnimationUtils() {}

    public static Animator createCircularReveal(final View view, int centerX, int centerY,
            float startRadius, float endRadius) {
        if (view == null) {
            throw new IllegalArgumentException("View cannot be null");
        }
        final ValueAnimator anim = ValueAnimator.ofFloat(startRadius, endRadius);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                // Re-draw the target as the reveal radius advances. Our renderer
                // does not actually clip to the circle, but invalidating keeps
                // any per-frame view state the caller mutates in sync.
                try { view.invalidate(); } catch (Throwable ignored) {}
            }
        });
        return anim;
    }
}
