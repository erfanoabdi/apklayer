// android.preference.PreferenceFragment -- the framework (non-androidx) Fragment
// that hosts a preference hierarchy.
//
// AOSP reference (aosp-mirror android16-release, fetched 2026-08-24):
//   frameworks/base/core/java/android/preference/PreferenceFragment.java
//   public abstract class PreferenceFragment extends Fragment
//   public void addPreferencesFromResource(@XmlRes int)
//   public void addPreferencesFromIntent(Intent)
//   public PreferenceScreen getPreferenceScreen() / setPreferenceScreen(PreferenceScreen)
//   public PreferenceManager getPreferenceManager()
//   public Preference findPreference(CharSequence)
//   public boolean onPreferenceTreeClick(PreferenceScreen, Preference)
//   public interface OnPreferenceStartFragmentCallback
//
// ★ addPreferencesFromResource used to be a no-op carrying a KNOWN GAP note
// ("it needs a real preference-XML inflater"). PreferenceInflater is that
// inflater, so it inflates for real now: the hierarchy exists, findPreference()
// resolves, and the rows bind into the fragment's own ListView.
//
// ONE DIVERGENCE: AOSP inflates com.android.internal.R.layout
// .preference_list_fragment for its view. Mac.apk has no internal layouts, so
// onCreateView builds the same shape in code -- a ListView carrying the frozen
// AOSP public id android.R.id.list, which is what app code looks up and what
// getListView() must find.
package android.preference;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

