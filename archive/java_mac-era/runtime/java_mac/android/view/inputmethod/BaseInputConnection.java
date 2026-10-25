package android.view.inputmethod;

import android.view.View;
import android.view.KeyEvent;
import android.text.Editable;

// android.view.inputmethod.BaseInputConnection: the AOSP base implementation of
// InputConnection that views return from View.onCreateInputConnection. Apps
// (CandyCrush, SmashHit) reference it at class-init / onCreate; a missing class
// threw NoClassDefFoundError and aborted onCreate before the game view was
// built. Mac.apk has no soft-IME (text comes from the host keyboard), so the
// editing methods are inert -- but the class must load, construct, and present
// the InputConnection contract. Mirrors AOSP method surface + return defaults.
public class BaseInputConnection implements InputConnection {  // InputConnection is now an interface (v1.0.422)
    protected final View mTargetView;
    private final boolean mFullEditor;

    public BaseInputConnection(View targetView, boolean fullEditor) {
        this.mTargetView = targetView;
        this.mFullEditor = fullEditor;
    }

    // v1.0.425 Tier-2: back the connection with a REAL Editable so commitText /
    // deleteSurroundingText / sendKeyEvent / setComposingText actually edit text
    // (AOSP BaseInputConnection does the same against the target editor's Editable
    // or, for the non-editor case, a lazily-created dummy used for key-event
    // simulation). Pairs with the IME bind path (InputMethodManager feeds host
    // keystrokes into a bound connection): a legacy EditText that returns a
    // BaseInputConnection now receives typed text through the standard contract,
    // and the cursor rides forward via Selection markers (which work now that
    // SpannableStringBuilder tracks span offsets).
    private android.text.Editable mDummyEditable;
    private int mComposingStart = -1, mComposingEnd = -1;

    public Editable getEditable() {
        if (mFullEditor && mTargetView instanceof android.widget.TextView tv) {
            Editable e = tv.getEditableText();
            if (e != null) return e;
        }
        if (mDummyEditable == null) {
            mDummyEditable = new android.text.SpannableStringBuilder();
            android.text.Selection.setSelection(mDummyEditable, 0);
        }
        return mDummyEditable;
    }

    private int selStart() {
        int s = android.text.Selection.getSelectionStart(getEditable());
        return s < 0 ? getEditable().length() : s;
    }
    private int selEnd() {
        int e = android.text.Selection.getSelectionEnd(getEditable());
        return e < 0 ? getEditable().length() : e;
    }

    // Put the shared COMPOSING sentinel over [start, end) of the LIVE editor
    // content, so BaseInputConnection.getComposingSpanStart/End (the only
    // documented way an app can read the composing region) agree with this
    // connection's own bookkeeping.
    //
    // Deliberately NOT setComposingSpans(content, start, end): AOSP calls that
    // helper on the INCOMING text (a fresh Spannable holding only the default
    // composing-style spans) and lets Editable.replace copy the spans in. Run
    // over the live content, its "force SPAN_COMPOSING onto every span in the
    // range" loop would also stamp SPAN_COMPOSING onto the Selection cursor
    // markers sitting inside the composing run, and the next
    // removeComposingSpans would then delete the cursor. Setting the sentinel
    // directly reaches the same end state AOSP's content holds.
    private static void markComposing(Editable content, int start, int end) {
        content.setSpan(COMPOSING, start, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                | android.text.Spanned.SPAN_COMPOSING);
    }

    private void replaceText(CharSequence text, int newCursorPosition, boolean composing) {
        Editable content = getEditable();
        if (text == null) text = "";
        int a, b;
        if (mComposingStart >= 0 && mComposingEnd >= 0) {       // replace the composing region
            a = Math.min(mComposingStart, mComposingEnd);
            b = Math.max(mComposingStart, mComposingEnd);
        } else {                                                // replace the selection
            a = Math.min(selStart(), selEnd());
            b = Math.max(selStart(), selEnd());
        }
        a = Math.max(0, Math.min(a, content.length()));
        b = Math.max(0, Math.min(b, content.length()));
        content.replace(a, b, text);
        if (composing) {
            mComposingStart = a; mComposingEnd = a + text.length();
            markComposing(content, mComposingStart, mComposingEnd);
        } else {
            mComposingStart = mComposingEnd = -1;
            removeComposingSpans(content);
        }
        // newCursorPosition: >0 = relative to end of inserted text, <=0 = relative
        // to its start (AOSP). Clamp into range.
        int newCursor = (newCursorPosition > 0) ? a + text.length() + (newCursorPosition - 1)
                                                : a + newCursorPosition;
        newCursor = Math.max(0, Math.min(newCursor, content.length()));
        android.text.Selection.setSelection((android.text.Spannable) content, newCursor);
    }

    public boolean beginBatchEdit() { return false; }
    public boolean endBatchEdit()   { return false; }

