package android.renderscript;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * MAC.APK INTERNAL, NOT PART OF THE ANDROID API. Package-private, so no app can
 * see or name it.
 *
 * Shared plumbing for the ScriptIntrinsic implementations in this package: cell
 * decode/encode, launch-range clamping, and the premultiply helpers. It exists
 * so the ten intrinsics do not each carry their own copy of the Element
 * unpacking rules, which is exactly the sort of duplication that lets two of
 * them drift apart.
 *
 * VALUE DOMAIN. Cells are decoded to float in their NATURAL units, not
 * normalized: a uchar4 decodes to 0..255, a float4 decodes to whatever it
 * holds. That is what AOSP's cpu_ref kernels do (see
 * rsCpuIntrinsicColorMatrix.cpp's One(), which calls convert_float4 on the
 * uchar4 and never divides by 255), and every coefficient in this package is
 * scaled to match.
 */
final class IntrinsicSupport {

    private IntrinsicSupport() {
    }

    static ByteBuffer bufferOf(Allocation a) {
        return ByteBuffer.wrap(a.data()).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Byte offset of cell (x, y, z) within a's window. Uses the backing
     * store's row and plane strides, which differ from the window's own
     * dimensions when a is an AllocationAdapter.
     */
    static int cellPos(Allocation a, int x, int y, int z) {
        final int cell = a.getElement().getBytesSize();
        return a.baseOffset() + (z * a.planeStride() + y * a.rowStride() + x) * cell;
    }

    /**
     * Decodes one cell into out[0..3], zero-filling components the Element does
     * not have. A vec3 reads three components and leaves out[3] at 0, matching
     * the kernels, which never look at the pad slot.
     */
    static void readCell(ByteBuffer bb, int pos, Element e, float[] out) {
        final Element.DataType dt = e.mType;
        final int n = componentCount(e);
        final int step = dt.mSize;
        for (int i = 0; i < 4; i++) {
            out[i] = i < n ? (float) Allocation.readComponent(bb, pos + i * step, dt) : 0f;
        }
    }

    /**
     * Encodes out[0..n-1] back into one cell, clamping and rounding per the
     * Element's DataType. Integer types round to nearest rather than
     * truncating: AOSP's generic C path truncates but its NEON path (the one
     * that actually ran on every ARM device) rounds, and rounding is the less
     * lossy of the two.
     */
    static void writeCell(ByteBuffer bb, int pos, Element e, float[] in) {
        final Element.DataType dt = e.mType;
        final int n = componentCount(e);
        final int step = dt.mSize;
        for (int i = 0; i < n; i++) {
            Allocation.writeComponent(bb, pos + i * step, dt, in[i]);
        }
    }

    /**
     * Components the Element actually carries. Derived from the byte size
     * rather than getVectorSize() because a PIXEL Element reports vector size
     * from its DataKind, and because a vec3 must report 3 even though it
     * occupies 4 slots.
     */
    static int componentCount(Element e) {
        final Element.DataType dt = e.mType;
        if (dt.mSize == 0) {
            return 0;
        }
        switch (dt) {
            case UNSIGNED_5_6_5:
            case UNSIGNED_5_5_5_1:
            case UNSIGNED_4_4_4_4:
                // One packed unit; the intrinsics do not accept these.
                return 1;
            default:
                return Math.min(4, Math.max(1, e.getVectorSize()));
        }
    }

    /** True for the uchar / uchar2 / uchar3 / uchar4 family the kernels expect. */
    static boolean isU8(Element e) {
        return e.mType == Element.DataType.UNSIGNED_8;
    }

    static boolean isF32(Element e) {
        return e.mType == Element.DataType.FLOAT_32;
    }

    /**
     * Resolves a LaunchOptions window against an Allocation's real dimensions.
     *
     * @return {x0, x1, y0, y1, z0, z1}, half open. A zero end means "to the
     *         edge", which is how the driver reads an unset field.
     */
    static int[] bounds(Allocation a, Script.LaunchOptions opt) {
        int dimX = a.mCurrentDimX;
        int dimY = Math.max(1, a.mCurrentDimY);
        int dimZ = Math.max(1, a.mCurrentDimZ);
        int x0 = 0;
        int x1 = dimX;
        int y0 = 0;
        int y1 = dimY;
        int z0 = 0;
        int z1 = dimZ;
        if (opt != null) {
            if (opt.getXEnd() > 0) {
                x0 = Math.max(0, opt.getXStart());
                x1 = Math.min(dimX, opt.getXEnd());
            }
            if (opt.getYEnd() > 0) {
                y0 = Math.max(0, opt.getYStart());
                y1 = Math.min(dimY, opt.getYEnd());
            }
            if (opt.getZEnd() > 0) {
                z0 = Math.max(0, opt.getZStart());
                z1 = Math.min(dimZ, opt.getZEnd());
            }
        }
        return new int[] {x0, x1, y0, y1, z0, z1};
    }

    /** Both Allocations must describe the same grid for a paired kernel. */
    static void requireSameDims(Allocation ain, Allocation aout) {
        if (ain.mCurrentDimX != aout.mCurrentDimX
                || ain.mCurrentDimY != aout.mCurrentDimY
                || ain.mCurrentDimZ != aout.mCurrentDimZ) {
            throw new RSIllegalArgumentException(
                    "Input and output Allocation dimensions must match.");
        }
    }

    /**
     * PREMULTIPLICATION, and why some kernels here do it when AOSP's do not.
     *
     * On a device an Allocation created from a Bitmap holds the Bitmap's own
     * bytes, and an ARGB_8888 Bitmap is stored PREMULTIPLIED. So the driver's
     * blur and Porter-Duff blend operate on premultiplied colour without ever
     * saying so.
     *
     * Mac.apk's Bitmap is non-premultiplied end to end (see the note in
     * android.graphics.Bitmap), so an Allocation built from one holds straight
     * colour. Running the same formulas on straight colour is visibly wrong:
     * a blur bleeds the colour of fully transparent pixels into their opaque
     * neighbours as a dark or white halo, and SRC_OVER stops being SRC_OVER.
     * The kernels that need it therefore premultiply on the way in and undo it
     * on the way out, which reproduces the DEVICE'S OUTPUT rather than the
     * device's intermediate representation.
     */
    static void premultiply(float[] c) {
        final float m = c[3] / 255f;
        c[0] *= m;
        c[1] *= m;
        c[2] *= m;
    }

    static void unpremultiply(float[] c) {
        if (c[3] <= 0f) {
            c[0] = 0f;
            c[1] = 0f;
            c[2] = 0f;
            return;
        }
        final float inv = 255f / c[3];
        c[0] *= inv;
        c[1] *= inv;
        c[2] *= inv;
    }

    /**
     * Shared (2r+1)x(2r+1) convolution for ScriptIntrinsicConvolve3x3 and 5x5.
     *
     * Taps outside the image CLAMP to the nearest border pixel, matching
     * rsCpuIntrinsicConvolve*.cpp's rsMax(x-1, 0) / rsMin(x+1, dim-1). The
     * uchar path rounds by adding 0.5 and clamping to [0, 255] before
     * truncating; the float path does neither, so a float result may leave the
     * 0..1 range, which is the documented platform behaviour.
     *
     * The source is snapshot into planar floats first, so an in-place call
     * (ain == aout) still reads unmodified neighbours instead of half-written
     * ones.
     */
    static void convolve(Allocation ain, Allocation aout, float[] coeff, int r,
                         Script.LaunchOptions opt) {
        final Element ie = ain.getElement();
        final Element oe = aout.getElement();
        final int nin = componentCount(ie);
        final int nout = componentCount(oe);
        final boolean floatOut = isF32(oe);
        final int w = ain.mCurrentDimX;
        final int h = Math.max(1, ain.mCurrentDimY);

        final ByteBuffer sb = bufferOf(ain);
        final ByteBuffer db = bufferOf(aout);
        final int sCell = ie.getBytesSize();
        final int dCell = oe.getBytesSize();
        final int sBase = ain.baseOffset();
        final int dBase = aout.baseOffset();

        final int sStride = ain.rowStride();
        final int dStride = aout.rowStride();
        final float[][] p = new float[nin][w * h];
        final float[] cell = new float[4];
        for (int y = 0; y < h; y++) {
            int pos = sBase + y * sStride * sCell;
            for (int x = 0; x < w; x++, pos += sCell) {
                readCell(sb, pos, ie, cell);
                int i = y * w + x;
                for (int c = 0; c < nin; c++) {
                    p[c][i] = cell[c];
                }
            }
        }

        final int side = 2 * r + 1;
        final int[] b = bounds(aout, opt);
        for (int y = b[2]; y < b[3]; y++) {
            for (int x = b[0]; x < b[1]; x++) {
                for (int c = 0; c < nin; c++) {
                    float s = 0f;
                    for (int ky = 0; ky < side; ky++) {
                        int sy = y + ky - r;
                        if (sy < 0) {
                            sy = 0;
                        } else if (sy >= h) {
                            sy = h - 1;
                        }
                        final int row = sy * w;
                        for (int kx = 0; kx < side; kx++) {
                            int sx = x + kx - r;
                            if (sx < 0) {
                                sx = 0;
                            } else if (sx >= w) {
                                sx = w - 1;
                            }
                            s += p[c][row + sx] * coeff[ky * side + kx];
                        }
                    }
                    cell[c] = s;
                }
                for (int c = nin; c < 4; c++) {
                    cell[c] = 0f;
                }
                final int dp = dBase + (y * dStride + x) * dCell;
                if (floatOut) {
                    writeCell(db, dp, oe, cell);
                } else {
                    for (int c = 0; c < nout; c++) {
                        db.put(dp + c, (byte) (int) clamp(cell[c] + 0.5f, 0f, 255f));
                    }
                }
            }
        }
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
