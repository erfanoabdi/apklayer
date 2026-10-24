package android.view.inputmethod;

/**
 * Base for stylus-handwriting gestures the IME sends an editor (API 34).
 * Source: frameworks/base/core/java/android/view/inputmethod/HandwritingGesture.java
 */
public abstract class HandwritingGesture {
    public static final int GRANULARITY_CHARACTER = 1;
    public static final int GRANULARITY_WORD = 2;

    protected String mFallbackText;

    public String getFallbackText() { return mFallbackText; }
}
