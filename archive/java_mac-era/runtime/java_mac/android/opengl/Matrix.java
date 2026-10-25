// android.opengl.Matrix -- column-major 4x4 utilities. AndEngine and
// many engines call these for projection/view setup so we implement
// them properly in Java. No JNI; pure float math.
package android.opengl;

public class Matrix {
    public Matrix() {}

    public static void multiplyMM(float[] r, int rOff,
                                  float[] a, int aOff,
                                  float[] b, int bOff) {
        for (int i = 0; i < 4; ++i) {
            float ai0 = a[aOff + i + 0];
            float ai1 = a[aOff + i + 4];
            float ai2 = a[aOff + i + 8];
            float ai3 = a[aOff + i + 12];
            r[rOff + i + 0]  = ai0 * b[bOff + 0]  + ai1 * b[bOff + 1]  + ai2 * b[bOff + 2]  + ai3 * b[bOff + 3];
            r[rOff + i + 4]  = ai0 * b[bOff + 4]  + ai1 * b[bOff + 5]  + ai2 * b[bOff + 6]  + ai3 * b[bOff + 7];
            r[rOff + i + 8]  = ai0 * b[bOff + 8]  + ai1 * b[bOff + 9]  + ai2 * b[bOff + 10] + ai3 * b[bOff + 11];
            r[rOff + i + 12] = ai0 * b[bOff + 12] + ai1 * b[bOff + 13] + ai2 * b[bOff + 14] + ai3 * b[bOff + 15];
        }
    }

    public static void multiplyMV(float[] r, int rOff,
                                  float[] m, int mOff,
                                  float[] v, int vOff) {
        for (int i = 0; i < 4; ++i) {
            r[rOff + i] = m[mOff + i + 0]  * v[vOff + 0]
                        + m[mOff + i + 4]  * v[vOff + 1]
                        + m[mOff + i + 8]  * v[vOff + 2]
                        + m[mOff + i + 12] * v[vOff + 3];
        }
    }

    public static void setIdentityM(float[] sm, int smOff) {
        for (int i = 0; i < 16; ++i) sm[smOff + i] = 0f;
        sm[smOff +  0] = 1f;
        sm[smOff +  5] = 1f;
        sm[smOff + 10] = 1f;
        sm[smOff + 15] = 1f;
    }

    public static void translateM(float[] m, int mOff, float x, float y, float z) {
        m[mOff + 12] += m[mOff + 0] * x + m[mOff + 4] * y + m[mOff + 8]  * z;
        m[mOff + 13] += m[mOff + 1] * x + m[mOff + 5] * y + m[mOff + 9]  * z;
        m[mOff + 14] += m[mOff + 2] * x + m[mOff + 6] * y + m[mOff + 10] * z;
        m[mOff + 15] += m[mOff + 3] * x + m[mOff + 7] * y + m[mOff + 11] * z;
    }

    public static void scaleM(float[] m, int mOff, float x, float y, float z) {
        for (int i = 0; i < 4; ++i) {
            m[mOff + i]      *= x;
            m[mOff + i + 4]  *= y;
            m[mOff + i + 8]  *= z;
        }
    }

    public static void orthoM(float[] m, int mOff,
                              float left, float right,
                              float bottom, float top,
                              float near, float far) {
        float r_l = 1f / (right - left);
        float t_b = 1f / (top - bottom);
        float f_n = 1f / (far - near);
        for (int i = 0; i < 16; ++i) m[mOff + i] = 0f;
        m[mOff + 0]  = 2f * r_l;
        m[mOff + 5]  = 2f * t_b;
        m[mOff + 10] = -2f * f_n;
        m[mOff + 12] = -(right + left) * r_l;
        m[mOff + 13] = -(top + bottom) * t_b;
        m[mOff + 14] = -(far + near) * f_n;
        m[mOff + 15] = 1f;
    }

