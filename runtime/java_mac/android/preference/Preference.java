// android.preference.Preference -- the legacy (pre-androidx) base class for a
// single configurable preference entry. Deprecated on real Android since API 29
// but still the settings surface of a large slice of the F-Droid corpus, which
// targets old SDKs on purpose (Anuto TD, Simple Solitaire, Unit Converter
// Ultimate all reference it; measured by debug/preflight.py 2026-07-29).
//
// AOSP reference (mirrored declaration-for-declaration; fetched 2026-07-29):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/
//     core/java/android/preference/Preference.java
//   public class Preference implements Comparable<Preference>
//   public Context getContext()
//   protected void notifyChanged()            protected void notifyHierarchyChanged()
//   protected boolean persistInt(int)         protected int getPersistedInt(int)
//   protected boolean persistBoolean(boolean) protected boolean getPersistedBoolean(boolean)
//   protected boolean persistString(String)   protected String getPersistedString(String)
//   protected boolean persistFloat(float)     protected float getPersistedFloat(float)
//   protected boolean persistLong(long)       protected long getPersistedLong(long)
//   public boolean persistStringSet(Set<String>)
//   public Set<String> getPersistedStringSet(Set<String>)
//   protected boolean shouldPersist()         public boolean shouldDisableDependents()
//   public void notifyDependencyChange(boolean)
//   @CallSuper protected View onCreateView(ViewGroup parent)
//   public View getView(View convertView, ViewGroup parent)
//   @CallSuper protected void onBindView(View view)
//   public SharedPreferences getSharedPreferences()
//   public SharedPreferences.Editor getEditor()
//   public PreferenceManager getPreferenceManager()
//   protected void onAttachedToHierarchy(PreferenceManager)
//   protected boolean callChangeListener(Object)
//   protected void onSetInitialValue(boolean, Object)
//   protected Object onGetDefaultValue(TypedArray, int)
//   @Override public int compareTo(Preference)
//   public static class BaseSavedState extends AbsSavedState
//
// TWO DELIBERATE DIVERGENCES, both documented again at the site:
//
// 1. getSharedPreferences() / shouldPersist() fall back to the DEFAULT store
//    when this Preference is not attached to a hierarchy. AOSP returns null and
//    refuses to persist there, which is technically faithful -- but on AOSP a
//    Preference an app can reach is essentially always attached, because
//    PreferenceActivity / PreferenceFragment inflate it through a
//    PreferenceManager. Mac.apk now HAS that inflater (PreferenceInflater), so
//    an XML-declared hierarchy is attached exactly as on a device -- but a
//    Preference an app builds in CODE and never adds to a group is still
//    unattached, and the faithful null would turn every
//    persistInt()/getPersistedString() into a silent no-op -- the app's settings
//    would appear to save and then not. The fallback store is exactly the one a
//    default-configured PreferenceManager hands back
//    (PreferenceManager.getDefaultSharedPreferences), so the OBSERVABLE result
//    matches AOSP for the default configuration, which is the only one these
//    apps use. Nothing here is keyed off an app identity, so it stays generic.
//
// 2. onCreateView() builds the AOSP `preference` row layout PROGRAMMATICALLY
//    rather than inflating com.android.internal.R.layout.preference, which does
//    not exist in Mac.apk. The child ids are the frozen AOSP public ids
//    (verified against core/res/res/values/public-final.xml, quoted below),
//    because subclasses do
//    `super.onCreateView(parent).findViewById(android.R.id.title)` and that
//    lookup must resolve.
package android.preference;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;

@SuppressWarnings("deprecation")
public class Preference implements Comparable<Preference> {

    /** AOSP: "Specify for {@link #setOrder(int)} if a specific order is not
     *  required." */
    public static final int DEFAULT_ORDER = Integer.MAX_VALUE;

    // Frozen AOSP public resource ids, verified 2026-07-29 against
    // core/res/res/values/public-final.xml (ids in public.xml are immutable by
    // definition -- they are the platform's ABI for resources):
    //   <public type="id" name="icon"         id="0x01020006" />
    //   <public type="id" name="summary"      id="0x01020010" />
    //   <public type="id" name="title"        id="0x01020016" />
    //   <public type="id" name="widget_frame" id="0x01020018" />
    private static final int ID_ICON         = 0x01020006;
    private static final int ID_SUMMARY      = 0x01020010;
    private static final int ID_TITLE        = 0x01020016;
    private static final int ID_WIDGET_FRAME = 0x01020018;

    private Context mContext;
    private PreferenceManager mPreferenceManager;
    private OnPreferenceChangeInternalListener mInternalListener;

    /** AOSP: a per-Preference override of the whole persistence backend. When
     *  set (here or on the PreferenceManager) every persist and
     *  getPersisted call routes through it instead of SharedPreferences. */
    private PreferenceDataStore mPreferenceDataStore;

    /** AOSP mParentGroup, set by PreferenceGroup.addPreference via
     *  assignParent(). Backs the public getParent(). */
    private PreferenceGroup mParentGroup;

    private CharSequence mTitle;
    private CharSequence mSummary;
    private String mKey;
    private Drawable mIcon;
    private Intent mIntent;
    private String mFragment;
    private Bundle mExtras;

