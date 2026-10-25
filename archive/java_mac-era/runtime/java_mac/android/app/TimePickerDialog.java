package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.TimePicker;

// AOSP fidelity: the deprecation usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("deprecation")
/**
 * A dialog hosting a TimePicker with a set-time callback.
 * Source: frameworks/base/core/java/android/app/TimePickerDialog.java
 */
public class TimePickerDialog extends AlertDialog
        implements DialogInterface.OnClickListener, TimePicker.OnTimeChangedListener {

    public interface OnTimeSetListener {
        void onTimeSet(TimePicker view, int hourOfDay, int minute);
    }

    private final TimePicker mTimePicker;
    private final OnTimeSetListener mListener;

    public TimePickerDialog(Context context, OnTimeSetListener listener,
            int hourOfDay, int minute, boolean is24HourView) {
        this(context, 0, listener, hourOfDay, minute, is24HourView);
    }
    public TimePickerDialog(Context context, int themeResId, OnTimeSetListener listener,
            int hourOfDay, int minute, boolean is24HourView) {
        // android14 TimePickerDialog.java:111-120:
        //     super(context, resolveDialogTheme(context, themeResId));
        //     final Context themeContext = getContext();
        // (the picker is inflated in themeContext). We called super(context) and
        // built the picker on the caller's Context. MACAPK_DIALOG_THEME_WRAPPER=0
        // restores that exactly (AlertDialog(context, 0) keeps the Context).
        super(context, MACAPK_DIALOG_THEME_WRAPPER ? resolveDialogTheme(context, themeResId) : 0);
        mListener = listener;
        mTimePicker = new TimePicker(MACAPK_DIALOG_THEME_WRAPPER ? getContext() : context);
        try {
            mTimePicker.setIs24HourView(is24HourView);
            mTimePicker.setCurrentHour(hourOfDay);
            mTimePicker.setCurrentMinute(minute);
        } catch (Throwable ignored) {}
        try { setView(mTimePicker); } catch (Throwable ignored) {}
        setButton(BUTTON_POSITIVE, "OK", this);
        setButton(BUTTON_NEGATIVE, "Cancel", (DialogInterface.OnClickListener) null);
    }

    /** android14 TimePickerDialog.resolveDialogTheme (:85-93): 0 resolves
     *  ?android:timePickerDialogTheme, anything else is used as given. */
    static int resolveDialogTheme(Context context, int resId) {
        if (resId == 0) {
            return context == null ? 0 : macapkResolveThemeAttr(context, ATTR_TIME_PICKER_DIALOG_THEME);
        }
        return resId;
    }

    public TimePicker getTimePicker() { return mTimePicker; }

    public void updateTime(int hourOfDay, int minuteOfHour) {
        try {
            mTimePicker.setCurrentHour(hourOfDay);
            mTimePicker.setCurrentMinute(minuteOfHour);
        } catch (Throwable ignored) {}
    }

    @Override public void onClick(DialogInterface dialog, int which) {
        if (which == BUTTON_POSITIVE && mListener != null && mTimePicker != null) {
            try {
                mListener.onTimeSet(mTimePicker,
                        mTimePicker.getCurrentHour(), mTimePicker.getCurrentMinute());
            } catch (Throwable ignored) {}
        }
    }

    /** AOSP onTimeChanged(TimePicker, int, int): the body is literally
     *  {@code /* do nothing *}{@code /} -- the dialog registers itself as its own
     *  picker's change listener (AOSP declares TimePickerDialog implements
     *  TimePicker.OnTimeChangedListener) but acts only on the positive button.
     *  The type identity is the point. */
    @Override public void onTimeChanged(TimePicker view, int hourOfDay, int minute) { }
}
