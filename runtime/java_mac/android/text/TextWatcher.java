// android.text.TextWatcher -- callback interface for EditText changes.
// Cocos2d-x's TextInputWraper implements this for its on-screen IME
// translation.
//
// AOSP: `public interface TextWatcher extends NoCopySpan` (frameworks/base
// core/java/android/text/TextWatcher.java). TextWatchers are attached to an
// Editable AS SPANS (SpannableStringBuilder.replace() finds them via
// getSpans(..., TextWatcher.class) to fire before/onTextChanged/after), and
// NoCopySpan is what stops a watcher hookup from leaking into an unrelated
// copy when that Editable is sliced (subSequence) or copy-constructed.
package android.text;

public interface TextWatcher extends NoCopySpan {
    void beforeTextChanged(CharSequence s, int start, int count, int after);
    void onTextChanged(CharSequence s, int start, int before, int count);
    void afterTextChanged(Editable s);
}
