package android.accessibilityservice;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.TextAttribute;

/**
 * API 33+ base class letting an accessibility service act as a secondary input
 * method (declared with AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR).
 *
 * The service subclasses this and returns an instance from
 * AccessibilityService.onCreateInputMethod(); the platform then calls
 * onStartInput / onUpdateSelection / onFinishInput as the user moves through
 * editable fields, and the service edits via getCurrentInputConnection().
 *
 * Mac.apk binds no accessibility service, so those callbacks never fire and no
 * input connection is ever established -- getCurrentInputConnection() returns
 * null, which is precisely what AOSP returns before onStartInput and after
 * onFinishInput. A service that null-checks (all of them do; AOSP documents the
 * method as @Nullable) therefore behaves as it does on a device between fields.
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/InputMethod.java
 */
public class InputMethod {

    private final AccessibilityService mService;
    private boolean mInputStarted;
    private EditorInfo mEditorInfo;
    private AccessibilityInputConnection mConnection;

    public InputMethod(AccessibilityService service) {
        mService = service;
    }

    /** AOSP: null until onStartInput and again after onFinishInput. Mac.apk
     *  never starts input for a service, so it stays null. */
    public final AccessibilityInputConnection getCurrentInputConnection() {
        return mConnection;
    }

    public final EditorInfo getCurrentInputEditorInfo() { return mEditorInfo; }

    public final boolean getCurrentInputStarted() { return mInputStarted; }

    /**
     * Overridable hooks. These are NOT no-op overrides of a stateful
     * superclass method -- InputMethod is the root of this hierarchy and AOSP's
     * own bodies are empty; the state (mInputStarted / mEditorInfo) is assigned
     * by the platform-side dispatcher, not by these methods, on a device too.
     */
    public void onStartInput(EditorInfo attribute, boolean restarting) { }

    public void onFinishInput() { }

    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
            int newSelEnd, int candidatesStart, int candidatesEnd) { }

    /** Kept so the service reference is live and a subclass can be given the
     *  owning service the way AOSP's package-private plumbing does. */
    AccessibilityService getService() { return mService; }

    /**
     * The restricted editing surface an accessibility IME gets. On a device
     * every call is forwarded to the focused editor over the input-method
     * binder. Mac.apk never hands one of these out (getCurrentInputConnection
     * is null), so the bodies exist for API completeness and for a service that
     * holds a reference across a field change -- AOSP's own methods become
     * no-ops in exactly that situation, because the underlying connection is
     * already gone.
     */
    public final class AccessibilityInputConnection {

        AccessibilityInputConnection() { }

        public void commitText(CharSequence text, int newCursorPosition,
                TextAttribute textAttribute) { }

        public void setSelection(int start, int end) { }

        public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                int flags) {
            return null;
        }

        public void deleteSurroundingText(int beforeLength, int afterLength) { }

        public void sendKeyEvent(KeyEvent event) { }

        public void performEditorAction(int editorAction) { }

        public void performContextMenuAction(int id) { }

        /** AOSP returns 0 when there is no connection to ask. */
        public int getCursorCapsMode(int reqModes) { return 0; }

        public void clearMetaKeyStates(int states) { }
    }
}
