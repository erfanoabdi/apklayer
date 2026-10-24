// android.graphics.Matrix -- 3x3 matrix, backed by a 9-element float array
// matching AOSP's MSCALE_X / MSKEW_X / ... constants.
//
// History: prior to v1.0.50 every method was a no-op or `return true`. Canvas
// needs real matrix state because Canvas.drawBitmap(Matrix) and
// Path.transform(Matrix) both rely on the matrix actually encoding the
// transform.
//
// ⚠ v1.0.923: THE MATH NO LONGER ROUTES THROUGH java.awt.geom.AffineTransform.
// It used to: invert / preConcat / postConcat / setConcat all converted to an
// AffineTransform, did the work there, and converted back. AffineTransform is
// AFFINE-ONLY -- it stores six numbers and cannot represent the bottom row --
// so every one of those methods silently DROPPED the perspective row, and
// mapPoints/mapVectors/mapRadius/rectStaysRect were written to match (six
// values used, nine stored). The answer-differential measured the cost: a
// perspective matrix mapped points as if it were affine (no divide by w), an
// inverse came back as the identity's, and a concat lost the row entirely.
// Everything below is now a direct port of SkMatrix, which is what a real
// device runs underneath android.graphics.Matrix's native methods.
//
// macapkToAffine() SURVIVES, and is deliberately lossy: Canvas / Shader /
// Bitmap / Path all draw through Java2D, which is affine-only, so they get the
// affine projection of this matrix. That is a rendering-backend limitation, no
// longer a limitation of what a Matrix can HOLD or ANSWER.
//
// Sources, quoted where a value is non-obvious:
//   frameworks/base/graphics/java/android/graphics/Matrix.java  (android-11.0.0_r48)
//   external/skia/src/core/SkMatrix.cpp                         (android-11.0.0_r48)
package android.graphics;

import java.awt.geom.AffineTransform;

public class Matrix {
    public static final int MSCALE_X = 0;
    public static final int MSKEW_X  = 1;
    public static final int MTRANS_X = 2;
    public static final int MSKEW_Y  = 3;
    public static final int MSCALE_Y = 4;
    public static final int MTRANS_Y = 5;
    public static final int MPERSP_0 = 6;
    public static final int MPERSP_1 = 7;
    public static final int MPERSP_2 = 8;

    // ---------------------------------------------------------------------
    // Skia's type mask (SkMatrix.h). Almost every bit is derivable from the
    // nine values, so it would be tempting to skip it -- but ONE bit is not:
    // kRectStaysRect. SkMatrix::setScale / setScaleTranslate / setTranslate OR
    // that bit in UNCONDITIONALLY (SkMatrix.cpp:268-326, and setScaleTranslate
    // in SkMatrix.h:1718 `this->setTypeMask(mask | kRectStaysRect_Mask)`),
    // WITHOUT checking that both scale components are non-zero, while
    // computeTypeMask() -- used by every matrix built through setValues,
    // setSkew, setSinCos or a concat -- requires both to be non-zero.
    // So setScale(0, 3).rectStaysRect() is TRUE and the same nine values
    // installed via setValues() answer FALSE. That is not a rounding artifact:
    // the answer-differential measured both (GEO.MAT.bank.scaleZeroX = 1,
    // GEO.MAT.bank.zero2x2 = 0). Storing the mask is the only way to be right
    // about both, so we store it exactly as Skia does.
    // ---------------------------------------------------------------------
    private static final int kTranslate_Mask     = 0x01;
    private static final int kScale_Mask         = 0x02;
    private static final int kAffine_Mask        = 0x04;
    private static final int kPerspective_Mask   = 0x08;
    private static final int kRectStaysRect_Mask = 0x10;
    /** Sentinel: "recompute from the values on next read". */
    private static final int kUnknown_Mask       = 0x80;
    private static final int kORableMasks =
            kTranslate_Mask | kScale_Mask | kAffine_Mask | kPerspective_Mask;

    private final float[] m = new float[9];
    private int mTypeMask;

    public Matrix() { reset(); }
    public Matrix(Matrix src) {
        // AOSP: nCreate(src ? src->native_instance : 0) -- a null src yields a
        // fresh identity, not a NullPointerException.
        if (src != null) {
            System.arraycopy(src.m, 0, m, 0, 9);
            mTypeMask = src.mTypeMask;
        } else {
            reset();
        }
    }

    // ---------------------------------------------------------------------
    // Type mask
    // ---------------------------------------------------------------------

    /** Port of SkMatrix::computeTypeMask (SkMatrix.cpp:111-184). Skia compares
     *  the float bits through SkScalarAs2sCompliment, which maps -0.0f to 0 --
     *  identical to a plain float `!= 0` in Java, so the direct comparisons
     *  below are the same predicate. */
    private int computeTypeMask() {
        if (m[6] != 0 || m[7] != 0 || m[8] != 1) {
            // "Once it is determined that that this is a perspective transform,
            // all other flags are moot as far as optimizations are concerned."
            // Note kRectStaysRect is NOT among kORableMasks, so a perspective
            // matrix never "stays rect".
            return kORableMasks;
        }
        int mask = 0;
        if (m[2] != 0 || m[5] != 0) mask |= kTranslate_Mask;

        final float m00 = m[0], m01 = m[1], m10 = m[3], m11 = m[4];
        if (m01 != 0 || m10 != 0) {
            // Skia is deliberately conservative here and always sets scale
            // alongside affine, "so that matrices have the same type masks as
            // their inverses".
            mask |= kAffine_Mask | kScale_Mask;
            // rectStaysRect in the affine case: primary diagonal all zero AND
            // secondary diagonal all non-zero (a multiple-of-90 rotation).
            if (m00 == 0 && m11 == 0 && m01 != 0 && m10 != 0) mask |= kRectStaysRect_Mask;
        } else {
            if (m00 != 1 || m11 != 1) mask |= kScale_Mask;
            // Secondary diagonal is already known zero, so only the primary
            // diagonal has to be all non-zero.
            if (m00 != 0 && m11 != 0) mask |= kRectStaysRect_Mask;
        }
        return mask;
    }

