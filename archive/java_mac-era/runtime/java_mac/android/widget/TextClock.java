// android.widget.TextClock -- a TextView that shows the current date/time
// using 12- or 24-hour format patterns, ticking each minute (or second).
// Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/TextClock.java
//   public class TextClock extends TextView
package android.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class TextClock extends TextView {
    public static final CharSequence DEFAULT_FORMAT_12_HOUR = "h:mm a";
    public static final CharSequence DEFAULT_FORMAT_24_HOUR = "H:mm";

    private CharSequence mFormat12 = DEFAULT_FORMAT_12_HOUR;
    private CharSequence mFormat24 = DEFAULT_FORMAT_24_HOUR;
    private CharSequence mFormat;
    private boolean mHasSeconds;
    private String mTimeZone;
    private Calendar mTime;

    private Handler mHandler;
    private Runnable mTick;
    private boolean mRegistered;

    public TextClock(Context context) { this(context, null, 0); }
    public TextClock(Context context, android.util.AttributeSet attrs) {
        this(context, attrs, 0);
    }
    /** AOSP API 21 four-argument constructor: chains to the parent's four-argument
     *  form so defStyleRes reaches TextView (AOSP does the same, e.g. EditText.java:100-101).
     *  Without it an app subclass calling the four-argument super() dies with
     *  NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public TextClock(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    public TextClock(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        createTime(mTimeZone);
        chooseFormat();
        onTimeChanged();
    }

    private void createTime(String timeZone) {
        mTime = timeZone != null
                ? Calendar.getInstance(TimeZone.getTimeZone(timeZone))
                : Calendar.getInstance();
    }

    public CharSequence getFormat12Hour() { return mFormat12; }
    public void setFormat12Hour(CharSequence format) {
        mFormat12 = format; chooseFormat(); onTimeChanged();
    }
    public CharSequence getFormat24Hour() { return mFormat24; }
    public void setFormat24Hour(CharSequence format) {
        mFormat24 = format; chooseFormat(); onTimeChanged();
    }
    public boolean is24HourModeEnabled() { return android.text.format.DateFormat.is24HourFormat(getContext()); }
    public CharSequence getFormat() { return mFormat; }

    public String getTimeZone() { return mTimeZone; }
    public void setTimeZone(String timeZone) {
        mTimeZone = timeZone; createTime(timeZone); onTimeChanged();
    }

    private void chooseFormat() {
        final boolean format24 = is24HourModeEnabled();
        mFormat = format24
                ? (mFormat24 != null ? mFormat24 : DEFAULT_FORMAT_24_HOUR)
                : (mFormat12 != null ? mFormat12 : DEFAULT_FORMAT_12_HOUR);
        mHasSeconds = hasSeconds(mFormat);
    }

    private static boolean hasSeconds(CharSequence inFormat) {
        if (inFormat == null) return false;
        return inFormat.toString().indexOf('s') >= 0;
    }

    @Override public void onAttachedToWindow() {
        try { super.onAttachedToWindow(); } catch (Throwable ignored) {}
        if (!mRegistered) {
            mRegistered = true;
            scheduleTick();
        }
        createTime(mTimeZone);
        onTimeChanged();
    }
    @Override public void onDetachedFromWindow() {
        try { super.onDetachedFromWindow(); } catch (Throwable ignored) {}
        if (mRegistered) { mRegistered = false; cancelTick(); }
    }

    private void onTimeChanged() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        try {
            SimpleDateFormat fmt = new SimpleDateFormat(
                    mFormat.toString(), Locale.getDefault());
            if (mTimeZone != null) fmt.setTimeZone(TimeZone.getTimeZone(mTimeZone));
            setText(fmt.format(mTime.getTime()));
        } catch (Throwable t) {
            setText("");
        }
    }

    private void ensureHandler() {
        if (mHandler == null) mHandler = new Handler(Looper.getMainLooper());
    }
    private void scheduleTick() {
        ensureHandler();
        if (mTick == null) {
            mTick = new Runnable() {
                @Override public void run() {
                    if (mRegistered) {
                        onTimeChanged();
                        mHandler.postDelayed(mTick, mHasSeconds ? 1000 : 60000);
                    }
                }
            };
        }
        mHandler.postDelayed(mTick, mHasSeconds ? 1000 : 60000);
    }
    private void cancelTick() {
        if (mHandler != null && mTick != null) mHandler.removeCallbacks(mTick);
    }

    /** AOSP TextClock.java:466 -- verbatim. Re-reads the clock and repaints;
     *  an app calls it after changing the format or the time zone, or when it
     *  knows the displayed minute is stale. */
    public void refreshTime() {
        onTimeChanged();
        invalidate();
    }
}
