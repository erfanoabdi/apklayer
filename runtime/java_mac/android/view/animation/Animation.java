// android.view.animation.Animation -- the legacy view-animation base. Was a
// pure no-op (every setter empty, no applyTransformation/getTransformation), so
// XML <set>/<alpha>/<translate>/<scale>/<rotate> animations (splash screens,
// fragment transitions, ListView/RecyclerView item anims, Toast/dialog
// enter-exit) "ran" but never moved a pixel.
//
// This is now the real AOSP state machine: getTransformation(currentTime, out)
// computes a normalized 0..1 time from start/offset/duration, runs it through the
// interpolator, calls the subclass applyTransformation() to fill the
// Transformation, fires start/repeat/end listener callbacks at the right edges,
// and returns whether the animation has more frames. Subclasses (AlphaAnimation,
// TranslateAnimation, ScaleAnimation, RotateAnimation, AnimationSet) override
// applyTransformation. Mirrors
// frameworks/base/core/java/android/view/animation/Animation.java.
package android.view.animation;

public abstract class Animation implements Cloneable {
    public interface AnimationListener {
        void onAnimationStart(Animation animation);
        void onAnimationEnd(Animation animation);
        void onAnimationRepeat(Animation animation);
    }

    public static final int RESTART = 1;
    public static final int REVERSE = 2;
    public static final int INFINITE = -1;
    public static final int ABSOLUTE = 0;
    public static final int RELATIVE_TO_SELF = 1;
    public static final int RELATIVE_TO_PARENT = 2;

    public static final int ZORDER_NORMAL = 0;
    public static final int ZORDER_TOP = 1;
    public static final int ZORDER_BOTTOM = -1;

    // Special start-time sentinels (AOSP).
    private static final long START_ON_FIRST_FRAME = -1;

    long mStartTime = -1;
    long mStartOffset;
    long mDuration;
    int mRepeatCount = 0;
    int mRepeated = 0;
    int mRepeatMode = RESTART;
    Interpolator mInterpolator;
    AnimationListener mListener;

    boolean mFillBefore = true;
    boolean mFillAfter = false;
    boolean mFillEnabled = false;

    boolean mInitialized = false;
    boolean mCycleFlip = false;
    boolean mStarted = false;
    boolean mEnded = false;
    boolean mMore = true;
    boolean mOneMoreTime = true;

    int mZAdjustment = ZORDER_NORMAL;
    float mScaleFactor = 1.0f;

    // Cached dimensions captured at initialize() for RELATIVE_* resolution.
    int mWidth, mHeight, mParentWidth, mParentHeight;

    public Animation() {
        ensureInterpolator();
    }

    /** AOSP Animation(Context, AttributeSet) (android16-release
     *  core/java/android/view/animation/Animation.java, the ctor that reads
     *  R.styleable.Animation): duration, startOffset, fillEnabled/Before/After,
     *  repeatCount, repeatMode, zAdjustment, then the interpolator resource,
     *  then ensureInterpolator().
     *
     *  The attribute values are read as raw XML text through AnimationUtils
     *  rather than a TypedArray -- see the PART D header there for why our
     *  pipeline hands us text where a device hands aapt-compiled TypedValues.
     *
     *  NOT MIRRORED, deliberately: backdropColor / showWallpaper /
     *  hasRoundedCorners / showBackdrop are window-transition properties read by
     *  the system's window animator, which we do not have; a value stored here
     *  would be read by nobody. detachWallpaper is accepted because our setter
     *  already exists and is a no-op, matching that same "nobody reads it". */
    public Animation(android.content.Context context, android.util.AttributeSet attrs) {
        setDuration(AnimationUtils.animInt(context, attrs, "duration", 0));
        setStartOffset(AnimationUtils.animInt(context, attrs, "startOffset", 0));

        setFillEnabled(AnimationUtils.animBool(attrs, "fillEnabled", mFillEnabled));
        setFillBefore(AnimationUtils.animBool(attrs, "fillBefore", mFillBefore));
        setFillAfter(AnimationUtils.animBool(attrs, "fillAfter", mFillAfter));

        setRepeatCount(AnimationUtils.animInt(context, attrs, "repeatCount", mRepeatCount));
        setRepeatMode(AnimationUtils.animEnum(attrs, "repeatMode",
                REPEAT_MODE_NAMES, REPEAT_MODE_VALUES, RESTART));
        setZAdjustment(AnimationUtils.animEnum(attrs, "zAdjustment",
                Z_ADJUSTMENT_NAMES, Z_ADJUSTMENT_VALUES, ZORDER_NORMAL));

        setDetachWallpaper(AnimationUtils.animBool(attrs, "detachWallpaper", false));

        // AOSP: `if (resID > 0) setInterpolator(context, resID);` then
        // ensureInterpolator(). A null answer here leaves the AOSP default
        // (AccelerateDecelerateInterpolator) in place, which is what
        // ensureInterpolator installs.
        Interpolator in = AnimationUtils.animInterpolator(context, attrs);
        if (in != null) setInterpolator(in);

        ensureInterpolator();
    }

