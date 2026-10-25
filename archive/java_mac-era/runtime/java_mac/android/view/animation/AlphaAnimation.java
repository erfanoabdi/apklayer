// android.view.animation.AlphaAnimation -- fades a view between two alpha values.
// Was a ctor-only stub that never set alpha. Now interpolates fromAlpha->toAlpha
// into Transformation.setAlpha each frame (AOSP behavior). Mirrors
// frameworks/base/core/java/android/view/animation/AlphaAnimation.java.
package android.view.animation;

public class AlphaAnimation extends Animation {
    // Not final: the (Context, AttributeSet) ctor below must call
    // super(context, attrs) first, so it cannot delegate to the 2-float ctor.
    // AOSP does not mark them final either.
    private float mFromAlpha;
    private float mToAlpha;

    public AlphaAnimation(float fromAlpha, float toAlpha) {
        mFromAlpha = fromAlpha;
        mToAlpha = toAlpha;
    }

    /** AOSP AlphaAnimation(Context, AttributeSet) (android16-release
     *  core/java/android/view/animation/AlphaAnimation.java):
     *      mFromAlpha = a.getFloat(AlphaAnimation_fromAlpha, 1.0f);
     *      mToAlpha   = a.getFloat(AlphaAnimation_toAlpha, 1.0f);
     *  Both default to 1.0, so an <alpha> declaring only one end still fades
     *  to/from fully opaque rather than to/from 0. */
    public AlphaAnimation(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        mFromAlpha = AnimationUtils.animFloat(context, attrs, "fromAlpha", 1.0f);
        mToAlpha = AnimationUtils.animFloat(context, attrs, "toAlpha", 1.0f);
    }

    /** AOSP AlphaAnimation.hasAlpha(): "@return true" -- this animation always
     *  changes alpha, which is what lets the draw path skip re-recording a view
     *  whose animation only moves it. */
    @Override public boolean hasAlpha() { return true; }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        final float alpha = mFromAlpha;
        t.setAlpha(alpha + ((mToAlpha - alpha) * interpolatedTime));
    }

    @Override public boolean willChangeTransformationMatrix() { return false; }
    @Override public boolean willChangeBounds() { return false; }
}
