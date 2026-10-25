// android.view.animation.LayoutAnimationController -- staggers one Animation
// across a ViewGroup's children, delaying each child by its index.
// Source: frameworks/base core/java/android/view/animation/LayoutAnimationController.java
// (android16-release).
//
// ViewGroup in this tree holds a controller (setLayoutAnimation) but does not
// yet run the staggered pass, so nothing here is driven by the framework. The
// arithmetic below is AOSP's own, verbatim, so an app that calls
// getAnimationForView(child) itself -- the documented way to reuse a controller
// outside a ViewGroup -- gets the same Animation a device produces.
package android.view.animation;

import android.view.View;
import android.view.ViewGroup;

public class LayoutAnimationController {
    public static final int ORDER_NORMAL = 0;
    public static final int ORDER_REVERSE = 1;
    public static final int ORDER_RANDOM = 2;

    protected Animation mAnimation;
    /** AOSP's protected mInterpolator: shapes how the per-child delay ramps
     *  across the group. Null until getDelayForView needs one, then Linear. */
    protected Interpolator mInterpolator;
    /** AOSP's protected mRandomizer: allocated lazily by ORDER_RANDOM only. */
    protected java.util.Random mRandomizer;

    protected float mDelay = 0.5f;
    protected int mOrder;
    private long mMaxDelay;

    public LayoutAnimationController(Animation animation) { this(animation, 0.5f); }
    public LayoutAnimationController(Animation animation, float delay) {
        mAnimation = animation;
        mDelay = delay;
    }
    /** AOSP LayoutAnimationController(Context, AttributeSet)
     *  (core/java/android/view/animation/LayoutAnimationController.java,
     *  android14-release):
     *      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LayoutAnimation);
     *      Animation.Description d = Animation.Description.parseValue(
     *              a.peekValue(R.styleable.LayoutAnimation_delay), context);
     *      mDelay = d.value;
     *      int resource = a.getResourceId(R.styleable.LayoutAnimation_animation, 0);
     *      if (resource > 0) setAnimation(context, resource);
     *      resource = a.getResourceId(R.styleable.LayoutAnimation_interpolator, 0);
     *      if (resource > 0) setInterpolator(context, resource);
     *      setOrder(a.getInt(R.styleable.LayoutAnimation_animationOrder, ORDER_NORMAL));
     *
     *  This body was EMPTY, which is why AnimationUtils.loadLayoutAnimation
     *  could only ever hand back a controller whose mAnimation was null. An app
     *  that then reads `controller.getAnimation().getDuration()` -- a normal
     *  thing to do, and what Aegis 3.4.2 does in MainActivity.loadEntries --
     *  gets an NPE out of onStart. Found by the 2026-08-27 fleet census.
     *
     *  Attributes are read BY NAME rather than through obtainStyledAttributes +
     *  com.android.internal.R.styleable, which this tree has no table for. That
     *  is the same idiom AnimationUtils.rawAttr uses for every other animation
     *  attribute here, and it handles both the android-namespaced and the
     *  bare spelling our decoded XML produces. */
    public LayoutAnimationController(android.content.Context context,
            android.util.AttributeSet attrs) {
        // delay: AOSP parses it through Animation.Description, which accepts a
        // bare float ("0.5") or a percentage ("30%"); both mean a fraction of
        // the child animation's duration. Default stays AOSP's 0.5f.
        String delay = AnimationUtils.rawAttr(attrs, "delay");
        if (delay != null) {
            try {
                String d = delay.trim();
                mDelay = d.endsWith("%")
                        ? Float.parseFloat(d.substring(0, d.length() - 1)) / 100f
                        : Float.parseFloat(d);
            } catch (NumberFormatException ignored) { /* keep the 0.5f default */ }
        }
        int animId = macapkResIdOf(context, AnimationUtils.rawAttr(attrs, "animation"), "anim");
        if (animId != 0) setAnimation(context, animId);
        int interpId = macapkResIdOf(context,
                AnimationUtils.rawAttr(attrs, "interpolator"), "interpolator");
        if (interpId != 0) setInterpolator(context, interpId);
        String order = AnimationUtils.rawAttr(attrs, "animationOrder");
        if (order != null) {
            switch (order.trim()) {
                case "reverse": setOrder(ORDER_REVERSE); break;
                case "random":  setOrder(ORDER_RANDOM);  break;
                default:        setOrder(ORDER_NORMAL);  break;
            }
        }
    }

