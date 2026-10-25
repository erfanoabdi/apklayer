// android.preference.PreferenceManager -- the object that owns a preference
// hierarchy's SharedPreferences store, plus the static
// getDefaultSharedPreferences / setDefaultValues family that a great deal of
// legacy code calls without ever touching a preference UI.
//
// AOSP reference (fetched 2026-07-29):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/PreferenceManager.java
//   public static final String KEY_HAS_SET_DEFAULT_VALUES = "_has_set_default_values";
//   public static void setDefaultValues(Context context, @XmlRes int resId, boolean readAgain)
//   public static void setDefaultValues(Context context, String sharedPreferencesName,
//           int sharedPreferencesMode, int resId, boolean readAgain)
//   public static SharedPreferences getDefaultSharedPreferences(Context context)
//   public static String getDefaultSharedPreferencesName(Context context)
//   private static int getDefaultSharedPreferencesMode()
//   public SharedPreferences getSharedPreferences()
//   public String getSharedPreferencesName()   public int getSharedPreferencesMode()
//   @Nullable public Preference findPreference(CharSequence key)
//   public PreferenceScreen createPreferenceScreen(Context context)
//   SharedPreferences.Editor getEditor()       boolean shouldCommit()
//
// ★ THE MEASURED BUG THIS FIXES (debug/preflight.py, 2026-07-29):
//   `setDefaultValues(Context,int,boolean)` returns **void** in AOSP. The previous
//   Mac.apk stub declared it `boolean`, which is a DIFFERENT descriptor
//   (`(Landroid/content/Context;IZ)Z` vs `...)V`), so every app calling it got a
//   NoSuchMethodError -- and because ActivityHost swallows onCreate exceptions,
//   that aborts onCreate early and leaves late-set singletons null (memory
//   `activity-oncreate-abort-null-singleton`). Two of the 17 new F-Droid apps
//   (ch.logixisland.anuto, com.physphil.android.unitconverterultimate) call it
//   from their Application/Activity init.
//
// The class was also `private PreferenceManager() {}` static-only. AOSP has no
// PUBLIC constructor either, so the public API surface is unchanged -- but it
// now has a package-private one so PreferenceActivity / PreferenceFragment /
// Preference can hold a real manager and the getSharedPreferences() chain that
// Preference.persist*() depends on actually resolves.
package android.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

@SuppressWarnings("deprecation")
public class PreferenceManager {

    /** AOSP constant, verbatim. Apps read it directly to decide whether their
     *  own first-run setup has happened. */
    public static final String KEY_HAS_SET_DEFAULT_VALUES = "_has_set_default_values";

    /** AOSP: "The context to use. This should always be set." */
    private final Context mContext;
    private String mSharedPreferencesName;
    private int mSharedPreferencesMode;
    private SharedPreferences mSharedPreferences;
    private PreferenceScreen mPreferenceScreen;

    /** AOSP storage modes. STORAGE_DEFAULT is credential-encrypted storage
     *  (unreadable until the user unlocks); STORAGE_DEVICE_PROTECTED is the
     *  direct-boot-safe area. Mac.apk has ONE storage area, so the flag is
     *  tracked and reported faithfully but selects the same files -- see
     *  setStorageDeviceProtected(). */
    private static final int STORAGE_DEFAULT = 0;
    private static final int STORAGE_DEVICE_PROTECTED = 1;
    private int mStorage = STORAGE_DEFAULT;

    private PreferenceDataStore mPreferenceDataStore;

    /** AOSP: the Activity / PreferenceFragment hosting this hierarchy, used by
     *  RingtonePreference and any DialogPreference to start an activity for a
     *  result. Weakly held on AOSP; a plain field here because the manager does
     *  not outlive its host in Mac.apk (nothing caches a PreferenceManager
     *  statically). */
    private android.app.Activity mActivity;
    private PreferenceFragment mFragment;

    private long mNextId = 0;
    private int mNextRequestCode;

    private java.util.List<OnActivityResultListener> mActivityResultListeners;
    private java.util.List<OnActivityStopListener> mActivityStopListeners;
    private java.util.List<OnActivityDestroyListener> mActivityDestroyListeners;

