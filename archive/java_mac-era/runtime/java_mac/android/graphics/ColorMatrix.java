// android.graphics.ColorMatrix -- a 4x5 color transform matrix (the math
// behind ColorMatrixColorFilter / Compose ColorFilter.colorMatrix()). Stored
// as a 20-float row-major array exactly like AOSP so getArray() round-trips and
// our Canvas can apply it per-pixel (image filters) or to a single paint color
// (shape/text fills).
//
// Was MISSING entirely -- an app constructing `new ColorMatrix()` (Coil image
// filters, grayscale, Compose saturation) NoClassDefFoundError'd, aborting the
// composition. Mirrors frameworks/base/graphics/java/android/graphics/ColorMatrix.java.
package android.graphics;

public class ColorMatrix {
    private final float[] mArray = new float[20];

    public ColorMatrix() { reset(); }
    public ColorMatrix(float[] src) { System.arraycopy(src, 0, mArray, 0, 20); }
    public ColorMatrix(ColorMatrix src) {
        System.arraycopy(src.mArray, 0, mArray, 0, 20);
    }

    public final float[] getArray() { return mArray; }

    public void reset() {
        java.util.Arrays.fill(mArray, 0f);
        mArray[0] = mArray[6] = mArray[12] = mArray[18] = 1f;
    }
    public void set(ColorMatrix src) { System.arraycopy(src.mArray, 0, mArray, 0, 20); }
    public void set(float[] src) { System.arraycopy(src, 0, mArray, 0, 20); }

    public void setScale(float rScale, float gScale, float bScale, float aScale) {
        java.util.Arrays.fill(mArray, 0f);
        mArray[0]  = rScale;
        mArray[6]  = gScale;
        mArray[12] = bScale;
        mArray[18] = aScale;
    }

    /** AOSP setSaturation: sat=0 fully desaturates (grayscale), 1 is identity.
     *  Luminance weights are the AOSP/Skia constants. */
    public void setSaturation(float sat) {
        java.util.Arrays.fill(mArray, 0f);
        float[] m = mArray;
        final float invSat = 1 - sat;
        final float R = 0.213f * invSat;
        final float G = 0.715f * invSat;
        final float B = 0.072f * invSat;
        m[0]  = R + sat; m[1]  = G;       m[2]  = B;
        m[5]  = R;       m[6]  = G + sat; m[7]  = B;
        m[10] = R;       m[11] = G;       m[12] = B + sat;
        m[18] = 1;
    }

    /** AOSP ColorMatrix.setRGB2YUV() -- coefficients match libjpeg's, verbatim
     *  from frameworks/base graphics/java/android/graphics/ColorMatrix.java.
     *  Note reset() first, so the alpha row (m[18]=1) survives. */
    public void setRGB2YUV() {
        reset();
        float[] m = mArray;
        m[0]  = 0.299f;    m[1]  = 0.587f;    m[2]  = 0.114f;
        m[5]  = -0.16874f; m[6]  = -0.33126f; m[7]  = 0.5f;
        m[10] = 0.5f;      m[11] = -0.41869f; m[12] = -0.08131f;
    }

    /** AOSP ColorMatrix.setYUV2RGB(). m[0] and m[1] keep the values reset()
     *  left (1 and 0) -- AOSP writes only the cells listed here. */
    public void setYUV2RGB() {
        reset();
        float[] m = mArray;
                                        m[2]  = 1.402f;
        m[5]  = 1;  m[6]  = -0.34414f;  m[7]  = -0.71414f;
        m[10] = 1;  m[11] = 1.772f;     m[12] = 0;
    }

    public void setConcat(ColorMatrix matA, ColorMatrix matB) {
        float[] a = matA.mArray, b = matB.mArray;
        float[] tmp = (matA == this || matB == this) ? new float[20] : mArray;
        int index = 0;
        for (int j = 0; j < 20; j += 5) {
            for (int i = 0; i < 4; i++) {
                tmp[index++] = a[j + 0] * b[i + 0] + a[j + 1] * b[i + 5]
                             + a[j + 2] * b[i + 10] + a[j + 3] * b[i + 15];
            }
            tmp[index++] = a[j + 0] * b[4] + a[j + 1] * b[9]
                         + a[j + 2] * b[14] + a[j + 3] * b[19] + a[j + 4];
        }
        if (tmp != mArray) System.arraycopy(tmp, 0, mArray, 0, 20);
    }
    public void preConcat(ColorMatrix prematrix)   { setConcat(this, prematrix); }
    public void postConcat(ColorMatrix postmatrix) { setConcat(postmatrix, this); }

    public void setRotate(int axis, float degrees) {
        reset();
        float radians = (float) (degrees * Math.PI / 180);
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        switch (axis) {
            case 0:
                mArray[6] = mArray[12] = cosine;
                mArray[7] = sine;
                mArray[11] = -sine;
                break;
            case 1:
                mArray[0] = mArray[12] = cosine;
                mArray[2] = -sine;
                mArray[10] = sine;
                break;
            case 2:
                mArray[0] = mArray[6] = cosine;
                mArray[1] = sine;
                mArray[5] = -sine;
                break;
            default: break;
        }
    }
}
