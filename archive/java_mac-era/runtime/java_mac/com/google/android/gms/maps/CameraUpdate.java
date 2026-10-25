// com.google.android.gms.maps.CameraUpdate -- an opaque camera-change token
// produced by CameraUpdateFactory and consumed by GoogleMap.moveCamera /
// animateCamera. In the real SDK it wraps a remote IObjectWrapper; here it
// carries enough to let the inert GoogleMap update its stored CameraPosition
// so getCameraPosition() reflects the requested change.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/CameraUpdate
package com.google.android.gms.maps;

import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

public final class CameraUpdate {
    // Package-visible payload read by GoogleMap when applying the update.
    final LatLng target;       // may be null (zoom-only / scroll updates)
    final Float zoom;          // absolute zoom, or null
    final Float zoomDelta;     // relative zoom change, or null
    final CameraPosition camera; // a full camera position, or null
    final LatLngBounds bounds; // for newLatLngBounds, or null

    CameraUpdate(LatLng target, Float zoom, Float zoomDelta,
                 CameraPosition camera, LatLngBounds bounds) {
        this.target = target;
        this.zoom = zoom;
        this.zoomDelta = zoomDelta;
        this.camera = camera;
        this.bounds = bounds;
    }
}
