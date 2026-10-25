// android.view.inputmethod.InputConnectionWrapper -- AOSP's pass-through wrapper
// around another InputConnection (the IME framework + AndroidX wrap the editor's
// connection to intercept/augment calls). v1.0.426 Tier-2: was an empty stub, so
// AndroidX/IME code that `new InputConnectionWrapper(target, mutable)` and relied
// on delegation got nothing. Now it implements InputConnection (an interface
// since v1.0.422) and forwards every call to the wrapped target.
// Source: frameworks/base/core/java/android/view/inputmethod/InputConnectionWrapper.java
package android.view.inputmethod;

import android.view.KeyEvent;

public class InputConnectionWrapper implements InputConnection {
    private InputConnection mTarget;
    protected boolean mMutable;

    public InputConnectionWrapper(InputConnection target, boolean mutable) {
        mTarget = target; mMutable = mutable;
    }
    public void setTarget(InputConnection target) {
        if (mTarget != null && !mMutable) throw new SecurityException("not mutable");
        mTarget = target;
    }
    public InputConnection getTarget() { return mTarget; }

    @Override public CharSequence getTextBeforeCursor(int n, int flags) {
        return mTarget != null ? mTarget.getTextBeforeCursor(n, flags) : null;
    }
    @Override public CharSequence getTextAfterCursor(int n, int flags) {
        return mTarget != null ? mTarget.getTextAfterCursor(n, flags) : null;
    }
    @Override public CharSequence getSelectedText(int flags) {
        return mTarget != null ? mTarget.getSelectedText(flags) : null;
    }
    @Override public int getCursorCapsMode(int reqModes) {
        return mTarget != null ? mTarget.getCursorCapsMode(reqModes) : 0;
    }
    @Override public boolean deleteSurroundingText(int b, int a) {
        return mTarget != null && mTarget.deleteSurroundingText(b, a);
    }
    @Override public boolean deleteSurroundingTextInCodePoints(int b, int a) {
        return mTarget != null && mTarget.deleteSurroundingTextInCodePoints(b, a);
    }
    @Override public boolean setComposingText(CharSequence t, int p) {
        return mTarget != null && mTarget.setComposingText(t, p);
    }
    @Override public boolean setComposingRegion(int s, int e) {
        return mTarget != null && mTarget.setComposingRegion(s, e);
    }
    @Override public boolean finishComposingText() {
        return mTarget != null && mTarget.finishComposingText();
    }
    @Override public boolean commitText(CharSequence t, int p) {
        return mTarget != null && mTarget.commitText(t, p);
    }
    @Override public boolean commitCompletion(CompletionInfo t) {
        return mTarget != null && mTarget.commitCompletion(t);
    }
    @Override public boolean commitCorrection(CorrectionInfo correctionInfo) {
        return mTarget != null && mTarget.commitCorrection(correctionInfo);
    }
    @Override public boolean setSelection(int s, int e) {
        return mTarget != null && mTarget.setSelection(s, e);
    }
    @Override public boolean performEditorAction(int action) {
        return mTarget != null && mTarget.performEditorAction(action);
    }
    @Override public boolean performContextMenuAction(int id) {
        return mTarget != null && mTarget.performContextMenuAction(id);
    }
    @Override public boolean beginBatchEdit() {
        return mTarget != null && mTarget.beginBatchEdit();
    }
    @Override public boolean endBatchEdit() {
        return mTarget != null && mTarget.endBatchEdit();
    }
    @Override public boolean sendKeyEvent(KeyEvent event) {
        return mTarget != null && mTarget.sendKeyEvent(event);
    }
    @Override public boolean clearMetaKeyStates(int states) {
        return mTarget != null && mTarget.clearMetaKeyStates(states);
    }
    @Override public boolean reportFullscreenMode(boolean enabled) {
        return mTarget != null && mTarget.reportFullscreenMode(enabled);
    }
    @Override public boolean performPrivateCommand(String action, android.os.Bundle data) {
        return mTarget != null && mTarget.performPrivateCommand(action, data);
    }
    @Override public boolean requestCursorUpdates(int cursorUpdateMode) {
        return mTarget != null && mTarget.requestCursorUpdates(cursorUpdateMode);
    }
    @Override public android.os.Handler getHandler() {
        return mTarget != null ? mTarget.getHandler() : null;
    }
    @Override public void closeConnection() {
        if (mTarget != null) mTarget.closeConnection();
    }
    @Override public boolean commitContent(InputContentInfo info, int flags, android.os.Bundle opts) {
        return mTarget != null && mTarget.commitContent(info, flags, opts);
    }

    // ---- AOSP API-36 surface completion (android.view lane) ------------------
    // A wrapper must FORWARD, not fall back on the interface default -- the
    // interface defaults above would silently bypass the wrapped connection.
    @Override public boolean commitText(CharSequence text, int newCursorPosition,
            TextAttribute textAttribute) {
        return mTarget != null && mTarget.commitText(text, newCursorPosition, textAttribute);
    }
    @Override public boolean setComposingText(CharSequence text, int newCursorPosition,
            TextAttribute textAttribute) {
        return mTarget != null && mTarget.setComposingText(text, newCursorPosition, textAttribute);
    }
    @Override public boolean setComposingRegion(int start, int end,
            TextAttribute textAttribute) {
        return mTarget != null && mTarget.setComposingRegion(start, end, textAttribute);
    }
    @Override public boolean replaceText(int start, int end, CharSequence text,
            int newCursorPosition, TextAttribute textAttribute) {
        return mTarget != null
                && mTarget.replaceText(start, end, text, newCursorPosition, textAttribute);
    }
    @Override public TextSnapshot takeSnapshot() {
        return mTarget != null ? mTarget.takeSnapshot() : null;
    }
    @Override public void requestTextBoundsInfo(android.graphics.RectF bounds,
            java.util.concurrent.Executor executor,
            java.util.function.Consumer<TextBoundsInfoResult> consumer) {
        if (mTarget != null) {
            mTarget.requestTextBoundsInfo(bounds, executor, consumer);
        } else {
            // AOSP's wrapper NPEs on a null target; this tree's wrapper is
            // null-tolerant throughout (see every method above), so the
            // consumer is still answered rather than dropped.
            executor.execute(() -> consumer.accept(
                    new TextBoundsInfoResult(TextBoundsInfoResult.CODE_UNSUPPORTED)));
        }
    }
}