    public boolean clearMetaKeyStates(int states) { return false; }
    public boolean commitText(CharSequence text, int newCursorPosition) {
        replaceText(text, newCursorPosition, false);
        return true;
    }
    public boolean commitCompletion(CompletionInfo text) { return false; }
    public boolean commitCorrection(Object correctionInfo) { return false; }
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        Editable content = getEditable();
        int st = selStart(), en = selEnd();
        int delBefore = Math.min(Math.max(0, beforeLength), st);
        int delAfter  = Math.min(Math.max(0, afterLength), content.length() - en);
        if (delAfter > 0)  content.delete(en, en + delAfter);
        if (delBefore > 0) content.delete(st - delBefore, st);
        return true;
    }
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return deleteSurroundingText(beforeLength, afterLength);
    }
    public boolean finishComposingText() {
        mComposingStart = mComposingEnd = -1;
        // AOSP finishComposingText: removeComposingSpans(content). Drops the
        // COMPOSING sentinel AND any span an IME marked SPAN_COMPOSING, so the
        // composing underline disappears when composition ends.
        removeComposingSpans(getEditable());
        return true;
    }
    public int getCursorCapsMode(int reqModes) { return 0; }
    public CharSequence getTextBeforeCursor(int length, int flags) {
        Editable content = getEditable();
        int st = selStart();
        return content.subSequence(Math.max(0, st - Math.max(0, length)), st);
    }
    public CharSequence getTextAfterCursor(int length, int flags) {
        Editable content = getEditable();
        int en = selEnd();
        return content.subSequence(en, Math.min(content.length(), en + Math.max(0, length)));
    }
    public CharSequence getSelectedText(int flags) {
        int a = Math.min(selStart(), selEnd()), b = Math.max(selStart(), selEnd());
        if (a == b) return null;
        return getEditable().subSequence(a, b);
    }
    public boolean performContextMenuAction(int id) { return false; }
    public boolean performEditorAction(int actionCode) { return false; }
    public boolean performPrivateCommand(String action, android.os.Bundle data) { return false; }
    public boolean reportFullscreenMode(boolean enabled) { return false; }
    public boolean sendKeyEvent(KeyEvent event) {
        // AOSP BaseInputConnection.sendKeyEvent simulates the key against the
        // editable on ACTION_DOWN: DEL deletes back, printable chars insert.
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int code = event.getKeyCode();
        if (code == 67 /* KEYCODE_DEL */) { return deleteSurroundingText(1, 0); }
        int u = event.getUnicodeChar();
        if (u >= 32 && u != 127) { return commitText(String.valueOf((char) u), 1); }
        return false;
    }
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        replaceText(text, newCursorPosition, true);
        return true;
    }
    public boolean setComposingRegion(int start, int end) {
        mComposingStart = start; mComposingEnd = end;
        // AOSP marks the requested range as the composing region on the content
        // (clamped and ordered, as AOSP does before setComposingSpans).
        Editable content = getEditable();
        int a = Math.max(0, Math.min(Math.min(start, end), content.length()));
        int b = Math.max(0, Math.min(Math.max(start, end), content.length()));
        markComposing(content, a, b);
        return true;
    }
    public boolean setSelection(int start, int end) {
        Editable content = getEditable();
        int a = Math.max(0, Math.min(start, content.length()));
        int b = Math.max(0, Math.min(end, content.length()));
        android.text.Selection.setSelection((android.text.Spannable) content, a, b);
        return true;
    }
    public boolean requestCursorUpdates(int cursorUpdateMode) { return false; }

    // AOSP getSurroundingText (API 31): a window of text around the cursor plus
    // the selection range, mapped back into the full editor via `offset`. We
    // build it from the live Editable so an IME that prefers the modern API
    // (over getTextBeforeCursor/getTextAfterCursor) sees the same content.
    public SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        Editable content = getEditable();
        int len = content.length();
        int selSt = Math.min(selStart(), selEnd());
        int selEn = Math.max(selStart(), selEnd());
        int before = Math.max(0, beforeLength);
        int after = Math.max(0, afterLength);
        int start = Math.max(0, selSt - before);
        int end = Math.min(len, selEn + after);
        CharSequence sub = content.subSequence(start, end);
        return new SurroundingText(sub, selSt - start, selEn - start, start);
    }

    // AOSP getExtractedText: a snapshot of the whole editor for the IME's
    // extract-text UI. We have no extract UI, but return a faithful snapshot so
    // callers that read it back get the real text + selection.
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        Editable content = getEditable();
        ExtractedText et = new ExtractedText();
        et.text = content.toString();
        et.startOffset = 0;
        et.partialStartOffset = -1;
        et.partialEndOffset = -1;
        et.selectionStart = Math.min(selStart(), selEnd());
        et.selectionEnd = Math.max(selStart(), selEnd());
        et.flags = 0;
        return et;
    }

    // AOSP replaceText (API 34): replace [start, end) with `text`, optionally
    // applying spans, then position the cursor. Mirrors commitText's placement.
    public boolean replaceText(int start, int end, CharSequence text,
                               int newCursorPosition, Object textAttribute) {
        Editable content = getEditable();
        if (text == null) text = "";
        int a = Math.max(0, Math.min(start, content.length()));
        int b = Math.max(0, Math.min(end, content.length()));
        if (a > b) { int t = a; a = b; b = t; }
        content.replace(a, b, text);
        mComposingStart = mComposingEnd = -1;
        removeComposingSpans(content);   // AOSP: replaceText finishes composition
        int newCursor = (newCursorPosition > 0)
            ? a + text.length() + (newCursorPosition - 1) : a + newCursorPosition;
        newCursor = Math.max(0, Math.min(newCursor, content.length()));
        android.text.Selection.setSelection((android.text.Spannable) content, newCursor);
        return true;
    }
    public boolean commitContent(Object inputContentInfo, int flags, android.os.Bundle opts) { return false; }
    public android.os.Handler getHandler() { return null; }
    public void closeConnection() {}

    // AOSP static helpers used by the IME framework for composing-span bookkeeping.
    //
    // These are the ONE documented way an app can ask "what is being composed
    // right now" (a custom EditText drawing its own composing underline, a
    // spell-checker locating the composing run). They used to be hardcoded
    // no-ops while this class tracked the composing region only in its private
    // mComposingStart/mComposingEnd fields, so the two read paths
    // disagreed: setComposingText() had a live composing run and
    // getComposingSpanStart() still answered -1. Now the span IS maintained on
    // the live Editable, so both agree.
    //
    // COMPOSING is a shared sentinel span object, exactly as in AOSP
    // (`static final Object COMPOSING = new ComposingText();` where
    // `class ComposingText implements NoCopySpan {}`): NoCopySpan keeps it from
    // being carried into a copy of the text.
    private static final class ComposingText implements android.text.NoCopySpan {}
    static final Object COMPOSING = new ComposingText();

    public static int getComposingSpanStart(android.text.Spannable text) {
        return text.getSpanStart(COMPOSING);
    }
    public static int getComposingSpanEnd(android.text.Spannable text) {
        return text.getSpanEnd(COMPOSING);
    }
    public static void removeComposingSpans(android.text.Spannable text) {
        text.removeSpan(COMPOSING);
        Object[] sps = text.getSpans(0, text.length(), Object.class);
        if (sps != null) {
            for (int i = sps.length - 1; i >= 0; i--) {
                Object o = sps[i];
                if ((text.getSpanFlags(o) & android.text.Spanned.SPAN_COMPOSING) != 0) {
                    text.removeSpan(o);
                }
            }
        }
    }
    public static void setComposingSpans(android.text.Spannable text) {
        setComposingSpans(text, 0, text.length());
    }
    public static void setComposingSpans(android.text.Spannable text, int start, int end) {
        final Object[] sps = text.getSpans(start, end, Object.class);
        if (sps != null) {
            for (int i = sps.length - 1; i >= 0; i--) {
                final Object o = sps[i];
                if (o == COMPOSING) {
                    text.removeSpan(o);
                    continue;
                }
                // AOSP: any span already inside the composing region must carry
                // SPAN_COMPOSING with EXCLUSIVE_EXCLUSIVE extension, so that
                // finishing composition can strip it cleanly.
                final int fl = text.getSpanFlags(o);
                if ((fl & (android.text.Spanned.SPAN_COMPOSING
                           | android.text.Spanned.SPAN_POINT_MARK_MASK))
                        != (android.text.Spanned.SPAN_COMPOSING
                            | android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)) {
                    text.setSpan(o, text.getSpanStart(o), text.getSpanEnd(o),
                            (fl & ~android.text.Spanned.SPAN_POINT_MARK_MASK)
                            | android.text.Spanned.SPAN_COMPOSING
                            | android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        text.setSpan(COMPOSING, start, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                | android.text.Spanned.SPAN_COMPOSING);
    }

    /** AOSP BaseInputConnection.takeSnapshot():1086 -- an atomic capture of the
     *  live editor state. Transcribed; MEMORY_EFFICIENT_TEXT_LENGTH is AOSP's
     *  own EditorInfo constant (2048), halved for each side exactly as AOSP
     *  does, and the same three CAP_MODE_* bits are requested. */
    @Override
    public TextSnapshot takeSnapshot() {
        final android.text.Editable content = getEditable();
        if (content == null) return null;

        int composingStart = getComposingSpanStart(content);
        int composingEnd = getComposingSpanEnd(content);
        if (composingEnd < composingStart) {
            final int tmp = composingStart;
            composingStart = composingEnd;
            composingEnd = tmp;
        }

        final int half = 2048 / 2;   // EditorInfo.MEMORY_EFFICIENT_TEXT_LENGTH / 2
        final SurroundingText surroundingText =
                getSurroundingText(half, half, InputConnection.GET_TEXT_WITH_STYLES);
        if (surroundingText == null) return null;

        final int cursorCapsMode = getCursorCapsMode(
                android.text.TextUtils.CAP_MODE_CHARACTERS
                | android.text.TextUtils.CAP_MODE_WORDS
                | android.text.TextUtils.CAP_MODE_SENTENCES);

        return new TextSnapshot(surroundingText, composingStart, composingEnd, cursorCapsMode);
    }
}
