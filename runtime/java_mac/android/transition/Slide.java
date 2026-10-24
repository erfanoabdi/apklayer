// android.transition.Slide -- slides a view in from / out to an edge of the
// scene. Common for fragment transitions and side panels. Was absent.
// Mirrors frameworks/base/core/java/android/transition/Slide.java: onAppear
// translates from the off-screen edge to 0, onDisappear translates 0 to the
// off-screen edge. We compute the off-screen distance from the scene-root size.
package android.transition;

import android.util.AttributeSet;
import android.content.Context;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class Slide extends Visibility {

    private int mSlideEdge = Gravity.BOTTOM;

    public Slide() {}

    public Slide(int slideEdge) {
        setSlideEdge(slideEdge);
    }

    /**
     * AOSP Slide.java:154-160 -- the XML-inflation constructor. Reads
     * android:slideEdge; attrs.xml maps left=0x03 top=0x30 right=0x05
     * bottom=0x50 start=0x00800003 end=0x00800005 (the Gravity constants), and
     * AOSP's default when the attribute is absent is Gravity.BOTTOM.
     */
    public Slide(Context context, AttributeSet attrs) {
        super(context, attrs);
        int edge = Transition.getEnumAttr(attrs, "slideEdge",
                new String[] { "left", "top", "right", "bottom", "start", "end" },
                new int[] { 0x03, 0x30, 0x05, 0x50, 0x00800003, 0x00800005 },
                Gravity.BOTTOM);
        setSlideEdge(edge);
    }

    public void setSlideEdge(int slideEdge) { mSlideEdge = slideEdge; }
    public int getSlideEdge() { return mSlideEdge; }

    private float[] offset(ViewGroup sceneRoot, View view) {
        // Returns {dx, dy}: the off-screen translation for the configured edge.
        float w = sceneRoot != null ? sceneRoot.getWidth() : view.getWidth();
        float h = sceneRoot != null ? sceneRoot.getHeight() : view.getHeight();
        switch (mSlideEdge) {
            case Gravity.LEFT:
            case Gravity.START:  return new float[] { -w, 0f };
            case Gravity.RIGHT:
            case Gravity.END:    return new float[] {  w, 0f };
            case Gravity.TOP:    return new float[] { 0f, -h };
            case Gravity.BOTTOM:
            default:             return new float[] { 0f,  h };
        }
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (view == null) return null;
        float[] o = offset(sceneRoot, view);
        return slide(view, o[0], 0f, o[1], 0f);
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (view == null) return null;
        float[] o = offset(sceneRoot, view);
        return slide(view, 0f, o[0], 0f, o[1]);
    }

    private Animator slide(final View view, float startX, float endX, float startY, float endY) {
        view.setTranslationX(startX);
        view.setTranslationY(startY);
        Animator anim;
        if (startX != endX && startY != endY) {
            // Both axes; drive X with the primary animator and Y with a listener.
            // For our purposes a single-axis dominant slide is sufficient: combine
            // into the X animator and snap Y in the end listener.
            anim = ObjectAnimator.ofFloat(view, "translationX", startX, endX);
            final float fy = endY;
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { view.setTranslationY(fy); }
            });
        } else if (startX != endX) {
            anim = ObjectAnimator.ofFloat(view, "translationX", startX, endX);
        } else {
            anim = ObjectAnimator.ofFloat(view, "translationY", startY, endY);
        }
        return anim;
    }
}
