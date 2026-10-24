// com.google.android.gms.maps.model.MarkerOptions -- builder-style options
// bag for a marker. Every fluent setter returns `this` and the matching
// getter reads the stored value, exactly per the GMS reference, so apps
// that build a MarkerOptions and pass it to GoogleMap.addMarker() get a
// faithful round-trip. Nothing is rendered (inert map).
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/MarkerOptions
package com.google.android.gms.maps.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class MarkerOptions implements Parcelable {
    private LatLng position;
    private String title;
    private String snippet;
    private BitmapDescriptor icon;
    private float anchorU = 0.5F;
    private float anchorV = 1.0F;
    private boolean draggable = false;
    private boolean visible = true;
    private boolean flat = false;
    private float rotation = 0.0F;
    private float alpha = 1.0F;
    private float infoWindowAnchorU = 0.5F;
    private float infoWindowAnchorV = 0.0F;
    private float zIndex = 0.0F;

    public MarkerOptions() {}

    public MarkerOptions position(LatLng position) { this.position = position; return this; }
    public MarkerOptions title(String title)       { this.title = title; return this; }
    public MarkerOptions snippet(String snippet)   { this.snippet = snippet; return this; }
    public MarkerOptions icon(BitmapDescriptor icon) { this.icon = icon; return this; }
    public MarkerOptions anchor(float u, float v)  { this.anchorU = u; this.anchorV = v; return this; }
    public MarkerOptions infoWindowAnchor(float u, float v) {
        this.infoWindowAnchorU = u; this.infoWindowAnchorV = v; return this;
    }
    public MarkerOptions draggable(boolean draggable) { this.draggable = draggable; return this; }
    public MarkerOptions visible(boolean visible)  { this.visible = visible; return this; }
    public MarkerOptions flat(boolean flat)        { this.flat = flat; return this; }
    public MarkerOptions rotation(float rotation)  { this.rotation = rotation; return this; }
    public MarkerOptions alpha(float alpha)        { this.alpha = alpha; return this; }
    public MarkerOptions zIndex(float zIndex)      { this.zIndex = zIndex; return this; }

    public LatLng getPosition()            { return position; }
    public String getTitle()               { return title; }
    public String getSnippet()             { return snippet; }
    public BitmapDescriptor getIcon()      { return icon; }
    public float getAnchorU()              { return anchorU; }
    public float getAnchorV()              { return anchorV; }
    public float getInfoWindowAnchorU()    { return infoWindowAnchorU; }
    public float getInfoWindowAnchorV()    { return infoWindowAnchorV; }
    public boolean isDraggable()           { return draggable; }
    public boolean isVisible()             { return visible; }
    public boolean isFlat()                { return flat; }
    public float getRotation()             { return rotation; }
    public float getAlpha()                { return alpha; }
    public float getZIndex()               { return zIndex; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(position, flags);
        dest.writeString(title);
        dest.writeString(snippet);
        dest.writeFloat(anchorU);
        dest.writeFloat(anchorV);
        dest.writeBoolean(draggable);
        dest.writeBoolean(visible);
        dest.writeBoolean(flat);
        dest.writeFloat(rotation);
        dest.writeFloat(alpha);
        dest.writeFloat(zIndex);
    }

    public static final Parcelable.Creator<MarkerOptions> CREATOR =
            new Parcelable.Creator<MarkerOptions>() {
        @Override public MarkerOptions createFromParcel(Parcel in) { return new MarkerOptions(); }
        @Override public MarkerOptions[] newArray(int size) { return new MarkerOptions[size]; }
    };
}
