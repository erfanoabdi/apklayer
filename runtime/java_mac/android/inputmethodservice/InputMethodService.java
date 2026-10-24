package android.inputmethodservice;

import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.time.Duration;

/** The standard soft-keyboard base class. Apps subclass it and override
 *  onCreateInputView / onStartInputView / onKeyDown to draw and drive a
 *  keyboard.
 *
 *  ⚠ STRUCTURAL LIMIT, stated plainly rather than papered over: Mac.apk runs
 *  ONE app and has no system process, so an APK that IS an input method is
 *  never bound and never displayed. Nothing here fabricates a window, an
 *  InputMethodManagerService, or a privileged-operations channel. The three
 *  method families that need one -- showWindow/hideWindow, requestShowSelf/
 *  requestHideSelf, and switchInputMethod/switchToNext -- therefore move only
 *  the state THIS object owns and are documented individually below.
 *
 *  Everything that is pure in-process logic in AOSP is faithful here, because
 *  none of it needs a system: getTextForImeAction's action mapping, sendKeyChar's
 *  digit/enter routing, sendDefaultEditorAction's imeOptions test,
 *  onEvaluateFullscreenMode's orientation+flag test, onShowInputRequested's
 *  gating, onFinishInput/onFinishInputView's finishComposingText, the extract-UI
 *  visibility rules, and the whole InputMethodImpl/InputMethodSessionImpl
 *  dispatch chain. That chain matters most: doStartInput/doFinishInput are what
 *  turn a bound editor into onStartInput/onStartInputView callbacks, so a
 *  bundled IME is drivable from its own InputMethodImpl even with no system on
 *  the other end.
 *
 *  API 3. Source: frameworks/base
 *  core/java/android/inputmethodservice/InputMethodService.java
 *  (android16-release). */
public class InputMethodService extends AbstractInputMethodService {

    /** The back button will close the IME window if it is visible (default). */
    public static final int BACK_DISPOSITION_DEFAULT = 0;
    /** @deprecated at API 29 -- no longer honoured by the system. */
    @Deprecated
    public static final int BACK_DISPOSITION_WILL_NOT_DISMISS = 1;
    /** @deprecated at API 29 -- no longer honoured by the system. */
    @Deprecated
    public static final int BACK_DISPOSITION_WILL_DISMISS = 2;
    /** The IME does not consume the back key at all. */
    public static final int BACK_DISPOSITION_ADJUST_NOTHING = 3;

    // AOSP InputMethodService.java: BACK_DISPOSITION_MIN/MAX bound the value
    // setBackDisposition() will accept.
    private static final int BACK_DISPOSITION_MIN = BACK_DISPOSITION_DEFAULT;
    private static final int BACK_DISPOSITION_MAX = BACK_DISPOSITION_ADJUST_NOTHING;

    // AOSP InputMethodService.java:379,384 -- the stylus handwriting idle
    // timeout and its ceiling (10s, and 10x that as the maximum a caller may
    // set).
    private static final long STYLUS_HANDWRITING_IDLE_TIMEOUT_MS = 10000;
    private static final long STYLUS_HANDWRITING_IDLE_TIMEOUT_MAX_MS =
            STYLUS_HANDWRITING_IDLE_TIMEOUT_MS * 10;

    private LayoutInflater mInflater;

    private InputBinding mInputBinding;
    private InputConnection mInputConnection;
    private InputConnection mStartedInputConnection;
    private EditorInfo mInputEditorInfo;

    private boolean mInputStarted;
    private boolean mInputViewStarted;
    private boolean mCandidatesViewStarted;

    private boolean mShowInputRequested;
    private boolean mLastShowInputRequested;
    private boolean mDecorViewVisible;
    private boolean mIsInputViewShown;
    private boolean mIsFullscreen;
    private boolean mFullscreenApplied;
    private boolean mExtractViewHidden;
    private int mCandidatesVisibility = View.GONE;

    private int mBackDisposition = BACK_DISPOSITION_DEFAULT;
    private int mStatusIcon;

    private View mInputView;
    private View mCandidatesView;
    private View mExtractView;
    private CompletionInfo[] mCurCompletions;
    private ExtractEditText mExtractEditText;
    private ExtractedText mExtractedText;
    private int mExtractedToken;

    private long mStylusHwSessionsTimeout = STYLUS_HANDWRITING_IDLE_TIMEOUT_MS;
    private Region mLastHandwritingRegion;

    public InputMethodService() { super(); }

    // ---------------------------------------------------------------------
    // The InputMethod / InputMethodSession transports.
    //
    // These two inner classes are the whole reason the rest of this file is
    // worth writing: they are how an editor's state reaches the IME's
    // callbacks, and none of that plumbing needs a system service. AOSP's
    // versions carry Binder tokens, tracing and inline-suggestion controllers;
    // the parts stripped here are exactly the parts that talk to the system.
    // ---------------------------------------------------------------------

    /** AOSP InputMethodService.InputMethodImpl. */
    public class InputMethodImpl extends AbstractInputMethodImpl {
        public InputMethodImpl() { }