@SuppressWarnings("deprecation")
public class PreferenceFragment extends android.app.Fragment
        implements PreferenceManager.OnPreferenceTreeClickListener {

    /** Frozen AOSP public id (core/res/res/values/public-final.xml):
     *    <public type="id" name="list" id="0x0102000a" /> */
    private static final int ID_LIST = 0x0102000a;

    /** AOSP's savedInstanceState key for the hierarchy's own state. */
    private static final String PREFERENCES_TAG = "android:preferences";

    private PreferenceManager mPreferenceManager;
    private ListView mList;
    private boolean mHavePrefs;
    private boolean mInitDone;

    public PreferenceFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // See the divergence note in the file header.
        android.content.Context ctx = getActivity();
        if (ctx == null) return super.onCreateView(inflater, container, savedInstanceState);
        ListView list = new ListView(ctx);
        list.setId(ID_LIST);
        mList = list;
        return list;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mHavePrefs) bindPreferences();

        mInitDone = true;

        if (savedInstanceState != null) {
            Bundle container = savedInstanceState.getBundle(PREFERENCES_TAG);
            if (container != null) {
                final PreferenceScreen preferenceScreen = getPreferenceScreen();
                if (preferenceScreen != null) preferenceScreen.restoreHierarchyState(container);
            }
        }
    }

    /** AOSP body. */
    public void onSaveInstanceState(Bundle outState) {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            Bundle container = new Bundle();
            preferenceScreen.saveHierarchyState(container);
            outState.putBundle(PREFERENCES_TAG, container);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mPreferenceManager != null) mPreferenceManager.dispatchActivityStop();
    }

    @Override
    public void onDestroyView() {
        mList = null;
        onUnbindPreferences();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (mPreferenceManager != null) {
            mPreferenceManager.dispatchActivityDestroy();
            // AOSP drops the listener here so a destroyed fragment cannot be
            // called back by a hierarchy that outlives it.
            mPreferenceManager.setOnPreferenceTreeClickListener(null);
        }
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mPreferenceManager != null) {
            mPreferenceManager.dispatchActivityResult(requestCode, resultCode, data);
        }
    }

    // ---- the hierarchy -----------------------------------------------------

    /** AOSP body. No longer a no-op -- see the file header. */
    public void addPreferencesFromResource(int preferencesResId) {
        requirePreferenceManager();
        setPreferenceScreen(mPreferenceManager.inflateFromResource(getActivity(),
                preferencesResId, getPreferenceScreen()));
    }

    /** AOSP body. */
    public void addPreferencesFromIntent(Intent intent) {
        requirePreferenceManager();
        setPreferenceScreen(mPreferenceManager.inflateFromIntent(intent, getPreferenceScreen()));
    }

    /** NOT AOSP for this class -- it is androidx.preference.PreferenceFragmentCompat's
     *  shape, and some apps call it on the framework class by mistake or through
     *  a shared base. Routed to the real inflater so the mistake still produces a
     *  working screen; rootKey selects a sub-tree exactly as androidx does. */
    public void setPreferencesFromResource(int preferencesResId, String rootKey) {
        requirePreferenceManager();
        PreferenceScreen inflated =
                mPreferenceManager.inflateFromResource(getActivity(), preferencesResId, null);
        if (rootKey != null && inflated != null) {
            Preference root = inflated.findPreference(rootKey);
            if (root instanceof PreferenceScreen) inflated = (PreferenceScreen) root;
        }
        setPreferenceScreen(inflated);
    }

    public Preference findPreference(CharSequence key) {
        return mPreferenceManager == null ? null : mPreferenceManager.findPreference(key);
    }

    public PreferenceScreen getPreferenceScreen() {
        return mPreferenceManager == null ? null : mPreferenceManager.getPreferenceScreen();
    }

    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        requirePreferenceManager();
        if (mPreferenceManager.setPreferences(preferenceScreen) && preferenceScreen != null) {
            mHavePrefs = true;
            if (mInitDone) bindPreferences();
        }
    }

    /** AOSP hands back the manager it built in onCreate. Created lazily here so
     *  it works regardless of how far the fragment lifecycle got. */
    public PreferenceManager getPreferenceManager() {
        requirePreferenceManager();
        return mPreferenceManager;
    }

    private void requirePreferenceManager() {
        if (mPreferenceManager != null) return;
        android.app.Activity activity = null;
        try { activity = getActivity(); } catch (Throwable ignored) {}
        if (activity != null) {
            // AOSP: `new PreferenceManager(getActivity(), FIRST_REQUEST_CODE)`
            // with FIRST_REQUEST_CODE == 100.
            mPreferenceManager = new PreferenceManager(activity, 100);
            mPreferenceManager.setFragment(this);
            mPreferenceManager.setOnPreferenceTreeClickListener(this);
            return;
        }
        android.content.Context ctx = null;
        try { ctx = getContext(); } catch (Throwable ignored) {}
        if (ctx != null) {
            mPreferenceManager = new PreferenceManager(ctx);
            mPreferenceManager.setOnPreferenceTreeClickListener(this);
        }
    }

    private void bindPreferences() {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen == null) return;
        ListView list = getListView();
        if (list == null) return;
        preferenceScreen.bind(list);
        onBindPreferences();
    }

    /** AOSP @hide hooks around bindPreferences, kept so a subclass that
     *  overrides them (Settings-app-derived code does) still gets called. */
    protected void onBindPreferences() {}
    protected void onUnbindPreferences() {}

    /** AOSP: the ListView showing the rows. */
    public ListView getListView() {
        if (mList != null) return mList;
        View root = null;
        try { root = getView(); } catch (Throwable ignored) {}
        if (root != null) {
            View found = root.findViewById(ID_LIST);
            if (found instanceof ListView) mList = (ListView) found;
        }
        return mList;
    }

    /** AOSP body: a Preference declaring android:fragment asks the hosting
     *  activity to open it. PreferenceActivity implements the callback. */
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                                         Preference preference) {
        if (preference.getFragment() != null
                && getActivity() instanceof OnPreferenceStartFragmentCallback) {
            return ((OnPreferenceStartFragmentCallback) getActivity())
                    .onPreferenceStartFragment(this, preference);
        }
        return false;
    }

    public interface OnPreferenceStartFragmentCallback {
        boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref);
    }
}
