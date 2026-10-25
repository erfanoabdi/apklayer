// com.google.android.gms.maps.GoogleMapOptions -- a fluent options bag
// passed to MapView/MapFragment at construction to pre-configure the map
// (type, initial camera, gesture/UI toggles). Faithful fluent surface;
// MapView reads the type + camera onto the GoogleMap it builds.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/GoogleMapOptions
package com.google.android.gms.maps;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLngBounds;

public final class GoogleMapOptions implements Parcelable {
    private int mapType = GoogleMap.MAP_TYPE_NORMAL;
    private CameraPosition camera;
    private Boolean zOrderOnTop;
    private Boolean useViewLifecycleInFragment;
    private Boolean compassEnabled;
    private Boolean rotateGesturesEnabled;
    private Boolean scrollGesturesEnabled;
    private Boolean tiltGesturesEnabled;
    private Boolean zoomControlsEnabled;
    private Boolean zoomGesturesEnabled;
    private Boolean liteMode;
    private Boolean mapToolbarEnabled;
    private Boolean ambientEnabled;
    private Float minZoomPreference;
    private Float maxZoomPreference;
    private LatLngBounds latLngBoundsForCameraTarget;

    public GoogleMapOptions() {}

    public GoogleMapOptions mapType(int mapType) { this.mapType = mapType; return this; }
    public GoogleMapOptions camera(CameraPosition camera) { this.camera = camera; return this; }
    public GoogleMapOptions zOrderOnTop(boolean v) { this.zOrderOnTop = v; return this; }
    public GoogleMapOptions useViewLifecycleInFragment(boolean v) { this.useViewLifecycleInFragment = v; return this; }
    public GoogleMapOptions compassEnabled(boolean v) { this.compassEnabled = v; return this; }
    public GoogleMapOptions rotateGesturesEnabled(boolean v) { this.rotateGesturesEnabled = v; return this; }
    public GoogleMapOptions scrollGesturesEnabled(boolean v) { this.scrollGesturesEnabled = v; return this; }
    public GoogleMapOptions tiltGesturesEnabled(boolean v) { this.tiltGesturesEnabled = v; return this; }
    public GoogleMapOptions zoomControlsEnabled(boolean v) { this.zoomControlsEnabled = v; return this; }
    public GoogleMapOptions zoomGesturesEnabled(boolean v) { this.zoomGesturesEnabled = v; return this; }
    public GoogleMapOptions liteMode(boolean v) { this.liteMode = v; return this; }
    public GoogleMapOptions mapToolbarEnabled(boolean v) { this.mapToolbarEnabled = v; return this; }
    public GoogleMapOptions ambientEnabled(boolean v) { this.ambientEnabled = v; return this; }
    public GoogleMapOptions minZoomPreference(float v) { this.minZoomPreference = v; return this; }
    public GoogleMapOptions maxZoomPreference(float v) { this.maxZoomPreference = v; return this; }
    public GoogleMapOptions latLngBoundsForCameraTarget(LatLngBounds b) { this.latLngBoundsForCameraTarget = b; return this; }

    public int getMapType() { return mapType; }
    public CameraPosition getCamera() { return camera; }
    public Boolean getZOrderOnTop() { return zOrderOnTop; }
    public Boolean getUseViewLifecycleInFragment() { return useViewLifecycleInFragment; }
    public Boolean getCompassEnabled() { return compassEnabled; }
    public Boolean getRotateGesturesEnabled() { return rotateGesturesEnabled; }
    public Boolean getScrollGesturesEnabled() { return scrollGesturesEnabled; }
    public Boolean getTiltGesturesEnabled() { return tiltGesturesEnabled; }
    public Boolean getZoomControlsEnabled() { return zoomControlsEnabled; }
    public Boolean getZoomGesturesEnabled() { return zoomGesturesEnabled; }
    public Boolean getLiteMode() { return liteMode; }
    public Boolean getMapToolbarEnabled() { return mapToolbarEnabled; }
    public Boolean getAmbientEnabled() { return ambientEnabled; }
    public Float getMinZoomPreference() { return minZoomPreference; }
    public Float getMaxZoomPreference() { return maxZoomPreference; }
    public LatLngBounds getLatLngBoundsForCameraTarget() { return latLngBoundsForCameraTarget; }

    public static GoogleMapOptions createFromAttributes(
            android.content.Context context, android.util.AttributeSet attrs) {
        // No XML attribute parsing here; return defaults so layout-inflated
        // MapViews still get a valid options object.
        return new GoogleMapOptions();
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mapType);
        dest.writeParcelable(camera, flags);
    }

    public static final Parcelable.Creator<GoogleMapOptions> CREATOR =
            new Parcelable.Creator<GoogleMapOptions>() {
        @Override public GoogleMapOptions createFromParcel(Parcel in) {
            GoogleMapOptions o = new GoogleMapOptions();
            o.mapType = in.readInt();
            o.camera = in.readParcelable(CameraPosition.class.getClassLoader());
            return o;
        }
        @Override public GoogleMapOptions[] newArray(int size) { return new GoogleMapOptions[size]; }
    };
}
