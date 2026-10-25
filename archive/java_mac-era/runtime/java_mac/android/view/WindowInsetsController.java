// android.view.WindowInsetsController -- AOSP declares this an INTERFACE, and
// that KIND is load-bearing.
//
// WHY (found 2026-07-29 by debug/kind_audit.py -- the only mismatch it reports
// under android/view once the four hand-found ones were fixed): we shipped it as
// a `public final class`. Anything that correctly declares
// `implements WindowInsetsController` fails to link with
// IncompatibleClassChangeError, and `final` additionally forbids the AOSP
// implementation shape outright. The previous version of this file already
// recorded the defect in its own comment ("because AOSP declares
// WindowInsetsController as an INTERFACE, not a final class -- the concrete
// state below split into an implementation class") -- this is that split.
//
// Surface verified against
// frameworks/base/core/java/android/view/WindowInsetsController.java:
// `public interface WindowInsetsController`, with show / hide /
// controlWindowInsetsAnimation / set+getSystemBarsAppearance /
// set+getSystemBarsBehavior / add+removeOnControllableInsetsChangedListener
// abstract, plus the nested `interface OnControllableInsetsChangedListener` and
// the Appearance/Behavior constants.
//
// BEHAVIOUR IS UNCHANGED by this edit: the state machine moved verbatim to
// android.view.InsetsController (AOSP's own name for its implementation), and
// Window.getInsetsController() / View.getWindowInsetsController() still return
// null exactly as before. Making them return a real InsetsController is a
// separate, render-visible change (it switches on the edge-to-edge path in
// WindowCompat and Compose's system-bars insets) and must be measured, not
// bundled into a type-shape fix.
package android.view;

public interface WindowInsetsController {
    // System bar appearance bits (mirrored from AOSP API constants).
    int APPEARANCE_OPAQUE_STATUS_BARS = 0x1;
    int APPEARANCE_OPAQUE_NAVIGATION_BARS = 0x2;
    int APPEARANCE_LOW_PROFILE_BARS = 0x4;
    int APPEARANCE_LIGHT_STATUS_BARS = 0x8;
    int APPEARANCE_LIGHT_NAVIGATION_BARS = 0x10;
    // Legacy aliases kept from the prior impl so existing callers still compile.
    int APPEARANCE_OPAQUE_STATUS_BAR = APPEARANCE_OPAQUE_STATUS_BARS;
    int APPEARANCE_OPAQUE_NAVIGATION_BAR = APPEARANCE_OPAQUE_NAVIGATION_BARS;

    int BEHAVIOR_DEFAULT = 1;
    // Deprecated AOSP constants. BEHAVIOR_SHOW_BARS_BY_TOUCH is 0, NOT an alias
    // of BEHAVIOR_DEFAULT (AOSP WindowInsetsController.java:132); only
    // BEHAVIOR_SHOW_BARS_BY_SWIPE aliases BEHAVIOR_DEFAULT (line 157). Compiled
    // app bytecode inlines these values, so a wrong constant makes an app's
    // `getSystemBarsBehavior() == BEHAVIOR_SHOW_BARS_BY_TOUCH` test answer
    // backwards.
    int BEHAVIOR_SHOW_BARS_BY_TOUCH = 0;
    int BEHAVIOR_SHOW_BARS_BY_SWIPE = BEHAVIOR_DEFAULT;
    int BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 2;

    interface OnControllableInsetsChangedListener {
        void onControllableInsetsChanged(WindowInsetsController controller, int typeMask);
    }

    void show(int types);
    void hide(int types);

    void setSystemBarsAppearance(int appearance, int mask);
    int getSystemBarsAppearance();

    void setSystemBarsBehavior(int behavior);
    int getSystemBarsBehavior();

    void controlWindowInsetsAnimation(int types, long durationMillis,
            android.view.animation.Interpolator interpolator,
            android.os.CancellationSignal cancellationSignal,
            android.view.WindowInsetsAnimationControlListener listener);

    void addOnControllableInsetsChangedListener(OnControllableInsetsChangedListener l);
    void removeOnControllableInsetsChangedListener(OnControllableInsetsChangedListener l);
}