    public static void frustumM(float[] m, int mOff,
                                float left, float right,
                                float bottom, float top,
                                float near, float far) {
        if (near <= 0f || far <= near) throw new IllegalArgumentException();
        float r_l = 1f / (right - left);
        float t_b = 1f / (top - bottom);
        float n_f = 1f / (near - far);
        for (int i = 0; i < 16; ++i) m[mOff + i] = 0f;
        m[mOff + 0]  = 2f * near * r_l;
        m[mOff + 5]  = 2f * near * t_b;
        m[mOff + 8]  = (right + left) * r_l;
        m[mOff + 9]  = (top + bottom) * t_b;
        m[mOff + 10] = (far + near) * n_f;
        m[mOff + 11] = -1f;
        m[mOff + 14] = 2f * far * near * n_f;
    }

    /** Define a projection matrix from a field-of-view angle, an aspect
     *  ratio and the z clip planes. This is the idiom renderers reach for
     *  in onSurfaceChanged instead of hand-computing frustum bounds, so a
     *  missing perspectiveM is a NoSuchMethodError the first time the
     *  camera is set up. Body is AOSP's verbatim
     *  (frameworks/base/opengl/java/android/opengl/Matrix.java): note that
     *  it writes ALL 16 entries, so unlike frustumM it needs no pre-clear,
     *  and m[offset+15] is 0 (a projection matrix, not affine).
     *  fovy is in DEGREES, hence the PI/360 (half-angle in radians). */
    public static void perspectiveM(float[] m, int offset,
                                    float fovy, float aspect, float zNear, float zFar) {
        float f = 1.0f / (float) Math.tan(fovy * (Math.PI / 360.0));
        float rangeReciprocal = 1.0f / (zNear - zFar);

        m[offset + 0] = f / aspect;
        m[offset + 1] = 0.0f;
        m[offset + 2] = 0.0f;
        m[offset + 3] = 0.0f;

        m[offset + 4] = 0.0f;
        m[offset + 5] = f;
        m[offset + 6] = 0.0f;
        m[offset + 7] = 0.0f;

        m[offset + 8] = 0.0f;
        m[offset + 9] = 0.0f;
        m[offset + 10] = (zFar + zNear) * rangeReciprocal;
        m[offset + 11] = -1.0f;

        m[offset + 12] = 0.0f;
        m[offset + 13] = 0.0f;
        m[offset + 14] = 2.0f * zFar * zNear * rangeReciprocal;
        m[offset + 15] = 0.0f;
    }

    /** Post-multiply m by a rotation of `a` degrees about the (x,y,z)
     *  axis: m := m * R. Was previously a "cheap shortcut" that handled
     *  ONLY the three canonical axes and silently left m UNCHANGED for
     *  any other axis (e.g. rotateM(m,a,1,1,0)) -- a silent-wrong stub
     *  that broke 3D apps. Now uses the full Rodrigues rotation matrix
     *  (mirrors AOSP Matrix.rotateM -> setRotateM), which reduces exactly
     *  to the old canonical-axis cases. */
    public static void rotateM(float[] m, int mOff, float a, float x, float y, float z) {
        float[] r = new float[16];
        setRotateM(r, 0, a, x, y, z);
        float[] result = new float[16];
        multiplyMM(result, 0, m, mOff, r, 0);
        System.arraycopy(result, 0, m, mOff, 16);
    }

