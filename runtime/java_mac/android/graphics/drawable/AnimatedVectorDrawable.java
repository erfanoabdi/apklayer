// android.graphics.drawable.AnimatedVectorDrawable -- a VectorDrawable whose
// path/group properties animate. v1.0.426 Tier-2: was an empty stub that did NOT
// extend Drawable, so `Drawable d = getDrawable(avd)` ClassCastException'd and
// `implements Animatable2` failed. Now it extends Drawable and implements
// Animatable2, drawing its (static) inner vector and treating the animation as a
// no-op (we don't run the per-property timeline, but the icon paints its base
// state instead of crashing). Compose rememberAnimatedVectorPainter and AppCompat
// animated-vector selectors (checkbox check, FAB morph) instantiate this.
// Source: frameworks/base/.../graphics/drawable/AnimatedVectorDrawable.java
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import java.util.ArrayList;
import java.util.List;

public class AnimatedVectorDrawable extends Drawable implements Animatable2 {
    private Drawable inner;   // the underlying VectorDrawable (base/static frame)
    private boolean running;
    private final List<AnimationCallback> callbacks = new ArrayList<>();

    public AnimatedVectorDrawable() {}

    /** Mac.apk hook: the resource inflater hands us the base vector to paint. */
    public void macapkSetInner(Drawable d) { this.inner = d; }
    public Drawable macapkInner() { return inner; }

    @Override public void draw(Canvas canvas) {
        if (inner != null) { inner.setBounds(getBounds()); inner.draw(canvas); }
    }
    @Override public void setBounds(int l, int t, int r, int b) {
        super.setBounds(l, t, r, b);
        if (inner != null) inner.setBounds(l, t, r, b);
    }
    @Override public void setAlpha(int alpha) { if (inner != null) inner.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { if (inner != null) inner.setColorFilter(cf); }
    /** AOSP AnimatedVectorDrawable.getColorFilter (android14-release :450-453):
     *  `return mAnimatedVectorState.mVectorDrawable.getColorFilter();`. Missing,
     *  so it answered the base null. (The inner VectorDrawable answers null
     *  itself under MACAPK_DRAWABLE_COLOR_FILTER=0.) */
    @Override public ColorFilter getColorFilter() { return inner != null ? inner.getColorFilter() : null; }
    @Override public void setTintList(android.content.res.ColorStateList t) {
        super.setTintList(t);
        if (inner != null) inner.setTintList(t);
    }
    @Override public int getOpacity() { return -3; /* TRANSLUCENT */ }
    @Override public int getIntrinsicWidth()  { return inner != null ? inner.getIntrinsicWidth()  : -1; }
    @Override public int getIntrinsicHeight() { return inner != null ? inner.getIntrinsicHeight() : -1; }

    // Animatable2 -- we don't run the path timeline, so start() settles
    // immediately to the base/end state and notifies callbacks (so a one-shot
    // morph's onAnimationEnd fires and the caller proceeds instead of hanging).
    @Override public void start() {
        if (running) return;
        running = true;
        for (AnimationCallback c : new ArrayList<>(callbacks)) try { c.onAnimationStart(this); } catch (Throwable ignore) {}
        running = false;
        for (AnimationCallback c : new ArrayList<>(callbacks)) try { c.onAnimationEnd(this); } catch (Throwable ignore) {}
        invalidateSelf();
    }
    @Override public void stop() { running = false; }
    /** AOSP AnimatedVectorDrawable.reset(): "Resets the AnimatedVectorDrawable
     *  to the start state as specified in the animators." (frameworks/base
     *  graphics/java/android/graphics/drawable/AnimatedVectorDrawable.java,
     *  android11-release, ~L910-919). No @Override: reset() is not part of
     *  Animatable/Animatable2, just a plain public method on this class.
     *
     *  AOSP's real implementation is a trick: ensureAnimatorSet(), then
     *  VectorDrawableAnimatorUI.reset() calls start() (which snaps every
     *  animated property to its time-0 value via the real Animator
     *  framework) and immediately cancel()s it, so the timeline never
     *  advances past that. There is no public "just apply the initial
     *  values" API, hence the start-then-cancel trick.
     *
     *  We don't run a per-property timeline at all (see macapkSetInner):
     *  draw() always paints inner's base/static frame regardless of
     *  `running`, so there is no separate "start value" for the properties
     *  to snap to -- inner is already permanently at that frame. The
     *  faithful subset of the contract is therefore "stop the animation and
     *  repaint": we deliberately do NOT replay the onAnimationStart/
     *  onAnimationEnd callback pair the AOSP start-then-cancel trick incurs
     *  as a side effect, since that is an artifact of how real Android has
     *  to fake a synchronous property-snap, not a documented part of what
     *  reset() promises -- and start() (above) already owns the "fire the
     *  callbacks" contract for this stub. */
    public void reset() {
        running = false;
        invalidateSelf();
    }
    @Override public boolean isRunning() { return running; }
    @Override public void registerAnimationCallback(AnimationCallback callback) {
        if (callback != null && !callbacks.contains(callback)) callbacks.add(callback);
    }
    @Override public boolean unregisterAnimationCallback(AnimationCallback callback) {
        return callbacks.remove(callback);
    }
    @Override public void clearAnimationCallbacks() { callbacks.clear(); }
}
