// android.view.animation.AnimationSet -- groups several Animations so they play
// together and compose their Transformations. Was an empty stub. Now a faithful
// container: addAnimation collects children, getTransformation drives each child
// at the set's clock and composes their per-frame Transformations, and the set's
// duration is the max of its children (plus offsets). Mirrors
// frameworks/base/core/java/android/view/animation/AnimationSet.java.
package android.view.animation;

import java.util.ArrayList;
import java.util.List;

public class AnimationSet extends Animation {
    private static final int PROPERTY_FILL_AFTER_MASK = 0x1;
    private static final int PROPERTY_FILL_BEFORE_MASK = 0x2;
    private static final int PROPERTY_REPEAT_MODE_MASK = 0x4;
    private static final int PROPERTY_START_OFFSET_MASK = 0x8;
    private static final int PROPERTY_SHARE_INTERPOLATOR_MASK = 0x10;
    private static final int PROPERTY_DURATION_MASK = 0x20;
    private static final int PROPERTY_MORPH_MATRIX_MASK = 0x40;
    private static final int PROPERTY_CHANGE_BOUNDS_MASK = 0x80;

    private int mFlags = 0;
    private boolean mDirty;
    private boolean mHasAlpha;

    private final ArrayList<Animation> mAnimations = new ArrayList<>();

    private Transformation mTempTransformation = new Transformation();

    private long mLastEnd;
    private long[] mStoredOffsets;

    public AnimationSet(boolean shareInterpolator) {
        setFlag(PROPERTY_SHARE_INTERPOLATOR_MASK, shareInterpolator);
        init();
    }

    /** AOSP AnimationSet(Context, AttributeSet) (android16-release
     *  core/java/android/view/animation/AnimationSet.java).
     *
     *  This used to skip the attributes entirely with the note "resources-driven
     *  AnimationSets that reach us already have children added programmatically
     *  by the inflater" -- which was true only because there WAS no XML
     *  inflater; AnimationUtils.loadAnimation returned an empty stub. Now that
     *  createAnimationFromXml builds real sets, a <set> declaring
     *  android:duration or android:startOffset has to have them read, or the
     *  set pushes duration 0 onto every child in initialize().
     *
     *  THE FLAG DANCE IS LOAD-BEARING AND SUBTLE. `super(context, attrs)` calls
     *  setDuration / setFillAfter / setFillBefore / setRepeatMode /
     *  setStartOffset unconditionally, and this class OVERRIDES all five to set
     *  their PROPERTY_*_MASK -- so every mask would end up set whether or not
     *  the XML declared the attribute, and initialize() would then overwrite
     *  each child's own duration with the set's default of 0. AOSP relies on
     *  Java's initialization order to undo that: `private int mFlags = 0;` is a
     *  FIELD INITIALIZER, which runs AFTER the super constructor returns, so it
     *  wipes the masks super() set. The explicit hasValue() re-checks below are
     *  then the real answer. Do not "simplify" mFlags to an uninitialized field:
     *  that silently changes every XML-inflated <set>. */
    public AnimationSet(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);

        setFlag(PROPERTY_SHARE_INTERPOLATOR_MASK,
                AnimationUtils.animBool(attrs, "shareInterpolator", true));
        init();

