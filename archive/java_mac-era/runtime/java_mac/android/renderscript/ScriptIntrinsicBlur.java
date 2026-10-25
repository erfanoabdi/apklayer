package android.renderscript;

import java.nio.ByteBuffer;

/**
 * Gaussian blur intrinsic: the "blur a bitmap" idiom behind frosted
 * backgrounds, blurred album art and dimmed dialog backdrops, and by a wide
 * margin the most used thing in this package.
 *
 * THIS IS A REAL IMPLEMENTATION, not a stub. RenderScript's GPU backend is gone
 * from modern Android and AOSP itself now services the intrinsics from the CPU
 * reference path, so a separable Gaussian convolution over the Allocation's own
 * bytes is both correct and faithful to what a current device does. Handing the
 * input back unblurred would be the silent kind of wrong this package exists to
 * avoid: the app would show an unblurred background and never know.
 *
 * Details that matter for matching the platform's output:
 *
 *  - sigma = 0.4 * radius + 0.6, weights normalized to sum to 1. That is
 *    ComputeGaussianWeights() in rsCpuIntrinsicBlur.cpp verbatim, and it is not
 *    the textbook sigma; a different one gives a visibly different blur.
 *  - The kernel radius is ceil(radius), so a fractional radius still reaches
 *    the next whole pixel.
 *  - Edges CLAMP (the border pixel is extended) rather than wrapping or
 *    treating the outside as transparent.
 *  - The convolution runs on PREMULTIPLIED colour. AOSP does not premultiply
 *    here because a device's Allocation already holds premultiplied Bitmap
 *    bytes; Mac.apk's Bitmap is non-premultiplied end to end, so we do it
 *    around the kernel to land on the same visible result. See
 *    IntrinsicSupport's note.
 *
 * AOSP clamps radius to (0, 25]; setRadius throws outside that, so a caller
 * that passes 26 fails here exactly as it would on a device.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsicBlur.java
 *         frameworks/rs/cpu_ref/rsCpuIntrinsicBlur.cpp
 */
public final class ScriptIntrinsicBlur extends ScriptIntrinsic {

    private Allocation mInput;
    private float mRadius = 5.0f;

    private ScriptIntrinsicBlur(RenderScript rs) {
        super(rs);
    }

    /**
     * @param e must be U8 or U8_4; the driver rejects anything else and so do
     *          we, because a float input would silently produce garbage.
     */
    public static ScriptIntrinsicBlur create(RenderScript rs, Element e) {
        if (e != null && rs != null
                && !e.isCompatible(Element.U8_4(rs)) && !e.isCompatible(Element.U8(rs))) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        return new ScriptIntrinsicBlur(rs);
    }

    public void setInput(Allocation ain) {
        if (ain != null && !IntrinsicSupport.isU8(ain.getElement())) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        mInput = ain;
    }

    public void setRadius(float radius) {
        if (radius <= 0 || radius > 25) {
            throw new RSIllegalArgumentException("Radius out of range (0 < r <= 25).");
        }
        mRadius = radius;
    }

    public void forEach(Allocation aout) {
        forEach(aout, null);
    }

    public void forEach(Allocation aout, Script.LaunchOptions opt) {
        if (mInput == null) {
            throw new RSIllegalArgumentException("Blur executed without an input Allocation.");
        }
        if (aout == null) {
            throw new RSIllegalArgumentException("Blur executed without an output Allocation.");
        }
        IntrinsicSupport.requireSameDims(mInput, aout);
        if (!IntrinsicSupport.isU8(aout.getElement())) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        blur(mInput, aout, mRadius, opt);
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 2, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(1, null);
    }

