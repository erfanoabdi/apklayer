// android.preference.SwitchPreference -- a Preference whose widget is a Switch.
// The other half of AOSP's TwoStatePreference pair; added alongside
// CheckBoxPreference because a legacy preferences XML mixes the two freely and a
// missing one would be a NoClassDefFoundError at inflate time.
//
// AOSP reference:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/SwitchPreference.java
//   public class SwitchPreference extends TwoStatePreference
//   public SwitchPreference(Context, AttributeSet, int defStyleAttr, int defStyleRes)
//     + the 3 shorter forms
//   @Override protected void onBindView(View view)
//   public void setSwitchTextOn(CharSequence) / (int)
//   public void setSwitchTextOff(CharSequence) / (int)
//   public CharSequence getSwitchTextOn() / getSwitchTextOff()
package android.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

@SuppressWarnings("deprecation")
public class SwitchPreference extends TwoStatePreference {

    private CharSequence mSwitchOn;
    private CharSequence mSwitchOff;

    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // AOSP reads switchTextOn / switchTextOff off R.styleable.SwitchPreference
        // (summaryOn / summaryOff / disableDependentsState are handled by
        // TwoStatePreference's constructor, as on AOSP).
        if (context != null && attrs != null) {
            CharSequence on = PreferenceAttrs.text(context, attrs, "switchTextOn");
            if (on != null) mSwitchOn = on;
            CharSequence off = PreferenceAttrs.text(context, attrs, "switchTextOff");
            if (off != null) mSwitchOff = off;
        }
    }
    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public SwitchPreference(Context context, AttributeSet attrs) { this(context, attrs, 0, 0); }
    public SwitchPreference(Context context) { this(context, null, 0, 0); }
    public SwitchPreference() { super(); }

    public void setSwitchTextOn(CharSequence onText) { mSwitchOn = onText; notifyChanged(); }
    public void setSwitchTextOn(int resId) { setSwitchTextOn(resString(resId)); }
    public CharSequence getSwitchTextOn() { return mSwitchOn; }

    public void setSwitchTextOff(CharSequence offText) { mSwitchOff = offText; notifyChanged(); }
    public void setSwitchTextOff(int resId) { setSwitchTextOff(resString(resId)); }
    public CharSequence getSwitchTextOff() { return mSwitchOff; }

    private CharSequence resString(int resId) {
        Context c = getContext();
        if (c == null || resId == 0) return null;
        try { return c.getString(resId); } catch (Throwable t) { return null; }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        // AOSP looks up com.android.internal.R.id.switch_widget; that id is not
        // in public-final.xml, so the widget is located by type -- see the same
        // note in CheckBoxPreference.syncCheckboxView.
        Checkable c = findCheckable(view);
        if (c instanceof android.widget.Switch) {
            android.widget.Switch sw = (android.widget.Switch) c;
            if (mSwitchOn != null) sw.setTextOn(mSwitchOn);
            if (mSwitchOff != null) sw.setTextOff(mSwitchOff);
        }
        if (c != null) c.setChecked(isChecked());
    }

    private static Checkable findCheckable(View v) {
        if (v == null) return null;
        if (v instanceof Checkable) return (Checkable) v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Checkable hit = findCheckable(g.getChildAt(i));
                if (hit != null) return hit;
            }
        }
        return null;
    }
}