    /** Build a rotation matrix of `a` degrees about the (x,y,z) axis into
     *  rm (AOSP-identical, column-major). The axis is normalized. */
    public static void setRotateM(float[] rm, int rmOff, float a, float x, float y, float z) {
        rm[rmOff + 3] = 0;
        rm[rmOff + 7] = 0;
        rm[rmOff + 11] = 0;
        rm[rmOff + 12] = 0;
        rm[rmOff + 13] = 0;
        rm[rmOff + 14] = 0;
        rm[rmOff + 15] = 1;
        double rad = Math.toRadians(a);
        float s = (float) Math.sin(rad);
        float c = (float) Math.cos(rad);
        if (1.0f == x && 0.0f == y && 0.0f == z) {
            rm[rmOff + 5] = c;   rm[rmOff + 10] = c;
            rm[rmOff + 6] = s;   rm[rmOff + 9] = -s;
            rm[rmOff + 1] = 0;   rm[rmOff + 2] = 0;
            rm[rmOff + 4] = 0;   rm[rmOff + 8] = 0;
            rm[rmOff + 0] = 1;
        } else if (0.0f == x && 1.0f == y && 0.0f == z) {
            rm[rmOff + 0] = c;   rm[rmOff + 10] = c;
            rm[rmOff + 8] = s;   rm[rmOff + 2] = -s;
            rm[rmOff + 1] = 0;   rm[rmOff + 4] = 0;
            rm[rmOff + 6] = 0;   rm[rmOff + 9] = 0;
            rm[rmOff + 5] = 1;
        } else if (0.0f == x && 0.0f == y && 1.0f == z) {
            rm[rmOff + 0] = c;   rm[rmOff + 5] = c;
            rm[rmOff + 1] = s;   rm[rmOff + 4] = -s;
            rm[rmOff + 2] = 0;   rm[rmOff + 6] = 0;
            rm[rmOff + 8] = 0;   rm[rmOff + 9] = 0;
            rm[rmOff + 10] = 1;
        } else {
            float len = length(x, y, z);
            if (1.0f != len) {
                float recipLen = 1.0f / len;
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
            rm[rmOff + 0] = x * x * nc + c;
            rm[rmOff + 4] = xy * nc - zs;
            rm[rmOff + 8] = zx * nc + ys;
            rm[rmOff + 1] = xy * nc + zs;
            rm[rmOff + 5] = y * y * nc + c;
            rm[rmOff + 9] = yz * nc - xs;
            rm[rmOff + 2] = zx * nc - ys;
            rm[rmOff + 6] = yz * nc + xs;
            rm[rmOff + 10] = z * z * nc + c;
        }
    }

    public static void setLookAtM(float[] rm, int rmOff,
                                  float eyeX, float eyeY, float eyeZ,
                                  float centerX, float centerY, float centerZ,
                                  float upX, float upY, float upZ) {
        // Real gluLookAt-equivalent view matrix, matching AOSP
        // android.opengl.Matrix.setLookAtM exactly. The previous stub
        // returned identity, so any app that built its MVP as
        // projection * view (e.g. Vector Pinball's GL20Renderer:
        // frustumM + setLookAtM + multiplyMM) got projection-only --
        // the world geometry kept its un-centered, un-pushed-back
        // coordinates and projected to a degenerate clip volume (w==0
        // for z==0 geometry), so nothing rasterized: a black GL surface
        // with valid draw calls and no GL error. Column-major.
        float fx = centerX - eyeX;
        float fy = centerY - eyeY;
        float fz = centerZ - eyeZ;
        float rlf = 1.0f / length(fx, fy, fz);
        fx *= rlf; fy *= rlf; fz *= rlf;
        // s = f x up
        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float rls = 1.0f / length(sx, sy, sz);
        sx *= rls; sy *= rls; sz *= rls;
        // u = s x f
        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        rm[rmOff + 0]  = sx;
        rm[rmOff + 1]  = ux;
        rm[rmOff + 2]  = -fx;
        rm[rmOff + 3]  = 0.0f;

        rm[rmOff + 4]  = sy;
        rm[rmOff + 5]  = uy;
        rm[rmOff + 6]  = -fy;
        rm[rmOff + 7]  = 0.0f;

        rm[rmOff + 8]  = sz;
        rm[rmOff + 9]  = uz;
        rm[rmOff + 10] = -fz;
        rm[rmOff + 11] = 0.0f;

        rm[rmOff + 12] = 0.0f;
        rm[rmOff + 13] = 0.0f;
        rm[rmOff + 14] = 0.0f;
        rm[rmOff + 15] = 1.0f;

        translateM(rm, rmOff, -eyeX, -eyeY, -eyeZ);
    }

    /** Full 4x4 inverse via Cramer's rule (AOSP-identical). Was a
     *  "diagonal-only" approximation that returned wrong results for any
     *  matrix with off-diagonal terms (i.e. any rotation/perspective) --
     *  a silent-wrong stub. Returns false (leaving mInv untouched) when
     *  the matrix is singular. */
    public static boolean invertM(float[] mInv, int mInvOff, float[] m, int mOff) {
        // transpose matrix
        final float src0  = m[mOff + 0];
        final float src4  = m[mOff + 1];
        final float src8  = m[mOff + 2];
        final float src12 = m[mOff + 3];
        final float src1  = m[mOff + 4];
        final float src5  = m[mOff + 5];
        final float src9  = m[mOff + 6];
        final float src13 = m[mOff + 7];
        final float src2  = m[mOff + 8];
        final float src6  = m[mOff + 9];
        final float src10 = m[mOff + 10];
        final float src14 = m[mOff + 11];
        final float src3  = m[mOff + 12];
        final float src7  = m[mOff + 13];
        final float src11 = m[mOff + 14];
        final float src15 = m[mOff + 15];
        // calculate pairs for first 8 elements (cofactors)
        final float atmp0  = src10 * src15;
        final float atmp1  = src11 * src14;
        final float atmp2  = src9  * src15;
        final float atmp3  = src11 * src13;
        final float atmp4  = src9  * src14;
        final float atmp5  = src10 * src13;
        final float atmp6  = src8  * src15;
        final float atmp7  = src11 * src12;
        final float atmp8  = src8  * src14;
        final float atmp9  = src10 * src12;
        final float atmp10 = src8  * src13;
        final float atmp11 = src9  * src12;
        // calculate first 8 elements (cofactors)
        final float dst0  = (atmp0 * src5 + atmp3 * src6 + atmp4 * src7)  - (atmp1 * src5 + atmp2 * src6 + atmp5 * src7);
        final float dst1  = (atmp1 * src4 + atmp6 * src6 + atmp9 * src7)  - (atmp0 * src4 + atmp7 * src6 + atmp8 * src7);
        final float dst2  = (atmp2 * src4 + atmp7 * src5 + atmp10 * src7) - (atmp3 * src4 + atmp6 * src5 + atmp11 * src7);
        final float dst3  = (atmp5 * src4 + atmp8 * src5 + atmp11 * src6) - (atmp4 * src4 + atmp9 * src5 + atmp10 * src6);
        final float dst4  = (atmp1 * src1 + atmp2 * src2 + atmp5 * src3)  - (atmp0 * src1 + atmp3 * src2 + atmp4 * src3);
        final float dst5  = (atmp0 * src0 + atmp7 * src2 + atmp8 * src3)  - (atmp1 * src0 + atmp6 * src2 + atmp9 * src3);
        final float dst6  = (atmp3 * src0 + atmp6 * src1 + atmp11 * src3) - (atmp2 * src0 + atmp7 * src1 + atmp10 * src3);
        final float dst7  = (atmp4 * src0 + atmp9 * src1 + atmp10 * src2) - (atmp5 * src0 + atmp8 * src1 + atmp11 * src2);
        // calculate pairs for second 8 elements (cofactors)
        final float btmp0  = src2 * src7;
        final float btmp1  = src3 * src6;
        final float btmp2  = src1 * src7;
        final float btmp3  = src3 * src5;
        final float btmp4  = src1 * src6;
        final float btmp5  = src2 * src5;
        final float btmp6  = src0 * src7;
        final float btmp7  = src3 * src4;
        final float btmp8  = src0 * src6;
        final float btmp9  = src2 * src4;
        final float btmp10 = src0 * src5;
        final float btmp11 = src1 * src4;
        // calculate second 8 elements (cofactors)
        final float dst8  = (btmp0 * src13 + btmp3 * src14 + btmp4 * src15)  - (btmp1 * src13 + btmp2 * src14 + btmp5 * src15);
        final float dst9  = (btmp1 * src12 + btmp6 * src14 + btmp9 * src15)  - (btmp0 * src12 + btmp7 * src14 + btmp8 * src15);
        final float dst10 = (btmp2 * src12 + btmp7 * src13 + btmp10 * src15) - (btmp3 * src12 + btmp6 * src13 + btmp11 * src15);
        final float dst11 = (btmp5 * src12 + btmp8 * src13 + btmp11 * src14) - (btmp4 * src12 + btmp9 * src13 + btmp10 * src14);
        final float dst12 = (btmp2 * src10 + btmp5 * src11 + btmp1 * src9)   - (btmp4 * src11 + btmp0 * src9  + btmp3 * src10);
        final float dst13 = (btmp8 * src11 + btmp0 * src8  + btmp7 * src10)  - (btmp6 * src10 + btmp9 * src11 + btmp1 * src8);
        final float dst14 = (btmp6 * src9  + btmp11 * src11 + btmp3 * src8)  - (btmp10 * src11 + btmp2 * src8 + btmp7 * src9);
        final float dst15 = (btmp10 * src10 + btmp4 * src8 + btmp9 * src9)   - (btmp8 * src9  + btmp11 * src10 + btmp5 * src8);
        // calculate determinant
        final float det = src0 * dst0 + src1 * dst1 + src2 * dst2 + src3 * dst3;
        if (det == 0.0f) return false;
        // calculate matrix inverse
        final float invdet = 1.0f / det;
        mInv[mInvOff + 0]  = dst0  * invdet;
        mInv[mInvOff + 1]  = dst1  * invdet;
        mInv[mInvOff + 2]  = dst2  * invdet;
        mInv[mInvOff + 3]  = dst3  * invdet;
        mInv[mInvOff + 4]  = dst4  * invdet;
        mInv[mInvOff + 5]  = dst5  * invdet;
        mInv[mInvOff + 6]  = dst6  * invdet;
        mInv[mInvOff + 7]  = dst7  * invdet;
        mInv[mInvOff + 8]  = dst8  * invdet;
        mInv[mInvOff + 9]  = dst9  * invdet;
        mInv[mInvOff + 10] = dst10 * invdet;
        mInv[mInvOff + 11] = dst11 * invdet;
        mInv[mInvOff + 12] = dst12 * invdet;
        mInv[mInvOff + 13] = dst13 * invdet;
        mInv[mInvOff + 14] = dst14 * invdet;
        mInv[mInvOff + 15] = dst15 * invdet;
        return true;
    }

    public static void transposeM(float[] mTrans, int mTransOff, float[] m, int mOff) {
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < 4; ++j)
                mTrans[mTransOff + j * 4 + i] = m[mOff + i * 4 + j];
    }

