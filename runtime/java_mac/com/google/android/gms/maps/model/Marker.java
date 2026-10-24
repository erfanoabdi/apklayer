// com.google.android.gms.maps.model.Marker -- the live handle returned by
// GoogleMap.addMarker(MarkerOptions). On a real device it proxies a remote
// rendered marker; here it is a mutable value object stored in the inert
// GoogleMap. Every getter/setter round-trips, remove() drops it from the
// map's marker list, equals/hashCode key on the synthetic id, exactly as
// the SDK documents.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/Marker
package com.google.android.gms.maps.model;

public final class Marker {
    // The owning map handles removal; may be null if the map was GC'd.
    private com.google.android.gms.maps.GoogleMap owner;
    private final String id;
    private LatLng position;
    private String title;
    private String snippet;
    private BitmapDescriptor icon;
    private float alpha;
    private float rotation;
    private boolean visible;
    private boolean draggable;
    private boolean flat;
    private float zIndex;
    private float anchorU, anchorV;
    private float infoWindowAnchorU, infoWindowAnchorV;
    private Object tag;

    public Marker(com.google.android.gms.maps.GoogleMap owner, String id, MarkerOptions opts) {
        this.owner = owner;
        this.id = id;
        if (opts != null) {
            this.position = opts.getPosition();
            this.title = opts.getTitle();
            this.snippet = opts.getSnippet();
            this.icon = opts.getIcon();
            this.alpha = opts.getAlpha();
            this.rotation = opts.getRotation();
            this.visible = opts.isVisible();
            this.draggable = opts.isDraggable();
            this.flat = opts.isFlat();
            this.zIndex = opts.getZIndex();
            this.anchorU = opts.getAnchorU();
            this.anchorV = opts.getAnchorV();
            this.infoWindowAnchorU = opts.getInfoWindowAnchorU();
            this.infoWindowAnchorV = opts.getInfoWindowAnchorV();
        } else {
            this.alpha = 1.0F;
            this.visible = true;
        }
    }

    public String getId()                  { return id; }
    public LatLng getPosition()            { return position; }
    public void setPosition(LatLng p)      { this.position = p; }
    public String getTitle()               { return title; }
    public void setTitle(String t)         { this.title = t; }
    public String getSnippet()             { return snippet; }
    public void setSnippet(String s)       { this.snippet = s; }
    public void setIcon(BitmapDescriptor i) { this.icon = i; }
    public float getAlpha()                { return alpha; }
    public void setAlpha(float a)          { this.alpha = a; }
    public float getRotation()             { return rotation; }
    public void setRotation(float r)       { this.rotation = r; }
    public boolean isVisible()             { return visible; }
    public void setVisible(boolean v)      { this.visible = v; }
    public boolean isDraggable()           { return draggable; }
    public void setDraggable(boolean d)    { this.draggable = d; }
    public boolean isFlat()                { return flat; }
    public void setFlat(boolean f)         { this.flat = f; }
    public float getZIndex()               { return zIndex; }
    public void setZIndex(float z)         { this.zIndex = z; }
    public void setAnchor(float u, float v) { this.anchorU = u; this.anchorV = v; }
    public void setInfoWindowAnchor(float u, float v) {
        this.infoWindowAnchorU = u; this.infoWindowAnchorV = v;
    }
    public Object getTag()                 { return tag; }
    public void setTag(Object t)           { this.tag = t; }

    // Inert map: there is no rendered info window, so these are no-ops
    // that match the void/boolean signatures apps call.
    public void showInfoWindow()           { }
    public void hideInfoWindow()           { }
    public boolean isInfoWindowShown()     { return false; }

    public void remove() {
        if (owner != null) {
            owner.macapkRemoveMarker(this);
            owner = null;
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Marker)) return false;
        return id.equals(((Marker) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
}
