// android.view.inputmethod.ConnectionlessHandwritingCallback -- the result
// callback for InputMethodManager.startConnectionlessStylusHandwriting*, where
// the IME recognises handwriting and hands back TEXT without ever opening an
// InputConnection. Source: frameworks/base
// core/java/android/view/inputmethod/ConnectionlessHandwritingCallback.java
// (android16-release).
//
// Mac.apk has no stylus source and no IME, so onResult is never reached; the
// manager reports CONNECTIONLESS_HANDWRITING_ERROR_UNSUPPORTED, which is the
// error a device raises when the current IME does not support the feature.
package android.view.inputmethod;

public interface ConnectionlessHandwritingCallback {
    /** No handwriting was recognised in the session. */
    int CONNECTIONLESS_HANDWRITING_ERROR_NO_TEXT_RECOGNIZED = 0;
    /** The current IME does not support connectionless handwriting. */
    int CONNECTIONLESS_HANDWRITING_ERROR_UNSUPPORTED = 1;
    /** The session could not be started, or ended before recognising anything. */
    int CONNECTIONLESS_HANDWRITING_ERROR_OTHER = 2;

    void onResult(CharSequence text);
    void onError(int errorCode);
}
