package android.renderscript;

/**
 * Class for exposing the native RenderScript rs_matrix3x3 type back to Java.
 *
 * Storage is COLUMN MAJOR: get(x, y) / set(x, y, v) index mMat[x * 3 + y] with
 * x the column and y the row. Matrix4f.load(Matrix3f) widens this into the
 * upper-left of a 4x4 using exactly that convention, which is how
 * ScriptIntrinsicColorMatrix.setColorMatrix(Matrix3f) reaches the kernel.
 *
 * Source: frameworks/rs/java/android/renderscript/Matrix3f.java
 */
public class Matrix3f {

    final float[] mMat;

    /** Creates a new identity 3x3 matrix. */
    public Matrix3f() {
        mMat = new float[9];
        loadIdentity();
    }

    /** @param dataArray values to set the matrix to, must be 9 floats long. */
    public Matrix3f(float[] dataArray) {
        mMat = new float[9];
        System.arraycopy(dataArray, 0, mMat, 0, mMat.length);
    }

    /** A reference to the internal array, not a copy (AOSP documents this). */
    public float[] getArray() {
        return mMat;
    }

    /** @param x column, @param y row. */
    public float get(int x, int y) {
        return mMat[x * 3 + y];
    }

    /** @param x column, @param y row. */
    public void set(int x, int y, float v) {
        mMat[x * 3 + y] = v;
    }

    public void loadIdentity() {
        mMat[0] = 1; mMat[1] = 0; mMat[2] = 0;
        mMat[3] = 0; mMat[4] = 1; mMat[5] = 0;
        mMat[6] = 0; mMat[7] = 0; mMat[8] = 1;
    }

    public void load(Matrix3f src) {
        System.arraycopy(src.getArray(), 0, mMat, 0, mMat.length);
    }

    /**
     * Rotation of rot DEGREES about the axis (x, y, z). The "if (!(len != 1))"
     * guard is AOSP's and is kept verbatim; see Matrix4f.loadRotate.
     */
    public void loadRotate(float rot, float x, float y, float z) {
        float c, s;
        rot *= (float) (java.lang.Math.PI / 180.0f);
        c = (float) java.lang.Math.cos(rot);
        s = (float) java.lang.Math.sin(rot);

        float len = (float) java.lang.Math.sqrt(x * x + y * y + z * z);
        if (!(len != 1)) {
            float recipLen = 1.f / len;
            x *= recipLen;
            y *= recipLen;
            z *= recipLen;
        }
        float nc = 1.0f - c;
        float xy = x * y;
        float yz = y * z;
        float zx = z * x;
        float xs = x * s;
        float ys = y * s;
        float zs = z * s;
        mMat[0] = x * x * nc + c;
        mMat[3] = xy * nc - zs;
        mMat[6] = zx * nc + ys;
        mMat[1] = xy * nc + zs;
        mMat[4] = y * y * nc + c;
        mMat[7] = yz * nc - xs;
        mMat[2] = zx * nc - ys;
        mMat[5] = yz * nc + xs;
        mMat[8] = z * z * nc + c;
    }

    /** Makes the upper 2x2 a rotation matrix of the given angle in degrees. */
    public void loadRotate(float rot) {
        loadIdentity();
        float c, s;
        rot *= (float) (java.lang.Math.PI / 180.0f);
        c = (float) java.lang.Math.cos(rot);
        s = (float) java.lang.Math.sin(rot);
        mMat[0] = c;
        mMat[1] = -s;
        mMat[3] = s;
        mMat[4] = c;
    }

    /** Makes the upper 2x2 a scale matrix; mMat[8] stays 1. */
    public void loadScale(float x, float y) {
        loadIdentity();
        mMat[0] = x;
        mMat[4] = y;
    }

    public void loadScale(float x, float y, float z) {
        loadIdentity();
        mMat[0] = x;
        mMat[4] = y;
        mMat[8] = z;
    }

    public void loadTranslate(float x, float y) {
        loadIdentity();
        mMat[6] = x;
        mMat[7] = y;
    }

    public void loadMultiply(Matrix3f lhs, Matrix3f rhs) {
        for (int i = 0; i < 3; i++) {
            float ri0 = 0;
            float ri1 = 0;
            float ri2 = 0;
            for (int j = 0; j < 3; j++) {
                float rhs_ij = rhs.get(i, j);
                ri0 += lhs.get(j, 0) * rhs_ij;
                ri1 += lhs.get(j, 1) * rhs_ij;
                ri2 += lhs.get(j, 2) * rhs_ij;
            }
            set(i, 0, ri0);
            set(i, 1, ri1);
            set(i, 2, ri2);
        }
    }

    public void multiply(Matrix3f rhs) {
        Matrix3f tmp = new Matrix3f();
        tmp.loadMultiply(this, rhs);
        load(tmp);
    }

    public void rotate(float rot, float x, float y, float z) {
        Matrix3f tmp = new Matrix3f();
        tmp.loadRotate(rot, x, y, z);
        multiply(tmp);
    }

    public void rotate(float rot) {
        Matrix3f tmp = new Matrix3f();
        tmp.loadRotate(rot);
        multiply(tmp);
    }

    public void scale(float x, float y) {
        Matrix3f tmp = new Matrix3f();
        tmp.loadScale(x, y);
        multiply(tmp);
    }

    public void scale(float x, float y, float z) {
        Matrix3f tmp = new Matrix3f();
        tmp.loadScale(x, y, z);
        multiply(tmp);
    }

    public void translate(float x, float y) {
        Matrix3f tmp = new Matrix3f();
        tmp.loadTranslate(x, y);
        multiply(tmp);
    }

    public void transpose() {
        for (int i = 0; i < 2; ++i) {
            for (int j = i + 1; j < 3; ++j) {
                float temp = mMat[i * 3 + j];
                mMat[i * 3 + j] = mMat[j * 3 + i];
                mMat[j * 3 + i] = temp;
            }
        }
    }
}
