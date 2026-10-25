package android.renderscript;

import java.nio.ByteBuffer;

/**
 * Counts pixel values into a 256-bin histogram: auto-levels, exposure
 * analysis, thresholding.
 *
 * Two modes, and they write DIFFERENT output shapes, which is the thing to get
 * right:
 *
 *  - forEach() bins each channel independently. The output Allocation must have
 *    the same vector size as the input, and the bins are INTERLEAVED, so bin b
 *    of channel c lives at cell b, component c. It is not four consecutive
 *    256-run blocks.
 *  - forEach_Dot() first reduces each pixel to one value with the dot
 *    coefficients, then bins that. The output must be single-component.
 *
 * The dot reduction is done in the platform's fixed point: each coefficient is
 * rounded to a 1/256 step first, and the bin is (sum + 0x7f) >> 8. Doing it in
 * float instead puts a handful of pixels in the neighbouring bin.
 *
 * The output is OVERWRITTEN, not accumulated: a second forEach on the same
 * output does not add to the first.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsicHistogram.java
 *         frameworks/rs/cpu_ref/rsCpuIntrinsicHistogram.cpp
 */
public final class ScriptIntrinsicHistogram extends ScriptIntrinsic {

    private Allocation mOut;
    private final float[] mDot = {0.299f, 0.587f, 0.114f, 0.f};

    private ScriptIntrinsicHistogram(RenderScript rs) {
        super(rs);
    }

    /** @param e must be U8, U8_2, U8_3 or U8_4. */
    public static ScriptIntrinsicHistogram create(RenderScript rs, Element e) {
        if (e != null && !IntrinsicSupport.isU8(e)) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        return new ScriptIntrinsicHistogram(rs);
    }

    /** @param aout a 1D, 256-cell Allocation of I32 or U32 (vector size 1..4). */
    public void setOutput(Allocation aout) {
        Element e = aout.getType().getElement();
        if (e.mType != Element.DataType.SIGNED_32 && e.mType != Element.DataType.UNSIGNED_32) {
            throw new RSIllegalArgumentException("Output type must be I32 or U32.");
        }
        if ((aout.getType().getX() != 256) || (aout.getType().getY() != 0)
                || aout.getType().hasMipmaps() || (aout.getType().getYuv() != 0)) {
            throw new RSIllegalArgumentException("Output must be 1D, 256 in length.");
        }
        mOut = aout;
    }

    /**
     * @param r red, @param g green, @param b blue, @param a alpha weight. Each
     *          must be in [0, 1] and they must sum to at most 1, so the reduced
     *          value cannot leave the 0..255 bin range.
     */
    public void setDotCoefficients(float r, float g, float b, float a) {
        if ((r < 0.f) || (g < 0.f) || (b < 0.f) || (a < 0.f)) {
            throw new RSIllegalArgumentException("Coefficient may not be negative.");
        }
        if ((r + g + b + a) > 1.f) {
            throw new RSIllegalArgumentException("Sum of coefficients must be 1.0 or less.");
        }
        mDot[0] = r;
        mDot[1] = g;
        mDot[2] = b;
        mDot[3] = a;
    }

    public void forEach(Allocation ain) {
        forEach(ain, null);
    }

    public void forEach(Allocation ain, Script.LaunchOptions opt) {
        requireReady(ain);
        final int vin = IntrinsicSupport.componentCount(ain.getElement());
        final int vout = IntrinsicSupport.componentCount(mOut.getElement());
        if (vin != vout) {
            throw new RSIllegalArgumentException(
                    "Input and output vector size mismatch.");
        }

        final int[] sums = new int[256 * vout];
        scan(ain, opt, sums, vout, false);
        writeSums(sums, vout);
    }

    public void forEach_Dot(Allocation ain) {
        forEach_Dot(ain, null);
    }

    public void forEach_Dot(Allocation ain, Script.LaunchOptions opt) {
        requireReady(ain);
        if (IntrinsicSupport.componentCount(mOut.getElement()) != 1) {
            throw new RSIllegalArgumentException("Output vector size must be one.");
        }
        final int[] sums = new int[256];
        scan(ain, opt, sums, 1, true);
        writeSums(sums, 1);
    }

    private void requireReady(Allocation ain) {
        if (ain == null) {
            throw new RSIllegalArgumentException("Histogram executed without an input.");
        }
        if (mOut == null) {
            throw new RSIllegalArgumentException("Histogram executed without setOutput.");
        }
        if (!IntrinsicSupport.isU8(ain.getElement())) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
    }

    private void scan(Allocation ain, Script.LaunchOptions opt, int[] sums, int vout,
                      boolean dot) {
        final ByteBuffer sb = IntrinsicSupport.bufferOf(ain);
        final Element ie = ain.getElement();
        final int cell = ie.getBytesSize();
        final int nin = IntrinsicSupport.componentCount(ie);
        final int base = ain.baseOffset();
        final int row = ain.rowStride();
        final int plane = ain.planeStride();
        final int[] b = IntrinsicSupport.bounds(ain, opt);

        // The platform quantises the coefficients to 1/256 before use.
        final int[] dotI = new int[4];
        for (int i = 0; i < 4; i++) {
            dotI[i] = (int) ((mDot[i] * 256.f) + 0.5f);
        }

        for (int z = b[4]; z < b[5]; z++) {
            for (int y = b[2]; y < b[3]; y++) {
                int pos = base + (z * plane + y * row + b[0]) * cell;
                for (int x = b[0]; x < b[1]; x++, pos += cell) {
                    if (dot) {
                        int t = 0;
                        for (int c = 0; c < nin; c++) {
                            t += dotI[c] * (sb.get(pos + c) & 0xff);
                        }
                        int bin = (t + 0x7f) >> 8;
                        if (bin < 0) {
                            bin = 0;
                        } else if (bin > 255) {
                            bin = 255;
                        }
                        sums[bin]++;
                    } else {
                        for (int c = 0; c < vout; c++) {
                            sums[((sb.get(pos + c) & 0xff) * vout) + c]++;
                        }
                    }
                }
            }
        }
    }

    private void writeSums(int[] sums, int vout) {
        final ByteBuffer ob = IntrinsicSupport.bufferOf(mOut);
        final int cell = mOut.getElement().getBytesSize();
        final int base = mOut.baseOffset();
        for (int i = 0; i < 256; i++) {
            for (int c = 0; c < vout; c++) {
                ob.putInt(base + i * cell + c * 4, sums[i * vout + c]);
            }
        }
    }

    public Script.KernelID getKernelID_Separate() {
        return createKernelID(0, 3, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(1, null);
    }
}
