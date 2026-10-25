// com.google.android.gms.maps.OnMapReadyCallback -- single-method callback
// fired when a MapView/MapFragment's GoogleMap instance is ready. Our inert
// map fires this with a non-null blank GoogleMap so the hosting screen
// proceeds to render its UI.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/OnMapReadyCallback
package com.google.android.gms.maps;

public interface OnMapReadyCallback {
    void onMapReady(GoogleMap googleMap);
}
