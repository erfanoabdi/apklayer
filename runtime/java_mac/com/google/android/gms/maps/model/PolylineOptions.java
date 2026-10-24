// com.google.android.gms.maps.model.PolylineOptions -- options bag for a
// polyline. Faithful fluent surface; points accumulate in an ordered list.
// Inert map => nothing is drawn, but the geometry round-trips.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/PolylineOptions
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class PolylineOptions implements Parcelable {
    private final List<LatLng> points = new ArrayList<LatLng>();
    private float width = 10.0F;
    private int color = 0xFF000000;
    private float zIndex = 0.0F;
    private boolean visible = true;
    private boolean geodesic = false;
    private boolean clickable = false;

    public PolylineOptions() {}

    public PolylineOptions add(LatLng point) { points.add(point); return this; }
    public PolylineOptions add(LatLng... pts) {
        if (pts != null) points.addAll(Arrays.asList(pts));
        return this;
    }
    public PolylineOptions addAll(Iterable<LatLng> pts) {
        if (pts != null) for (LatLng p : pts) points.add(p);
        return this;
    }
    public PolylineOptions width(float width)     { this.width = width; return this; }
    public PolylineOptions color(int color)       { this.color = color; return this; }
    public PolylineOptions zIndex(float zIndex)   { this.zIndex = zIndex; return this; }
    public PolylineOptions visible(boolean visible) { this.visible = visible; return this; }
    public PolylineOptions geodesic(boolean geodesic) { this.geodesic = geodesic; return this; }
    public PolylineOptions clickable(boolean clickable) { this.clickable = clickable; return this; }

    public List<LatLng> getPoints() { return points; }
    public float getWidth()         { return width; }
    public int getColor()           { return color; }
    public float getZIndex()        { return zIndex; }
    public boolean isVisible()      { return visible; }
    public boolean isGeodesic()     { return geodesic; }
    public boolean isClickable()    { return clickable; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(width);
        dest.writeInt(color);
        dest.writeFloat(zIndex);
        dest.writeBoolean(visible);
        dest.writeBoolean(geodesic);
        dest.writeBoolean(clickable);
    }

    public static final Parcelable.Creator<PolylineOptions> CREATOR =
            new Parcelable.Creator<PolylineOptions>() {
        @Override public PolylineOptions createFromParcel(Parcel in) { return new PolylineOptions(); }
        @Override public PolylineOptions[] newArray(int size) { return new PolylineOptions[size]; }
    };
}
