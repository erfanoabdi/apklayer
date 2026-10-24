// android.text.method.SingleLineTransformationMethod -- AOSP transform that
// collapses a multi-line string onto one line for single-line TextViews.
// AOSP:
//   frameworks/base/core/java/android/text/method/SingleLineTransformationMethod.java
//   public class SingleLineTransformationMethod extends ReplacementTransformationMethod
// Maps '\n' -> ' ' and '\r' -> U+FEFF (zero-width no-break space), so the text
// renders on one line. Process-wide singleton via getInstance().
package android.text.method;

public class SingleLineTransformationMethod extends ReplacementTransformationMethod {
    private static final char[] ORIGINAL = { '\n', '\r' };
    private static final char[] REPLACEMENT = { ' ', '﻿' };

    @Override protected char[] getOriginal() { return ORIGINAL; }
    @Override protected char[] getReplacement() { return REPLACEMENT; }

    private static SingleLineTransformationMethod sInstance;
    public static SingleLineTransformationMethod getInstance() {
        if (sInstance == null) sInstance = new SingleLineTransformationMethod();
        return sInstance;
    }
}
