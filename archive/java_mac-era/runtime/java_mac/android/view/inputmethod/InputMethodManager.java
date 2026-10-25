// android.view.inputmethod.InputMethodManager -- the soft-keyboard
// service. There's no IME on macOS to surface; engines call it to
// show/hide the keyboard for in-game text entry. All calls succeed
// silently, which matches the "no virtual keyboard pops up" behavior
// the user already gets.
package android.view.inputmethod;

import android.os.IBinder;
import android.view.View;

public class InputMethodManager {
    public static final int SHOW_IMPLICIT = 0x0001;
    public static final int SHOW_FORCED   = 0x0002;
    public static final int HIDE_IMPLICIT_ONLY = 0x0001;
    public static final int HIDE_NOT_ALWAYS    = 0x0002;
    public static final int RESULT_UNCHANGED_SHOWN  = 0;
    public static final int RESULT_UNCHANGED_HIDDEN = 1;
    public static final int RESULT_SHOWN  = 2;
    public static final int RESULT_HIDDEN = 3;

    private static final InputMethodManager INSTANCE = new InputMethodManager();

    public static InputMethodManager peekInstance() { return INSTANCE; }

    // Mac.apk IS the IME: there is no on-screen keyboard, so when an app
    // shows the soft input for a View we record that View as the active
    // text target and route host (Mac) keyboard keystrokes into it (see
    // TouchDispatcher + TextView.macapkHandleSoftKey). This is how a Unity
    // InputField (room code, chat, name) receives typing -- Unity's
    // com.unity3d.player.S calls showSoftInput(editText, ...) to open, then
    // its TextWatcher pushes each change to the engine via nativeSetInputString.
    private static final boolean sTrace = "1".equals(System.getenv("MACAPK_TRACE_IME"));
    private static volatile android.widget.TextView sActiveInput;
    public static android.widget.TextView macapkActiveInput() {
        android.widget.TextView v = sActiveInput;
        if (v == null) return null;
        // Unity's soft input closes by setVisibility(GONE)/ViewGroup.removeView,
        // NOT hideSoftInputFromWindow, so honor the target only while it is
        // still visible AND attached -- otherwise host keys return to gameplay.
        if (v.getVisibility() != View.VISIBLE || v.getParent() == null) {
            sActiveInput = null;
            return null;
        }
        return v;
    }
    private static void macapkSetActiveInput(View view) {
        sActiveInput = (view instanceof android.widget.TextView)
            ? (android.widget.TextView) view : null;
    }

    // The IME BIND PATH (v1.0.425). Real Android, on startInput/restartInput,
    // calls the served view's onCreateInputConnection(EditorInfo) to obtain the
    // live InputConnection, then the IME drives edits through it. Compose's
    // BasicTextField has NO TextView -- its text input flows ENTIRELY through the
    // RecordingInputConnection that AndroidComposeView.onCreateInputConnection
    // returns. Our IMM never called it, so Compose fields could focus but never
    // receive typed text. Here we mirror ViewRootImpl: bind the connection on
    // show/restart, expose it to the key dispatcher (TouchDispatcher feeds host
    // keystrokes via commitText / deleteSurroundingText), and drop it on hide.
    private static volatile View sServedView;
    private static volatile android.view.inputmethod.InputConnection sActiveConnection;
    // AOSP's mCurrentEditorInfo: assigned in startInputInner for EVERY served
    // view, editor or not (a plain View simply returns a null InputConnection).
    // isActive()/isActive(View) key off it, so keep it as its own piece of state
    // rather than conflating "input started" with "an InputConnection exists".
    private static volatile android.view.inputmethod.EditorInfo sCurrentEditorInfo;

    /** Whether the APP has asked for the soft input to be showing.
     *
     *  toggleSoftInput used to read this off `sServedView != null ||
     *  sActiveInput != null`, which was a sound proxy only while those fields
     *  were written EXCLUSIVELY by showSoftInput/restartInput. Following focus
     *  (macapkCheckFocus below) also sets sServedView -- including for a
     *  non-editor -- so the proxy would report "already shown" for a screen
     *  where the app had never shown anything, and the FIRST
     *  toggleSoftInputFromWindow would take the HIDE branch. That is exactly
     *  F-Droid's search box (AppListActivity.onCreate does requestFocus() then
     *  toggleSoftInputFromWindow), i.e. the app this path was built for. Keep
     *  the two notions separate. */
    private static volatile boolean sSoftInputShown;

    /** The view an explicit hideSoftInputFromWindow was aimed at, so following
     *  focus does not immediately re-bind the thing the app just dismissed.
     *  Cleared as soon as focus moves elsewhere or the app shows input again.
     *  Without it, "hide the keyboard but leave the field focused" -- how a
     *  game returns keys to gameplay -- would be undone by the next query. */
    private static volatile View sHideSuppressed;

    private void macapkStartInput(View view) { macapkStartInput(view, true); }