    private int typeMaskFull() {
        if ((mTypeMask & kUnknown_Mask) != 0) mTypeMask = computeTypeMask();
        return mTypeMask;
    }
    /** SkMatrix::getType -- only the four public bits. */
    private int getType() { return typeMaskFull() & 0xF; }
    private void setTypeMask(int mask) { mTypeMask = mask; }
    private void orTypeMask(int mask) { mTypeMask |= mask; }
    private void clearTypeMask(int mask) { mTypeMask &= ~mask; }
    /** SkMatrix::updateTranslateMask. */
    private void updateTranslateMask() {
        if (m[2] != 0 || m[5] != 0) mTypeMask |= kTranslate_Mask;
        else mTypeMask &= ~kTranslate_Mask;
    }
    private boolean hasPerspective() { return m[6] != 0 || m[7] != 0 || m[8] != 1; }
    /** SkMatrix::isScaleTranslate -- no affine and no perspective bits. */
    private boolean isScaleTranslate() {
        return (getType() & ~(kScale_Mask | kTranslate_Mask)) == 0;
    }

    public boolean isIdentity() { return getType() == 0; }
    /** SkMatrix::isAffine == !hasPerspective(). */
    public boolean isAffine() { return !hasPerspective(); }
    /** "Returns true if will map a rectangle to another rectangle. This can be
     *  true if the matrix is identity, scale-only, or rotates a multiple of 90
     *  degrees." (Matrix.java:274-279.) See the kRectStaysRect note above for
     *  why this reads a stored bit rather than recomputing from the values. */
    public boolean rectStaysRect() { return (typeMaskFull() & kRectStaysRect_Mask) != 0; }

    // ---------------------------------------------------------------------
    // Setters. Each mirrors the corresponding SkMatrix setter INCLUDING its
    // type-mask handling, because that is observable through rectStaysRect().
    // ---------------------------------------------------------------------

    public void reset() {
        m[0] = 1; m[1] = 0; m[2] = 0;
        m[3] = 0; m[4] = 1; m[5] = 0;
        m[6] = 0; m[7] = 0; m[8] = 1;
        setTypeMask(kRectStaysRect_Mask);   // kIdentity_Mask | kRectStaysRect_Mask
    }
    /** "(deep) copy the src matrix into this matrix. If src is null, reset this
     *  matrix to the identity matrix." (Matrix.java:281-291.) */
    public void set(Matrix src) {
        if (src == null) { reset(); return; }
        System.arraycopy(src.m, 0, m, 0, 9);
        mTypeMask = src.mTypeMask;
    }

    public void setTranslate(float dx, float dy) {
        m[0] = 1; m[1] = 0; m[2] = dx;
        m[3] = 0; m[4] = 1; m[5] = dy;
        m[6] = 0; m[7] = 0; m[8] = 1;
        setTypeMask(((dx != 0 || dy != 0) ? kTranslate_Mask : 0) | kRectStaysRect_Mask);
    }
    public void setScale(float sx, float sy) {
        m[0] = sx; m[1] = 0;  m[2] = 0;
        m[3] = 0;  m[4] = sy; m[5] = 0;
        m[6] = 0;  m[7] = 0;  m[8] = 1;
        setTypeMask(((sx == 1 && sy == 1) ? 0 : kScale_Mask) | kRectStaysRect_Mask);
    }
    public void setScale(float sx, float sy, float px, float py) {
        if (sx == 1 && sy == 1) reset();
        else setScaleTranslate(sx, sy, px - sx * px, py - sy * py);
    }
    /** SkMatrix::setScaleTranslate (SkMatrix.h:1700-1719). */
    private void setScaleTranslate(float sx, float sy, float tx, float ty) {
        m[0] = sx; m[1] = 0;  m[2] = tx;
        m[3] = 0;  m[4] = sy; m[5] = ty;
        m[6] = 0;  m[7] = 0;  m[8] = 1;
        int mask = 0;
        if (sx != 1 || sy != 1) mask |= kScale_Mask;
        if (tx != 0 || ty != 0) mask |= kTranslate_Mask;
        setTypeMask(mask | kRectStaysRect_Mask);
    }

