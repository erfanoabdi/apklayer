package android.location;

/**
 * One satellite in a legacy GpsStatus snapshot.
 *
 * Deprecated on Android since API 24 in favor of GnssStatus, but still
 * referenced by 19 of 37 corpus apps because the old GpsStatus.Listener path
 * lives on in long-tail location code. A Mac has no GNSS receiver, so no
 * GpsSatellite is ever produced; the class exists so the iteration code that
 * would consume one links and runs (over an empty list).
 *
 * Source: frameworks/base/location/java/android/location/GpsSatellite.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/location/java/android/location/GpsSatellite.java
 */
public final class GpsSatellite {

    int mPrn;
    float mSnr;
    float mElevation;
    float mAzimuth;
    boolean mHasEphemeris;
    boolean mHasAlmanac;
    boolean mUsedInFix;
    // AOSP keeps a per-satellite validity bit so GpsStatus.setStatus can reuse
    // entries across snapshots; the iterator skips invalid ones.
    boolean mValid;

    GpsSatellite(int prn) { mPrn = prn; }

    public int getPrn() { return mPrn; }

    public float getSnr() { return mSnr; }

    public float getElevation() { return mElevation; }

    public float getAzimuth() { return mAzimuth; }

    public boolean hasEphemeris() { return mHasEphemeris; }

    public boolean hasAlmanac() { return mHasAlmanac; }

    public boolean usedInFix() { return mUsedInFix; }
}