    /** AOSP mNoCommit: true while a batch (setDefaultValues, or the activity's
     *  own teardown) is running, so individual persist calls accumulate in one
     *  editor instead of each hitting the store. */
    private boolean mNoCommit;
    private SharedPreferences.Editor mEditor;

    /** Package-private, matching AOSP (which has no public constructor at all --
     *  apps reach a manager only through getPreferenceManager()). */
    PreferenceManager(Context context) {
        mContext = context;
        mSharedPreferencesName = getDefaultSharedPreferencesName(context);
        mSharedPreferencesMode = getDefaultSharedPreferencesMode();
    }

    /** AOSP's activity-hosted constructor, `PreferenceManager(Activity, int)`. */
    PreferenceManager(android.app.Activity activity, int firstRequestCode) {
        this((Context) activity);
        mActivity = activity;
        mNextRequestCode = firstRequestCode;
    }

    android.app.Activity getActivity() { return mActivity; }
    void setFragment(PreferenceFragment fragment) { mFragment = fragment; }
    PreferenceFragment getFragment() { return mFragment; }

    // ---- static surface ---------------------------------------------------

    public static SharedPreferences getDefaultSharedPreferences(Context ctx) {
        if (ctx == null) return null;
        // MODE_PRIVATE = 0; Mac.apk's Context impl ignores the mode bits.
        return ctx.getSharedPreferences(getDefaultSharedPreferencesName(ctx), 0);
    }

    public static String getDefaultSharedPreferencesName(Context ctx) {
        // AOSP: `context.getPackageName() + "_preferences"`. Many callers depend
        // on this exact spelling, so it is mirrored literally.
        String pkg = ctx != null ? ctx.getPackageName() : "macapk";
        return (pkg == null ? "macapk" : pkg) + "_preferences";
    }

    /** AOSP declares this PRIVATE. Kept public because the previous Mac.apk stub
     *  exposed it and some in-tree/app code may reference it; widening
     *  visibility cannot break a caller. */
    public static int getDefaultSharedPreferencesMode() { return 0; }

    /**
     * AOSP: `public static void setDefaultValues(Context, int resId, boolean readAgain)`
     * -- note VOID. See the header for why the old boolean return was a real bug.
     */
    public static void setDefaultValues(Context context, int resId, boolean readAgain) {
        setDefaultValues(context, getDefaultSharedPreferencesName(context),
                getDefaultSharedPreferencesMode(), resId, readAgain);
    }

    /**
     * AOSP: `public static void setDefaultValues(Context, String, int, int, boolean)`.
     *
     * AOSP's body opens the `_has_set_default_values` SharedPreferences file and,
     * when `readAgain || !alreadyDone`, constructs a PreferenceManager and runs
     * `inflateFromResource(context, resId, null)` -- which builds every Preference
     * in the XML and lets each one's `onSetInitialValue` persist its own default
     * through the correct typed `putXxx` -- then records the flag.
     *
     * ★ CORRECTED (this method previously could not inflate). The old body said
     * "Mac.apk has no preference-XML inflater [...] so the inflate step is
     * satisfied by the EQUIVALENT generic mechanism", meaning
     * com.macapk.loader.PreferenceDefaultsScanner, and it named the residual gap
     * it left: a CUSTOM Preference subclass that overrides onSetInitialValue to
     * persist something other than its declared `android:defaultValue` never had
     * that override run, because the scanner reads XML rather than constructing
     * objects. PreferenceInflater exists now, so the real inflate runs and that
     * gap is closed -- the app's own subclass is instantiated and its own
     * onSetInitialValue executes, exactly as on a device.
     *
     * The scanner has NOT been removed and is not redundant: it runs at
     * Application init, strictly BEFORE any app code could call this, which is
     * what makes a default present for an app that reads SharedPreferences
     * directly and never calls setDefaultValues at all. It is idempotent, so the
     * two mechanisms agreeing is a no-op.
     *
     * ONE narrow fallback, and it is deliberately narrow: if the preference XML
     * cannot be OPENED (Resources.NotFoundException -- a Mac.apk resource-index
     * limitation, not an app bug) the scanner is used instead so the app's
     * defaults still land. Any other InflateException -- an unknown tag, a class
     * with no (Context, AttributeSet) constructor, malformed XML -- PROPAGATES,
     * because those are real app-visible failures and a device would throw them
     * too. Nothing here hides an inflate failure.
     */
    public static void setDefaultValues(Context context, String sharedPreferencesName,
                                        int sharedPreferencesMode, int resId, boolean readAgain) {
        if (context == null) return;
        SharedPreferences defaultValueSp =
                context.getSharedPreferences(KEY_HAS_SET_DEFAULT_VALUES, 0);
        boolean already = defaultValueSp != null
                && defaultValueSp.getBoolean(KEY_HAS_SET_DEFAULT_VALUES, false);
        if (!readAgain && already) return;

        if (resId != 0) {
            PreferenceManager pm = new PreferenceManager(context);
            pm.setSharedPreferencesName(sharedPreferencesName);
            pm.setSharedPreferencesMode(sharedPreferencesMode);
            try {
                pm.inflateFromResource(context, resId, null);
            } catch (android.view.InflateException e) {
                if (!(e.getCause() instanceof Resources.NotFoundException)) throw e;
                runDefaultsScanner(context);
            }
        }

        if (defaultValueSp != null) {
            SharedPreferences.Editor editor =
                    defaultValueSp.edit().putBoolean(KEY_HAS_SET_DEFAULT_VALUES, true);
            try { editor.apply(); } catch (Throwable ignored) {}
        }
    }

