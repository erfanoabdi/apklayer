// com.google.android.gms.maps.MapView -- a View that hosts a GoogleMap.
// Per the project GMS policy this is an INERT BLANK MAP: it is a real
// FrameLayout subclass so it lays out and occupies its slot in the UI
// (the hosting screen renders), and getMapAsync fires onMapReady with a
// non-null, functional-but-blank GoogleMap so the app's map-setup code runs.
// No map tiles are fetched or drawn. All lifecycle methods are accepted
// (they drive nothing because there is no live map surface).
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/MapView
package com.google.android.gms.maps;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.gms.maps.model.CameraPosition;
import java.util.ArrayList;
import java.util.List;

public class MapView extends android.widget.FrameLayout {
    private GoogleMap map;
    private GoogleMapOptions options;
    // Callbacks queued before the map is built (e.g. getMapAsync called
    // before onCreate). We build the map lazily on onCreate/getMapAsync.
    private final List<OnMapReadyCallback> pendingCallbacks = new ArrayList<OnMapReadyCallback>();
    private boolean created = false;

    public MapView(Context context) { super(context); }
    public MapView(Context context, AttributeSet attrs) { super(context, attrs); }
    public MapView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
    public MapView(Context context, GoogleMapOptions options) {
        super(context);
        this.options = options;
    }

    private void ensureMap() {
        if (map != null) return;
        map = new GoogleMap();
        if (options != null) {
            map.setMapType(options.getMapType());
            CameraPosition cam = options.getCamera();
            if (cam != null) {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(cam));
            }
            Float minZ = options.getMinZoomPreference();
            if (minZ != null) map.setMinZoomPreference(minZ);
            Float maxZ = options.getMaxZoomPreference();
            if (maxZ != null) map.setMaxZoomPreference(maxZ);
        }
    }

    private void flushPending() {
        if (!created || map == null) return;
        if (pendingCallbacks.isEmpty()) return;
        // Copy then clear so a callback that re-registers does not recurse.
        List<OnMapReadyCallback> toFire = new ArrayList<OnMapReadyCallback>(pendingCallbacks);
        pendingCallbacks.clear();
        for (OnMapReadyCallback cb : toFire) {
            if (cb != null) cb.onMapReady(map);
        }
    }

    public void getMapAsync(OnMapReadyCallback callback) {
        if (callback == null) return;
        ensureMap();
        if (created) {
            // Already created: fire right away with the blank map.
            callback.onMapReady(map);
        } else {
            pendingCallbacks.add(callback);
        }
    }

    // ---- lifecycle (forwarded by the host Activity/Fragment) ----------

    public void onCreate(android.os.Bundle savedInstanceState) {
        ensureMap();
        created = true;
        flushPending();
    }

    public void onStart() { }
    public void onResume() { }
    public void onPause() { }
    public void onStop() { }
    public void onDestroy() { }
    public void onLowMemory() { }
    public void onSaveInstanceState(android.os.Bundle outState) { }
    public void onEnterAmbient(android.os.Bundle ambientDetails) { }
    public void onExitAmbient() { }
}
