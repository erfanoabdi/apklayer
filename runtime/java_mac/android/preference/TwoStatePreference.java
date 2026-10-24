// android.preference.TwoStatePreference -- AOSP's shared base of
// CheckBoxPreference and SwitchPreference: a Preference with a boolean state
// persisted through persistBoolean().
//
// AOSP reference (fetched 2026-07-29, bodies mirrored verbatim where quoted):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/TwoStatePreference.java
//   public abstract class TwoStatePreference extends Preference
//   protected void onClick()                public void setChecked(boolean)
//   public boolean isChecked()              public boolean shouldDisableDependents()
//   set/getSummaryOn, set/getSummaryOff
//   public boolean getDisableDependentsState()
//   public void setDisableDependentsState(boolean)
//   protected Object onGetDefaultValue(TypedArray, int)
//   protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
//   void syncSummaryView(View view)
//   protected Parcelable onSaveInstanceState() / onRestoreInstanceState(Parcelable)
//
// This class did NOT exist in Mac.apk before: CheckBoxPreference extended
// Preference directly and held its own in-memory `checked` field that was never
// persisted. That is why the whole family is being rebuilt here rather than
// patched -- a checkbox setting that silently forgets itself between launches is
// exactly the failure mode the no-shortcuts rule exists to prevent.
//
// NOT abstract, unlike AOSP, for the same reason as PreferenceGroup /
// DialogPreference: a <TwoStatePreference>-shaped tag reaching an app's OWN
// inflater via Class.forName().newInstance() must not fail with
// InstantiationException. (Our PreferenceInflater would not construct it either
// way -- AOSP's own XML never uses the abstract tag -- but an app's custom
// inflater is not ours to constrain.)
package android.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public class TwoStatePreference extends Preference {

    // Frozen AOSP public id, verified against core/res/res/values/public-final.xml:
    //   <public type="id" name="summary" id="0x01020010" />
    // AOSP uses com.android.internal.R.id.summary, which is the same number.
    private static final int ID_SUMMARY = 0x01020010;

    CharSequence mSummaryOn;
    CharSequence mSummaryOff;
    boolean mChecked;
    private boolean mCheckedSet;
    boolean mDisableDependentsState;

    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // AOSP reads summaryOn / summaryOff / disableDependentsState off the
        // CheckBoxPreference and SwitchPreference styleables (both declare the
        // same three). Reading them once here covers both subclasses; the
        // attribute NAMES are identical, which is all that matters when the
        // lookup is by name.
        if (context != null && attrs != null) {
            CharSequence on = PreferenceAttrs.text(context, attrs, "summaryOn");
            if (on != null) mSummaryOn = on;
            CharSequence off = PreferenceAttrs.text(context, attrs, "summaryOff");
            if (off != null) mSummaryOff = off;
            mDisableDependentsState = PreferenceAttrs.bool(
                    context, attrs, "disableDependentsState", mDisableDependentsState);
        }
    }

    /** AOSP onGetDefaultValue(TypedArray, int): `a.getBoolean(index, false)`. */
    @Override
    Object onGetDefaultValue(AttributeSet attrs) {
        return Boolean.valueOf(PreferenceAttrs.bool(getContext(), attrs, "defaultValue", false));
    }
    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public TwoStatePreference(Context context, AttributeSet attrs) { this(context, attrs, 0, 0); }
    public TwoStatePreference(Context context) { this(context, null, 0, 0); }

    /** Not AOSP; kept because the previous Mac.apk CheckBoxPreference chain
     *  allowed a context-less construction. */
    public TwoStatePreference() { super(); }

    /** AOSP body, verbatim. */
    @Override
    protected void onClick() {
        super.onClick();
        final boolean newValue = !isChecked();
        if (callChangeListener(newValue)) {
            setChecked(newValue);
        }
    }

    /** AOSP body, verbatim -- note it PERSISTS, which the old Mac.apk
     *  CheckBoxPreference never did. */
    public void setChecked(boolean checked) {
        final boolean changed = mChecked != checked;
        if (changed || !mCheckedSet) {
            mChecked = checked;
            mCheckedSet = true;
            persistBoolean(checked);
            if (changed) {
                notifyDependencyChange(shouldDisableDependents());
                notifyChanged();
            }
        }
    }

    public boolean isChecked() { return mChecked; }

    /** AOSP body, verbatim. */
    @Override
    public boolean shouldDisableDependents() {
        boolean shouldDisable = mDisableDependentsState ? mChecked : !mChecked;
        return shouldDisable || super.shouldDisableDependents();
    }

    public void setSummaryOn(CharSequence summary) {
        mSummaryOn = summary;
        if (isChecked()) notifyChanged();
    }
    public void setSummaryOn(int summaryResId) { setSummaryOn(resString(summaryResId)); }
    public CharSequence getSummaryOn() { return mSummaryOn; }

    public void setSummaryOff(CharSequence summary) {
        mSummaryOff = summary;
        if (!isChecked()) notifyChanged();
    }
    public void setSummaryOff(int summaryResId) { setSummaryOff(resString(summaryResId)); }
    public CharSequence getSummaryOff() { return mSummaryOff; }

    public boolean getDisableDependentsState() { return mDisableDependentsState; }
    public void setDisableDependentsState(boolean disableDependentsState) {
        mDisableDependentsState = disableDependentsState;
    }

    private CharSequence resString(int resId) {
        Context c = getContext();
        if (c == null || resId == 0) return null;
        try { return c.getString(resId); } catch (Throwable t) { return null; }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a == null ? Boolean.FALSE : Boolean.valueOf(a.getBoolean(index, false));
    }

    /** AOSP body, with one guard: AOSP casts `(Boolean) defaultValue` and NPEs
     *  when the XML declared no defaultValue at all. Mac.apk's
     *  XML may declare no android:defaultValue at all, in which case AOSP's
     *  TypedArray slot is absent and mDefaultValue stays null. A null is treated
     *  as `false`, which is AOSP's own no-attribute default
     *  (`a.getBoolean(index, false)`). */
    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setChecked(restoreValue ? getPersistedBoolean(mChecked)
                : (defaultValue instanceof Boolean ? (Boolean) defaultValue : false));
    }

    /** AOSP body, using the frozen public summary id in place of
     *  com.android.internal.R.id.summary (identical value). TextUtils.isEmpty is
     *  inlined so this does not depend on that class. */
    void syncSummaryView(View view) {
        if (view == null) return;
        View found = view.findViewById(ID_SUMMARY);
        if (!(found instanceof TextView)) return;
        TextView summaryView = (TextView) found;

        boolean useDefaultSummary = true;
        if (mChecked && !isEmpty(mSummaryOn)) {
            summaryView.setText(mSummaryOn);
            useDefaultSummary = false;
        } else if (!mChecked && !isEmpty(mSummaryOff)) {
            summaryView.setText(mSummaryOff);
            useDefaultSummary = false;
        }

        if (useDefaultSummary) {
            final CharSequence summary = getSummary();
            if (!isEmpty(summary)) {
                summaryView.setText(summary);
                useDefaultSummary = false;
            }
        }

        int newVisibility = useDefaultSummary ? View.GONE : View.VISIBLE;
        if (newVisibility != summaryView.getVisibility()) {
            summaryView.setVisibility(newVisibility);
        }
    }

    private static boolean isEmpty(CharSequence cs) { return cs == null || cs.length() == 0; }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        syncSummaryView(view);
    }

    @Override
    protected Parcelable onSaveInstanceState() { return super.onSaveInstanceState(); }

    @Override
    protected void onRestoreInstanceState(Parcelable state) { super.onRestoreInstanceState(state); }
}