    /** AOSP InputMethodManager.startInputInner: bind the served view's
     *  InputConnection.
     *
     *  @param appRequested true when the APP asked for input on this view
     *         (showSoftInput / restartInput). False when we are following
     *         FOCUS, which is the case AOSP gates on IS_TEXT_EDITOR -- see
     *         the gate below.
     */
    private void macapkStartInput(View view, boolean appRequested) {
        if (view == null) {
            sServedView = null; sActiveConnection = null; sCurrentEditorInfo = null;
            return;
        }
        try {
            // THE IS_TEXT_EDITOR GATE. ViewRootImpl.getStartInputFlags:
            //     startInputFlags |= StartInputFlags.VIEW_HAS_FOCUS;
            //     if (focusedView.onCheckIsTextEditor()) {
            //         startInputFlags |= StartInputFlags.IS_TEXT_EDITOR;
            //     }
            // and InputMethodManagerService.startInputOrWindowGainedFocusInternal
            // declines to bind/show the IME for a window whose focused view is
            // not a text editor. That gate is what keeps a GAME safe: a
            // GLSurfaceView/SurfaceView takes focus on nearly every engine
            // (libGDX's AndroidGraphics calls requestFocus on its
            // GLSurfaceView), and without this test following focus would bind
            // an IME to it and start routing typed characters away from the
            // engine's own dispatchKeyEvent.
            //
            // An APP-requested start is deliberately NOT gated: an app calling
            // showSoftInput on a view is the explicit request AOSP honours
            // regardless (SOFT_INPUT_STATE_ALWAYS_VISIBLE), and Unity's hidden
            // input EditText has always arrived that way.
            if (!appRequested && !view.onCheckIsTextEditor()) {
                sServedView = view; sActiveConnection = null; sCurrentEditorInfo = null;
                return;
            }
            android.view.inputmethod.EditorInfo ei = new android.view.inputmethod.EditorInfo();
            android.view.inputmethod.InputConnection ic = view.onCreateInputConnection(ei);
            if (ic == null) ic = macapkFallbackConnection(view, ei);
            sServedView = view;
            sActiveConnection = ic;   // still null for a plain View / non-editor
            sCurrentEditorInfo = ei;
            if (sTrace) {
                System.err.println("[Mac.apk.IME] startInput " + view.getClass().getName()
                    + " editor=" + safeIsEditor(view)
                    + " ic=" + (ic == null ? "null" : ic.getClass().getSimpleName())
                    + " imeOptions=0x" + Integer.toHexString(ei.imeOptions)
                    + (appRequested ? " (app)" : " (focus)"));
            }
        } catch (Throwable t) {
            sServedView = view; sActiveConnection = null; sCurrentEditorInfo = null;
        }
    }

    private static boolean safeIsEditor(View v) {
        try { return v.onCheckIsTextEditor(); } catch (Throwable t) { return false; }
    }

    /** A TextView-backed editor that returned no connection of its own.
     *
     *  AOSP's TextView.onCreateInputConnection fills the EditorInfo from the
     *  field and returns an EditableInputConnection; ours returns null
     *  (TextView.java:3281), which left isAcceptingText() permanently false and
     *  every InputConnection route into a legacy EditText dead. Supply both
     *  halves here so the IME contract holds. See
     *  MacApkEditorInputConnection's header for the removal condition -- this
     *  is unreachable the moment TextView returns a real connection. */
    private static android.view.inputmethod.InputConnection macapkFallbackConnection(
            View view, android.view.inputmethod.EditorInfo ei) {
        if (!(view instanceof android.widget.TextView)) return null;
        android.widget.TextView tv = (android.widget.TextView) view;
        if (!safeIsEditor(tv) || !tv.isEnabled()) return null;
        try {
            // AOSP TextView.onCreateInputConnection fills exactly these.
            ei.inputType = tv.getInputType();
            ei.imeOptions = tv.getImeOptions();
            ei.privateImeOptions = tv.getPrivateImeOptions();
            ei.actionLabel = tv.getImeActionLabel();
            ei.actionId = tv.getImeActionId();
            ei.hintText = tv.getHint();
            ei.fieldId = tv.getId();
            CharSequence t = tv.getText();
            ei.initialSelStart = ei.initialSelEnd = (t == null ? 0 : t.length());
            // AOSP: when a field declares no explicit action, a SINGLE-LINE
            // editor's Enter is still an action and a MULTI-LINE editor's is a
            // newline. TextView.getActionIdForEnterEvent() (:2514 here) already
            // encodes that rule; mirror its answer into imeOptions so
            // macapkImeActionForEnter -- which reads imeOptions and nothing
            // else -- reaches the same verdict.
            if ((ei.imeOptions & android.view.inputmethod.EditorInfo.IME_MASK_ACTION)
                    == android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED
                    && !tv.isSingleLine()) {
                ei.imeOptions |= android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION;
            }
        } catch (Throwable ignore) { }
        return new MacApkEditorInputConnection(tv);
    }

