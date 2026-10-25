// android.widget.Chronometer -- a TextView that shows elapsed time as a
// running MM:SS (or H:MM:SS) counter from a base time. Was Absent. start()
// ticks every second via the main Handler, updating the text; the
// OnChronometerTickListener fires each tick. AOSP:
//   frameworks/base/core/java/android/widget/Chronometer.java
//   public class Chronometer extends TextView
package android.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Formatter;
import java.util.Locale;

public class Chronometer extends TextView {
    public interface OnChronometerTickListener {
        void onChronometerTick(Chronometer chronometer);
    }

    private long mBase;
    private long mNow;
    private boolean mVisible;
    private boolean mStarted;
    private boolean mRunning;
    private boolean mLogged;
    private String mFormat;
    private Formatter mFormatter;
    private Locale mFormatterLocale;
    private Object[] mFormatterArgs = new Object[1];
    private StringBuilder mFormatBuilder;
    private OnChronometerTickListener mOnChronometerTickListener;
    private StringBuilder mRecycle = new StringBuilder(8);
    private boolean mCountDown;

    private Handler mHandler;
    private Runnable mTick;

    private static final int TICK_WHAT = 2;

    public Chronometer(Context context) { this(context, null, 0); }
    public Chronometer(Context context, android.util.AttributeSet attrs) {
        this(context, attrs, 0);
    }
    /** AOSP API 21 four-argument constructor: chains to the parent's four-argument
     *  form so defStyleRes reaches TextView (AOSP does the same, e.g. EditText.java:100-101).
     *  Without it an app subclass calling the four-argument super() dies with
     *  NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public Chronometer(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    public Chronometer(Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mBase = SystemClock.elapsedRealtime();
        updateText(mBase);
    }

    public void setBase(long base) {
        mBase = base;
        dispatchChronometerTick();
        updateText(SystemClock.elapsedRealtime());
    }
    public long getBase() { return mBase; }

    public void setFormat(String format) { mFormat = format; }
    public String getFormat() { return mFormat; }

    public void setOnChronometerTickListener(OnChronometerTickListener listener) {
        mOnChronometerTickListener = listener;
    }
    public OnChronometerTickListener getOnChronometerTickListener() {
        return mOnChronometerTickListener;
    }

    public void setCountDown(boolean countDown) {
        mCountDown = countDown;
        updateText(SystemClock.elapsedRealtime());
    }
    public boolean isCountDown() { return mCountDown; }
    public boolean isTheFinalCountDown() { return false; }

    public void start() {
        mStarted = true;
        updateRunning();
    }
    public void stop() {
        mStarted = false;
        updateRunning();
    }
    public void setStarted(boolean started) {
        mStarted = started;
        updateRunning();
    }

    @Override public void onAttachedToWindow() {
        try { super.onAttachedToWindow(); } catch (Throwable ignored) {}
        mVisible = true;
        updateRunning();
    }
    @Override public void onDetachedFromWindow() {
        try { super.onDetachedFromWindow(); } catch (Throwable ignored) {}
        mVisible = false;
        updateRunning();
    }

    private synchronized void updateText(long now) {
        mNow = now;
        long seconds = mCountDown ? mBase - now : now - mBase;
        seconds /= 1000;
        boolean negative = false;
        if (seconds < 0) { seconds = -seconds; negative = true; }

        String text = formatElapsedTime(mRecycle, seconds);
        if (negative) text = "-" + text;

        if (mFormat != null) {
            Locale loc = Locale.getDefault();
            if (mFormatter == null || !loc.equals(mFormatterLocale)) {
                mFormatterLocale = loc;
                mFormatBuilder = new StringBuilder();
                mFormatter = new Formatter(mFormatBuilder, loc);
            }
            mFormatBuilder.setLength(0);
            mFormatterArgs[0] = text;
            try {
                mFormatter.format(mFormat, mFormatterArgs);
                text = mFormatBuilder.toString();
            } catch (Throwable ignored) {}
        }
        setText(text);
    }

    private static String formatElapsedTime(StringBuilder recycle, long elapsedSeconds) {
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;
        if (recycle != null) recycle.setLength(0);
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d",
                    hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void updateRunning() {
        boolean running = mVisible && mStarted;
        if (running != mRunning) {
            if (running) {
                updateText(SystemClock.elapsedRealtime());
                dispatchChronometerTick();
                scheduleTick();
            } else {
                cancelTick();
            }
            mRunning = running;
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
                    if (mRunning) {
                        updateText(SystemClock.elapsedRealtime());
                        dispatchChronometerTick();
                        mHandler.postDelayed(mTick, 1000);
                    }
                }
            };
        }
        mHandler.postDelayed(mTick, 1000);
    }

    private void cancelTick() {
        if (mHandler != null && mTick != null) mHandler.removeCallbacks(mTick);
    }

    void dispatchChronometerTick() {
        if (mOnChronometerTickListener != null) {
            mOnChronometerTickListener.onChronometerTick(this);
        }
    }
}
