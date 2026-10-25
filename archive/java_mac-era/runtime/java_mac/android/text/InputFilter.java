// android.text.InputFilter -- character filter applied to EditText
// input. AOSP source lives at frameworks/base/core/java/android/text/
// InputFilter.java. The runtime contract is "given (source, dest)
// returns the substring that's allowed to be inserted, or null to
// accept as-is".
//
// Mac.apk hosts no real EditText input pipeline (text input is a stub
// for cocos2d-x's "tap once, enter a name" prompt that's never used in
// the games we target), so this file exists purely to satisfy
// ClassLoader lookups during onCreate when an app builds a filter
// array. The two AOSP-public nested classes are stubbed to "accept
// everything".
package android.text;

public interface InputFilter {
    CharSequence filter(CharSequence source, int start, int end,
                        Spanned dest, int dstart, int dend);

    class AllCaps implements InputFilter {
        public AllCaps() {}
        // AOSP AllCaps(Locale) (API 26+): locale-aware upper-casing. AndroidX +
        // apps that render all-caps text with a Locale construct this overload;
        // absent, `new InputFilter.AllCaps(locale)` -> NoSuchMethodError.
        public AllCaps(java.util.Locale locale) {}
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            return source != null
                ? source.subSequence(start, end).toString().toUpperCase()
                : null;
        }
    }

    class LengthFilter implements InputFilter {
        private final int max;
        public LengthFilter(int max) { this.max = max; }
        public int getMax() { return max; }
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            int keep = max - (dest != null ? dest.length() - (dend - dstart) : 0);
            if (keep <= 0)             return "";
            if (keep >= end - start)   return null;
            return source != null ? source.subSequence(start, start + keep) : null;
        }
    }
}
