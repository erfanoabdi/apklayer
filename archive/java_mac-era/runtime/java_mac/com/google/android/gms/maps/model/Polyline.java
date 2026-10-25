// com.google.android.gms.maps.model.Polyline -- live handle returned by
// GoogleMap.addPolyline(PolylineOptions). Mutable value object on the inert
// map; getters/setters round-trip and remove() drops it from the map.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/Polyline
package com.google.android.gms.maps.model;

import java.util.ArrayList;
import java.util.List;

public final class Polyline {
    private com.google.android.gms.maps.GoogleMap owner;
    private final String id;
    private List<LatLng> points;
    private float width;
    private int color;
    private float zIndex;
    private boolean visible;
    private boolean geodesic;
    private boolean clickable;
    private Object tag;

    public Polyline(com.google.android.gms.maps.GoogleMap owner, String id, PolylineOptions opts) {
        this.owner = owner;
        this.id = id;
        if (opts != null) {
            this.points = new ArrayList<LatLng>(opts.getPoints());
            this.width = opts.getWidth();
            this.color = opts.getColor();
            this.zIndex = opts.getZIndex();
            this.visible = opts.isVisible();
            this.geodesic = opts.isGeodesic();
            this.clickable = opts.isClickable();
        } else {
            this.points = new ArrayList<LatLng>();
            this.visible = true;
        }
    }

    public String getId()              { return id; }
    public List<LatLng> getPoints()    { return points; }
    public void setPoints(List<LatLng> pts) { this.points = (pts != null) ? new ArrayList<LatLng>(pts) : new ArrayList<LatLng>(); }
    public float getWidth()            { return width; }
    public void setWidth(float w)      { this.width = w; }
    public int getColor()              { return color; }
    public void setColor(int c)        { this.color = c; }
    public float getZIndex()           { return zIndex; }
    public void setZIndex(float z)     { this.zIndex = z; }
    public boolean isVisible()         { return visible; }
    public void setVisible(boolean v)  { this.visible = v; }
    public boolean isGeodesic()        { return geodesic; }
    public void setGeodesic(boolean g) { this.geodesic = g; }
    public boolean isClickable()       { return clickable; }
    public void setClickable(boolean c) { this.clickable = c; }
    public Object getTag()             { return tag; }
    public void setTag(Object t)       { this.tag = t; }

    public void remove() {
        if (owner != null) {
            owner.macapkRemovePolyline(this);
            owner = null;
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Polyline)) return false;
        return id.equals(((Polyline) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
}
