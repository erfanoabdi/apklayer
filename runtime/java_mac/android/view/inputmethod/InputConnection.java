// android.view.inputmethod.InputConnection -- AOSP INTERFACE: the channel an
// IME / Compose text field uses to read and edit the editor's text. Was a
// CLASS, so any APK class declared `implements InputConnection` (Compose's
// RecordingInputConnection, AndroidX EditorInfo helpers) threw
// IncompatibleClassChangeError when invoked through the interface type, aborting
// text input -- the structural twin of the Bundle-Parcelable bug. Making it an
// interface (with default methods so partial implementers compile + our own
// BaseInputConnection overrides what it has) lets those classes implement it and
// lets the framework call methods through the InputConnection type. Method
// surface + types match BaseInputConnection (the exotic CorrectionInfo /
// InputContentInfo are Object, as in BaseInputConnection, to avoid depending on
// types we don't model). v1.0.422.
//
// The interface had frozen at the API-24 surface while BaseInputConnection grew
// past it: getSurroundingText (API 31) and getExtractedText were implemented on
// the concrete class but not DECLARED here, so a caller holding the value as an
// InputConnection (which is how AndroidX's InputConnectionCompat helpers and
// Emoji2 hold it) could not reach them and got NoSuchMethodError. The API 25-34
// methods whose AOSP signature can be expressed with types Mac.apk already
// models are now declared, with AOSP's own default bodies.
// Source: frameworks/base/core/java/android/view/inputmethod/InputConnection.java
package android.view.inputmethod;

public interface InputConnection {
    /** Flag for {@link #getTextBeforeCursor}/{@link #getTextAfterCursor}: keep styles. */
    int GET_TEXT_WITH_STYLES = 0x0001;
    /** Flag for {@link #getExtractedText}: keep monitoring the extracted text. */
    int GET_EXTRACTED_TEXT_MONITOR = 0x0001;

    // Handwriting-gesture results, reported through the IntConsumer of
    // performHandwritingGesture (not modeled yet, see the note at the bottom).
    int HANDWRITING_GESTURE_RESULT_UNKNOWN = 0;
    int HANDWRITING_GESTURE_RESULT_SUCCESS = 1;
    int HANDWRITING_GESTURE_RESULT_UNSUPPORTED = 2;
    int HANDWRITING_GESTURE_RESULT_FAILED = 3;
    int HANDWRITING_GESTURE_RESULT_CANCELLED = 4;
    int HANDWRITING_GESTURE_RESULT_FALLBACK = 5;

    // requestCursorUpdates() modes and (API 33+) filters.
    int CURSOR_UPDATE_IMMEDIATE = 1 << 0;
    int CURSOR_UPDATE_MONITOR = 1 << 1;
    int CURSOR_UPDATE_FILTER_EDITOR_BOUNDS = 1 << 2;
    int CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS = 1 << 3;
    int CURSOR_UPDATE_FILTER_INSERTION_MARKER = 1 << 4;
    int CURSOR_UPDATE_FILTER_VISIBLE_LINE_BOUNDS = 1 << 5;
    int CURSOR_UPDATE_FILTER_TEXT_APPEARANCE = 1 << 6;

    // AOSP defines this as Intent.FLAG_GRANT_READ_URI_PERMISSION; our
    // android.content.Intent does not declare that constant, so the literal
    // value is inlined (AOSP Intent.FLAG_GRANT_READ_URI_PERMISSION = 0x00000001).
    int INPUT_CONTENT_GRANT_READ_URI_PERMISSION = 0x00000001;

    default CharSequence getTextBeforeCursor(int length, int flags) { return ""; }
    default CharSequence getTextAfterCursor(int length, int flags) { return ""; }
    default CharSequence getSelectedText(int flags) { return null; }

