// android.speech.AlternativeSpan (API 33) -- alternative transcriptions for a
// span of a recognition result. AOSP:
// frameworks/base/core/java/android/speech/AlternativeSpan.java
package android.speech;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AlternativeSpan implements Parcelable {
    private final int mStartPosition;
    private final int mEndPosition;
    private final List<String> mAlternatives;

    /**
     * AOSP validates in the constructor and throws IllegalArgumentException
     * rather than storing a nonsensical span: an inverted or negative range
     * would otherwise surface much later as an out-of-bounds substring in
     * whatever renders the result.
     */
    public AlternativeSpan(int startPosition, int endPosition, List<String> alternatives) {
        Objects.requireNonNull(alternatives);
        if (startPosition < 0 || endPosition < 0 || startPosition >= endPosition) {
            throw new IllegalArgumentException("Both START and END positions must be non-negative"
                    + " and END must be greater than START. Got START=" + startPosition
                    + ", END=" + endPosition);
        }
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("The list of alternatives must not be empty.");
        }
        mStartPosition = startPosition;
        mEndPosition = endPosition;
        mAlternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
    }

    public int getStartPosition() { return mStartPosition; }

    public int getEndPosition() { return mEndPosition; }

    public List<String> getAlternatives() { return mAlternatives; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStartPosition);
        dest.writeInt(mEndPosition);
        dest.writeStringList(mAlternatives);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlternativeSpan)) return false;
        AlternativeSpan that = (AlternativeSpan) o;
        return mStartPosition == that.mStartPosition
                && mEndPosition == that.mEndPosition
                && mAlternatives.equals(that.mAlternatives);
    }

    @Override public int hashCode() {
        return Objects.hash(mStartPosition, mEndPosition, mAlternatives);
    }

    @Override public String toString() {
        return "AlternativeSpan { startPosition = " + mStartPosition
                + ", endPosition = " + mEndPosition
                + ", alternatives = " + mAlternatives + " }";
    }

    public static final Parcelable.Creator<AlternativeSpan> CREATOR =
            new Parcelable.Creator<AlternativeSpan>() {
        @Override public AlternativeSpan createFromParcel(Parcel in) {
            int start = in.readInt();
            int end = in.readInt();
            List<String> alts = new ArrayList<>();
            in.readStringList(alts);
            return new AlternativeSpan(start, end, alts);
        }
        @Override public AlternativeSpan[] newArray(int size) { return new AlternativeSpan[size]; }
    };
}