    /** SK_ScalarNearlyZero == SK_Scalar1 / (1 << 12). SkMatrix::setRotate feeds
     *  setSinCos through SkScalarSinSnapToZero / SkScalarCosSnapToZero, so
     *  setRotate(90) stores an EXACT zero cosine where a plain (float)cos would
     *  store -4.37e-8. That is why setRotate(90).rectStaysRect() is true on a
     *  device (the affine branch of computeTypeMask needs the primary diagonal
     *  to be exactly zero) and was false here. setSinCos does NOT snap: the
     *  caller supplied those values. */
    private static final float SK_ScalarNearlyZero = 1f / (1 << 12);
    private static float snapToZero(double v) {
        float f = (float) v;
        return Math.abs(f) <= SK_ScalarNearlyZero ? 0f : f;
    }
    public void setRotate(float degrees) {
        double r = Math.toRadians(degrees);
        setSinCos(snapToZero(Math.sin(r)), snapToZero(Math.cos(r)));
    }
    public void setRotate(float degrees, float px, float py) {
        double r = Math.toRadians(degrees);
        setSinCos(snapToZero(Math.sin(r)), snapToZero(Math.cos(r)), px, py);
    }
    public void setSinCos(float sin, float cos) {
        m[0] = cos; m[1] = -sin; m[2] = 0;
        m[3] = sin; m[4] = cos;  m[5] = 0;
        m[6] = 0;   m[7] = 0;    m[8] = 1;
        setTypeMask(kUnknown_Mask);
    }
    public void setSinCos(float sin, float cos, float px, float py) {
        final float oneMinusCos = 1 - cos;
        m[0] = cos; m[1] = -sin; m[2] = sin * py + oneMinusCos * px;
        m[3] = sin; m[4] = cos;  m[5] = -sin * px + oneMinusCos * py;
        m[6] = 0;   m[7] = 0;    m[8] = 1;
        setTypeMask(kUnknown_Mask);
    }
    public void setSkew(float kx, float ky) {
        m[0] = 1;  m[1] = kx; m[2] = 0;
        m[3] = ky; m[4] = 1;  m[5] = 0;
        m[6] = 0;  m[7] = 0;  m[8] = 1;
        setTypeMask(kUnknown_Mask);
    }
    public void setSkew(float kx, float ky, float px, float py) {
        m[0] = 1;  m[1] = kx; m[2] = -kx * py;
        m[3] = ky; m[4] = 1;  m[5] = -ky * px;
        m[6] = 0;  m[7] = 0;  m[8] = 1;
        setTypeMask(kUnknown_Mask);
    }

    /** SkMatrix::setRectToRect (SkMatrix.cpp:547-598). Two edges that a
     *  width/height-based implementation gets wrong: an EMPTY src resets to
     *  identity and returns FALSE (it does not leave the matrix alone), and an
     *  EMPTY dst zeroes the first eight values -- 2x2 AND translation -- and
     *  returns TRUE. AOSP throws NPE for a null src or dst in Java
     *  (Matrix.java:570-574) and dereferences stf.nativeInt. */
    public boolean setRectToRect(RectF src, RectF dst, ScaleToFit stf) {
        if (dst == null || src == null) throw new NullPointerException();
        final int align = stf.nativeInt;
        if (src.left >= src.right || src.top >= src.bottom) {   // SkRect::isEmpty
            reset();
            return false;
        }
        if (dst.left >= dst.right || dst.top >= dst.bottom) {
            m[0] = 0; m[1] = 0; m[2] = 0;
            m[3] = 0; m[4] = 0; m[5] = 0;
            m[6] = 0; m[7] = 0; m[8] = 1;
            setTypeMask(kScale_Mask | kRectStaysRect_Mask);
            return true;
        }
        float sx = dst.width() / src.width();
        float sy = dst.height() / src.height();
        boolean xLarger = false;
        if (align != ScaleToFit.FILL.nativeInt) {
            if (sx > sy) { xLarger = true; sx = sy; } else { sy = sx; }
        }
        float tx = dst.left - src.left * sx;
        float ty = dst.top - src.top * sy;
        if (align == ScaleToFit.CENTER.nativeInt || align == ScaleToFit.END.nativeInt) {
            float diff = xLarger ? dst.width() - src.width() * sy
                                 : dst.height() - src.height() * sy;
            if (align == ScaleToFit.CENTER.nativeInt) diff = diff * 0.5f;
            if (xLarger) tx += diff; else ty += diff;
        }
        setScaleTranslate(sx, sy, tx, ty);
        return true;
    }

    // ---------------------------------------------------------------------
    // Concatenation
    // ---------------------------------------------------------------------

    /** float a*b + c*d evaluated in DOUBLE then narrowed, exactly as Skia's
     *  muladdmul (SkMatrix.cpp:600-602). Skia keeps the extra precision here
     *  because concat and invert are where it matters most. */
    private static float muladdmul(float a, float b, float c, float d) {
        return (float) ((double) a * b + (double) c * d);
    }
    /** SkMatrix::rowcol3 -- FLOAT arithmetic, unlike muladdmul above. */
    private static float rowcol3(float[] a, int row, float[] b, int col) {
        return a[row] * b[col] + a[row + 1] * b[col + 3] + a[row + 2] * b[col + 6];
    }
    private static boolean onlyScaleAndTranslate(int mask) {
        return (mask & (kAffine_Mask | kPerspective_Mask)) == 0;
    }