    // AOSP API 31. The interface carries a real default (not a stub): it composes
    // the window from the three legacy accessors, so an implementer that only
    // overrode the API-24 methods still answers the modern call correctly.
    // Verbatim from AOSP, with Preconditions.checkArgumentNonnegative expanded
    // (that class is com.android.internal and has no public counterpart here).
    default SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        if (beforeLength < 0) {
            throw new IllegalArgumentException("beforeLength must not be negative");
        }
        if (afterLength < 0) {
            throw new IllegalArgumentException("afterLength must not be negative");
        }
        CharSequence textBeforeCursor = getTextBeforeCursor(beforeLength, flags);
        if (textBeforeCursor == null) {
            return null;
        }
        CharSequence textAfterCursor = getTextAfterCursor(afterLength, flags);
        if (textAfterCursor == null) {
            return null;
        }
        CharSequence selectedText = getSelectedText(flags);
        if (selectedText == null) {
            selectedText = "";
        }
        CharSequence surroundingText =
                android.text.TextUtils.concat(textBeforeCursor, selectedText, textAfterCursor);
        return new SurroundingText(surroundingText, textBeforeCursor.length(),
                textBeforeCursor.length() + selectedText.length(), -1);
    }

    // AOSP declares getExtractedText abstract. This interface uses defaults so a
    // partial implementer still compiles (see the header), and null is AOSP's
    // documented answer when the connection cannot produce the extraction, so
    // that is the fallback. BaseInputConnection overrides it with a real
    // snapshot of the editor.
    default ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        return null;
    }

    default int getCursorCapsMode(int reqModes) { return 0; }
    default boolean deleteSurroundingText(int beforeLength, int afterLength) { return false; }
    default boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) { return false; }
    default boolean setComposingText(CharSequence text, int newCursorPosition) { return false; }
    default boolean setComposingRegion(int start, int end) { return false; }
    default boolean finishComposingText() { return false; }
    default boolean commitText(CharSequence text, int newCursorPosition) { return false; }
    default boolean commitCompletion(CompletionInfo text) { return false; }
    default boolean commitCorrection(CorrectionInfo correctionInfo) { return false; }
    default boolean setSelection(int start, int end) { return false; }
    default boolean performEditorAction(int editorAction) { return false; }
    default boolean performContextMenuAction(int id) { return false; }
    default boolean beginBatchEdit() { return false; }
    default boolean endBatchEdit() { return false; }
    default boolean sendKeyEvent(android.view.KeyEvent event) { return false; }
    default boolean clearMetaKeyStates(int states) { return false; }
    default boolean reportFullscreenMode(boolean enabled) { return false; }
    default boolean performPrivateCommand(String action, android.os.Bundle data) { return false; }
    default boolean requestCursorUpdates(int cursorUpdateMode) { return false; }

    // AOSP API 34. Verbatim default: a filter of 0 means "no filtering", which is
    // exactly what the one-argument form does, so it delegates; any real filter
    // is a capability we cannot claim, hence false.
    default boolean requestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) {
        if (cursorUpdateFilter == 0) {
            return requestCursorUpdates(cursorUpdateMode);
        }
        return false;
    }

    // AOSP API 34 default: false ("the input connection is no longer valid, or
    // the editor does not support it").
    default boolean performSpellCheck() { return false; }

    // AOSP API 33 default: false. Signals the editor did not take over key
    // handling from the IME.
    default boolean setImeConsumesInput(boolean imeConsumesInput) { return false; }

    default android.os.Handler getHandler() { return null; }
    default void closeConnection() {}
    default boolean commitContent(InputContentInfo inputContentInfo, int flags, android.os.Bundle opts) { return false; }

    // The block that used to sit here said these methods were "NOT declared
    // deliberately", because TextAttribute / TextSnapshot / TextBoundsInfoResult
    // did not exist in this tree. They do now (added by the android.view API-gap
    // lane), and the note was explicit that the right order is "add the type
    // first, then the method" -- so here are the methods, with AOSP's own
    // default bodies.
    //
    // AOSP declares every one of these `default` on the interface too: the
    // TextAttribute overloads delegate to the plain form and DROP the attribute
    // (an editor that does not understand conversion suggestions still has to
    // commit the text), takeSnapshot() returns null, and requestTextBoundsInfo
    // reports CODE_UNSUPPORTED. Those are the answers a plain EditText gives on
    // a device, not stand-ins.

    /** AOSP InputConnection.commitText(CharSequence, int, TextAttribute)
     *  (API 33): `return commitText(text, newCursorPosition);`. */
    default boolean commitText(CharSequence text, int newCursorPosition,
            TextAttribute textAttribute) {
        return commitText(text, newCursorPosition);
    }
    /** AOSP InputConnection.setComposingText(CharSequence, int, TextAttribute). */
    default boolean setComposingText(CharSequence text, int newCursorPosition,
            TextAttribute textAttribute) {
        return setComposingText(text, newCursorPosition);
    }
    /** AOSP InputConnection.setComposingRegion(int, int, TextAttribute). */
    default boolean setComposingRegion(int start, int end, TextAttribute textAttribute) {
        return setComposingRegion(start, end);
    }
    /** AOSP InputConnection.replaceText(int, int, CharSequence, int, TextAttribute)
     *  (API 34). AOSP's default body is `return false` -- the editor must
     *  implement it to support atomic replacement; BaseInputConnection does. */
    default boolean replaceText(int start, int end, CharSequence text, int newCursorPosition,
            TextAttribute textAttribute) {
        return false;
    }
    /** AOSP InputConnection.takeSnapshot() (API 33): `return null` on the
     *  interface -- only an editor that can capture atomically implements it. */
    default TextSnapshot takeSnapshot() { return null; }
    /** AOSP InputConnection.requestTextBoundsInfo(RectF, Executor,
     *  Consumer&lt;TextBoundsInfoResult&gt;) (API 34): AOSP's default hands back
     *  CODE_UNSUPPORTED on the caller's executor, which is what an editor that
     *  cannot report layout geometry answers. It never drops the callback. */
    default void requestTextBoundsInfo(android.graphics.RectF bounds,
            java.util.concurrent.Executor executor,
            java.util.function.Consumer<TextBoundsInfoResult> consumer) {
        java.util.Objects.requireNonNull(bounds);
        java.util.Objects.requireNonNull(executor);
        java.util.Objects.requireNonNull(consumer);
        executor.execute(() -> consumer.accept(
                new TextBoundsInfoResult(TextBoundsInfoResult.CODE_UNSUPPORTED)));
    }

    default void performHandwritingGesture(android.view.inputmethod.HandwritingGesture gesture,
            java.util.concurrent.Executor executor, java.util.function.IntConsumer consumer) { }
    default boolean previewHandwritingGesture(android.view.inputmethod.PreviewableHandwritingGesture gesture,
            android.os.CancellationSignal cancellationSignal) { return false; }
}

