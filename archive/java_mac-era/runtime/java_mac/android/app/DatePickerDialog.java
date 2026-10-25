package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.DatePicker;

/**
 * A dialog hosting a DatePicker with a set-date callback. Now that the
 * WindowManager overlay stack (v1.0.691) composites dialogs, this actually
 * shows. Extends AlertDialog so show()/dismiss() go through the overlay path.
 *
 * Source: frameworks/base/core/java/android/app/DatePickerDialog.java
 */
public class DatePickerDialog extends AlertDialog
        implements DialogInterface.OnClickListener, DatePicker.OnDateChangedListener {

    public interface OnDateSetListener {
        void onDateSet(DatePicker view, int year, int month, int dayOfMonth);
    }

    private final DatePicker mDatePicker;
    // AOSP's field is mDateSetListener and is REASSIGNABLE via
    // setOnDateSetListener; ours was final, so that setter could not exist.
    private OnDateSetListener mListener;

    public DatePickerDialog(Context context) {
        this(context, 0, null, 2026, 0, 1);
    }
    public DatePickerDialog(Context context, OnDateSetListener listener,
            int year, int month, int dayOfMonth) {
        this(context, 0, listener, year, month, dayOfMonth);
    }
    public DatePickerDialog(Context context, int themeResId, OnDateSetListener listener,
            int year, int month, int dayOfMonth) {
        // android14 DatePickerDialog.java:114-121:
        //     super(context, resolveDialogTheme(context, themeResId));
        //     final Context themeContext = getContext();
        //     ... inflater.inflate(R.layout.date_picker_dialog, null) in themeContext
        // We called super(context) and built the picker on the caller's Context,
        // so the dialog and its picker ran in the activity theme.
        // MACAPK_DIALOG_THEME_WRAPPER=0: AlertDialog(context, 0) then stores the
        // caller's Context unchanged and the picker is built on it, as before.
        super(context, MACAPK_DIALOG_THEME_WRAPPER ? resolveDialogTheme(context, themeResId) : 0);
        mListener = listener;
        mDatePicker = new DatePicker(MACAPK_DIALOG_THEME_WRAPPER ? getContext() : context);
        // AOSP passes `this` (the dialog implements OnDateChangedListener).
        try { mDatePicker.init(year, month, dayOfMonth, this); } catch (Throwable ignored) {}
        try { setView(mDatePicker); } catch (Throwable ignored) {}
        setButton(BUTTON_POSITIVE, "OK", this);
        setButton(BUTTON_NEGATIVE, "Cancel", (DialogInterface.OnClickListener) null);
    }

    /** android14 DatePickerDialog.resolveDialogTheme (:141-149): 0 resolves
     *  ?android:datePickerDialogTheme, anything else is used as given. The
     *  result then goes through AlertDialog(Context, int), whose own
     *  resolveDialogTheme falls back to ?android:alertDialogTheme if this
     *  answered 0. */
    static int resolveDialogTheme(Context context, int themeResId) {
        if (themeResId == 0) {
            return context == null ? 0 : macapkResolveThemeAttr(context, ATTR_DATE_PICKER_DIALOG_THEME);
        }
        return themeResId;
    }

    public DatePicker getDatePicker() { return mDatePicker; }

    public void updateDate(int year, int month, int dayOfMonth) {
        try { mDatePicker.updateDate(year, month, dayOfMonth); } catch (Throwable ignored) {}
    }

    /** AOSP setOnDateSetListener(OnDateSetListener): "Sets the listener to call
     *  when the user sets the date." YouTube calls this. */
    public void setOnDateSetListener(OnDateSetListener listener) { mListener = listener; }

    /** AOSP onDateChanged(DatePicker, int, int, int): {@code mDatePicker.init(year,
     *  month, dayOfMonth, this);} verbatim -- the dialog is its OWN picker's
     *  change listener (AOSP declares DatePickerDialog implements
     *  DatePicker.OnDateChangedListener), and re-init'ing keeps the picker's
     *  internal spinner state and the dialog in agreement. */
    @Override public void onDateChanged(DatePicker view, int year, int month, int dayOfMonth) {
        try { mDatePicker.init(year, month, dayOfMonth, this); } catch (Throwable ignored) {}
    }

    @Override public void onClick(DialogInterface dialog, int which) {
        if (which == BUTTON_POSITIVE && mListener != null && mDatePicker != null) {
            try {
                mListener.onDateSet(mDatePicker, mDatePicker.getYear(),
                        mDatePicker.getMonth(), mDatePicker.getDayOfMonth());
            } catch (Throwable ignored) {}
        }
    }
}
