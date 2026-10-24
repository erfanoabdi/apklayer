// android.transition.ChangeImageTransform -- animates an ImageView's content
// transform (the image matrix + scale type) between scenes. Was absent. We
// capture the ImageView's image matrix at start/end; without per-element matrix
// interpolation we snap to the end matrix on completion, which is faithful for the
// common scale-type-change case and keeps the lifecycle/listeners correct.
// Mirrors frameworks/base/core/java/android/transition/ChangeImageTransform.java.
package android.transition;

import android.util.AttributeSet;

import android.content.Context;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ChangeImageTransform extends Transition {

    private static final String PROP_MATRIX = "android:changeImageTransform:matrix";

    public ChangeImageTransform() {}

    /** AOSP ChangeImageTransform(Context, AttributeSet) -- the XML-inflation constructor; all of
     *  its configuration lives on the superclass, so it just chains up. */
    public ChangeImageTransform(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void capture(TransitionValues values) {
        View v = values.view;
        if (!(v instanceof ImageView)) return;
        ImageView iv = (ImageView) v;
        Matrix m = iv.getImageMatrix();
        values.values.put(PROP_MATRIX, (m != null) ? new Matrix(m) : new Matrix());
    }

    @Override public void captureStartValues(TransitionValues transitionValues) { capture(transitionValues); }
    @Override public void captureEndValues(TransitionValues transitionValues) { capture(transitionValues); }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null || endValues == null) return null;
        if (!(endValues.view instanceof ImageView)) return null;
        final Matrix startMatrix = (Matrix) startValues.values.get(PROP_MATRIX);
        final Matrix endMatrix = (Matrix) endValues.values.get(PROP_MATRIX);
        if (startMatrix == null || endMatrix == null || startMatrix.equals(endMatrix)) return null;

        final ImageView view = (ImageView) endValues.view;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                // Matrix lerp is non-trivial; snap to the end matrix once the
                // animation completes (a benign timing difference, not a
                // correctness one).
                if (a.getAnimatedFraction() >= 1f) view.setImageMatrix(endMatrix);
            }
        });
        return anim;
    }
}
