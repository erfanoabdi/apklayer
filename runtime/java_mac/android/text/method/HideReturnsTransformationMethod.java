// android.text.method.HideReturnsTransformationMethod -- AOSP transform that
// hides carriage returns ('\r') by mapping them to U+FEFF. AOSP:
//   frameworks/base/core/java/android/text/method/HideReturnsTransformationMethod.java
//   public class HideReturnsTransformationMethod extends ReplacementTransformationMethod
// Process-wide singleton via getInstance().
package android.text.method;

public class HideReturnsTransformationMethod extends ReplacementTransformationMethod {
    private static final char[] ORIGINAL = { '\r' };
    private static final char[] REPLACEMENT = { '﻿' };

    @Override protected char[] getOriginal() { return ORIGINAL; }
    @Override protected char[] getReplacement() { return REPLACEMENT; }

    private static HideReturnsTransformationMethod sInstance;
    public static HideReturnsTransformationMethod getInstance() {
        if (sInstance == null) sInstance = new HideReturnsTransformationMethod();
        return sInstance;
    }
}
