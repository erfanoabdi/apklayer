// com.google.android.gms.maps.UiSettings -- toggles for the map's on-screen
// gesture / control surface (zoom buttons, compass, my-location button,
// gesture enables). On the inert map nothing is drawn, but every setter
// stores its value and every getter reads it back so apps configure the
// UI exactly as on a real device.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/UiSettings
package com.google.android.gms.maps;

public final class UiSettings {
    private boolean zoomControlsEnabled = false;
    private boolean compassEnabled = true;
    private boolean myLocationButtonEnabled = true;
    private boolean indoorLevelPickerEnabled = true;
    private boolean scrollGesturesEnabled = true;
    private boolean scrollGesturesEnabledDuringRotateOrZoom = true;
    private boolean zoomGesturesEnabled = true;
    private boolean tiltGesturesEnabled = true;
    private boolean rotateGesturesEnabled = true;
    private boolean mapToolbarEnabled = true;

    UiSettings() {}

    public void setZoomControlsEnabled(boolean enabled)   { this.zoomControlsEnabled = enabled; }
    public boolean isZoomControlsEnabled()                { return zoomControlsEnabled; }
    public void setCompassEnabled(boolean enabled)        { this.compassEnabled = enabled; }
    public boolean isCompassEnabled()                     { return compassEnabled; }
    public void setMyLocationButtonEnabled(boolean enabled) { this.myLocationButtonEnabled = enabled; }
    public boolean isMyLocationButtonEnabled()            { return myLocationButtonEnabled; }
    public void setIndoorLevelPickerEnabled(boolean enabled) { this.indoorLevelPickerEnabled = enabled; }
    public boolean isIndoorLevelPickerEnabled()           { return indoorLevelPickerEnabled; }
    public void setScrollGesturesEnabled(boolean enabled) { this.scrollGesturesEnabled = enabled; }
    public boolean isScrollGesturesEnabled()              { return scrollGesturesEnabled; }
    public void setScrollGesturesEnabledDuringRotateOrZoom(boolean enabled) { this.scrollGesturesEnabledDuringRotateOrZoom = enabled; }
    public boolean isScrollGesturesEnabledDuringRotateOrZoom() { return scrollGesturesEnabledDuringRotateOrZoom; }
    public void setZoomGesturesEnabled(boolean enabled)   { this.zoomGesturesEnabled = enabled; }
    public boolean isZoomGesturesEnabled()                { return zoomGesturesEnabled; }
    public void setTiltGesturesEnabled(boolean enabled)   { this.tiltGesturesEnabled = enabled; }
    public boolean isTiltGesturesEnabled()                { return tiltGesturesEnabled; }
    public void setRotateGesturesEnabled(boolean enabled) { this.rotateGesturesEnabled = enabled; }
    public boolean isRotateGesturesEnabled()              { return rotateGesturesEnabled; }
    public void setMapToolbarEnabled(boolean enabled)     { this.mapToolbarEnabled = enabled; }
    public boolean isMapToolbarEnabled()                  { return mapToolbarEnabled; }

    public void setAllGesturesEnabled(boolean enabled) {
        this.scrollGesturesEnabled = enabled;
        this.zoomGesturesEnabled = enabled;
        this.tiltGesturesEnabled = enabled;
        this.rotateGesturesEnabled = enabled;
    }
}
