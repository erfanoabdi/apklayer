package android.view.inputmethod;

import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/**
 * The InputConnection a Mac.apk {@link TextView}-based editor is bound to when
 * the view itself does not supply one.
 *
 * <p>WHAT AOSP DOES. {@code TextView.onCreateInputConnection(EditorInfo)}
 * (frameworks/base core/java/android/widget/TextView.java:8508) fills the
 * EditorInfo from the field and returns
 * {@code new EditableInputConnection(this)} -- a BaseInputConnection subclass
 * (com/android/internal/widget/EditableInputConnection.java) whose
 * {@code getEditable()} returns {@code mTextView.getEditableText()}, which on
 * AOSP IS {@code mText}. Every edit the IME makes therefore lands directly in
 * the view's own buffer.
 *
 * <p>WHY THIS CLASS EXISTS INSTEAD. In this tree two things differ:
 * <ol>
 *   <li>{@code TextView.onCreateInputConnection} returns {@code null}
 *       unconditionally (TextView.java:3281), so binding an editor produced no
 *       connection at all -- {@code isAcceptingText()} was permanently false
 *       and every InputConnection-shaped route into a legacy EditText was
 *       dead.</li>
 *   <li>{@code TextView.getEditableText()} returns a SpannableStringBuilder
 *       COPIED from {@code text} and cached per text identity
 *       (TextView.java:1572). Reads are faithful, but a write through it does
 *       NOT reach {@code text}, does not fire the TextWatchers and does not
 *       invalidate -- so a plain {@code BaseInputConnection(tv, true)} would
 *       edit a detached buffer and the field would appear to ignore every
 *       keystroke.</li>
 * </ol>
 * So this subclass keeps BaseInputConnection's READ side (getEditable() points
 * at the live, correctly-rebuilt buffer, which is what getTextBeforeCursor /
 * getSurroundingText / getExtractedText / takeSnapshot all use) and routes
 * every MUTATION through {@link TextView#macapkHandleSoftKey}, the routine the
 * soft-input path already uses -- so a character typed through the connection
 * and a character typed through the raw-key fallback take the SAME code path
 * and cannot diverge.
 *
 * <p>REMOVAL CONDITION. The moment {@code TextView.onCreateInputConnection}
 * returns a real connection over an Editable {@code mText}, this class stops
 * being reached: {@link InputMethodManager} only falls back to it when the view
 * returned null. Delete it then. It is NOT per-app code -- it keys off
 * {@code onCheckIsTextEditor()}, which is AOSP's own IS_TEXT_EDITOR test.
 */
final class MacApkEditorInputConnection extends BaseInputConnection {

    private final TextView mView;
    /** Number of characters of the current composing run that we have already
     *  inserted, so the next setComposingText can retract them. Our editor has
     *  no composing-span support of its own, so composition is emulated as
     *  "delete what was provisionally inserted, insert the new run". */
    private int mComposedLen;

    MacApkEditorInputConnection(TextView view) {
        // fullEditor=false: BaseInputConnection's own editable is never used --
        // getEditable() below overrides it with the view's live buffer.
        super(view, false);
        mView = view;
    }

    View target() { return mView; }

    @Override
    public android.text.Editable getEditable() {
        android.text.Editable e = mView.getEditableText();
        return e != null ? e : super.getEditable();
    }

    // ---- mutations: every one goes through the view's own key routine -------

    private boolean typeChar(char c) {
        try { return mView.macapkHandleSoftKey(c, 0); }
        catch (Throwable t) { return false; }
    }

    private boolean backspace() {
        // KEYCODE_DEL == 67. macapkHandleSoftKey deletes one character back.
        try { return mView.macapkHandleSoftKey(8, 67); }
        catch (Throwable t) { return false; }
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        retractComposing();
        if (text == null) return true;
        for (int i = 0; i < text.length(); i++) typeChar(text.charAt(i));
        return true;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        retractComposing();
        if (text == null) return true;
        for (int i = 0; i < text.length(); i++) typeChar(text.charAt(i));
        mComposedLen = text.length();
        return true;
    }

    private void retractComposing() {
        while (mComposedLen > 0) { backspace(); mComposedLen--; }
    }

    @Override
    public boolean finishComposingText() {
        mComposedLen = 0;   // the provisional text becomes final in place
        return true;
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        // We cannot mark a region in a buffer we do not own; report the
        // AOSP default for an editor that declines (BaseInputConnection
        // returns true after marking, an editor without spans returns false).
        return false;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        retractComposing();
        // afterLength is ignored: macapkHandleSoftKey edits at the END of the
        // buffer, which is where this editor's caret always is. Deleting
        // forward would remove the wrong characters, and doing nothing is the
        // honest answer -- see the caret note in the class header.
        for (int i = 0; i < Math.max(0, beforeLength); i++) backspace();
        return true;
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        try {
            return mView.macapkHandleSoftKey(event.getUnicodeChar(), event.getKeyCode(), event);
        } catch (Throwable t) { return false; }
    }

    /**
     * AOSP: the IME calls this when its action button (Search / Go / Done) is
     * pressed; TextView.onEditorAction(int) then invokes the registered
     * OnEditorActionListener. Routed through macapkHandleSoftKey's ENTER branch,
     * which since the text-input lane (2026-09-29, MACAPK_EDITOR_ACTION_DEFAULTS)
     * runs TextView.onEditorAction(int) itself -- so actionNext advances focus,
     * actionDone closes input, and a subclass override (NumberPicker's
     * CustomEditText commit) runs -- and calls the listener with the field's own
     * action id (macapkActionIdForEnterEvent) and a null KeyEvent, exactly the
     * pair AOSP passes for an IME-button action (TextView.java:7965-7969). NOTE
     * this is one of TWO host-keystroke routes that run those defaults (the
     * other is the sActiveInput soft-key route); actionCode itself is still
     * ignored here (see docs/text-input-pickers-and-editor-action_2026-09-29.md,
     * follow-up F2).
     */
    @Override
    public boolean performEditorAction(int actionCode) {
        retractComposing();
        try { return mView.macapkHandleSoftKey(0, 66 /* KEYCODE_ENTER */, null); }
        catch (Throwable t) { return false; }
    }

    @Override
    public boolean replaceText(int start, int end, CharSequence text,
                               int newCursorPosition, Object textAttribute) {
        // Whole-buffer rewrite through setText, which fires the TextWatchers
        // and invalidates (TextView.java:385) -- the only write path that keeps
        // `text` and getEditableText() in agreement.
        try {
            CharSequence cur = mView.getText();
            String s = cur == null ? "" : cur.toString();
            int a = Math.max(0, Math.min(Math.min(start, end), s.length()));
            int b = Math.max(0, Math.min(Math.max(start, end), s.length()));
            mView.setText(s.substring(0, a) + (text == null ? "" : text) + s.substring(b));
            mComposedLen = 0;
            return true;
        } catch (Throwable t) { return false; }
    }

    @Override
    public boolean beginBatchEdit() { return true; }
    @Override
    public boolean endBatchEdit() { return true; }
}
