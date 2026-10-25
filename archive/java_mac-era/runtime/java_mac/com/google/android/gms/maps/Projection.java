// com.google.android.gms.maps.Projection -- converts between screen pixels
// and geographic coordinates for a GoogleMap. The inert map has no real
// viewport transform, so we return identity-ish defaults (screen point ->
// camera target, target -> origin) and a world-spanning visible region so
// callers get non-null, sane-shaped results instead of crashing.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/Projection
package com.google.android.gms.maps;

import android.graphics.Point;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.VisibleRegion;

public final class Projection {
    private final GoogleMap map;

    Projection(GoogleMap map) { this.map = map; }

    public LatLng fromScreenLocation(Point point) {
        // No viewport transform on the inert map: report the camera target.
        com.google.android.gms.maps.model.CameraPosition cp = map.getCameraPosition();
        return (cp != null) ? cp.target : new LatLng(0.0D, 0.0D);
    }

    public Point toScreenLocation(LatLng location) {
        // Map everything to the origin; nothing is rendered to anchor to.
        return new Point(0, 0);
    }

    public VisibleRegion getVisibleRegion() {
        // A blank map "sees" the whole world; hand back a full-extent region.
        LatLng sw = new LatLng(-85.0D, -180.0D);
        LatLng ne = new LatLng(85.0D, 180.0D);
        LatLng nw = new LatLng(85.0D, -180.0D);
        LatLng se = new LatLng(-85.0D, 180.0D);
        LatLngBounds bounds = new LatLngBounds(sw, ne);
        return new VisibleRegion(sw, se, nw, ne, bounds);
    }
}
