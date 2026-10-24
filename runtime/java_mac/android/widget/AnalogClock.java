// android.widget.AnalogClock -- deprecated View that draws a clock face with
// hour/minute hands. Was Absent. We keep the time model + dial/hand drawables
// and a per-minute tick; the actual face is painted by onDraw via Canvas when
// the runtime drives it. AOSP:
//   frameworks/base/core/java/android/widget/AnalogClock.java
//   @Deprecated public class AnalogClock extends View
package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Calendar;

@Deprecated
public class AnalogClock extends View {
    private Calendar mCalendar;
    private Drawable mHourHand;
    private Drawable mMinuteHand;
    private Drawable mDial;
    private int mDialWidth;
    private int mDialHeight;
    private float mMinutes;
    private float mHour;

    private Handler mHandler;
    private Runnable mTick;
    private boolean mTickerStopped = true;

    public AnalogClock(Context context) { super(context); init(); }
    public AnalogClock(Context context, android.util.AttributeSet attrs) {
        super(context); init();
    }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public AnalogClock(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public AnalogClock(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context); init();
    }

    private void init() { mCalendar = Calendar.getInstance(); }

    @Override public void onAttachedToWindow() {
        try { super.onAttachedToWindow(); } catch (Throwable ignored) {}
        mTickerStopped = false;
        ensureHandler();
        if (mTick == null) {
            mTick = new Runnable() {
                @Override public void run() {
                    if (mTickerStopped) return;
                    onTimeChanged();
                    invalidate();
                    mHandler.postDelayed(mTick, 60000);
                }
            };
        }
        mTick.run();
    }

    @Override public void onDetachedFromWindow() {
        try { super.onDetachedFromWindow(); } catch (Throwable ignored) {}
        mTickerStopped = true;
        if (mHandler != null && mTick != null) mHandler.removeCallbacks(mTick);
    }

    private void onTimeChanged() {
        // AOSP AnalogClock.onTimeChanged() reads the clock through mTimeZone
        // when one has been pinned with setTimeZone(String); with none set it
        // follows the system zone. mCalendar is rebuilt by createClock() when
        // the zone changes, so this line is unchanged from before.
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        int hour = mCalendar.get(Calendar.HOUR);
        int minute = mCalendar.get(Calendar.MINUTE);
        int second = mCalendar.get(Calendar.SECOND);
        mMinutes = minute + second / 60.0f;
        mHour = hour + mMinutes / 60.0f;
    }

    private void ensureHandler() {
        if (mHandler == null) mHandler = new Handler(Looper.getMainLooper());
    }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.AnalogClock had 22
    // public members missing -- the Icon-based dial/hand setters added in API
    // 30, their four tint families, and the time-zone pair). Bodies mirror
    // frameworks/base/core/java/android/widget/AnalogClock.java
    // (android16-release); cited line numbers are from that file.
    // ==================================================================

    private Drawable mSecondHand;
    private String mTimeZone;

    // AOSP AnalogClock.TintInfo (:899) -- one per tintable drawable. Kept as a
    // real inner class rather than eight loose fields so `apply` stays a single
    // implementation, exactly as AOSP has it.
    private final class TintInfo {
        boolean mHasTintList;
        android.content.res.ColorStateList mTintList;
        boolean mHasTintBlendMode;
        android.graphics.BlendMode mTintBlendMode;

        /** AOSP TintInfo.apply(Drawable) -- a MUTATED copy with the tint
         *  applied, or null for a null input.
         *  One substitution: AOSP calls Drawable.setTintBlendMode(BlendMode),
         *  which in this tree is an empty method
         *  (android/graphics/drawable/Drawable.java:208, owned by the graphics
         *  surface), so the tint would silently not apply. We push the same
         *  blend through the PorterDuff.Mode entry point instead, which our
         *  Drawable does honour. For every blend that has a PorterDuff pair
         *  this is the identical operation; the Skia-only blends convert to
         *  null, which is also what AOSP's own conversion answers. */
        Drawable apply(Drawable drawable) {
            if (drawable == null) return null;
            Drawable newDrawable = drawable.mutate();
            if (mHasTintList) newDrawable.setTintList(mTintList);
            if (mHasTintBlendMode) {
                newDrawable.setTintMode(MacApkBlendModes.toPorterDuff(mTintBlendMode));
            }
            // AOSP: "All drawables should have the same state as the View itself."
            if (drawable.isStateful()) newDrawable.setState(getDrawableState());
            return newDrawable;
        }
    }

    private final TintInfo mDialTintInfo = new TintInfo();
    private final TintInfo mHourHandTintInfo = new TintInfo();
    private final TintInfo mMinuteHandTintInfo = new TintInfo();
    private final TintInfo mSecondHandTintInfo = new TintInfo();

    /** AOSP :225 -- setDial also caches the intrinsic size, because the dial is
     *  what AnalogClock measures itself from. */
    public void setDial(android.graphics.drawable.Icon icon) {
        mDial = icon.loadDrawable(getContext());
        if (mDial != null) {
            mDialWidth = mDial.getIntrinsicWidth();
            mDialHeight = mDial.getIntrinsicHeight();
        }
        if (mDialTintInfo.mHasTintList || mDialTintInfo.mHasTintBlendMode) {
            mDial = mDialTintInfo.apply(mDial);
        }
        invalidate();
    }
    /** AOSP :251 / :265 / :281 / :295. Note AOSP re-applies to the CURRENT
     *  drawable immediately, so a tint set after the icon still takes. */
    public void setDialTintList(android.content.res.ColorStateList tint) {
        mDialTintInfo.mTintList = tint;
        mDialTintInfo.mHasTintList = true;
        mDial = mDialTintInfo.apply(mDial);
    }
    public android.content.res.ColorStateList getDialTintList() { return mDialTintInfo.mTintList; }
    public void setDialTintBlendMode(android.graphics.BlendMode blendMode) {
        mDialTintInfo.mTintBlendMode = blendMode;
        mDialTintInfo.mHasTintBlendMode = true;
        mDial = mDialTintInfo.apply(mDial);
    }
    public android.graphics.BlendMode getDialTintBlendMode() { return mDialTintInfo.mTintBlendMode; }