    // android:repeatMode / android:zAdjustment enum spellings, from
    // core/res/res/values/attrs.xml's <attr name="repeatMode"> and
    // <attr name="zAdjustment"> declarations.
    private static final String[] REPEAT_MODE_NAMES = { "restart", "reverse" };
    private static final int[] REPEAT_MODE_VALUES = { RESTART, REVERSE };
    private static final String[] Z_ADJUSTMENT_NAMES = { "normal", "top", "bottom" };
    private static final int[] Z_ADJUSTMENT_VALUES = { ZORDER_NORMAL, ZORDER_TOP, ZORDER_BOTTOM };

    /** AOSP Animation.setInterpolator(Context, int): load the interpolator
     *  resource and install it. */
    public void setInterpolator(android.content.Context context, int resID) {
        setInterpolator(AnimationUtils.loadInterpolator(context, resID));
    }

    /** AOSP Animation.getScaleFactor(): the scale the animation was last driven
     *  at (set by getTransformation(long, Transformation, float)). Subclasses
     *  multiply their pivots by it. */
    protected float getScaleFactor() { return mScaleFactor; }

    /** AOSP Animation.hasAlpha(): "Return true if this animation changes the
     *  view's alpha property." The base answer is false; AlphaAnimation and
     *  AnimationSet override. The draw path uses it to decide whether an alpha
     *  animation needs the view re-recorded. */
    public boolean hasAlpha() { return false; }

    /** AOSP Animation.Description (android16-release Animation.java, the
     *  protected static nested class): a size expressed as ABSOLUTE pixels or
     *  as a fraction RELATIVE_TO_SELF / RELATIVE_TO_PARENT. */
    protected static class Description {
        public int type;
        public float value;
    }

    /** The string-pipeline equivalent of Description.parseValue(TypedValue).
     *  AnimationUtils.parseVal has already reproduced the four TypedValue
     *  branches from the pre-aapt text; this maps them onto Description's
     *  ABSOLUTE / RELATIVE_TO_SELF / RELATIVE_TO_PARENT vocabulary exactly as
     *  AOSP's parseValue does (FRACTION -> RELATIVE_*, FLOAT/INT/DIMENSION ->
     *  ABSOLUTE, anything else -> ABSOLUTE 0). */
    static Description describe(AnimationUtils.Val v) {
        Description d = new Description();
        d.type = ABSOLUTE;
        d.value = 0.0f;
        if (v == null) return d;
        switch (v.kind) {
            case AnimationUtils.Val.PARENT:
                d.type = RELATIVE_TO_PARENT; d.value = v.value; break;
            case AnimationUtils.Val.SELF:
                d.type = RELATIVE_TO_SELF; d.value = v.value; break;
            case AnimationUtils.Val.FLOAT:
            case AnimationUtils.Val.DIMEN:
                d.type = ABSOLUTE; d.value = v.value; break;
            default:
                break;
        }
        return d;
    }

    @Override
    protected Animation clone() throws CloneNotSupportedException {
        final Animation animation = (Animation) super.clone();
        return animation;
    }

    public void reset() {
        mPreviousRegion.setEmpty();
        mInitialized = false;
        mCycleFlip = false;
        mRepeated = 0;
        mMore = true;
        mOneMoreTime = true;
        mListenerHandler = null;
    }

    public void cancel() {
        if (mStarted && !mEnded) {
            if (mListener != null) mListener.onAnimationEnd(this);
            mEnded = true;
        }
        mStartTime = Long.MIN_VALUE;
        mStarted = mEnded = true;
    }

    /** AOSP Animation.detach() (android16-release Animation.java):
     *
     *      void detach() {
     *          if (mStarted && !mEnded) { mEnded = true; guard.close(); fireAnimationEnd(); }
     *      }
     *
     *  Called by View.clearAnimation. It is the guarantee that a started
     *  animation which is torn down before it finished STILL delivers exactly
     *  one onAnimationEnd -- the `mStarted && !mEnded` test is what makes it
     *  exactly one, since the normal completion path in getTransformation has
     *  already set mEnded by then.
     *
     *  Package-private in AOSP; same here (View is in android.view, so it
     *  cannot see it -- hence public, the one deviation, with the guard intact).
     *  Made public rather than adding a bridge because a public detach() is
     *  inert to any app that calls it on an unstarted or finished animation. */
    public void detach() {
        if (mStarted && !mEnded) {
            mEnded = true;
            fireAnimationEnd();
        }
    }

