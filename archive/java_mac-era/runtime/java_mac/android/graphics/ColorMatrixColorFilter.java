// android.graphics.ColorMatrixColorFilter -- a ColorFilter that transforms each
// pixel through a 4x5 ColorMatrix (grayscale, saturation, tint, Compose
// ColorFilter.colorMatrix()). Holds a private 20-float copy exactly like AOSP so
// the matrix is decoupled from a caller-supplied ColorMatrix instance.
//
// Software path: our Canvas/Paint reads the live array via macapkArray() and
// applies the AOSP/Skia per-component math itself (no GPU shader). Mirrors
// frameworks/base/graphics/java/android/graphics/ColorMatrixColorFilter.java.
package android.graphics;

import java.util.Arrays;

public class ColorMatrixColorFilter extends ColorFilter {
    // 4x5 row-major, same shape as ColorMatrix.getArray(). Kept as our own copy
    // so a later mutation of the source ColorMatrix does not silently change this
    // filter (AOSP snapshots at construction / setColorMatrix time too).
    private final float[] mMatrix = new float[20];

    public ColorMatrixColorFilter(ColorMatrix matrix) {
        System.arraycopy(matrix.getArray(), 0, mMatrix, 0, 20);
    }

    public ColorMatrixColorFilter(float[] array) {
        if (array.length < 20) {
            throw new ArrayIndexOutOfBoundsException("array must have length of 20");
        }
        System.arraycopy(array, 0, mMatrix, 0, 20);
    }

    /** Copies this filter's matrix into the supplied ColorMatrix. */
    public void getColorMatrix(ColorMatrix colorMatrix) {
        colorMatrix.set(mMatrix);
    }

    /** API 29 -- replaces the matrix from a ColorMatrix instance. */
    public void setColorMatrix(ColorMatrix matrix) {
        if (matrix == null) {
            // AOSP resets to identity when given null.
            ColorMatrix identity = new ColorMatrix();
            System.arraycopy(identity.getArray(), 0, mMatrix, 0, 20);
        } else {
            System.arraycopy(matrix.getArray(), 0, mMatrix, 0, 20);
        }
    }

    /** API 29 -- replaces the matrix from a raw 20-float array. */
    public void setColorMatrixArray(float[] array) {
        if (array != null && array.length < 20) {
            throw new ArrayIndexOutOfBoundsException("array must have length of 20");
        }
        if (array == null) {
            ColorMatrix identity = new ColorMatrix();
            System.arraycopy(identity.getArray(), 0, mMatrix, 0, 20);
        } else {
            System.arraycopy(array, 0, mMatrix, 0, 20);
        }
    }

    /** Mac.apk accessor: the live 20-float array our software Canvas applies. */
    public float[] macapkArray() {
        return mMatrix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Arrays.equals(mMatrix, ((ColorMatrixColorFilter) o).mMatrix);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mMatrix);
    }
}