    /** AOSP :301 and the hour-hand tint family that follows it. */
    public void setHourHand(android.graphics.drawable.Icon icon) {
        mHourHand = icon.loadDrawable(getContext());
        if (mHourHandTintInfo.mHasTintList || mHourHandTintInfo.mHasTintBlendMode) {
            mHourHand = mHourHandTintInfo.apply(mHourHand);
        }
        invalidate();
    }
    public void setHourHandTintList(android.content.res.ColorStateList tint) {
        mHourHandTintInfo.mTintList = tint;
        mHourHandTintInfo.mHasTintList = true;
        mHourHand = mHourHandTintInfo.apply(mHourHand);
    }
    public android.content.res.ColorStateList getHourHandTintList() { return mHourHandTintInfo.mTintList; }
    public void setHourHandTintBlendMode(android.graphics.BlendMode blendMode) {
        mHourHandTintInfo.mTintBlendMode = blendMode;
        mHourHandTintInfo.mHasTintBlendMode = true;
        mHourHand = mHourHandTintInfo.apply(mHourHand);
    }
    public android.graphics.BlendMode getHourHandTintBlendMode() { return mHourHandTintInfo.mTintBlendMode; }

    /** AOSP minute-hand family. */
    public void setMinuteHand(android.graphics.drawable.Icon icon) {
        mMinuteHand = icon.loadDrawable(getContext());
        if (mMinuteHandTintInfo.mHasTintList || mMinuteHandTintInfo.mHasTintBlendMode) {
            mMinuteHand = mMinuteHandTintInfo.apply(mMinuteHand);
        }
        invalidate();
    }
    public void setMinuteHandTintList(android.content.res.ColorStateList tint) {
        mMinuteHandTintInfo.mTintList = tint;
        mMinuteHandTintInfo.mHasTintList = true;
        mMinuteHand = mMinuteHandTintInfo.apply(mMinuteHand);
    }
    public android.content.res.ColorStateList getMinuteHandTintList() { return mMinuteHandTintInfo.mTintList; }
    public void setMinuteHandTintBlendMode(android.graphics.BlendMode blendMode) {
        mMinuteHandTintInfo.mTintBlendMode = blendMode;
        mMinuteHandTintInfo.mHasTintBlendMode = true;
        mMinuteHand = mMinuteHandTintInfo.apply(mMinuteHand);
    }
    public android.graphics.BlendMode getMinuteHandTintBlendMode() { return mMinuteHandTintInfo.mTintBlendMode; }

    /** AOSP second-hand family. AOSP's setSecondHand(null-icon) is not allowed
     *  (@NonNull), and a second hand only ticks when the clock is visible; we
     *  keep the drawable so the getters and any future painting see it. */
    public void setSecondHand(android.graphics.drawable.Icon icon) {
        mSecondHand = icon == null ? null : icon.loadDrawable(getContext());
        if (mSecondHandTintInfo.mHasTintList || mSecondHandTintInfo.mHasTintBlendMode) {
            mSecondHand = mSecondHandTintInfo.apply(mSecondHand);
        }
        invalidate();
    }
    public void setSecondHandTintList(android.content.res.ColorStateList tint) {
        mSecondHandTintInfo.mTintList = tint;
        mSecondHandTintInfo.mHasTintList = true;
        mSecondHand = mSecondHandTintInfo.apply(mSecondHand);
    }
    public android.content.res.ColorStateList getSecondHandTintList() { return mSecondHandTintInfo.mTintList; }
    public void setSecondHandTintBlendMode(android.graphics.BlendMode blendMode) {
        mSecondHandTintInfo.mTintBlendMode = blendMode;
        mSecondHandTintInfo.mHasTintBlendMode = true;
        mSecondHand = mSecondHandTintInfo.apply(mSecondHand);
    }
    public android.graphics.BlendMode getSecondHandTintBlendMode() { return mSecondHandTintInfo.mTintBlendMode; }

    // ---- time zone ----------------------------------------------------
    /** AOSP :568 / :548. AOSP holds a java.time.ZoneId and returns its id;
     *  null means "follow the system zone", which is the state a clock that
     *  never had setTimeZone called is in -- so getTimeZone() answers null
     *  there, not the system zone's name. This port keeps the id string and
     *  rebuilds mCalendar on the matching java.util.TimeZone, which is the
     *  same clock. */
    public void setTimeZone(String timeZone) {
        mTimeZone = timeZone;
        createClock();
        onTimeChanged();
        invalidate();
    }
    public String getTimeZone() { return mTimeZone; }

    /** AOSP createClock() -- rebuild the calendar on the current zone. */
    private void createClock() {
        mCalendar = (mTimeZone == null)
                ? Calendar.getInstance()
                : Calendar.getInstance(java.util.TimeZone.getTimeZone(mTimeZone));
    }
}
