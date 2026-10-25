// android.preference.DialogPreference -- the base class for any Preference that
// opens a dialog when clicked (ListPreference, EditTextPreference, and app
// subclasses).
//
// Measured gap: referenced by de.tobiasbielefeld.solitaire (Simple Solitaire
// Collection) from the 2026-07-29 F-Droid batch; also the single largest reason
// ch.blinkenlights.android.vanilla was dropped from that batch.
//
// AOSP reference (fetched 2026-07-29):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/DialogPreference.java
//   public abstract class DialogPreference extends Preference implements
//           DialogInterface.OnClickListener, DialogInterface.OnDismissListener,
//           PreferenceManager.OnActivityDestroyListener
//   public DialogPreference(Context, AttributeSet, int, int) + the 3 shorter forms
//   set/getDialogTitle, set/getDialogMessage, set/getDialogIcon,
//   set/getPositiveButtonText, set/getNegativeButtonText,
//   set/getDialogLayoutResource
//   public void onClick(DialogInterface dialog, int which)
//   public Dialog getDialog()
//   public void onActivityDestroy()
//   protected void onPrepareDialogBuilder(AlertDialog.Builder builder)
//   protected void showDialog(Bundle state)
//   protected View onCreateDialogView()
//   protected void onBindDialogView(View view)
//   protected void onDialogClosed(boolean positiveResult)
//   protected Parcelable onSaveInstanceState()
//   protected void onRestoreInstanceState(Parcelable state)
//
// NOT abstract here. AOSP can be abstract because its own PreferenceInflater is
// the only code path that instantiates the concrete subclasses; Mac.apk has no
// inflater, and a `<DialogPreference>`-shaped tag reaching an app's own
// Class.forName().newInstance() must not die on InstantiationException.
//
// showDialog() really does build and show an AlertDialog, because Mac.apk has a
// working android.app.AlertDialog.Builder -- so an app that overrides
// onCreateDialogView()/onBindDialogView()/onDialogClosed() (the whole point of
// subclassing this) gets its callbacks in the AOSP order.
package android.preference;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

