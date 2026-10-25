// android.icu.text.UnicodeFilter -- ICU4J abstract base for code-point
// predicates (UnicodeSet's superclass). ICU4J:
//   developer.android.com/reference/android/icu/text/UnicodeFilter
//   public abstract class UnicodeFilter implements UnicodeMatcher
//
// The single abstract membership method is contains(int); concrete subclasses
// (UnicodeSet) implement it. matchesIndexValue is given a portable default
// expressed in terms of contains() so callers that route through the
// UnicodeMatcher interface still work.
package android.icu.text;

public abstract class UnicodeFilter implements UnicodeMatcher {
    protected UnicodeFilter() {}

    public abstract boolean contains(int c);

    @Override
    public boolean matchesIndexValue(int v) {
        // ICU's filter indexes membership by the low byte of a code point; for
        // our purposes "does any code point with this low byte belong" is well
        // approximated by a direct contains() on v itself, which is what the
        // single-code-point query path needs.
        return contains(v);
    }
}
