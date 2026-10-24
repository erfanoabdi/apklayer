// android.preference.PreferenceGroup -- a Preference that contains other
// Preferences. AOSP base of both PreferenceCategory and PreferenceScreen.
//
// AOSP reference (fetched 2026-07-29):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/PreferenceGroup.java
//   public abstract class PreferenceGroup extends Preference
//           implements GenericInflater.Parent<Preference>
//   public PreferenceGroup(Context, AttributeSet, int defStyleAttr, int defStyleRes)
//   public PreferenceGroup(Context, AttributeSet, int defStyleAttr)
//   public PreferenceGroup(Context, AttributeSet)
//   public void setOrderingAsAdded(boolean)      public boolean isOrderingAsAdded()
//   public void addItemFromInflater(Preference)  public int getPreferenceCount()
//   public Preference getPreference(int)         public boolean addPreference(Preference)
//   public boolean removePreference(Preference)  public void removeAll()
//   protected boolean onPrepareAddPreference(Preference)
//   public Preference findPreference(CharSequence)
//   protected boolean isOnSameScreenAsChildren()
//   protected void onAttachedToActivity()        protected void onPrepareForRemoval()
//   public void notifyDependencyChange(boolean)
//   protected void dispatchSaveInstanceState(Bundle)
//   protected void dispatchRestoreInstanceState(Bundle)
//
// The `implements GenericInflater.Parent<Preference>` clause is NOT mirrored:
// android.preference.GenericInflater is package-private AOSP internals, so no
// app bytecode can reference the interface, and adding it would only force a
// second non-API class into the framework jar.
//
// NOT abstract here, unlike AOSP. AOSP can afford abstract because its own
// PreferenceInflater is the only thing that ever instantiates the concrete
// subclasses, and its XML never names the abstract tag. Ours behaves the same
// way, but a `<PreferenceGroup>`-shaped tag reaching Class.forName().newInstance()
// through an APP'S own inflater must not fail on InstantiationException, and that
// path is not ours to constrain. Nothing else about the class differs.
package android.preference;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public class PreferenceGroup extends Preference
        implements Preference.OnPreferenceChangeInternalListener {

    private final List<Preference> mPreferenceList = new ArrayList<Preference>();
    private boolean mOrderingAsAdded = true;
    private int mCurrentPreferenceOrder = 0;
    private boolean mAttachedToActivity = false;

    public PreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // AOSP: mOrderingAsAdded = a.getBoolean(PreferenceGroup_orderingFromXml,
        // mOrderingAsAdded). An app that sets android:orderingFromXml="false"
        // wants its rows sorted by title, and dropping the attribute silently
        // reorders its settings screen.
        mOrderingAsAdded =
                PreferenceAttrs.bool(context, attrs, "orderingFromXml", mOrderingAsAdded);
    }
    public PreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public PreferenceGroup(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }
    public PreferenceGroup(Context context) { this(context, null, 0, 0); }

    /** Not AOSP; retained so the pre-existing `new PreferenceScreen()` shape in
     *  the tree keeps compiling. */
    public PreferenceGroup() { super(); }

    /** AOSP: "Whether to order the Preference children of this group as they
     *  are added." */
    public void setOrderingAsAdded(boolean orderingAsAdded) { mOrderingAsAdded = orderingAsAdded; }
    public boolean isOrderingAsAdded() { return mOrderingAsAdded; }

    public void addItemFromInflater(Preference preference) { addPreference(preference); }

    public int getPreferenceCount() { return mPreferenceList.size(); }

    public Preference getPreference(int index) {
        return index >= 0 && index < mPreferenceList.size() ? mPreferenceList.get(index) : null;
    }

    public boolean addPreference(Preference preference) {
        if (preference == null) return false;
        if (mPreferenceList.contains(preference)) return true;
        if (!onPrepareAddPreference(preference)) return false;

        if (preference.getOrder() == Preference.DEFAULT_ORDER) {
            if (mOrderingAsAdded) preference.setOrder(mCurrentPreferenceOrder++);
            if (preference instanceof PreferenceGroup) {
                ((PreferenceGroup) preference).setOrderingAsAdded(mOrderingAsAdded);
            }
        }

        int insertionIndex = Collections.binarySearch(mPreferenceList, preference);
        if (insertionIndex < 0) insertionIndex = insertionIndex * -1 - 1;
        mPreferenceList.add(insertionIndex, preference);

        preference.assignParent(this);          // AOSP: backs Preference.getParent()
        PreferenceManager pm = getPreferenceManager();
        if (pm != null) preference.onAttachedToHierarchy(pm);
        preference.setOnPreferenceChangeInternalListener(this);
        if (mAttachedToActivity) preference.onAttachedToActivity();
        notifyHierarchyChanged();
        return true;
    }

    public boolean removePreference(Preference preference) {
        boolean removed = mPreferenceList.remove(preference);
        if (removed) {
            preference.assignParent(null);
            preference.setOnPreferenceChangeInternalListener(null);
            preference.onPrepareForRemoval();
            notifyHierarchyChanged();
        }
        return removed;
    }

    public void removeAll() {
        for (Preference p : new ArrayList<Preference>(mPreferenceList)) {
            p.assignParent(null);
            p.setOnPreferenceChangeInternalListener(null);
            p.onPrepareForRemoval();
        }
        mPreferenceList.clear();
        mCurrentPreferenceOrder = 0;
        notifyHierarchyChanged();
    }

    /** AOSP package-private sortPreferences(): the adapter calls this before
     *  every flatten so a child whose order changed after insertion still lands
     *  in the right row. Collections.sort is stable, which is what keeps two
     *  equal-order rows in insertion order. */
    void sortPreferences() {
        Collections.sort(mPreferenceList);
    }

    /** AOSP hook: "Prepares a Preference to be added to the group." */
    protected boolean onPrepareAddPreference(Preference preference) {
        preference.onParentChanged(this, shouldDisableDependents());
        return true;
    }

    /** AOSP: recursive key lookup over this group and its descendants. */
    public Preference findPreference(CharSequence key) {
        if (key == null) return null;
        if (key.equals(getKey())) return this;
        for (Preference p : mPreferenceList) {
            String curKey = p.getKey();
            if (curKey != null && curKey.equals(key.toString())) return p;
            if (p instanceof PreferenceGroup) {
                Preference hit = ((PreferenceGroup) p).findPreference(key);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /** AOSP: "Whether this preference group should be shown on the same screen
     *  as its contained preferences." True for PreferenceCategory, false for
     *  PreferenceScreen (which opens a new screen). */
    protected boolean isOnSameScreenAsChildren() { return true; }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();
        mAttachedToActivity = true;
        for (Preference p : mPreferenceList) p.onAttachedToActivity();
    }

    @Override
    protected void onPrepareForRemoval() {
        super.onPrepareForRemoval();
        mAttachedToActivity = false;
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        for (Preference p : mPreferenceList) p.onAttachedToHierarchy(preferenceManager);
    }

    @Override
    public void notifyDependencyChange(boolean disableDependents) {
        super.notifyDependencyChange(disableDependents);
        for (Preference p : mPreferenceList) p.onParentChanged(this, disableDependents);
    }

    @Override
    protected void dispatchSaveInstanceState(Bundle container) {
        super.dispatchSaveInstanceState(container);
        for (Preference p : mPreferenceList) p.dispatchSaveInstanceState(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(Bundle container) {
        super.dispatchRestoreInstanceState(container);
        for (Preference p : mPreferenceList) p.dispatchRestoreInstanceState(container);
    }

    // OnPreferenceChangeInternalListener -- AOSP forwards a child's change up so
    // the hosting adapter can re-bind.
    @Override
    public void onPreferenceChange(Preference preference) { notifyChanged(); }
    @Override
    public void onPreferenceHierarchyChange(Preference preference) { notifyHierarchyChanged(); }
}
