// android.preference.PreferenceGroupAdapter -- the ListAdapter that turns a
// PreferenceGroup into rows for the settings ListView. AOSP declares this
// `public` but `@hide`, so it is not part of the API signature; declared public
// here to match the real class file (an app that reached it reflectively on a
// device finds the same shape).
//
// Mirrors AOSP
// frameworks/base/core/java/android/preference/PreferenceGroupAdapter.java
// body-for-body for the parts that matter: the flatten walk (which is what
// makes a PreferenceCategory's children appear on the SAME screen while a nested
// PreferenceScreen's do not), the recycle bookkeeping, and the
// OnPreferenceChangeInternalListener plumbing that re-syncs the list when the
// hierarchy changes underneath it.
//
// Two AOSP details are deliberately simplified, and neither is observable to an
// app: the row-highlight overlay (mHighlightedPosition / mHighlightedDrawable)
// is Settings-app-only @hide surface with no public entry point, and the
// deferred sync goes through the same Handler contract but without AOSP's
// mIsSyncing notifyAll() rendezvous, which exists only to let a concurrent
// syncMyPreferences caller block -- nothing in this package has a second caller.
package android.preference;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public class PreferenceGroupAdapter extends BaseAdapter
        implements Preference.OnPreferenceChangeInternalListener {

    /** The group we are providing data from. */
    private final PreferenceGroup mPreferenceGroup;

    /** position -> Preference. These need not be direct children of the group;
     *  a PreferenceCategory's children are flattened in alongside it. */
    private List<Preference> mPreferenceList;

    /** AOSP: the distinct row LAYOUTS seen so far. getViewTypeCount() may only
     *  be answered once (ListView caches it), so after the first answer no new
     *  layout may claim a recycle slot -- hence mHasReturnedViewTypeCount. */
    private final ArrayList<PreferenceLayout> mPreferenceLayouts =
            new ArrayList<PreferenceLayout>();

    private PreferenceLayout mTempPreferenceLayout = new PreferenceLayout();

    private boolean mHasReturnedViewTypeCount = false;

    /** Lazily acquired; see onPreferenceHierarchyChange for what happens when a
     *  Looper is not available. */
    private Handler mHandler;
    private boolean mHandlerResolved;

    private final Runnable mSyncRunnable = new Runnable() {
        public void run() { syncMyPreferences(); }
    };

    private static class PreferenceLayout implements Comparable<PreferenceLayout> {
        private int resId;
        private int widgetResId;
        private String name;

        public int compareTo(PreferenceLayout other) {
            int compareNames = name.compareTo(other.name);
            if (compareNames == 0) {
                if (resId == other.resId) {
                    if (widgetResId == other.widgetResId) return 0;
                    return widgetResId - other.widgetResId;
                }
                return resId - other.resId;
            }
            return compareNames;
        }
    }

    public PreferenceGroupAdapter(PreferenceGroup preferenceGroup) {
        mPreferenceGroup = preferenceGroup;
        // If this group gets or loses any children, let us know.
        mPreferenceGroup.setOnPreferenceChangeInternalListener(this);
        mPreferenceList = new ArrayList<Preference>();
        syncMyPreferences();
    }

    private void syncMyPreferences() {
        List<Preference> newPreferenceList = new ArrayList<Preference>(mPreferenceList.size());
        flattenPreferenceGroup(newPreferenceList, mPreferenceGroup);
        mPreferenceList = newPreferenceList;

        notifyDataSetChanged();
    }

    private void flattenPreferenceGroup(List<Preference> preferences, PreferenceGroup group) {
        group.sortPreferences();

        final int groupSize = group.getPreferenceCount();
        for (int i = 0; i < groupSize; i++) {
            final Preference preference = group.getPreference(i);

            preferences.add(preference);

            if (!mHasReturnedViewTypeCount && preference.isRecycleEnabled()) {
                addPreferenceClassName(preference);
            }

            if (preference instanceof PreferenceGroup) {
                final PreferenceGroup preferenceAsGroup = (PreferenceGroup) preference;
                if (preferenceAsGroup.isOnSameScreenAsChildren()) {
                    flattenPreferenceGroup(preferences, preferenceAsGroup);
                }
            }

            preference.setOnPreferenceChangeInternalListener(this);
        }
    }

    /** AOSP createPreferenceLayout. */
    private PreferenceLayout createPreferenceLayout(Preference preference, PreferenceLayout in) {
        PreferenceLayout pl = in != null ? in : new PreferenceLayout();
        pl.name = preference.getClass().getName();
        pl.resId = preference.getLayoutResource();
        pl.widgetResId = preference.getWidgetLayoutResource();
        return pl;
    }

    private void addPreferenceClassName(Preference preference) {
        final PreferenceLayout pl = createPreferenceLayout(preference, null);
        int insertPos = Collections.binarySearch(mPreferenceLayouts, pl);

        // Only insert if it doesn't exist (when it is negative).
        if (insertPos < 0) {
            // Convert to insert index
            insertPos = insertPos * -1 - 1;
            mPreferenceLayouts.add(insertPos, pl);
        }
    }

    public int getCount() {
        return mPreferenceList.size();
    }

    public Preference getItem(int position) {
        if (position < 0 || position >= getCount()) return null;
        return mPreferenceList.get(position);
    }

    public long getItemId(int position) {
        if (position < 0 || position >= getCount()) return AdapterView.INVALID_ROW_ID;
        Preference p = getItem(position);
        return p == null ? AdapterView.INVALID_ROW_ID : p.getId();
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        final Preference preference = getItem(position);
        if (preference == null) return convertView;

        // Build a PreferenceLayout to compare with the ones known to be cacheable.
        mTempPreferenceLayout = createPreferenceLayout(preference, mTempPreferenceLayout);

        // If it's not one of the cached ones, drop convertView so the layout
        // gets re-created by the Preference. This is what setRecycleEnabled(false)
        // buys an app: its class never enters mPreferenceLayouts, so it never
        // matches here and never receives a recycled view.
        if (Collections.binarySearch(mPreferenceLayouts, mTempPreferenceLayout) < 0) {
            convertView = null;
        }
        return preference.getView(convertView, parent);
    }

    @Override
    public boolean isEnabled(int position) {
        if (position < 0 || position >= getCount()) return true;
        Preference p = getItem(position);
        return p == null || p.isSelectable();
    }

    @Override
    public boolean areAllItemsEnabled() {
        // AOSP: "There should always be a preference group, and these groups are
        // always disabled."
        return false;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        if (!mHasReturnedViewTypeCount) mHasReturnedViewTypeCount = true;

        final Preference preference = getItem(position);
        if (preference == null || !preference.isRecycleEnabled()) return IGNORE_ITEM_VIEW_TYPE;

        mTempPreferenceLayout = createPreferenceLayout(preference, mTempPreferenceLayout);

        int viewType = Collections.binarySearch(mPreferenceLayouts, mTempPreferenceLayout);
        if (viewType < 0) return IGNORE_ITEM_VIEW_TYPE;
        return viewType;
    }

    @Override
    public int getViewTypeCount() {
        if (!mHasReturnedViewTypeCount) mHasReturnedViewTypeCount = true;
        return Math.max(1, mPreferenceLayouts.size());
    }

    // ---- OnPreferenceChangeInternalListener --------------------------------

    /** A child's value/appearance changed: re-bind, do not re-flatten. */
    public void onPreferenceChange(Preference preference) {
        notifyDataSetChanged();
    }

    /** A child was added or removed anywhere below us: re-flatten, coalescing a
     *  burst of changes into one pass (AOSP posts the same runnable).
     *
     *  AOSP builds its Handler in the CONSTRUCTOR with `new Handler()`, which
     *  needs a prepared Looper on the calling thread. Doing that here would make
     *  the adapter's constructor -- and therefore the whole settings screen --
     *  fail on any thread without one. Instead the Handler is resolved once,
     *  lazily, and a missing Looper falls back to syncing INLINE. That is a
     *  weaker batching guarantee, not a weaker result: the list is re-flattened
     *  either way, just immediately instead of at the end of the burst. */
    public void onPreferenceHierarchyChange(Preference preference) {
        Handler h = handler();
        if (h == null) {
            syncMyPreferences();
            return;
        }
        h.removeCallbacks(mSyncRunnable);
        h.post(mSyncRunnable);
    }

    private Handler handler() {
        if (mHandlerResolved) return mHandler;
        mHandlerResolved = true;
        try {
            Looper looper = Looper.myLooper();
            if (looper == null) looper = Looper.getMainLooper();
            if (looper != null) mHandler = new Handler(looper);
        } catch (Throwable t) {
            mHandler = null;
        }
        return mHandler;
    }
}
