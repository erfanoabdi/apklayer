package android.renderscript;

import java.nio.ByteBuffer;

/**
 * Applies a 4x4 colour matrix plus a constant offset to every cell.
 *
 * This is the second most used intrinsic after the blur: greyscale filters,
 * sepia, channel swaps and YUV/RGB conversion all come through here. It is a
 * real implementation, ported from AOSP's CPU reference kernel.
 *
 * THE VALUE DOMAIN IS THE SUBTLE PART, and getting it wrong produces an image
 * that is uniformly too dark or blown out rather than an obvious failure. The
 * platform does NOT normalize: for a uchar input the components enter the
 * matrix as 0..255. What it scales instead is the coefficients and the offset,
 * by the pair chosen in rsCpuIntrinsicColorMatrix.cpp's preLaunch():
 *
 *   in u8    -> out u8     coefficients x1,     offset x255
 *   in float -> out float  coefficients x1,     offset x1
 *   in float -> out u8     coefficients x255,   offset x255, plus 0.5 rounding
 *   in u8    -> out float  coefficients x1/255, offset x1
 *
 * So setAdd(0.5f, 0, 0, 0) shifts red by half of FULL SCALE, not by half a
 * count, whichever element types are in play. Output cells are clamped to
 * [0, 255.5] and truncated, which is the platform's exact behaviour.
 *
 * The matrix is COLUMN MAJOR (see {@link Matrix4f}), so out[row] is the sum
 * over col of coeff[col * 4 + row] * in[col].
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsicColorMatrix.java
 *         frameworks/rs/cpu_ref/rsCpuIntrinsicColorMatrix.cpp
 */
public final class ScriptIntrinsicColorMatrix extends ScriptIntrinsic {

    private final Matrix4f mMatrix = new Matrix4f();
    private final Float4 mAdd = new Float4();

    private ScriptIntrinsicColorMatrix(RenderScript rs) {
        super(rs);
    }

    /**
     * @param e ignored since API 19; the element check happens per launch.
     * @deprecated use the single-argument version.
     */
    @Deprecated
    public static ScriptIntrinsicColorMatrix create(RenderScript rs, Element e) {
        return create(rs);
    }

    public static ScriptIntrinsicColorMatrix create(RenderScript rs) {
        return new ScriptIntrinsicColorMatrix(rs);
    }

    public void setColorMatrix(Matrix4f m) {
        mMatrix.load(m);
    }

    /** The alpha channel becomes a straight copy. */
    public void setColorMatrix(Matrix3f m) {
        mMatrix.load(m);
    }

    public void setAdd(Float4 f) {
        mAdd.x = f.x;
        mAdd.y = f.y;
        mAdd.z = f.z;
        mAdd.w = f.w;
    }

    public void setAdd(float r, float g, float b, float a) {
        mAdd.x = r;
        mAdd.y = g;
        mAdd.z = b;
        mAdd.w = a;
    }

    /** RGB to luminance with the alpha channel copied through. */
    public void setGreyscale() {
        mMatrix.loadIdentity();
        mMatrix.set(0, 0, 0.299f);
        mMatrix.set(1, 0, 0.587f);
        mMatrix.set(2, 0, 0.114f);
        mMatrix.set(0, 1, 0.299f);
        mMatrix.set(1, 1, 0.587f);
        mMatrix.set(2, 1, 0.114f);
        mMatrix.set(0, 2, 0.299f);
        mMatrix.set(1, 2, 0.587f);
        mMatrix.set(2, 2, 0.114f);
    }

    /** YUV to RGB with a direct copy of the 4th channel. */
    public void setYUVtoRGB() {
        mMatrix.loadIdentity();
        mMatrix.set(0, 0, 1.f);
        mMatrix.set(1, 0, 0.f);
        mMatrix.set(2, 0, 1.13983f);
        mMatrix.set(0, 1, 1.f);
        mMatrix.set(1, 1, -0.39465f);
        mMatrix.set(2, 1, -0.5806f);
        mMatrix.set(0, 2, 1.f);
        mMatrix.set(1, 2, 2.03211f);
        mMatrix.set(2, 2, 0.f);
    }