    /** SkMatrix::setConcat (SkMatrix.cpp:612-681). The perspective branch is
     *  the one AffineTransform could not express. */
    public boolean setConcat(Matrix a, Matrix b) {
        final int aType = a.getType();
        final int bType = b.getType();
        if (a.isIdentity()) {
            set(b);
        } else if (b.isIdentity()) {
            set(a);
        } else if (onlyScaleAndTranslate(aType | bType)) {
            // Careful: a or b may alias this, so read everything first.
            float sx = a.m[0] * b.m[0];
            float sy = a.m[4] * b.m[4];
            float tx = a.m[0] * b.m[2] + a.m[2];
            float ty = a.m[4] * b.m[5] + a.m[5];
            setScaleTranslate(sx, sy, tx, ty);
        } else {
            final float[] t = new float[9];
            if (((aType | bType) & kPerspective_Mask) != 0) {
                t[0] = rowcol3(a.m, 0, b.m, 0);
                t[1] = rowcol3(a.m, 0, b.m, 1);
                t[2] = rowcol3(a.m, 0, b.m, 2);
                t[3] = rowcol3(a.m, 3, b.m, 0);
                t[4] = rowcol3(a.m, 3, b.m, 1);
                t[5] = rowcol3(a.m, 3, b.m, 2);
                t[6] = rowcol3(a.m, 6, b.m, 0);
                t[7] = rowcol3(a.m, 6, b.m, 1);
                t[8] = rowcol3(a.m, 6, b.m, 2);
            } else {
                t[0] = muladdmul(a.m[0], b.m[0], a.m[1], b.m[3]);
                t[1] = muladdmul(a.m[0], b.m[1], a.m[1], b.m[4]);
                t[2] = muladdmul(a.m[0], b.m[2], a.m[1], b.m[5]) + a.m[2];
                t[3] = muladdmul(a.m[3], b.m[0], a.m[4], b.m[3]);
                t[4] = muladdmul(a.m[3], b.m[1], a.m[4], b.m[4]);
                t[5] = muladdmul(a.m[3], b.m[2], a.m[4], b.m[5]) + a.m[5];
                t[6] = 0; t[7] = 0; t[8] = 1;
            }
            System.arraycopy(t, 0, m, 0, 9);
            setTypeMask(kUnknown_Mask);
        }
        return true;
    }

    public boolean preConcat(Matrix other) {
        if (!other.isIdentity()) setConcat(this, other);
        return true;
    }
    public boolean postConcat(Matrix other) {
        if (!other.isIdentity()) setConcat(other, this);
        return true;
    }

    /** SkMatrix::preTranslate (SkMatrix.cpp:277-293). */
    public boolean preTranslate(float dx, float dy) {
        final int mask = getType();
        if (mask <= kTranslate_Mask) {
            m[2] += dx; m[5] += dy;
        } else if ((mask & kPerspective_Mask) != 0) {
            Matrix t = new Matrix(); t.setTranslate(dx, dy);
            return preConcat(t);
        } else {
            m[2] += m[0] * dx + m[1] * dy;
            m[5] += m[3] * dx + m[4] * dy;
        }
        updateTranslateMask();
        return true;
    }
    /** SkMatrix::postTranslate (SkMatrix.cpp:295-308). */
    public boolean postTranslate(float dx, float dy) {
        if (hasPerspective()) {
            Matrix t = new Matrix(); t.setTranslate(dx, dy);
            postConcat(t);
        } else {
            m[2] += dx; m[5] += dy;
            updateTranslateMask();
        }
        return true;
    }
    /** SkMatrix::preScale (SkMatrix.cpp:338-367) -- the "blind multiplies",
     *  which include the PERSPECTIVE row. Dropping m[6]/m[7] here silently
     *  turned a pre-scaled perspective matrix into a different projection. */
    public boolean preScale(float sx, float sy) {
        if (sx == 1 && sy == 1) return true;
        m[0] *= sx; m[3] *= sx; m[6] *= sx;
        m[1] *= sy; m[4] *= sy; m[7] *= sy;
        if (m[0] == 1 && m[4] == 1
                && (mTypeMask & (kPerspective_Mask | kAffine_Mask)) == 0) {
            clearTypeMask(kScale_Mask);
        } else {
            orTypeMask(kScale_Mask);
        }
        return true;
    }
    public boolean preScale(float sx, float sy, float px, float py) {
        if (sx == 1 && sy == 1) return true;
        Matrix t = new Matrix(); t.setScale(sx, sy, px, py);
        return preConcat(t);
    }
    public boolean postScale(float sx, float sy) {
        if (sx == 1 && sy == 1) return true;
        Matrix t = new Matrix(); t.setScale(sx, sy);
        return postConcat(t);
    }
    public boolean postScale(float sx, float sy, float px, float py) {
        if (sx == 1 && sy == 1) return true;
        Matrix t = new Matrix(); t.setScale(sx, sy, px, py);
        return postConcat(t);
    }
    public boolean preRotate(float degrees) {
        Matrix t = new Matrix(); t.setRotate(degrees);
        return preConcat(t);
    }
    public boolean preRotate(float degrees, float px, float py) {
        Matrix t = new Matrix(); t.setRotate(degrees, px, py);
        return preConcat(t);
    }
    public boolean postRotate(float degrees) {
        Matrix t = new Matrix(); t.setRotate(degrees);
        return postConcat(t);
    }
    public boolean postRotate(float degrees, float px, float py) {
        Matrix t = new Matrix(); t.setRotate(degrees, px, py);
        return postConcat(t);
    }
    public boolean preSkew(float kx, float ky) {
        Matrix t = new Matrix(); t.setSkew(kx, ky);
        return preConcat(t);
    }
    public boolean preSkew(float kx, float ky, float px, float py) {
        Matrix t = new Matrix(); t.setSkew(kx, ky, px, py);
        return preConcat(t);
    }
    public boolean postSkew(float kx, float ky) {
        Matrix t = new Matrix(); t.setSkew(kx, ky);
        return postConcat(t);
    }
    public boolean postSkew(float kx, float ky, float px, float py) {
        Matrix t = new Matrix(); t.setSkew(kx, ky, px, py);
        return postConcat(t);
    }