    /**
     * Separable Gaussian: a horizontal pass into a scratch plane, then a
     * vertical pass into the destination. Separating turns an O(r^2)
     * per-pixel convolution into O(r), which is what makes a radius of 25
     * affordable on the CPU.
     *
     * The scratch plane covers the WHOLE image even when a LaunchOptions
     * window is set, because a pixel just inside the window still reads
     * horizontal neighbours from outside it.
     */
    private static void blur(Allocation ain, Allocation aout, float radius,
                             Script.LaunchOptions opt) {
        final int w = ain.mCurrentDimX;
        final int h = Math.max(1, ain.mCurrentDimY);
        final Element ie = ain.getElement();
        final Element oe = aout.getElement();
        final int nin = IntrinsicSupport.componentCount(ie);
        final int nout = IntrinsicSupport.componentCount(oe);
        final int r = (int) Math.ceil(radius);
        final float[] k = gaussianKernel(radius, r);

        final ByteBuffer src = IntrinsicSupport.bufferOf(ain);
        final ByteBuffer dst = IntrinsicSupport.bufferOf(aout);
        final int inCell = ie.getBytesSize();
        final int outCell = oe.getBytesSize();
        final int inBase = ain.baseOffset();
        final int outBase = aout.baseOffset();
        final int inStride = ain.rowStride();
        final int outStride = aout.rowStride();

        // Decode the whole source once, premultiplied, into planar floats. One
        // pass over the bytes beats decoding each pixel 2r times.
        final int n = w * h;
        final float[][] p = new float[nin][n];
        final float[] cell = new float[4];
        for (int y = 0; y < h; y++) {
            int pos = inBase + y * inStride * inCell;
            for (int x = 0; x < w; x++, pos += inCell) {
                IntrinsicSupport.readCell(src, pos, ie, cell);
                if (nin == 4) {
                    IntrinsicSupport.premultiply(cell);
                }
                int i = y * w + x;
                for (int c = 0; c < nin; c++) {
                    p[c][i] = cell[c];
                }
            }
        }

        final float[][] t = new float[nin][n];
        for (int y = 0; y < h; y++) {
            final int row = y * w;
            for (int x = 0; x < w; x++) {
                final int o = row + x;
                for (int c = 0; c < nin; c++) {
                    float s = 0;
                    for (int d = -r; d <= r; d++) {
                        int sx = x + d;
                        if (sx < 0) {
                            sx = 0;
                        } else if (sx >= w) {
                            sx = w - 1;
                        }
                        s += p[c][row + sx] * k[d + r];
                    }
                    t[c][o] = s;
                }
            }
        }

        final int[] b = IntrinsicSupport.bounds(aout, opt);
        for (int y = b[2]; y < b[3]; y++) {
            for (int x = b[0]; x < b[1]; x++) {
                for (int c = 0; c < nin; c++) {
                    float s = 0;
                    for (int d = -r; d <= r; d++) {
                        int sy = y + d;
                        if (sy < 0) {
                            sy = 0;
                        } else if (sy >= h) {
                            sy = h - 1;
                        }
                        s += t[c][sy * w + x] * k[d + r];
                    }
                    cell[c] = s;
                }
                for (int c = nin; c < 4; c++) {
                    cell[c] = 0f;
                }
                if (nin == 4) {
                    cell[3] = IntrinsicSupport.clamp(cell[3], 0f, 255f);
                    IntrinsicSupport.unpremultiply(cell);
                }
                // A U8 output fed from a U8_4 input keeps only the first
                // channel, which is what a narrower output Element means.
                if (nout < nin) {
                    for (int c = nout; c < 4; c++) {
                        cell[c] = 0f;
                    }
                }
                IntrinsicSupport.writeCell(dst, outBase + (y * outStride + x) * outCell, oe, cell);
            }
        }
    }

    private static float[] gaussianKernel(float radius, int r) {
        final float sigma = 0.4f * radius + 0.6f;
        final float coeff1 = (float) (1.0 / (Math.sqrt(2.0 * Math.PI) * sigma));
        final float coeff2 = -1.0f / (2.0f * sigma * sigma);
        final float[] k = new float[2 * r + 1];
        float sum = 0;
        for (int i = -r; i <= r; i++) {
            final float v = (float) (coeff1 * Math.exp(i * (float) i * coeff2));
            k[i + r] = v;
            sum += v;
        }
        final float norm = 1.0f / sum;
        for (int i = 0; i < k.length; i++) {
            k[i] *= norm;
        }
        return k;
    }
}
