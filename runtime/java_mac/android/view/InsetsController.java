// android.view.InsetsController -- AOSP's own (@hide) implementation of the
// public WindowInsetsController interface. This file holds the state machine
// that used to live INSIDE WindowInsetsController back when we wrongly shipped
// that type as a `final class`; see the header there for why the split was
// required (a guest `implements WindowInsetsController` cannot link against a
// class). The code below is carried over verbatim, so nothing about the
// observable behaviour changed with the split.
//
// Mac.apk renders inside an NSWindow with no real Android system bars, but
// edge-to-edge / immersive apps DRIVE this object and then READ it back
// (getSystemBarsAppearance for light-vs-dark icon decisions, requested-visibility
// state for layout). A pure no-op made those reads return stale 0s and dropped
// requested state on the floor, so apps mis-styled their bars or repeatedly
// re-issued show/hide. This keeps real appearance / behavior / per-type
// visibility state so a read reflects the last write, and notifies
// controllable-insets listeners.
//
// v1.0.939: REACHABLE. Window.getInsetsController() and
// View.getWindowInsetsController() now hand one of these out, and the
// state below is wired through to what actually paints: setSystemBarsAppearance
// drives the light-icon decision that StatusBarDriver used to take from the
// theme alone, and show/hide really add and remove the bar. The measurement the
// old header asked for is in the v1.0.939 Current_State entry.
package android.view;

import java.util.ArrayList;

public class InsetsController implements WindowInsetsController {
    private int mAppearance;
    private int mBehavior = BEHAVIOR_DEFAULT;
    // Per-type visibility; default all system bars visible (AOSP default).
    private int mVisibleTypes = WindowInsets.Type.systemBars();

    private final ArrayList<OnControllableInsetsChangedListener> mListeners = new ArrayList<>();

    @Override public void setSystemBarsAppearance(int appearance, int mask) {
        mAppearance = (mAppearance & ~mask) | (appearance & mask);
        // Publish it: the bar painter reads this and it outranks the theme.
        Window.macapkSetBarAppearance(appearance, mask);
    }
    @Override public int getSystemBarsAppearance() { return mAppearance; }

    @Override public void setSystemBarsBehavior(int behavior) { mBehavior = behavior; }
    @Override public int getSystemBarsBehavior() { return mBehavior; }

    @Override public void show(int types) {
        mVisibleTypes |= types;
        Window.macapkSetBarVisible(types, true);
        macapkRouteIme(types, true);
        notifyControllable(types);
    }
    @Override public void hide(int types) {
        mVisibleTypes &= ~types;
        Window.macapkSetBarVisible(types, false);
        macapkRouteIme(types, false);
        notifyControllable(types);
    }

    /** AOSP's InsetsController routes the ime() type to InputMethodManager --
     *  show/hide(Type.ime()) IS the modern, recommended way to raise and dismiss
     *  the soft keyboard. We only flipped visibility bits, so hide(ime()) never
     *  unbound the editor: a game dismissing the keyboard this way kept its
     *  keystrokes routed into the still-focused text field. (Showing was mostly
     *  rescued by the lazy checkFocus bind on the first keystroke, but the
     *  shown-state bookkeeping was still wrong, so a later toggleSoftInput took
     *  the wrong branch.) */
    private static void macapkRouteIme(int types, boolean show) {
        if ((types & android.view.WindowInsets.Type.ime()) == 0) return;
        try {
            android.view.inputmethod.InputMethodManager imm =
                android.view.inputmethod.InputMethodManager.peekInstance();
            if (imm == null) return;
            if (show) {
                // An explicit show cancels the suppression a previous hide()
                // armed -- FIRST, so it happens even when there is no target to
                // show for and the lazy first-keystroke rebind is what actually
                // restores typing.
                android.view.inputmethod.InputMethodManager.macapkClearHideSuppressed();
                // Show for the editor that has focus. Passing null would CLEAR
                // the active input (macapkSetActiveInput(null)), i.e. the
                // opposite of showing. macapkShowTarget falls back to the
                // FOCUSED view because a preceding hide() nulls sActiveInput --
                // reading only the active input made hide->show a one-way
                // trapdoor that left typing dead until focus moved elsewhere.
                android.widget.TextView t =
                    android.view.inputmethod.InputMethodManager.macapkShowTarget();
                if (t != null) imm.showSoftInput(t, 0);
            } else android.view.inputmethod.InputMethodManager.macapkHideInput();
        } catch (Throwable ignored) { }
    }

    /** Not an AOSP public method, but a convenient read for our own layout code
     *  and any caller that wants to know what show/hide last set. */
    public boolean isRequestedVisible(int type) { return (mVisibleTypes & type) != 0; }

    /** KNOWN GAP (do not mistake this for a finished implementation). AOSP's
     *  whole point here is to call listener.onReady(controller, types) with a
     *  live WindowInsetsAnimationController the app then drives (the draggable
     *  IME-follow gesture). WindowInsetsAnimationController exists in this tree
     *  only as a type, and we never animate system-bar insets, so the honest
     *  answer is onCancelled -- an app waiting on onReady would otherwise wait
     *  forever. */
    @Override public void controlWindowInsetsAnimation(int types, long durationMillis,
            android.view.animation.Interpolator interpolator,
            android.os.CancellationSignal cancellationSignal,
            android.view.WindowInsetsAnimationControlListener listener) {
        if (listener != null) { try { listener.onCancelled(null); } catch (Throwable ignored) {} }
    }

    @Override public void addOnControllableInsetsChangedListener(
            OnControllableInsetsChangedListener l) {
        if (l == null) return;
        if (!mListeners.contains(l)) mListeners.add(l);
        // AOSP immediately reports the currently-controllable types.
        try { l.onControllableInsetsChanged(this, WindowInsets.Type.systemBars()); }
        catch (Throwable ignored) {}
    }
    @Override public void removeOnControllableInsetsChangedListener(
            OnControllableInsetsChangedListener l) {
        mListeners.remove(l);
    }

    private void notifyControllable(int typeMask) {
        for (OnControllableInsetsChangedListener l : new ArrayList<>(mListeners)) {
            try { l.onControllableInsetsChanged(this, typeMask); } catch (Throwable ignored) {}
        }
    }
}
