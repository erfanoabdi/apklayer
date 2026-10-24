package android.media.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A content rating in the Android TV rating scheme -- a (domain, rating system,
 * main rating, sub-ratings) tuple with a canonical flat string form. API 21.
 *
 * Entirely self-contained string/value logic with no platform dependency, so it
 * is implemented in full, flatten/unflatten included. Mac.apk is not a TV
 * device (TvInputManager reports no inputs), so nothing here ever produces one
 * -- but an app that constructs and compares ratings, which is all this class
 * is for, gets correct answers.
 *
 * Source: frameworks/base/media/java/android/media/tv/TvContentRating.java
 * (android16-release).
 */
public final class TvContentRating {

    private static final String DELIMITER = "/";

    /** AOSP: "Rating constant denoting unrated content." */
    public static final TvContentRating UNRATED =
            new TvContentRating("com.android.tv", "", "", Collections.<String>emptyList());

    private final String mDomain;
    private final String mRatingSystem;
    private final String mRating;
    private final List<String> mSubRatings;

    private TvContentRating(String domain, String ratingSystem, String rating,
            List<String> subRatings) {
        mDomain = domain;
        mRatingSystem = ratingSystem;
        mRating = rating;
        mSubRatings = Collections.unmodifiableList(new ArrayList<>(subRatings));
    }

    public static TvContentRating createRating(String domain, String ratingSystem,
            String rating, String... subRatings) {
        Objects.requireNonNull(domain, "domain cannot be null");
        Objects.requireNonNull(rating, "rating cannot be null");
        List<String> subs = (subRatings == null)
                ? Collections.<String>emptyList() : Arrays.asList(subRatings);
        return new TvContentRating(domain, ratingSystem, rating, subs);
    }

    public String getDomain() { return mDomain; }
    public String getRatingSystem() { return mRatingSystem; }
    public String getMainRating() { return mRating; }
    public List<String> getSubRatings() { return mSubRatings; }

    /** AOSP's canonical form: domain/system/rating[/sub]... with sub-ratings
     *  sorted, so two equivalent ratings flatten identically
     *  (TvContentRating.java, flattenToString). */
    public String flattenToString() {
        StringBuilder builder = new StringBuilder();
        builder.append(mDomain);
        builder.append(DELIMITER);
        builder.append(mRatingSystem);
        builder.append(DELIMITER);
        builder.append(mRating);
        if (!mSubRatings.isEmpty()) {
            List<String> sorted = new ArrayList<>(mSubRatings);
            Collections.sort(sorted);
            for (String sub : sorted) {
                builder.append(DELIMITER);
                builder.append(sub);
            }
        }
        return builder.toString();
    }

    /** AOSP throws IllegalArgumentException on a string with fewer than three
     *  components (TvContentRating.java, unflattenFromString). */
    public static TvContentRating unflattenFromString(String ratingString) {
        if (ratingString == null || ratingString.isEmpty()) {
            throw new IllegalArgumentException("Empty rating string");
        }
        String[] parts = ratingString.split(DELIMITER);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid rating string: " + ratingString);
        }
        List<String> subs = new ArrayList<>();
        for (int i = 3; i < parts.length; i++) subs.add(parts[i]);
        return new TvContentRating(parts[0], parts[1], parts[2], subs);
    }

    /** AOSP: "Returns true if this rating has the same main rating as the
     *  specified rating and when this rating's sub-ratings contain the
     *  specified rating's sub-ratings." */
    public boolean contains(TvContentRating rating) {
        Objects.requireNonNull(rating, "rating cannot be null");
        if (!rating.getMainRating().equals(mRating)) return false;
        if (!Objects.equals(rating.getDomain(), mDomain)
                || !Objects.equals(rating.getRatingSystem(), mRatingSystem)) {
            return false;
        }
        return mSubRatings.containsAll(rating.getSubRatings());
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TvContentRating)) return false;
        TvContentRating o = (TvContentRating) obj;
        return contains(o) && o.contains(this);
    }

    @Override public int hashCode() {
        return Objects.hash(mDomain, mRatingSystem, mRating);
    }

    @Override public String toString() { return flattenToString(); }
}