@SuppressWarnings("deprecation")
public class DialogPreference extends Preference
        implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener,
                   PreferenceManager.OnActivityDestroyListener {

    private CharSequence mDialogTitle;
    private CharSequence mDialogMessage;
    private Drawable mDialogIcon;
    private CharSequence mPositiveButtonText;
    private CharSequence mNegativeButtonText;
    private int mDialogLayoutResId;

    private Dialog mDialog;
    private int mWhichButtonClicked;

    public DialogPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // AOSP reads the DialogPreference styleable here. Same six attributes,
        // read by name -- see PreferenceAttrs for why the id-keyed
        // obtainStyledAttributes form cannot be used. An absent dialogTitle
        // still falls through to getTitle() in getDialogTitle(), as on AOSP.
        if (context != null && attrs != null) {
            mDialogTitle = PreferenceAttrs.text(context, attrs, "dialogTitle");
            mDialogMessage = PreferenceAttrs.text(context, attrs, "dialogMessage");
            int dialogIconRes = PreferenceAttrs.resId(context, attrs, "dialogIcon", 0);
            if (dialogIconRes != 0) setDialogIcon(dialogIconRes);
            CharSequence pos = PreferenceAttrs.text(context, attrs, "positiveButtonText");
            if (pos != null) mPositiveButtonText = pos;
            CharSequence neg = PreferenceAttrs.text(context, attrs, "negativeButtonText");
            if (neg != null) mNegativeButtonText = neg;
            mDialogLayoutResId =
                    PreferenceAttrs.resId(context, attrs, "dialogLayout", mDialogLayoutResId);
        }
        mPositiveButtonText = "OK";
        mNegativeButtonText = "Cancel";
    }
    public DialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public DialogPreference(Context context, AttributeSet attrs) { this(context, attrs, 0, 0); }
    public DialogPreference(Context context) { this(context, null, 0, 0); }

    /** Not AOSP. Needed because the pre-existing Mac.apk ListPreference /
     *  MultiSelectListPreference stubs each had a no-arg constructor and now
     *  chain through this class; removing theirs would be a gratuitous
     *  source-compat regression for app code that reflectively instantiates. */
    public DialogPreference() { this(null, null, 0, 0); }

    public void setDialogTitle(CharSequence dialogTitle) { mDialogTitle = dialogTitle; }
    public void setDialogTitle(int dialogTitleResId) { setDialogTitle(resString(dialogTitleResId)); }
    /** AOSP: falls back to the Preference title when no dialogTitle was set. */
    public CharSequence getDialogTitle() { return mDialogTitle != null ? mDialogTitle : getTitle(); }

    public void setDialogMessage(CharSequence dialogMessage) { mDialogMessage = dialogMessage; }
    public void setDialogMessage(int dialogMessageResId) { setDialogMessage(resString(dialogMessageResId)); }
    public CharSequence getDialogMessage() { return mDialogMessage; }

    public void setDialogIcon(Drawable dialogIcon) { mDialogIcon = dialogIcon; }
    public void setDialogIcon(int dialogIconRes) {
        Context c = getContext();
        if (c == null || dialogIconRes == 0) return;
        try {
            android.content.res.Resources r = c.getResources();
            if (r != null) mDialogIcon = r.getDrawable(dialogIconRes);
        } catch (Throwable ignored) {}
    }
    public Drawable getDialogIcon() { return mDialogIcon; }

    public void setPositiveButtonText(CharSequence positiveButtonText) {
        mPositiveButtonText = positiveButtonText;
    }
    public void setPositiveButtonText(int positiveButtonTextResId) {
        setPositiveButtonText(resString(positiveButtonTextResId));
    }
    public CharSequence getPositiveButtonText() { return mPositiveButtonText; }

    public void setNegativeButtonText(CharSequence negativeButtonText) {
        mNegativeButtonText = negativeButtonText;
    }
    public void setNegativeButtonText(int negativeButtonTextResId) {
        setNegativeButtonText(resString(negativeButtonTextResId));
    }
    public CharSequence getNegativeButtonText() { return mNegativeButtonText; }

    public void setDialogLayoutResource(int dialogLayoutResId) { mDialogLayoutResId = dialogLayoutResId; }
    public int getDialogLayoutResource() { return mDialogLayoutResId; }

    private CharSequence resString(int resId) {
        Context c = getContext();
        if (c == null || resId == 0) return null;
        try { return c.getString(resId); } catch (Throwable t) { return null; }
    }

    // ---- dialog lifecycle (AOSP order) -----------------------------------

    @Override
    protected void onClick() { showDialog(null); }

    /** AOSP: builds an AlertDialog, calls onCreateDialogView() and, when that
     *  returns a view, setView() on the builder; otherwise setMessage(). Then
     *  onPrepareDialogBuilder(), then shows. */
    protected void showDialog(Bundle state) {
        Context context = getContext();
        if (context == null) return;
        mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setTitle(getDialogTitle())
                    .setIcon(mDialogIcon)
                    .setPositiveButton(mPositiveButtonText, this)
                    .setNegativeButton(mNegativeButtonText, this);

            View contentView = onCreateDialogView();
            if (contentView != null) {
                onBindDialogView(contentView);
                builder.setView(contentView);
            } else {
                builder.setMessage(mDialogMessage);
            }

            onPrepareDialogBuilder(builder);

            Dialog dialog = mDialog = builder.create();
            if (state != null) {
                try { dialog.onRestoreInstanceState(state); } catch (Throwable ignored) {}
            }
            dialog.setOnDismissListener(this);
            dialog.show();
        } catch (Throwable t) {
            // A dialog we could not construct must not take the app down: on
            // AOSP the click would simply have opened a settings dialog.
            mDialog = null;
        }
    }

    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {}

    /** AOSP inflates mDialogLayoutResId, or returns null when unset (which makes
     *  showDialog use setMessage instead). */
    protected View onCreateDialogView() {
        if (mDialogLayoutResId == 0) return null;
        Context context = getContext();
        if (context == null) return null;
        try {
            android.view.LayoutInflater li = android.view.LayoutInflater.from(context);
            return li == null ? null : li.inflate(mDialogLayoutResId, null);
        } catch (Throwable t) {
            return null;
        }
    }

    protected void onBindDialogView(View view) {}

    @Override
    public void onClick(DialogInterface dialog, int which) { mWhichButtonClicked = which; }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mDialog = null;
        onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE);
    }

    protected void onDialogClosed(boolean positiveResult) {}

    public Dialog getDialog() { return mDialog; }

    @Override
    public void onActivityDestroy() {
        if (mDialog == null) return;
        try { if (mDialog.isShowing()) mDialog.dismiss(); } catch (Throwable ignored) {}
        mDialog = null;
    }

    // ---- instance state --------------------------------------------------
    // AOSP saves the dialog's own bundle inside a SavedState. Mac.apk keeps the
    // superclass behavior when no dialog is showing (the overwhelmingly common
    // case) and otherwise falls back to it too, because there is no
    // Parcel-backed Bundle round-trip in this framework to restore it from.

    @Override
    protected Parcelable onSaveInstanceState() { return super.onSaveInstanceState(); }

    @Override
    protected void onRestoreInstanceState(Parcelable state) { super.onRestoreInstanceState(state); }
}
