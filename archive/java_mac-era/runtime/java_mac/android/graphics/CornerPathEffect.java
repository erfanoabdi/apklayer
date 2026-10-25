// Mac.apk -- android.graphics.CornerPathEffect.
//
// Mirrors AOSP's public API surface: a PathEffect that transforms
// sharp angles between line segments into rounded corners of the
// given radius. AOSP's only public constructor is
// CornerPathEffect(float radius); the radius is otherwise opaque to
// callers (the native effect does the rounding), so we just retain it.
//
// Our Canvas is software (java.awt.Graphics2D), so there is no native
// effect object to build here. The radius is exposed via the
// macapk-prefixed accessor below so the Canvas/Paint layer can
// approximate the effect -- e.g. with a BasicStroke using JOIN_ROUND
// when this effect is attached to a stroking Paint. The prefix keeps
// it out of the AOSP-visible API surface so APK bytecode never sees a
// method that real Android lacks.
package android.graphics;

public class CornerPathEffect extends PathEffect {
    private final float mRadius;

    /**
     * Transforms geometries that are drawn (either STROKE or FILL
     * styles) by replacing sharp angles between line segments into
     * rounded angles of the specified radius.
     *
     * @param radius the radius applied to round the corners.
     */
    public CornerPathEffect(float radius) {
        mRadius = radius;
    }

    /** Mac.apk accessor: the corner radius this effect was built with. */
    public float macapkRadius() {
        return mRadius;
    }
}
