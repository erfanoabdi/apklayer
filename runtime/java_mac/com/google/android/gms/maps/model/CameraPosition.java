// com.google.android.gms.maps.model.CameraPosition -- immutable camera
// description (target, zoom, tilt, bearing). Pure value object; the inert
// GoogleMap stores the most-recent CameraPosition so getCameraPosition()
// reads back what the app set.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/CameraPosition
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class CameraPosition implements Parcelable {
    public final LatLng target;
    public final float zoom;
    public final float tilt;
    public final float bearing;

    public CameraPosition(LatLng target, float zoom, float tilt, float bearing) {
        if (target == null) throw new NullPointerException("null camera target");
        if (tilt < 0.0F || tilt > 90.0F) {
            throw new IllegalArgumentException("Tilt needs to be between 0 and 90 inclusive: " + tilt);
        }
        this.target = target;
        this.zoom = zoom;
        this.tilt = tilt;
        this.bearing = ((bearing % 360.0F) + 360.0F) % 360.0F;
    }

    public static CameraPosition fromLatLngZoom(LatLng target, float zoom) {
        return new CameraPosition(target, zoom, 0.0F, 0.0F);
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(CameraPosition camera) { return new Builder(camera); }

    public static final class Builder {
        private LatLng target;
        private float zoom;
        private float tilt;
        private float bearing;

        public Builder() {}
        public Builder(CameraPosition camera) {
            if (camera != null) {
                this.target = camera.target;
                this.zoom = camera.zoom;
                this.tilt = camera.tilt;
                this.bearing = camera.bearing;
            }
        }

        public Builder target(LatLng location) { this.target = location; return this; }
        public Builder zoom(float zoom)        { this.zoom = zoom; return this; }
        public Builder tilt(float tilt)        { this.tilt = tilt; return this; }
        public Builder bearing(float bearing)  { this.bearing = bearing; return this; }

        public CameraPosition build() {
            return new CameraPosition(target, zoom, tilt, bearing);
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CameraPosition)) return false;
        CameraPosition other = (CameraPosition) o;
        return target.equals(other.target)
            && Float.compare(zoom, other.zoom) == 0
            && Float.compare(tilt, other.tilt) == 0
            && Float.compare(bearing, other.bearing) == 0;
    }

    @Override public int hashCode() {
        int result = target.hashCode();
        result = 31 * result + Float.floatToIntBits(zoom);
        result = 31 * result + Float.floatToIntBits(tilt);
        result = 31 * result + Float.floatToIntBits(bearing);
        return result;
    }

    @Override public String toString() {
        return "CameraPosition{target=" + target + ", zoom=" + zoom
            + ", tilt=" + tilt + ", bearing=" + bearing + "}";
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        target.writeToParcel(dest, flags);
        dest.writeFloat(zoom);
        dest.writeFloat(tilt);
        dest.writeFloat(bearing);
    }

    public static final Parcelable.Creator<CameraPosition> CREATOR =
            new Parcelable.Creator<CameraPosition>() {
        @Override public CameraPosition createFromParcel(Parcel in) {
            LatLng t = LatLng.CREATOR.createFromParcel(in);
            return new CameraPosition(t, in.readFloat(), in.readFloat(), in.readFloat());
        }
        @Override public CameraPosition[] newArray(int size) { return new CameraPosition[size]; }
    };
}
