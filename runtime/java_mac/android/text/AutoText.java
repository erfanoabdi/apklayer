// android.text.AutoText -- the framework's built-in typo-correction dictionary
// lookup ("teh" -> "the"). Public API since API 1 and still present in API 36;
// it was absent here entirely, so `AutoText.get(...)` was a
// NoClassDefFoundError rather than "no correction found".
//
// Mirrors frameworks/base/core/java/android/text/AutoText.java's PUBLIC
// surface. AOSP's implementation reads a per-locale compressed trie out of
// res/xml/autotext.xml (com.android.internal.R.xml.autotext), a framework
// resource Mac.apk does not ship and cannot synthesize -- there is no
// upstream-equivalent word list to route to on macOS either, so this answers
// "no correction" for every word, which is EXACTLY what a device answers for
// every locale whose autotext.xml is empty (most of them: AOSP ships a
// non-empty list for en_US and a handful of others only) and for every word
// not in the list.
//
// That is a real answer, not a placeholder: getSize() returning 0 is the
// documented signal that this locale has no auto-text data, and every caller
// checks the null return from get() anyway.
package android.text;

import android.view.View;

public class AutoText {

    private AutoText() { }

    /**
     * AOSP AutoText.get(CharSequence src, int start, int end, View view):
     * the replacement for src[start, end) in the view's locale, or null when
     * there is none.
     */
    public static String get(CharSequence src, int start, int end, View view) {
        return null;
    }

    /**
     * AOSP AutoText.getSize(View view): "the size of the auto-text dictionary
     * for the view's locale" -- 0 means there is none.
     */
    public static int getSize(View view) {
        return 0;
    }
}
