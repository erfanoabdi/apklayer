// android.graphics.Camera -- AOSP's 3D-transform helper used by
// view animations (RotateAnimation around X/Y, card-flip effects,
// Transformation matrices). On real Android this is a thin Java
// wrapper over a native Skia SkCamera that maintains a 4x4 model
// matrix and projects it down to a 3x3 affine matrix the Canvas
// can concat.
//
// We are software-only (java.awt-backed Canvas, no GPU, no true
// perspective projection), so we faithfully implement the parts of
// Camera that ARE representable as a 2D affine transform and
// approximate the out-of-plane parts:
//
//   - rotateZ  -> a genuine in-plane rotation (exact).
//   - translate x/y -> genuine translation (exact). z is tracked
//     and folded into a uniform perspective-ish scale so moving the
//     content "back" shrinks it, "forward" enlarges it (approx).
//   - rotateX / rotateY -> out-of-plane tilts that a 3x3 affine
//     cannot express exactly. We approximate them by scaling the
//     affected axis by cos(angle): a rotateY foreshortens width,
//     rotateX foreshortens height. This is a cheap pseudo-3D that
//     keeps card-flip / coverflow animations from looking frozen
//     (the card visibly squashes as it turns) without claiming
//     real perspective.
//
// Mirrors the AOSP public API surface exactly (method names, return
// types, parameter types) so APK bytecode binds.
package android.graphics;

public class Camera {
    // ⚠ v1.0.939: REAL 3D. This class used to fake rotateX/rotateY by scaling
    // the affine matrix by cos(angle) -- content squashed, but there was no
    // perspective term at all, so View.getMatrix() answered a plain identity
    // for any view with a 3D rotation and Camera-driven card-flip animations
    // had no depth. The comment justifying it said "a 3x3 affine cannot carry a
    // real depth component", which was true of the OLD android.graphics.Matrix
    // and is no longer: Matrix has held all nine values, MPERSP included, with
    // a perspective-aware invert, for some time.
    //
    // The model below is Skia's Sk3DView, and it is not a guess -- it was
    // derived from, and then checked against, the reference device's own
    // answers (fwprobe VW.mtx.rot3d_x30 / rot3d_y30 / rot3d_camdist on an
    // API-30 AVD). For a 200x100 view with pivot (100,50) and rotationX=30 the
    // reference reports
    //      1.0000 -0.0868  4.3403 / 0.0000 0.8226 8.8689 / 0.0000 -0.0009 1.0434
    // and this implementation reproduces every one of those to 4dp; likewise
    // rotationY=30. That is what pins the sign conventions and the 576.
    //
    // THE PROJECTION. We accumulate a 3x4 model transform (a 3x3 basis plus a
    // translation), whose columns are the images of the unit axes:
    //      U = M*(1,0,0)   V = M*(0,1,0)   O = M*(0,0,0)
    // and the camera at distance d in front of the plane projects it to
    //      [ Ux    Vx    Ox      ]
    //      [ Uy    Vy    Oy      ]
    //      [ Uz/d  Vz/d  1+Oz/d  ]
    // The W (z) column is dropped because the patch being projected is the
    // view's own plane; that is exactly what Sk3DView does.
    //
    // THE 576. Skia's camera sits 8 inches from the plane and converts inches
    // to pixels at 72 dpi, so d = 8*72 = 576 by default. AOSP's
    // View.setCameraDistance takes PIXELS and divides by densityDpi to get
    // inches (View.java), which is why the probe's explicit 2560 px at 320 dpi
    // is 8 inches and lands on the SAME matrix as the default -- the two
    // reference rows are identical, and that identity is what proves the unit
    // conversion rather than merely the constant.
    private static final float DEFAULT_LOCATION_Z = -576f;

    // The accumulated 3D model transform, row-major 3x4:
    //   m[0] m[1] m[2] | m[3]
    //   m[4] m[5] m[6] | m[7]
    //   m[8] m[9] m[10]| m[11]
    private final float[] mM = new float[12];

    private float mLocationX = 0f;
    private float mLocationY = 0f;
    private float mLocationZ = DEFAULT_LOCATION_Z;   // pixels, as AOSP stores it

    private final java.util.ArrayDeque<float[]> mStack = new java.util.ArrayDeque<>();

    public Camera() {
        identity(mM);
    }

    private static void identity(float[] m) {
        java.util.Arrays.fill(m, 0f);
        m[0] = 1f; m[5] = 1f; m[10] = 1f;
    }

    /** dst = dst * rhs, both 3x4 (the implicit 4th row is 0,0,0,1).
     *  PRE-concat, matching Sk3DView: every rotate/translate composes on the
     *  RIGHT, so the transform is applied in the object's own frame and the
     *  call order reads outermost-first. */
    private static void preConcat(float[] dst, float[] r) {
        final float[] o = new float[12];
        for (int row = 0; row < 3; row++) {
            final int d = row * 4;
            for (int col = 0; col < 3; col++) {
                o[d + col] = dst[d] * r[col] + dst[d + 1] * r[4 + col] + dst[d + 2] * r[8 + col];
            }
            o[d + 3] = dst[d] * r[3] + dst[d + 1] * r[7] + dst[d + 2] * r[11] + dst[d + 3];
        }
        System.arraycopy(o, 0, dst, 0, 12);
    }

    /** Saves the current camera transform onto a stack. */
    public void save() {
        mStack.push(mM.clone());
    }