    private int mOrder = DEFAULT_ORDER;
    /** AOSP mIconResId: `android:icon` as an id, resolved lazily in
     *  onCreateView/onBindView so a Drawable is not decoded for a row that is
     *  never shown (AOSP does the same). */
    private int mIconResId;
    private int mLayoutResource;
    private int mWidgetLayoutResource;

    private boolean mEnabled = true;
    private boolean mSelectable = true;
    private boolean mPersistent = true;
    private boolean mShouldDisableView = true;

    private String mDependencyKey;
    private Object mDefaultValue;

    /** AOSP mTitleRes: the resource id `android:title` referenced, kept
     *  alongside the resolved text because getTitleRes() is public API and
     *  PreferenceActivity's header machinery passes it around. */
    private int mTitleRes;

    /** AOSP's two-part enabled state. mEnabled is what the app asked for;
     *  mDependencyMet is "my android:dependency preference is not disabling me";
     *  mParentDependencyMet is "my containing group is not disabling me".
     *  isEnabled() is the AND of all three, which is why a dependency can grey a
     *  row out WITHOUT clobbering the app's own setEnabled() value. The old
     *  Mac.apk onParentChanged called setEnabled(!disableChild), which
     *  overwrote it permanently. */
    private boolean mDependencyMet = true;
    private boolean mParentDependencyMet = true;

    /** AOSP: preferences that declared android:dependency pointing AT this one.
     *  Null until the first dependent registers, matching AOSP. */
    private java.util.List<Preference> mDependents;

    private boolean mRecycleEnabled = true;
    private boolean mSingleLineTitle = true;
    private boolean mHasSingleLineTitleAttr;
    private boolean mIconSpaceReserved;

    private OnPreferenceChangeListener mOnChangeListener;
    private OnPreferenceClickListener mOnClickListener;

    /**
     * AOSP reads the Preference styleable here through
     * obtainStyledAttributes(attrs, com.android.internal.R.styleable.Preference,
     * ...). Mac.apk has no com.android.internal.R to key that call with, so the
     * SAME attribute names are read off the AttributeSet by PreferenceAttrs --
     * same names, same defaults, same place in the lifecycle (before the object
     * escapes the constructor, so a subclass that inspects getKey() in its own
     * body sees the real key).
     *
     * This used to be deliberately side-effect-free, with the note that
     * "XML-declared defaults reach the app through
     * com.macapk.loader.PreferenceDefaultsScanner instead". The scanner still
     * runs and is still what guarantees defaults are in the store before
     * Application.onCreate -- but it only writes VALUES; it cannot give the
     * Preference OBJECT its title, summary, entries, dependency or order. With
     * PreferenceInflater now building real hierarchies, those have to land here.
     */
    public Preference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
        if (attrs == null || context == null) return;

        mIconResId = PreferenceAttrs.resId(context, attrs, "icon", 0);
        String key = PreferenceAttrs.raw(attrs, "key");
        if (key != null) mKey = key;

        String rawTitle = PreferenceAttrs.raw(attrs, "title");
        if (rawTitle != null) {
            mTitleRes = PreferenceAttrs.resolveId(context, rawTitle);
            mTitle = PreferenceAttrs.text(context, attrs, "title");
        }
        CharSequence summary = PreferenceAttrs.text(context, attrs, "summary");
        if (summary != null) mSummary = summary;

        mOrder = PreferenceAttrs.integer(context, attrs, "order", mOrder);
        String fragment = PreferenceAttrs.raw(attrs, "fragment");
        if (fragment != null) mFragment = fragment;

        mLayoutResource = PreferenceAttrs.resId(context, attrs, "layout", mLayoutResource);
        mWidgetLayoutResource =
                PreferenceAttrs.resId(context, attrs, "widgetLayout", mWidgetLayoutResource);

        mEnabled = PreferenceAttrs.bool(context, attrs, "enabled", true);
        mSelectable = PreferenceAttrs.bool(context, attrs, "selectable", true);
        mPersistent = PreferenceAttrs.bool(context, attrs, "persistent", mPersistent);

        String dependency = PreferenceAttrs.raw(attrs, "dependency");
        if (dependency != null) mDependencyKey = dependency;

        // AOSP: mDefaultValue = onGetDefaultValue(a, attr). The virtual call is
        // what makes each subclass read its own TYPE (Boolean for a checkbox,
        // Set<String> for a multi-select, String otherwise). We cannot hand it a
        // TypedArray, so the same dispatch happens in onGetDefaultValue(attrs),
        // which every subclass here overrides in lockstep with its
        // onGetDefaultValue(TypedArray, int).
        if (PreferenceAttrs.has(attrs, "defaultValue")) {
            mDefaultValue = onGetDefaultValue(attrs);
        }

