package android.telephony;

/**
 * Base signal strength.
 *
 * No cellular radio on a Mac, so every subclass default-constructs to AOSP's
 * "nothing was reported" state: each measurement CellInfo.UNAVAILABLE and the
 * level SIGNAL_STRENGTH_NONE_OR_UNKNOWN. The ASU/dBm conversion helpers below
 * are AOSP's arithmetic verbatim, so a subclass built from real values (e.g.
 * unparcelled from an app's own Bundle) converts identically to a device.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellSignalStrength.java
 */
public abstract class CellSignalStrength {

    public static final int SIGNAL_STRENGTH_NONE_OR_UNKNOWN = 0;
    public static final int SIGNAL_STRENGTH_POOR = 1;
    public static final int SIGNAL_STRENGTH_MODERATE = 2;
    public static final int SIGNAL_STRENGTH_GOOD = 3;
    public static final int SIGNAL_STRENGTH_GREAT = 4;

    /** @hide */ public static final int NUM_SIGNAL_STRENGTH_BINS = 5;
    /** @hide */ protected static final int NUM_SIGNAL_STRENGTH_THRESHOLDS =
            NUM_SIGNAL_STRENGTH_BINS - 1;

    protected CellSignalStrength() { }

    public abstract int getLevel();

    public abstract int getAsuLevel();

    public abstract int getDbm();

    @Override public abstract int hashCode();

    @Override public abstract boolean equals(Object o);

    /** @hide */
    public static int getRssiDbmFromAsu(int asu) {
        if (asu > 31 || asu < 0) return CellInfo.UNAVAILABLE;
        return -113 + (2 * asu);
    }

    /** @hide */
    protected static int getAsuFromRssiDbm(int dbm) {
        if (dbm == CellInfo.UNAVAILABLE) return 99;
        return (dbm + 113) / 2;
    }

    /** @hide */
    public static int getRscpDbmFromAsu(int asu) {
        if (asu > 96 || asu < 0) return CellInfo.UNAVAILABLE;
        return asu - 120;
    }

    /** @hide */
    protected static int getAsuFromRscpDbm(int dbm) {
        if (dbm == CellInfo.UNAVAILABLE) return 255;
        return dbm + 120;
    }

    /** @hide */
    public static int getEcNoDbFromAsu(int asu) {
        if (asu > 49 || asu < 0) return CellInfo.UNAVAILABLE;
        return -24 + (asu / 2);
    }

    /** @hide */
    protected static int inRangeOrUnavailable(int value, int rangeMin, int rangeMax) {
        if (value < rangeMin || value > rangeMax) return CellInfo.UNAVAILABLE;
        return value;
    }

    /** @hide */
    protected static int inRangeOrUnavailable(int value, int rangeMin, int rangeMax, int special) {
        if ((value < rangeMin || value > rangeMax) && value != special) {
            return CellInfo.UNAVAILABLE;
        }
        return value;
    }

    /** @hide */
    public static int getNumSignalStrengthLevels() { return NUM_SIGNAL_STRENGTH_BINS; }
}
