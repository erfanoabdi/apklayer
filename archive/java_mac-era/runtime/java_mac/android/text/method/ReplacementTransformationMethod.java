// android.text.method.ReplacementTransformationMethod -- AOSP base for
// transforms that substitute a fixed set of characters with another set.
// AOSP:
//   frameworks/base/core/java/android/text/method/ReplacementTransformationMethod.java
//   public abstract class ReplacementTransformationMethod
//       implements TransformationMethod
// Subclasses provide getOriginal()/getReplacement() arrays; getTransformation
// returns a same-length view that maps each original char to its replacement.
package android.text.method;

import android.graphics.Rect;
import android.text.GetChars;
import android.view.View;

public abstract class ReplacementTransformationMethod implements TransformationMethod {

    protected abstract char[] getOriginal();
    protected abstract char[] getReplacement();

    @Override
    public CharSequence getTransformation(CharSequence source, View v) {
        char[] original = getOriginal();
        // Fast path: nothing to replace.
        if (source == null) return null;
        boolean has = false;
        for (int i = 0; i < source.length() && !has; i++) {
            char c = source.charAt(i);
            for (int j = 0; j < original.length; j++) {
                if (c == original[j]) { has = true; break; }
            }
        }
        if (!has) return source;
        return new ReplacementCharSequence(source, original, getReplacement());
    }

    @Override
    public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                               int direction, Rect previouslyFocusedRect) {}

    private static class ReplacementCharSequence implements CharSequence, GetChars {
        private final CharSequence mSource;
        private final char[] mOriginal;
        private final char[] mReplacement;

        ReplacementCharSequence(CharSequence source, char[] original, char[] replacement) {
            mSource = source;
            mOriginal = original;
            mReplacement = replacement;
        }

        @Override public int length() { return mSource.length(); }
        @Override public char charAt(int i) {
            char c = mSource.charAt(i);
            for (int j = 0; j < mOriginal.length; j++) {
                if (c == mOriginal[j]) return mReplacement[j];
            }
            return c;
        }
        @Override public CharSequence subSequence(int start, int end) {
            char[] c = new char[end - start];
            getChars(start, end, c, 0);
            return new String(c);
        }
        @Override public void getChars(int start, int end, char[] dest, int off) {
            for (int i = start; i < end; i++) dest[off + (i - start)] = charAt(i);
        }
        @Override public String toString() {
            char[] c = new char[length()];
            getChars(0, length(), c, 0);
            return new String(c);
        }
    }
}
