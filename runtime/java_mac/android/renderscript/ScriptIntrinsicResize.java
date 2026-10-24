package android.renderscript;

import java.nio.ByteBuffer;

/**
 * Bicubic resampler: scales the input Allocation to the output Allocation's
 * dimensions.
 *
 * Real implementation, ported from AOSP's CPU reference kernel. The sample
 * grid is the PIXEL-CENTRE mapping
 *
 *     xf = (x + 0.5) * srcWidth / dstWidth - 0.5
 *
 * not the naive x * srcWidth / dstWidth, which is half a destination pixel off
 * and shows up as a visible drift toward one corner on a large upscale.
 *
 * The interpolant is the Catmull-Rom form the platform uses:
 *
 *   p1 + 0.5 x (p2 - p0 + x (2 p0 - 5 p1 + 4 p2 - p3 + x (3 (p1 - p2) + p3 - p0)))
 *
 * Taps outside the image CLAMP to the border. The uchar path adds 0.5 and
 * clamps to [0, 255]; a float path does neither, and Catmull-Rom OVERSHOOTS by
 * design, so a float output legitimately leaves the input's range near a hard
 * edge.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsicResize.java
 *         frameworks/rs/cpu_ref/rsCpuIntrinsicResize.cpp
 */
public final class ScriptIntrinsicResize extends ScriptIntrinsic {

    private Allocation mInput;

    private ScriptIntrinsicResize(RenderScript rs) {
        super(rs);
    }

    public static ScriptIntrinsicResize create(RenderScript rs) {
        return new ScriptIntrinsicResize(rs);
    }

    /** @param ain a 2D Allocation of a uchar or float Element. */
    public void setInput(Allocation ain) {
        if (ain != null) {
            Element e = ain.getElement();
            if (e.mType != Element.DataType.UNSIGNED_8 && e.mType != Element.DataType.FLOAT_32) {
                throw new RSIllegalArgumentException("Unsupported element type.");
            }
            if (ain.getType().getY() == 0 || ain.getType().getZ() != 0) {
                throw new RSIllegalArgumentException("Resize requires a 2D input Allocation.");
            }
        }
        mInput = ain;
    }

    public void forEach_bicubic(Allocation aout) {
        forEach_bicubic(aout, null);
    }

    public void forEach_bicubic(Allocation aout, Script.LaunchOptions opt) {
        if (mInput == null) {
            throw new RSIllegalArgumentException("Set input before calling forEach.");
        }
        if (aout == null) {
            throw new RSIllegalArgumentException("Resize executed without an output.");
        }
        final Element ie = mInput.getElement();
        final Element oe = aout.getElement();
        if (ie.mType != oe.mType || ie.getVectorSize() != oe.getVectorSize()) {
            throw new RSIllegalArgumentException(
                    "Input and output Element types must match.");
        }

        final int sw = mInput.mCurrentDimX;
        final int sh = Math.max(1, mInput.mCurrentDimY);
        final int dw = aout.mCurrentDimX;
        final int dh = Math.max(1, aout.mCurrentDimY);
        final float scaleX = (float) sw / dw;
        final float scaleY = (float) sh / dh;
        final int n = IntrinsicSupport.componentCount(ie);
        final boolean floatOut = IntrinsicSupport.isF32(oe);

        final ByteBuffer sb = IntrinsicSupport.bufferOf(mInput);
        final ByteBuffer db = IntrinsicSupport.bufferOf(aout);
        final int sCell = ie.getBytesSize();
        final int dCell = oe.getBytesSize();
        final int sBase = mInput.baseOffset();
        final int dBase = aout.baseOffset();
        final int sRow = mInput.rowStride();
        final int dRow = aout.rowStride();

        // Snapshot the source planarly: each output pixel touches 16 source
        // cells, so decoding on the fly would re-unpack every cell 16 times.
        final float[][] p = new float[n][sw * sh];
        final float[] cell = new float[4];
        for (int y = 0; y < sh; y++) {
            int pos = sBase + y * sRow * sCell;
            for (int x = 0; x < sw; x++, pos += sCell) {
                IntrinsicSupport.readCell(sb, pos, ie, cell);
                int i = y * sw + x;
                for (int c = 0; c < n; c++) {
                    p[c][i] = cell[c];
                }
            }
        }

        final int[] b = IntrinsicSupport.bounds(aout, opt);
        final float[] col = new float[4];
        for (int y = b[2]; y < b[3]; y++) {
            float yf = (y + 0.5f) * scaleY - 0.5f;
            final int starty = (int) Math.floor(yf - 1);
            final float fy = yf - (float) Math.floor(yf);
            final int ys0 = Math.max(0, starty);
            final int ys1 = Math.max(0, starty + 1);
            final int ys2 = Math.min(sh - 1, starty + 2);
            final int ys3 = Math.min(sh - 1, starty + 3);

            for (int x = b[0]; x < b[1]; x++) {
                float xf = (x + 0.5f) * scaleX - 0.5f;
                final int startx = (int) Math.floor(xf - 1);
                final float fx = xf - (float) Math.floor(xf);
                final int xs0 = Math.max(0, startx);
                final int xs1 = Math.max(0, startx + 1);
                final int xs2 = Math.min(sw - 1, startx + 2);
                final int xs3 = Math.min(sw - 1, startx + 3);

                for (int c = 0; c < n; c++) {
                    final float[] pc = p[c];
                    float r0 = cubic(pc[ys0 * sw + xs0], pc[ys0 * sw + xs1],
                                     pc[ys0 * sw + xs2], pc[ys0 * sw + xs3], fx);
                    float r1 = cubic(pc[ys1 * sw + xs0], pc[ys1 * sw + xs1],
                                     pc[ys1 * sw + xs2], pc[ys1 * sw + xs3], fx);
                    float r2 = cubic(pc[ys2 * sw + xs0], pc[ys2 * sw + xs1],
                                     pc[ys2 * sw + xs2], pc[ys2 * sw + xs3], fx);
                    float r3 = cubic(pc[ys3 * sw + xs0], pc[ys3 * sw + xs1],
                                     pc[ys3 * sw + xs2], pc[ys3 * sw + xs3], fx);
                    col[c] = cubic(r0, r1, r2, r3, fy);
                }
                for (int c = n; c < 4; c++) {
                    col[c] = 0f;
                }

                final int dp = dBase + (y * dRow + x) * dCell;
                if (floatOut) {
                    IntrinsicSupport.writeCell(db, dp, oe, col);
                } else {
                    for (int c = 0; c < n; c++) {
                        db.put(dp + c,
                                (byte) (int) IntrinsicSupport.clamp(col[c] + 0.5f, 0f, 255f));
                    }
                }
            }
        }
    }

    private static float cubic(float p0, float p1, float p2, float p3, float x) {
        return p1 + 0.5f * x * (p2 - p0 + x * (2.f * p0 - 5.f * p1 + 4.f * p2 - p3
                + x * (3.f * (p1 - p2) + p3 - p0)));
    }

    public Script.KernelID getKernelID_bicubic() {
        return createKernelID(0, 2, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(0, null);
    }
}
