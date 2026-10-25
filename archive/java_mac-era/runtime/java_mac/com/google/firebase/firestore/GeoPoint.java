package com.google.firebase.firestore;

// com.google.firebase.firestore.GeoPoint -- immutable lat/lng value type.
// Faithful client-side value object; nothing here touches a backend, so it
// works fully even on our de-Googled (offline-only) Firestore shim.
public class GeoPoint implements Comparable<GeoPoint> {
    private final double latitude;
    private final double longitude;

    public GeoPoint(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be in the range of [-90, 90]");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be in the range of [-180, 180]");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    @Override
    public int compareTo(GeoPoint other) {
        int c = Double.compare(latitude, other.latitude);
        return c == 0 ? Double.compare(longitude, other.longitude) : c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoPoint)) return false;
        GeoPoint g = (GeoPoint) o;
        return latitude == g.latitude && longitude == g.longitude;
    }

    @Override
    public int hashCode() {
        int result = 31 + (int) (Double.doubleToLongBits(latitude) ^ (Double.doubleToLongBits(latitude) >>> 32));
        result = 31 * result + (int) (Double.doubleToLongBits(longitude) ^ (Double.doubleToLongBits(longitude) >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "GeoPoint { latitude=" + latitude + ", longitude=" + longitude + " }";
    }
}