    // ---------------------------------------------------------------------
    // Inversion
    // ---------------------------------------------------------------------

    private static double dcross(double a, double b, double c, double d) { return a * b - c * d; }

    /** SkMatrix::sk_inv_determinant (SkMatrix.cpp:723-751). The near-zero test
     *  compares against the CUBE of SK_ScalarNearlyZero because a 3x3
     *  determinant is on the order of the cube of the elements. */
    private static double invDeterminant(float[] mat, boolean isPersp) {
        double det;
        if (isPersp) {
            det = mat[0] * dcross(mat[4], mat[8], mat[5], mat[7])
                + mat[1] * dcross(mat[5], mat[6], mat[3], mat[8])
                + mat[2] * dcross(mat[3], mat[7], mat[4], mat[6]);
        } else {
            det = dcross(mat[0], mat[4], mat[1], mat[3]);
        }
        final float tol = SK_ScalarNearlyZero * SK_ScalarNearlyZero * SK_ScalarNearlyZero;
        if (Math.abs((float) det) <= tol) return 0;
        return 1.0 / det;
    }
    private static float scrossScale(float a, float b, float c, float d, double scale) {
        return (float) ((a * b - c * d) * scale);
    }
    private static float dcrossScale(double a, double b, double c, double d, double scale) {
        return (float) (dcross(a, b, c, d) * scale);
    }
    /** SkMatrix::ComputeInv (SkMatrix.cpp:789-818). */
    private static void computeInv(float[] dst, float[] src, double invDet, boolean isPersp) {
        if (isPersp) {
            dst[0] = scrossScale(src[4], src[8], src[5], src[7], invDet);
            dst[1] = scrossScale(src[2], src[7], src[1], src[8], invDet);
            dst[2] = scrossScale(src[1], src[5], src[2], src[4], invDet);
            dst[3] = scrossScale(src[5], src[6], src[3], src[8], invDet);
            dst[4] = scrossScale(src[0], src[8], src[2], src[6], invDet);
            dst[5] = scrossScale(src[2], src[3], src[0], src[5], invDet);
            dst[6] = scrossScale(src[3], src[7], src[4], src[6], invDet);
            dst[7] = scrossScale(src[1], src[6], src[0], src[7], invDet);
            dst[8] = scrossScale(src[0], src[4], src[1], src[3], invDet);
        } else {
            dst[0] = (float) (src[4] * invDet);
            dst[1] = (float) (-src[1] * invDet);
            dst[2] = dcrossScale(src[1], src[5], src[4], src[2], invDet);
            dst[3] = (float) (-src[3] * invDet);
            dst[4] = (float) (src[0] * invDet);
            dst[5] = dcrossScale(src[3], src[2], src[0], src[5], invDet);
            dst[6] = 0; dst[7] = 0; dst[8] = 1;
        }
    }

    /** "If this matrix can be inverted, return true and if inverse is not null,
     *  set inverse to be the inverse of this matrix." (Matrix.java:613-619.)
     *  AOSP dereferences inverse.native_instance, so a null argument is an NPE
     *  from the JVM before native runs -- not a quiet `return false`. */
    public boolean invert(Matrix inverse) {
        if (inverse == null) throw new NullPointerException();
        if (isIdentity()) { inverse.reset(); return true; }

        final int mask = getType();
        if ((mask & ~(kScale_Mask | kTranslate_Mask)) == 0) {
            if ((mask & kScale_Mask) != 0) {
                if (m[0] == 0 || m[4] == 0) return false;
                float invX = 1f / m[0];
                float invY = 1f / m[4];
                float tx = -m[2] * invX;
                float ty = -m[5] * invY;
                inverse.m[1] = inverse.m[3] = inverse.m[6] = inverse.m[7] = 0;
                inverse.m[0] = invX; inverse.m[4] = invY; inverse.m[8] = 1;
                inverse.m[2] = tx;   inverse.m[5] = ty;
                inverse.setTypeMask(mask | kRectStaysRect_Mask);
            } else {
                inverse.setTranslate(-m[2], -m[5]);
            }
            return true;
        }

        final boolean isPersp = (mask & kPerspective_Mask) != 0;
        final double invDet = invDeterminant(m, isPersp);
        if (invDet == 0) return false;      // underflow

        final float[] tmp = new float[9];
        computeInv(tmp, m, invDet, isPersp);
        for (int i = 0; i < 9; i++) {
            if (Float.isNaN(tmp[i]) || Float.isInfinite(tmp[i])) return false;
        }
        System.arraycopy(tmp, 0, inverse.m, 0, 9);
        inverse.setTypeMask(mTypeMask);
        return true;
    }

    // ---------------------------------------------------------------------
    // Values
    // ---------------------------------------------------------------------

