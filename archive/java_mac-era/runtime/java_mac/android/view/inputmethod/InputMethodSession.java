// android.view.inputmethod.InputMethodSession -- the per-editor half of an IME's
// interface, called by the framework as the editor's state changes.
// Source: frameworks/base
// core/java/android/view/inputmethod/InputMethodSession.java (android16-release).
//
// Mac.apk binds no IME, so nothing in this tree implements or calls it -- but an
// InputMethodService subclass in an APK does, and its inner session class
// implements this interface by name.
package android.view.inputmethod;

public interface InputMethodSession {
    void finishInput();
    void updateExtractedText(int token, ExtractedText text);
    void updateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd);
    void viewClicked(boolean focusChanged);
    void updateCursor(android.graphics.Rect newCursor);
    void updateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo);
    void displayCompletions(CompletionInfo[] completions);
    void appPrivateCommand(String action, android.os.Bundle data);
    /** @deprecated at API 28 -- use InputMethodManager.toggleSoftInput. */
    @Deprecated
    void toggleSoftInput(int showFlags, int hideFlags);
    void dispatchKeyEvent(int seq, android.view.KeyEvent event, EventCallback callback);
    void dispatchTrackballEvent(int seq, android.view.MotionEvent event, EventCallback callback);
    void dispatchGenericMotionEvent(int seq, android.view.MotionEvent event,
            EventCallback callback);

    /** AOSP InputMethodSession.EventCallback: the IME reports back whether it
     *  consumed a dispatched event. */
    interface EventCallback {
        void finishedEvent(int seq, boolean handled);
    }
}