    /** The XML-walking default-value writer that predates the inflater. See
     *  setDefaultValues for when and why it is still reachable. */
    private static void runDefaultsScanner(Context context) {
        try {
            Context app = context.getApplicationContext();
            if (app instanceof android.app.Application) {
                com.macapk.loader.PreferenceDefaultsScanner.populate((android.app.Application) app);
            }
        } catch (Throwable ignored) {
            // Defaults may simply already be in place.
        }
    }

    // ---- instance surface -------------------------------------------------

    public Context getContext() { return mContext; }

    public SharedPreferences getSharedPreferences() {
        if (mSharedPreferences == null && mContext != null) {
            mSharedPreferences =
                    mContext.getSharedPreferences(mSharedPreferencesName, mSharedPreferencesMode);
        }
        return mSharedPreferences;
    }

    public String getSharedPreferencesName() { return mSharedPreferencesName; }

    public void setSharedPreferencesName(String sharedPreferencesName) {
        mSharedPreferencesName = sharedPreferencesName;
        mSharedPreferences = null;   // AOSP drops the cached instance
    }

    public int getSharedPreferencesMode() { return mSharedPreferencesMode; }

    public void setSharedPreferencesMode(int sharedPreferencesMode) {
        mSharedPreferencesMode = sharedPreferencesMode;
        mSharedPreferences = null;
    }

    // ---- storage / data store (AOSP public surface) -----------------------

    /** AOSP: the data store that replaces SharedPreferences for every
     *  Preference attached to this manager, or null. */
    public PreferenceDataStore getPreferenceDataStore() { return mPreferenceDataStore; }

    public void setPreferenceDataStore(PreferenceDataStore dataStore) {
        mPreferenceDataStore = dataStore;
    }

    /**
     * AOSP: switch this hierarchy to credential-encrypted storage (the default).
     */
    public void setStorageDefault() {
        mStorage = STORAGE_DEFAULT;
        mSharedPreferences = null;
    }

    /**
     * AOSP: switch this hierarchy to device-protected (direct-boot) storage, by
     * re-opening the SharedPreferences through
     * Context.createDeviceProtectedStorageContext().
     *
     * Mac.apk has ONE storage area: there is no file-based-encryption split and
     * no locked-boot state to be safe from, so the two areas are the same
     * directory. The FLAG is tracked exactly, so isStorageDefault() /
     * isStorageDeviceProtected() answer what the app set -- an app that stores a
     * boot-time preference here reads back what it wrote, which is the whole
     * observable contract on a device that is already unlocked.
     */
    public void setStorageDeviceProtected() {
        mStorage = STORAGE_DEVICE_PROTECTED;
        mSharedPreferences = null;
    }

    public boolean isStorageDefault() { return mStorage == STORAGE_DEFAULT; }

    public boolean isStorageDeviceProtected() { return mStorage == STORAGE_DEVICE_PROTECTED; }

    // ---- ids / request codes / activity dispatch (AOSP package-private) ----

    long getNextId() {
        synchronized (this) { return mNextId++; }
    }

