package android.graphics;

/**
 * A DrawFilter subclass can be installed in a Canvas, allowing it to modify (or
 * disregard) the paint settings made by the application for each drawing call.
 *
 * Mac.apk note: this AWT-backed Canvas is software-only and does not honor the
 * filter's effects (PaintFlagsDrawFilter's clear/set flag masks are ignored at
 * raster time). The class exists so that bytecode which constructs a filter and
 * calls Canvas.setDrawFilter()/getDrawFilter() links and round-trips correctly,
 * mirroring AOSP's public API surface
 * (frameworks/base/graphics/java/android/graphics/DrawFilter.java), where the
 * base class is likewise an empty marker whose real behavior lives in the native
 * subclasses.
 */
public class DrawFilter {
    // AOSP's base class is intentionally empty; it serves only as the shared
    // supertype for PaintFlagsDrawFilter (and any future filter kinds). On real
    // Android a native pointer field backs the subclasses; we have no native peer
    // so there is nothing to store here.
    public DrawFilter() {
    }
}
