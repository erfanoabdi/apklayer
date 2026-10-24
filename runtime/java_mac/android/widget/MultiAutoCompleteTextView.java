// android.widget.MultiAutoCompleteTextView -- AutoCompleteTextView that
// completes the current token (delimited via a Tokenizer) rather than the
// whole field, so a user can autocomplete several comma-separated entries.
// Was a bare `extends Object` stub missing the nested Tokenizer /
// CommaTokenizer types. AOSP:
//   frameworks/base/core/java/android/widget/MultiAutoCompleteTextView.java
//   public class MultiAutoCompleteTextView extends AutoCompleteTextView
package android.widget;

import android.content.Context;
import android.text.Editable;

public class MultiAutoCompleteTextView extends AutoCompleteTextView {
    private Tokenizer mTokenizer;

    public MultiAutoCompleteTextView(Context context) { super(context); }
    public MultiAutoCompleteTextView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }
    /** AOSP API 21 four-argument constructor: chains to the parent's four-argument
     *  form so defStyleRes reaches TextView (AOSP does the same, e.g. EditText.java:100-101).
     *  Without it an app subclass calling the four-argument super() dies with
     *  NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public MultiAutoCompleteTextView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    public MultiAutoCompleteTextView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setTokenizer(Tokenizer t) { mTokenizer = t; }

    @Override public boolean enoughToFilter() {
        Editable text = getText();
        int end = getSelectionEnd();
        if (end < 0 || mTokenizer == null) return false;
        int start = mTokenizer.findTokenStart(text, end);
        return end - start >= getThreshold();
    }

    @Override protected void performFiltering(CharSequence text, int keyCode) {
        if (enoughToFilter()) {
            int end = getSelectionEnd();
            int start = mTokenizer.findTokenStart(text, end);
            performFiltering(text, start, end, keyCode);
        } else {
            dismissDropDown();
        }
    }

    protected void performFiltering(CharSequence text, int start, int end, int keyCode) {
        // Filter on just the current token's substring.
        super.performFiltering(text.subSequence(start, end), keyCode);
    }

    @Override protected void replaceText(CharSequence text) {
        clearComposingTextIfPossible();
        Editable editable = getText();
        int end = getSelectionEnd();
        int start = mTokenizer != null ? mTokenizer.findTokenStart(editable, end) : 0;

        if (mTokenizer != null) {
            CharSequence terminated = mTokenizer.terminateToken(text);
            editable.replace(start, end, terminated);
        } else {
            editable.replace(start, end, text);
        }
    }

    private void clearComposingTextIfPossible() {
        // No IME composing region in our framework; nothing to clear.
    }

    public interface Tokenizer {
        int findTokenStart(CharSequence text, int cursor);
        int findTokenEnd(CharSequence text, int cursor);
        CharSequence terminateToken(CharSequence text);
    }

    // AOSP CommaTokenizer: tokens delimited by ", " (comma + space).
    public static class CommaTokenizer implements Tokenizer {
        @Override public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;
            while (i > 0 && text.charAt(i - 1) != ',') i--;
            while (i < cursor && text.charAt(i) == ' ') i++;
            return i;
        }
        @Override public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();
            while (i < len) {
                if (text.charAt(i) == ',') return i;
                i++;
            }
            return len;
        }
        @Override public CharSequence terminateToken(CharSequence text) {
            int i = text.length();
            while (i > 0 && text.charAt(i - 1) == ' ') i--;
            if (i > 0 && text.charAt(i - 1) == ',') {
                return text;
            }
            return text + ", ";
        }
    }
}