    // ---- following FOCUS (AOSP checkFocus / focusIn / focusOut) -------------
    //
    // On AOSP a tap that focuses an editor binds the IME to it. The chain is
    // View.onFocusChanged -> ViewRootImpl's ImeFocusController.onViewFocusChanged
    // -> InputMethodManager.focusIn(view) -> startInputInner, and separately
    // InputMethodManager.checkFocus() re-syncs whenever a public query is made:
    //
    //     public boolean isActive(View view) {
    //         checkFocus();
    //         synchronized (mH) {
    //             return hasServedByInputMethodLocked(view)
    //                     && mCurrentTextBoxAttribute != null;
    //         }
    //     }
    //     public boolean isAcceptingText() {
    //         checkFocus();
    //         return mServedInputConnectionWrapper != null;
    //     }
    //
    // Mac.apk had NEITHER path: sServedView / sActiveConnection / sCurrentEditorInfo
    // were written ONLY by showSoftInput and restartInput, so an editor focused
    // by a TAP was never bound. Measured 2026-08-25 on debug/imetest with a
    // plain EditText: focus GRANTED, then isActive=false anyActive=false
    // accepting=false.
    private static final ThreadLocal<Boolean> sInCheckFocus = new ThreadLocal<>();

    /** AOSP InputMethodManager.checkFocus(): if the focused view is not the
     *  served view, start input on it.
     *
     *  ONE DELIBERATE DEVIATION: a null focused view does NOT clear the served
     *  view here. AOSP can distinguish "nothing is focused" from "we could not
     *  find the tree", because it always has a ViewRootImpl; macapkFocusedView
     *  answers null for BOTH (no Activity yet, no window, a decor we did not
     *  build -- see macapkTraceFocused, which exists precisely because those
     *  look identical). Clearing on that answer would tear down a live Unity
     *  binding whenever the lookup merely failed. Detachment and hide already
     *  clear it (servedViewOrNull / hideSoftInputFromWindow). */
    public static void macapkCheckFocus() {
        if (Boolean.TRUE.equals(sInCheckFocus.get())) return;   // onCreateInputConnection re-entry
        sInCheckFocus.set(Boolean.TRUE);
        try {
            View focused = macapkFocusedView();
            if (focused == null) return;
            if (focused != sHideSuppressed) sHideSuppressed = null;  // focus moved on
            if (focused == sServedView) return;
            if (focused == sHideSuppressed) return;   // the app dismissed this one
            INSTANCE.macapkStartInput(focused, false /* following focus */);
        } catch (Throwable ignore) {
        } finally { sInCheckFocus.set(Boolean.FALSE); }
    }

    /** AOSP InputMethodManager.focusIn(View) -- the EAGER half, called from
     *  View.onFocusChanged when a view GAINS focus. Not yet wired: View.java is
     *  not this lane's file. Until it is, macapkCheckFocus() (called from every
     *  public query below, exactly as AOSP does) supplies the same binding one
     *  query later, which is before any keystroke can be routed. */
    public void focusIn(View view) { macapkOnViewFocusChanged(view, true); }
    public void focusOut(View view) { macapkOnViewFocusChanged(view, false); }

    /** The single entry point View.onFocusChanged should call:
     *      if (mAttachInfo != null) InputMethodManager.macapkOnViewFocusChanged(this, gainFocus);
     *  AOSP routes it through ImeFocusController.onViewFocusChanged. */
    public static void macapkOnViewFocusChanged(View view, boolean gainFocus) {
        if (view == null) return;
        if (gainFocus) {
            if (view != sServedView) INSTANCE.macapkStartInput(view, false);
        } else if (view == sServedView) {
            // AOSP focusOut does NOT finish input immediately (another view in
            // the same window usually gains focus in the same pass); it lets the
            // next checkFocus re-target. Clearing the connection here is safe
            // and simpler, and the next query re-binds whatever now has focus.
            sServedView = null; sActiveConnection = null; sCurrentEditorInfo = null;
        }
    }

    /** The served view, or null -- dropping it first if it has detached. Real
     *  Android clears the served view when its window loses focus; we have no
     *  window-focus plumbing, so detachment is the equivalent signal (the same
     *  staleness guard macapkActiveConnection already applies). */
    private static View servedViewOrNull() {
        View v = sServedView;
        if (v == null) return null;
        if (!v.isAttachedToWindow()) {
            sServedView = null; sActiveConnection = null; sCurrentEditorInfo = null;
            return null;
        }
        return v;
    }

