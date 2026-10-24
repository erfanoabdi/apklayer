// android.content.UriRelativeFilter (API 35) -- one constraint on a URI's
// path, query or fragment, used inside a UriRelativeFilterGroup to narrow an
// IntentFilter beyond scheme/host.
//
// AOSP ref: frameworks/base/core/java/android/content/UriRelativeFilter.java
// (android16-release). matchData() and the query-splitting helper are ported
// verbatim -- including that a QUERY filter matches if ANY "&"- (or ";"-)
// separated parameter matches, and that a malformed uriPart yields false.
package android.content;

import android.net.Uri;
import android.os.PatternMatcher;

import java.util.Objects;

public final class UriRelativeFilter {
    /** Tag name AOSP writes for this element inside an IntentFilter's XML. */
    static final String URI_RELATIVE_FILTER_STR = "uriRelativeFilter";

    public static final int PATH = 0;
    public static final int QUERY = 1;
    public static final int FRAGMENT = 2;

    private final int mUriPart;
    private final int mPatternType;
    private final String mFilter;

    public UriRelativeFilter(int uriPart, int patternType, String filter) {
        mUriPart = uriPart;
        mPatternType = patternType;
        mFilter = Objects.requireNonNull(filter);
    }

    public int getUriPart() { return mUriPart; }
    public int getPatternType() { return mPatternType; }
    public String getFilter() { return mFilter; }

    /** AOSP: build a PatternMatcher from (filter, patternType) and apply it to
     *  the selected part of the URI. */
    public boolean matchData(Uri data) {
        PatternMatcher pe = new PatternMatcher(mFilter, mPatternType);
        switch (getUriPart()) {
            case PATH:     return pe.match(data.getPath());
            case QUERY:    return matchQuery(pe, data.getQuery());
            case FRAGMENT: return pe.match(data.getFragment());
            default:       return false;
        }
    }

    private boolean matchQuery(PatternMatcher pe, String query) {
        if (query != null) {
            String[] params = query.split("&");
            if (params.length == 1) {
                params = query.split(";");
            }
            for (int i = 0; i < params.length; i++) {
                if (pe.match(params[i])) return true;
            }
        }
        return false;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UriRelativeFilter)) return false;
        UriRelativeFilter that = (UriRelativeFilter) o;
        return mUriPart == that.mUriPart
                && mPatternType == that.mPatternType
                && mFilter.equals(that.mFilter);
    }

    @Override public int hashCode() {
        return Objects.hash(mUriPart, mPatternType, mFilter);
    }

    @Override public String toString() {
        return "UriRelativeFilter{part=" + mUriPart
                + ", patternType=" + mPatternType
                + ", filter=" + mFilter + '}';
    }
}
