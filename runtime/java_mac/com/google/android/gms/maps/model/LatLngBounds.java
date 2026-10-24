// com.google.android.gms.maps.model.LatLngBounds -- an immutable
// axis-aligned lat/lng box defined by its southwest and northeast
// corners. We implement the real geometry (contains/including/center)
// because it is pure client-side math the app may depend on; nothing
// here touches a Google backend.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/LatLngBounds
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class LatLngBounds implements Parcelable {
    public final LatLng southwest;
    public final LatLng northeast;

    public LatLngBounds(LatLng southwest, LatLng northeast) {
        if (southwest == null) throw new NullPointerException("null southwest");
        if (northeast == null) throw new NullPointerException("null northeast");
        if (southwest.latitude > northeast.latitude) {
            throw new IllegalArgumentException(
                "Southern latitude exceeds northern latitude ("
                + southwest.latitude + " > " + northeast.latitude + ")");
        }
        this.southwest = southwest;
        this.northeast = northeast;
    }

    public boolean contains(LatLng point) {
        if (point == null) return false;
        boolean latIn = southwest.latitude <= point.latitude
                     && point.latitude <= northeast.latitude;
        if (!latIn) return false;
        return containsLongitude(point.longitude);
    }

    private boolean containsLongitude(double lng) {
        // The box may wrap across the antimeridian when west > east.
        if (southwest.longitude <= northeast.longitude) {
            return southwest.longitude <= lng && lng <= northeast.longitude;
        }
        return southwest.longitude <= lng || lng <= northeast.longitude;
    }

    public LatLng getCenter() {
        double centerLat = (southwest.latitude + northeast.latitude) / 2.0D;
        double swLng = southwest.longitude;
        double neLng = northeast.longitude;
        double centerLng;
        if (swLng <= neLng) {
            centerLng = (neLng + swLng) / 2.0D;
        } else {
            centerLng = (neLng + swLng + 360.0D) / 2.0D;
        }
        return new LatLng(centerLat, centerLng);
    }

    public LatLngBounds including(LatLng point) {
        if (point == null) return this;
        double minLat = Math.min(southwest.latitude, point.latitude);
        double maxLat = Math.max(northeast.latitude, point.latitude);
        double swLng = southwest.longitude;
        double neLng = northeast.longitude;
        double newLng = point.longitude;
        double minLng = swLng;
        double maxLng = neLng;
        if (!containsLongitude(newLng)) {
            // Extend toward whichever edge is closer (matches the SDK's
            // minimal-growth behavior).
            if (gap(swLng, newLng) < gap(newLng, neLng)) {
                minLng = newLng;
            } else {
                maxLng = newLng;
            }
        }
        return new LatLngBounds(new LatLng(minLat, minLng), new LatLng(maxLat, maxLng));
    }

    private static double gap(double from, double to) {
        double d = to - from;
        return (d < 0.0D) ? d + 360.0D : d;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private double minLat = Double.POSITIVE_INFINITY;
        private double maxLat = Double.NEGATIVE_INFINITY;
        private double minLng = Double.NaN;
        private double maxLng = Double.NaN;

        public Builder include(LatLng point) {
            if (point == null) return this;
            minLat = Math.min(minLat, point.latitude);
            maxLat = Math.max(maxLat, point.latitude);
            double lng = point.longitude;
            if (Double.isNaN(minLng)) {
                minLng = lng;
                maxLng = lng;
            } else if (!withinSpan(minLng, maxLng, lng)) {
                if (gap(minLng, lng) < gap(lng, maxLng)) {
                    minLng = lng;
                } else {
                    maxLng = lng;
                }
            }
            return this;
        }

        private static boolean withinSpan(double west, double east, double lng) {
            if (west <= east) return west <= lng && lng <= east;
            return west <= lng || lng <= east;
        }

        public LatLngBounds build() {
            if (Double.isNaN(minLng)) {
                throw new IllegalStateException("no included points");
            }
            return new LatLngBounds(new LatLng(minLat, minLng), new LatLng(maxLat, maxLng));
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LatLngBounds)) return false;
        LatLngBounds other = (LatLngBounds) o;
        return southwest.equals(other.southwest) && northeast.equals(other.northeast);
    }

    @Override public int hashCode() {
        return 31 * southwest.hashCode() + northeast.hashCode();
    }

    @Override public String toString() {
        return "LatLngBounds{southwest=" + southwest + ", northeast=" + northeast + "}";
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        southwest.writeToParcel(dest, flags);
        northeast.writeToParcel(dest, flags);
    }

    public static final Parcelable.Creator<LatLngBounds> CREATOR =
            new Parcelable.Creator<LatLngBounds>() {
        @Override public LatLngBounds createFromParcel(Parcel in) {
            return new LatLngBounds(
                LatLng.CREATOR.createFromParcel(in),
                LatLng.CREATOR.createFromParcel(in));
        }
        @Override public LatLngBounds[] newArray(int size) { return new LatLngBounds[size]; }
    };
}
