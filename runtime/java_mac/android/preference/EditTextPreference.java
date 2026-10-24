// android.preference.EditTextPreference -- a DialogPreference hosting a single
// EditText, persisting its contents as a String.
//
// Added with the rest of the DialogPreference family: DialogPreference is the
// abstract base and ListPreference / EditTextPreference are AOSP's two concrete
// subclasses, so shipping one without the other leaves a
// NoClassDefFoundError for the commonest legacy `<EditTextPreference>` tag.
//
// AOSP reference:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/EditTextPreference.java
//   public class EditTextPreference extends DialogPreference
//   public EditTextPreference(Context, AttributeSet, int defStyleAttr, int defStyleRes)
//     + the 3 shorter forms
//   public void setText(String text)      public String getText()
//   protected void onBindDialogView(View view)
//   protected void onAddEditTextToDialogView(View dialogView, EditText editText)
//   protected void onDialogClosed(boolean positiveResult)
//   protected boolean shouldDisableDependents()
//   public EditText getEditText()
//   protected Parcelable onSaveInstanceState() / onRestoreInstanceState(Parcelable)
//   protected Object onGetDefaultValue(TypedArray, int)
//   protected void onSetInitialValue(boolean, Object)
package android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

@SuppressWarnings("deprecation")
public class EditTextPreference extends DialogPreference {

    /** AOSP holds a real EditText instance for the lifetime of the Preference,
     *  created in the constructor, so getEditText() is valid before the dialog
     *  is ever shown. Mirrored. */
    private EditText mEditText;
    private String mText;
    private boolean mTextSet;

    /** AOSP onGetDefaultValue(TypedArray, int): `a.getString(index)`. */
    @Override
    Object onGetDefaultValue(AttributeSet attrs) {
        return PreferenceAttrs.string(getContext(), attrs, "defaultValue");
    }

    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (context != null) {
            try { mEditText = new EditText(context, attrs); } catch (Throwable t) { mEditText = null; }
        }
    }
    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public EditTextPreference(Context context, AttributeSet attrs) { this(context, attrs, 0, 0); }
    public EditTextPreference(Context context) { this(context, null, 0, 0); }
    public EditTextPreference() { super(); }

    /** AOSP body: persist, then notify only when the value actually changed. */
    public void setText(String text) {
        final boolean changed = mText == null ? text != null : !mText.equals(text);
        if (changed || !mTextSet) {
            mText = text;
            mTextSet = true;
            persistString(text);
            if (changed) notifyDependencyChange(shouldDisableDependents());
        }
    }

    public String getText() { return mText; }

    public EditText getEditText() { return mEditText; }

    /** AOSP: put the persisted text into the EditText, then re-parent it into the
     *  dialog view. */
    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        EditText editText = mEditText;
        if (editText == null) return;
        editText.setText(getText());
        ViewGroup oldParent = null;
        try {
            View p = (View) editText.getParent();
            if (p instanceof ViewGroup) oldParent = (ViewGroup) p;
        } catch (Throwable ignored) {}
        if (oldParent != view && oldParent != null) oldParent.removeView(editText);
        if (oldParent != view) onAddEditTextToDialogView(view, editText);
    }

    /** AOSP hook: adds the EditText to the dialog's `edittext_container` when
     *  present, else to the root. That container id is not in
     *  core/res/res/values/public-final.xml, so there is no frozen number to
     *  mirror -- the root is used, which is what AOSP falls back to. */
    protected void onAddEditTextToDialogView(View dialogView, EditText editText) {
        if (dialogView instanceof ViewGroup && editText != null) {
            try { ((ViewGroup) dialogView).addView(editText); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (!positiveResult || mEditText == null) return;
        String value = mEditText.getText() == null ? null : mEditText.getText().toString();
        if (callChangeListener(value)) setText(value);
    }

    /** AOSP: an EditTextPreference disables its dependents when EMPTY. */
    @Override
    public boolean shouldDisableDependents() {
        return (mText == null || mText.length() == 0) || super.shouldDisableDependents();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a == null ? null : a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setText(restoreValue ? getPersistedString(mText)
                : (defaultValue == null ? null : String.valueOf(defaultValue)));
    }

    @Override
    protected Parcelable onSaveInstanceState() { return super.onSaveInstanceState(); }

    @Override
    protected void onRestoreInstanceState(Parcelable state) { super.onRestoreInstanceState(state); }
}
