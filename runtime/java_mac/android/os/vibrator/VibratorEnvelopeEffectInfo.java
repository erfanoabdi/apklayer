// android.os.vibrator.VibratorEnvelopeEffectInfo (API 36) -- "the vibrator's
// capabilities and limitations for envelope effects": the maximum number of
// control points, and the min/max duration between two of them.
//
// AOSP ref: frameworks/base/core/java/android/os/vibrator/
// VibratorEnvelopeEffectInfo.java (android16-release). Ported field-for-field,
// including the derived getMaxDurationMillis(). AOSP's own contract for a
// device WITHOUT envelope-effect support is that every getter returns 0, which
// is exactly what Vibrator.getEnvelopeEffectInfo() constructs here -- so the
// no-haptics answer needs no special case, it is the documented one.
package android.os.vibrator;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public final class VibratorEnvelopeEffectInfo implements Parcelable {
    private final int mMaxSize;
    private final long mMinControlPointDurationMillis;
    private final long mMaxControlPointDurationMillis;

    VibratorEnvelopeEffectInfo(Parcel in) {
        mMaxSize = in.readInt();
        mMinControlPointDurationMillis = in.readLong();
        mMaxControlPointDurationMillis = in.readLong();
    }

    /** AOSP marks this @hide but public; Vibrator constructs one through it. */
    public VibratorEnvelopeEffectInfo(int maxSize,
                                      long minControlPointDurationMillis,
                                      long maxControlPointDurationMillis) {
        mMaxSize = maxSize;
        mMinControlPointDurationMillis = minControlPointDurationMillis;
        mMaxControlPointDurationMillis = maxControlPointDurationMillis;
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMaxSize);
        dest.writeLong(mMinControlPointDurationMillis);
        dest.writeLong(mMaxControlPointDurationMillis);
    }

    @Override public int describeContents() { return 0; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VibratorEnvelopeEffectInfo)) return false;
        VibratorEnvelopeEffectInfo other = (VibratorEnvelopeEffectInfo) o;
        return mMaxSize == other.mMaxSize
                && mMinControlPointDurationMillis == other.mMinControlPointDurationMillis
                && mMaxControlPointDurationMillis == other.mMaxControlPointDurationMillis;
    }

    @Override public int hashCode() {
        return Objects.hash(mMaxSize, mMinControlPointDurationMillis,
                            mMaxControlPointDurationMillis);
    }

    @Override public String toString() {
        return "VibratorEnvelopeEffectInfo{"
                + ", mMaxSize=" + mMaxSize
                + ", mMinControlPointDurationMillis=" + mMinControlPointDurationMillis
                + ", mMaxControlPointDurationMillis=" + mMaxControlPointDurationMillis
                + '}';
    }

    public static final Creator<VibratorEnvelopeEffectInfo> CREATOR =
            new Creator<VibratorEnvelopeEffectInfo>() {
                @Override public VibratorEnvelopeEffectInfo createFromParcel(Parcel in) {
                    return new VibratorEnvelopeEffectInfo(in);
                }
                @Override public VibratorEnvelopeEffectInfo[] newArray(int size) {
                    return new VibratorEnvelopeEffectInfo[size];
                }
            };

    /** AOSP: `mMaxSize * mMaxControlPointDurationMillis`, so it is 0 whenever
     *  envelope effects are unsupported. */
    public long getMaxDurationMillis() { return mMaxSize * mMaxControlPointDurationMillis; }

    public int getMaxSize() { return mMaxSize; }

    public long getMinControlPointDurationMillis() { return mMinControlPointDurationMillis; }

    public long getMaxControlPointDurationMillis() { return mMaxControlPointDurationMillis; }
}