    public void setInterpolator(Interpolator i) { mInterpolator = i; }
    public Interpolator getInterpolator() { return mInterpolator; }

    public void setStartTime(long startTimeMillis) {
        mStartTime = startTimeMillis;
        mStarted = mEnded = false;
        mCycleFlip = false;
        mRepeated = 0;
        mMore = true;
    }

    public void start() { setStartTime(-1); }
    public void startNow() { setStartTime(AnimationUtils.currentAnimationTimeMillis()); }

    public void setRepeatMode(int repeatMode) { mRepeatMode = repeatMode; }
    public int getRepeatMode() { return mRepeatMode; }
    public void setRepeatCount(int repeatCount) {
        if (repeatCount < 0) repeatCount = INFINITE;
        mRepeatCount = repeatCount;
    }
    public int getRepeatCount() { return mRepeatCount; }

    public void setFillEnabled(boolean fillEnabled) { mFillEnabled = fillEnabled; }
    public boolean isFillEnabled() { return mFillEnabled; }
    public void setFillBefore(boolean fillBefore) { mFillBefore = fillBefore; }
    public boolean getFillBefore() { return mFillBefore; }
    public void setFillAfter(boolean fillAfter) { mFillAfter = fillAfter; }
    public boolean getFillAfter() { return mFillAfter; }

    public void setStartOffset(long startOffset) { mStartOffset = startOffset; }
    public long getStartOffset() { return mStartOffset; }

    public void setDuration(long durationMillis) {
        if (durationMillis < 0) durationMillis = 0;
        mDuration = durationMillis;
    }
    public long getDuration() { return mDuration; }

    public void restrictDuration(long durationMillis) {
        if (durationMillis < 1) durationMillis = 1;
        if (mStartOffset > durationMillis) {
            mStartOffset = durationMillis;
            mDuration = 0;
            mRepeatCount = 0;
            return;
        }
        long dur = mDuration + mStartOffset;
        if (dur > durationMillis) {
            mDuration = durationMillis - mStartOffset;
            dur = durationMillis;
        }
        if (mDuration <= 0) { mDuration = 0; mRepeatCount = 0; return; }
        if (mRepeatCount < 0 || mRepeatCount > durationMillis
                || (dur * mRepeatCount) > durationMillis) {
            mRepeatCount = (int) (durationMillis / dur) - 1;
            if (mRepeatCount < 0) mRepeatCount = 0;
        }
    }

    public void scaleCurrentDuration(float scale) {
        mDuration = (long) (mDuration * scale);
        mStartOffset = (long) (mStartOffset * scale);
    }

    public void setAnimationListener(AnimationListener listener) { mListener = listener; }

    public void setZAdjustment(int zAdjustment) { mZAdjustment = zAdjustment; }
    public int getZAdjustment() { return mZAdjustment; }

    protected void ensureInterpolator() {
        if (mInterpolator == null) {
            mInterpolator = new AccelerateDecelerateInterpolator();
        }
    }

    public long getStartTime() { return mStartTime; }

    public long computeDurationHint() {
        return (getStartOffset() + getDuration()) * (getRepeatCount() + 1);
    }

    // The core driver. Mirrors AOSP getTransformation: returns true while there
    // are more frames to render.
    public boolean getTransformation(long currentTime, Transformation outTransformation) {
        if (mStartTime == -1) {
            mStartTime = currentTime;
        }

        final long startOffset = getStartOffset();
        final long duration = mDuration;
        float normalizedTime;
        if (duration != 0) {
            normalizedTime = ((float) (currentTime - (mStartTime + startOffset))) / (float) duration;
        } else {
            normalizedTime = currentTime < mStartTime ? 0.0f : 1.0f;
        }

        final boolean expired = normalizedTime >= 1.0f || isCanceled();
        mMore = !expired;

        if (!mFillEnabled) normalizedTime = Math.max(Math.min(normalizedTime, 1.0f), 0.0f);

        if ((normalizedTime >= 0.0f || mFillBefore) && (normalizedTime <= 1.0f || mFillAfter)) {
            if (!mStarted) {
                fireAnimationStart();
                mStarted = true;
            }

            if (mFillEnabled) normalizedTime = Math.max(Math.min(normalizedTime, 1.0f), 0.0f);

            if (mCycleFlip) normalizedTime = 1.0f - normalizedTime;

            final float interpolatedTime = mInterpolator.getInterpolation(normalizedTime);
            applyTransformation(interpolatedTime, outTransformation);
        }

        if (expired) {
            if (mRepeatCount == mRepeated || isCanceled()) {
                if (!mEnded) {
                    mEnded = true;
                    fireAnimationEnd();
                }
            } else {
                if (mRepeatCount > 0) mRepeated++;
                if (mRepeatMode == REVERSE) mCycleFlip = !mCycleFlip;
                mStartTime = -1;
                mMore = true;
                fireAnimationRepeat();
            }
        }

        if (!mMore && mOneMoreTime) {
            mOneMoreTime = false;
            return true;
        }

        return mMore;
    }

