package android.graphics;

// android.graphics.Shader -- base for gradient / bitmap fills. v1.0.429 adds the
// local-matrix accessors: Coil's SVG decoder (Now-in-Android topic icons) calls
// LinearGradient.setLocalMatrix(Matrix) to position a gradient; a missing method
// threw NoSuchMethodError. Subclasses (LinearGradient/RadialGradient/BitmapShader)
// inherit these. Stale-comment fix (v1.0.834): this used to say "our Canvas
// doesn't yet sample shaders" -- true when v1.0.429 landed, not since
// v1.0.433/v1.0.434 (Canvas.awtPaintForShader maps LinearGradient/
// RadialGradient/SweepGradient/BitmapShader/ComposeShader to a real AWT
// gradient/texture Paint, consulting macapkLocalAffine() below as the
// gradient transform).
public class Shader {
    // DECAL added API 31: sample transparent outside the shader bounds. We map
    // it to NO_CYCLE (CLAMP-like) in Canvas since AWT has no decal cycle.
    public enum TileMode { CLAMP, REPEAT, MIRROR, DECAL }

    private Matrix localMatrix;

    public Shader() {}

    public boolean getLocalMatrix(Matrix localM) {
        if (localMatrix == null) return false;
        if (localM != null) localM.set(localMatrix);
        return true;
    }
    public void setLocalMatrix(Matrix localM) {
        if (localM == null) { this.localMatrix = null; return; }
        Matrix copy = new Matrix();
        copy.set(localM);
        this.localMatrix = copy;
    }

    /** Internal: the local matrix as an AWT AffineTransform, or null when none
     *  is set. Canvas passes this to LinearGradientPaint/RadialGradientPaint as
     *  the gradient transform so a shader positioned via setLocalMatrix (Coil's
     *  SVG decoder, Compose Brush with a transform) samples in the right space. */
    public java.awt.geom.AffineTransform macapkLocalAffine() {
        return (localMatrix != null && !localMatrix.isIdentity())
            ? localMatrix.macapkToAffine() : null;
    }
}
