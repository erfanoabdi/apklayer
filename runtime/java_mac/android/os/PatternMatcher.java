// android.os.PatternMatcher -- a string matcher used primarily by IntentFilter
// data matching (scheme/host/path patterns). Fully functional: literal, prefix,
// and simple-glob matching are implemented to match AOSP semantics so
// IntentFilter path matching behaves correctly. (ADVANCED_GLOB / SUFFIX exist
// in AOSP as additional types; only the three public-API types are exposed.)
//
// AOSP ref: frameworks/base/core/java/android/os/PatternMatcher.java
package android.os;

public class PatternMatcher implements Parcelable {
    public static final int PATTERN_LITERAL = 0;
    public static final int PATTERN_PREFIX = 1;
    public static final int PATTERN_SIMPLE_GLOB = 2;
    // ADVANCED_GLOB (3) and SUFFIX (4) are hidden in AOSP; included so SDK code
    // that references the int values does not break, with glob fallback behavior.
    public static final int PATTERN_ADVANCED_GLOB = 3;
    public static final int PATTERN_SUFFIX = 4;

    private final String mPattern;
    private final int mType;

    public PatternMatcher(String pattern, int type) {
        mPattern = pattern;
        mType = type;
    }

    /** AOSP PatternMatcher(Parcel) -- public in AOSP (it is what
     *  PathPermission's own Parcel constructor chains to via super(src), see
     *  frameworks/base/core/java/android/content/pm/PathPermission.java:52).
     *  Previously the CREATOR below inlined this read; it now delegates here so
     *  the subclass has the super-constructor AOSP guarantees it. */
    public PatternMatcher(Parcel src) {
        mPattern = src.readString();
        mType = src.readInt();
    }

    /** AOSP's accessor for the pattern string is getPath() -- there is no
     *  getPattern() in the SDK at all, so app bytecode compiled against the
     *  real android.jar emits `invokevirtual getPath()` and got
     *  NoSuchMethodError from us. (The name is historical: PatternMatcher was
     *  introduced for IntentFilter's <data android:path*> attributes.) */
    public final String getPath() { return mPattern; }
    /** Non-AOSP alias for getPath(). Kept because
     *  android/net/wifi/WifiNetworkSpecifier.java on this tree calls it; an
     *  extra method is invisible to guest bytecode (an app can only call what
     *  its compile-time android.jar declared), so it is safe to carry until
     *  that caller is migrated. */
    public final String getPattern() { return mPattern; }
    public final int getType() { return mType; }

    public boolean match(String str) {
        return matchPattern(str, mPattern, mType);
    }

    @Override public String toString() {
        String type = "? ";
        switch (mType) {
            case PATTERN_LITERAL:      type = "LITERAL: "; break;
            case PATTERN_PREFIX:       type = "PREFIX: "; break;
            case PATTERN_SIMPLE_GLOB:  type = "GLOB: "; break;
            case PATTERN_ADVANCED_GLOB: type = "ADVANCED: "; break;
            case PATTERN_SUFFIX:       type = "SUFFIX: "; break;
        }
        return "PatternMatcher{" + type + mPattern + "}";
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPattern);
        dest.writeInt(mType);
    }

    public static final Parcelable.Creator<PatternMatcher> CREATOR =
        new Parcelable.Creator<PatternMatcher>() {
            @Override public PatternMatcher createFromParcel(Parcel source) {
                return new PatternMatcher(source);
            }
            @Override public PatternMatcher[] newArray(int size) {
                return new PatternMatcher[size];
            }
        };

    // ---- matching, faithful to AOSP's static helpers ----

    static boolean matchPattern(String match, String pattern, int type) {
        if (match == null) return false;
        if (type == PATTERN_LITERAL) {
            return pattern.equals(match);
        } else if (type == PATTERN_PREFIX) {
            return match.startsWith(pattern);
        } else if (type == PATTERN_SUFFIX) {
            return match.endsWith(pattern);
        } else if (type == PATTERN_SIMPLE_GLOB) {
            return matchGlobPattern(match, pattern);
        } else if (type == PATTERN_ADVANCED_GLOB) {
            // ADVANCED_GLOB uses a compiled regex on AOSP; we have only the raw
            // pattern. Fall back to simple-glob, which covers the common '.' / '*'
            // forms. Documented divergence: complex regexes will not match.
            return matchGlobPattern(match, pattern);
        }
        return false;
    }

    // Faithful port of AOSP PatternMatcher.matchPattern (SIMPLE_GLOB):
    // '*' matches zero-or-more of the PRECEDING literal char; a bare '.' is any
    // single char; '.*' is any sequence.
    private static boolean matchGlobPattern(String match, String pattern) {
        final int NP = pattern.length();
        final int NM = match.length();
        if (NP <= 0) {
            return NM <= 0;
        }
        int ip = 0, im = 0;
        char nextChar = pattern.charAt(0);
        while ((ip < NP) && (im < NM)) {
            char c = nextChar;
            ip++;
            nextChar = (ip < NP) ? pattern.charAt(ip) : 0;
            final boolean escaped = (c == '\\');
            if (escaped) {
                c = nextChar;
                ip++;
                nextChar = (ip < NP) ? pattern.charAt(ip) : 0;
            }
            if (nextChar == '*') {
                if (!escaped && c == '.') {
                    if (ip >= (NP - 1)) {
                        // at the end -> .* matches everything remaining
                        return true;
                    }
                    ip++;
                    nextChar = pattern.charAt(ip);
                    // Consume chars until we find the literal after .*
                    if (nextChar == '\\') {
                        ip++;
                        nextChar = (ip < NP) ? pattern.charAt(ip) : 0;
                    }
                    do {
                        if (match.charAt(im) == nextChar) break;
                        im++;
                    } while (im < NM);
                    if (im == NM) return false;
                    ip++;
                    nextChar = (ip < NP) ? pattern.charAt(ip) : 0;
                    im++;
                } else {
                    // Repeat the literal char c zero-or-more times.
                    while ((im < NM) && (match.charAt(im) == c)) im++;
                    ip++;
                    nextChar = (ip < NP) ? pattern.charAt(ip) : 0;
                }
            } else {
                if (c != '.' && match.charAt(im) != c) return false;
                im++;
            }
        }
        if (ip >= NP && im >= NM) {
            return true;
        }
        // Trailing ".*" with nothing left to match still succeeds.
        if ((ip == NP - 2) && (pattern.charAt(ip) == '.') && (pattern.charAt(ip + 1) == '*')) {
            return true;
        }
        return false;
    }
}