    public static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    // ============ AOSP overloads added 2026-08-24 ============================
    // opengl/java/android/opengl/Matrix.java (android16-release). All pure Java
    // on device too -- these are NOT JNI -- so they are exact, not approximate.

    /** AOSP Matrix.translateM(tm, tmOffset, m, mOffset, x, y, z) (:665) --
     *  the NON-destructive form: tm := m translated, leaving m alone. */
    public static void translateM(float[] tm, int tmOffset,
            float[] m, int mOffset, float x, float y, float z) {
        for (int i = 0; i < 12; i++) {
            tm[tmOffset + i] = m[mOffset + i];
        }
        for (int i = 0; i < 4; i++) {
            int tmi = tmOffset + i;
            int mi = mOffset + i;
            tm[12 + tmi] = m[mi] * x + m[4 + mi] * y + m[8 + mi] * z + m[12 + mi];
        }
    }

    /** AOSP Matrix.scaleM(sm, smOffset, m, mOffset, x, y, z) (:620) -- the
     *  non-destructive form. Note it COPIES the translation column (12..15)
     *  unscaled; forgetting that silently zeroes an object's position. */
    public static void scaleM(float[] sm, int smOffset,
            float[] m, int mOffset, float x, float y, float z) {
        for (int i = 0; i < 4; i++) {
            int smi = smOffset + i;
            int mi = mOffset + i;
            sm[     smi] = m[     mi] * x;
            sm[ 4 + smi] = m[ 4 + mi] * y;
            sm[ 8 + smi] = m[ 8 + mi] * z;
            sm[12 + smi] = m[12 + mi];
        }
    }