        /** AOSP stores the token and hands it to the soft-input window. There
         *  is no window token in a single-app host, so this records nothing;
         *  it is kept because the interface declares it and an IME subclass
         *  may chain to super. */
        @Override public void attachToken(android.os.IBinder token) { }

        /** AOSP: caches the binding + its connection, reports fullscreen mode,
         *  initializes, then calls onBindInput(). The reporting and the window
         *  initialization are the system-facing halves; the caching and the
         *  callback are not, and both are faithful. */
        @Override public void bindInput(InputBinding binding) {
            mInputBinding = binding;
            mInputConnection = binding != null ? binding.getConnection() : null;
            onBindInput();
        }

        @Override public void unbindInput() {
            onUnbindInput();
            mInputBinding = null;
            mInputConnection = null;
        }

        @Override public void startInput(InputConnection ic, EditorInfo editorInfo) {
            doStartInput(ic, editorInfo, false);
        }

        @Override public void restartInput(InputConnection ic, EditorInfo editorInfo) {
            doStartInput(ic, editorInfo, true);
        }

        /** AOSP's showSoftInput flips mShowInputRequested and shows the window;
         *  the window is the system-facing half. onShowInputRequested() is the
         *  IME's own decision point and is honoured. */
        @Override public void showSoftInput(int flags, ResultReceiver resultReceiver) {
            if (onShowInputRequested(flags, false)) {
                showWindow(true);
            }
            if (resultReceiver != null) {
                resultReceiver.send(InputMethodManager.RESULT_UNCHANGED_SHOWN, null);
            }
        }

        @Override public void hideSoftInput(int flags, ResultReceiver resultReceiver) {
            hideWindow();
            if (resultReceiver != null) {
                resultReceiver.send(InputMethodManager.RESULT_UNCHANGED_HIDDEN, null);
            }
        }

        /** AOSP: `dispatchOnCurrentInputMethodSubtypeChanged(subtype)`, which
         *  caches the subtype and then invokes the protected callback. */
        @Override public void changeInputMethodSubtype(InputMethodSubtype subtype) {
            onCurrentInputMethodSubtypeChanged(subtype);
        }
    }

    /** AOSP InputMethodService.InputMethodSessionImpl. Every entry point is
     *  gated on isEnabled() exactly as AOSP gates it. */
    public class InputMethodSessionImpl extends AbstractInputMethodSessionImpl {
        public InputMethodSessionImpl() { }

        @Override public void finishInput() {
            if (!isEnabled()) return;
            doFinishInput();
        }

        @Override public void displayCompletions(CompletionInfo[] completions) {
            if (!isEnabled()) return;
            mCurCompletions = completions;
            onDisplayCompletions(completions);
        }

        @Override public void updateExtractedText(int token, ExtractedText text) {
            if (!isEnabled()) return;
            onUpdateExtractedText(token, text);
        }

        @Override public void updateSelection(int oldSelStart, int oldSelEnd,
                int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
            if (!isEnabled()) return;
            InputMethodService.this.onUpdateSelection(oldSelStart, oldSelEnd,
                    newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        }

        @Override public void viewClicked(boolean focusChanged) {
            if (!isEnabled()) return;
            InputMethodService.this.onViewClicked(focusChanged);
        }

        @Override public void updateCursor(Rect newCursor) {
            if (!isEnabled()) return;
            InputMethodService.this.onUpdateCursor(newCursor);
        }

        @Override public void updateCursorAnchorInfo(CursorAnchorInfo info) {
            if (!isEnabled()) return;
            InputMethodService.this.onUpdateCursorAnchorInfo(info);
        }

        @Override public void appPrivateCommand(String action, Bundle data) {
            if (!isEnabled()) return;
            InputMethodService.this.onAppPrivateCommand(action, data);
        }

        /** @deprecated at API 28. AOSP routes to the hidden
         *  onToggleSoftInput(showFlags, hideFlags), which shows the window when
         *  it is hidden and hides it when it is shown. */
        @Override @Deprecated public void toggleSoftInput(int showFlags, int hideFlags) {
            if (isInputViewShown()) {
                hideWindow();
            } else if (onShowInputRequested(showFlags, false)) {
                showWindow(true);
            }
        }
    }

    /** AOSP: `return new InputMethodImpl();` */
    @Override public AbstractInputMethodImpl onCreateInputMethodInterface() {
        return new InputMethodImpl();
    }

    /** AOSP: `return new InputMethodSessionImpl();` */
    @Override public AbstractInputMethodSessionImpl onCreateInputMethodSessionInterface() {
        return new InputMethodSessionImpl();
    }

    /** AOSP InputMethodService.Insets -- what the IME reports as the region the
     *  application below must not draw under. Public mutable fields, exactly as
     *  AOSP declares them; touchableRegion is final and pre-allocated. */
    public static final class Insets {
        /** The application's content is fully behind the IME's window frame. */
        public static final int TOUCHABLE_INSETS_FRAME = 0;
        /** Only the IME's content area is touchable. */
        public static final int TOUCHABLE_INSETS_CONTENT = 1;
        /** Only the visible portion of the IME is touchable. */
        public static final int TOUCHABLE_INSETS_VISIBLE = 2;
        /** touchableRegion below defines the touchable area. */
        public static final int TOUCHABLE_INSETS_REGION = 3;