    int getNextRequestCode() {
        synchronized (this) { return mNextRequestCode++; }
    }

    void registerOnActivityResultListener(OnActivityResultListener listener) {
        synchronized (this) {
            if (mActivityResultListeners == null) {
                mActivityResultListeners = new java.util.ArrayList<OnActivityResultListener>();
            }
            if (!mActivityResultListeners.contains(listener)) {
                mActivityResultListeners.add(listener);
            }
        }
    }

    void unregisterOnActivityResultListener(OnActivityResultListener listener) {
        synchronized (this) {
            if (mActivityResultListeners != null) mActivityResultListeners.remove(listener);
        }
    }

    void registerOnActivityStopListener(OnActivityStopListener listener) {
        synchronized (this) {
            if (mActivityStopListeners == null) {
                mActivityStopListeners = new java.util.ArrayList<OnActivityStopListener>();
            }
            if (!mActivityStopListeners.contains(listener)) mActivityStopListeners.add(listener);
        }
    }

    void unregisterOnActivityStopListener(OnActivityStopListener listener) {
        synchronized (this) {
            if (mActivityStopListeners != null) mActivityStopListeners.remove(listener);
        }
    }

    void registerOnActivityDestroyListener(OnActivityDestroyListener listener) {
        synchronized (this) {
            if (mActivityDestroyListeners == null) {
                mActivityDestroyListeners = new java.util.ArrayList<OnActivityDestroyListener>();
            }
            if (!mActivityDestroyListeners.contains(listener)) {
                mActivityDestroyListeners.add(listener);
            }
        }
    }

    void unregisterOnActivityDestroyListener(OnActivityDestroyListener listener) {
        synchronized (this) {
            if (mActivityDestroyListeners != null) mActivityDestroyListeners.remove(listener);
        }
    }

