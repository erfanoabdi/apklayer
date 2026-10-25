// android.location.Geocoder -- forward/reverse geocoding. Mac.apk has
// no geocoding backend, so lookups return empty results cleanly (AOSP
// also returns an empty list when the service has no data). Apps that
// geocode an address must get an empty list, not a NoSuchMethodError or
// a crash. The deprecated synchronous overloads return List<Address>;
// the modern API-33+ overloads deliver via a GeocodeListener callback.
package android.location;

import android.content.Context;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class Geocoder {
    public interface GeocodeListener {
        void onGeocode(List<Address> addresses);
        default void onError(String errorMessage) {}
    }

    private final Locale mLocale;

    public Geocoder(Context context) { this(context, Locale.getDefault()); }
    public Geocoder(Context context, Locale locale) {
        if (locale == null) throw new IllegalArgumentException("locale == null");
        mLocale = locale;
    }

    // AOSP: present only on devices with a geocoder backend. Mac.apk has none.
    public static boolean isPresent() { return false; }

    // Deprecated synchronous overloads (pre-API-33). Return empty list.
    public List<Address> getFromLocation(double latitude, double longitude, int maxResults) {
        checkLatLon(latitude, longitude);
        return Collections.emptyList();
    }

    public List<Address> getFromLocationName(String locationName, int maxResults) {
        if (locationName == null) throw new IllegalArgumentException("locationName == null");
        return Collections.emptyList();
    }

    public List<Address> getFromLocationName(String locationName, int maxResults,
            double lowerLeftLatitude, double lowerLeftLongitude,
            double upperRightLatitude, double upperRightLongitude) {
        if (locationName == null) throw new IllegalArgumentException("locationName == null");
        return Collections.emptyList();
    }

    // API-33+ async overloads. Deliver an empty result on the listener.
    public void getFromLocation(double latitude, double longitude, int maxResults,
            GeocodeListener listener) {
        checkLatLon(latitude, longitude);
        if (listener != null) listener.onGeocode(Collections.emptyList());
    }

    public void getFromLocationName(String locationName, int maxResults,
            GeocodeListener listener) {
        if (locationName == null) throw new IllegalArgumentException("locationName == null");
        if (listener != null) listener.onGeocode(Collections.emptyList());
    }

    public void getFromLocationName(String locationName, int maxResults,
            double lowerLeftLatitude, double lowerLeftLongitude,
            double upperRightLatitude, double upperRightLongitude,
            GeocodeListener listener) {
        if (locationName == null) throw new IllegalArgumentException("locationName == null");
        if (listener != null) listener.onGeocode(Collections.emptyList());
    }

    private static void checkLatLon(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude == " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude == " + longitude);
        }
    }
}
