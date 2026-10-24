// com.google.android.gms.maps.model.LatLng -- immutable lat/lng point.
// Faithful to the GMS Maps reference: two public final double fields,
// a single (double,double) constructor, Parcelable. Latitude is clamped
// to [-90, 90] and longitude wrapped to [-180, 180) exactly as the real
// SDK documents, so apps that read back the fields see the same values.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/LatLng
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class LatLng implements Parcelable {
    public final double latitude;
    public final double longitude;

    public LatLng(double latitude, double longitude) {
        // Clamp latitude and wrap longitude per the documented contract.
        this.latitude = Math.max(-90.0D, Math.min(90.0D, latitude));
        double wrapped = ((longitude - 180.0D) % 360.0D);
        if (wrapped < 0.0D) wrapped += 360.0D;
        this.longitude = wrapped - 180.0D;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LatLng)) return false;
        LatLng other = (LatLng) o;
        return Double.compare(latitude, other.latitude) == 0
            && Double.compare(longitude, other.longitude) == 0;
    }

    @Override public int hashCode() {
        long la = Double.doubleToLongBits(latitude);
        long lo = Double.doubleToLongBits(longitude);
        int result = (int) (la ^ (la >>> 32));
        result = 31 * result + (int) (lo ^ (lo >>> 32));
        return result;
    }

    @Override public String toString() {
        return "lat/lng: (" + latitude + "," + longitude + ")";
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
    }

    public static final Parcelable.Creator<LatLng> CREATOR = new Parcelable.Creator<LatLng>() {
        @Override public LatLng createFromParcel(Parcel in) {
            return new LatLng(in.readDouble(), in.readDouble());
        }
        @Override public LatLng[] newArray(int size) { return new LatLng[size]; }
    };
}
