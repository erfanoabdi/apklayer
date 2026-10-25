package android.renderscript;

import java.nio.ByteBuffer;

/**
 * Applies a 3D colour lookup table with trilinear interpolation: the "film
 * emulation LUT" / colour-grading intrinsic.
 *
 * The LUT is a 3D uchar4 Allocation indexed by the input's r, g and b. Its
 * dimensions need not be 256 cubed; a 17x17x17 or 33x33x33 cube is normal, and
 * the input is scaled into it. The alpha channel is passed through untouched,
 * which is what the driver does.
 *
 * The interpolation below is the platform's FIXED POINT arithmetic rather than
 * a float rewrite, because the shift sequence (>>7, >>15, >>15, then
 * (v + 0x7f) >> 8) is where its rounding lives, and a float version rounds
 * differently in the last bit.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsic3DLUT.java
 *         frameworks/rs/cpu_ref/rsCpuIntrinsic3DLUT.cpp
 */
public final class ScriptIntrinsic3DLUT extends ScriptIntrinsic {

    private Allocation mLUT;
    private final Element mElement;

    private ScriptIntrinsic3DLUT(RenderScript rs, Element e) {
        super(rs);
        mElement = e;
    }

    /** @param e must be compatible with U8_4. */
    public static ScriptIntrinsic3DLUT create(RenderScript rs, Element e) {
        if (rs != null && e != null && !e.isCompatible(Element.U8_4(rs))) {
            throw new RSIllegalArgumentException("Element must be compatible with uchar4.");
        }
        return new ScriptIntrinsic3DLUT(rs, e);
    }

    public void setLUT(Allocation lut) {
        final Type t = lut.getType();
        if (t.getZ() == 0) {
            throw new RSIllegalArgumentException("LUT must be 3d.");
        }
        if (mElement != null && !t.getElement().isCompatible(mElement)) {
            throw new RSIllegalArgumentException("LUT element type must match.");
        }
        mLUT = lut;
    }

    public void forEach(Allocation ain, Allocation aout) {
        forEach(ain, aout, null);
    }

    public void forEach(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        if (mLUT == null) {
            throw new RSIllegalArgumentException("3DLUT executed without a LUT.");
        }
        if (ain == null || aout == null) {
            throw new RSIllegalArgumentException("3DLUT requires both allocations.");
        }
        IntrinsicSupport.requireSameDims(ain, aout);

        final ByteBuffer sb = IntrinsicSupport.bufferOf(ain);
        final ByteBuffer db = IntrinsicSupport.bufferOf(aout);
        final ByteBuffer lb = IntrinsicSupport.bufferOf(mLUT);
        final int sCell = ain.getElement().getBytesSize();
        final int dCell = aout.getElement().getBytesSize();
        final int lCell = mLUT.getElement().getBytesSize();
        final int sBase = ain.baseOffset();
        final int dBase = aout.baseOffset();
        final int lBase = mLUT.baseOffset();

        final int lx = mLUT.getType().getX();
        final int ly = Math.max(1, mLUT.getType().getY());
        final int strideY = lx * lCell;
        final int strideZ = strideY * ly;

        // dims are the LAST valid index on each axis, so a 17-cube maps 0..255
        // onto 0..16 and the +1 neighbour of the top cell stays in range.
        final int[] dims = {lx - 1, ly - 1, Math.max(1, mLUT.getType().getZ()) - 1};
        final int[] coordMul = new int[3];
        for (int i = 0; i < 3; i++) {
            coordMul[i] = (int) ((1.f / 255.f) * dims[i] * 0x8000);
        }

        final int sRow = ain.rowStride();
        final int dRow = aout.rowStride();
        final int sPlane = ain.planeStride();
        final int dPlane = aout.planeStride();
        final int[] b = IntrinsicSupport.bounds(ain, opt);

        for (int z = b[4]; z < b[5]; z++) {
            for (int y = b[2]; y < b[3]; y++) {
                int sp = sBase + (z * sPlane + y * sRow + b[0]) * sCell;
                int dp = dBase + (z * dPlane + y * dRow + b[0]) * dCell;
                for (int x = b[0]; x < b[1]; x++, sp += sCell, dp += dCell) {
                    int ir = sb.get(sp) & 0xff;
                    int ig = sb.get(sp + 1) & 0xff;
                    int ib = sb.get(sp + 2) & 0xff;
                    int ia = sb.get(sp + 3) & 0xff;

                    int bcx = ir * coordMul[0];
                    int bcy = ig * coordMul[1];
                    int bcz = ib * coordMul[2];
                    int c1x = bcx >> 15;
                    int c1y = bcy >> 15;
                    int c1z = bcz >> 15;
                    int w2x = bcx & 0x7fff;
                    int w2y = bcy & 0x7fff;
                    int w2z = bcz & 0x7fff;
                    int w1x = 0x8000 - w2x;
                    int w1y = 0x8000 - w2y;
                    int w1z = 0x8000 - w2z;

                    // The +1 neighbour is CLAMPED to the last cell. AOSP reads
                    // it unclamped and relies on the corresponding weight being
                    // zero at the top of the range, which is true but reads one
                    // cell past the LUT on a 256-cube; in Java that is an
                    // IndexOutOfBoundsException rather than a harmless read, and
                    // clamping is numerically identical because the weight is 0.
                    final int c2x = Math.min(c1x + 1, dims[0]);
                    final int c2y = Math.min(c1y + 1, dims[1]);
                    final int c2z = Math.min(c1z + 1, dims[2]);
                    final int o1x = lBase + c1x * lCell;
                    final int o2x = lBase + c2x * lCell;
                    final int o1y = c1y * strideY;
                    final int o2y = c2y * strideY;
                    final int o1z = c1z * strideZ;
                    final int o2z = c2z * strideZ;

                    for (int c = 0; c < 3; c++) {
                        long v000 = lb.get(o1x + o1y + o1z + c) & 0xff;
                        long v100 = lb.get(o2x + o1y + o1z + c) & 0xff;
                        long v010 = lb.get(o1x + o2y + o1z + c) & 0xff;
                        long v110 = lb.get(o2x + o2y + o1z + c) & 0xff;
                        long v001 = lb.get(o1x + o1y + o2z + c) & 0xff;
                        long v101 = lb.get(o2x + o1y + o2z + c) & 0xff;
                        long v011 = lb.get(o1x + o2y + o2z + c) & 0xff;
                        long v111 = lb.get(o2x + o2y + o2z + c) & 0xff;

                        long yz00 = ((v000 * w1x) + (v100 * w2x)) >> 7;
                        long yz10 = ((v010 * w1x) + (v110 * w2x)) >> 7;
                        long yz01 = ((v001 * w1x) + (v101 * w2x)) >> 7;
                        long yz11 = ((v011 * w1x) + (v111 * w2x)) >> 7;

                        long z0 = ((yz00 * w1y) + (yz10 * w2y)) >> 15;
                        long z1 = ((yz01 * w1y) + (yz11 * w2y)) >> 15;

                        long v = ((z0 * w1z) + (z1 * w2z)) >> 15;
                        long v2 = (v + 0x7f) >> 8;
                        db.put(dp + c, (byte) v2);
                    }
                    db.put(dp + 3, (byte) ia);
                }
            }
        }
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 3, null, null);
    }
}
