// android.view.inputmethod.TextSnapshot -- an atomic capture of the editor's
// text state, returned by InputConnection.takeSnapshot(). Source:
// frameworks/base core/java/android/view/inputmethod/TextSnapshot.java
// (android16-release), including its argument validation.
package android.view.inputmethod;

public final class TextSnapshot {
    private final SurroundingText mSurroundingText;
    private final int mCompositionStart;
    private final int mCompositionEnd;
    private final int mCursorCapsMode;

    public TextSnapshot(SurroundingText surroundingText, int compositionStart,
            int compositionEnd, int cursorCapsMode) {
        java.util.Objects.requireNonNull(surroundingText);
        if (compositionStart < -1) {
            throw new IllegalArgumentException("compositionStart must be -1 or higher but was "
                    + compositionStart);
        }
        if (compositionEnd < -1) {
            throw new IllegalArgumentException("compositionEnd must be -1 or higher but was "
                    + compositionEnd);
        }
        if (compositionStart == -1 && compositionEnd != -1) {
            throw new IllegalArgumentException("compositionEnd must be -1 if compositionStart is "
                    + "-1 but was " + compositionEnd);
        }
        if (compositionStart != -1 && compositionEnd == -1) {
            throw new IllegalArgumentException("compositionStart must be -1 if compositionEnd is "
                    + "-1 but was " + compositionStart);
        }
        if (compositionStart > compositionEnd) {
            throw new IllegalArgumentException("compositionStart=" + compositionStart
                    + " must be equal to or greater than compositionEnd=" + compositionEnd);
        }
        mSurroundingText = surroundingText;
        mCompositionStart = compositionStart;
        mCompositionEnd = compositionEnd;
        mCursorCapsMode = cursorCapsMode;
    }

    public SurroundingText getSurroundingText() { return mSurroundingText; }
    /** AOSP: the selection offsets are the SurroundingText's, rebased by its
     *  offset into the full editor text. */
    public int getSelectionStart() {
        return mSurroundingText.getSelectionStart() == -1
                ? -1 : mSurroundingText.getOffset() + mSurroundingText.getSelectionStart();
    }
    public int getSelectionEnd() {
        return mSurroundingText.getSelectionEnd() == -1
                ? -1 : mSurroundingText.getOffset() + mSurroundingText.getSelectionEnd();
    }
    public int getCompositionStart() { return mCompositionStart; }
    public int getCompositionEnd() { return mCompositionEnd; }
    public int getCursorCapsMode() { return mCursorCapsMode; }
}
