// com.google.android.gms.maps.model.Circle -- live handle returned by
// GoogleMap.addCircle(CircleOptions). Mutable value object on the inert
// map; getters/setters round-trip and remove() drops it from the map.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/Circle
package com.google.android.gms.maps.model;

public final class Circle {
    private com.google.android.gms.maps.GoogleMap owner;
    private final String id;
    private LatLng center;
    private double radius;
    private float strokeWidth;
    private int strokeColor;
    private int fillColor;
    private float zIndex;
    private boolean visible;
    private boolean clickable;
    private Object tag;

    public Circle(com.google.android.gms.maps.GoogleMap owner, String id, CircleOptions opts) {
        this.owner = owner;
        this.id = id;
        if (opts != null) {
            this.center = opts.getCenter();
            this.radius = opts.getRadius();
            this.strokeWidth = opts.getStrokeWidth();
            this.strokeColor = opts.getStrokeColor();
            this.fillColor = opts.getFillColor();
            this.zIndex = opts.getZIndex();
            this.visible = opts.isVisible();
            this.clickable = opts.isClickable();
        } else {
            this.visible = true;
        }
    }

    public String getId()               { return id; }
    public LatLng getCenter()           { return center; }
    public void setCenter(LatLng c)     { this.center = c; }
    public double getRadius()           { return radius; }
    public void setRadius(double r)     { this.radius = r; }
    public float getStrokeWidth()       { return strokeWidth; }
    public void setStrokeWidth(float w) { this.strokeWidth = w; }
    public int getStrokeColor()         { return strokeColor; }
    public void setStrokeColor(int c)   { this.strokeColor = c; }
    public int getFillColor()           { return fillColor; }
    public void setFillColor(int c)     { this.fillColor = c; }
    public float getZIndex()            { return zIndex; }
    public void setZIndex(float z)      { this.zIndex = z; }
    public boolean isVisible()          { return visible; }
    public void setVisible(boolean v)   { this.visible = v; }
    public boolean isClickable()        { return clickable; }
    public void setClickable(boolean c) { this.clickable = c; }
    public Object getTag()              { return tag; }
    public void setTag(Object t)        { this.tag = t; }

    public void remove() {
        if (owner != null) {
            owner.macapkRemoveCircle(this);
            owner = null;
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Circle)) return false;
        return id.equals(((Circle) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
}
