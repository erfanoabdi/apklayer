// com.google.android.gms.maps.model.GroundOverlay -- live handle returned by
// GoogleMap.addGroundOverlay(GroundOverlayOptions). Mutable value object on
// the inert map; getters/setters round-trip and remove() drops it.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/GroundOverlay
package com.google.android.gms.maps.model;

public final class GroundOverlay {
    private com.google.android.gms.maps.GoogleMap owner;
    private final String id;
    private LatLng position;
    private float width;
    private float height;
    private LatLngBounds bounds;
    private float bearing;
    private float zIndex;
    private float transparency;
    private boolean visible;
    private boolean clickable;
    private Object tag;

    public GroundOverlay(com.google.android.gms.maps.GoogleMap owner, String id, GroundOverlayOptions opts) {
        this.owner = owner;
        this.id = id;
        if (opts != null) {
            this.position = opts.getLocation();
            this.width = opts.getWidth();
            this.height = opts.getHeight();
            this.bounds = opts.getBounds();
            this.bearing = opts.getBearing();
            this.zIndex = opts.getZIndex();
            this.transparency = opts.getTransparency();
            this.visible = opts.isVisible();
            this.clickable = opts.isClickable();
        } else {
            this.visible = true;
        }
    }

    public String getId()                   { return id; }
    public LatLng getPosition()             { return position; }
    public void setPosition(LatLng p)       { this.position = p; }
    public float getWidth()                 { return width; }
    public float getHeight()                { return height; }
    public void setDimensions(float w)      { this.width = w; }
    public void setDimensions(float w, float h) { this.width = w; this.height = h; }
    public LatLngBounds getBounds()         { return bounds; }
    public void setPositionFromBounds(LatLngBounds b) { this.bounds = b; }
    public float getBearing()               { return bearing; }
    public void setBearing(float b)         { this.bearing = b; }
    public float getZIndex()                { return zIndex; }
    public void setZIndex(float z)          { this.zIndex = z; }
    public float getTransparency()          { return transparency; }
    public void setTransparency(float t)    { this.transparency = t; }
    public boolean isVisible()              { return visible; }
    public void setVisible(boolean v)       { this.visible = v; }
    public boolean isClickable()            { return clickable; }
    public void setClickable(boolean c)     { this.clickable = c; }
    public Object getTag()                  { return tag; }
    public void setTag(Object t)            { this.tag = t; }

    public void remove() {
        if (owner != null) {
            owner.macapkRemoveGroundOverlay(this);
            owner = null;
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroundOverlay)) return false;
        return id.equals(((GroundOverlay) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
}
