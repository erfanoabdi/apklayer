// android.app.FragmentManager -- AOSP pre-androidx fragment manager.
// Companion to android.app.Fragment; required by F-Droid + AppCompat-
// based apps purely for the linker. They use androidx.fragment.app
// FragmentManager at runtime.
package android.app;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public abstract class FragmentManager {
    public interface OnBackStackChangedListener {
        void onBackStackChanged();
    }

    // AOSP core/java/android/app/FragmentManager.java (android13-release)
    // :100-138 -- BackStackEntry is an INTERFACE, not a class. Shipping it as
    // an abstract class throws IncompatibleClassChangeError the moment an app
    // (or androidx's compat shim, which references the platform type by
    // descriptor) does `instanceof BackStackEntry` or invokes it via an
    // invokeinterface bytecode -- the JVM verifier checks the referenced
    // type's ACC_INTERFACE flag against the invoke instruction, and a class
    // fails that check unconditionally.
    public interface BackStackEntry {
        // :106 getId()
        int getId();
        // :113 getName()
        String getName();
        // :119 getBreadCrumbTitleRes() -- previously missing entirely.
        int getBreadCrumbTitleRes();
        // :125 getBreadCrumbShortTitleRes() -- previously missing entirely.
        int getBreadCrumbShortTitleRes();
        // :131 getBreadCrumbTitle()
        CharSequence getBreadCrumbTitle();
        // :137 getBreadCrumbShortTitle()
        CharSequence getBreadCrumbShortTitle();
    }

    // AOSP :452-593 -- FragmentLifecycleCallbacks is an ABSTRACT STATIC CLASS
    // with 14 empty-bodied onFragmentXxx hooks (apps subclass it and override
    // only the ones they care about). We previously shipped it as an empty
    // marker interface, which is both the wrong kind (same
    // IncompatibleClassChangeError hazard as BackStackEntry above) and
    // missing every method, so any app-registered callback subclass would
    // fail to even compile against this framework's descriptor, let alone
    // receive a single dispatch.
    public abstract static class FragmentLifecycleCallbacks {
        // :462
        public void onFragmentPreAttached(FragmentManager fm, Fragment f, Context context) {}
        // :472
        public void onFragmentAttached(FragmentManager fm, Fragment f, Context context) {}
        // :483-484
        public void onFragmentPreCreated(FragmentManager fm, Fragment f,
                Bundle savedInstanceState) {}
        // :495
        public void onFragmentCreated(FragmentManager fm, Fragment f, Bundle savedInstanceState) {}
        // :506-507
        public void onFragmentActivityCreated(FragmentManager fm, Fragment f,
                Bundle savedInstanceState) {}
        // :518-519
        public void onFragmentViewCreated(FragmentManager fm, Fragment f, View v,
                Bundle savedInstanceState) {}
        // :528
        public void onFragmentStarted(FragmentManager fm, Fragment f) {}
        // :537
        public void onFragmentResumed(FragmentManager fm, Fragment f) {}
        // :546
        public void onFragmentPaused(FragmentManager fm, Fragment f) {}
        // :555
        public void onFragmentStopped(FragmentManager fm, Fragment f) {}
        // :565
        public void onFragmentSaveInstanceState(FragmentManager fm, Fragment f, Bundle outState) {}
        // :574
        public void onFragmentViewDestroyed(FragmentManager fm, Fragment f) {}
        // :583
        public void onFragmentDestroyed(FragmentManager fm, Fragment f) {}
        // :592
        public void onFragmentDetached(FragmentManager fm, Fragment f) {}
    }

    public abstract FragmentTransaction beginTransaction();
    public abstract boolean executePendingActions();
    public boolean executePendingTransactions() { return executePendingActions(); }
    public abstract Fragment findFragmentById(int id);
    public abstract Fragment findFragmentByTag(String tag);
    public abstract void popBackStack();
    public abstract boolean popBackStackImmediate();
    public abstract void popBackStack(String name, int flags);
    public abstract void popBackStack(int id, int flags);
    public abstract boolean popBackStackImmediate(String name, int flags);
    public abstract boolean popBackStackImmediate(int id, int flags);
    public abstract int getBackStackEntryCount();
    public abstract BackStackEntry getBackStackEntryAt(int index);
    public abstract void addOnBackStackChangedListener(OnBackStackChangedListener listener);
    public abstract void removeOnBackStackChangedListener(OnBackStackChangedListener listener);
    public abstract void putFragment(android.os.Bundle bundle, String key, Fragment fragment);
    public abstract Fragment getFragment(android.os.Bundle bundle, String key);
    public abstract List<Fragment> getFragments();
    public abstract Fragment.SavedState saveFragmentInstanceState(Fragment f);
    public abstract boolean isDestroyed();

    public static final int POP_BACK_STACK_INCLUSIVE = 1;

    // ================================================================
    // AOSP API-36 surface completion.
    // AOSP ref: frameworks/base/core/java/android/app/FragmentManager.java
    // (android16-release).
    // ================================================================

    /** AOSP registerFragmentLifecycleCallbacks(FragmentLifecycleCallbacks,
     *  boolean): "Registers a FragmentLifecycleCallbacks to listen to fragment
     *  lifecycle events happening in this FragmentManager." `recursive` also
     *  observes child FragmentManagers. AnkiDroid registers one. Abstract in
     *  AOSP; FragmentManagerImpl really dispatches these. */
    public abstract void registerFragmentLifecycleCallbacks(
            FragmentLifecycleCallbacks cb, boolean recursive);

    /** AOSP unregisterFragmentLifecycleCallbacks(FragmentLifecycleCallbacks):
     *  "Unregisters a previously registered FragmentLifecycleCallbacks." */
    public abstract void unregisterFragmentLifecycleCallbacks(FragmentLifecycleCallbacks cb);

    /** AOSP getPrimaryNavigationFragment(): "Return the currently active primary
     *  navigation fragment for this FragmentManager", set by
     *  FragmentTransaction.setPrimaryNavigationFragment; null when none. */
    public abstract Fragment getPrimaryNavigationFragment();

    /** AOSP isStateSaved(): "Returns true if the FragmentManager's state has
     *  already been saved by its host" -- i.e. a commit() now would throw.
     *  Fragment.isStateSaved() asks this. */
    public abstract boolean isStateSaved();

    /** AOSP dump(String, FileDescriptor, PrintWriter, String[]): "Print the
     *  FragmentManager's state into the given stream", used by
     *  `adb shell dumpsys activity` and by app crash handlers that dump their
     *  own fragment state. */
    public abstract void dump(String prefix, java.io.FileDescriptor fd,
            java.io.PrintWriter writer, String[] args);

    /** AOSP invalidateOptionsMenu(): concrete (not abstract) in AOSP -- "Invalidate
     *  the attached activity's options menu as necessary." AOSP's base body is
     *  empty; FragmentManagerImpl overrides it. */
    public void invalidateOptionsMenu() { }

    /** AOSP enableDebugLogging(boolean): STATIC -- "Control whether the
     *  framework's internal fragment manager debugging logs are turned on."
     *  AOSP sets FragmentManagerImpl.DEBUG; we record it the same way so a
     *  future dump() can honour it. */
    public static void enableDebugLogging(boolean enabled) { sDebugLogging = enabled; }
    static boolean sDebugLogging;
}
