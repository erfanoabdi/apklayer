// android.content.UriRelativeFilterGroup (API 35) -- an ALLOW or BLOCK group
// of UriRelativeFilters attached to an IntentFilter. All filters in a group
// must match for the group to match ("matchData ... true if ALL filters
// match"), and the FIRST matching group decides.
//
// AOSP ref:
// frameworks/base/core/java/android/content/UriRelativeFilterGroup.java
// (android16-release). The matching logic is ported verbatim, including the
// empty-group == no-match rule. The @hide parcel/XML conversion helpers, which
// exist to carry groups over binder to the PackageManagerService, are omitted:
// their parcel types are @hide and there is no PMS to carry them to.
package android.content;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class UriRelativeFilterGroup {
    public static final int ACTION_ALLOW = 0;
    public static final int ACTION_BLOCK = 1;

    private final int mAction;
    private final ArrayList<UriRelativeFilter> mUriRelativeFilters =
            new ArrayList<UriRelativeFilter>();

    public UriRelativeFilterGroup(int action) {
        mAction = action;
    }

    public int getAction() { return mAction; }

    /** AOSP :168 -- de-duplicating add (its backing store is an ArraySet). */
    public void addUriRelativeFilter(UriRelativeFilter uriRelativeFilter) {
        Objects.requireNonNull(uriRelativeFilter);
        if (!mUriRelativeFilters.contains(uriRelativeFilter)) {
            mUriRelativeFilters.add(uriRelativeFilter);
        }
    }

    /** AOSP :179 -- an UNMODIFIABLE view, not a copy. */
    public Collection<UriRelativeFilter> getUriRelativeFilters() {
        return Collections.unmodifiableCollection(mUriRelativeFilters);
    }

    /** AOSP :190 -- ALL filters must match; an empty group matches nothing. */
    public boolean matchData(Uri data) {
        if (mUriRelativeFilters.isEmpty()) {
            return false;
        }
        for (UriRelativeFilter filter : mUriRelativeFilters) {
            if (!filter.matchData(data)) {
                return false;
            }
        }
        return true;
    }

    /** AOSP's @hide static helper: the FIRST group that matches decides, and
     *  its action decides allow vs block; no matching group means no match. */
    public static boolean matchGroupsToUri(List<UriRelativeFilterGroup> groups, Uri uri) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).matchData(uri)) {
                return groups.get(i).getAction() == ACTION_ALLOW;
            }
        }
        return false;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UriRelativeFilterGroup)) return false;
        UriRelativeFilterGroup that = (UriRelativeFilterGroup) o;
        return mAction == that.mAction && mUriRelativeFilters.equals(that.mUriRelativeFilters);
    }

    @Override public int hashCode() {
        return Objects.hash(mAction, mUriRelativeFilters);
    }

    @Override public String toString() {
        return "UriRelativeFilterGroup{action=" + mAction
                + ", filters=" + mUriRelativeFilters + '}';
    }
}
