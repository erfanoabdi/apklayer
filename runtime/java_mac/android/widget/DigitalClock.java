// android.widget.DigitalClock -- deprecated (use TextClock) TextView showing
// the current time, ticking each second. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/DigitalClock.java
//   @Deprecated public class DigitalClock extends TextView
package android.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

@Deprecated
public class DigitalClock extends TextView {
    private Calendar mCalendar;
    private static final String M12 = "h:mm:ss aa";
    private static final String M24 = "k:mm:ss";
    private String mFormat = M12;

    private Handler mHandler;
    private Runnable mTick;
    private boolean mTickerStopped = true;

    public DigitalClock(Context context) { super(context); init(); }
    public DigitalClock(Context context, android.util.AttributeSet attrs) {
        super(context, attrs); init();
    }

    private void init() {
        mCalendar = Calendar.getInstance();
        setFormat();
    }

    private void setFormat() {
        mFormat = android.text.format.DateFormat.is24HourFormat(getContext())
                ? M24 : M12;
    }

    @Override public void onAttachedToWindow() {
        try { super.onAttachedToWindow(); } catch (Throwable ignored) {}
        mTickerStopped = false;
        ensureHandler();
        if (mTick == null) {
            mTick = new Runnable() {
                @Override public void run() {
                    if (mTickerStopped) return;
                    mCalendar.setTimeInMillis(System.currentTimeMillis());
                    try {
                        SimpleDateFormat fmt = new SimpleDateFormat(
                                mFormat, Locale.getDefault());
                        setText(fmt.format(mCalendar.getTime()));
                    } catch (Throwable ignored) {}
                    invalidate();
                    mHandler.postDelayed(mTick, 1000);
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

    private void ensureHandler() {
        if (mHandler == null) mHandler = new Handler(Looper.getMainLooper());
    }
}
