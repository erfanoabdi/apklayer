// android.app.FragmentTransaction -- pre-androidx fragment transaction.
// Legacy stub for linker compatibility.
package android.app;

import android.view.View;

public abstract class FragmentTransaction {
    // AOSP core/java/android/app/FragmentTransaction.java (android13-release)
    // :166-183. TRANSIT_FRAGMENT_CLOSE carries TRANSIT_EXIT_MASK (0x2000), not
    // TRANSIT_ENTER_MASK (0x1000) -- it is the one transition of the three that
    // is an EXIT. We previously had it as 0x1002 (ENTER_MASK | 2), colliding
    // with the ENTER namespace and not matching AOSP's inlined-constant value
    // (8194) at all.
    public static final int TRANSIT_ENTER_MASK            = 0x1000;
    public static final int TRANSIT_EXIT_MASK              = 0x2000;
    public static final int TRANSIT_UNSET                 = -1;
    public static final int TRANSIT_NONE                 = 0;
    public static final int TRANSIT_FRAGMENT_OPEN        = 1 | TRANSIT_ENTER_MASK;
    public static final int TRANSIT_FRAGMENT_CLOSE       = 2 | TRANSIT_EXIT_MASK;
    public static final int TRANSIT_FRAGMENT_FADE        = 3 | TRANSIT_ENTER_MASK;

    public abstract FragmentTransaction add(Fragment fragment, String tag);
    public abstract FragmentTransaction add(int containerViewId, Fragment fragment);
    public abstract FragmentTransaction add(int containerViewId, Fragment fragment, String tag);
    public abstract FragmentTransaction replace(int containerViewId, Fragment fragment);
    public abstract FragmentTransaction replace(int containerViewId, Fragment fragment, String tag);
    public abstract FragmentTransaction remove(Fragment fragment);
    public abstract FragmentTransaction hide(Fragment fragment);
    public abstract FragmentTransaction show(Fragment fragment);
    public abstract FragmentTransaction detach(Fragment fragment);
    public abstract FragmentTransaction attach(Fragment fragment);
    public abstract FragmentTransaction setTransition(int transit);
    public abstract FragmentTransaction setCustomAnimations(int enter, int exit);
    public abstract FragmentTransaction setCustomAnimations(int enter, int exit, int popEnter, int popExit);
    // AOSP FragmentTransaction.addSharedElement (android11-release): maps a
    // View from a disappearing Fragment to the transitionName of a View in
    // an appearing Fragment, for the shared-element transition machinery.
    // No real transition dispatch exists on this legacy-linkage path (see
    // Fragment.java's transition setters), so nothing consumes the mapping
    // yet -- this exists so the method resolves for any caller that builds
    // against the platform SDK surface instead of NoSuchMethodError.
    public abstract FragmentTransaction addSharedElement(View sharedElement, String name);
    public abstract FragmentTransaction addToBackStack(String name);
    public abstract FragmentTransaction disallowAddToBackStack();
    public abstract FragmentTransaction setBreadCrumbTitle(int res);
    public abstract FragmentTransaction setBreadCrumbTitle(CharSequence text);
    public abstract FragmentTransaction setBreadCrumbShortTitle(int res);
    public abstract FragmentTransaction setBreadCrumbShortTitle(CharSequence text);
    public abstract int commit();
    public abstract int commitAllowingStateLoss();
    public abstract void commitNow();
    public abstract void commitNowAllowingStateLoss();
    public abstract boolean isEmpty();
    public abstract boolean isAddToBackStackAllowed();

    // ---- AOSP API-36 surface completion ----
    // AOSP ref: frameworks/base/core/java/android/app/FragmentTransaction.java
    // (android16-release). All four are abstract there too.

    /** AOSP runOnCommit(Runnable): "Add a Runnable to this transaction that will
     *  be run after this transaction has been committed", i.e. after the ops
     *  execute. AOSP rejects it on a transaction added to the back stack. */
    public abstract FragmentTransaction runOnCommit(Runnable runnable);

    /** AOSP setPrimaryNavigationFragment(Fragment): "Set a currently active
     *  fragment in this FragmentManager as the primary navigation fragment",
     *  which is what receives back-press / child-navigation delegation. */
    public abstract FragmentTransaction setPrimaryNavigationFragment(Fragment fragment);

    /** AOSP setReorderingAllowed(boolean): "Sets whether or not to allow
     *  optimizing operations within and across transactions." */
    public abstract FragmentTransaction setReorderingAllowed(boolean reorderingAllowed);

    /** AOSP setTransitionStyle(int): "Set a custom style resource that will be
     *  used for resolving transit animations." */
    public abstract FragmentTransaction setTransitionStyle(int styleRes);
}
