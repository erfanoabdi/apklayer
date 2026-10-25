// android.graphics.drawable.Animatable2 -- AOSP INTERFACE (extends Animatable)
// for drawables that animate + report start/end via callbacks. Was a CLASS, so
// any drawable declared `implements Animatable2` (AnimatedVectorDrawable,
// AnimatedVectorDrawableCompat) threw IncompatibleClassChangeError at class-load
// -- the exact failure the Bundle-Parcelable audit targets. Making it an
// interface lets those implement it. v1.0.422.
package android.graphics.drawable;

public interface Animatable2 extends Animatable {
    void registerAnimationCallback(AnimationCallback callback);
    boolean unregisterAnimationCallback(AnimationCallback callback);
    void clearAnimationCallbacks();

    abstract class AnimationCallback {
        public void onAnimationStart(Drawable drawable) {}
        public void onAnimationEnd(Drawable drawable) {}
    }
}