        // AOSP AnimationSet.java:92-108 (android16-release) wraps exactly these
        // five hasValue() checks in
        //     if (context.getApplicationInfo().targetSdkVersion
        //             >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) { ... }
        // Below ICS none of the masks is set here, so mFlags keeps whatever the
        // field initializer left (0 plus share-interpolator) -- which is what
        // makes a pre-ICS <set> ALWAYS override its children's duration /
        // fillBefore / fillAfter / repeatMode / startOffset rather than only
        // when the attribute was declared on the set.
        //
        // This comment used to read "Every app we run targets far past API 14,
        // so the modern branch is the only reachable one". MEASURED FALSE
        // 2026-08-24: jetpackjoyride's effective target is 8 (it declares no
        // targetSdkVersion, so PackageParser defaults it to minSdk) and
        // frozenbubble targets 12. `debug/compatgate.py --corpus ../Apks`
        // re-measures this in a second; four of the six such claims in this
        // tree were false the day they were checked.
        //
        // The >= branch is LITERALLY the previous code, so nothing changes for
        // an app at or above 14. An unreadable ApplicationInfo resolves to
        // MAX_VALUE and takes that same branch.
        int targetSdkVersion = Integer.MAX_VALUE;
        try {
            android.content.pm.ApplicationInfo ai =
                    (context != null) ? context.getApplicationInfo() : null;
            if (ai != null && android.app.Application.macapkTargetSdkKnown(ai.targetSdkVersion)) targetSdkVersion = ai.targetSdkVersion;
        } catch (Throwable ignored) { }
        if (targetSdkVersion >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (AnimationUtils.hasAttr(attrs, "duration")) mFlags |= PROPERTY_DURATION_MASK;
            if (AnimationUtils.hasAttr(attrs, "fillBefore")) mFlags |= PROPERTY_FILL_BEFORE_MASK;
            if (AnimationUtils.hasAttr(attrs, "fillAfter")) mFlags |= PROPERTY_FILL_AFTER_MASK;
            if (AnimationUtils.hasAttr(attrs, "repeatMode")) mFlags |= PROPERTY_REPEAT_MODE_MASK;
            if (AnimationUtils.hasAttr(attrs, "startOffset")) mFlags |= PROPERTY_START_OFFSET_MASK;
        }
    }

    @Override
    protected AnimationSet clone() throws CloneNotSupportedException {
        final AnimationSet animation = (AnimationSet) super.clone();
        return animation;
    }

    private void setFlag(int mask, boolean value) {
        if (value) mFlags |= mask;
        else mFlags &= ~mask;
    }

    private void init() {
        mStartTime = 0;
    }

    @Override
    public void setFillAfter(boolean fillAfter) {
        mFlags |= PROPERTY_FILL_AFTER_MASK;
        super.setFillAfter(fillAfter);
    }

    @Override
    public void setFillBefore(boolean fillBefore) {
        mFlags |= PROPERTY_FILL_BEFORE_MASK;
        super.setFillBefore(fillBefore);
    }

    @Override
    public void setRepeatMode(int repeatMode) {
        mFlags |= PROPERTY_REPEAT_MODE_MASK;
        super.setRepeatMode(repeatMode);
    }

    @Override
    public void setStartOffset(long startOffset) {
        mFlags |= PROPERTY_START_OFFSET_MASK;
        super.setStartOffset(startOffset);
    }

    /** AOSP AnimationSet.hasAlpha(): true when ANY child changes alpha. The
     *  loop body used to test willChangeTransformationMatrix() and then throw
     *  the answer away, so this always returned false -- the wrong question and
     *  an unused answer. Animation.hasAlpha() now exists (false on the base,
     *  true on AlphaAnimation), so ask it. */
    @Override
    public boolean hasAlpha() {
        if (mDirty) {
            mDirty = mHasAlpha = false;
            final int count = mAnimations.size();
            for (int i = 0; i < count; i++) {
                if (mAnimations.get(i).hasAlpha()) { mHasAlpha = true; break; }
            }
        }
        return mHasAlpha;
    }

    @Override
    public void setDuration(long durationMillis) {
        mFlags |= PROPERTY_DURATION_MASK;
        super.setDuration(durationMillis);
        mLastEnd = mStartOffset + mDuration;
    }

    public void addAnimation(Animation a) {
        mAnimations.add(a);
        boolean noMatrix = (mFlags & PROPERTY_MORPH_MATRIX_MASK) == 0;
        if (noMatrix && a.willChangeTransformationMatrix()) {
            mFlags |= PROPERTY_MORPH_MATRIX_MASK;
        }
        boolean changeBounds = (mFlags & PROPERTY_CHANGE_BOUNDS_MASK) == 0;
        if (changeBounds && a.willChangeBounds()) {
            mFlags |= PROPERTY_CHANGE_BOUNDS_MASK;
        }
        if ((mFlags & PROPERTY_DURATION_MASK) == PROPERTY_DURATION_MASK) {
            mLastEnd = mStartOffset + mDuration;
        } else {
            if (mAnimations.size() == 1) {
                mDuration = a.getStartOffset() + a.getDuration();
                mLastEnd = mStartOffset + mDuration;
            } else {
                mLastEnd = Math.max(mLastEnd, mStartOffset + a.getStartOffset() + a.getDuration());
                mDuration = mLastEnd - mStartOffset;
            }
        }
        mDirty = true;
    }

    public List<Animation> getAnimations() { return mAnimations; }

    @Override
    public boolean getTransformation(long currentTime, Transformation t) {
        final int count = mAnimations.size();
        final Transformation temp = mTempTransformation;

        boolean more = false;
        boolean started = false;
        boolean ended = true;

        t.clear();

        for (int i = count - 1; i >= 0; --i) {
            final Animation a = mAnimations.get(i);
            temp.clear();
            final boolean canRepeat = !a.hasEnded() || a.getRepeatCount() != 0;
            more = a.getTransformation(currentTime, temp,
                    getScaleFactorInternal()) || more;
            t.compose(temp);
            started = started || a.hasStarted();
            ended = a.hasEnded() && ended;
        }

        if (started && !mStarted) {
            if (mListener != null) {
                try { mListener.onAnimationStart(this); } catch (Throwable ignored) {}
            }
            mStarted = true;
        }

        if (ended != mEnded) {
            if (mListener != null) {
                try { mListener.onAnimationEnd(this); } catch (Throwable ignored) {}
            }
            mEnded = ended;
        }

        return more;
    }

    private float getScaleFactorInternal() { return mScaleFactor; }

    @Override
    public void setStartTime(long startTimeMillis) {
        super.setStartTime(startTimeMillis);
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) {
            Animation a = mAnimations.get(i);
            a.setStartTime(startTimeMillis);
        }
    }

    @Override
    public long getStartTime() {
        long startTime = Long.MAX_VALUE;
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) {
            Animation a = mAnimations.get(i);
            startTime = Math.min(startTime, a.getStartTime());
        }
        return startTime;
    }

    @Override
    public void restrictDuration(long durationMillis) {
        super.restrictDuration(durationMillis);
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) {
            mAnimations.get(i).restrictDuration(durationMillis);
        }
    }

    @Override
    public long getDuration() {
        final int count = mAnimations.size();
        long duration = 0;
        final boolean durationSet = (mFlags & PROPERTY_DURATION_MASK) == PROPERTY_DURATION_MASK;
        if (durationSet) {
            duration = mDuration;
        } else {
            for (int i = 0; i < count; i++) {
                duration = Math.max(duration,
                        mAnimations.get(i).getDuration());
            }
        }
        return duration;
    }

    @Override
    public long computeDurationHint() {
        long duration = 0;
        final int count = mAnimations.size();
        for (int i = count - 1; i >= 0; --i) {
            final long d = mAnimations.get(i).computeDurationHint();
            if (d > duration) duration = d;
        }
        return duration;
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        boolean durationSet = (mFlags & PROPERTY_DURATION_MASK) == PROPERTY_DURATION_MASK;
        boolean fillAfterSet = (mFlags & PROPERTY_FILL_AFTER_MASK) == PROPERTY_FILL_AFTER_MASK;
        boolean fillBeforeSet = (mFlags & PROPERTY_FILL_BEFORE_MASK) == PROPERTY_FILL_BEFORE_MASK;
        boolean repeatModeSet = (mFlags & PROPERTY_REPEAT_MODE_MASK) == PROPERTY_REPEAT_MODE_MASK;
        boolean shareInterpolator =
                (mFlags & PROPERTY_SHARE_INTERPOLATOR_MASK) == PROPERTY_SHARE_INTERPOLATOR_MASK;
        boolean startOffsetSet = (mFlags & PROPERTY_START_OFFSET_MASK) == PROPERTY_START_OFFSET_MASK;

        if (shareInterpolator) {
            ensureInterpolator();
        }

        final ArrayList<Animation> children = mAnimations;
        final int count = children.size();

        final long duration = mDuration;
        final boolean fillAfter = mFillAfter;
        final boolean fillBefore = mFillBefore;
        final int repeatMode = mRepeatMode;
        final Interpolator interpolator = mInterpolator;
        final long startOffset = mStartOffset;

        long[] storedOffsets = mStoredOffsets;
        if (startOffsetSet) {
            if (storedOffsets == null || storedOffsets.length != count) {
                storedOffsets = mStoredOffsets = new long[count];
            }
        } else if (storedOffsets != null) {
            storedOffsets = mStoredOffsets = null;
        }

        for (int i = 0; i < count; i++) {
            Animation a = children.get(i);
            if (durationSet) a.setDuration(duration);
            if (fillAfterSet) a.setFillAfter(fillAfter);
            if (fillBeforeSet) a.setFillBefore(fillBefore);
            if (repeatModeSet) a.setRepeatMode(repeatMode);
            if (shareInterpolator) a.setInterpolator(interpolator);
            if (startOffsetSet) {
                long offset = a.getStartOffset();
                a.setStartOffset(offset + startOffset);
                storedOffsets[i] = offset;
            }
            a.initialize(width, height, parentWidth, parentHeight);
        }
    }

    @Override
    public void reset() {
        super.reset();
        restoreChildrenStartOffset();
    }

    void restoreChildrenStartOffset() {
        final long[] offsets = mStoredOffsets;
        if (offsets == null) return;
        final ArrayList<Animation> animations = mAnimations;
        final int count = animations.size();
        for (int i = 0; i < count; i++) {
            animations.get(i).setStartOffset(offsets[i]);
        }
    }

    @Override
    public boolean willChangeTransformationMatrix() {
        return (mFlags & PROPERTY_MORPH_MATRIX_MASK) == PROPERTY_MORPH_MATRIX_MASK;
    }

    @Override
    public boolean willChangeBounds() {
        return (mFlags & PROPERTY_CHANGE_BOUNDS_MASK) == PROPERTY_CHANGE_BOUNDS_MASK;
    }
}
