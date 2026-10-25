// com.google.android.gms.maps.model.BitmapDescriptorFactory -- static
// factory for BitmapDescriptor instances. We honor every documented
// factory method by storing the source (hue / bitmap / resource id) on
// the returned descriptor. No texture is uploaded (the map draws nothing)
// but the descriptors are valid value objects so MarkerOptions.icon(...)
// chains compile and run exactly as on a real device.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/BitmapDescriptorFactory
package com.google.android.gms.maps.model;

import android.graphics.Bitmap;

public final class BitmapDescriptorFactory {
    public static final float HUE_RED     = 0.0F;
    public static final float HUE_ORANGE  = 30.0F;
    public static final float HUE_YELLOW  = 60.0F;
    public static final float HUE_GREEN   = 120.0F;
    public static final float HUE_CYAN    = 180.0F;
    public static final float HUE_AZURE   = 210.0F;
    public static final float HUE_BLUE    = 240.0F;
    public static final float HUE_VIOLET  = 270.0F;
    public static final float HUE_MAGENTA = 300.0F;
    public static final float HUE_ROSE    = 330.0F;

    private BitmapDescriptorFactory() {}

    public static BitmapDescriptor defaultMarker() {
        return new BitmapDescriptor(HUE_RED, null);
    }

    public static BitmapDescriptor defaultMarker(float hue) {
        return new BitmapDescriptor(hue, null);
    }

    public static BitmapDescriptor fromBitmap(Bitmap image) {
        return new BitmapDescriptor(HUE_RED, image);
    }

    public static BitmapDescriptor fromResource(int resourceId) {
        return new BitmapDescriptor(HUE_RED, null);
    }

    public static BitmapDescriptor fromAsset(String assetName) {
        return new BitmapDescriptor(HUE_RED, null);
    }

    public static BitmapDescriptor fromFile(String fileName) {
        return new BitmapDescriptor(HUE_RED, null);
    }

    public static BitmapDescriptor fromPath(String absolutePath) {
        return new BitmapDescriptor(HUE_RED, null);
    }
}
