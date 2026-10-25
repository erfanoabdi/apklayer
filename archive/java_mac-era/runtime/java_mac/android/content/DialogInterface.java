package android.content;
public interface DialogInterface {
    /** AOSP core/java/android/content/DialogInterface.java -- the
     *  pre-API-3 aliases, @Deprecated upstream and identical in value to
     *  BUTTON_POSITIVE / BUTTON_NEGATIVE / BUTTON_NEUTRAL. */
    @Deprecated int BUTTON1 = -1;
    @Deprecated int BUTTON2 = -2;
    @Deprecated int BUTTON3 = -3;

    int BUTTON_POSITIVE = -1;
    int BUTTON_NEGATIVE = -2;
    int BUTTON_NEUTRAL  = -3;
    void cancel();
    void dismiss();
    interface OnClickListener   { void onClick(DialogInterface dialog, int which); }
    interface OnCancelListener  { void onCancel(DialogInterface dialog); }
    interface OnDismissListener { void onDismiss(DialogInterface dialog); }
    interface OnKeyListener     { boolean onKey(DialogInterface dialog, int keyCode, android.view.KeyEvent ev); }
    interface OnShowListener    { void onShow(DialogInterface dialog); }
    interface OnMultiChoiceClickListener { void onClick(DialogInterface dialog, int which, boolean isChecked); }
}
