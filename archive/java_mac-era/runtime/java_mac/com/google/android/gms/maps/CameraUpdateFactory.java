// com.google.android.gms.maps.CameraUpdateFactory -- static factory for
// CameraUpdate tokens. We honor every documented factory by capturing the
// requested change on the returned CameraUpdate so the inert GoogleMap can
// apply it to its stored CameraPosition. No rendering occurs.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/CameraUpdateFactory
package com.google.android.gms.maps;

import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

public final class CameraUpdateFactory {
    private CameraUpdateFactory() {}

    public static CameraUpdate newLatLng(LatLng latLng) {
        return new CameraUpdate(latLng, null, null, null, null);
    }

    public static CameraUpdate newLatLngZoom(LatLng latLng, float zoom) {
        return new CameraUpdate(latLng, zoom, null, null, null);
    }

    public static CameraUpdate newLatLngBounds(LatLngBounds bounds, int padding) {
        return new CameraUpdate(null, null, null, null, bounds);
    }

    public static CameraUpdate newLatLngBounds(LatLngBounds bounds, int width, int height, int padding) {
        return new CameraUpdate(null, null, null, null, bounds);
    }

    public static CameraUpdate newCameraPosition(CameraPosition cameraPosition) {
        return new CameraUpdate(null, null, null, cameraPosition, null);
    }

    public static CameraUpdate zoomTo(float zoom) {
        return new CameraUpdate(null, zoom, null, null, null);
    }

    public static CameraUpdate zoomBy(float amount) {
        return new CameraUpdate(null, null, amount, null, null);
    }

    public static CameraUpdate zoomBy(float amount, android.graphics.Point focus) {
        return new CameraUpdate(null, null, amount, null, null);
    }

    public static CameraUpdate zoomIn() {
        return new CameraUpdate(null, null, 1.0F, null, null);
    }

    public static CameraUpdate zoomOut() {
        return new CameraUpdate(null, null, -1.0F, null, null);
    }

    public static CameraUpdate scrollBy(float xPixel, float yPixel) {
        // Pixel scroll has no meaning on an inert map; return a no-op update.
        return new CameraUpdate(null, null, null, null, null);
    }
}