    /** Restores the camera transform most recently pushed by save(). */
    public void restore() {
        float[] m = mStack.poll();
        if (m != null) System.arraycopy(m, 0, mM, 0, 12);
    }

    /** Translates in the object's own frame. z is a REAL depth now, so pushing
     *  content away from the eye foreshortens it through the projection rather
     *  than through the uniform-scale stand-in this used to apply. */
    public void translate(float x, float y, float z) {
        final float[] t = new float[12];
        identity(t);
        t[3] = x; t[7] = y; t[11] = z;
        preConcat(mM, t);
    }

    /** Rotation about X (an out-of-plane tilt). Sign pinned by the reference:
     *  rotationX=+30 must send the +Y axis to (0, cos, -sin). */
    public void rotateX(float deg) {
        final double r = Math.toRadians(deg);
        final float c = (float) Math.cos(r), s = (float) Math.sin(r);
        final float[] t = new float[12];
        identity(t);
        t[5] = c;  t[6] = s;
        t[9] = -s; t[10] = c;
        preConcat(mM, t);
    }

    /** Rotation about Y. Sign pinned by the reference: rotationY=+30 must send
     *  the +X axis to (cos, 0, sin). */
    public void rotateY(float deg) {
        final double r = Math.toRadians(deg);
        final float c = (float) Math.cos(r), s = (float) Math.sin(r);
        final float[] t = new float[12];
        identity(t);
        t[0] = c;  t[2] = -s;
        t[8] = s;  t[10] = c;
        preConcat(mM, t);
    }

    /** Rotation about Z -- a genuine in-plane rotation. */
    public void rotateZ(float deg) {
        final double r = Math.toRadians(-deg);
        final float c = (float) Math.cos(r), s = (float) Math.sin(r);
        final float[] t = new float[12];
        identity(t);
        t[0] = c; t[1] = -s;
        t[4] = s; t[5] = c;
        preConcat(mM, t);
    }

    /** Combined rotation, applied X then Y then Z as AOSP does. */
    public void rotate(float x, float y, float z) {
        rotateX(x);
        rotateY(y);
        rotateZ(z);
    }

    /** Sets the camera ("eye") location. AOSP takes INCHES and the native side
     *  multiplies by 72 to get pixels; we store pixels and report inches back
     *  out, which is the round trip getLocationZ has always promised. */
    public void setLocation(float x, float y, float z) {
        mLocationX = x;
        mLocationY = y;
        mLocationZ = z * 72f;
    }

    /** @return the camera's X location, in the same units passed to setLocation. */
    public float getLocationX() {
        return mLocationX;
    }

    /** @return the camera's Y location, in the same units passed to setLocation. */
    public float getLocationY() {
        return mLocationY;
    }

    /** @return the camera's Z location in inches (the internal value is pixels). */
    public float getLocationZ() {
        return mLocationZ / 72f;
    }

    /** Concatenates the current transform onto the canvas. */
    public void applyToCanvas(Canvas canvas) {
        if (canvas != null) {
            Matrix m = new Matrix();
            getMatrix(m);
            canvas.concat(m);
        }
    }

    /** Projects the accumulated 3D transform down to the 3x3 the rest of the
     *  graphics stack speaks. See the class header for the derivation. */
    public void getMatrix(Matrix matrix) {
        if (matrix == null) return;
        final float d = -mLocationZ;     // eye distance in front of the plane
        final float[] v = new float[9];
        v[Matrix.MSCALE_X] = mM[0];
        v[Matrix.MSKEW_X]  = mM[1];
        v[Matrix.MTRANS_X] = mM[3];
        v[Matrix.MSKEW_Y]  = mM[4];
        v[Matrix.MSCALE_Y] = mM[5];
        v[Matrix.MTRANS_Y] = mM[7];
        if (d != 0f) {
            v[Matrix.MPERSP_0] = mM[8] / d;
            v[Matrix.MPERSP_1] = mM[9] / d;
            v[Matrix.MPERSP_2] = 1f + mM[11] / d;
        } else {
            v[Matrix.MPERSP_0] = 0f;
            v[Matrix.MPERSP_1] = 0f;
            v[Matrix.MPERSP_2] = 1f;
        }
        matrix.setValues(v);
    }

    public float dotWithNormal(float dx, float dy, float dz) {
        // View normal = (0, 0, 1); dot((dx,dy,dz), (0,0,1)) = dz.
        return dz;
    }

    /**
     * Computes the incremental uniform scale produced by translating
     * content by dz along the depth axis. Standard pinhole projection:
     * an object at distance D from the eye projects at scale eye/D, so
     * moving it toward the eye by dz (D shrinks) enlarges it and moving
     * it away shrinks it. We return the ratio relative to the unmoved
     * position so the scale composes correctly onto the current matrix.
     *
     * @return the multiplicative scale factor, or 0 if degenerate (the
     *         caller then skips applying it).
     */
    private float depthScaleFor(float dz) {
        float eye = Math.abs(mLocationZ);
        if (eye < 1f) {
            return 0f; // degenerate eye distance; no meaningful projection.
        }
        // New distance after moving toward (+) the eye by dz. Clamp so
        // content can never cross the eye plane and invert / explode.
        float newDist = eye - dz;
        if (newDist < 1f) {
            newDist = 1f;
        }
        // scale at newDist relative to scale at eye = (eye/newDist) / (eye/eye).
        return eye / newDist;
    }
}
