// android.app.Fragment -- AOSP's pre-androidx Fragment class (API 11+,
// deprecated in API 28 but still referenced by ComponentActivity for
// backward compatibility). androidx.fragment.app.Fragment is the
// modern replacement; F-Droid + many AppCompat-based apps depend on
// android.app.Fragment existing as a class purely so the linker
// doesn't NoClassDefFoundError at boot — they ALWAYS use the androidx
// version at runtime.
//
// We provide a minimal abstract surface: lifecycle methods, view
// access, argument bundle. No real fragment-manager dispatch (the
// androidx FragmentManager runs the show; this class is only here
// for legacy linkage).
package android.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.transition.Transition;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

// AOSP: `public class Fragment implements ComponentCallbacks2,
// OnCreateContextMenuListener`
// (frameworks/base/core/java/android/app/Fragment.java:266). We declared only
// ComponentCallbacks, so a fragment was not assignable to either of the two
// types AOSP guarantees. Inherited by DialogFragment, ListFragment,
// PreferenceFragment and WebViewFragment.
public class Fragment implements android.content.ComponentCallbacks2,
        android.view.View.OnCreateContextMenuListener {
    private Bundle    arguments;
    /** AOSP's Fragment.mView (core/java/android/app/Fragment.java). Package-
     *  private, not this file's business alone: FragmentManagerImpl OWNS the
     *  view's creation/attachment/teardown (moveToState), exactly as AOSP's
     *  FragmentManagerImpl does at FragmentManager.java:1303-1320 and
     *  :1380-1427. */
    View              mView;
    private boolean   added;
    private boolean   detached;
    /** AOSP Fragment.mHidden / mHiddenChanged. Owned by FragmentManagerImpl's
     *  hideFragment/showFragment, which only FLAG the change; the manager's
     *  completeShowHideFragment is what applies View.GONE/VISIBLE (AOSP
     *  FragmentManager.java:1762-1786 + :1512-1560). Package-private for the
     *  same reason mView is: the manager, not the fragment, owns this. */
    boolean           mHidden;
    boolean           mHiddenChanged;
    private boolean   removing;
    private boolean   resumed;
    private boolean   userVisibleHint = true;
    private String    tag;
    protected Activity activity;
    private Bundle    savedInstanceState;
    private Fragment  parentFragment;
    private android.app.FragmentManager fragmentManager;

    // ---- AOSP from-layout state (core/java/android/app/Fragment.java:316-360) ----
    // These five fields are what make a `<fragment>` tag in a layout work, and
    // they are set by FragmentManagerImpl.onCreateView exactly as AOSP's
    // FragmentManager.java:3554-3559 sets them:
    //     fragment.mFromLayout = true;
    //     fragment.mFragmentId = id != 0 ? id : containerId;
    //     fragment.mContainerId = containerId;
    //     fragment.mTag = tag;
    //     fragment.mInLayout = true;
    // "From layout" is load-bearing and NOT cosmetic: AOSP's moveToState treats
    // a from-layout fragment differently from a programmatically-added one --
    // the from-layout one creates its view with a NULL container and is never
    // added to one (FragmentManager.java:1489-1500 ensureInflatedFragmentView),
    // because the LayoutInflater itself places the returned view in the tree.
    // A programmatically-added fragment resolves its container by id and IS
    // added to it (FragmentManager.java:1278-1309).
    boolean mFromLayout;
    boolean mInLayout;
    /** AOSP Fragment.java:326 -- "True if performCreateView has been called and
     *  a matching call to performDestroyView has not yet happened." Guards
     *  ensureInflatedFragmentView against creating the view twice, which matters
     *  because AOSP deliberately runs that method on EVERY arrival at CREATED. */
    boolean mPerformedCreateView;
    int     mFragmentId;
    int     mContainerId;
    /** The ViewGroup this fragment's view was added to, or null for a from-layout
     *  fragment. Needed on the way down: AOSP FragmentManager.java:1424 does
     *  `f.mContainer.removeView(f.mView)` before nulling both. */
    ViewGroup mContainer;

    public Fragment() {}

    /** AOSP factory that constructs by class name + sets args. */
    public static Fragment instantiate(Context context, String fname) {
        return instantiate(context, fname, null);
    }
    public static Fragment instantiate(Context context, String fname, Bundle args) {
        try {
            Class<?> cls = context.getClassLoader().loadClass(fname);
            Fragment f = (Fragment) cls.getDeclaredConstructor().newInstance();
            if (args != null) f.setArguments(args);
            return f;
        } catch (Throwable t) {
            throw new InstantiationException("Unable to instantiate fragment " + fname + ": " + t, t);
        }
    }

    // AOSP core/java/android/app/Fragment.java (android13-release) :485-489
    // extends android.util.AndroidRuntimeException, not plain RuntimeException.
    // We previously had the wrong superclass: real Android code that does
    // `catch (AndroidRuntimeException e)` around fragment instantiation (a
    // real, documented AOSP idiom -- AndroidRuntimeException is the base for
    // "unchecked exceptions thrown by the Android frameworks") would NOT catch
    // ours, since plain RuntimeException is a supertype of AndroidRuntimeException,
    // not a subtype -- the exception would propagate uncaught instead of being
    // handled the way the app expects.
    //
    // DEVIATION, deliberate: AOSP's constructor takes (String, Exception); ours
    // keeps (String, Throwable) because instantiate() below deliberately catches
    // the broader Throwable (including Error subclasses like
    // ExceptionInInitializerError/NoClassDefFoundError, which surface often in
    // this framework's class-loading path) and wraps it the same way AOSP wraps
    // its narrower checked-exception list. Narrowing to Exception here would
    // silently stop wrapping those Errors, which is a real behavior regression
    // this framework relies on -- do not "fix" this back to Exception without
    // also auditing every Throwable that instantiate() can see.
    public static class InstantiationException extends android.util.AndroidRuntimeException {
        public InstantiationException(String msg, Throwable cause) { super(msg, cause); }
    }

    // Lifecycle (all no-op stubs that subclasses can override).
    // AOSP's Fragment.onAttach(Context) chains to onAttach(Activity)
    // when the host is an Activity, so a subclass overriding either
    // overload is reached when FragmentManagerImpl calls the Context
    // form (mirrors AOSP performAttach).
    public void onAttach(Context context)            {
        if (context instanceof Activity) {
            this.activity = (Activity) context;
            onAttach((Activity) context);
        }
    }
    public void onAttach(Activity activity)          { this.activity = activity; }
    public void onCreate(Bundle savedInstanceState)  { this.savedInstanceState = savedInstanceState; }
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) { return null; }
    public void onViewCreated(View view, Bundle savedInstanceState) {}
    public void onActivityCreated(Bundle savedInstanceState) {}
    public void onStart()                           {}
    public void onResume()                          { this.resumed = true; }
    public void onPause()                           { this.resumed = false; }
    public void onStop()                            {}
    /** AOSP's Fragment.onDestroyView() body is EMPTY (it is a @CallSuper hook
     *  for subclasses). mView is nulled by the MANAGER on the way down
     *  (FragmentManager.java:1427), after it has removed the view from its
     *  container -- doing it here instead would strand the view in the parent
     *  ViewGroup, because the manager needs mView to know what to remove. */
    public void onDestroyView()                     {}
    public void onDestroy()                         {}
    public void onDetach()                          { this.activity = null; }
    public void onSaveInstanceState(Bundle outState) {}
    @Override public void onConfigurationChanged(Configuration newConfig) {}
    @Override public void onLowMemory() {}
    /** AOSP Fragment.onTrimMemory: empty. Present so the ComponentCallbacks2
     *  declaration above is honest rather than inherited-abstract. */
    @Override public void onTrimMemory(int level) {}
    /** AOSP Fragment.onCreateContextMenu forwards to the activity's
     *  onCreateContextMenu(menu, v, menuInfo). We have no activity-level context
     *  menu pipeline yet, so the body is empty; the SIGNATURE is what the
     *  OnCreateContextMenuListener declaration promises, and an empty body is
     *  also what a fragment that does not override it does on a device. */
    @Override public void onCreateContextMenu(android.view.ContextMenu menu,
            android.view.View v, android.view.ContextMenu.ContextMenuInfo menuInfo) {}
    public void onHiddenChanged(boolean hidden) {}
    public void onActivityResult(int requestCode, int resultCode, Intent data) {}
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {}

    /** AOSP Fragment.onInflate(Context, AttributeSet, Bundle) (Fragment.java
     *  :1378-1428). Called by FragmentManagerImpl.onCreateView BEFORE the
     *  fragment is added (FragmentManager.java:3561), so a fragment declared in
     *  a layout can read its OWN tag's custom attributes -- this is the only
     *  point at which they are available. AOSP's body then loads the
     *  fragment*Transition attrs off R.styleable.Fragment and chains to the two
     *  deprecated overloads; we keep the chain (which is the part apps override)
     *  and skip the transition loading, since Mac.apk runs no fragment
     *  transitions. NOTE for androidx: NavHostFragment reads its navGraph here,
     *  which is why omitting this call leaves a NavHostFragment with no graph. */
    public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState) {
        onInflate(attrs, savedInstanceState);
        if (context instanceof Activity) {
            onInflate((Activity) context, attrs, savedInstanceState);
        }
    }
    /** @deprecated AOSP Fragment.java:1332 -- use onInflate(Context, ...). */
    @Deprecated
    public void onInflate(AttributeSet attrs, Bundle savedInstanceState) {}
    /** @deprecated AOSP Fragment.java:1435 -- use onInflate(Context, ...). */
    @Deprecated
    public void onInflate(Activity activity, AttributeSet attrs, Bundle savedInstanceState) {}

    // ---- view creation (driven by FragmentManagerImpl, mirroring AOSP) ----

    /** AOSP Fragment.onGetLayoutInflater(Bundle) (Fragment.java:1282-1288):
     *  "final LayoutInflater result = mHost.onGetLayoutInflater();" -- i.e. the
     *  HOST ACTIVITY's inflater, not a fresh one, so the activity's factories
     *  (ours: the fragment private factory) apply inside a fragment's own
     *  onCreateView too. That is what lets a `<fragment>` nested inside a
     *  fragment's layout resolve. */
    public LayoutInflater onGetLayoutInflater(Bundle savedInstanceState) {
        if (activity == null) {
            throw new IllegalStateException("onGetLayoutInflater() cannot be executed until the "
                + "Fragment is attached to the FragmentManager.");
        }
        return activity.getLayoutInflater();
    }
    public LayoutInflater getLayoutInflater() { return onGetLayoutInflater(savedInstanceState); }
    LayoutInflater performGetLayoutInflater(Bundle savedInstanceState) {
        return onGetLayoutInflater(savedInstanceState);
    }

    /** AOSP Fragment.performCreateView (Fragment.java:2499-2506): sets
     *  mPerformedCreateView then returns onCreateView(...). The flag is the
     *  guard ensureInflatedFragmentView tests, so it must be set even when
     *  onCreateView returns null. */
    View performCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
        mPerformedCreateView = true;
        return onCreateView(inflater, container, savedInstanceState);
    }

    /** AOSP Fragment.performDestroyView (Fragment.java:2741-2755). */
    void performDestroyView() {
        onDestroyView();
        mPerformedCreateView = false;
    }

    // Accessors
    public Bundle getArguments()                   { return arguments; }
    public void   setArguments(Bundle args)        { this.arguments = args; }
    public View   getView()                        { return mView; }
    public Activity getActivity()                  { return activity; }
    public Context getContext()                    { return activity; }
    public boolean isAdded()                       { return added; }
    public boolean isDetached()                    { return detached; }
    public boolean isHidden()                      { return mHidden; }
    public boolean isRemoving()                    { return removing; }
    public boolean isResumed()                     { return resumed; }
    /** AOSP Fragment.isVisible() is "mAdded && !mHidden && mView != null &&
     *  mView.getWindowToken() != null && mView.getVisibility() == View.VISIBLE".
     *  We deliberately keep only the first two clauses. Adding `mView != null`
     *  would newly answer false for the HEADLESS helper fragments this manager
     *  was built for (play-games-bridge HelperFragment adds itself with
     *  add(f, tag) and never creates a view), and that path is not measurable
     *  tonight -- see docs/fragment-hosting_2026-07-30.md "OPEN QUESTIONS".
     *  The clause that MATTERS is !mHidden, and it only became meaningful now
     *  that hide()/show() actually set mHidden. */
    public boolean isVisible()                     { return added && !mHidden; }
    public boolean getUserVisibleHint()            { return userVisibleHint; }
    public void    setUserVisibleHint(boolean v)   { this.userVisibleHint = v; }
    // AOSP core/java/android/app/Fragment.java (android13-release) :948-954 --
    // a plain boolean round trip (mRetainInstance), getter is `final`.
    // Previously the setter was a no-op and the getter hardcoded false with no
    // backing field, so any app checking getRetainInstance() after calling
    // setRetainInstance(true) silently got false back.
    private boolean retainInstance;
    final public boolean getRetainInstance()       { return retainInstance; }
    public void    setRetainInstance(boolean retain) { this.retainInstance = retain; }
    public String  getTag()                        { return tag; }
    /** AOSP Fragment.getId() (Fragment.java:636) returns mFragmentId, which is
     *  set from the `<fragment android:id>` (or the container's id) by
     *  FragmentManagerImpl.onCreateView. This used to return a field nothing
     *  ever wrote, so findFragmentById() could never match. */
    public int     getId()                         { return mFragmentId; }
    public Bundle  getSavedInstanceState()         { return savedInstanceState; }
    public Fragment getParentFragment()            { return parentFragment; }
    public FragmentManager getFragmentManager()    { return fragmentManager; }
    public FragmentManager getChildFragmentManager(){ return fragmentManager; }
    public void    setMenuVisibility(boolean menuVisible) {}
    public void    setHasOptionsMenu(boolean hasMenu) {}

    // setTargetFragment chain
    private Fragment targetFragment;
    private int      targetRequestCode;
    public void    setTargetFragment(Fragment fragment, int requestCode) {
        this.targetFragment = fragment;
        this.targetRequestCode = requestCode;
    }
    public Fragment getTargetFragment()            { return targetFragment; }
    public int      getTargetRequestCode()         { return targetRequestCode; }

    public void startActivity(Intent intent)       { if (activity != null) activity.startActivity(intent); }
    public void startActivityForResult(Intent intent, int requestCode) {
        if (activity != null) activity.startActivityForResult(intent, requestCode);
    }
    public void requestPermissions(String[] permissions, int requestCode) {
        // AOSP Fragment.requestPermissions (core/java/android/app/Fragment.java
        // :1234-1239) requires a host and delegates to
        // mHost.onRequestPermissionsFromFragment, which routes the eventual
        // answer back to THIS fragment's onRequestPermissionsResult. The
        // contract that matters to callers is "you always get exactly one
        // callback per request"; dropping the request on the floor leaves an
        // app blocked forever with nothing to catch and no timeout. So mirror
        // Activity.requestPermissions' already-correct behaviour locally
        // instead of replicating the 'who'-token startActivityForResult
        // routing (that machinery exists in AOSP to survive activity
        // recreation, which our simplified model does not do).
        if (activity == null) {
            throw new IllegalStateException(
                    "Fragment " + this + " not attached to Activity");
        }
        // Stage S5 (MACAPK_MEDIA_PERMS_ASK, default ON): the host activity runs
        // the same request Activity.requestPermissions does -- the caller is
        // paused, the storage permissions get the runtime-permission dialog,
        // and THIS fragment's onRequestPermissionsResult gets checkPermission
        // of each requested permission -- as AOSP's
        // HostCallbacks.onRequestPermissionsFromFragment routes it (android14
        // Activity.java :9341-9346). docs/storage-runtime-permissions_2026-09-26.md.
        // =0 keeps the pre-S5 body below.
        if (com.macapk.loader.SpecialAccessStore.mediaPermsAsk()) {
            activity.macapkRequestPermissionsFromFragment(this, permissions, requestCode);
            return;
        }
        final int[] grants = new int[permissions != null ? permissions.length : 0];
        for (int i = 0; i < grants.length; i++) {
            grants[i] = android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        final String[] perms = permissions;
        final int code = requestCode;
        // Asynchronous, like AOSP: apps (and permission-wrapper libraries)
        // assume the requesting frame has fully unwound before the result
        // arrives. Fall back to an inline call if there is no main looper yet
        // so the callback is never simply lost.
        try {
            android.os.Looper main = android.os.Looper.getMainLooper();
            if (main != null) {
                new android.os.Handler(main).post(
                        () -> onRequestPermissionsResult(code, perms, grants));
                return;
            }
        } catch (Throwable ignored) {}
        onRequestPermissionsResult(code, perms, grants);
    }

    /** AOSP Fragment.shouldShowRequestPermissionRationale (android14
     *  :1273-1279) asks the host's PackageManager -- the same answer the host
     *  Activity gives (stage S5: from the grant store for the storage family,
     *  false for everything granted at install). No host: false. */
    public boolean shouldShowRequestPermissionRationale(String permission) {
        return activity != null && activity.shouldShowRequestPermissionRationale(permission);
    }

    public android.content.res.Resources getResources() {
        return activity != null ? activity.getResources() : null;
    }
    public String getString(int resId)             {
        android.content.res.Resources r = getResources();
        return r != null ? r.getString(resId) : null;
    }
    public String getString(int resId, Object... args) {
        android.content.res.Resources r = getResources();
        if (r == null) return null;
        try {
            String s = r.getString(resId);
            return s != null && args != null && args.length > 0
                   ? String.format(s, args) : s;
        } catch (Throwable t) { return null; }
    }
    public CharSequence getText(int resId) {
        android.content.res.Resources r = getResources();
        return r != null ? r.getString(resId) : null;
    }

    // Internal setters used by FragmentManager (kept package-private
    // in real AOSP, but exposed here so loader code can populate
    // state without reflection if needed).
    void macapkSetActivity(Activity a)     { this.activity = a; }
    void macapkSetAdded(boolean b)         { this.added = b; }
    void macapkSetFragmentManager(FragmentManager fm) { this.fragmentManager = fm; }
    void macapkSetTag(String t)            { this.tag = t; }
    void macapkSetParentFragment(Fragment p) { this.parentFragment = p; }
    // Lifecycle state owned by FragmentManagerImpl (AOSP keeps mState on
    // Fragment the same way).
    int macapkState = 0;

    /** AOSP inner class used by FragmentManager.saveFragmentInstanceState to
     *  persist fragment state across recreation. AOSP's holds the state Bundle
     *  and nothing else (Fragment.java:443-482); carry it here too so
     *  setInitialSavedState -> onCreate(savedInstanceState) round-trips rather
     *  than silently dropping what the caller saved.
     *
     *  CREATOR is a Parcelable.ClassLoaderCreator, not a plain Creator -- AOSP
     *  declares it that way (the loader is needed to inflate the Bundle's app
     *  classes), and an app that assigns it to the wider type would not link
     *  against a plain Creator. @deprecated matches AOSP. */
    @Deprecated
    public static class SavedState implements android.os.Parcelable {
        final Bundle mState;
        public SavedState() { mState = null; }
        SavedState(Bundle state) { mState = state; }
        SavedState(android.os.Parcel in, ClassLoader loader) {
            Bundle b = in.readBundle();
            if (loader != null && b != null) b.setClassLoader(loader);
            mState = b;
        }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeBundle(mState);
        }
        public static final android.os.Parcelable.ClassLoaderCreator<SavedState> CREATOR =
                new android.os.Parcelable.ClassLoaderCreator<SavedState>() {
            @Override public SavedState createFromParcel(android.os.Parcel s) {
                return new SavedState(s, null);
            }
            @Override public SavedState createFromParcel(android.os.Parcel s, ClassLoader loader) {
                return new SavedState(s, loader);
            }
            @Override public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }

    // ---- Fragment transitions (AOSP core/java/android/app/Fragment.java,
    // android11-release) -- linkage surface only, same spirit as the rest of
    // this class: android.app.Fragment has no real fragment-manager dispatch
    // (androidx.fragment.app.Fragment runs the show at runtime), so nothing
    // here drives an actual scene transition. What matters is that a caller
    // referencing android.app.Fragment's transition API (directly, or via a
    // library built against the platform SDK) gets a working store/retrieve
    // round trip instead of NoSuchMethodError.
    //
    // AOSP has SIX Transition-typed setters (four scene ones + two shared-
    // element ones), each paired with a getter, matching this file's existing
    // convention of never leaving a setter without its getter (see
    // setTargetFragment/getTargetFragment, setUserVisibleHint/
    // getUserVisibleHint above).
    //
    // mReturnTransition/mReenterTransition/mSharedElementReturnTransition
    // default to the USE_DEFAULT_TRANSITION sentinel (AOSP Fragment.java:
    // "private static final Transition USE_DEFAULT_TRANSITION = new
    // TransitionSet();"), an arbitrary concrete Transition used ONLY for its
    // object identity. The getter compares by reference (==), not by null:
    // that is what lets it tell "never called setReturnTransition" (fall
    // back to getEnterTransition(), per AOSP's documented default) apart
    // from "setReturnTransition(null) was called explicitly" (return null).
    // AOSP's own setters additionally run a shouldChangeTransition() guard
    // that exists to gate an internal FragmentManager notification we have
    // no equivalent of (no real dispatch), so it is intentionally omitted
    // here -- the store/retrieve contract is the only observable part.
    private static final Transition USE_DEFAULT_TRANSITION = new TransitionSet();
    private Transition mEnterTransition;
    private Transition mExitTransition;
    private Transition mReenterTransition = USE_DEFAULT_TRANSITION;
    private Transition mReturnTransition = USE_DEFAULT_TRANSITION;
    private Transition mSharedElementEnterTransition;
    private Transition mSharedElementReturnTransition = USE_DEFAULT_TRANSITION;

    /** AOSP Fragment.setEnterTransition -- the Transition used to move Views
     *  into the initial scene. Null leaves entering Views unaffected.
     *  @attr ref android.R.styleable#Fragment_fragmentEnterTransition */
    public void setEnterTransition(Transition transition) { mEnterTransition = transition; }
    public Transition getEnterTransition() { return mEnterTransition; }

    /** AOSP Fragment.setExitTransition -- the Transition used to move Views
     *  out of the scene when the fragment is removed/hidden/detached NOT due
     *  to popping the back stack. Null leaves exiting Views unaffected.
     *  @attr ref android.R.styleable#Fragment_fragmentExitTransition */
    public void setExitTransition(Transition transition) { mExitTransition = transition; }
    public Transition getExitTransition() { return mExitTransition; }

    /** AOSP Fragment.setReenterTransition -- the Transition used to move
     *  Views into the scene when returning due to popping the back stack.
     *  If never set, defaults to the same value as {@link #getExitTransition()}.
     *  @attr ref android.R.styleable#Fragment_fragmentReenterTransition */
    public void setReenterTransition(Transition transition) { mReenterTransition = transition; }
    public Transition getReenterTransition() {
        return mReenterTransition == USE_DEFAULT_TRANSITION ? getExitTransition() : mReenterTransition;
    }

    /** AOSP Fragment.setReturnTransition -- the Transition used to move Views
     *  out of the scene when the fragment is being removed/hidden/detached
     *  because of popping the back stack. If never set, defaults to the same
     *  value as {@link #getEnterTransition()}.
     *  @attr ref android.R.styleable#Fragment_fragmentExitTransition */
    public void setReturnTransition(Transition transition) { mReturnTransition = transition; }
    public Transition getReturnTransition() {
        return mReturnTransition == USE_DEFAULT_TRANSITION ? getEnterTransition() : mReturnTransition;
    }

    /** AOSP Fragment.setSharedElementEnterTransition -- the Transition used
     *  for shared elements transferred INTO the content scene (e.g. a
     *  ChangeBounds tween of the shared View's size/position). Null causes
     *  transferred shared elements to blink to the final position.
     *  @attr ref android.R.styleable#Fragment_fragmentSharedElementEnterTransition */
    public void setSharedElementEnterTransition(Transition transition) { mSharedElementEnterTransition = transition; }
    public Transition getSharedElementEnterTransition() { return mSharedElementEnterTransition; }

    /** AOSP Fragment.setSharedElementReturnTransition -- the Transition used
     *  for shared elements transferred back during a pop of the back stack
     *  (acts in the LEAVING fragment). If never set, defaults to the same
     *  value as {@link #getSharedElementEnterTransition()}.
     *  @attr ref android.R.styleable#Fragment_fragmentSharedElementReturnTransition */
    public void setSharedElementReturnTransition(Transition transition) { mSharedElementReturnTransition = transition; }
    public Transition getSharedElementReturnTransition() {
        return mSharedElementReturnTransition == USE_DEFAULT_TRANSITION
                ? getSharedElementEnterTransition() : mSharedElementReturnTransition;
    }

    // Context-menu registration + assist dump (API 11). Mac.apk has no context
    // menus / dumpsys, so these are no-ops.
    /** AOSP Fragment.isInLayout() (Fragment.java:893) returns mInLayout -- true
     *  for a fragment declared by a `<fragment>` tag. Was hardcoded false while
     *  Mac.apk could not inflate the tag; it now can, and apps branch on this. */
    public boolean isInLayout() { return mInLayout; }
    public void registerForContextMenu(android.view.View view) { }
    public void unregisterForContextMenu(android.view.View view) { }
    public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) { }

    // ================================================================
    // AOSP API-36 surface completion (android.app.Fragment).
    // AOSP ref: frameworks/base/core/java/android/app/Fragment.java
    // (android16-release). The platform Fragment is deprecated in favour of
    // androidx.fragment.app.Fragment, but legacy apps (and androidx's own
    // compat shims, which reference the platform descriptor) still link
    // against it, so the surface has to be complete.
    // ================================================================

    /** AOSP: {@code @Override final public boolean equals(Object o) { return
     *  super.equals(o); }} -- FINAL on purpose, "so it can't be overridden",
     *  because the FragmentManager keys collections on fragment IDENTITY. An
     *  app subclass that tried to define value equality would break that. */
    @Override public final boolean equals(Object o) { return super.equals(o); }

    /** AOSP: {@code @Override final public int hashCode() { return
     *  super.hashCode(); }} -- final for the same reason as equals. */
    @Override public final int hashCode() { return super.hashCode(); }

    /** AOSP getHost(): {@code return mHost == null ? null : mHost.onGetHost();}
     *  -- "Return the host object of this fragment", which for an
     *  Activity-hosted fragment IS the Activity. final, per AOSP. */
    public final Object getHost() { return activity; }

    /** AOSP getLoaderManager(): the fragment's own LoaderManager, created
     *  lazily. AOSP THROWS IllegalStateException when the fragment is not
     *  attached -- keep that, it is what tells an app it called too early
     *  instead of handing back a manager wired to nothing.
     *  @deprecated matches AOSP. */
    @Deprecated
    public LoaderManager getLoaderManager() {
        if (mLoaderManager != null) return mLoaderManager;
        if (activity == null) {
            throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        }
        mLoaderManager = new LoaderManager();
        return mLoaderManager;
    }
    private LoaderManager mLoaderManager;

    /** AOSP isStateSaved(): {@code if (mFragmentManager == null) return false;
     *  return mFragmentManager.isStateSaved();} -- "true if this fragment's
     *  state has already been saved by its host", i.e. a transaction now would
     *  be lost. final, per AOSP. */
    public final boolean isStateSaved() {
        FragmentManager fm = getFragmentManager();
        return fm != null && fm.isStateSaved();
    }

    // ---- Options-menu callbacks (AOSP Fragment.java) ----
    // These are the hooks setHasOptionsMenu(true) enables. AOSP's bodies are
    // empty / false; the app overrides them and the host's menu dispatch calls
    // them. Missing entirely, an app's @Override does not resolve.

    /** AOSP onCreateOptionsMenu(Menu, MenuInflater): empty body. Mastodon
     *  overrides this one. */
    public void onCreateOptionsMenu(android.view.Menu menu,
            android.view.MenuInflater inflater) { }
    /** AOSP onPrepareOptionsMenu(Menu): empty body. */
    public void onPrepareOptionsMenu(android.view.Menu menu) { }
    /** AOSP onDestroyOptionsMenu(): empty body -- "Called when this fragment's
     *  option menu items are no longer being included in the overall menu." */
    public void onDestroyOptionsMenu() { }
    /** AOSP onOptionsItemSelected(MenuItem): {@code return false;} -- "return
     *  false to allow normal menu processing to proceed". Mastodon overrides
     *  this one. */
    public boolean onOptionsItemSelected(android.view.MenuItem item) { return false; }
    /** AOSP onOptionsMenuClosed(Menu): empty body. */
    public void onOptionsMenuClosed(android.view.Menu menu) { }
    /** AOSP onContextItemSelected(MenuItem): {@code return false;} -- "return
     *  false to allow normal context menu processing to proceed". */
    public boolean onContextItemSelected(android.view.MenuItem item) { return false; }

    // ---- Remaining lifecycle / mode callbacks ----

    /** AOSP onViewStateRestored(Bundle): sets mCalled (AOSP's did-you-call-super
     *  tripwire) and nothing else -- "Called when all saved state has been
     *  restored into the view hierarchy of the fragment." */
    public void onViewStateRestored(Bundle savedInstanceState) { }

    /** AOSP onAttachFragment(Fragment): empty body -- "Called when a fragment
     *  is attached as a child of this fragment." */
    public void onAttachFragment(Fragment childFragment) { }

    /** AOSP onMultiWindowModeChanged(boolean, Configuration): empty body; the
     *  host activity forwards its own callback to every attached fragment. */
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) { }
    /** AOSP onMultiWindowModeChanged(boolean): empty body.
     *  @deprecated matches AOSP (superseded by the Configuration overload). */
    @Deprecated
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) { }
    /** AOSP onPictureInPictureModeChanged(boolean, Configuration): empty body. */
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
            Configuration newConfig) { }
    /** AOSP onPictureInPictureModeChanged(boolean): empty body.
     *  @deprecated matches AOSP. */
    @Deprecated
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) { }

    /** AOSP onCreateAnimator(int, boolean, int): {@code return null;} -- "Called
     *  when a fragment loads an animation"; null means "use the default", which
     *  is also what happens here since FragmentManagerImpl runs no animator. */
    public android.animation.Animator onCreateAnimator(int transit, boolean enter,
            int nextAnim) {
        return null;
    }

    // ---- Initial saved state (AOSP Fragment.java:1010) ----

    /** AOSP setInitialSavedState(SavedState): "Set the initial saved state that
     *  this Fragment should restore itself from when first being constructed",
     *  paired with FragmentManager.saveFragmentInstanceState. AOSP throws
     *  IllegalStateException if the fragment is already active (mIndex >= 0) --
     *  keep that check, it is the observable half; ours uses isAdded(), which is
     *  the same "already in a manager" condition this tree can express. */
    public void setInitialSavedState(SavedState state) {
        if (isAdded()) {
            throw new IllegalStateException("Fragment already active");
        }
        mInitialSavedState = (state != null) ? state.mState : null;
    }
    /** The Bundle setInitialSavedState stored, for FragmentManagerImpl to feed
     *  into onCreate. Package-private for the same reason mView is: the
     *  manager owns the restore, not the fragment. */
    Bundle mInitialSavedState;

    // ---- Transitions ----

    /** AOSP setAllowEnterTransitionOverlap(boolean) / getter: whether the enter
     *  transition may overlap the exit transition of the fragment being
     *  replaced. AOSP's getter defaults to TRUE when never set. */
    public void setAllowEnterTransitionOverlap(boolean allow) {
        mAllowEnterTransitionOverlap = allow;
    }
    public boolean getAllowEnterTransitionOverlap() { return mAllowEnterTransitionOverlap; }
    private boolean mAllowEnterTransitionOverlap = true;

    /** AOSP setAllowReturnTransitionOverlap(boolean) / getter: the same for the
     *  return (back-stack pop) direction. Defaults to TRUE. */
    public void setAllowReturnTransitionOverlap(boolean allow) {
        mAllowReturnTransitionOverlap = allow;
    }
    public boolean getAllowReturnTransitionOverlap() { return mAllowReturnTransitionOverlap; }
    private boolean mAllowReturnTransitionOverlap = true;

    /** AOSP setEnterSharedElementCallback(SharedElementCallback): the app's hook
     *  into shared-element ("hero image") mapping for this fragment. AOSP
     *  substitutes SharedElementCallback.NULL_CALLBACK for null so its
     *  transition code never null-checks; we store null directly because
     *  nothing here dereferences it -- FragmentManagerImpl runs no shared-element
     *  transition -- and a getter would have to answer what the app set. */
    public void setEnterSharedElementCallback(SharedElementCallback callback) {
        mEnterSharedElementCallback = callback;
    }
    /** AOSP setExitSharedElementCallback(SharedElementCallback); see above. */
    public void setExitSharedElementCallback(SharedElementCallback callback) {
        mExitSharedElementCallback = callback;
    }
    private SharedElementCallback mEnterSharedElementCallback;
    private SharedElementCallback mExitSharedElementCallback;

    /** AOSP postponeEnterTransition(): "Postpone the entering Fragment
     *  transition until startPostponedEnterTransition() or
     *  FragmentManager.executePendingTransactions() has been called."
     *  FragmentManagerImpl runs no entering transition to postpone, so the flag
     *  is recorded and the fragment is shown as it already is -- the same
     *  end state a device reaches once the postponement is lifted. Crucially
     *  the pairing still works: an app that postpones and later starts is not
     *  left with a permanently invisible fragment. */
    public void postponeEnterTransition() { mEnterTransitionPostponed = true; }
    /** AOSP startPostponedEnterTransition(): "Begin postponed transitions after
     *  postponeEnterTransition() was called." */
    public void startPostponedEnterTransition() { mEnterTransitionPostponed = false; }
    private boolean mEnterTransitionPostponed;

    // ---- Activity starts with options (AOSP Fragment.java:1440-1500) ----
    // AOSP routes each of these through mHost.onStartActivityFromFragment so the
    // eventual result comes back to THIS fragment. Activity.startActivityFromFragment
    // in this tree records that pairing (see its comment there), so route through
    // it rather than through Activity.startActivityForResult directly.

    /** AOSP startActivity(Intent, Bundle): AOSP throws IllegalStateException
     *  when the fragment is not attached -- keep it; a silently dropped start
     *  is much harder to diagnose than the exception AOSP raises. */
    public void startActivity(Intent intent, Bundle options) {
        if (activity == null) {
            throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        }
        activity.startActivity(intent, options);
    }

    /** AOSP startActivityForResult(Intent, int, Bundle). */
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (activity == null) {
            throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        }
        activity.startActivityFromFragment(this, intent, requestCode, options);
    }

    /** AOSP startIntentSenderForResult(IntentSender, int, Intent, int, int,
     *  int, Bundle). */
    public void startIntentSenderForResult(android.content.IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options)
            throws android.content.IntentSender.SendIntentException {
        if (activity == null) {
            throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        }
        activity.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask,
                flagsValues, extraFlags, options);
    }
}
