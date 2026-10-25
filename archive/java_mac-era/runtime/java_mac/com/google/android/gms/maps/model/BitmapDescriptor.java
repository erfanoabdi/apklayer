// com.google.android.gms.maps.model.BitmapDescriptor -- an opaque handle
// to a bitmap used to texture a marker / ground overlay. In the real SDK
// it wraps a remote IObjectWrapper; here it simply carries the optional
// hue / bitmap so MarkerOptions can store and return it. Nothing is drawn
// (the map is inert), so the handle is purely a value object.
// Ref: developers.google.com/android/reference/com/google/android/gms/maps/model/BitmapDescriptor
package com.google.android.gms.maps.model;

public final class BitmapDescriptor {
    // Package-visible payload so BitmapDescriptorFactory can populate it
    // and a future renderer could read it. Apps never inspect these.
    final float hue;
    final android.graphics.Bitmap bitmap;

    BitmapDescriptor(float hue, android.graphics.Bitmap bitmap) {
        this.hue = hue;
        this.bitmap = bitmap;
    }
}