    /** Resolve an attribute's raw resource reference to an id. Accepts the two
     *  spellings our decoded XML produces: "@anim/name" (apktool text) and
     *  "@0x7f010001" (a compiled TYPE_REFERENCE rendered by BinaryXmlParser).
     *  Returns 0 when absent or unresolvable, which both callers treat as
     *  "attribute not set" exactly as AOSP's getResourceId(..., 0) does. */
    private static int macapkResIdOf(android.content.Context context,
                                     String raw, String defType) {
        if (context == null || raw == null) return 0;
        String v = raw.trim();
        if (v.isEmpty() || v.charAt(0) != '@') return 0;
        v = v.substring(1);
        if (v.startsWith("0x") || v.startsWith("0X")) {
            try { return (int) Long.parseLong(v.substring(2), 16); }
            catch (NumberFormatException ignored) { return 0; }
        }
        int slash = v.indexOf('/');
        String type = slash >= 0 ? v.substring(0, slash) : defType;
        String name = slash >= 0 ? v.substring(slash + 1) : v;
        String pkg = null;
        int colon = type.indexOf(':');
        if (colon >= 0) { pkg = type.substring(0, colon); type = type.substring(colon + 1); }
        try {
            android.content.res.Resources res = context.getResources();
            if (res == null) return 0;
            return res.getIdentifier(name, type,
                    pkg != null ? pkg : context.getPackageName());
        } catch (Throwable ignored) { return 0; }
    }

    public void setAnimation(Animation animation) { mAnimation = animation; }
    /** AOSP LayoutAnimationController.setAnimation(Context, int animResId):
     *  loads the animation from resources and installs it. */
    public void setAnimation(android.content.Context context, int resourceID) {
        setAnimation(AnimationUtils.loadAnimation(context, resourceID));
    }
    public Animation getAnimation() { return mAnimation; }

    public void setInterpolator(Interpolator interpolator) { mInterpolator = interpolator; }
    /** AOSP LayoutAnimationController.setInterpolator(Context, int). */
    public void setInterpolator(android.content.Context context, int resourceID) {
        setInterpolator(AnimationUtils.loadInterpolator(context, resourceID));
    }
    /** AOSP LayoutAnimationController.getInterpolator(). */
    public Interpolator getInterpolator() { return mInterpolator; }

    public void setDelay(float delay) { mDelay = delay; }
    public float getDelay() { return mDelay; }
    public void setOrder(int order) { mOrder = order; }
    public int getOrder() { return mOrder; }

    /** AOSP LayoutAnimationController.willOverlap():293 `return mDelay < 1.0f`. */
    public boolean willOverlap() { return mDelay < 1.0f; }

    public void start() {
        if (mAnimation != null) {
            mAnimation.setStartTime(-1);
            mMaxDelay = Long.MIN_VALUE;
        }
    }

    /** AOSP LayoutAnimationController.isDone(): true once the whole staggered
     *  run has finished. */
    public boolean isDone() {
        if (mAnimation == null) return true;
        return AnimationUtils.currentAnimationTimeMillis()
                > mAnimation.getStartTime() + mMaxDelay + mAnimation.getDuration();
    }

    /** AOSP LayoutAnimationController.getAnimationForView(View):322 -- a CLONE
     *  of the shared animation with this child's start offset applied. */
    public final Animation getAnimationForView(View view) {
        if (mAnimation == null) return null;
        final long delay = getDelayForView(view) + mAnimation.getStartOffset();
        mMaxDelay = Math.max(mMaxDelay, delay);
        try {
            final Animation animation = mAnimation.clone();
            animation.setStartOffset(delay);
            return animation;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    /** AOSP LayoutAnimationController.getDelayForView(View):370, verbatim. */
    protected long getDelayForView(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null || mAnimation == null) return 0;
        AnimationParameters params = lp.layoutAnimationParameters;
        if (params == null) return 0;

        final float delay = mDelay * mAnimation.getDuration();
        final long viewDelay = (long) (getTransformedIndex(params) * delay);
        final float totalDelay = delay * params.count;

        if (mInterpolator == null) mInterpolator = new LinearInterpolator();

        float normalizedDelay = totalDelay == 0f ? 0f : viewDelay / totalDelay;
        normalizedDelay = mInterpolator.getInterpolation(normalizedDelay);
        return (long) (normalizedDelay * totalDelay);
    }

    /** AOSP LayoutAnimationController.getTransformedIndex(AnimationParameters):403,
     *  verbatim -- this is where ORDER_REVERSE / ORDER_RANDOM take effect. */
    protected int getTransformedIndex(AnimationParameters params) {
        switch (getOrder()) {
            case ORDER_REVERSE:
                return params.count - 1 - params.index;
            case ORDER_RANDOM:
                if (mRandomizer == null) mRandomizer = new java.util.Random();
                return (int) (params.count * mRandomizer.nextFloat());
            case ORDER_NORMAL:
            default:
                return params.index;
        }
    }

    /** AOSP LayoutAnimationController.AnimationParameters -- the per-child
     *  bookkeeping a ViewGroup attaches to each child's LayoutParams
     *  (ViewGroup.attachLayoutAnimationParameters). */
    public static class AnimationParameters {
        public AnimationParameters() {}
        /** Number of children in the group being animated. */
        public int count;
        /** This child's index within the group. */
        public int index;
    }
}
