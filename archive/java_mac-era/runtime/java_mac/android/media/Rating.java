// android.media.Rating -- a user/content rating (stars, thumbs, percent). AOSP
// frameworks/base media/java/android/media/Rating.java. Immutable, built via the
// static newXxxRating factories. A pure data holder; fully serviceable.
package android.media;

public final class Rating implements android.os.Parcelable {
    public static final int RATING_NONE = 0;
    public static final int RATING_HEART = 1;
    public static final int RATING_THUMB_UP_DOWN = 2;
    public static final int RATING_3_STARS = 3;
    public static final int RATING_4_STARS = 4;
    public static final int RATING_5_STARS = 5;
    public static final int RATING_PERCENTAGE = 6;

    private static final float RATING_NOT_RATED = -1.0f;

    private final int mRatingStyle;
    private final float mRatingValue;

    private Rating(int ratingStyle, float rating) {
        mRatingStyle = ratingStyle;
        mRatingValue = rating;
    }

    public static Rating newUnratedRating(int ratingStyle) {
        if (ratingStyle < RATING_HEART || ratingStyle > RATING_PERCENTAGE) return null;
        return new Rating(ratingStyle, RATING_NOT_RATED);
    }
    public static Rating newHeartRating(boolean hasHeart) {
        return new Rating(RATING_HEART, hasHeart ? 1.0f : 0.0f);
    }
    public static Rating newThumbRating(boolean thumbIsUp) {
        return new Rating(RATING_THUMB_UP_DOWN, thumbIsUp ? 1.0f : 0.0f);
    }
    public static Rating newStarRating(int starRatingStyle, float starRating) {
        if (starRatingStyle != RATING_3_STARS && starRatingStyle != RATING_4_STARS
                && starRatingStyle != RATING_5_STARS) return null;
        float max = starRatingStyle - 1;  // 3_STARS=3 -> [0..3]; AOSP uses style as max
        if (starRatingStyle == RATING_3_STARS) max = 3f;
        else if (starRatingStyle == RATING_4_STARS) max = 4f;
        else max = 5f;
        if (starRating < 0f || starRating > max) return null;
        return new Rating(starRatingStyle, starRating);
    }
    public static Rating newPercentageRating(float percent) {
        if (percent < 0f || percent > 100f) return null;
        return new Rating(RATING_PERCENTAGE, percent);
    }

    public boolean isRated() { return mRatingValue >= 0.0f; }
    public int getRatingStyle() { return mRatingStyle; }
    public boolean hasHeart() {
        return mRatingStyle == RATING_HEART && mRatingValue == 1.0f;
    }
    public boolean isThumbUp() {
        return mRatingStyle == RATING_THUMB_UP_DOWN && mRatingValue == 1.0f;
    }
    public float getStarRating() {
        switch (mRatingStyle) {
            case RATING_3_STARS:
            case RATING_4_STARS:
            case RATING_5_STARS:
                return isRated() ? mRatingValue : -1.0f;
            default: return -1.0f;
        }
    }
    public float getPercentRating() {
        return (mRatingStyle == RATING_PERCENTAGE && isRated()) ? mRatingValue : -1.0f;
    }

    @Override public int describeContents() { return mRatingStyle; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mRatingStyle);
        dest.writeFloat(mRatingValue);
    }
    public static final android.os.Parcelable.Creator<Rating> CREATOR =
        new android.os.Parcelable.Creator<Rating>() {
            @Override public Rating createFromParcel(android.os.Parcel in) {
                return new Rating(in.readInt(), in.readFloat());
            }
            @Override public Rating[] newArray(int size) {
                return new Rating[size];
            }
        };
}
