package android.location;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A set of requirements used to select a location provider.
 *
 * Mac.apk registers no location providers, so nothing scores a Criteria against
 * a provider list -- but the object itself is pure data and is faithful here,
 * including the copy constructor, the range checks, and the non-obvious
 * setAccuracy/getAccuracy mapping onto the horizontal-accuracy field (AOSP
 * stores only mHorizontalAccuracy; the older ACCURACY_FINE/COARSE pair is a
 * lossy view of it). Reimplementing that mapping matters because an app that
 * round-trips setAccuracy -> getAccuracy must see AOSP's answer, and the naive
 * "store what you were given" version does not produce it.
 *
 * Source: frameworks/base/location/java/android/location/Criteria.java
 */
public class Criteria implements Parcelable {

    public static final int NO_REQUIREMENT = 0;

    public static final int POWER_LOW = 1;
    public static final int POWER_MEDIUM = 2;
    public static final int POWER_HIGH = 3;

    public static final int ACCURACY_FINE = 1;
    public static final int ACCURACY_COARSE = 2;

    public static final int ACCURACY_LOW = 1;
    public static final int ACCURACY_MEDIUM = 2;
    public static final int ACCURACY_HIGH = 3;

    private int mHorizontalAccuracy = NO_REQUIREMENT;
    private int mVerticalAccuracy = NO_REQUIREMENT;
    private int mSpeedAccuracy = NO_REQUIREMENT;
    private int mBearingAccuracy = NO_REQUIREMENT;
    private int mPowerRequirement = NO_REQUIREMENT;
    private boolean mAltitudeRequired = false;
    private boolean mBearingRequired = false;
    private boolean mSpeedRequired = false;
    private boolean mCostAllowed = false;

    public Criteria() { }

    public Criteria(Criteria criteria) {
        mHorizontalAccuracy = criteria.mHorizontalAccuracy;
        mVerticalAccuracy = criteria.mVerticalAccuracy;
        mSpeedAccuracy = criteria.mSpeedAccuracy;
        mBearingAccuracy = criteria.mBearingAccuracy;
        mPowerRequirement = criteria.mPowerRequirement;
        mAltitudeRequired = criteria.mAltitudeRequired;
        mBearingRequired = criteria.mBearingRequired;
        mSpeedRequired = criteria.mSpeedRequired;
        mCostAllowed = criteria.mCostAllowed;
    }

    /** AOSP: Preconditions.checkArgumentInRange(v, NO_REQUIREMENT, ACCURACY_HIGH, name). */
    private static int checkAccuracy(int v) {
        if (v < NO_REQUIREMENT || v > ACCURACY_HIGH) {
            throw new IllegalArgumentException("accuracy out of range of ["
                    + NO_REQUIREMENT + ", " + ACCURACY_HIGH + "]: is " + v);
        }
        return v;
    }

    public void setHorizontalAccuracy(int accuracy) {
        mHorizontalAccuracy = checkAccuracy(accuracy);
    }

    public int getHorizontalAccuracy() { return mHorizontalAccuracy; }

    public void setVerticalAccuracy(int accuracy) {
        mVerticalAccuracy = checkAccuracy(accuracy);
    }

    public int getVerticalAccuracy() { return mVerticalAccuracy; }

    public void setSpeedAccuracy(int accuracy) {
        mSpeedAccuracy = checkAccuracy(accuracy);
    }

    public int getSpeedAccuracy() { return mSpeedAccuracy; }

    public void setBearingAccuracy(int accuracy) {
        mBearingAccuracy = checkAccuracy(accuracy);
    }

    public int getBearingAccuracy() { return mBearingAccuracy; }

    /**
     * Legacy view of the horizontal-accuracy requirement: FINE maps to
     * ACCURACY_HIGH, COARSE to ACCURACY_LOW, per AOSP.
     */
    public void setAccuracy(int accuracy) {
        if (accuracy < NO_REQUIREMENT || accuracy > ACCURACY_COARSE) {
            throw new IllegalArgumentException("accuracy out of range of ["
                    + NO_REQUIREMENT + ", " + ACCURACY_COARSE + "]: is " + accuracy);
        }
        switch (accuracy) {
            case NO_REQUIREMENT:
                setHorizontalAccuracy(NO_REQUIREMENT);
                break;
            case ACCURACY_FINE:
                setHorizontalAccuracy(ACCURACY_HIGH);
                break;
            case ACCURACY_COARSE:
                setHorizontalAccuracy(ACCURACY_LOW);
                break;
            default:
                break;
        }
    }

