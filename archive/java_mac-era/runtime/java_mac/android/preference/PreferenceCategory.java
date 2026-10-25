// android.preference.PreferenceCategory -- a PreferenceGroup that draws a
// section header and shows its children on the SAME screen.
//
// Measured gap: referenced by de.tobiasbielefeld.solitaire (Simple Solitaire
// Collection) and com.physphil.android.unitconverterultimate, both from the
// 2026-07-29 F-Droid batch (debug/preflight.py reported it as a missing
// PLATFORM class for both).
//
// AOSP reference:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/PreferenceCategory.java
//   public class PreferenceCategory extends PreferenceGroup
//   protected boolean onPrepareAddPreference(Preference preference)  // rejects nested groups
//   public boolean isEnabled()          // always false: a category is a header, not a row
//   public boolean shouldDisableDependents()
// The AOSP class also sets its layout to layout.preference_category in the
// constructor; Mac.apk has no internal layout resources, so Preference's
// programmatic row is used (see Preference.onCreateView).
package android.preference;

import android.content.Context;
import android.util.AttributeSet;

@SuppressWarnings("deprecation")
public class PreferenceCategory extends PreferenceGroup {

    public PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    public PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public PreferenceCategory(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }
    public PreferenceCategory(Context context) { this(context, null, 0, 0); }

    /** AOSP: "Categories cannot be nested." */
    @Override
    protected boolean onPrepareAddPreference(Preference preference) {
        if (preference instanceof PreferenceCategory) {
            throw new IllegalArgumentException(
                "Cannot add a " + preference.getClass().getSimpleName()
                + " directly to a PreferenceCategory");
        }
        return super.onPrepareAddPreference(preference);
    }

    /** AOSP returns false unconditionally -- a category row is a header and is
     *  never selectable/enabled itself. */
    @Override
    public boolean isEnabled() { return false; }

    @Override
    public boolean shouldDisableDependents() { return false; }
}
