// android.location.Location -- a geographic point with metadata.
// Full getter/setter/has* surface + the pure-math distanceTo /
// distanceBetween (Vincenty inverse formula, copied verbatim from AOSP
// frameworks/base Location.java so games/maps that compute distances
// get the exact same answer as on a real device).
package android.location;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class Location implements Parcelable {
    public static final int FORMAT_DEGREES = 0;
    public static final int FORMAT_MINUTES = 1;
    public static final int FORMAT_SECONDS = 2;

    private String mProvider;
    private long mTime = 0;
    private long mElapsedRealtimeNanos = 0;
    private double mLatitude = 0.0;
    private double mLongitude = 0.0;
    private double mAltitude = 0.0;
    private float mSpeed = 0.0f;
    private float mBearing = 0.0f;
    private float mAccuracy = 0.0f;
    private float mVerticalAccuracy = 0.0f;
    private float mSpeedAccuracy = 0.0f;
    private float mBearingAccuracy = 0.0f;
    private Bundle mExtras = null;

    private boolean mHasAltitude = false;
    private boolean mHasSpeed = false;
    private boolean mHasBearing = false;
    private boolean mHasAccuracy = false;
    private boolean mHasVerticalAccuracy = false;
    private boolean mHasSpeedAccuracy = false;
    private boolean mHasBearingAccuracy = false;

    public Location(String provider) { mProvider = provider; }
    public Location(Location o) { set(o); }

    public void set(Location o) {
        if (o == null) return;
        mProvider = o.mProvider;
        mTime = o.mTime;
        mElapsedRealtimeNanos = o.mElapsedRealtimeNanos;
        mLatitude = o.mLatitude;
        mLongitude = o.mLongitude;
        mAltitude = o.mAltitude;
        mSpeed = o.mSpeed;
        mBearing = o.mBearing;
        mAccuracy = o.mAccuracy;
        mVerticalAccuracy = o.mVerticalAccuracy;
        mSpeedAccuracy = o.mSpeedAccuracy;
        mBearingAccuracy = o.mBearingAccuracy;
        mExtras = (o.mExtras == null) ? null : new Bundle(o.mExtras);
        mHasAltitude = o.mHasAltitude;
        mHasSpeed = o.mHasSpeed;
        mHasBearing = o.mHasBearing;
        mHasAccuracy = o.mHasAccuracy;
        mHasVerticalAccuracy = o.mHasVerticalAccuracy;
        mHasSpeedAccuracy = o.mHasSpeedAccuracy;
        mHasBearingAccuracy = o.mHasBearingAccuracy;
    }

    public void reset() {
        mProvider = null;
        mTime = 0;
        mElapsedRealtimeNanos = 0;
        mLatitude = 0;
        mLongitude = 0;
        mAltitude = 0;
        mSpeed = 0;
        mBearing = 0;
        mAccuracy = 0;
        mVerticalAccuracy = 0;
        mSpeedAccuracy = 0;
        mBearingAccuracy = 0;
        mExtras = null;
        mHasAltitude = mHasSpeed = mHasBearing = mHasAccuracy = false;
        mHasVerticalAccuracy = mHasSpeedAccuracy = mHasBearingAccuracy = false;
    }

    public String getProvider() { return mProvider; }
    public void setProvider(String provider) { mProvider = provider; }

    public long getTime() { return mTime; }
    public void setTime(long time) { mTime = time; }

    public long getElapsedRealtimeNanos() { return mElapsedRealtimeNanos; }
    public void setElapsedRealtimeNanos(long time) { mElapsedRealtimeNanos = time; }
    public long getElapsedRealtimeMillis() { return mElapsedRealtimeNanos / 1000000L; }

    public double getLatitude() { return mLatitude; }
    public void setLatitude(double latitude) { mLatitude = latitude; }

    public double getLongitude() { return mLongitude; }
    public void setLongitude(double longitude) { mLongitude = longitude; }

    public double getAltitude() { return mAltitude; }
    public void setAltitude(double altitude) { mAltitude = altitude; mHasAltitude = true; }
    public boolean hasAltitude() { return mHasAltitude; }
    public void removeAltitude() { mAltitude = 0; mHasAltitude = false; }

    public float getSpeed() { return mSpeed; }
    public void setSpeed(float speed) { mSpeed = speed; mHasSpeed = true; }
    public boolean hasSpeed() { return mHasSpeed; }
    public void removeSpeed() { mSpeed = 0; mHasSpeed = false; }

    public float getBearing() { return mBearing; }
    public void setBearing(float bearing) {
        // AOSP normalizes bearing into [0, 360).
        while (bearing < 0.0f) bearing += 360.0f;
        while (bearing >= 360.0f) bearing -= 360.0f;
        mBearing = bearing;
        mHasBearing = true;
    }
    public boolean hasBearing() { return mHasBearing; }
    public void removeBearing() { mBearing = 0; mHasBearing = false; }

    public float getAccuracy() { return mAccuracy; }
    public void setAccuracy(float accuracy) { mAccuracy = accuracy; mHasAccuracy = true; }
    public boolean hasAccuracy() { return mHasAccuracy; }
    public void removeAccuracy() { mAccuracy = 0; mHasAccuracy = false; }

    public float getVerticalAccuracyMeters() { return mVerticalAccuracy; }
    public void setVerticalAccuracyMeters(float v) { mVerticalAccuracy = v; mHasVerticalAccuracy = true; }
    public boolean hasVerticalAccuracy() { return mHasVerticalAccuracy; }

    public float getSpeedAccuracyMetersPerSecond() { return mSpeedAccuracy; }
    public void setSpeedAccuracyMetersPerSecond(float v) { mSpeedAccuracy = v; mHasSpeedAccuracy = true; }
    public boolean hasSpeedAccuracy() { return mHasSpeedAccuracy; }

    public float getBearingAccuracyDegrees() { return mBearingAccuracy; }
    public void setBearingAccuracyDegrees(float v) { mBearingAccuracy = v; mHasBearingAccuracy = true; }
    public boolean hasBearingAccuracy() { return mHasBearingAccuracy; }

    public Bundle getExtras() { return mExtras; }
    public void setExtras(Bundle extras) {
        mExtras = (extras == null) ? null : new Bundle(extras);
    }

    /** Deprecated alias of isMock() (AOSP: "return isMock();"). */
    public boolean isFromMockProvider() { return isMock(); }

    // === Distance math (verbatim AOSP, Vincenty inverse formula) =========
    private static final class BearingDistanceCache {
        double mLat1 = 0.0;
        double mLon1 = 0.0;
        double mLat2 = 0.0;
        double mLon2 = 0.0;
        float mDistance = 0.0f;
        float mInitialBearing = 0.0f;
        float mFinalBearing = 0.0f;
    }

    private static final ThreadLocal<BearingDistanceCache> sBearingDistanceCache =
            ThreadLocal.withInitial(BearingDistanceCache::new);

    public float distanceTo(Location dest) {
        BearingDistanceCache cache = sBearingDistanceCache.get();
        if (mLatitude != cache.mLat1 || mLongitude != cache.mLon1
                || dest.mLatitude != cache.mLat2 || dest.mLongitude != cache.mLon2) {
            computeDistanceAndBearing(mLatitude, mLongitude,
                    dest.mLatitude, dest.mLongitude, cache);
        }
        return cache.mDistance;
    }

    public float bearingTo(Location dest) {
        BearingDistanceCache cache = sBearingDistanceCache.get();
        if (mLatitude != cache.mLat1 || mLongitude != cache.mLon1
                || dest.mLatitude != cache.mLat2 || dest.mLongitude != cache.mLon2) {
            computeDistanceAndBearing(mLatitude, mLongitude,
                    dest.mLatitude, dest.mLongitude, cache);
        }
        return cache.mInitialBearing;
    }

    public static void distanceBetween(double startLatitude, double startLongitude,
            double endLatitude, double endLongitude, float[] results) {
        if (results == null || results.length < 1) {
            throw new IllegalArgumentException("results is null or has length < 1");
        }
        BearingDistanceCache cache = sBearingDistanceCache.get();
        computeDistanceAndBearing(startLatitude, startLongitude,
                endLatitude, endLongitude, cache);
        results[0] = cache.mDistance;
        if (results.length > 1) {
            results[1] = cache.mInitialBearing;
            if (results.length > 2) {
                results[2] = cache.mFinalBearing;
            }
        }
    }

    private static void computeDistanceAndBearing(double lat1, double lon1,
            double lat2, double lon2, BearingDistanceCache results) {
        // Based on http://www.ngs.noaa.gov/PUBS_LIB/inverse.pdf
        // using the "Inverse Formula" (section 4)

        // Convert lat/long to radians
        lat1 *= Math.PI / 180.0;
        lat2 *= Math.PI / 180.0;
        lon1 *= Math.PI / 180.0;
        lon2 *= Math.PI / 180.0;

        double a = 6378137.0; // WGS84 major axis
        double b = 6356752.3142; // WGS84 semi-major axis
        double f = (a - b) / a;
        double aSqMinusBSqOverBSq = (a * a - b * b) / (b * b);

        double l = lon2 - lon1;
        double aA = 0.0;
        double u1 = Math.atan((1.0 - f) * Math.tan(lat1));
        double u2 = Math.atan((1.0 - f) * Math.tan(lat2));

        double cosU1 = Math.cos(u1);
        double cosU2 = Math.cos(u2);
        double sinU1 = Math.sin(u1);
        double sinU2 = Math.sin(u2);
        double cosU1cosU2 = cosU1 * cosU2;
        double sinU1sinU2 = sinU1 * sinU2;

        double sigma = 0.0;
        double deltaSigma = 0.0;
        double cosSqAlpha;
        double cos2SM;
        double cosSigma;
        double sinSigma;
        double cosLambda = 0.0;
        double sinLambda = 0.0;

        double lambda = l; // initial guess
        for (int iter = 0; iter < 20; iter++) {
            double lambdaOrig = lambda;
            cosLambda = Math.cos(lambda);
            sinLambda = Math.sin(lambda);
            double t1 = cosU2 * sinLambda;
            double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
            double sinSqSigma = t1 * t1 + t2 * t2;
            sinSigma = Math.sqrt(sinSqSigma);
            cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = (sinSigma == 0) ? 0.0 :
                    cosU1cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
            cos2SM = (cosSqAlpha == 0) ? 0.0 : cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha;

            double uSquared = cosSqAlpha * aSqMinusBSqOverBSq;
            aA = 1 + (uSquared / 16384.0) * (4096.0 + uSquared * (-768 + uSquared * (320.0
                    - 175.0 * uSquared)));
            double bB = (uSquared / 1024.0) * (256.0 + uSquared * (-128.0 + uSquared * (74.0
                    - 47.0 * uSquared)));
            double cC = (f / 16.0) * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));
            double cos2SMSq = cos2SM * cos2SM;
            deltaSigma = bB * sinSigma * (cos2SM + (bB / 4.0) * (cosSigma * (-1.0 + 2.0 * cos2SMSq)
                    - (bB / 6.0) * cos2SM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0
                    + 4.0 * cos2SMSq)));

            lambda = l + (1.0 - cC) * f * sinAlpha * (sigma + cC * sinSigma * (cos2SM
                    + cC * cosSigma * (-1.0 + 2.0 * cos2SM * cos2SM)));

            double delta = (lambda - lambdaOrig) / lambda;
            if (Math.abs(delta) < 1.0e-12) {
                break;
            }
        }

        results.mDistance = (float) (b * aA * (sigma - deltaSigma));
        float initialBearing = (float) Math.atan2(cosU2 * sinLambda,
                cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);
        initialBearing = (float) (initialBearing * (180.0 / Math.PI));
        results.mInitialBearing = initialBearing;
        float finalBearing = (float) Math.atan2(cosU1 * sinLambda,
                -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda);
        finalBearing = (float) (finalBearing * (180.0 / Math.PI));
        results.mFinalBearing = finalBearing;
        results.mLat1 = lat1;
        results.mLat2 = lat2;
        results.mLon1 = lon1;
        results.mLon2 = lon2;
    }

    // AOSP coordinate string conversions (FORMAT_DEGREES/MINUTES/SECONDS).
    public static String convert(double coordinate, int outputType) {
        if (coordinate < -180.0 || coordinate > 180.0 || Double.isNaN(coordinate)) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        if (outputType != FORMAT_DEGREES && outputType != FORMAT_MINUTES
                && outputType != FORMAT_SECONDS) {
            throw new IllegalArgumentException("outputType=" + outputType);
        }
        StringBuilder sb = new StringBuilder();
        if (coordinate < 0) { sb.append('-'); coordinate = -coordinate; }
        java.text.DecimalFormat df = new java.text.DecimalFormat("###.#####",
                java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
        if (outputType == FORMAT_MINUTES || outputType == FORMAT_SECONDS) {
            int degrees = (int) Math.floor(coordinate);
            sb.append(degrees);
            sb.append(':');
            coordinate -= degrees;
            coordinate *= 60.0;
            if (outputType == FORMAT_SECONDS) {
                int minutes = (int) Math.floor(coordinate);
                sb.append(minutes);
                sb.append(':');
                coordinate -= minutes;
                coordinate *= 60.0;
            }
        }
        sb.append(df.format(coordinate));
        return sb.toString();
    }

    public static double convert(String coordinate) {
        if (coordinate == null) throw new NullPointerException("coordinate");
        boolean negative = false;
        if (coordinate.charAt(0) == '-') { coordinate = coordinate.substring(1); negative = true; }
        java.util.StringTokenizer st = new java.util.StringTokenizer(coordinate, ":");
        int tokens = st.countTokens();
        if (tokens < 1) throw new IllegalArgumentException("coordinate=" + coordinate);
        try {
            String degrees = st.nextToken();
            double val;
            if (tokens == 1) return Double.parseDouble(degrees) * (negative ? -1 : 1);
            String minutes = st.nextToken();
            int deg = Integer.parseInt(degrees);
            double min;
            double sec = 0.0;
            if (st.hasMoreTokens()) {
                min = Integer.parseInt(minutes);
                String seconds = st.nextToken();
                sec = Double.parseDouble(seconds);
            } else {
                min = Double.parseDouble(minutes);
            }
            boolean isNegative180 = negative && deg == 180 && min == 0 && sec == 0;
            if (min < 0 || min >= 60 || (!isNegative180 && (sec < 0 || sec >= 60))) {
                throw new IllegalArgumentException("coordinate=" + coordinate);
            }
            val = deg * 3600.0 + min * 60.0 + sec;
            val /= 3600.0;
            return negative ? -val : val;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
    }

    @Override public String toString() {
        return "Location[" + mProvider + " " + mLatitude + "," + mLongitude
            + (mHasAccuracy ? " hAcc=" + mAccuracy : " hAcc=???") + "]";
    }

    // === Parcelable ========================================================
    // AOSP Location implements Parcelable so LocationManager.KEY_LOCATION_CHANGED
    // can ride a PendingIntent Intent extra, and so apps can hand a Location
    // across an Activity/process boundary via Bundle/Intent putParcelable.
    // AOSP's writeToParcel/CREATOR (frameworks/base core/java/android/location/
    // Location.java) writes provider, a fields-present bitmask, time,
    // elapsedRealtimeNanos, lat/lon unconditionally, then each optional value
    // gated behind its own has-flag, then extras last. We mirror that same
    // shape using our own per-field has* booleans (this class predates AOSP's
    // single mFieldsMask refactor) instead of adding a bitmask field that
    // nothing else in this class uses.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mProvider);
        dest.writeLong(mTime);
        dest.writeLong(mElapsedRealtimeNanos);
        dest.writeDouble(mLatitude);
        dest.writeDouble(mLongitude);
        dest.writeBoolean(mHasAltitude);
        if (mHasAltitude) dest.writeDouble(mAltitude);
        dest.writeBoolean(mHasSpeed);
        if (mHasSpeed) dest.writeFloat(mSpeed);
        dest.writeBoolean(mHasBearing);
        if (mHasBearing) dest.writeFloat(mBearing);
        dest.writeBoolean(mHasAccuracy);
        if (mHasAccuracy) dest.writeFloat(mAccuracy);
        dest.writeBoolean(mHasVerticalAccuracy);
        if (mHasVerticalAccuracy) dest.writeFloat(mVerticalAccuracy);
        dest.writeBoolean(mHasSpeedAccuracy);
        if (mHasSpeedAccuracy) dest.writeFloat(mSpeedAccuracy);
        dest.writeBoolean(mHasBearingAccuracy);
        if (mHasBearingAccuracy) dest.writeFloat(mBearingAccuracy);
        dest.writeBundle(mExtras);
    }


    // v1.0.684: MSL-altitude, elapsed-realtime-uncertainty, and isMock. These
    // are API-34 additions 13 of 37 corpus apps reference. Every one is a plain
    // stored value with a has*/remove* pair, matching the existing accessor
    // pattern in this file. Source: frameworks/base/location/java/android/location/Location.java
    private double mMslAltitudeMeters;
    private float mMslAltitudeAccuracyMeters;
    private double mElapsedRealtimeUncertaintyNanos;
    private boolean mHasMslAltitude, mHasMslAltitudeAccuracy, mHasElapsedRealtimeUncertainty;
    private boolean mIsMock;

    public boolean hasMslAltitude() { return mHasMslAltitude; }
    public double getMslAltitudeMeters() { return mMslAltitudeMeters; }
    public void setMslAltitudeMeters(double v) { mMslAltitudeMeters = v; mHasMslAltitude = true; }
    public void removeMslAltitude() { mMslAltitudeMeters = 0.0; mHasMslAltitude = false; }

    public boolean hasMslAltitudeAccuracy() { return mHasMslAltitudeAccuracy; }
    public float getMslAltitudeAccuracyMeters() { return mMslAltitudeAccuracyMeters; }
    public void setMslAltitudeAccuracyMeters(float v) { mMslAltitudeAccuracyMeters = v; mHasMslAltitudeAccuracy = true; }
    public void removeMslAltitudeAccuracy() { mMslAltitudeAccuracyMeters = 0.0f; mHasMslAltitudeAccuracy = false; }

    public boolean hasElapsedRealtimeUncertaintyNanos() { return mHasElapsedRealtimeUncertainty; }
    public double getElapsedRealtimeUncertaintyNanos() { return mElapsedRealtimeUncertaintyNanos; }
    public void setElapsedRealtimeUncertaintyNanos(double v) { mElapsedRealtimeUncertaintyNanos = v; mHasElapsedRealtimeUncertainty = true; }
    // AOSP Location.removeElapsedRealtimeUncertaintyNanos: clears the has-bit only;
    // the stored value is deliberately NOT reset (AOSP just drops the mask bit).
    public void removeElapsedRealtimeUncertaintyNanos() { mHasElapsedRealtimeUncertainty = false; }

    // AOSP Location.getElapsedRealtimeAgeMillis(): how stale this fix is, measured
    // against the same monotonic clock the fix was stamped with. No hardware is
    // involved, so this is exact here.
    public long getElapsedRealtimeAgeMillis() {
        return getElapsedRealtimeAgeMillis(android.os.SystemClock.elapsedRealtime());
    }
    public long getElapsedRealtimeAgeMillis(long referenceRealtimeMs) {
        return referenceRealtimeMs - getElapsedRealtimeMillis();
    }

    // AOSP Location.isComplete(): the four fields the framework requires before it
    // will hand a fix to an app. Mirror the exact conjunction -- an app may use it
    // to decide whether to trust a Location it built or received.
    public boolean isComplete() {
        return mProvider != null && hasAccuracy() && mTime != 0 && mElapsedRealtimeNanos != 0;
    }

    // AOSP Location.dump(Printer, String): one line, the prefix then toString().
    public void dump(android.util.Printer pw, String prefix) {
        pw.println(prefix + this);
    }

    public boolean isMock() { return mIsMock; }
    public void setMock(boolean mock) { mIsMock = mock; }

    public void removeSpeedAccuracy() { mSpeedAccuracy = 0.0f; mHasSpeedAccuracy = false; }
    public void removeBearingAccuracy() { mBearingAccuracy = 0.0f; mHasBearingAccuracy = false; }
    public void removeVerticalAccuracy() { mVerticalAccuracy = 0.0f; mHasVerticalAccuracy = false; }

    public static final Creator<Location> CREATOR = new Creator<Location>() {
        @Override public Location createFromParcel(Parcel in) {
            Location l = new Location(in.readString());
            l.mTime = in.readLong();
            l.mElapsedRealtimeNanos = in.readLong();
            l.mLatitude = in.readDouble();
            l.mLongitude = in.readDouble();
            l.mHasAltitude = in.readBoolean();
            if (l.mHasAltitude) l.mAltitude = in.readDouble();
            l.mHasSpeed = in.readBoolean();
            if (l.mHasSpeed) l.mSpeed = in.readFloat();
            l.mHasBearing = in.readBoolean();
            if (l.mHasBearing) l.mBearing = in.readFloat();
            l.mHasAccuracy = in.readBoolean();
            if (l.mHasAccuracy) l.mAccuracy = in.readFloat();
            l.mHasVerticalAccuracy = in.readBoolean();
            if (l.mHasVerticalAccuracy) l.mVerticalAccuracy = in.readFloat();
            l.mHasSpeedAccuracy = in.readBoolean();
            if (l.mHasSpeedAccuracy) l.mSpeedAccuracy = in.readFloat();
            l.mHasBearingAccuracy = in.readBoolean();
            if (l.mHasBearingAccuracy) l.mBearingAccuracy = in.readFloat();
            l.mExtras = in.readBundle();
            return l;
        }
        @Override public Location[] newArray(int size) { return new Location[size]; }
    };
}