    /** AOSP body: first listener that CONSUMES the result stops the walk. */
    void dispatchActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        java.util.List<OnActivityResultListener> list;
        synchronized (this) {
            if (mActivityResultListeners == null) return;
            list = new java.util.ArrayList<OnActivityResultListener>(mActivityResultListeners);
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).onActivityResult(requestCode, resultCode, data)) break;
        }
    }

    void dispatchActivityStop() {
        java.util.List<OnActivityStopListener> list;
        synchronized (this) {
            if (mActivityStopListeners == null) return;
            list = new java.util.ArrayList<OnActivityStopListener>(mActivityStopListeners);
        }
        for (int i = 0; i < list.size(); i++) list.get(i).onActivityStop();
    }

    void dispatchActivityDestroy() {
        java.util.List<OnActivityDestroyListener> list = null;
        synchronized (this) {
            if (mActivityDestroyListeners != null) {
                list = new java.util.ArrayList<OnActivityDestroyListener>(mActivityDestroyListeners);
            }
        }
        if (list != null) {
            for (int i = 0; i < list.size(); i++) list.get(i).onActivityDestroy();
        }
    }

    // ---- XML inflation (AOSP package-private inflateFromResource) ----------

    /**
     * AOSP: run the PreferenceInflater over `resId` and merge the result into
     * `rootPreferences`, opening a batch so the whole tree's initial values are
     * written in ONE editor commit.
     */
    PreferenceScreen inflateFromResource(Context context, int resId,
                                         PreferenceScreen rootPreferences) {
        setNoCommit(true);
        try {
            PreferenceInflater inflater = new PreferenceInflater(context, this);
            rootPreferences = (PreferenceScreen) inflater.inflate(resId, rootPreferences, true);
        } finally {
            setNoCommit(false);
        }
        if (rootPreferences != null) rootPreferences.onAttachedToHierarchy(this);
        return rootPreferences;
    }

    /** AOSP: same, but the XML comes from an Intent's activity metadata. */
    PreferenceScreen inflateFromIntent(android.content.Intent queryIntent,
                                       PreferenceScreen rootPreferences) {
        // AOSP resolves every activity matching the intent and inflates the
        // `android.preference` metadata XML of each. Mac.apk's PackageManager
        // resolves only the app's own manifest, so this covers the single-app
        // case, which is the only one an APK can rely on anyway (the multi-app
        // case needs other installed packages, which do not exist here).
        if (queryIntent == null || mContext == null) return rootPreferences;
        try {
            android.content.pm.PackageManager pm = mContext.getPackageManager();
            if (pm == null) return rootPreferences;
            java.util.List<android.content.pm.ResolveInfo> matches =
                    pm.queryIntentActivities(queryIntent,
                            android.content.pm.PackageManager.GET_META_DATA);
            if (matches == null) return rootPreferences;
            for (android.content.pm.ResolveInfo info : matches) {
                if (info == null || info.activityInfo == null) continue;
                android.os.Bundle md = info.activityInfo.metaData;
                if (md == null) continue;
                int resId = md.getInt(METADATA_KEY_PREFERENCES, 0);
                if (resId == 0) continue;
                rootPreferences = inflateFromResource(mContext, resId, rootPreferences);
            }
        } catch (Throwable t) {
            // A missing metadata entry is not an error -- AOSP simply finds
            // nothing to inflate. Never let it abort the caller's onCreate.
        }
        return rootPreferences;
    }

    /** AOSP delegates to the root PreferenceScreen's recursive key lookup. */
    public Preference findPreference(CharSequence key) {
        return mPreferenceScreen == null ? null : mPreferenceScreen.findPreference(key);
    }

    public PreferenceScreen createPreferenceScreen(Context context) {
        PreferenceScreen screen = new PreferenceScreen(context);
        screen.onAttachedToHierarchy(this);
        return screen;
    }

    PreferenceScreen getPreferenceScreen() { return mPreferenceScreen; }

    /** AOSP: the metadata key an activity uses to point at its preference XML. */
    public static final String METADATA_KEY_PREFERENCES = "android.preference";

    boolean setPreferences(PreferenceScreen preferenceScreen) {
        if (preferenceScreen == mPreferenceScreen) return false;
        mPreferenceScreen = preferenceScreen;
        return true;
    }

    /** AOSP package-private: an editor for the managed store. During a batch
     *  (mNoCommit) the SAME editor is handed out repeatedly so every write in
     *  the batch lands in one commit. */
    SharedPreferences.Editor getEditor() {
        if (mPreferenceDataStore != null) return null;
        if (mNoCommit) {
            if (mEditor == null) {
                SharedPreferences sp = getSharedPreferences();
                mEditor = sp == null ? null : sp.edit();
            }
            return mEditor;
        }
        SharedPreferences sp = getSharedPreferences();
        return sp == null ? null : sp.edit();
    }

    /** AOSP package-private: false while a batch edit (inflateFromResource,
     *  setDefaultValues, a PreferenceActivity teardown) is in progress, so
     *  individual persist calls do not each hit the store. */
    boolean shouldCommit() { return !mNoCommit; }

    /** AOSP setNoCommit: opening a batch is free; closing one flushes. */
    private void setNoCommit(boolean noCommit) {
        if (!noCommit && mEditor != null) {
            try { mEditor.apply(); } catch (Throwable ignored) {}
            mEditor = null;
        }
        mNoCommit = noCommit;
    }

    // ---- nested listener interfaces (AOSP surface) -----------------------

    /** AOSP: `public interface OnPreferenceTreeClickListener` (@hide, so it is
     *  not in the API signature but IS public in the class file). This is the
     *  hook that makes PreferenceActivity.onPreferenceTreeClick and
     *  PreferenceFragment.onPreferenceTreeClick -- both PUBLIC API that apps
     *  override constantly -- actually fire when a row is tapped. */
    public interface OnPreferenceTreeClickListener {
        boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference);
    }

    private OnPreferenceTreeClickListener mOnPreferenceTreeClickListener;

    void setOnPreferenceTreeClickListener(OnPreferenceTreeClickListener listener) {
        mOnPreferenceTreeClickListener = listener;
    }

    OnPreferenceTreeClickListener getOnPreferenceTreeClickListener() {
        return mOnPreferenceTreeClickListener;
    }

    public interface OnActivityResultListener {
        boolean onActivityResult(int requestCode, int resultCode, android.content.Intent data);
    }
    public interface OnActivityStopListener { void onActivityStop(); }
    /** AOSP's third activity-lifecycle listener; DialogPreference implements it
     *  so a showing dialog is dismissed when the hosting activity goes away. */
    public interface OnActivityDestroyListener { void onActivityDestroy(); }
}
