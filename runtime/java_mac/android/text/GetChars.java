// android.text.GetChars -- a CharSequence that can bulk-copy a range of its
// characters into a char[] (the fast path TextLayout / BoringLayout use instead
// of charAt per index). AOSP: Spanned/Spannable/Editable all extend it.
// Mirrors frameworks/base/core/java/android/text/GetChars.java.
package android.text;

public interface GetChars extends CharSequence {
    void getChars(int start, int end, char[] dest, int destoff);
}
