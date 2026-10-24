// com.google.android.gms.maps.model.Polygon -- live handle returned by
// GoogleMap.addPolygon(PolygonOptions). Mutable value object on the inert
// map; getters/setters round-trip and remove() drops it from the map.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/Polygon
package com.google.android.gms.maps.model;

import java.util.ArrayList;
import java.util.List;

public final class Polygon {
    private com.google.android.gms.maps.GoogleMap owner;
    private final String id;
    private List<LatLng> points;
    private List<List<LatLng>> holes;
    private float strokeWidth;
    private int strokeColor;
    private int fillColor;
    private float zIndex;
    private boolean visible;
    private boolean geodesic;
    private boolean clickable;
    private Object tag;

    public Polygon(com.google.android.gms.maps.GoogleMap owner, String id, PolygonOptions opts) {
        this.owner = owner;
        this.id = id;
        if (opts != null) {
            this.points = new ArrayList<LatLng>(opts.getPoints());
            this.holes = new ArrayList<List<LatLng>>(opts.getHoles());
            this.strokeWidth = opts.getStrokeWidth();
            this.strokeColor = opts.getStrokeColor();
            this.fillColor = opts.getFillColor();
            this.zIndex = opts.getZIndex();
            this.visible = opts.isVisible();
            this.geodesic = opts.isGeodesic();
            this.clickable = opts.isClickable();
        } else {
            this.points = new ArrayList<LatLng>();
            this.holes = new ArrayList<List<LatLng>>();
            this.visible = true;
        }
    }

    public String getId()                { return id; }
    public List<LatLng> getPoints()      { return points; }
    public void setPoints(List<LatLng> pts) { this.points = (pts != null) ? new ArrayList<LatLng>(pts) : new ArrayList<LatLng>(); }
    public List<List<LatLng>> getHoles() { return holes; }
    public void setHoles(List<? extends List<LatLng>> h) {
        this.holes = new ArrayList<List<LatLng>>();
        if (h != null) for (List<LatLng> ring : h) this.holes.add(new ArrayList<LatLng>(ring));
    }
    public float getStrokeWidth()        { return strokeWidth; }
    public void setStrokeWidth(float w)  { this.strokeWidth = w; }
    public int getStrokeColor()          { return strokeColor; }
    public void setStrokeColor(int c)    { this.strokeColor = c; }
    public int getFillColor()            { return fillColor; }
    public void setFillColor(int c)      { this.fillColor = c; }
    public float getZIndex()             { return zIndex; }
    public void setZIndex(float z)       { this.zIndex = z; }
    public boolean isVisible()           { return visible; }
    public void setVisible(boolean v)    { this.visible = v; }
    public boolean isGeodesic()          { return geodesic; }
    public void setGeodesic(boolean g)   { this.geodesic = g; }
    public boolean isClickable()         { return clickable; }
    public void setClickable(boolean c)  { this.clickable = c; }
    public Object getTag()               { return tag; }
    public void setTag(Object t)         { this.tag = t; }

    public void remove() {
        if (owner != null) {
            owner.macapkRemovePolygon(this);
            owner = null;
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Polygon)) return false;
        return id.equals(((Polygon) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
}
