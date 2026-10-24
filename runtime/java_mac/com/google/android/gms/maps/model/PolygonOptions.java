// com.google.android.gms.maps.model.PolygonOptions -- options bag for a
// polygon (outer ring + optional holes). Faithful fluent surface.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/PolygonOptions
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PolygonOptions implements Parcelable {
    private final List<LatLng> points = new ArrayList<LatLng>();
    private final List<List<LatLng>> holes = new ArrayList<List<LatLng>>();
    private float strokeWidth = 10.0F;
    private int strokeColor = 0xFF000000;
    private int fillColor = 0x00000000;
    private float zIndex = 0.0F;
    private boolean visible = true;
    private boolean geodesic = false;
    private boolean clickable = false;

    public PolygonOptions() {}

    public PolygonOptions add(LatLng point) { points.add(point); return this; }
    public PolygonOptions add(LatLng... pts) {
        if (pts != null) points.addAll(Arrays.asList(pts));
        return this;
    }
    public PolygonOptions addAll(Iterable<LatLng> pts) {
        if (pts != null) for (LatLng p : pts) points.add(p);
        return this;
    }
    public PolygonOptions addHole(Iterable<LatLng> hole) {
        List<LatLng> h = new ArrayList<LatLng>();
        if (hole != null) for (LatLng p : hole) h.add(p);
        holes.add(h);
        return this;
    }
    public PolygonOptions strokeWidth(float width)  { this.strokeWidth = width; return this; }
    public PolygonOptions strokeColor(int color)    { this.strokeColor = color; return this; }
    public PolygonOptions fillColor(int color)      { this.fillColor = color; return this; }
    public PolygonOptions zIndex(float zIndex)      { this.zIndex = zIndex; return this; }
    public PolygonOptions visible(boolean visible)  { this.visible = visible; return this; }
    public PolygonOptions geodesic(boolean geodesic) { this.geodesic = geodesic; return this; }
    public PolygonOptions clickable(boolean clickable) { this.clickable = clickable; return this; }

    public List<LatLng> getPoints()       { return points; }
    public List<List<LatLng>> getHoles()  { return holes; }
    public float getStrokeWidth()         { return strokeWidth; }
    public int getStrokeColor()           { return strokeColor; }
    public int getFillColor()             { return fillColor; }
    public float getZIndex()              { return zIndex; }
    public boolean isVisible()            { return visible; }
    public boolean isGeodesic()           { return geodesic; }
    public boolean isClickable()          { return clickable; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(strokeWidth);
        dest.writeInt(strokeColor);
        dest.writeInt(fillColor);
        dest.writeFloat(zIndex);
        dest.writeBoolean(visible);
        dest.writeBoolean(geodesic);
        dest.writeBoolean(clickable);
    }

    public static final Parcelable.Creator<PolygonOptions> CREATOR =
            new Parcelable.Creator<PolygonOptions>() {
        @Override public PolygonOptions createFromParcel(Parcel in) { return new PolygonOptions(); }
        @Override public PolygonOptions[] newArray(int size) { return new PolygonOptions[size]; }
    };
}
