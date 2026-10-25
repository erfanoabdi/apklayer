package android.renderscript;


/**
 * 3x3 convolution over a uchar or float Allocation: sharpen, emboss,
 * edge detect, box blur, and every other small-kernel filter.
 *
 * Real implementation. Two behaviours are the platform's and are worth naming
 * because a hand-rolled convolution usually gets them wrong:
 *
 *  - Edges CLAMP. A tap outside the image reuses the nearest border pixel; it
 *    is not zero and it does not wrap. Zero-padding darkens every border.
 *  - The uchar path adds 0.5 and clamps to [0, 255] BEFORE truncating, so the
 *    result is round-to-nearest. The float path neither rounds nor clamps, so
 *    a float Allocation can legitimately come back negative or over 1.0.
 *
 * Coefficients are in ROW MAJOR order, top-left first, and are NOT normalized
 * for you: a box blur must pass 1/9 in every slot.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsicConvolve3x3.java
 *         frameworks/rs/cpu_ref/rsCpuIntrinsicConvolve3x3.cpp
 */
public final class ScriptIntrinsicConvolve3x3 extends ScriptIntrinsic {

    private final float[] mValues = new float[9];
    private Allocation mInput;

    private ScriptIntrinsicConvolve3x3(RenderScript rs) {
        super(rs);
    }

    /**
     * @param e must be one of U8, U8_2, U8_3, U8_4, F32, F32_2, F32_3, F32_4.
     *          The default coefficients are an identity kernel (1 in the
     *          centre, 0 elsewhere), so a forEach before setCoefficients is a
     *          copy rather than a black frame.
     */
    public static ScriptIntrinsicConvolve3x3 create(RenderScript rs, Element e) {
        if (e != null && e.mType != Element.DataType.UNSIGNED_8
                && e.mType != Element.DataType.FLOAT_32) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        ScriptIntrinsicConvolve3x3 si = new ScriptIntrinsicConvolve3x3(rs);
        si.mValues[4] = 1.f;
        return si;
    }

    public void setInput(Allocation ain) {
        if (ain != null && ain.getElement().mType != Element.DataType.UNSIGNED_8
                && ain.getElement().mType != Element.DataType.FLOAT_32) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        mInput = ain;
    }

    /** @param v exactly 9 coefficients, row major from the top left. */
    public void setCoefficients(float[] v) {
        if (v == null || v.length != 9) {
            throw new RSIllegalArgumentException(
                    "Convolve 3x3 needs 9 coefficients.");
        }
        System.arraycopy(v, 0, mValues, 0, 9);
    }

    public void forEach(Allocation aout) {
        forEach(aout, null);
    }

    public void forEach(Allocation aout, Script.LaunchOptions opt) {
        if (mInput == null) {
            throw new RSIllegalArgumentException(
                    "Convolve executed without an input Allocation.");
        }
        if (aout == null) {
            throw new RSIllegalArgumentException(
                    "Convolve executed without an output Allocation.");
        }
        IntrinsicSupport.requireSameDims(mInput, aout);
        IntrinsicSupport.convolve(mInput, aout, mValues, 1, opt);
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 2, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(1, null);
    }
}