        public int contentTopInsets;
        public int visibleTopInsets;
        public int touchableInsets;
        public final Region touchableRegion = new Region();

        public Insets() { }
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    public void onInitializeInterface() { }
    public void onBindInput() { }
    public void onUnbindInput() { }

    public void onStartInput(EditorInfo attribute, boolean restarting) { }

    /** AOSP finishes any composing text on the connection. */
    public void onFinishInput() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.finishComposingText();
        }
    }

    public void onStartInputView(EditorInfo info, boolean restarting) { }

    /** AOSP: finish composing text unless the whole input is going away
     *  (doFinishInput will handle that case through onFinishInput). */
    public void onFinishInputView(boolean finishingInput) {
        if (!finishingInput) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    public void onStartCandidatesView(EditorInfo editorInfo, boolean restarting) { }

    /** AOSP mirrors onFinishInputView. */
    public void onFinishCandidatesView(boolean finishingInput) {
        if (!finishingInput) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /** AOSP doStartInput(): finish a previous input if this is not a restart,
     *  latch the new connection/editor, fire onStartInput, and -- only if the
     *  IME's decor is up -- fire onStartInputView or onStartCandidatesView.
     *  Package-visible in AOSP; the session impl above is its only caller. */
    void doStartInput(InputConnection ic, EditorInfo editorInfo, boolean restarting) {
        if (!restarting && mInputStarted) {
            doFinishInput();
        }
        mInputStarted = true;
        mStartedInputConnection = ic;
        mInputEditorInfo = editorInfo;
        onStartInput(editorInfo, restarting);
        if (mDecorViewVisible) {
            if (mShowInputRequested) {
                mInputViewStarted = true;
                onStartInputView(mInputEditorInfo, restarting);
            } else if (mCandidatesVisibility == View.VISIBLE) {
                mCandidatesViewStarted = true;
                onStartCandidatesView(mInputEditorInfo, restarting);
            }
        }
    }

    /** AOSP doFinishInput(): tear the views down first (finishingInput=true, so
     *  they skip their own finishComposingText), then onFinishInput, then clear
     *  the latched state. */
    void doFinishInput() {
        finishViews(true);
        if (mInputStarted) {
            onFinishInput();
        }
        mInputStarted = false;
        mStartedInputConnection = null;
        mCurCompletions = null;
    }

    /** AOSP finishViews(): onFinishInputView / onFinishCandidatesView fire at
     *  most once each per started view. */
    private void finishViews(boolean finishingInput) {
        if (mInputViewStarted) {
            onFinishInputView(finishingInput);
        } else if (mCandidatesViewStarted) {
            onFinishCandidatesView(finishingInput);
        }
        mInputViewStarted = false;
        mCandidatesViewStarted = false;
    }

    // ---------------------------------------------------------------------
    // Views
    // ---------------------------------------------------------------------

    public View onCreateInputView() { return null; }
    public View onCreateCandidatesView() { return null; }

    /** AOSP inflates com.android.internal.R.layout.input_method_extract_view.
     *  That layout is a platform-internal resource this tree does not ship, so
     *  there is nothing to inflate; AOSP's own contract already allows null
     *  here (setExtractView(null) is a supported state and clears the extract
     *  widgets), so returning null is a legal AOSP state rather than a lie. */
    public View onCreateExtractTextView() { return null; }

    /** AOSP adds the view to mInputFrame and caches it. There is no frame here,
     *  so only the cache survives -- getCurrentInputView-style access from the
     *  app still sees what it set. */
    public void setInputView(View view) { mInputView = view; }

    public void setCandidatesView(View view) { mCandidatesView = view; }

    /** AOSP caches the view and resolves the extract widgets out of it by the
     *  internal ids inputExtractEditText / inputExtractAction /
     *  inputExtractAccessories, then starts extracting text.
     *
     *  Mac.apk ships no com.android.internal resources, so the id lookup cannot
     *  work -- but the OBJECT AOSP is looking for is identified just as well by
     *  its type, and finding it that way keeps onUpdateExtractedText,
     *  onUpdateSelection and onExtractedTextClicked functional for an IME that
     *  supplies its own extract layout. The action/accessory widgets have no
     *  distinguishing type, so they stay unresolved, which is the same state
     *  AOSP is in when the layout omits them (its own null guards cover it).
     *
     *  Passing null clears the editor, exactly as AOSP's else-branch does. */
    public void setExtractView(View view) {
        mExtractView = view;
        if (view != null) {
            mExtractEditText = findExtractEditText(view);
            if (mExtractEditText != null) {
                mExtractEditText.setIME(this);
            }
        } else {
            mExtractEditText = null;
        }
    }

    /** Depth-first scan for the ExtractEditText inside an IME-supplied extract
     *  layout. Stands in for AOSP's findViewById(inputExtractEditText). */
    private static ExtractEditText findExtractEditText(View v) {
        if (v instanceof ExtractEditText) {
            return (ExtractEditText) v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View child = g.getChildAt(i);
                if (child == null) continue;
                ExtractEditText found = findExtractEditText(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** AOSP updateCandidatesVisibility(shown) then, if the input view was never
     *  requested, shows or hides the whole window to match. */
    public void setCandidatesViewShown(boolean shown) {
        updateCandidatesVisibility(shown);
        if (!mShowInputRequested && mDecorViewVisible != shown) {
            if (shown) {
                showWindow(false);
            } else {
                hideWindow();
            }
        }
    }

    /** AOSP updateCandidatesVisibility(): VISIBLE when shown, otherwise the
     *  hidden visibility the IME asked for. */
    private void updateCandidatesVisibility(boolean shown) {
        int vis = shown ? View.VISIBLE : getCandidatesHiddenVisibility();
        if (mCandidatesVisibility != vis) {
            mCandidatesVisibility = vis;
            if (mCandidatesView != null) {
                mCandidatesView.setVisibility(vis);
            }
        }
    }

    /** AOSP: GONE while the extract view is up (the candidates would be
     *  redundant), INVISIBLE otherwise so the layout still reserves its space. */
    public int getCandidatesHiddenVisibility() {
        return isExtractViewShown() ? View.GONE : View.INVISIBLE;
    }

    /** AOSP flips mExtractViewHidden only on a real change, then re-evaluates
     *  the extract frame's visibility. */
    public void setExtractViewShown(boolean shown) {
        if (mExtractViewHidden == shown) {
            mExtractViewHidden = !shown;
            updateExtractFrameVisibility();
        }
    }

    /** AOSP updateExtractFrameVisibility(): in fullscreen the extract frame is
     *  visible unless explicitly hidden, and the candidates visibility follows. */
    private void updateExtractFrameVisibility() {
        if (isFullscreenMode()) {
            updateCandidatesVisibility(mCandidatesVisibility == View.VISIBLE);
        }
        if (mExtractView != null) {
            mExtractView.setVisibility(isExtractViewShown() ? View.VISIBLE : View.GONE);
        }
    }

    /** AOSP: `return mIsFullscreen && !mExtractViewHidden;` */
    public boolean isExtractViewShown() {
        return mIsFullscreen && !mExtractViewHidden;
    }

    /** AOSP evaluates this against the hard-keyboard configuration: show the
     *  soft input view when there is no hardware keyboard, or when there is one
     *  but it is folded away. Mac.apk always has the host's physical keyboard,
     *  and Configuration.keyboard/hardKeyboardHidden carry that -- so this
     *  answers from the same two fields AOSP reads and is honest either way. */
    public boolean onEvaluateInputViewShown() {
        Configuration config = getResources() != null
                ? getResources().getConfiguration() : null;
        if (config == null) {
            return true;
        }
        return config.keyboard == Configuration.KEYBOARD_NOKEYS
                || config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES;
    }

    /** AOSP: fullscreen only in landscape, and never when the editor set
     *  IME_FLAG_NO_FULLSCREEN. */
    public boolean onEvaluateFullscreenMode() {
        Configuration config = getResources() != null
                ? getResources().getConfiguration() : null;
        if (config == null || config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return false;
        }
        if (mInputEditorInfo != null
                && (mInputEditorInfo.imeOptions & EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0) {
            return false;
        }
        return true;
    }

    public boolean isInputViewShown() { return mDecorViewVisible; }
    public boolean isFullscreenMode() { return mIsFullscreen; }
    public boolean isShowInputRequested() { return mShowInputRequested; }

    /** AOSP recomputes whether the input view should be up and, on a change,
     *  toggles the frame and lazily creates the input view. The frame is the
     *  system-facing half; the lazy onCreateInputView + setInputView is the
     *  app-facing half and is what makes an IME's view actually get built. */
    public void updateInputViewShown() {
        boolean isShown = mShowInputRequested && onEvaluateInputViewShown();
        if (mIsInputViewShown != isShown && mDecorViewVisible) {
            mIsInputViewShown = isShown;
            if (mInputView == null) {
                onInitializeInterface();
                View v = onCreateInputView();
                if (v != null) {
                    setInputView(v);
                }
            }
        }
    }

    /** AOSP recomputes fullscreen state, applies the extract UI when entering
     *  it, and reports the new window configuration through onConfigureWindow.
     *  The LinearLayout weight juggling AOSP does is on the decor it owns; the
     *  state transitions and the two callbacks are what an IME observes. */
    public void updateFullscreenMode() {
        boolean isFullscreen = mShowInputRequested && onEvaluateFullscreenMode();
        boolean changed = mLastShowInputRequested != mShowInputRequested;
        if (mIsFullscreen != isFullscreen || !mFullscreenApplied) {
            changed = true;
            mIsFullscreen = isFullscreen;
            mFullscreenApplied = true;
            if (isFullscreen && mExtractView == null) {
                View v = onCreateExtractTextView();
                if (v != null) {
                    setExtractView(v);
                }
            }
            updateExtractFrameVisibility();
        }
        if (changed) {
            onConfigureWindow(getStylusHandwritingWindowInternal(), isFullscreen,
                    !mShowInputRequested);
            mLastShowInputRequested = mShowInputRequested;
        }
    }

    /** AOSP resizes the soft-input window to MATCH_PARENT height in fullscreen
     *  and WRAP_CONTENT otherwise. There is no soft-input window here, so the
     *  default body has nothing to resize; an IME that overrides this to react
     *  to the fullscreen transition still gets the call from
     *  updateFullscreenMode() above. */
    public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) { }

    /** AOSP fills the insets from the real frame/decor geometry. Without a
     *  decor there is no geometry to read, so this reports the AOSP defaults
     *  for a window that occupies nothing: zero content/visible insets,
     *  TOUCHABLE_INSETS_VISIBLE and an empty touchable region -- which is the
     *  same shape AOSP writes, with the measured values it cannot take. */
    public void onComputeInsets(Insets outInsets) {
        if (outInsets == null) return;
        outInsets.contentTopInsets = 0;
        outInsets.visibleTopInsets = 0;
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE;
        outInsets.touchableRegion.setEmpty();
    }

    // ---------------------------------------------------------------------
    // Window show / hide.
    //
    // AOSP's showWindow/hideWindow drive a real Dialog (SoftInputWindow) and
    // notify the system. Here they move only the state this object owns, which
    // is precisely the state every other method on this class reads --
    // isInputViewShown, isShowInputRequested, doStartInput's decor test. So the
    // object stays SELF-CONSISTENT: an IME driven through its own
    // InputMethodImpl sees the same callback sequence it would on a device,
    // minus the pixels.
    // ---------------------------------------------------------------------

    public void showWindow(boolean showInput) {
        mShowInputRequested = showInput || mShowInputRequested;
        boolean wasVisible = mDecorViewVisible;
        mDecorViewVisible = true;
        updateFullscreenMode();
        updateInputViewShown();
        if (!wasVisible) {
            onWindowShown();
        }
        if (mInputStarted && mShowInputRequested && !mInputViewStarted) {
            mInputViewStarted = true;
            onStartInputView(mInputEditorInfo, false);
        } else if (mInputStarted && !mShowInputRequested
                && mCandidatesVisibility == View.VISIBLE && !mCandidatesViewStarted) {
            mCandidatesViewStarted = true;
            onStartCandidatesView(mInputEditorInfo, false);
        }
    }

    public void hideWindow() {
        finishViews(false);
        boolean wasVisible = mDecorViewVisible;
        mDecorViewVisible = false;
        mIsInputViewShown = false;
        mShowInputRequested = false;
        if (wasVisible) {
            onWindowHidden();
        }
    }

    public void onWindowShown() { }
    public void onWindowHidden() { }

    /** AOSP asks the system to hide this IME. There is no system to ask, so the
     *  IME hides itself -- which is the observable half of what the system
     *  would have done. */
    public void requestHideSelf(int flags) { hideWindow(); }

    /** AOSP asks the system to show this IME; final, so an app cannot change
     *  the meaning. Same reasoning as requestHideSelf. */
    public final void requestShowSelf(int flags) {
        if (onShowInputRequested(flags, false)) {
            showWindow(true);
        }
    }

    /** AOSP: refuse if the IME itself says the input view should not show; for
     *  an IMPLICIT request also refuse in fullscreen-when-not-already-shown and
     *  when a hardware keyboard is present. The hardware-keyboard clause is the
     *  one that bites on this host: the Mac always has one. */
    public boolean onShowInputRequested(int flags, boolean configChange) {
        if (!onEvaluateInputViewShown()) {
            return false;
        }
        if ((flags & InputMethod.SHOW_EXPLICIT) == 0) {
            if (!configChange && onEvaluateFullscreenMode() && !isInputViewShown()) {
                return false;
            }
            Configuration config = getResources() != null
                    ? getResources().getConfiguration() : null;
            if (config != null && config.keyboard != Configuration.KEYBOARD_NOKEYS) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Editor-facing helpers -- pure in-process logic, faithful to AOSP.
    // ---------------------------------------------------------------------

    public EditorInfo getCurrentInputEditorInfo() { return mInputEditorInfo; }

    /** AOSP prefers the connection the current startInput() delivered, falling
     *  back to the one bindInput() cached. */
    public InputConnection getCurrentInputConnection() {
        InputConnection ic = mStartedInputConnection;
        if (ic != null) {
            return ic;
        }
        return mInputConnection;
    }

    public InputBinding getCurrentInputBinding() { return mInputBinding; }

    public boolean getCurrentInputStarted() { return mInputStarted; }

    public int getCurrentInputConnectionFlags() { return 0; }

    /** AOSP sends an ACTION_DOWN/ACTION_UP pair from the virtual keyboard
     *  device, flagged FLAG_SOFT_KEYBOARD|FLAG_KEEP_TOUCH_MODE so the editor
     *  does not leave touch mode. */
    public void sendDownUpKeyEvents(int keyEventCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long eventTime = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyEventCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        ic.sendKeyEvent(new KeyEvent(eventTime, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, keyEventCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    /** AOSP: newline goes through the editor's default action when it has one,
     *  digits are sent as key events so client-side TextWatchers see them, and
     *  everything else is committed as text. */
    public void sendKeyChar(char charCode) {
        switch (charCode) {
            case '\n':
                if (!sendDefaultEditorAction(true)) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                }
                break;
            default:
                if (charCode >= '0' && charCode <= '9') {
                    sendDownUpKeyEvents(charCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(String.valueOf(charCode), 1);
                    }
                }
                break;
        }
    }

    /** AOSP: perform the editor's action when it has one and (for an enter-key
     *  origin) has not opted out with IME_FLAG_NO_ENTER_ACTION. */
    public boolean sendDefaultEditorAction(boolean fromEnterKey) {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null
                && (!fromEnterKey
                        || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
                && (ei.imeOptions & EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_NONE) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.performEditorAction(ei.imeOptions & EditorInfo.IME_MASK_ACTION);
            }
            return true;
        }
        return false;
    }

    /** AOSP maps the action bits onto com.android.internal.R.string.ime_action_*.
     *  Those are platform-internal strings this tree does not ship, so the
     *  English labels AOSP's own values/strings.xml defines are returned
     *  directly. IME_ACTION_NONE returns null in AOSP and does so here. */
    public CharSequence getTextForImeAction(int imeOptions) {
        switch (imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_NONE:     return null;
            case EditorInfo.IME_ACTION_GO:       return "Go";
            case EditorInfo.IME_ACTION_SEARCH:   return "Search";
            case EditorInfo.IME_ACTION_SEND:     return "Send";
            case EditorInfo.IME_ACTION_NEXT:     return "Next";
            case EditorInfo.IME_ACTION_DONE:     return "Done";
            case EditorInfo.IME_ACTION_PREVIOUS: return "Prev";
            default:                             return "Execute";
        }
    }

    // ---------------------------------------------------------------------
    // Extract (fullscreen) UI callbacks
    // ---------------------------------------------------------------------

    /** AOSP hides the candidates strip when the extract editor is scrollable,
     *  so the two do not fight for the same vertical space. */
    public void onExtractedTextClicked() {
        if (mExtractEditText == null) {
            return;
        }
        if (mExtractEditText.hasVerticalScrollBar()) {
            setCandidatesViewShown(false);
        }
    }

    /** AOSP: same rule, but only for a VERTICAL movement. */
    public void onExtractedCursorMovement(int dx, int dy) {
        if (mExtractEditText == null || dy == 0) {
            return;
        }
        if (mExtractEditText.hasVerticalScrollBar()) {
            setCandidatesViewShown(false);
        }
    }

    /** AOSP pushes a selection made INSIDE the extract editor back to the real
     *  editor over the input connection. ExtractEditText calls this only when
     *  the change did not originate from the IME. */
    public void onExtractedSelectionChanged(int start, int end) {
        InputConnection conn = getCurrentInputConnection();
        if (conn != null) {
            conn.setSelection(start, end);
        }
    }

    /** AOSP forwards the context-menu id to the editor and always reports it
     *  handled. */
    public boolean onExtractTextContextMenuItem(int id) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.performContextMenuAction(id);
        }
        return true;
    }

    /** AOSP: no extract UI for a null input type or an editor that set
     *  IME_FLAG_NO_EXTRACT_UI. */
    public void onUpdateExtractingVisibility(EditorInfo ei) {
        if (ei == null
                || ei.inputType == android.text.InputType.TYPE_NULL
                || (ei.imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0) {
            setExtractViewShown(false);
            return;
        }
        setExtractViewShown(true);
    }

    /** AOSP populates the extract action button from the editor's action label
     *  or getTextForImeAction(). The accessory/action widgets are resolved by
     *  internal id, which setExtractView() cannot do here, so AOSP's own
     *  `if (mExtractAccessories == null) return;` early-out is what this
     *  reduces to -- the same branch a device takes for an extract layout that
     *  declares no action button. */
    public void onUpdateExtractingViews(EditorInfo ei) { }

    /** AOSP hides itself when the editor turns out to accept no input at all. */
    public void onExtractingInputChanged(EditorInfo ei) {
        if (ei != null && ei.inputType == android.text.InputType.TYPE_NULL) {
            requestHideSelf(InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /** AOSP pushes the text into the extract editor when the token matches. The
     *  latch is kept so a subclass that overrides onCreateExtractTextView() and
     *  reads the extracted text still observes the AOSP token semantics. */
    public void onUpdateExtractedText(int token, ExtractedText text) {
        if (mExtractedToken != token) {
            return;
        }
        if (text != null && mExtractEditText != null) {
            mExtractedText = text;
            mExtractEditText.setExtractedText(text);
        }
    }

    // ---------------------------------------------------------------------
    // Editor state callbacks -- empty in AOSP too.
    // ---------------------------------------------------------------------

    /** AOSP mirrors the application's selection into the extract editor,
     *  rebased onto the extracted text's start offset and clamped to its
     *  length, bracketed by start/finishInternalChanges so the write does not
     *  bounce straight back out as an onExtractedSelectionChanged. */
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
            int newSelEnd, int candidatesStart, int candidatesEnd) {
        final ExtractEditText eet = mExtractEditText;
        if (eet != null && isFullscreenMode() && mExtractedText != null) {
            final int off = mExtractedText.startOffset;
            eet.startInternalChanges();
            newSelStart -= off;
            newSelEnd -= off;
            final CharSequence cur = eet.getText();
            final int len = cur != null ? cur.length() : 0;
            if (newSelStart < 0) newSelStart = 0;
            else if (newSelStart > len) newSelStart = len;
            if (newSelEnd < 0) newSelEnd = 0;
            else if (newSelEnd > len) newSelEnd = len;
            eet.setSelection(newSelStart, newSelEnd);
            eet.finishInternalChanges();
        }
    }
    public void onDisplayCompletions(CompletionInfo[] completions) { }
    /** @deprecated at API 29. */
    @Deprecated public void onViewClicked(boolean focusChanged) { }
    /** @deprecated at API 29 -- use onUpdateCursorAnchorInfo. */
    @Deprecated public void onUpdateCursor(Rect newCursor) { }
    public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) { }
    public void onUpdateEditorToolType(int toolType) { }
    public void onAppPrivateCommand(String action, Bundle data) { }
    protected void onCurrentInputMethodSubtypeChanged(InputMethodSubtype newSubtype) { }
    public void onCustomImeSwitcherButtonRequestedVisible(boolean visible) { }

    // ---------------------------------------------------------------------
    // Key handling. AbstractInputMethodService implements KeyEvent.Callback,
    // so all four live here.
    // ---------------------------------------------------------------------

    /** AOSP consumes BACK to close its own window (starting tracking so the UP
     *  can complete the gesture) and otherwise falls through to arrow-key
     *  movement inside the extract editor. There is no extract editor here, so
     *  the movement branch has nothing to move; the BACK branch is real. */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (handleBack(false)) {
                event.startTracking();
                return true;
            }
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.isTracking() && !event.isCanceled()) {
                return handleBack(true);
            }
        }
        return false;
    }

    /** AOSP: `return false;` */
    public boolean onKeyLongPress(int keyCode, KeyEvent event) { return false; }

    /** AOSP: `return doMovementKey(keyCode, event, count);` -- movement inside
     *  the extract editor, which is absent here. */
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) { return false; }

    /** AOSP handleBack(): the IME consumes BACK only while its window is up and
     *  it has not declared BACK_DISPOSITION_ADJUST_NOTHING; on the UP half it
     *  actually dismisses. */
    private boolean handleBack(boolean doIt) {
        if (mBackDisposition == BACK_DISPOSITION_ADJUST_NOTHING) {
            return false;
        }
        if (mShowInputRequested) {
            if (doIt) requestHideSelf(0);
            return true;
        }
        if (mDecorViewVisible) {
            if (mCandidatesVisibility == View.VISIBLE) {
                if (doIt) setCandidatesViewShown(false);
            } else {
                if (doIt) requestHideSelf(0);
            }
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Status icon / back disposition / IME switching
    // ---------------------------------------------------------------------

    /** AOSP caches the icon and pushes it to the status bar over the privileged
     *  operations channel. There is no status bar to push to; the cache is what
     *  a subclass can still observe. */
    public void showStatusIcon(int iconResId) { mStatusIcon = iconResId; }

    public void hideStatusIcon() { mStatusIcon = 0; }

    public int getBackDisposition() { return mBackDisposition; }

    /** AOSP rejects an out-of-range value with a log line and no state change --
     *  reproduced, because an IME that reads getBackDisposition() back must see
     *  the rejection. */
    public void setBackDisposition(int disposition) {
        if (disposition == mBackDisposition) {
            return;
        }
        if (disposition > BACK_DISPOSITION_MAX || disposition < BACK_DISPOSITION_MIN) {
            return;
        }
        mBackDisposition = disposition;
    }

    /** AOSP asks the system to make `id` the current IME. Mac.apk installs no
     *  IME list, so there is no other IME to switch to and nothing to ask. */
    public void switchInputMethod(String id) { }

    /** Same, with an explicit subtype. Final in AOSP. */
    public final void switchInputMethod(String id, InputMethodSubtype subtype) { }

    /** AOSP asks the system for the next IME in the rotation. With exactly zero
     *  installed IMEs there is no next one, so this reports the failure AOSP
     *  reports when the switch cannot happen. */
    public final boolean switchToNextInputMethod(boolean onlyCurrentIme) { return false; }

    public final boolean switchToPreviousInputMethod() { return false; }

    /** AOSP asks whether the system would offer a switch. With no other IME
     *  installed the honest answer is no. */
    public final boolean shouldOfferSwitchingToNextInputMethod() { return false; }

    // ---------------------------------------------------------------------
    // Stylus handwriting.
    //
    // Mac.apk has no stylus and no ink window. AOSP's own defaults for the
    // callbacks are empty/false, so those are faithful; the session-timeout
    // accessors are pure arithmetic on a field and are faithful including the
    // clamp and the IllegalStateException.
    // ---------------------------------------------------------------------

    public boolean onStartStylusHandwriting() { return false; }
    public void onFinishStylusHandwriting() { }
    public void onPrepareStylusHandwriting() { }
    public void onStylusHandwritingMotionEvent(MotionEvent motionEvent) { }
    public boolean onStartConnectionlessStylusHandwriting(int inputType,
            CursorAnchorInfo cursorAnchorInfo) { return false; }

    /** AOSP tears the ink window down. No ink window exists here. */
    public final void finishStylusHandwriting() { }

    public final void finishConnectionlessStylusHandwriting(CharSequence text) {
        finishStylusHandwriting();
    }

    /** AOSP: `return mInkWindow;` -- the handwriting window, which is created
     *  lazily and is null until a handwriting session starts. No session can
     *  start here, so it is always null, which is a state AOSP also has. */
    public final Window getStylusHandwritingWindow() { return null; }

    // Named apart from the public accessor so updateFullscreenMode() reads as
    // AOSP does (it passes the soft-input window, not the ink window); both are
    // absent here, so the call site documents the intent rather than the value.
    private Window getStylusHandwritingWindowInternal() { return null; }

    public final Duration getStylusHandwritingSessionTimeout() {
        return Duration.ofMillis(mStylusHwSessionsTimeout);
    }

    /** AOSP: 100 seconds. Static and final. */
    public static final Duration getStylusHandwritingIdleTimeoutMax() {
        return Duration.ofMillis(STYLUS_HANDWRITING_IDLE_TIMEOUT_MAX_MS);
    }

    /** AOSP throws IllegalStateException on a non-positive duration and clamps
     *  anything above the maximum. Both are observable and both are kept. */
    public final void setStylusHandwritingSessionTimeout(Duration duration) {
        long timeoutMs = duration.toMillis();
        if (timeoutMs <= 0) {
            throw new IllegalStateException(
                    "A positive value should be set for Stylus handwriting session timeout.");
        }
        if (timeoutMs > STYLUS_HANDWRITING_IDLE_TIMEOUT_MAX_MS) {
            timeoutMs = STYLUS_HANDWRITING_IDLE_TIMEOUT_MAX_MS;
        }
        mStylusHwSessionsTimeout = timeoutMs;
    }

    /** AOSP copies the region (making it immutable to the caller) and pushes it
     *  to the system, skipping a repeat. The copy + the dedup are the parts
     *  that are observable through a subsequent call, so they are kept. */
    public final void setStylusHandwritingRegion(Region handwritingRegion) {
        Region copy = new Region(handwritingRegion);
        if (copy.equals(mLastHandwritingRegion)) {
            return;
        }
        mLastHandwritingRegion = copy;
    }

    // ---------------------------------------------------------------------
    // Inline suggestions.
    //
    // ⚠ GAP THIS LANE CANNOT CLOSE: onCreateInlineSuggestionsRequest(Bundle)
    // and onInlineSuggestionsResponse(InlineSuggestionsResponse) need
    // android.view.inputmethod.InlineSuggestionsRequest / -Response, which this
    // tree does not ship and which live in a package this lane does not own.
    // Both are trivial in AOSP (`return null;` / `return false;`), so they land
    // the moment those two Parcelables exist.
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // Misc
    // ---------------------------------------------------------------------

    /** AOSP: `return mInflater;`, set in onCreate() from the themed context. */
    public LayoutInflater getLayoutInflater() {
        if (mInflater == null) {
            mInflater = LayoutInflater.from(this);
        }
        return mInflater;
    }

    /** AOSP: `return mWindow;` -- the SoftInputWindow Dialog the IME's decor
     *  lives in. Mac.apk creates no such window; null is the AOSP state before
     *  onCreate() has run. */
    public Dialog getWindow() { return null; }

    /** AOSP returns the display width less the navigation bar and cutout. Our
     *  Resources' DisplayMetrics carries the host window width, which is the
     *  same quantity for a host that draws no system bars over the IME. */
    public int getMaxWidth() {
        if (getResources() == null || getResources().getDisplayMetrics() == null) {
            return 0;
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    /** @deprecated at API 21 -- AOSP now reports whether the device is
     *  high-end-GFX, and throws if called after onCreate(). Every Mac.apk host
     *  is Apple Silicon with a Metal GPU, so the answer is unconditionally
     *  true. */
    @Deprecated public boolean enableHardwareAcceleration() { return true; }

    /** @deprecated at API 31 -- AOSP logs a warning and always returns 0. */
    @Deprecated public int getInputMethodWindowRecommendedHeight() { return 0; }
}
