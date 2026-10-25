// android.speech.AlternativeSpans (API 33) -- a bag of AlternativeSpan for one
// recognition result. AOSP:
// frameworks/base/core/java/android/speech/AlternativeSpans.java
package android.speech;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AlternativeSpans implements Parcelable {
    private final List<AlternativeSpan> mSpans;

    public AlternativeSpans(List<AlternativeSpan> spans) {
        Objects.requireNonNull(spans);
        mSpans = Collections.unmodifiableList(new ArrayList<>(spans));
    }

    public List<AlternativeSpan> getSpans() { return mSpans; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mSpans);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlternativeSpans)) return false;
        return mSpans.equals(((AlternativeSpans) o).mSpans);
    }

    @Override public int hashCode() { return mSpans.hashCode(); }

    @Override public String toString() { return "AlternativeSpans { spans = " + mSpans + " }"; }

    public static final Parcelable.Creator<AlternativeSpans> CREATOR =
            new Parcelable.Creator<AlternativeSpans>() {
        @Override public AlternativeSpans createFromParcel(Parcel in) {
            List<AlternativeSpan> spans = in.createTypedArrayList(AlternativeSpan.CREATOR);
            return new AlternativeSpans(spans == null ? new ArrayList<>() : spans);
        }
        @Override public AlternativeSpans[] newArray(int size) { return new AlternativeSpans[size]; }
    };
}