    /** AOSP returns COARSE for anything below ACCURACY_HIGH, including NO_REQUIREMENT. */
    public int getAccuracy() {
        return mHorizontalAccuracy >= ACCURACY_HIGH ? ACCURACY_FINE : ACCURACY_COARSE;
    }

    public void setPowerRequirement(int powerRequirement) {
        if (powerRequirement < NO_REQUIREMENT || powerRequirement > POWER_HIGH) {
            throw new IllegalArgumentException("powerRequirement out of range of ["
                    + NO_REQUIREMENT + ", " + POWER_HIGH + "]: is " + powerRequirement);
        }
        mPowerRequirement = powerRequirement;
    }

    public int getPowerRequirement() { return mPowerRequirement; }

    public void setCostAllowed(boolean costAllowed) { mCostAllowed = costAllowed; }

    public boolean isCostAllowed() { return mCostAllowed; }

    public void setAltitudeRequired(boolean altitudeRequired) {
        mAltitudeRequired = altitudeRequired;
    }

    public boolean isAltitudeRequired() { return mAltitudeRequired; }

    public void setSpeedRequired(boolean speedRequired) { mSpeedRequired = speedRequired; }

    public boolean isSpeedRequired() { return mSpeedRequired; }

    public void setBearingRequired(boolean bearingRequired) {
        mBearingRequired = bearingRequired;
    }

    public boolean isBearingRequired() { return mBearingRequired; }

    public static final Creator<Criteria> CREATOR = new Creator<Criteria>() {
        @Override public Criteria createFromParcel(Parcel in) {
            Criteria c = new Criteria();
            c.mHorizontalAccuracy = in.readInt();
            c.mVerticalAccuracy = in.readInt();
            c.mSpeedAccuracy = in.readInt();
            c.mBearingAccuracy = in.readInt();
            c.mPowerRequirement = in.readInt();
            c.mAltitudeRequired = in.readInt() != 0;
            c.mBearingRequired = in.readInt() != 0;
            c.mSpeedRequired = in.readInt() != 0;
            c.mCostAllowed = in.readInt() != 0;
            return c;
        }
        @Override public Criteria[] newArray(int size) { return new Criteria[size]; }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mHorizontalAccuracy);
        parcel.writeInt(mVerticalAccuracy);
        parcel.writeInt(mSpeedAccuracy);
        parcel.writeInt(mBearingAccuracy);
        parcel.writeInt(mPowerRequirement);
        parcel.writeInt(mAltitudeRequired ? 1 : 0);
        parcel.writeInt(mBearingRequired ? 1 : 0);
        parcel.writeInt(mSpeedRequired ? 1 : 0);
        parcel.writeInt(mCostAllowed ? 1 : 0);
    }

    @Override public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("Criteria[");
        s.append("power=").append(requirementToString(mPowerRequirement)).append(", ");
        s.append("accuracy=").append(requirementToString(mHorizontalAccuracy));
        if (mVerticalAccuracy != NO_REQUIREMENT) {
            s.append(", verticalAccuracy=").append(requirementToString(mVerticalAccuracy));
        }
        if (mSpeedAccuracy != NO_REQUIREMENT) {
            s.append(", speedAccuracy=").append(requirementToString(mSpeedAccuracy));
        }
        if (mBearingAccuracy != NO_REQUIREMENT) {
            s.append(", bearingAccuracy=").append(requirementToString(mBearingAccuracy));
        }
        if (mAltitudeRequired || mBearingRequired || mSpeedRequired) {
            s.append(", required=[");
            if (mAltitudeRequired) s.append("altitude, ");
            if (mBearingRequired) s.append("bearing, ");
            if (mSpeedRequired) s.append("speed, ");
            s.setLength(s.length() - 2);
            s.append("]");
        }
        if (mCostAllowed) {
            s.append(", costAllowed");
        }
        s.append(']');
        return s.toString();
    }

    private static String requirementToString(int power) {
        switch (power) {
            case NO_REQUIREMENT: return "None";
            case POWER_LOW: return "Low";
            case POWER_MEDIUM: return "Medium";
            case POWER_HIGH: return "High";
            default: return "???";
        }
    }
}