    /** RGB to YUV with a direct copy of the 4th channel. */
    public void setRGBtoYUV() {
        mMatrix.loadIdentity();
        mMatrix.set(0, 0, 0.299f);
        mMatrix.set(1, 0, 0.587f);
        mMatrix.set(2, 0, 0.114f);
        mMatrix.set(0, 1, -0.14713f);
        mMatrix.set(1, 1, -0.28886f);
        mMatrix.set(2, 1, 0.436f);
        mMatrix.set(0, 2, 0.615f);
        mMatrix.set(1, 2, -0.51499f);
        mMatrix.set(2, 2, -0.10001f);
    }

    public void forEach(Allocation ain, Allocation aout) {
        forEach(ain, aout, null);
    }

    /**
     * Input components beyond the input Element's vector size are treated as
     * zero for the multiply; output components beyond the output Element's
     * vector size are discarded.
     */
    public void forEach(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        if (ain == null || aout == null) {
            throw new RSIllegalArgumentException("ColorMatrix requires both allocations.");
        }
        checkElement(ain.getElement());
        checkElement(aout.getElement());
        IntrinsicSupport.requireSameDims(ain, aout);

        final Element ie = ain.getElement();
        final Element oe = aout.getElement();
        final boolean floatIn = IntrinsicSupport.isF32(ie);
        final boolean floatOut = IntrinsicSupport.isF32(oe);

        final float fpMul;
        final float addMul;
        if (floatIn == floatOut) {
            fpMul = 1f;
            addMul = floatOut ? 1f : 255f;
        } else if (!floatOut) {
            fpMul = 255f;
            addMul = 255f;
        } else {
            fpMul = 1f / 255f;
            addMul = 1f;
        }
        final float roundBias = fpMul > 254f ? 0.5f : 0f;

        final float[] m = mMatrix.getArray();
        final float[] coeff = new float[16];
        for (int i = 0; i < 16; i++) {
            coeff[i] = m[i] * fpMul;
        }
        final float[] add = {
            mAdd.x * addMul + roundBias,
            mAdd.y * addMul + roundBias,
            mAdd.z * addMul + roundBias,
            mAdd.w * addMul + roundBias,
        };

        final ByteBuffer sb = IntrinsicSupport.bufferOf(ain);
        final ByteBuffer db = IntrinsicSupport.bufferOf(aout);
        final int sCell = ie.getBytesSize();
        final int dCell = oe.getBytesSize();
        final int sBase = ain.baseOffset();
        final int dBase = aout.baseOffset();
        final int sRow = ain.rowStride();
        final int dRow = aout.rowStride();
        final int sPlane = ain.planeStride();
        final int dPlane = aout.planeStride();
        final int nOut = IntrinsicSupport.componentCount(oe);
        final int[] b = IntrinsicSupport.bounds(ain, opt);

        final float[] f = new float[4];
        final float[] sum = new float[4];

        for (int z = b[4]; z < b[5]; z++) {
            for (int y = b[2]; y < b[3]; y++) {
                int sp = sBase + (z * sPlane + y * sRow + b[0]) * sCell;
                int dp = dBase + (z * dPlane + y * dRow + b[0]) * dCell;
                for (int x = b[0]; x < b[1]; x++, sp += sCell, dp += dCell) {
                    IntrinsicSupport.readCell(sb, sp, ie, f);
                    for (int r = 0; r < 4; r++) {
                        sum[r] = f[0] * coeff[r]
                               + f[1] * coeff[4 + r]
                               + f[2] * coeff[8 + r]
                               + f[3] * coeff[12 + r]
                               + add[r];
                    }
                    if (floatOut) {
                        IntrinsicSupport.writeCell(db, dp, oe, sum);
                    } else {
                        // The platform clamps to 255.5 and TRUNCATES rather
                        // than rounding, so writeCell (which rounds) is
                        // deliberately not used here.
                        for (int c = 0; c < nOut; c++) {
                            float v = IntrinsicSupport.clamp(sum[c], 0f, 255.5f);
                            db.put(dp + c, (byte) (int) v);
                        }
                    }
                }
            }
        }
    }

    private void checkElement(Element e) {
        if (e.mType != Element.DataType.UNSIGNED_8 && e.mType != Element.DataType.FLOAT_32) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        if (e.getVectorSize() < 1 || e.getVectorSize() > 4) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 3, null, null);
    }
}
