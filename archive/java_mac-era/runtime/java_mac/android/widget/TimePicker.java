// android.widget.TimePicker -- a widget to pick a time of day. Was Absent.
// The hour/minute model, 24-hour mode, and OnTimeChangedListener are
// faithful; the spinner/clock chrome is not painted. AOSP:
//   frameworks/base/core/java/android/widget/TimePicker.java
//   public class TimePicker extends FrameLayout
package android.widget;

import android.content.Context;

public class TimePicker extends FrameLayout {
    public interface OnTimeChangedListener {
        void onTimeChanged(TimePicker view, int hourOfDay, int minute);
    }

    private int mHour;
    private int mMinute;
    private boolean mIs24HourView;
    private OnTimeChangedListener mListener;
    private boolean mEnabled = true;

    public TimePicker(Context context) { super(context); }
    public TimePicker(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public TimePicker(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public TimePicker(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Deprecated public void setCurrentHour(Integer currentHour) { setHour(currentHour); }
    @Deprecated public Integer getCurrentHour() { return getHour(); }
    @Deprecated public void setCurrentMinute(Integer currentMinute) { setMinute(currentMinute); }
    @Deprecated public Integer getCurrentMinute() { return getMinute(); }

    public void setHour(int hour) {
        mHour = Math.max(0, Math.min(23, hour));
        notifyChange();
    }
    public int getHour() { return mHour; }

    public void setMinute(int minute) {
        mMinute = Math.max(0, Math.min(59, minute));
        notifyChange();
    }
    public int getMinute() { return mMinute; }

    public void setIs24HourView(Boolean is24HourView) {
        mIs24HourView = is24HourView != null && is24HourView;
    }
    public boolean is24HourView() { return mIs24HourView; }

    public void setOnTimeChangedListener(OnTimeChangedListener onTimeChangedListener) {
        mListener = onTimeChangedListener;
    }

    private void notifyChange() {
        if (mListener != null) mListener.onTimeChanged(this, mHour, mMinute);
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mEnabled = enabled;
    }
    @Override public boolean isEnabled() { return mEnabled; }

    /** AOSP TimePicker.java:320 -- delegates to the mode delegate's
     *  validateInput(), which reports whether the hour/minute text fields hold
     *  a parseable time; the spinner delegate keeps a flag that is true unless
     *  the user typed something unparseable. This port has no editable text
     *  fields at all (hour/minute are held as ints and can only be set
     *  programmatically), so there is no state in which the input is invalid. */
    public boolean validateInput() { return true; }
}
