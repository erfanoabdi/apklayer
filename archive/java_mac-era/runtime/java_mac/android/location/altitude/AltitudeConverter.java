package android.location.altitude;

import android.content.Context;
import android.location.Location;

import java.io.IOException;

/**
 * Converts altitudes above the WGS84 ellipsoid into altitudes above Mean Sea Level.
 *
 * AOSP does this by reading a geoid-height map out of raw assets shipped in the
 * location module (com.android.internal.location.altitude.GeoidMap, backed by an
 * S2-cell tiled MapParamsProto). Mac.apk does not ship that asset database, and
 * fabricating a geoid height would be worse than admitting we cannot do it: a
 * wrong MSL altitude is silently wrong for every caller.
 *
 * So we take AOSP's own two documented failure paths, which apps already handle:
 *
 *   addMslAltitudeToLocation(Context, Location) -- declared to throw IOException
 *     "if an I/O error occurs when loading data from raw assets". No assets to
 *     load, so that is exactly our situation.
 *   tryAddMslAltitudeToLocation(Location) -- documented to return false and
 *     leave the location unchanged when the params are not loaded. AOSP's own
 *     code returns false when GeoidMap.getGeoidHeightParams() is null.
 *
 * The argument VALIDATION is faithful and runs first in both, because an app
 * relies on the IllegalArgumentException for a bad latitude/longitude/altitude
 * regardless of whether the conversion could have succeeded.
 *
 * Source: frameworks/base/location/java/android/location/altitude/AltitudeConverter.java
 */
public final class AltitudeConverter {

    private static final double MAX_ABS_VALID_LATITUDE = 90;
    private static final double MAX_ABS_VALID_LONGITUDE = 180;

    public AltitudeConverter() { }

    public void addMslAltitudeToLocation(Context context, Location location) throws IOException {
        validate(location);
        throw new IOException("no geoid height assets available on this platform");
    }

    public boolean tryAddMslAltitudeToLocation(Location location) {
        validate(location);
        // AOSP returns false when the geoid height params have not been loaded.
        return false;
    }

    /**
     * AOSP's private validate(): throws IllegalArgumentException for an invalid
     * latitude, longitude, or altitude above WGS84.
     */
    private static void validate(Location location) {
        if (!isFiniteAndAtAbsMost(location.getLatitude(), MAX_ABS_VALID_LATITUDE)) {
            throw new IllegalArgumentException("Invalid latitude: " + location.getLatitude());
        }
        if (!isFiniteAndAtAbsMost(location.getLongitude(), MAX_ABS_VALID_LONGITUDE)) {
            throw new IllegalArgumentException("Invalid longitude: " + location.getLongitude());
        }
        if (!location.hasAltitude()) {
            throw new IllegalArgumentException("Missing altitude above WGS84");
        }
        if (!Double.isFinite(location.getAltitude())) {
            throw new IllegalArgumentException(
                    "Invalid altitude above WGS84: " + location.getAltitude());
        }
    }

    private static boolean isFiniteAndAtAbsMost(double value, double rhs) {
        return Double.isFinite(value) && Math.abs(value) <= rhs;
    }
}