    /** AOSP Matrix.rotateM(rm, rmOffset, m, mOffset, a, x, y, z) (:711) --
     *  rm := m * R, non-destructive. */
    public static void rotateM(float[] rm, int rmOffset,
            float[] m, int mOffset, float a, float x, float y, float z) {
        float[] tmp = new float[16];
        setRotateM(tmp, 0, a, x, y, z);
        multiplyMM(rm, rmOffset, m, mOffset, tmp, 0);
    }

    /** AOSP Matrix.setRotateEulerM(rm, rmOffset, x, y, z) (:820).
     *
     *  ⚠ DEPRECATED IN AOSP BECAUSE IT IS WRONG, and it is reproduced wrong ON
     *  PURPOSE. Its rows 4..6 use `cxsy` and rows 8..10 use `-sxsy`, which is
     *  not a correct XYZ Euler rotation; setRotateEulerM2 below is the corrected
     *  version and swaps exactly those two terms. Apps written against the old
     *  behaviour depend on the old matrix, so "fixing" this here would rotate
     *  their scenes differently from every real device. */
    @Deprecated
    public static void setRotateEulerM(float[] rm, int rmOffset,
            float x, float y, float z) {
        x *= (float) (Math.PI / 180.0f);
        y *= (float) (Math.PI / 180.0f);
        z *= (float) (Math.PI / 180.0f);
        float cx = (float) Math.cos(x);
        float sx = (float) Math.sin(x);
        float cy = (float) Math.cos(y);
        float sy = (float) Math.sin(y);
        float cz = (float) Math.cos(z);
        float sz = (float) Math.sin(z);
        float cxsy = cx * sy;
        float sxsy = sx * sy;

        rm[rmOffset + 0]  =   cy * cz;
        rm[rmOffset + 1]  =  -cy * sz;
        rm[rmOffset + 2]  =   sy;
        rm[rmOffset + 3]  =  0.0f;

        rm[rmOffset + 4]  =  cxsy * cz + cx * sz;
        rm[rmOffset + 5]  = -cxsy * sz + cx * cz;
        rm[rmOffset + 6]  =  -sx * cy;
        rm[rmOffset + 7]  =  0.0f;

        rm[rmOffset + 8]  = -sxsy * cz + sx * sz;
        rm[rmOffset + 9]  =  sxsy * sz + sx * cz;
        rm[rmOffset + 10] =  cx * cy;
        rm[rmOffset + 11] =  0.0f;

        rm[rmOffset + 12] =  0.0f;
        rm[rmOffset + 13] =  0.0f;
        rm[rmOffset + 14] =  0.0f;
        rm[rmOffset + 15] =  1.0f;
    }

