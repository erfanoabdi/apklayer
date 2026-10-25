// android.view.inputmethod.InputMethod -- the top-level interface an IME
// service implements. Source: frameworks/base
// core/java/android/view/inputmethod/InputMethod.java (android16-release).
//
// Mac.apk installs no IME (InputMethodManager's lists are empty and text reaches
// the focused view straight from the Cocoa key handler), so nothing here is ever
// called -- but android.inputmethodservice.InputMethodService in an APK, and
// AbstractInputMethodService's inner AbstractInputMethodImpl, implement this
// interface by name.
package android.view.inputmethod;

public interface InputMethod {
    String SERVICE_INTERFACE = "android.view.InputMethod";
    String SERVICE_META_DATA = "android.view.im";

    /** The IME was shown by an explicit user action. */
    int SHOW_EXPLICIT = 0x00001;
    /** @deprecated at API 33 -- forced shows are no longer honoured. */
    @Deprecated
    int SHOW_FORCED = 0x00002;

    void attachToken(android.os.IBinder token);
    void bindInput(InputBinding binding);
    void unbindInput();
    void startInput(InputConnection inputConnection, EditorInfo attribute);
    void restartInput(InputConnection inputConnection, EditorInfo attribute);
    void createSession(SessionCallback callback);
    void setSessionEnabled(InputMethodSession session, boolean enabled);
    void revokeSession(InputMethodSession session);
    void showSoftInput(int flags, android.os.ResultReceiver resultReceiver);
    void hideSoftInput(int flags, android.os.ResultReceiver resultReceiver);
    void changeInputMethodSubtype(InputMethodSubtype subtype);

    /** AOSP InputMethod.SessionCallback: the IME hands back the session it
     *  created for createSession(). */
    interface SessionCallback {
        void sessionCreated(InputMethodSession session);
    }
}
