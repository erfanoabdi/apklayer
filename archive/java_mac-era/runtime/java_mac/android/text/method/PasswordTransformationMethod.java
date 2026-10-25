// android.text.method.PasswordTransformationMethod -- AOSP transform that
// masks every character with a dot for password fields. AOSP:
//   frameworks/base/core/java/android/text/method/PasswordTransformationMethod.java
//   public class PasswordTransformationMethod
//       implements TransformationMethod, TextWatcher
// with a process-wide singleton via getInstance().
//
// Promoted from the v1.0.92 empty stub to the faithful contract: getInstance()
// + a getTransformation() that returns a same-length masked CharSequence
// (DOT = U+2022). TextWatcher callbacks are no-ops (AOSP uses them only to
// drive the "briefly reveal the last typed char" animation, which we don't
// render); the masking itself is the observable behavior callers depend on.
package android.text.method;

import android.graphics.Rect;
import android.text.GetChars;
import android.text.TextWatcher;
import android.view.View;

public class PasswordTransformationMethod
        implements TransformationMethod, TextWatcher {
    private static PasswordTransformationMethod sInstance;
    private static final char DOT = '•';

    public PasswordTransformationMethod() {}

    public static PasswordTransformationMethod getInstance() {
        if (sInstance == null) sInstance = new PasswordTransformationMethod();
        return sInstance;
    }

    @Override
    public CharSequence getTransformation(CharSequence source, View view) {
        return new PasswordCharSequence(source);
    }

    @Override
    public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                               int direction, Rect previouslyFocusedRect) {}

    // TextWatcher -- AOSP uses these only for the reveal-last-char animation.
    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    @Override public void afterTextChanged(android.text.Editable s) {}

    // Same-length view of `source` with every visible character replaced by a
    // dot. Mirrors AOSP's nested PasswordCharSequence (GetChars so the layout
    // path copies masked chars, not the originals).
    private static class PasswordCharSequence implements CharSequence, GetChars {
        private final CharSequence mSource;
        PasswordCharSequence(CharSequence source) { mSource = source; }

        @Override public int length() { return mSource.length(); }
        @Override public char charAt(int i) { return DOT; }
        @Override public CharSequence subSequence(int start, int end) {
            char[] buf = new char[end - start];
            getChars(start, end, buf, 0);
            return new String(buf);
        }
        @Override public void getChars(int start, int end, char[] dest, int off) {
            for (int i = start; i < end; i++) dest[off + (i - start)] = DOT;
        }
        @Override public String toString() {
            char[] buf = new char[length()];
            getChars(0, length(), buf, 0);
            return new String(buf);
        }
    }
}