        mShouldDisableView =
                PreferenceAttrs.bool(context, attrs, "shouldDisableView", mShouldDisableView);
        mRecycleEnabled = PreferenceAttrs.bool(context, attrs, "recycleEnabled", mRecycleEnabled);
        if (PreferenceAttrs.has(attrs, "singleLineTitle")) {
            mSingleLineTitle =
                    PreferenceAttrs.bool(context, attrs, "singleLineTitle", mSingleLineTitle);
            mHasSingleLineTitleAttr = true;
        }
        mIconSpaceReserved =
                PreferenceAttrs.bool(context, attrs, "iconSpaceReserved", mIconSpaceReserved);
    }

    /** The AttributeSet twin of AOSP's `onGetDefaultValue(TypedArray, int)`.
     *  Same dispatch, same per-subclass typing; see the constructor for why the
     *  TypedArray form cannot be used. The base class reads a plain string,
     *  matching AOSP's Preference.onGetDefaultValue, which returns null -- we
     *  return the string because a Preference with a declared defaultValue and
     *  no subclass override is a plain-string preference in every case that
     *  reaches here. */
    Object onGetDefaultValue(AttributeSet attrs) {
        return PreferenceAttrs.string(mContext, attrs, "defaultValue");
    }

    public Preference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public Preference(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public Preference(Context context) {
        this(context, null, 0, 0);
    }

    /** Not AOSP. Retained because the previous Mac.apk stub had it and app code
     *  may reflectively instantiate through it; removing it would be a
     *  gratuitous regression. */
    public Preference() {
        this(null, null, 0, 0);
    }

    public Context getContext() { return mContext; }

    // ---- title / summary / key / icon -------------------------------------

    public CharSequence getTitle() { return mTitle; }
    public void setTitle(CharSequence title) {
        if (title == null ? mTitle != null : !title.equals(mTitle)) {
            mTitle = title;
            notifyChanged();
        }
    }
    public void setTitle(int titleResId) {
        setTitle(getStringRes(titleResId));
        mTitleRes = titleResId;
    }

    /** AOSP: the `android:title` resource id, or 0 when the title was set as a
     *  literal CharSequence. PreferenceActivity's header machinery and app code
     *  that re-localizes a hierarchy both read it. */
    public int getTitleRes() { return mTitleRes; }

    public CharSequence getSummary() { return mSummary; }
    public void setSummary(CharSequence summary) {
        if (summary == null ? mSummary != null : !summary.equals(mSummary)) {
            mSummary = summary;
            notifyChanged();
        }
    }
    public void setSummary(int summaryResId) { setSummary(getStringRes(summaryResId)); }

    public String getKey() { return mKey; }
    public void setKey(String key) { mKey = key; }
    public boolean hasKey() { return mKey != null && mKey.length() > 0; }

    public Drawable getIcon() {
        if (mIcon == null && mIconResId != 0) {
            // AOSP resolves the id lazily on first read for the same reason:
            // decoding a drawable for a row nobody scrolled to is wasted work.
            setIcon(mIconResId);
        }
        return mIcon;
    }
    public void setIcon(Drawable icon) {
        if (mIcon != icon) { mIcon = icon; notifyChanged(); }
    }
    public void setIcon(int iconResId) {
        if (mContext == null || iconResId == 0) return;
        try {
            android.content.res.Resources r = mContext.getResources();
            if (r != null) setIcon(r.getDrawable(iconResId));
        } catch (Throwable ignored) {}
    }

    private CharSequence getStringRes(int resId) {
        if (mContext == null || resId == 0) return null;
        try { return mContext.getString(resId); } catch (Throwable t) { return null; }
    }

    // ---- ordering / layout ------------------------------------------------

    public int getOrder() { return mOrder; }
    public void setOrder(int order) {
        if (order != mOrder) { mOrder = order; notifyHierarchyChanged(); }
    }

    public int getLayoutResource() { return mLayoutResource; }
    public void setLayoutResource(int layoutResId) { mLayoutResource = layoutResId; }

    public int getWidgetLayoutResource() { return mWidgetLayoutResource; }
    public void setWidgetLayoutResource(int widgetLayoutResId) {
        mWidgetLayoutResource = widgetLayoutResId;
    }

    // ---- state ------------------------------------------------------------

    /** AOSP body: the AND of the app's own flag and the two dependency gates.
     *  See the mDependencyMet field comment for why this is not
     *  `setEnabled(false)` on the dependent. */
    public boolean isEnabled() {
        return mEnabled && mDependencyMet && mParentDependencyMet;
    }
    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            notifyDependencyChange(shouldDisableDependents());
            notifyChanged();
        }
    }

    public boolean isSelectable() { return mSelectable; }
    public void setSelectable(boolean selectable) {
        if (mSelectable != selectable) { mSelectable = selectable; notifyChanged(); }
    }

    public boolean isPersistent() { return mPersistent; }
    public void setPersistent(boolean persistent) { mPersistent = persistent; }

    /** AOSP: whether the row's View may be recycled by the list adapter. An app
     *  turns it OFF for a Preference whose view holds per-row state (a running
     *  animation, a live SeekBar) that a recycled convertView would carry over. */
    public boolean isRecycleEnabled() { return mRecycleEnabled; }
    public void setRecycleEnabled(boolean enabled) {
        mRecycleEnabled = enabled;
        notifyChanged();
    }

    /** AOSP: whether the title TextView is constrained to a single line.
     *  Defaults true, and the "was it declared" flag exists because
     *  PreferenceGroupAdapter only applies the value when the app actually asked
     *  (AOSP: mHasSingleLineTitleAttr). */
    public boolean isSingleLineTitle() { return mSingleLineTitle; }
    public void setSingleLineTitle(boolean singleLineTitle) {
        mHasSingleLineTitleAttr = true;
        mSingleLineTitle = singleLineTitle;
        notifyChanged();
    }
    /** AOSP package-private mHasSingleLineTitleAttr, read by the adapter. */
    boolean hasSingleLineTitleAttr() { return mHasSingleLineTitleAttr; }

    /** AOSP: reserve the icon gutter even when this row has no icon, so a mixed
     *  list keeps its titles aligned. */
    public boolean isIconSpaceReserved() { return mIconSpaceReserved; }
    public void setIconSpaceReserved(boolean iconSpaceReserved) {
        mIconSpaceReserved = iconSpaceReserved;
        notifyChanged();
    }

    public boolean getShouldDisableView() { return mShouldDisableView; }
    public void setShouldDisableView(boolean shouldDisableView) {
        mShouldDisableView = shouldDisableView;
        notifyChanged();
    }

    public Object getDefaultValue() { return mDefaultValue; }
    public void setDefaultValue(Object defaultValue) { mDefaultValue = defaultValue; }

    public Intent getIntent() { return mIntent; }
    public void setIntent(Intent intent) { mIntent = intent; }

    public String getFragment() { return mFragment; }
    public void setFragment(String fragment) { mFragment = fragment; }

    public Bundle getExtras() {
        if (mExtras == null) mExtras = new Bundle();
        return mExtras;
    }
    public Bundle peekExtras() { return mExtras; }

    // ---- dependencies -----------------------------------------------------

    public String getDependency() { return mDependencyKey; }

    /** AOSP body: swap the registration, do not just store the key. Registering
     *  is what makes `android:dependency="use_proxy"` actually grey out the
     *  three proxy fields when the checkbox is cleared. */
    public void setDependency(String dependencyKey) {
        unregisterDependency();
        mDependencyKey = dependencyKey;
        registerDependency();
    }

    /** AOSP: "Finds a Preference in this hierarchy (the whole thing, even if
     *  this preference is not a descendant of the root) with the given key."
     *  Public API (protected). */
    protected Preference findPreferenceInHierarchy(String key) {
        if (key == null || key.length() == 0 || mPreferenceManager == null) return null;
        return mPreferenceManager.findPreference(key);
    }

    /** AOSP body. Note it THROWS on a dangling android:dependency, and that is
     *  deliberate upstream -- a settings screen wired to a key that does not
     *  exist is an app bug, and silently ignoring it produces a screen whose
     *  rows never grey out with nothing to point at. Mirrored, including the
     *  message text. */
    private void registerDependency() {
        if (mDependencyKey == null || mDependencyKey.length() == 0) return;

        Preference preference = findPreferenceInHierarchy(mDependencyKey);
        if (preference != null) {
            preference.registerDependent(this);
        } else {
            throw new IllegalStateException("Dependency \"" + mDependencyKey
                    + "\" not found for preference \"" + mKey + "\" (title: \"" + mTitle + "\"");
        }
    }

    private void unregisterDependency() {
        if (mDependencyKey != null) {
            final Preference oldDependency = findPreferenceInHierarchy(mDependencyKey);
            if (oldDependency != null) {
                oldDependency.unregisterDependent(this);
            }
        }
    }

    private void registerDependent(Preference dependent) {
        if (mDependents == null) {
            mDependents = new java.util.ArrayList<Preference>();
        }
        mDependents.add(dependent);
        dependent.onDependencyChanged(this, shouldDisableDependents());
    }

    private void unregisterDependent(Preference dependent) {
        if (mDependents != null) mDependents.remove(dependent);
    }

    /** AOSP: "Checks whether this preference's dependents should currently be
     *  disabled." The base class disables dependents when it is itself
     *  disabled; TwoStatePreference overrides this on the checked state. */
    public boolean shouldDisableDependents() { return !isEnabled(); }

    /** AOSP body: walk the registered dependents. This used to be an empty
     *  method with a note that "Mac.apk has no preference-XML inflater building
     *  the dependency graph, so nothing is registered to walk". There is one now
     *  (PreferenceInflater), and setDependency() registers even for a hierarchy
     *  built in code, so the walk is real. */
    public void notifyDependencyChange(boolean disableDependents) {
        final java.util.List<Preference> dependents = mDependents;
        if (dependents == null) return;

        final int dependentsCount = dependents.size();
        for (int i = 0; i < dependentsCount; i++) {
            dependents.get(i).onDependencyChanged(this, disableDependents);
        }
    }

    /** AOSP body, verbatim. Public API. */
    public void onDependencyChanged(Preference dependency, boolean disableDependent) {
        if (mDependencyMet == disableDependent) {
            mDependencyMet = !disableDependent;

            // Enabled state can change dependent preferences' states, so notify
            notifyDependencyChange(shouldDisableDependents());

            notifyChanged();
        }
    }

    /** AOSP body, verbatim, and PUBLIC (the old Mac.apk copy was package-private
     *  and called setEnabled(!disableChild), which permanently overwrote the
     *  app's own enabled flag instead of toggling the separate parent gate). */
    public void onParentChanged(Preference parent, boolean disableChild) {
        if (mParentDependencyMet == disableChild) {
            mParentDependencyMet = !disableChild;

            // Enabled state can change dependent preferences' states, so notify
            notifyDependencyChange(shouldDisableDependents());

            notifyChanged();
        }
    }

    /** AOSP: the PreferenceGroup that contains this preference, or null. Set by
     *  PreferenceGroup.addPreference / cleared by removePreference. */
    public PreferenceGroup getParent() { return mParentGroup; }

    /** AOSP package-private setter for the above. */
    void assignParent(PreferenceGroup parentGroup) { mParentGroup = parentGroup; }

    // ---- change notification ---------------------------------------------

    protected void notifyChanged() {
        if (mInternalListener != null) mInternalListener.onPreferenceChange(this);
    }

    protected void notifyHierarchyChanged() {
        if (mInternalListener != null) mInternalListener.onPreferenceHierarchyChange(this);
    }

    final void setOnPreferenceChangeInternalListener(OnPreferenceChangeInternalListener l) {
        mInternalListener = l;
    }

    public OnPreferenceChangeListener getOnPreferenceChangeListener() { return mOnChangeListener; }
    public void setOnPreferenceChangeListener(OnPreferenceChangeListener onPreferenceChangeListener) {
        mOnChangeListener = onPreferenceChangeListener;
    }
    public OnPreferenceClickListener getOnPreferenceClickListener() { return mOnClickListener; }
    public void setOnPreferenceClickListener(OnPreferenceClickListener onPreferenceClickListener) {
        mOnClickListener = onPreferenceClickListener;
    }

    /** AOSP: returns false if the change was REJECTED by the listener. */
    protected boolean callChangeListener(Object newValue) {
        return mOnChangeListener == null || mOnChangeListener.onPreferenceChange(this, newValue);
    }

    protected void onClick() {}

    /**
     * AOSP `performClick(PreferenceScreen)`, body-for-body.
     *
     * The middle stanza is the load-bearing one and it used to be missing: the
     * hosting PreferenceActivity / PreferenceFragment registers itself as the
     * manager's OnPreferenceTreeClickListener, and this is the ONLY path by
     * which their public `onPreferenceTreeClick(PreferenceScreen, Preference)`
     * override is ever called. Without it an app can override that method, see
     * it compile, and never see it run.
     *
     * Order matters and is AOSP's: the app's own OnPreferenceClickListener wins,
     * then the hosting screen, then the declared android:intent.
     */
    public void performClick(PreferenceScreen preferenceScreen) {
        if (!isEnabled()) return;

        onClick();

        if (mOnClickListener != null && mOnClickListener.onPreferenceClick(this)) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        if (preferenceManager != null) {
            PreferenceManager.OnPreferenceTreeClickListener listener =
                    preferenceManager.getOnPreferenceTreeClickListener();
            if (preferenceScreen != null && listener != null
                    && listener.onPreferenceTreeClick(preferenceScreen, this)) {
                return;
            }
        }

        if (mIntent != null && mContext != null) {
            try {
                mContext.startActivity(mIntent);
            } catch (Throwable t) {
                // AOSP lets ActivityNotFoundException escape. Here the caller is
                // the list adapter's item-click path, where an escaping throwable
                // tears down the whole settings screen; Mac.apk's Intent routing
                // resolves fewer implicit intents than a device, so this would
                // fire for reasons that are ours and not the app's.
            }
        }
    }

    // ---- hierarchy attachment --------------------------------------------

    public PreferenceManager getPreferenceManager() { return mPreferenceManager; }

    /** AOSP package-private mId, handed out by PreferenceManager.getNextId() at
     *  attach time. PreferenceGroupAdapter reports it as the ListView's stable
     *  row id, so it must be unique within one hierarchy and stable across a
     *  re-sync (which is why it is assigned once, at attach). */
    private long mId;
    long getId() { return mId; }

    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        mPreferenceManager = preferenceManager;
        if (preferenceManager != null) mId = preferenceManager.getNextId();
        dispatchSetInitialValue();
    }

    /** AOSP body, with its own comment: "At this point, the hierarchy that this
     *  preference is in is connected with all other preferences." That is
     *  precisely why the dependency is registered HERE and not in
     *  onAttachedToHierarchy -- during inflation a child is attached the moment
     *  it is added, so the preference its android:dependency names may not exist
     *  yet and registerDependency() would throw its not-found IllegalStateException
     *  on a perfectly valid XML. (Measured: it did, on the first end-to-end run.)
     *  PreferenceScreen.bind() drives this after the whole tree is built. */
    protected void onAttachedToActivity() {
        registerDependency();
    }
    protected void onPrepareForRemoval() {}

    /** AOSP body. The data store branch comes first because a store has no
     *  contains() to consult -- AOSP passes restorePersistedValue=true and lets
     *  the subclass read through the store, whose getters return the supplied
     *  default when the key is absent. */
    private void dispatchSetInitialValue() {
        if (getPreferenceDataStore() != null) {
            onSetInitialValue(true, mDefaultValue);
            return;
        }
        SharedPreferences sp = getSharedPreferences();
        if (sp == null) {
            onSetInitialValue(false, mDefaultValue);
            return;
        }
        final boolean shouldPersist = shouldPersist();
        if (!shouldPersist || !sp.contains(mKey)) {
            if (mDefaultValue != null) onSetInitialValue(false, mDefaultValue);
        } else {
            onSetInitialValue(true, null);
        }
    }

    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {}

    protected Object onGetDefaultValue(TypedArray a, int index) { return null; }

    // ---- persistence ------------------------------------------------------

    /** AOSP returns mPreferenceManager.getSharedPreferences(), or null when
     *  unattached. See divergence (1) in the file header for why Mac.apk falls
     *  back to the default store instead of returning null. */
    public SharedPreferences getSharedPreferences() {
        if (mPreferenceManager != null) {
            SharedPreferences sp = mPreferenceManager.getSharedPreferences();
            if (sp != null) return sp;
        }
        if (mContext == null) return null;
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    /** AOSP: this Preference's own data store, else the manager's, else null
     *  (meaning "use SharedPreferences"). */
    public PreferenceDataStore getPreferenceDataStore() {
        if (mPreferenceDataStore != null) return mPreferenceDataStore;
        if (mPreferenceManager != null) return mPreferenceManager.getPreferenceDataStore();
        return null;
    }

    /** AOSP: replace SharedPreferences for THIS preference only. */
    public void setPreferenceDataStore(PreferenceDataStore dataStore) {
        mPreferenceDataStore = dataStore;
    }

    public SharedPreferences.Editor getEditor() {
        SharedPreferences sp = getSharedPreferences();
        return sp == null ? null : sp.edit();
    }

    /** AOSP: "Should this Preference store its value into the
     *  SharedPreferences?" -- persistent AND keyed AND attached to a manager.
     *  Mac.apk drops the manager requirement for the reason in divergence (1). */
    protected boolean shouldPersist() {
        if (!mPersistent || !hasKey()) return false;
        // A data store IS a persistence backend, so its presence satisfies this
        // regardless of whether a SharedPreferences file could be opened.
        return getPreferenceDataStore() != null || getSharedPreferences() != null;
    }

    /** AOSP: public, and false when unattached (a write with no manager cannot
     *  participate in the manager's batch). Mac.apk answers true when
     *  unattached for the same reason getSharedPreferences() falls back -- see
     *  divergence (1) in the file header; an unattached Preference must write
     *  through or its value is silently lost. */
    public boolean shouldCommit() {
        return mPreferenceManager == null || mPreferenceManager.shouldCommit();
    }

    protected boolean persistString(String value) {
        if (!shouldPersist()) return false;
        if (value != null && value.equals(getPersistedString(null))) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putString(mKey, value);
            return true;
        }
        SharedPreferences.Editor editor = getEditor();
        if (editor == null) return false;
        editor.putString(mKey, value);
        tryCommit(editor);
        return true;
    }

    protected String getPersistedString(String defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getString(mKey, defaultReturnValue);
        SharedPreferences sp = getSharedPreferences();
        return sp == null ? defaultReturnValue : sp.getString(mKey, defaultReturnValue);
    }

    public boolean persistStringSet(Set<String> values) {
        if (!shouldPersist()) return false;
        if (values != null && values.equals(getPersistedStringSet(null))) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putStringSet(mKey, values);
            return true;
        }
        SharedPreferences.Editor editor = getEditor();
        if (editor == null) return false;
        editor.putStringSet(mKey, values);
        tryCommit(editor);
        return true;
    }

    public Set<String> getPersistedStringSet(Set<String> defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getStringSet(mKey, defaultReturnValue);
        SharedPreferences sp = getSharedPreferences();
        return sp == null ? defaultReturnValue : sp.getStringSet(mKey, defaultReturnValue);
    }

    protected boolean persistInt(int value) {
        if (!shouldPersist()) return false;
        if (value == getPersistedInt(~value)) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putInt(mKey, value);
            return true;
        }
        SharedPreferences.Editor editor = getEditor();
        if (editor == null) return false;
        editor.putInt(mKey, value);
        tryCommit(editor);
        return true;
    }

    protected int getPersistedInt(int defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getInt(mKey, defaultReturnValue);
        SharedPreferences sp = getSharedPreferences();
        return sp == null ? defaultReturnValue : sp.getInt(mKey, defaultReturnValue);
    }

    protected boolean persistFloat(float value) {
        if (!shouldPersist()) return false;
        if (value == getPersistedFloat(Float.NaN)) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putFloat(mKey, value);
            return true;
        }
        SharedPreferences.Editor editor = getEditor();
        if (editor == null) return false;
        editor.putFloat(mKey, value);
        tryCommit(editor);
        return true;
    }

    protected float getPersistedFloat(float defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getFloat(mKey, defaultReturnValue);
        SharedPreferences sp = getSharedPreferences();
        return sp == null ? defaultReturnValue : sp.getFloat(mKey, defaultReturnValue);
    }

    protected boolean persistLong(long value) {
        if (!shouldPersist()) return false;
        if (value == getPersistedLong(~value)) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putLong(mKey, value);
            return true;
        }
        SharedPreferences.Editor editor = getEditor();
        if (editor == null) return false;
        editor.putLong(mKey, value);
        tryCommit(editor);
        return true;
    }

    protected long getPersistedLong(long defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getLong(mKey, defaultReturnValue);
        SharedPreferences sp = getSharedPreferences();
        return sp == null ? defaultReturnValue : sp.getLong(mKey, defaultReturnValue);
    }

    protected boolean persistBoolean(boolean value) {
        if (!shouldPersist()) return false;
        if (value == getPersistedBoolean(!value)) return true;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) {
            dataStore.putBoolean(mKey, value);
            return true;
        }
        SharedPreferences.Editor editor = getEditor();
        if (editor == null) return false;
        editor.putBoolean(mKey, value);
        tryCommit(editor);
        return true;
    }

    protected boolean getPersistedBoolean(boolean defaultReturnValue) {
        if (!shouldPersist()) return defaultReturnValue;
        PreferenceDataStore dataStore = getPreferenceDataStore();
        if (dataStore != null) return dataStore.getBoolean(mKey, defaultReturnValue);
        SharedPreferences sp = getSharedPreferences();
        return sp == null ? defaultReturnValue : sp.getBoolean(mKey, defaultReturnValue);
    }

    /** AOSP routes the write through PreferenceManager.shouldCommit() so a
     *  batch edit (setDefaultValues, PreferenceActivity teardown) can defer it.
     *  Mirrored when a manager is attached; an unmanaged Preference applies
     *  immediately, which is what it must do to persist at all. */
    private void tryCommit(SharedPreferences.Editor editor) {
        if (mPreferenceManager == null || mPreferenceManager.shouldCommit()) {
            try { editor.apply(); } catch (Throwable ignored) {}
        }
    }

    // ---- view creation ----------------------------------------------------

    /** AOSP inflates mLayoutResource (default
     *  com.android.internal.R.layout.preference) and returns the root. Mac.apk
     *  has no internal layout resources, so this builds the same SHAPE
     *  programmatically -- a horizontal LinearLayout holding
     *  icon / (title over summary) / widget_frame -- carrying the frozen AOSP
     *  public ids so a subclass's findViewById(android.R.id.title) resolves.
     *  When the app supplied its OWN layout via setLayoutResource() that is
     *  inflated instead: an app-provided layout is a real resource we can read. */
    protected View onCreateView(ViewGroup parent) {
        if (mContext == null) return null;

        if (mLayoutResource != 0) {
            View v = inflateOrNull(mLayoutResource, parent);
            if (v != null) return v;
        }

        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);

        ImageView icon = new ImageView(mContext);
        icon.setId(ID_ICON);
        if (mIcon != null) icon.setImageDrawable(mIcon);
        row.addView(icon);

        LinearLayout text = new LinearLayout(mContext);
        text.setOrientation(LinearLayout.VERTICAL);
        // AOSP's default row is com.android.internal.R.layout.preference_material
        // (android14-release core/res/res/layout/preference_material.xml): the
        // title element declares
        //     android:textAppearance="?attr/textAppearanceListItem"
        // and the summary element
        //     android:textAppearance="?attr/textAppearanceListItemSecondary"
        //     android:textColor="?attr/textColorSecondary"
        // The classic row (layout/preference.xml: ?textAppearanceLarge title,
        // ?textAppearanceSmall + ?textColorSecondary summary) resolves to the
        // same values, because the base Theme aliases textAppearanceListItem /
        // ...Secondary to exactly those (themes.xml :133/:135). KNOWN RESIDUAL:
        // a Holo theme's preference_holo.xml titles with ?textAppearanceMedium
        // (18sp where this gives 22sp).
        // Our LayoutInflater cannot read a compiled platform layout yet, so each
        // TextView is built through its (Context, AttributeSet) constructor with
        // exactly those element attributes and the constructor resolves them
        // over the same tiers a device does. A bare `new TextView(mContext)`
        // takes ?android:textViewStyle's appearance instead (the textColor-
        // Tertiary / textColorSecondary grey at 14sp), which is right for an
        // app's own programmatic TextView and wrong for this row: measured on
        // tobsolitaire, unitconv, bouncy, anuto and frozenbubble, the title drew
        // in the summary grey. MACAPK_PREF_ROW_APPEARANCE=0 restores the bare
        // constructors.
        TextView title = new TextView(mContext, MACAPK_PREF_ROW_APPEARANCE
            ? macapkElementAttrs("android:textAppearance=\"?android:attr/textAppearanceListItem\"")
            : null);
        title.setId(ID_TITLE);
        if (mTitle != null) title.setText(mTitle);
        text.addView(title);
        TextView summary = new TextView(mContext, MACAPK_PREF_ROW_APPEARANCE
            ? macapkElementAttrs("android:textAppearance=\"?android:attr/textAppearanceListItemSecondary\""
                + " android:textColor=\"?android:attr/textColorSecondary\"")
            : null);
        summary.setId(ID_SUMMARY);
        if (mSummary != null) summary.setText(mSummary);
        text.addView(summary);
        row.addView(text);

        FrameLayout widgetFrame = new FrameLayout(mContext);
        widgetFrame.setId(ID_WIDGET_FRAME);
        if (mWidgetLayoutResource != 0) inflateInto(mWidgetLayoutResource, widgetFrame);
        row.addView(widgetFrame);

        return row;
    }

    static final boolean MACAPK_PREF_ROW_APPEARANCE =
        !"0".equals(System.getenv("MACAPK_PREF_ROW_APPEARANCE"));

    /** An AttributeSet holding one layout element's android: attributes, so a
     *  stand-in for a platform layout we cannot inflate hands its widget the
     *  same constructor input the layout would. Null (the bare constructor) if
     *  the parser is unavailable. */
    static AttributeSet macapkElementAttrs(String androidAttrs) {
        try {
            org.xmlpull.v1.XmlPullParser p = android.util.Xml.newPullParser();
            p.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            p.setInput(new java.io.StringReader("<TextView xmlns:android="
                + "\"http://schemas.android.com/apk/res/android\" " + androidAttrs + "/>"));
            while (p.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
            return android.util.Xml.asAttributeSet(p);
        } catch (Throwable t) {
            return null;
        }
    }

    private View inflateOrNull(int layoutRes, ViewGroup parent) {
        try {
            android.view.LayoutInflater li = android.view.LayoutInflater.from(mContext);
            return li == null ? null : li.inflate(layoutRes, parent, false);
        } catch (Throwable t) {
            return null;   // fall through to the programmatic layout
        }
    }

    private void inflateInto(int layoutRes, ViewGroup into) {
        try {
            android.view.LayoutInflater li = android.view.LayoutInflater.from(mContext);
            if (li != null) li.inflate(layoutRes, into, true);
        } catch (Throwable ignored) {}
    }

    public View getView(View convertView, ViewGroup parent) {
        if (convertView == null) convertView = onCreateView(parent);
        onBindView(convertView);
        return convertView;
    }

    /** AOSP pushes title / summary / icon / enabled into the view returned by
     *  onCreateView, using the same public ids. */
    protected void onBindView(View view) {
        if (view == null) return;
        try {
            View t = view.findViewById(ID_TITLE);
            if (t instanceof TextView) {
                TextView tv = (TextView) t;
                if (mTitle != null) tv.setText(mTitle);
                tv.setEnabled(isEnabled());
                // AOSP applies this from PreferenceGroupAdapter, and ONLY when
                // the app declared the attribute -- otherwise the row layout's
                // own singleLine setting wins.
                if (mHasSingleLineTitleAttr) tv.setSingleLine(mSingleLineTitle);
            }
            View s = view.findViewById(ID_SUMMARY);
            if (s instanceof TextView) {
                TextView sv = (TextView) s;
                if (mSummary != null) sv.setText(mSummary);
                // AOSP hides the summary view when there is no summary text.
                sv.setVisibility(mSummary == null ? View.GONE : View.VISIBLE);
                sv.setEnabled(isEnabled());
            }
            View i = view.findViewById(ID_ICON);
            if (i instanceof ImageView) {
                Drawable icon = getIcon();          // resolves mIconResId lazily
                if (icon != null) {
                    ((ImageView) i).setImageDrawable(icon);
                    i.setVisibility(View.VISIBLE);
                } else {
                    // AOSP: an icon-less row hides the ImageView unless the app
                    // asked for the gutter to stay reserved so titles line up.
                    i.setVisibility(mIconSpaceReserved ? View.INVISIBLE : View.GONE);
                }
            }
            if (mShouldDisableView) view.setEnabled(isEnabled());
        } catch (Throwable ignored) {
            // A subclass may hand us a view without these children. AOSP would
            // NPE here; there is no upside to propagating that.
        }
    }

    // ---- instance state ---------------------------------------------------

    public void saveHierarchyState(Bundle container) { dispatchSaveInstanceState(container); }

    protected void dispatchSaveInstanceState(Bundle container) {
        if (container == null || !hasKey()) return;
        Parcelable state = onSaveInstanceState();
        if (state != null) container.putParcelable(mKey, state);
    }

    protected Parcelable onSaveInstanceState() { return android.view.AbsSavedState.EMPTY_STATE; }

    public void restoreHierarchyState(Bundle container) { dispatchRestoreInstanceState(container); }

    protected void dispatchRestoreInstanceState(Bundle container) {
        if (container == null || !hasKey()) return;
        Parcelable state = container.getParcelable(mKey);
        if (state != null) onRestoreInstanceState(state);
    }

    protected void onRestoreInstanceState(Parcelable state) {}

    // ---- ordering ---------------------------------------------------------

    /** AOSP: compare by order, then by title, case-insensitively. */
    @Override
    public int compareTo(Preference another) {
        if (another == null) return -1;
        if (mOrder != another.mOrder) return mOrder - another.mOrder;
        if (mTitle == another.mTitle) return 0;
        if (mTitle == null) return 1;
        if (another.mTitle == null) return -1;
        return mTitle.toString().compareToIgnoreCase(another.mTitle.toString());
    }

    @Override
    public String toString() {
        CharSequence t = getTitle();
        return t == null ? super.toString() : t.toString();
    }

    // ---- nested types (AOSP surface) -------------------------------------

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }

    /** AOSP package-private hierarchy-change callback: how a PreferenceGroup
     *  learns that a child changed. */
    interface OnPreferenceChangeInternalListener {
        void onPreferenceChange(Preference preference);
        void onPreferenceHierarchyChange(Preference preference);
    }

    public static class BaseSavedState extends android.view.AbsSavedState {
        public BaseSavedState(Parcel source) { super(source); }
        public BaseSavedState(Parcelable superState) { super(superState); }

        public static final Parcelable.Creator<BaseSavedState> CREATOR =
                new Parcelable.Creator<BaseSavedState>() {
                    public BaseSavedState createFromParcel(Parcel in) { return new BaseSavedState(in); }
                    public BaseSavedState[] newArray(int size) { return new BaseSavedState[size]; }
                };
    }
}
