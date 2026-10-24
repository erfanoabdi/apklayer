// com.google.android.gms.maps.GoogleMap -- the central map controller handed
// to OnMapReadyCallback. This is a FUNCTIONAL-BUT-BLANK map: per the project
// GMS policy (de-Googled device, never fetch/fake Google data), it stores all
// overlay + camera + UI state faithfully so apps can add markers, move the
// camera, register listeners, and read everything back, but it NEVER fetches
// map tiles or draws geography. Add* returns a live handle, getCameraPosition
// reflects the last applied CameraUpdate, listeners are stored (and fired
// where we can synthesize the event locally). No Google backend is contacted.
//
// In the real SDK GoogleMap is `final` with no public constructor; we keep it
// public + non-final-constructible internally (MapView builds it) but apps
// only ever receive it via onMapReady, matching the observable contract.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/GoogleMap
package com.google.android.gms.maps;

import android.graphics.Bitmap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class GoogleMap {
    // Map type constants (match the SDK's int values exactly).
    public static final int MAP_TYPE_NONE      = 0;
    public static final int MAP_TYPE_NORMAL    = 1;
    public static final int MAP_TYPE_SATELLITE = 2;
    public static final int MAP_TYPE_TERRAIN   = 3;
    public static final int MAP_TYPE_HYBRID    = 4;

    // Camera-move reason codes for OnCameraMoveStartedListener.
    public static final int OnCameraMoveStartedListener_REASON_GESTURE = 1;
    public static final int OnCameraMoveStartedListener_REASON_API_ANIMATION = 2;
    public static final int OnCameraMoveStartedListener_REASON_DEVELOPER_ANIMATION = 3;

    private int mapType = MAP_TYPE_NORMAL;
    private CameraPosition cameraPosition =
        new CameraPosition(new LatLng(0.0D, 0.0D), 2.0F, 0.0F, 0.0F);
    private float minZoom = 2.0F;
    private float maxZoom = 21.0F;
    private boolean trafficEnabled = false;
    private boolean indoorEnabled = false;
    private boolean buildingsEnabled = true;
    private boolean myLocationEnabled = false;
    private boolean mapStyleSet = false;

    private final UiSettings uiSettings = new UiSettings();
    private final Projection projection = new Projection(this);

    private final List<Marker> markers = new ArrayList<Marker>();
    private final List<Polyline> polylines = new ArrayList<Polyline>();
    private final List<Polygon> polygons = new ArrayList<Polygon>();
    private final List<Circle> circles = new ArrayList<Circle>();
    private final List<GroundOverlay> groundOverlays = new ArrayList<GroundOverlay>();
    private final AtomicLong idSeq = new AtomicLong(0L);

    // Stored listeners (fired where we can synthesize the event locally).
    private OnMarkerClickListener markerClickListener;
    private OnMapClickListener mapClickListener;
    private OnMapLongClickListener mapLongClickListener;
    private OnCameraIdleListener cameraIdleListener;
    private OnCameraMoveListener cameraMoveListener;
    private OnCameraMoveStartedListener cameraMoveStartedListener;
    private OnCameraMoveCanceledListener cameraMoveCanceledListener;
    private OnInfoWindowClickListener infoWindowClickListener;
    private OnMarkerDragListener markerDragListener;
    private OnMyLocationButtonClickListener myLocationButtonClickListener;

    // MapView builds this; apps receive it only via onMapReady.
    GoogleMap() {}

    public int getMapType() { return mapType; }
    public void setMapType(int type) { this.mapType = type; }

    public UiSettings getUiSettings() { return uiSettings; }
    public Projection getProjection() { return projection; }

    public CameraPosition getCameraPosition() { return cameraPosition; }

    public float getMaxZoomLevel() { return maxZoom; }
    public float getMinZoomLevel() { return minZoom; }
    public void setMinZoomPreference(float minZoomPreference) { this.minZoom = minZoomPreference; }
    public void setMaxZoomPreference(float maxZoomPreference) { this.maxZoom = maxZoomPreference; }
    public void resetMinMaxZoomPreference() { this.minZoom = 2.0F; this.maxZoom = 21.0F; }

    public boolean isTrafficEnabled()  { return trafficEnabled; }
    public void setTrafficEnabled(boolean enabled) { this.trafficEnabled = enabled; }
    public boolean isIndoorEnabled()   { return indoorEnabled; }
    public boolean setIndoorEnabled(boolean enabled) { this.indoorEnabled = enabled; return true; }
    public boolean isBuildingsEnabled() { return buildingsEnabled; }
    public void setBuildingsEnabled(boolean enabled) { this.buildingsEnabled = enabled; }
    public boolean isMyLocationEnabled() { return myLocationEnabled; }
    public void setMyLocationEnabled(boolean enabled) { this.myLocationEnabled = enabled; }

    public boolean setMapStyle(MapStyleOptions style) {
        // We do not render, but report success so styled maps proceed.
        this.mapStyleSet = (style != null);
        return true;
    }

    public void setPadding(int left, int top, int right, int bottom) { /* inert */ }
    public void setLatLngBoundsForCameraTarget(
            com.google.android.gms.maps.model.LatLngBounds bounds) { /* inert */ }
    public void setContentDescription(String description) { /* inert */ }

    // ---- camera control ----------------------------------------------

    public void moveCamera(CameraUpdate update) {
        applyCameraUpdate(update);
        // A move is instantaneous: notify idle immediately.
        if (cameraMoveListener != null) cameraMoveListener.onCameraMove();
        if (cameraIdleListener != null) cameraIdleListener.onCameraIdle();
    }

    public void animateCamera(CameraUpdate update) {
        animateCamera(update, null);
    }

    public void animateCamera(CameraUpdate update, int durationMs, CancelableCallback callback) {
        animateCamera(update, callback);
    }

    public void animateCamera(CameraUpdate update, CancelableCallback callback) {
        // No animation surface; apply instantly and report completion so
        // apps waiting on the callback proceed.
        applyCameraUpdate(update);
        if (cameraMoveListener != null) cameraMoveListener.onCameraMove();
        if (cameraIdleListener != null) cameraIdleListener.onCameraIdle();
        if (callback != null) callback.onFinish();
    }

    public void stopAnimation() { /* nothing animating */ }

    private void applyCameraUpdate(CameraUpdate update) {
        if (update == null) return;
        LatLng target = cameraPosition.target;
        float zoom = cameraPosition.zoom;
        float tilt = cameraPosition.tilt;
        float bearing = cameraPosition.bearing;
        if (update.camera != null) {
            cameraPosition = update.camera;
            return;
        }
        if (update.target != null) target = update.target;
        if (update.bounds != null) target = update.bounds.getCenter();
        if (update.zoom != null) zoom = update.zoom;
        if (update.zoomDelta != null) zoom = zoom + update.zoomDelta;
        // Honor the zoom preference range.
        zoom = Math.max(minZoom, Math.min(maxZoom, zoom));
        cameraPosition = new CameraPosition(target, zoom, tilt, bearing);
    }

    // ---- overlays -----------------------------------------------------

    public Marker addMarker(MarkerOptions options) {
        Marker m = new Marker(this, nextId("m"), options);
        markers.add(m);
        return m;
    }

    public Polyline addPolyline(PolylineOptions options) {
        Polyline p = new Polyline(this, nextId("pl"), options);
        polylines.add(p);
        return p;
    }

    public Polygon addPolygon(PolygonOptions options) {
        Polygon p = new Polygon(this, nextId("pg"), options);
        polygons.add(p);
        return p;
    }

    public Circle addCircle(CircleOptions options) {
        Circle c = new Circle(this, nextId("c"), options);
        circles.add(c);
        return c;
    }

    public GroundOverlay addGroundOverlay(GroundOverlayOptions options) {
        GroundOverlay g = new GroundOverlay(this, nextId("go"), options);
        groundOverlays.add(g);
        return g;
    }

    public void clear() {
        markers.clear();
        polylines.clear();
        polygons.clear();
        circles.clear();
        groundOverlays.clear();
    }

    private String nextId(String prefix) {
        return prefix + idSeq.getAndIncrement();
    }

    // Removal hooks invoked by the live overlay objects. Public because the
    // overlay classes live in the maps.model subpackage; not part of the GMS
    // public API surface (macapk-prefixed so apps never collide with them).
    public void macapkRemoveMarker(Marker m)            { markers.remove(m); }
    public void macapkRemovePolyline(Polyline p)        { polylines.remove(p); }
    public void macapkRemovePolygon(Polygon p)          { polygons.remove(p); }
    public void macapkRemoveCircle(Circle c)            { circles.remove(c); }
    public void macapkRemoveGroundOverlay(GroundOverlay g) { groundOverlays.remove(g); }

    // ---- snapshot -----------------------------------------------------

    public void snapshot(SnapshotReadyCallback callback) {
        // Nothing is rendered, so there is no bitmap to capture.
        if (callback != null) callback.onSnapshotReady(null);
    }

    public void snapshot(SnapshotReadyCallback callback, Bitmap bitmap) {
        if (callback != null) callback.onSnapshotReady(bitmap);
    }

    // ---- listeners ----------------------------------------------------

    public void setOnMarkerClickListener(OnMarkerClickListener listener) { this.markerClickListener = listener; }
    public void setOnMapClickListener(OnMapClickListener listener) { this.mapClickListener = listener; }
    public void setOnMapLongClickListener(OnMapLongClickListener listener) { this.mapLongClickListener = listener; }
    public void setOnCameraIdleListener(OnCameraIdleListener listener) { this.cameraIdleListener = listener; }
    public void setOnCameraMoveListener(OnCameraMoveListener listener) { this.cameraMoveListener = listener; }
    public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener listener) { this.cameraMoveStartedListener = listener; }
    public void setOnCameraMoveCanceledListener(OnCameraMoveCanceledListener listener) { this.cameraMoveCanceledListener = listener; }
    public void setOnInfoWindowClickListener(OnInfoWindowClickListener listener) { this.infoWindowClickListener = listener; }
    public void setOnMarkerDragListener(OnMarkerDragListener listener) { this.markerDragListener = listener; }
    public void setOnMyLocationButtonClickListener(OnMyLocationButtonClickListener listener) { this.myLocationButtonClickListener = listener; }
    public void setInfoWindowAdapter(InfoWindowAdapter adapter) { /* inert: no info window UI */ }

    public void setOnMapLoadedCallback(OnMapLoadedCallback callback) {
        // The blank map is "loaded" the moment it is ready: fire immediately.
        if (callback != null) callback.onMapLoaded();
    }

    // ---- nested callback / listener interfaces ------------------------

    public interface CancelableCallback {
        void onFinish();
        void onCancel();
    }

    public interface SnapshotReadyCallback {
        void onSnapshotReady(Bitmap snapshot);
    }

    public interface OnMapLoadedCallback {
        void onMapLoaded();
    }

    public interface OnMarkerClickListener {
        boolean onMarkerClick(Marker marker);
    }

    public interface OnMapClickListener {
        void onMapClick(LatLng point);
    }

    public interface OnMapLongClickListener {
        void onMapLongClick(LatLng point);
    }

    public interface OnCameraIdleListener {
        void onCameraIdle();
    }

    public interface OnCameraMoveListener {
        void onCameraMove();
    }

    public interface OnCameraMoveStartedListener {
        void onCameraMoveStarted(int reason);
    }

    public interface OnCameraMoveCanceledListener {
        void onCameraMoveCanceled();
    }

    public interface OnInfoWindowClickListener {
        void onInfoWindowClick(Marker marker);
    }

    public interface OnMarkerDragListener {
        void onMarkerDragStart(Marker marker);
        void onMarkerDrag(Marker marker);
        void onMarkerDragEnd(Marker marker);
    }

    public interface OnMyLocationButtonClickListener {
        boolean onMyLocationButtonClick();
    }

    public interface InfoWindowAdapter {
        android.view.View getInfoWindow(Marker marker);
        android.view.View getInfoContents(Marker marker);
    }
}
