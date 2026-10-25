// com.google.android.gms.maps.model.GroundOverlayOptions -- options bag for
// a ground overlay (an image pinned to the map by position+dimensions or by
// a LatLngBounds). Faithful fluent surface; nothing is drawn (inert map).
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/GroundOverlayOptions
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class GroundOverlayOptions implements Parcelable {
    public static final float NO_DIMENSION = -1.0F;

    private BitmapDescriptor image;
    private LatLng location;
    private float width = NO_DIMENSION;
    private float height = NO_DIMENSION;
    private LatLngBounds bounds;
    private float bearing = 0.0F;
    private float zIndex = 0.0F;
    private float transparency = 0.0F;
    private boolean visible = true;
    private boolean clickable = false;
    private float anchorU = 0.5F;
    private float anchorV = 0.5F;

    public GroundOverlayOptions() {}

    public GroundOverlayOptions image(BitmapDescriptor image) { this.image = image; return this; }
    public GroundOverlayOptions anchor(float u, float v) { this.anchorU = u; this.anchorV = v; return this; }
    public GroundOverlayOptions position(LatLng location, float width) {
        this.location = location; this.width = width; this.height = NO_DIMENSION;
        this.bounds = null; return this;
    }
    public GroundOverlayOptions position(LatLng location, float width, float height) {
        this.location = location; this.width = width; this.height = height;
        this.bounds = null; return this;
    }
    public GroundOverlayOptions positionFromBounds(LatLngBounds bounds) {
        this.bounds = bounds; this.location = null; return this;
    }
    public GroundOverlayOptions bearing(float bearing)        { this.bearing = bearing; return this; }
    public GroundOverlayOptions zIndex(float zIndex)          { this.zIndex = zIndex; return this; }
    public GroundOverlayOptions transparency(float t)         { this.transparency = t; return this; }
    public GroundOverlayOptions visible(boolean visible)      { this.visible = visible; return this; }
    public GroundOverlayOptions clickable(boolean clickable)  { this.clickable = clickable; return this; }

    public BitmapDescriptor getImage()   { return image; }
    public LatLng getLocation()          { return location; }
    public float getWidth()              { return width; }
    public float getHeight()             { return height; }
    public LatLngBounds getBounds()      { return bounds; }
    public float getBearing()            { return bearing; }
    public float getZIndex()             { return zIndex; }
    public float getTransparency()       { return transparency; }
    public boolean isVisible()           { return visible; }
    public boolean isClickable()         { return clickable; }
    public float getAnchorU()            { return anchorU; }
    public float getAnchorV()            { return anchorV; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(width);
        dest.writeFloat(height);
        dest.writeFloat(bearing);
        dest.writeFloat(zIndex);
        dest.writeFloat(transparency);
        dest.writeBoolean(visible);
        dest.writeBoolean(clickable);
        dest.writeFloat(anchorU);
        dest.writeFloat(anchorV);
    }

    public static final Parcelable.Creator<GroundOverlayOptions> CREATOR =
            new Parcelable.Creator<GroundOverlayOptions>() {
        @Override public GroundOverlayOptions createFromParcel(Parcel in) { return new GroundOverlayOptions(); }
        @Override public GroundOverlayOptions[] newArray(int size) { return new GroundOverlayOptions[size]; }
    };
}
