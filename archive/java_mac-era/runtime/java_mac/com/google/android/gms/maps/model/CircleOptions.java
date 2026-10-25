// com.google.android.gms.maps.model.CircleOptions -- options bag for a
// circle (center + radius in meters). Faithful fluent surface.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/CircleOptions
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class CircleOptions implements Parcelable {
    private LatLng center;
    private double radius = 0.0D;
    private float strokeWidth = 10.0F;
    private int strokeColor = 0xFF000000;
    private int fillColor = 0x00000000;
    private float zIndex = 0.0F;
    private boolean visible = true;
    private boolean clickable = false;

    public CircleOptions() {}

    public CircleOptions center(LatLng center)    { this.center = center; return this; }
    public CircleOptions radius(double radius)    { this.radius = radius; return this; }
    public CircleOptions strokeWidth(float width) { this.strokeWidth = width; return this; }
    public CircleOptions strokeColor(int color)   { this.strokeColor = color; return this; }
    public CircleOptions fillColor(int color)     { this.fillColor = color; return this; }
    public CircleOptions zIndex(float zIndex)     { this.zIndex = zIndex; return this; }
    public CircleOptions visible(boolean visible) { this.visible = visible; return this; }
    public CircleOptions clickable(boolean clickable) { this.clickable = clickable; return this; }

    public LatLng getCenter()      { return center; }
    public double getRadius()      { return radius; }
    public float getStrokeWidth()  { return strokeWidth; }
    public int getStrokeColor()    { return strokeColor; }
    public int getFillColor()      { return fillColor; }
    public float getZIndex()       { return zIndex; }
    public boolean isVisible()     { return visible; }
    public boolean isClickable()   { return clickable; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(center, flags);
        dest.writeDouble(radius);
        dest.writeFloat(strokeWidth);
        dest.writeInt(strokeColor);
        dest.writeInt(fillColor);
        dest.writeFloat(zIndex);
        dest.writeBoolean(visible);
        dest.writeBoolean(clickable);
    }

    public static final Parcelable.Creator<CircleOptions> CREATOR =
            new Parcelable.Creator<CircleOptions>() {
        @Override public CircleOptions createFromParcel(Parcel in) { return new CircleOptions(); }
        @Override public CircleOptions[] newArray(int size) { return new CircleOptions[size]; }
    };
}
