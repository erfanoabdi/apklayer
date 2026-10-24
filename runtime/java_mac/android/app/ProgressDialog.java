// android.app.ProgressDialog -- a Dialog showing a spinner or a
// horizontal progress bar. Deprecated in API-26 but still used by older
// apps' loading screens. AOSP API-1+.
//
// AOSP ref: frameworks/base/core/java/android/app/ProgressDialog.java
// (extends AlertDialog). Mac.apk doesn't render dialog chrome, so the
// progress state is recorded but not displayed; the static show()
// factory + setters resolve so a "loading..." dialog never throws.
package android.app;

import android.content.Context;
import android.content.DialogInterface;

public class ProgressDialog extends AlertDialog {
    public static final int STYLE_SPINNER    = 0;
    public static final int STYLE_HORIZONTAL = 1;

    private int mProgressStyle = STYLE_SPINNER;
    private int mMax = 100;
    private int mProgress;
    private int mSecondaryProgress;
    private boolean mIndeterminate;
    private CharSequence mMessage;

    public ProgressDialog(Context context) { super(context); }
    public ProgressDialog(Context context, int theme) { super(context, theme); }

    /** AOSP static convenience factories. They construct, configure, and
     *  show() a ProgressDialog in one call. */
    public static ProgressDialog show(Context context, CharSequence title, CharSequence message) {
        return show(context, title, message, false);
    }
    public static ProgressDialog show(Context context, CharSequence title, CharSequence message, boolean indeterminate) {
        return show(context, title, message, indeterminate, false, null);
    }
    public static ProgressDialog show(Context context, CharSequence title, CharSequence message, boolean indeterminate, boolean cancelable) {
        return show(context, title, message, indeterminate, cancelable, null);
    }
    public static ProgressDialog show(Context context, CharSequence title, CharSequence message,
                                      boolean indeterminate, boolean cancelable,
                                      DialogInterface.OnCancelListener cancelListener) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setIndeterminate(indeterminate);
        dialog.setCancelable(cancelable);
        dialog.setOnCancelListener(cancelListener);
        try { dialog.show(); } catch (Throwable ignored) {}
        return dialog;
    }

    public void setProgressStyle(int style) { mProgressStyle = style; }
    @Override public void setMessage(CharSequence message) { mMessage = message; super.setMessage(message); }
    public void setMax(int max) { mMax = max; }
    public int getMax() { return mMax; }
    public void setProgress(int value) { mProgress = value; }
    public int getProgress() { return mProgress; }
    public void setSecondaryProgress(int secondaryProgress) { mSecondaryProgress = secondaryProgress; }
    public int getSecondaryProgress() { return mSecondaryProgress; }
    public void incrementProgressBy(int diff) { mProgress += diff; }
    public void incrementSecondaryProgressBy(int diff) { mSecondaryProgress += diff; }
    public void setIndeterminate(boolean indeterminate) { mIndeterminate = indeterminate; }
    public boolean isIndeterminate() { return mIndeterminate; }
    public void setIndeterminateDrawable(android.graphics.drawable.Drawable d) {}
    public void setProgressDrawable(android.graphics.drawable.Drawable d) {}
    public void setProgressNumberFormat(String format) {}
    public void setProgressPercentFormat(java.text.NumberFormat format) {}
}