    public boolean getTransformation(long currentTime, Transformation outTransformation,
            float scale) {
        mScaleFactor = scale;
        return getTransformation(currentTime, outTransformation);
    }

    public boolean hasStarted() { return mStarted; }
    public boolean hasEnded() { return mEnded; }

    // Subclasses fill outTransformation for the given interpolated 0..1 time.
    protected void applyTransformation(float interpolatedTime, Transformation t) {}

    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        reset();
        mInitialized = true;
        mWidth = width; mHeight = height;
        mParentWidth = parentWidth; mParentHeight = parentHeight;
    }

    public boolean isInitialized() { return mInitialized; }

    public boolean willChangeTransformationMatrix() { return true; }
    public boolean willChangeBounds() { return true; }

    public void setDetachWallpaper(boolean detachWallpaper) {
        // Was a discard; records the value so the AOSP getDetachWallpaper()
        // added below can answer it. Deprecated and inert on AOSP too.
        mDetachWallpaper = detachWallpaper;
    }

    // AOSP resolveSize: turn an (ABSOLUTE | RELATIVE_TO_SELF | RELATIVE_TO_PARENT)
    // dimension value into absolute pixels.
    protected float resolveSize(int type, float value, int size, int parentSize) {
        switch (type) {
            case ABSOLUTE: return value;
            case RELATIVE_TO_SELF: return size * value;
            case RELATIVE_TO_PARENT: return parentSize * value;
            default: return value;
        }
    }

    private boolean isCanceled() { return mStartTime == Long.MIN_VALUE; }

    // Listener dispatch. AOSP posts to the animation's handler when present; we
    // call directly (animations are driven on the main thread in our impl).
    private Object mListenerHandler; // reserved for AOSP parity; unused
    private final android.graphics.Rect mPreviousRegion = new android.graphics.Rect();

    private void fireAnimationStart() {
        if (mListener != null) {
            try { mListener.onAnimationStart(this); } catch (Throwable ignored) {}
        }
    }
    private void fireAnimationRepeat() {
        if (mListener != null) {
            try { mListener.onAnimationRepeat(this); } catch (Throwable ignored) {}
        }
    }
    private void fireAnimationEnd() {
        if (mListener != null) {
            try { mListener.onAnimationEnd(this); } catch (Throwable ignored) {}
        }
    }

    // Helper used by AnimationSet to query timing.
    long getStartOffsetInternal() { return mStartOffset; }
    long getDurationInternal() { return mDuration; }

    // ---- window-animation background / backdrop (AOSP API-36 surface) --------
    // These describe what the WINDOW MANAGER paints behind a window-transition
    // animation; they have no effect on a View animation and AOSP's own bodies
    // are plain field round-trips (WindowManagerService reads them out of the
    // Animation when it runs a window transition). Mac.apk runs no window
    // transitions, so the values round-trip and nothing consumes them -- which
    // is also true on a device for any Animation not handed to the window
    // manager, i.e. every animation an app runs on a View.
    private int mBackdropColor;
    private boolean mShowBackdrop;
    boolean mDetachWallpaper;

    /** AOSP Animation.setBackgroundColor(int) / getBackgroundColor(),
     *  deprecated at API 26: only 0 (transparent) and 0xff000000 (opaque black)
     *  were ever honoured, which is why the replacement is setBackdropColor. */
    @Deprecated
    public void setBackgroundColor(int bg) { mBackdropColor = bg; }
    @Deprecated
    public int getBackgroundColor() { return mBackdropColor; }

    /** AOSP Animation.setBackdropColor(int) / getBackdropColor() (API 33). */
    public void setBackdropColor(int backdropColor) { mBackdropColor = backdropColor; }
    public int getBackdropColor() { return mBackdropColor; }

    /** AOSP Animation.setShowBackdrop(boolean) / getShowBackdrop() (API 33). */
    public void setShowBackdrop(boolean showBackdrop) { mShowBackdrop = showBackdrop; }
    public boolean getShowBackdrop() { return mShowBackdrop; }

    /** AOSP Animation.getDetachWallpaper(), deprecated at API 30 and documented
     *  as having no effect since. The setter already exists earlier in this file. */
    @Deprecated
    public boolean getDetachWallpaper() { return mDetachWallpaper; }
}