    public void getValues(float[] values) {
        if (values.length < 9) throw new ArrayIndexOutOfBoundsException();
        System.arraycopy(m, 0, values, 0, 9);
    }
    public void setValues(float[] values) {
        if (values.length < 9) throw new ArrayIndexOutOfBoundsException();
        System.arraycopy(values, 0, m, 0, 9);
        setTypeMask(kUnknown_Mask);
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    /** Matrix.checkPointArrays (Matrix.java:577-588), verbatim. */
    private static void checkPointArrays(float[] src, int srcIndex,
                                         float[] dst, int dstIndex, int pointCount) {
        int srcStop = srcIndex + (pointCount << 1);
        int dstStop = dstIndex + (pointCount << 1);
        if ((pointCount | srcIndex | dstIndex | srcStop | dstStop) < 0
                || srcStop > src.length || dstStop > dst.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    public void mapPoints(float[] dst, int dstIndex, float[] src, int srcIndex, int pointCount) {
        checkPointArrays(src, srcIndex, dst, dstIndex, pointCount);
        mapPtsUnchecked(dst, dstIndex, src, srcIndex, pointCount, true);
    }
    public void mapPoints(float[] dst, float[] src) {
        if (dst.length != src.length) throw new ArrayIndexOutOfBoundsException();
        mapPoints(dst, 0, src, 0, dst.length >> 1);
    }
    public void mapPoints(float[] pts) { mapPoints(pts, 0, pts, 0, pts.length >> 1); }

    public void mapVectors(float[] dst, int dstIndex, float[] src, int srcIndex, int vectorCount) {
        checkPointArrays(src, srcIndex, dst, dstIndex, vectorCount);
        mapPtsUnchecked(dst, dstIndex, src, srcIndex, vectorCount, false);
    }
    public void mapVectors(float[] dst, float[] src) {
        if (dst.length != src.length) throw new ArrayIndexOutOfBoundsException();
        mapVectors(dst, 0, src, 0, dst.length >> 1);
    }
    public void mapVectors(float[] vecs) { mapVectors(vecs, 0, vecs, 0, vecs.length >> 1); }

    /** SkMatrix::Persp_pts (SkMatrix.cpp:961-987). Note the `if (z) z = 1/z`:
     *  a point that lands exactly on the vanishing plane maps to (0,0) rather
     *  than to an infinity. */
    private void mapOne(float x, float y, float[] out) {
        float ox = m[0] * x + m[1] * y + m[2];
        float oy = m[3] * x + m[4] * y + m[5];
        if (hasPerspective()) {
            float z = m[6] * x + m[7] * y + m[8];
            if (z != 0) z = 1f / z;
            ox *= z; oy *= z;
        }
        out[0] = ox; out[1] = oy;
    }

    /** asPoints=false is mapVectors, which "does not apply the translation
     *  associated with the matrix" (Matrix.java:640-644). For a PERSPECTIVE
     *  matrix that does not mean "drop m[2]/m[5]": SkMatrix::mapVectors
     *  (SkMatrix.cpp:1103-1123) maps the point and SUBTRACTS the mapped origin,
     *  because the perspective divide is not linear. */
    private void mapPtsUnchecked(float[] dst, int dstIndex, float[] src, int srcIndex,
                                 int count, boolean asPoints) {
        final float[] t = new float[2];
        float originX = 0f, originY = 0f;
        boolean subtractOrigin = false;
        if (!asPoints) {
            if (hasPerspective()) {
                mapOne(0f, 0f, t);
                originX = t[0]; originY = t[1];
                subtractOrigin = true;
            } else {
                // Affine: clear the translation and map as points.
                for (int i = 0; i < count; i++) {
                    float x = src[srcIndex + i * 2], y = src[srcIndex + i * 2 + 1];
                    dst[dstIndex + i * 2]     = m[0] * x + m[1] * y;
                    dst[dstIndex + i * 2 + 1] = m[3] * x + m[4] * y;
                }
                return;
            }
        }
        for (int i = 0; i < count; i++) {
            float x = src[srcIndex + i * 2], y = src[srcIndex + i * 2 + 1];
            mapOne(x, y, t);
            dst[dstIndex + i * 2]     = subtractOrigin ? t[0] - originX : t[0];
            dst[dstIndex + i * 2 + 1] = subtractOrigin ? t[1] - originY : t[1];
        }
    }

    public boolean mapRect(RectF dst) { return mapRect(dst, dst); }
    /** "@return the result of calling rectStaysRect()" (Matrix.java:712-726) --
     *  but SkMatrix::mapRect (SkMatrix.cpp:1147-1174) returns TRUE early for a
     *  translate-only or scale-translate matrix WITHOUT consulting the bit, so
     *  setValues({0,0,5, 0,0,7, 0,0,1}) answers rectStaysRect()=false and
     *  mapRect()=true. Callers use this to decide whether an axis-aligned fast
     *  path is legal, so answering a blanket `true` (as we did) told every
     *  caller that a 45-degree rotation preserved axis alignment. */
    public boolean mapRect(RectF dst, RectF src) {
        if (dst == null || src == null) throw new NullPointerException();
        final int type = getType();
        if (type <= kTranslate_Mask) {
            float tx = m[2], ty = m[5];
            sortAsRect(dst, src.left + tx, src.top + ty, src.right + tx, src.bottom + ty);
            return true;
        }
        if (isScaleTranslate()) {
            float sx = m[0], sy = m[4], tx = m[2], ty = m[5];
            sortAsRect(dst, src.left * sx + tx, src.top * sy + ty,
                            src.right * sx + tx, src.bottom * sy + ty);
            return true;
        }
        float[] quad = { src.left, src.top, src.right, src.top,
                         src.right, src.bottom, src.left, src.bottom };
        mapPtsUnchecked(quad, 0, quad, 0, 4, true);
        float minX = quad[0], maxX = quad[0], minY = quad[1], maxY = quad[1];
        for (int i = 2; i < 8; i += 2) {
            if (quad[i]     < minX) minX = quad[i];     else if (quad[i]     > maxX) maxX = quad[i];
            if (quad[i + 1] < minY) minY = quad[i + 1]; else if (quad[i + 1] > maxY) maxY = quad[i + 1];
        }
        dst.set(minX, minY, maxX, maxY);
        return rectStaysRect();   // "might still return true if rotated by 90, etc."
    }
    /** SkMatrix::sort_as_rect -- a negative scale flips left/right, and the
     *  result must still be a sorted rect. */
    private static void sortAsRect(RectF dst, float l, float t, float r, float b) {
        dst.set(Math.min(l, r), Math.min(t, b), Math.max(l, r), Math.max(t, b));
    }

    /** "Return the mean radius of a circle after it has been mapped by this
     *  matrix." (Matrix.java:740-746.) SkMatrix::mapRadius (SkMatrix.cpp:
     *  1176-1189) is the GEOMETRIC MEAN of the two mapped unit-axis LENGTHS.
     *  radius*sqrt(|det|) agrees for pure scale and pure rotation and is wrong
     *  for every skew and every perspective, which is exactly where a shadow or
     *  a blur radius comes from. */
    public float mapRadius(float radius) {
        float[] vec = { radius, 0f, 0f, radius };
        mapVectors(vec);
        float d0 = (float) Math.sqrt(vec[0] * vec[0] + vec[1] * vec[1]);
        float d1 = (float) Math.sqrt(vec[2] * vec[2] + vec[3] * vec[3]);
        return (float) Math.sqrt(d0 * d1);
    }

    // ---------------------------------------------------------------------
    // setPolyToPoly -- "Adapted from Rob Johnson's original sample code in
    // QuickDraw GX" (SkMatrix.cpp:1309-1437). Used to `return pointCount == 0`.
    // ---------------------------------------------------------------------

    private static boolean checkForZero(float x) { return x * x == 0; }

    private static void poly2(float[] p, int i, Matrix dst) {
        // NOTE: kMScaleX and kMScaleY are BOTH srcPt[1].y - srcPt[0].y in
        // Skia. It looks like a typo and is not -- the 2-point solve is a
        // similarity (rotate+uniform-scale+translate), and this is the form
        // Skia has shipped since 2006. Reproducing it is the point.
        dst.m[0] = p[i + 3] - p[i + 1];
        dst.m[3] = p[i + 0] - p[i + 2];
        dst.m[6] = 0;
        dst.m[1] = p[i + 2] - p[i + 0];
        dst.m[4] = p[i + 3] - p[i + 1];
        dst.m[7] = 0;
        dst.m[2] = p[i + 0];
        dst.m[5] = p[i + 1];
        dst.m[8] = 1;
        dst.setTypeMask(kUnknown_Mask);
    }
    private static void poly3(float[] p, int i, Matrix dst) {
        dst.m[0] = p[i + 4] - p[i + 0];
        dst.m[3] = p[i + 5] - p[i + 1];
        dst.m[6] = 0;
        dst.m[1] = p[i + 2] - p[i + 0];
        dst.m[4] = p[i + 3] - p[i + 1];
        dst.m[7] = 0;
        dst.m[2] = p[i + 0];
        dst.m[5] = p[i + 1];
        dst.m[8] = 1;
        dst.setTypeMask(kUnknown_Mask);
    }
    private static boolean poly4(float[] p, int i, Matrix dst) {
        final float p0x = p[i], p0y = p[i + 1];
        final float p1x = p[i + 2], p1y = p[i + 3];
        final float p2x = p[i + 4], p2y = p[i + 5];
        final float p3x = p[i + 6], p3y = p[i + 7];
        final float a1, a2;
        float x0 = p2x - p0x, y0 = p2y - p0y;
        float x1 = p2x - p1x, y1 = p2y - p1y;
        float x2 = p2x - p3x, y2 = p2y - p3y;

        /* check if abs(x2) > abs(y2) */
        if (x2 > 0 ? (y2 > 0 ? x2 > y2 : x2 > -y2) : (y2 > 0 ? -x2 > y2 : x2 < y2)) {
            float denom = (x1 * y2) / x2 - y1;
            if (checkForZero(denom)) return false;
            a1 = (((x0 - x1) * y2 / x2) - y0 + y1) / denom;
        } else {
            float denom = x1 - (y1 * x2) / y2;
            if (checkForZero(denom)) return false;
            a1 = (x0 - x1 - ((y0 - y1) * x2) / y2) / denom;
        }
        /* check if abs(x1) > abs(y1) */
        if (x1 > 0 ? (y1 > 0 ? x1 > y1 : x1 > -y1) : (y1 > 0 ? -x1 > y1 : x1 < y1)) {
            float denom = y2 - (x2 * y1) / x1;
            if (checkForZero(denom)) return false;
            a2 = (y0 - y2 - ((x0 - x2) * y1) / x1) / denom;
        } else {
            float denom = (y2 * x1) / y1 - x2;
            if (checkForZero(denom)) return false;
            a2 = (((y0 - y2) * x1) / y1 - x0 + x2) / denom;
        }
        dst.m[0] = a2 * p3x + p3x - p0x;
        dst.m[3] = a2 * p3y + p3y - p0y;
        dst.m[6] = a2;
        dst.m[1] = a1 * p1x + p1x - p0x;
        dst.m[4] = a1 * p1y + p1y - p0y;
        dst.m[7] = a1;
        dst.m[2] = p0x;
        dst.m[5] = p0y;
        dst.m[8] = 1;
        dst.setTypeMask(kUnknown_Mask);
        return true;
    }
    private static boolean polyProc(float[] p, int i, int count, Matrix dst) {
        if (count == 2) { poly2(p, i, dst); return true; }
        if (count == 3) { poly3(p, i, dst); return true; }
        return poly4(p, i, dst);
    }

    public boolean setPolyToPoly(float[] src, int srcIndex,
                                 float[] dst, int dstIndex, int pointCount) {
        if (pointCount > 4) throw new IllegalArgumentException();
        checkPointArrays(src, srcIndex, dst, dstIndex, pointCount);
        if (pointCount == 0) { reset(); return true; }
        if (pointCount == 1) {
            setTranslate(dst[dstIndex] - src[srcIndex], dst[dstIndex + 1] - src[srcIndex + 1]);
            return true;
        }
        Matrix tempMap = new Matrix(), result = new Matrix();
        if (!polyProc(src, srcIndex, pointCount, tempMap)) return false;
        if (!tempMap.invert(result)) return false;
        if (!polyProc(dst, dstIndex, pointCount, tempMap)) return false;
        setConcat(tempMap, result);
        return true;
    }

    // ---------------------------------------------------------------------
    // Value semantics
    // ---------------------------------------------------------------------

    /** AOSP deliberately has NO identity fast path here: the commented-out line
     *  in Matrix.java:298 reads `// if (obj == this) return true; -- NaN value
     *  would mean matrix != itself`, and the native comparison is float `==`.
     *  Consequences that java.util.Arrays.equals(float[]) gets exactly
     *  backwards, because it compares floatToIntBits: a matrix holding NaN is
     *  NOT equal to itself, two NaN-holding matrices are NOT equal, and +0.0 IS
     *  equal to -0.0. */
    @Override public boolean equals(Object obj) {
        if (!(obj instanceof Matrix)) return false;
        final float[] o = ((Matrix) obj).m;
        return m[0] == o[0] && m[1] == o[1] && m[2] == o[2]
            && m[3] == o[3] && m[4] == o[4] && m[5] == o[5]
            && m[6] == o[6] && m[7] == o[7] && m[8] == o[8];
    }
    /** AOSP Matrix.hashCode (Matrix.java:305-312) literally returns 44, with a
     *  comment saying it takes "the easy way out". Keep it: Arrays.hashCode
     *  would violate the equals/hashCode contract now that equals() treats
     *  +0.0 and -0.0 as equal. */
    @Override public int hashCode() { return 44; }

    public String toShortString() {
        return "[" + m[0] + ", " + m[1] + ", " + m[2] + "][" + m[3] + ", " + m[4]
            + ", " + m[5] + "][" + m[6] + ", " + m[7] + ", " + m[8] + "]";
    }
    @Override public String toString() { return "Matrix{" + toShortString() + "}"; }

    /** AOSP Matrix.dump(PrintWriter) (Matrix.java:819). Emits exactly the
     *  toShortString() text via print() calls -- no newline, no flush. Declared
     *  `final` in AOSP, so mirror that. */
    public final void dump(java.io.PrintWriter pw) {
        float[] values = new float[9];
        getValues(values);
        pw.print('[');
        pw.print(values[0]); pw.print(", "); pw.print(values[1]); pw.print(", ");
        pw.print(values[2]); pw.print("][");
        pw.print(values[3]); pw.print(", "); pw.print(values[4]); pw.print(", ");
        pw.print(values[5]); pw.print("][");
        pw.print(values[6]); pw.print(", "); pw.print(values[7]); pw.print(", ");
        pw.print(values[8]);
        pw.print(']');
    }

    /** AOSP Matrix.IDENTITY_MATRIX -- referenced by androidx/Compose. Treated as
     *  read-only by callers (a NoSuchFieldError here is equally fatal to a draw
     *  pass as a missing method). */
    public static final Matrix IDENTITY_MATRIX = new Matrix();

    /** AOSP carries the native ScaleToFit values on the enum and dereferences
     *  stf.nativeInt, which is what makes setRectToRect(.., null) an NPE. */
    public enum ScaleToFit {
        FILL(0), START(1), CENTER(2), END(3);
        final int nativeInt;
        ScaleToFit(int v) { this.nativeInt = v; }
    }

    // ---------------------------------------------------------------------
    // Mac.apk internal hooks (NOT AOSP surface)
    // ---------------------------------------------------------------------

    /** Internal: the AFFINE PROJECTION of this matrix, for the Java2D-backed
     *  draw path (Canvas / Shader / Bitmap / Path.transform). AffineTransform
     *  has no bottom row, so a perspective matrix is flattened here -- the
     *  perspective is preserved in the Matrix itself and lost only at the point
     *  where it is handed to a backend that cannot express it. */
    public AffineTransform macapkToAffine() {
        return new AffineTransform(m[0], m[3], m[1], m[4], m[2], m[5]);
    }
    public void macapkFromAffine(AffineTransform t) {
        m[0] = (float) t.getScaleX();  m[4] = (float) t.getScaleY();
        m[1] = (float) t.getShearX();  m[3] = (float) t.getShearY();
        m[2] = (float) t.getTranslateX(); m[5] = (float) t.getTranslateY();
        m[6] = 0; m[7] = 0; m[8] = 1;
        setTypeMask(kUnknown_Mask);
    }
    /** Internal: multiply two matrices, result = a * b. */
    void macapkMultiply(Matrix a, Matrix b) { setConcat(a, b); }
}
