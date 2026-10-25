// com.google.android.gms.maps.model.VisibleRegion -- the four map corners
// currently visible plus their bounding box. Returned by
// Projection.getVisibleRegion(); on the inert map the GoogleMap synthesizes
// a default world-spanning region so callers get a non-null value.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/VisibleRegion
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class VisibleRegion implements Parcelable {
    public final LatLng nearLeft;
    public final LatLng nearRight;
    public final LatLng farLeft;
    public final LatLng farRight;
    public final LatLngBounds latLngBounds;

    public VisibleRegion(LatLng nearLeft, LatLng nearRight,
                         LatLng farLeft, LatLng farRight,
                         LatLngBounds latLngBounds) {
        this.nearLeft = nearLeft;
        this.nearRight = nearRight;
        this.farLeft = farLeft;
        this.farRight = farRight;
        this.latLngBounds = latLngBounds;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VisibleRegion)) return false;
        VisibleRegion other = (VisibleRegion) o;
        return nearLeft.equals(other.nearLeft)
            && nearRight.equals(other.nearRight)
            && farLeft.equals(other.farLeft)
            && farRight.equals(other.farRight)
            && latLngBounds.equals(other.latLngBounds);
    }

    @Override public int hashCode() {
        int r = nearLeft.hashCode();
        r = 31 * r + nearRight.hashCode();
        r = 31 * r + farLeft.hashCode();
        r = 31 * r + farRight.hashCode();
        r = 31 * r + latLngBounds.hashCode();
        return r;
    }

    @Override public String toString() {
        return "VisibleRegion{nearLeft=" + nearLeft + ", nearRight=" + nearRight
            + ", farLeft=" + farLeft + ", farRight=" + farRight
            + ", latLngBounds=" + latLngBounds + "}";
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        nearLeft.writeToParcel(dest, flags);
        nearRight.writeToParcel(dest, flags);
        farLeft.writeToParcel(dest, flags);
        farRight.writeToParcel(dest, flags);
        latLngBounds.writeToParcel(dest, flags);
    }

    public static final Parcelable.Creator<VisibleRegion> CREATOR =
            new Parcelable.Creator<VisibleRegion>() {
        @Override public VisibleRegion createFromParcel(Parcel in) {
            return new VisibleRegion(
                LatLng.CREATOR.createFromParcel(in),
                LatLng.CREATOR.createFromParcel(in),
                LatLng.CREATOR.createFromParcel(in),
                LatLng.CREATOR.createFromParcel(in),
                LatLngBounds.CREATOR.createFromParcel(in));
        }
        @Override public VisibleRegion[] newArray(int size) { return new VisibleRegion[size]; }
    };
}