    /** AOSP Matrix.setRotateEulerM2(rm, rmOffset, x, y, z) (:868, API 34) --
     *  the CORRECTED Euler rotation. Differs from setRotateEulerM only in the
     *  `sxsy`/`cxsy` pair (see that method). The three IllegalArgumentException
     *  checks are AOSP's and happen before anything is written. */
    public static void setRotateEulerM2(float[] rm, int rmOffset,
            float x, float y, float z) {
        if (rm == null) {
            throw new IllegalArgumentException("rm == null");
        }
        if (rmOffset < 0) {
            throw new IllegalArgumentException("rmOffset < 0");
        }
        if (rm.length < rmOffset + 16) {
            throw new IllegalArgumentException("rm.length < rmOffset + 16");
        }

        x *= (float) (Math.PI / 180.0f);
        y *= (float) (Math.PI / 180.0f);
        z *= (float) (Math.PI / 180.0f);
        float cx = (float) Math.cos(x);
        float sx = (float) Math.sin(x);
        float cy = (float) Math.cos(y);
        float sy = (float) Math.sin(y);
        float cz = (float) Math.cos(z);
        float sz = (float) Math.sin(z);
        float cxsy = cx * sy;
        float sxsy = sx * sy;

        rm[rmOffset + 0]  =  cy * cz;
        rm[rmOffset + 1]  = -cy * sz;
        rm[rmOffset + 2]  =  sy;
        rm[rmOffset + 3]  =  0.0f;

        rm[rmOffset + 4]  =  sxsy * cz + cx * sz;
        rm[rmOffset + 5]  = -sxsy * sz + cx * cz;
        rm[rmOffset + 6]  = -sx * cy;
        rm[rmOffset + 7]  =  0.0f;

        rm[rmOffset + 8]  = -cxsy * cz + sx * sz;
        rm[rmOffset + 9]  =  cxsy * sz + sx * cz;
        rm[rmOffset + 10] =  cx * cy;
        rm[rmOffset + 11] =  0.0f;

        rm[rmOffset + 12] =  0.0f;
        rm[rmOffset + 13] =  0.0f;
        rm[rmOffset + 14] =  0.0f;
        rm[rmOffset + 15] =  1.0f;
    }
}
