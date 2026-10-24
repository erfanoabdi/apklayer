package android.location;

import android.util.SparseArray;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Legacy GNSS status snapshot (deprecated since API 24, still widely called).
 *
 * A Mac has no GNSS receiver, so LocationManager never produces one of these
 * and a default instance iterates an empty satellite list with TTFF 0 -- which
 * is exactly AOSP's own "no fix yet" state, so callers degrade correctly
 * instead of needing fabricated satellites.
 *
 * GpsStatus.create(GnssStatus, int) is a real conversion, not a stub: it is the
 * public API AOSP added in API 24 so an app on the modern GnssStatus callback
 * can still feed legacy code, and it runs entirely in-process. The SVID
 * offsets (GLONASS +64, BEIDOU +200, SBAS -87) and the "drop anything that is
 * not GPS/QZSS/GLONASS/BEIDOU/SBAS" rule are AOSP's, verbatim.
 *
 * Source: frameworks/base/location/java/android/location/GpsStatus.java
 */
@Deprecated
public final class GpsStatus {

    private static final int MAX_SATELLITES = 255;
    private static final int GLONASS_SVID_OFFSET = 64;
    private static final int BEIDOU_SVID_OFFSET = 200;
    private static final int SBAS_SVID_OFFSET = -87;

    public static final int GPS_EVENT_STARTED = 1;
    public static final int GPS_EVENT_STOPPED = 2;
    public static final int GPS_EVENT_FIRST_FIX = 3;
    public static final int GPS_EVENT_SATELLITE_STATUS = 4;

    private int mTimeToFirstFix;
    private final SparseArray<GpsSatellite> mSatellites = new SparseArray<>();

    private final class SatelliteIterator implements Iterator<GpsSatellite> {
        private final int mSatellitesCount;
        private int mIndex = 0;

        SatelliteIterator() { mSatellitesCount = mSatellites.size(); }

        @Override public boolean hasNext() {
            for (; mIndex < mSatellitesCount; ++mIndex) {
                if (mSatellites.valueAt(mIndex).mValid) {
                    return true;
                }
            }
            return false;
        }

        @Override public GpsSatellite next() {
            while (mIndex < mSatellitesCount) {
                GpsSatellite satellite = mSatellites.valueAt(mIndex);
                ++mIndex;
                if (satellite.mValid) {
                    return satellite;
                }
            }
            throw new NoSuchElementException();
        }

        @Override public void remove() { throw new UnsupportedOperationException(); }
    }

    private final Iterable<GpsSatellite> mSatelliteList = SatelliteIterator::new;

    GpsStatus() { }

    static GpsStatus createEmpty() { return new GpsStatus(); }

    public static GpsStatus create(GnssStatus gnssStatus, int timeToFirstFix) {
        GpsStatus status = new GpsStatus();
        status.setStatus(gnssStatus, timeToFirstFix);
        return status;
    }

    void setStatus(GnssStatus status, int timeToFirstFix) {
        for (int i = 0; i < mSatellites.size(); i++) {
            mSatellites.valueAt(i).mValid = false;
        }
        mTimeToFirstFix = timeToFirstFix;
        for (int i = 0; i < status.getSatelliteCount(); i++) {
            int constellationType = status.getConstellationType(i);
            int prn = status.getSvid(i);
            // AOSP: only the constellations that predate GnssSvStatus pass through
            // this legacy API, at their defacto-standard PRN offsets.
            if (constellationType == GnssStatus.CONSTELLATION_GLONASS) {
                prn += GLONASS_SVID_OFFSET;
            } else if (constellationType == GnssStatus.CONSTELLATION_BEIDOU) {
                prn += BEIDOU_SVID_OFFSET;
            } else if (constellationType == GnssStatus.CONSTELLATION_SBAS) {
                prn += SBAS_SVID_OFFSET;
            } else if (constellationType != GnssStatus.CONSTELLATION_GPS
                    && constellationType != GnssStatus.CONSTELLATION_QZSS) {
                continue;
            }
            if (prn <= 0 || prn > MAX_SATELLITES) {
                continue;
            }
            GpsSatellite satellite = mSatellites.get(prn);
            if (satellite == null) {
                satellite = new GpsSatellite(prn);
                mSatellites.put(prn, satellite);
            }
            satellite.mValid = true;
            satellite.mSnr = status.getCn0DbHz(i);
            satellite.mElevation = status.getElevationDegrees(i);
            satellite.mAzimuth = status.getAzimuthDegrees(i);
            satellite.mHasEphemeris = status.hasEphemerisData(i);
            satellite.mHasAlmanac = status.hasAlmanacData(i);
            satellite.mUsedInFix = status.usedInFix(i);
        }
    }

    /** Milliseconds from start to first fix; 0 means "no fix acquired". */
    public int getTimeToFirstFix() { return mTimeToFirstFix; }

    public Iterable<GpsSatellite> getSatellites() { return mSatelliteList; }

    /**
     * AOSP returns mSatellites.size() here, NOT the 255 the name suggests -- a
     * long-standing quirk of the deprecated API. Mirror it exactly: an app that
     * sizes an array off this value must get the same number it would on a device.
     */
    public int getMaxSatellites() { return mSatellites.size(); }

    /** AOSP declares this as an INTERFACE, so it must stay one. */
    @Deprecated
    public interface Listener {
        void onGpsStatusChanged(int event);
    }

    /** Also an interface in AOSP. */
    @Deprecated
    public interface NmeaListener {
        void onNmeaReceived(long timestamp, String nmea);
    }
}
