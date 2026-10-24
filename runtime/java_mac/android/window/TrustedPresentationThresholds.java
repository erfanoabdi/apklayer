package android.window;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * AOSP android.window.TrustedPresentationThresholds (API 34).
 *
 * The thresholds a window must satisfy -- a minimum fraction of it on screen, a
 * minimum alpha, and for a minimum duration -- before the system reports it as
 * "trusted presented". Apps use it to know their content was really shown to the
 * user (anti-overlay / anti-tapjacking checks) before enabling a sensitive action.
 *
 * Mac.apk stores the thresholds faithfully; see WindowManager's listener pair for
 * what we can and cannot answer about them.
 */
public final class TrustedPresentationThresholds implements Parcelable {
    private final float mMinAlpha;
    private final float mMinFractionRendered;
    private final int   mStabilityRequirementMs;

    public TrustedPresentationThresholds(float minAlpha, float minFractionRendered,
                                         int stabilityRequirementMs) {
        // AOSP validates in the constructor and throws for out-of-range values.
        if (!(minAlpha > 0f)) {
            throw new IllegalArgumentException("minAlpha must be greater than 0");
        }
        if (!(minFractionRendered > 0f)) {
            throw new IllegalArgumentException("minFractionRendered must be greater than 0");
        }
        if (stabilityRequirementMs < 1) {
            throw new IllegalArgumentException("stabilityRequirementMs must be at least 1");
        }
        mMinAlpha = minAlpha;
        mMinFractionRendered = minFractionRendered;
        mStabilityRequirementMs = stabilityRequirementMs;
    }

    public float getMinAlpha()             { return mMinAlpha; }
    public float getMinFractionRendered()  { return mMinFractionRendered; }
    public int   getStabilityRequirementMillis() { return mStabilityRequirementMs; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(mMinAlpha);
        dest.writeFloat(mMinFractionRendered);
        dest.writeInt(mStabilityRequirementMs);
    }

    public static final Parcelable.Creator<TrustedPresentationThresholds> CREATOR =
            new Parcelable.Creator<TrustedPresentationThresholds>() {
        @Override public TrustedPresentationThresholds createFromParcel(Parcel in) {
            return new TrustedPresentationThresholds(in.readFloat(), in.readFloat(), in.readInt());
        }
        @Override public TrustedPresentationThresholds[] newArray(int size) {
            return new TrustedPresentationThresholds[size];
        }
    };

    @Override public String toString() {
        return "TrustedPresentationThresholds{minAlpha=" + mMinAlpha
             + ", minFractionRendered=" + mMinFractionRendered
             + ", stabilityRequirementMs=" + mStabilityRequirementMs + "}";
    }
}
