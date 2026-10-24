// android.preference.ListPreference -- a DialogPreference presenting a
// single-choice list, persisting the chosen entryValue as a String.
//
// AOSP reference (fetched 2026-07-29; setValue / getSummary / onGetDefaultValue /
// onSetInitialValue bodies mirrored verbatim):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/ListPreference.java
//   public class ListPreference extends DialogPreference
//   public ListPreference(Context, AttributeSet, int defStyleAttr, int defStyleRes)
//     + the 3 shorter forms
//   set/getEntries, set/getEntryValues, setValue, getValue, setValueIndex,
//   getEntry, findIndexOfValue, getSummary, setSummary,
//   protected void onPrepareDialogBuilder(AlertDialog.Builder)
//   protected void onDialogClosed(boolean)
//   protected Object onGetDefaultValue(TypedArray, int)
//   protected void onSetInitialValue(boolean, Object)
//
// THREE CORRECTIONS to the previous Mac.apk version:
//  - it extended `Preference`, not `DialogPreference`. That made a
//    `(DialogPreference)` cast fail and hid the whole dialog surface; it is also
//    why de.tobiasbielefeld.solitaire's measured reference to
//    `ListPreference.onCreateView(ViewGroup)` had nothing to resolve against.
//  - setValue() did not PERSIST -- the chosen entry was held in a field and lost
//    on the next launch.
//  - setEntries(int) / setEntryValues(int) were empty, so an XML-declared
//    `android:entries="@array/..."` silently produced an empty list.
package android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcelable;
import android.util.AttributeSet;

@SuppressWarnings("deprecation")
public class ListPreference extends DialogPreference {

    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private String mValue;
    private String mSummary;
    private boolean mValueSet;
    private int mClickedDialogEntryIndex = -1;

    public ListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // AOSP reads R.styleable.ListPreference_entries / _entryValues here.
        // Without this a ListPreference inflated from XML has NO entries, so its
        // dialog is empty and getEntry() returns null for a perfectly valid
        // persisted value.
        if (context != null && attrs != null) {
            CharSequence[] entries = PreferenceAttrs.textArray(context, attrs, "entries");
            if (entries != null) setEntries(entries);
            CharSequence[] values = PreferenceAttrs.textArray(context, attrs, "entryValues");
            if (values != null) setEntryValues(values);
        }
    }

    /** AOSP onGetDefaultValue(TypedArray, int) is `a.getString(index)`. */
    @Override
    Object onGetDefaultValue(AttributeSet attrs) {
        return PreferenceAttrs.string(getContext(), attrs, "defaultValue");
    }
    public ListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public ListPreference(Context context, AttributeSet attrs) { this(context, attrs, 0, 0); }
    public ListPreference(Context context) { this(context, null, 0, 0); }

    /** Not AOSP; retained from the previous Mac.apk stub. */
    public ListPreference() { super(); }

    public void setEntries(CharSequence[] entries) { mEntries = entries; }
    public void setEntries(int entriesResId) { setEntries(textArray(entriesResId)); }
    public CharSequence[] getEntries() { return mEntries; }

    public void setEntryValues(CharSequence[] entryValues) { mEntryValues = entryValues; }
    public void setEntryValues(int entryValuesResId) { setEntryValues(textArray(entryValuesResId)); }
    public CharSequence[] getEntryValues() { return mEntryValues; }

    private CharSequence[] textArray(int resId) {
        Context c = getContext();
        if (c == null || resId == 0) return null;
        try {
            android.content.res.Resources r = c.getResources();
            return r == null ? null : r.getTextArray(resId);
        } catch (Throwable t) {
            return null;
        }
    }

    /** AOSP body, verbatim (TextUtils.equals inlined). */
    public void setValue(String value) {
        final boolean changed = !stringEquals(mValue, value);
        if (changed || !mValueSet) {
            mValue = value;
            mValueSet = true;
            persistString(value);
            if (changed) {
                notifyChanged();
            }
        }
    }

    private static boolean stringEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public String getValue() { return mValue; }

    /** AOSP body, verbatim: when a summary was set it is used as a FORMAT string
     *  with the currently-selected entry substituted (the `%s` idiom). */
    @Override
    public CharSequence getSummary() {
        final CharSequence entry = getEntry();
        if (mSummary == null) {
            return super.getSummary();
        } else {
            return String.format(mSummary, entry == null ? "" : entry);
        }
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(summary);
        if (summary == null && mSummary != null) {
            mSummary = null;
        } else if (summary != null && !summary.equals(mSummary)) {
            mSummary = summary.toString();
        }
    }

    public void setValueIndex(int index) {
        if (mEntryValues != null && index >= 0 && index < mEntryValues.length) {
            setValue(mEntryValues[index].toString());
        }
    }

    public CharSequence getEntry() {
        int index = findIndexOfValue(mValue);
        return index >= 0 && mEntries != null && index < mEntries.length ? mEntries[index] : null;
    }

    public int findIndexOfValue(String value) {
        if (value != null && mEntryValues != null) {
            for (int i = mEntryValues.length - 1; i >= 0; i--) {
                if (mEntryValues[i] != null && value.contentEquals(mEntryValues[i])) return i;
            }
        }
        return -1;
    }

    /** AOSP puts the entries into the builder as a single-choice list and
     *  suppresses the positive button (a row tap closes the dialog). Mirrored
     *  through Mac.apk's AlertDialog.Builder, which has the same methods. */
    @Override
    protected void onPrepareDialogBuilder(android.app.AlertDialog.Builder builder) {
        if (mEntries == null || mEntryValues == null) {
            throw new IllegalStateException(
                "ListPreference requires an entries array and an entryValues array.");
        }
        mClickedDialogEntryIndex = findIndexOfValue(mValue);
        builder.setSingleChoiceItems(mEntries, mClickedDialogEntryIndex,
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        mClickedDialogEntryIndex = which;
                        // AOSP: clicking a row acts as the POSITIVE result and
                        // dismisses, so there is no OK button to press.
                        ListPreference.this.onClick(dialog,
                                android.content.DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();
                    }
                });
        builder.setPositiveButton(null, null);
    }

    /** AOSP body: on a positive close, commit the clicked entry if the change
     *  listener accepts it. */
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult && mClickedDialogEntryIndex >= 0 && mEntryValues != null
                && mClickedDialogEntryIndex < mEntryValues.length) {
            String value = mEntryValues[mClickedDialogEntryIndex].toString();
            if (callChangeListener(value)) setValue(value);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a == null ? null : a.getString(index);
    }

    /** AOSP body, verbatim except for the null-tolerant cast: Mac.apk's
     *  XML may declare no android:defaultValue at all, so
     *  defaultValue can be a non-String here where AOSP guarantees a String. */
    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(mValue)
                : (defaultValue == null ? null : String.valueOf(defaultValue)));
    }

    @Override
    protected Parcelable onSaveInstanceState() { return super.onSaveInstanceState(); }

    @Override
    protected void onRestoreInstanceState(Parcelable state) { super.onRestoreInstanceState(state); }
}
