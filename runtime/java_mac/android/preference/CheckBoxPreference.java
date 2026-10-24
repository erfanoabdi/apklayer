// android.preference.CheckBoxPreference -- a Preference whose widget is a
// CheckBox.
//
// AOSP reference:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/CheckBoxPreference.java
//   public class CheckBoxPreference extends TwoStatePreference
//   public CheckBoxPreference(Context, AttributeSet, int defStyleAttr, int defStyleRes)
//     + the 3 shorter forms
//   @Override protected void onBindView(View view)
//   private void syncCheckboxView(View view)
//
// Measured gap (debug/preflight.py, 2026-07-29): de.tobiasbielefeld.solitaire
// referenced `<init>(Context,AttributeSet,int,int)` and `onCreateView(ViewGroup)`
// -- the 4-arg constructor did not exist and onCreateView was not on the chain at
// all. That app also SUBCLASSES this, which is why the whole TwoStatePreference
// chain had to become real rather than be patched: the previous Mac.apk version
// extended Preference directly and kept an in-memory `checked` field that was
// never persisted, i.e. a checkbox setting that silently forgot itself between
// launches.
package android.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

@SuppressWarnings("deprecation")
public class CheckBoxPreference extends TwoStatePreference {

    public CheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    public CheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public CheckBoxPreference(Context context, AttributeSet attrs) { this(context, attrs, 0, 0); }
    public CheckBoxPreference(Context context) { this(context, null, 0, 0); }

    /** Not AOSP; retained from the previous Mac.apk stub. */
    public CheckBoxPreference() { super(); }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        syncCheckboxView(view);
    }

    /** AOSP looks up `com.android.internal.R.id.checkbox` and pushes the checked
     *  state into it. That id is NOT in core/res/res/values/public-final.xml, so
     *  unlike title/summary/icon/widget_frame there is no frozen number to
     *  mirror -- the checkbox is located by TYPE instead (the first Checkable
     *  descendant), which is the same view in every layout AOSP ships for this
     *  Preference and is robust against whichever layout produced the row. */
    private void syncCheckboxView(View view) {
        Checkable c = findCheckable(view);
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
