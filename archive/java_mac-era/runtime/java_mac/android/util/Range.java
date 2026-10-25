// android.util.Range -- an immutable inclusive [lower, upper] interval over a
// Comparable type. AOSP frameworks/base core/java/android/util/Range.java.
// Created here because android.media.MediaCodecInfo's capability getters return
// Range<Integer>; mirrors AOSP's public surface exactly (clamp/contains/extend).
package android.util;

public final class Range<T extends Comparable<? super T>> {
    private final T mLower;
    private final T mUpper;

    public Range(T lower, T upper) {
        if (lower == null) throw new NullPointerException("lower must not be null");
        if (upper == null) throw new NullPointerException("upper must not be null");
        if (lower.compareTo(upper) > 0) {
            throw new IllegalArgumentException("lower must be less than or equal to upper");
        }
        mLower = lower;
        mUpper = upper;
    }

    public static <T extends Comparable<? super T>> Range<T> create(T lower, T upper) {
        return new Range<T>(lower, upper);
    }

    public T getLower() { return mLower; }
    public T getUpper() { return mUpper; }

    public boolean contains(T value) {
        boolean gteLower = value.compareTo(mLower) >= 0;
        boolean lteUpper = value.compareTo(mUpper) <= 0;
        return gteLower && lteUpper;
    }
    public boolean contains(Range<T> range) {
        boolean gteLower = range.mLower.compareTo(mLower) >= 0;
        boolean lteUpper = range.mUpper.compareTo(mUpper) <= 0;
        return gteLower && lteUpper;
    }

    public T clamp(T value) {
        if (value.compareTo(mLower) < 0) return mLower;
        if (value.compareTo(mUpper) > 0) return mUpper;
        return value;
    }

    public Range<T> intersect(Range<T> range) {
        int cmpLower = range.mLower.compareTo(mLower);
        int cmpUpper = range.mUpper.compareTo(mUpper);
        if (cmpLower <= 0 && cmpUpper >= 0) return this;
        T newLower = cmpLower <= 0 ? mLower : range.mLower;
        T newUpper = cmpUpper >= 0 ? mUpper : range.mUpper;
        return new Range<T>(newLower, newUpper);
    }
    public Range<T> intersect(T lower, T upper) {
        return intersect(new Range<T>(lower, upper));
    }

    public Range<T> extend(Range<T> range) {
        int cmpLower = range.mLower.compareTo(mLower);
        int cmpUpper = range.mUpper.compareTo(mUpper);
        T newLower = cmpLower <= 0 ? range.mLower : mLower;
        T newUpper = cmpUpper >= 0 ? range.mUpper : mUpper;
        return new Range<T>(newLower, newUpper);
    }
    public Range<T> extend(T lower, T upper) {
        return extend(new Range<T>(lower, upper));
    }
    public Range<T> extend(T value) {
        return extend(value, value);
    }

    @Override public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof Range)) return false;
        @SuppressWarnings("unchecked")
        Range<T> other = (Range<T>) obj;
        return mLower.equals(other.mLower) && mUpper.equals(other.mUpper);
    }
    @Override public int hashCode() {
        return mLower.hashCode() * 31 + mUpper.hashCode();
    }
    @Override public String toString() {
        return String.format("[%s, %s]", mLower, mUpper);
    }
}