    /** True while the editor the host keyboard types into is a PASSWORD field:
     *  the soft-input TextView target, the served view if it is a TextView, or
     *  the bound EditorInfo (a Compose / custom InputConnection editor has no
     *  TextView), by AOSP's own predicates -- TextView.isAnyPasswordInputType
     *  (TYPE_TEXT_VARIATION_PASSWORD / _VISIBLE_PASSWORD / _WEB_PASSWORD,
     *  TYPE_NUMBER_VARIATION_PASSWORD, android14-release TextView.java:7525-7546)
     *  or a PasswordTransformationMethod (:7516-7518).
     *
     *  Reads the fields raw, with none of macapkActiveInput's / macapkCheckFocus's
     *  clearing side effects, so asking cannot change where the key goes.
     *
     *  THREAD. These fields are written where the focus change is applied (the
     *  loop), so the one caller, the input recorder (com.macapk.loader.InputRecorder,
     *  which redacts on true), must ask on the thread that DELIVERS the key, after any
     *  queued focus change has run; asked earlier, from the AppKit thread, it would
     *  judge a key typed right after a tap on a password field against the previous
     *  editor. A target that is merely STALE after the field lost focus errs toward
     *  "password", but only once the delivering thread has seen the change. */
    public static boolean macapkPasswordTargetActive() {
        if (isPasswordView(sActiveInput) || isPasswordView(sServedView)) return true;
        final android.view.inputmethod.EditorInfo ei = sCurrentEditorInfo;
        if (ei == null) return false;
        final int variation = ei.inputType
            & (android.text.InputType.TYPE_MASK_CLASS | android.text.InputType.TYPE_MASK_VARIATION);
        return variation == (android.text.InputType.TYPE_CLASS_TEXT
                             | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
            || variation == (android.text.InputType.TYPE_CLASS_TEXT
                             | android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
            || variation == (android.text.InputType.TYPE_CLASS_TEXT
                             | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            || variation == (android.text.InputType.TYPE_CLASS_NUMBER
                             | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }
    private static boolean isPasswordView(View v) {
        if (!(v instanceof android.widget.TextView)) return false;
        final android.widget.TextView tv = (android.widget.TextView) v;
        return tv.isAnyPasswordInputType() || tv.macapkHasPasswordTransformationMethod();
    }

    /** The EditorInfo the served view filled in at startInput, or null.
     *  AOSP's InputMethodService reads exactly this through
     *  getCurrentInputEditorInfo(); an IME cannot decide what ENTER means
     *  without it. */
    public static android.view.inputmethod.EditorInfo macapkCurrentEditorInfo() {
        macapkCheckFocus();
        return servedViewOrNull() == null ? null : sCurrentEditorInfo;
    }

    /** What ENTER means for the currently-bound editor: the action id to hand
     *  {@link android.view.inputmethod.InputConnection#performEditorAction},
     *  or {@code IME_ACTION_NONE} for "this is a plain newline, insert it".
     *
     *  This is AOSP LatinIME's rule, and it is the authority because Mac.apk
     *  IS the IME -- the physical Mac keyboard is the only input method these
     *  guests have. Two sources, both quoted:
     *
     *  InputTypeUtils.getImeOptionsActionIdFromEditorInfo (LatinIME
     *  java/src/com/android/inputmethod/latin/utils/InputTypeUtils.java:107-116):
     *      if ((editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
     *          return EditorInfo.IME_ACTION_NONE;
     *      } else if (editorInfo.actionLabel != null) {
     *          return IME_ACTION_CUSTOM_LABEL;
     *      } else {
     *          return editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
     *      }
     *
     *  InputLogic (…/inputlogic/InputLogic.java:734-756), CODE_ENTER:
     *      if (IME_ACTION_CUSTOM_LABEL == imeOptionsActionId) performEditorAction(editorInfo.actionId);
     *      else if (IME_ACTION_NONE != imeOptionsActionId)     performEditorAction(imeOptionsActionId);
     *      else  <insert a carriage return>
     *
     *  Note the deliberate asymmetry LatinIME comments on at :744-749:
     *  IME_ACTION_UNSPECIFIED (0) still gets performEditorAction -- only
     *  IME_ACTION_NONE (1) means "there is no action". A Compose field that
     *  wants a literal newline sets IME_FLAG_NO_ENTER_ACTION, which lands on
     *  the NONE branch, so multi-line editors keep their newline.
     *
     *  Returns IME_ACTION_NONE when nothing is bound, so the caller falls
     *  through to ordinary key dispatch. */
    public static int macapkImeActionForEnter() {
        final android.view.inputmethod.EditorInfo ei = macapkCurrentEditorInfo();
        if (ei == null) return android.view.inputmethod.EditorInfo.IME_ACTION_NONE;
        if ((ei.imeOptions & android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0)
            return android.view.inputmethod.EditorInfo.IME_ACTION_NONE;
        if (ei.actionLabel != null) return ei.actionId;   // CUSTOM_LABEL branch
        return ei.imeOptions & android.view.inputmethod.EditorInfo.IME_MASK_ACTION;
    }

    /** The bound InputConnection for the focused editor, or null. Validated
     *  against the served view still being attached so stale connections from a
     *  dismissed field don't keep swallowing keys. */
    public static android.view.inputmethod.InputConnection macapkActiveConnection() {
        // AOSP's own pattern: every public query re-syncs the served view
        // against the focused view first (isAcceptingText -> checkFocus). This
        // is the IME-side read -- Mac.apk IS the IME, and TouchDispatcher is
        // its InputMethodService -- so it is the right place for the same call.
        macapkCheckFocus();
        android.view.inputmethod.InputConnection ic = sActiveConnection;
        View v = sServedView;
        if (ic == null) return null;
        if (v != null && !v.isAttachedToWindow()) {
            sActiveConnection = null; sServedView = null; return null;
        }
        return ic;
    }

    public boolean showSoftInput(View view, int flags) {
        if (sTrace) {
            System.err.println("[Mac.apk.IME] showSoftInput view="
                + (view == null ? "null" : view.getClass().getName()));
        }
        macapkSetActiveInput(view);
        macapkStartInput(view);
        if (view != null) view.requestFocus();
        sSoftInputShown = true;
        sHideSuppressed = null;
        return true;
    }
    public boolean showSoftInput(View view, int flags, android.os.ResultReceiver r) {
        return showSoftInput(view, flags);
    }
    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags) {
        macapkHideInput(); return true;
    }
    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags, android.os.ResultReceiver r) {
        macapkHideInput(); return true;
    }
    /** The view focus is on right now, whether or not an IME is bound to it.
     *  InsetsController.show(Type.ime()) needs this: a preceding hide() clears
     *  sActiveInput, so asking for the ACTIVE input after a hide answers null
     *  and the show becomes a no-op. */
    public static android.widget.TextView macapkShowTarget() {
        android.widget.TextView t = macapkActiveInput();
        if (t != null) return t;
        try {
            View f = macapkFocusedView();
            if (f instanceof android.widget.TextView) return (android.widget.TextView) f;
        } catch (Throwable ignored) { }
        return null;
    }

    /** Cancel a pending hide-suppression. An explicit show() is the app asking
     *  for the IME back, which outranks the "do not put it straight back"
     *  guard that the preceding hide() armed. Without this,
     *  hide(ime()) -> show(ime()) with focus unmoved left typing DEAD: the
     *  suppression blocked the lazy first-keystroke rebind and only
     *  showSoftInput cleared it -- which the show could not reach, because
     *  hide had already nulled the view it needed to pass. */
    public static void macapkClearHideSuppressed() { sHideSuppressed = null; }

    /** Package-visible to android.view so InsetsController.hide(Type.ime())
     *  can dismiss the IME the way AOSP routes it. */
    public static void macapkHideInput() {
        // Remember what was dismissed BEFORE clearing, so following focus does
        // not put it straight back (see sHideSuppressed).
        sHideSuppressed = sServedView != null ? sServedView : sActiveInput;
        sActiveInput = null; sActiveConnection = null; sServedView = null;
        sCurrentEditorInfo = null; sSoftInputShown = false;
    }
    /** AOSP toggleSoftInput: "If the input method is currently shown, hide it;
     *  otherwise show it for the currently focused view." (Deprecated at API 31
     *  in favour of WindowInsetsController, and still the call real apps make.)
     *
     *  These were EMPTY, which made them a silent no-op for the one thing an
     *  app uses them for -- and unlike a missing method they raise nothing, so
     *  the app looks like it opened a keyboard and simply never receives text.
     *  F-Droid's search box is exactly this: AppListActivity.onCreate does
     *  `searchInput.requestFocus()` then
     *  `InputMethodManager.toggleSoftInputFromWindow(...)`, never showSoftInput,
     *  so sActiveInput stayed null and every host keystroke fell through the
     *  text paths in TouchDispatcher.doDispatchKey to nothing. Read straight out
     *  of the app's own converted bytecode (debug/appclass.py fdroid --class
     *  org.fdroid.fdroid.views.apps.AppListActivity --method onCreate --calls),
     *  which is why this is the app's real behaviour and not an inference about
     *  what a search screen "probably" does.
     *
     *  "Currently shown" is sServedView/sActiveInput being set, which is the
     *  same state hideSoftInputFromWindow clears -- one notion of shown, so a
     *  toggle after a hide shows again rather than double-hiding.
     *
     *  The target is the focused view, found through the Activity's content
     *  root the way ViewRootImpl finds it. Nothing happens if no view has
     *  focus, which matches AOSP: with no focused editor there is nothing for
     *  the IME to attach to. */
    /** Trace helper for MACAPK_TRACE_IME (TouchDispatcher). Describes the
     *  currently focused view, which is what decides whether a keystroke has
     *  anywhere to go. */
    public static String macapkTraceFocused() {
        View f = macapkFocusedView();
        if (f != null) {
            return f.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(f));
        }
        // Distinguish "nothing is focused" from "we could not find the tree to
        // ask" -- they look identical at the call site and have opposite fixes.
        try {
            android.app.Activity a = com.macapk.loader.ActivityHost.peek();
            if (a == null) return "null(no-activity)";
            Object w = a.getWindow();
            if (w == null) return "null(no-window)";
            View root = a.getWindow().getDecorView();
            View content = a.findViewById(0x01020002);
            return "null(decor=" + (root == null ? "null" : root.getClass().getSimpleName())
                 + " content=" + (content == null ? "null" : content.getClass().getSimpleName()) + ")";
        } catch (Throwable t) { return "null(threw " + t.getClass().getSimpleName() + ")"; }
    }

    public void toggleSoftInput(int showFlags, int hideFlags) {
        if (sTrace) {
            System.err.println("[Mac.apk.IME] toggleSoftInput served="
                + (sServedView == null ? "null" : sServedView.getClass().getName())
                + " shown=" + sSoftInputShown
                + " activeInput=" + (sActiveInput == null ? "null" : "set")
                + " focused=" + macapkTraceFocused());
        }
        if (sSoftInputShown) { macapkHideInput(); return; }
        View focused = macapkFocusedView();
        if (focused != null) showSoftInput(focused, showFlags);
    }
    public void toggleSoftInputFromWindow(IBinder windowToken, int showFlags, int hideFlags) {
        toggleSoftInput(showFlags, hideFlags);
    }

    /** The view that currently holds focus, via the ViewGroup mFocused chain
     *  (ViewGroup.findFocus). ActivityHost is our ActivityThread analogue and is
     *  already the framework's way of reaching the running Activity (see
     *  android.app.LocaleManager). */
    private static View macapkFocusedView() {
        try {
            android.app.Activity a = com.macapk.loader.ActivityHost.peek();
            if (a == null) return null;
            View root = a.getWindow() != null ? a.getWindow().getDecorView() : null;
            View f = root != null ? root.findFocus() : null;
            if (f != null) return f;
            // 0x01020002 is android.R.id.content, spelled numerically because
            // this tree has no generated android.R (Activity.java:699 does the
            // same). AOSP's ViewRootImpl searches the decor; the content host
            // is the fallback for a window whose decor we did not build.
            View content = a.findViewById(0x01020002);
            return content != null ? content.findFocus() : null;
        } catch (Throwable ignore) { return null; }
    }
    public void restartInput(View view) { macapkStartInput(view); }

    // AOSP isActive(View)   = hasServedByInputMethodLocked(view) && mCurrentEditorInfo != null
    //      isActive()       = getServedViewLocked() != null && mCurrentEditorInfo != null
    //      isAcceptingText() = mServedInputConnection != null
    // These used to be hardcoded false even though this class already tracked
    // every piece of state they need, so the common defensive idiom
    // `if (imm.isActive(editText)) ...` (used by keyboard-aware widgets to decide
    // whether they currently own input) always took the wrong branch. Nothing
    // becomes true until the app itself started input on a view, so the pre-show
    // answers are unchanged.
    public boolean isActive(View view) {
        macapkCheckFocus();
        return view != null && servedViewOrNull() == view && sCurrentEditorInfo != null;
    }
    public boolean isActive() {
        macapkCheckFocus();
        return servedViewOrNull() != null && sCurrentEditorInfo != null;
    }
    public boolean isFullscreenMode() { return false; }
    public boolean isAcceptingText() { return macapkActiveConnection() != null; }   // checkFocus inside
    public void displayCompletions(View view, android.view.inputmethod.CompletionInfo[] completions) {}
    public void updateExtractedText(View view, int token, android.view.inputmethod.ExtractedText text) {}
    public void updateSelection(View view, int selStart, int selEnd, int candidatesStart, int candidatesEnd) {}
    public void viewClicked(View view) {}
    public void updateCursorAnchorInfo(View view, android.view.inputmethod.CursorAnchorInfo info) {}
    public void sendAppPrivateCommand(View view, String action, android.os.Bundle data) {}

    // IME enumeration (API 3+). Mac.apk exposes no installable IMEs -- text is
    // entered through the host (Mac) keyboard, not an in-app soft keyboard -- so
    // these honestly return empty lists, the same shape a device with all IMEs
    // disabled returns. Callers iterate/size-check the list; none should NPE.
    public java.util.List<InputMethodInfo> getInputMethodList() {
        return new java.util.ArrayList<>();
    }
    public java.util.List<InputMethodInfo> getEnabledInputMethodList() {
        return new java.util.ArrayList<>();
    }
    public java.util.List<android.view.inputmethod.InputMethodSubtype> getEnabledInputMethodSubtypeList(
            InputMethodInfo imi, boolean allowsImplicitlySelectedSubtypes) {
        return new java.util.ArrayList<>();
    }
    public InputMethodSubtype getCurrentInputMethodSubtype() { return null; }
    public boolean setCurrentInputMethodSubtype(InputMethodSubtype subtype) { return false; }
    /** AOSP: an undocumented passthrough to
     *  IInputMethodManager.getLastInputMethodSubtype() -- the subtype that was
     *  active the last time the user switched IMEs. Mac.apk exposes no
     *  installable IMEs and never switches between them (see class comment),
     *  so there has never been a "last" one; null is the same honest answer
     *  getCurrentInputMethodSubtype() already gives for the identical reason. */
    public InputMethodSubtype getLastInputMethodSubtype() { return null; }

    /** AOSP stylus handwriting delegation (API 34). No stylus/IME handwriting on
     *  Mac.apk, so preparation is a no-op and acceptance reports "not handled". */
    public void prepareStylusHandwritingDelegation(android.view.View delegatorView) { }
    public boolean acceptStylusHandwritingDelegation(android.view.View delegateView) { return false; }

    public void dispatchKeyEventFromInputMethod(android.view.View targetView, android.view.KeyEvent event) { }
    public void startStylusHandwriting(android.view.View view) { }

    public void showSoftInputFromInputMethod(android.os.IBinder token, int flags) { }

    // ======================================================================
    //  AOSP API-36 surface completion (Mac.apk API-gap lane, android.view).
    //
    //  Mac.apk installs NO input methods: text reaches the focused view straight
    //  from the Cocoa key handler, and getInputMethodList() / getEnabledInputMethodList()
    //  above already report an empty list. Every answer below is therefore the
    //  one AOSP gives on a device where the operation names an IME that is not
    //  installed or not currently bound -- a real, reachable state on Android,
    //  not a stand-in. Nothing here claims a capability we do not have.
    // ======================================================================

    /** AOSP InputMethodManager.getCurrentInputMethodInfo() (API 36): the IME
     *  currently bound to this client. Nothing is ever bound here, and AOSP
     *  returns null when there is no current IME. */
    public InputMethodInfo getCurrentInputMethodInfo() { return null; }

    /** AOSP InputMethodManager.getShortcutInputMethodsAndSubtypes(): the
     *  IME/subtype pairs registered as shortcuts (the voice-input key).
     *  @NonNull, and empty when no IME registered one. */
    public java.util.Map<InputMethodInfo, java.util.List<InputMethodSubtype>>
            getShortcutInputMethodsAndSubtypes() {
        return java.util.Collections.emptyMap();
    }

    /** AOSP InputMethodManager.setAdditionalInputMethodSubtypes(String,
     *  InputMethodSubtype[]): register extra subtypes for an installed IME.
     *  With no IME by that id installed, AOSP's own body finds nothing to update
     *  and returns without effect. */
    public void setAdditionalInputMethodSubtypes(String imiId, InputMethodSubtype[] subtypes) {}

    /** AOSP InputMethodManager.setExplicitlyEnabledInputMethodSubtypes(String,
     *  int[]) (API 36): same shape, same "no such IME" outcome. */
    public void setExplicitlyEnabledInputMethodSubtypes(String imeId, int[] subtypeHashCodes) {}

    /** AOSP InputMethodManager.showInputMethodPicker(): open the system IME
     *  chooser. There is no IME to choose between and no system UI to open, so
     *  nothing is shown -- the same as on a device with a single enabled IME,
     *  where the platform declines to show the picker. */
    public void showInputMethodPicker() {}

    /** AOSP InputMethodManager.showInputMethodAndSubtypeEnabler(String):
     *  open Settings' "enable subtypes" screen for one IME. Mac.apk has no
     *  Settings app to launch, so nothing opens. */
    public void showInputMethodAndSubtypeEnabler(String imiId) {}

    /** AOSP InputMethodManager.isInputMethodSuppressingSpellChecker(): whether
     *  the CURRENT IME asked the platform to stand down on spell checking. No
     *  IME is bound, so nothing is suppressing it. */
    public boolean isInputMethodSuppressingSpellChecker() { return false; }

    /** AOSP InputMethodManager.isStylusHandwritingAvailable() /
     *  isConnectionlessStylusHandwritingAvailable() (API 33/35): true only when
     *  the current IME supports stylus handwriting. There is no current IME, and
     *  macOS delivers no stylus source through the Cocoa bridge either, so both
     *  halves of AOSP's own test fail. */
    public boolean isStylusHandwritingAvailable() { return false; }
    public boolean isConnectionlessStylusHandwritingAvailable() { return false; }

    /** AOSP InputMethodManager.prepareStylusHandwritingDelegation(View, String):
     *  the two-argument (delegate package) form of the method already above.
     *  No stylus source exists, so there is no in-progress handwriting to
     *  delegate. */
    public void prepareStylusHandwritingDelegation(android.view.View delegatorView,
            String delegatePackageName) {}

    /** AOSP InputMethodManager.acceptStylusHandwritingDelegation(...) -- the
     *  three published overloads. AOSP reports FALSE (or, for the callback
     *  forms, DELEGATION_RESULT_FAILED) when no delegation is pending, which is
     *  always the case here. The callback overloads ALWAYS answer, so a caller
     *  never waits forever. */
    public boolean acceptStylusHandwritingDelegation(android.view.View delegateView,
            String delegatorPackageName) {
        return false;
    }
    public void acceptStylusHandwritingDelegation(android.view.View delegateView,
            String delegatorPackageName, java.util.concurrent.Executor executor,
            java.util.function.IntConsumer callback) {
        acceptStylusHandwritingDelegation(delegateView, delegatorPackageName, 0,
                executor, callback);
    }
    public void acceptStylusHandwritingDelegation(android.view.View delegateView,
            String delegatorPackageName, int flags, java.util.concurrent.Executor executor,
            java.util.function.IntConsumer callback) {
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(callback);
        // AOSP InputMethodManager.HANDWRITING_DELEGATE_RESULT_FAILED == 0.
        executor.execute(() -> callback.accept(0));
    }

    /** AOSP InputMethodManager.startConnectionlessStylusHandwriting(...) and its
     *  two delegation variants (API 35). AOSP reports the error through the
     *  callback rather than throwing; with no stylus and no IME, the error is
     *  "unsupported", which is
     *  ConnectionlessHandwritingCallback.CONNECTIONLESS_HANDWRITING_ERROR_UNSUPPORTED. */
    public void startConnectionlessStylusHandwriting(android.view.View delegatorView,
            CursorAnchorInfo cursorAnchorInfo, java.util.concurrent.Executor executor,
            ConnectionlessHandwritingCallback callback) {
        macapkReportHandwritingUnsupported(executor, callback);
    }
    public void startConnectionlessStylusHandwritingForDelegation(
            android.view.View delegatorView, CursorAnchorInfo cursorAnchorInfo,
            java.util.concurrent.Executor executor,
            ConnectionlessHandwritingCallback callback) {
        macapkReportHandwritingUnsupported(executor, callback);
    }
    public void startConnectionlessStylusHandwritingForDelegation(
            android.view.View delegatorView, CursorAnchorInfo cursorAnchorInfo,
            String delegatePackageName, java.util.concurrent.Executor executor,
            ConnectionlessHandwritingCallback callback) {
        macapkReportHandwritingUnsupported(executor, callback);
    }
    private static void macapkReportHandwritingUnsupported(
            java.util.concurrent.Executor executor, ConnectionlessHandwritingCallback callback) {
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(callback);
        executor.execute(() -> callback.onError(
                ConnectionlessHandwritingCallback.CONNECTIONLESS_HANDWRITING_ERROR_UNSUPPORTED));
    }

    /** AOSP InputMethodManager.invalidateInput(View) (API 33): tell the current
     *  IME the editor's state changed wholesale. Nothing is bound, so there is
     *  nobody to tell -- AOSP's own early-out when the view is not the served
     *  view. */
    public void invalidateInput(android.view.View view) {}

    /** AOSP InputMethodManager.updateCursor(View, int, int, int, int),
     *  deprecated at API 21 in favour of updateCursorAnchorInfo (already above).
     *  Only an IME that called setWatchingCursor ever receives it, and there is
     *  none. */
    @Deprecated
    public void updateCursor(android.view.View view, int left, int top, int right, int bottom) {}

    /** AOSP InputMethodManager.isWatchingCursor(View), deprecated since API 3
     *  and documented as always returning false on modern platforms. */
    @Deprecated
    public boolean isWatchingCursor(android.view.View view) { return false; }

    // ---- the IME-token operations (an IME calls these on ITSELF) -------------
    // Every one takes the IME's own window token, which the platform hands only
    // to a bound InputMethodService. No IME runs here, so no caller can hold a
    // valid token and AOSP's own "unknown token" outcome -- reject / no-op -- is
    // what these produce. Deprecated on AOSP too (API 28+), in favour of the
    // InputMethodService.Switching* APIs.
    @Deprecated
    public void showStatusIcon(IBinder imeToken, String packageName, int iconId) {}
    @Deprecated
    public void hideStatusIcon(IBinder imeToken) {}
    @Deprecated
    public void setInputMethod(IBinder token, String id) {}
    @Deprecated
    public void setInputMethodAndSubtype(IBinder token, String id, InputMethodSubtype subtype) {}
    @Deprecated
    public void hideSoftInputFromInputMethod(IBinder token, int flags) {}
    @Deprecated
    public boolean switchToLastInputMethod(IBinder imeToken) { return false; }
    @Deprecated
    public boolean switchToNextInputMethod(IBinder imeToken, boolean onlyCurrentIme) {
        return false;
    }
    @Deprecated
    public boolean shouldOfferSwitchingToNextInputMethod(IBinder imeToken) { return false; }
}
