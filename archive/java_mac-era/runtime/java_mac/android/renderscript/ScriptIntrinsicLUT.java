package android.renderscript;

import java.nio.ByteBuffer;

/**
 * Per-channel 256-entry lookup table over a uchar4 Allocation: the standard way
 * to apply a tone curve, invert a channel, or posterise.
 *
 * Four independent tables, one per channel, each defaulting to identity. The
 * tables live in one 1024-byte block (red 0..255, green 256..511, blue
 * 512..767, alpha 768..1023) exactly as AOSP lays them out, so the offsets
 * below are the platform's and not an invention.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsicLUT.java
 *         frameworks/rs/cpu_ref/rsCpuIntrinsicLUT.cpp
 */
public final class ScriptIntrinsicLUT extends ScriptIntrinsic {

    private final byte[] mCache = new byte[1024];

    private ScriptIntrinsicLUT(RenderScript rs) {
        super(rs);
        for (int ct = 0; ct < 256; ct++) {
            mCache[ct] = (byte) ct;
            mCache[ct + 256] = (byte) ct;
            mCache[ct + 512] = (byte) ct;
            mCache[ct + 768] = (byte) ct;
        }
    }

    /** @param e must be compatible with U8_4. Tables start out identity. */
    public static ScriptIntrinsicLUT create(RenderScript rs, Element e) {
        return new ScriptIntrinsicLUT(rs);
    }

    private static void validate(int index, int value) {
        if (index < 0 || index > 255) {
            throw new RSIllegalArgumentException("Index out of range (0-255).");
        }
        if (value < 0 || value > 255) {
            throw new RSIllegalArgumentException("Value out of range (0-255).");
        }
    }

    public void setRed(int index, int value) {
        validate(index, value);
        mCache[index] = (byte) value;
    }

    public void setGreen(int index, int value) {
        validate(index, value);
        mCache[index + 256] = (byte) value;
    }

    public void setBlue(int index, int value) {
        validate(index, value);
        mCache[index + 512] = (byte) value;
    }

    public void setAlpha(int index, int value) {
        validate(index, value);
        mCache[index + 768] = (byte) value;
    }

    public void forEach(Allocation ain, Allocation aout) {
        forEach(ain, aout, null);
    }

    public void forEach(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        if (ain == null || aout == null) {
            throw new RSIllegalArgumentException("LUT requires both allocations.");
        }
        if (mRS != null && (!ain.getElement().isCompatible(Element.U8_4(mRS))
                || !aout.getElement().isCompatible(Element.U8_4(mRS)))) {
            throw new RSIllegalArgumentException("Element must be compatible with uchar4.");
        }
        IntrinsicSupport.requireSameDims(ain, aout);

        final ByteBuffer sb = IntrinsicSupport.bufferOf(ain);
        final ByteBuffer db = IntrinsicSupport.bufferOf(aout);
        final int sCell = ain.getElement().getBytesSize();
        final int dCell = aout.getElement().getBytesSize();
        final int sBase = ain.baseOffset();
        final int dBase = aout.baseOffset();
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
                    db.put(dp, mCache[sb.get(sp) & 0xff]);
                    db.put(dp + 1, mCache[(sb.get(sp + 1) & 0xff) + 256]);
                    db.put(dp + 2, mCache[(sb.get(sp + 2) & 0xff) + 512]);
                    db.put(dp + 3, mCache[(sb.get(sp + 3) & 0xff) + 768]);
                }
            }
        }
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 3, null, null);
    }
}
