// android.preference.MultiSelectListPreference -- a DialogPreference letting the
// user pick zero or more entries from a fixed list, persisted as a Set<String>.
// AOSP API-11+. F-Droid and many other apps reference it in their preferences XML
// for "filter by repository" / "language selection" style choices.
//
// AOSP reference:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/MultiSelectListPreference.java
//   public class MultiSelectListPreference extends DialogPreference
//   set/getEntries, set/getEntryValues, setValues, getValues, findIndexOfValue,
//   protected void onPrepareDialogBuilder(AlertDialog.Builder)
//   protected void onDialogClosed(boolean)
//   protected Object onGetDefaultValue(TypedArray, int)
//   protected void onSetInitialValue(boolean, Object)
//
// THREE CORRECTIONS to the previous Mac.apk version:
//  - it extended `Preference`, not `DialogPreference` (AOSP's actual base).
//  - setValues() did not persist -- the selection was lost on the next launch.
//    persistStringSet is the load-bearing call here: the F-Droid regression noted
//    in com.macapk.loader.PreferenceDefaultsScanner.isWrongType() is precisely
//    about this key holding the wrong RUNTIME TYPE, so the store must see a real
//    Set<String>.
//  - setEntries(int) / setEntryValues(int) were empty, so an XML-declared
//    `android:entries="@array/..."` silently produced an empty list.
package android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("deprecation")
public class MultiSelectListPreference extends DialogPreference {

    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private Set<String> mValues = new HashSet<String>();
    private Set<String> mNewValues = new HashSet<String>();
    private boolean mPreferenceChanged;

    public MultiSelectListPreference(Context c, AttributeSet a, int defStyleAttr, int defStyleRes) {
        super(c, a, defStyleAttr, defStyleRes);
        // AOSP reads R.styleable.MultiSelectListPreference_entries / _entryValues.
        if (c != null && a != null) {
            CharSequence[] entries = PreferenceAttrs.textArray(c, a, "entries");
            if (entries != null) setEntries(entries);
            CharSequence[] values = PreferenceAttrs.textArray(c, a, "entryValues");
            if (values != null) setEntryValues(values);
        }
    }

    /** AOSP onGetDefaultValue(TypedArray, int):
     *  `CharSequence[] defaultValues = a.getTextArray(index); ... new HashSet<>(result)`. */
    @Override
    Object onGetDefaultValue(AttributeSet attrs) {
        return PreferenceAttrs.stringSet(getContext(), attrs, "defaultValue");
    }
    public MultiSelectListPreference(Context c, AttributeSet a, int defStyleAttr) {
        this(c, a, defStyleAttr, 0);
    }
    public MultiSelectListPreference(Context c, AttributeSet a) { this(c, a, 0, 0); }
    public MultiSelectListPreference(Context c) { this(c, null, 0, 0); }
    public MultiSelectListPreference() { super(); }

    public void setEntries(CharSequence[] entries) { mEntries = entries; }
    public void setEntries(int resId) { setEntries(textArray(resId)); }
    public CharSequence[] getEntries() { return mEntries; }

    public void setEntryValues(CharSequence[] entryValues) { mEntryValues = entryValues; }
    public void setEntryValues(int resId) { setEntryValues(textArray(resId)); }
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

    /** AOSP body: replace the set, then PERSIST it. */
    public void setValues(Set<String> values) {
        mValues.clear();
        if (values != null) mValues.addAll(values);
        persistStringSet(mValues);
    }

    public Set<String> getValues() { return mValues; }

    public int findIndexOfValue(String value) {
        if (mEntryValues == null || value == null) return -1;
        for (int i = mEntryValues.length - 1; i >= 0; --i) {
            if (mEntryValues[i] != null && value.contentEquals(mEntryValues[i])) return i;
        }
        return -1;
    }

    /** AOSP: which entries start checked. */
    private boolean[] getSelectedItems() {
        final CharSequence[] entries = mEntryValues;
        if (entries == null) return new boolean[0];
        boolean[] result = new boolean[entries.length];
        for (int i = 0; i < entries.length; i++) {
            result[i] = entries[i] != null && mValues.contains(entries[i].toString());
        }
        return result;
    }

    @Override
    protected void onPrepareDialogBuilder(android.app.AlertDialog.Builder builder) {
        if (mEntries == null || mEntryValues == null || mEntries.length != mEntryValues.length) {
            throw new IllegalStateException(
                "MultiSelectListPreference requires an entries array and an entryValues "
                + "array which are both the same length");
        }
        mNewValues = new HashSet<String>(mValues);
        builder.setMultiChoiceItems(mEntries, getSelectedItems(),
                new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(android.content.DialogInterface dialog, int which,
                                        boolean isChecked) {
                        if (which < 0 || mEntryValues == null || which >= mEntryValues.length) return;
                        String v = mEntryValues[which].toString();
                        if (isChecked) mPreferenceChanged |= mNewValues.add(v);
                        else           mPreferenceChanged |= mNewValues.remove(v);
                    }
                });
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult && mPreferenceChanged) {
            final Set<String> values = mNewValues;
            if (callChangeListener(values)) setValues(values);
        }
        mPreferenceChanged = false;
    }

    /** AOSP resolves the `defaultValue="@array/..."` reference to a Set<String>
     *  via TypedArray.getTextArray. Mirrored. */
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        if (a == null) return null;
        final CharSequence[] defaultValues = a.getTextArray(index);
        if (defaultValues == null) return null;
        final Set<String> result = new HashSet<String>();
        for (CharSequence cs : defaultValues) if (cs != null) result.add(cs.toString());
        return result;
    }

    /** AOSP body, with a tolerant cast. AOSP casts `(Set<String>) defaultValue`
     *  unconditionally; here the value can still be null (an XML that declares no
     *  android:defaultValue, or one whose `@array/` reference does not resolve),
     *  where AOSP's TypedArray would have produced an empty array. So defaultValue
     *  can be null or a
     *  non-Set here, where AOSP guarantees a Set. */
    @Override
    @SuppressWarnings("unchecked")
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            setValues(getPersistedStringSet(mValues));
        } else if (defaultValue instanceof Set) {
            setValues((Set<String>) defaultValue);
        } else {
            setValues(Collections.<String>emptySet());
        }
    }
}
