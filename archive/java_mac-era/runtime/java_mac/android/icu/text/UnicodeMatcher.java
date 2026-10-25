// android.icu.text.UnicodeMatcher -- ICU4J interface for objects that match a
// range of text. ICU4J:
//   developer.android.com/reference/android/icu/text/UnicodeMatcher
//   public interface UnicodeMatcher
//
// UnicodeFilter / UnicodeSet implement this. We expose the membership query
// (matches a single code point) that the filter/set hierarchy needs; the
// Transliterator-oriented matches(Replaceable,...) machinery is out of scope
// and not declared here.
package android.icu.text;

public interface UnicodeMatcher {
    int U_MATCH = 2;
    int U_PARTIAL_MATCH = 1;
    int U_MISMATCH = 0;

    boolean matchesIndexValue(int v);
}
