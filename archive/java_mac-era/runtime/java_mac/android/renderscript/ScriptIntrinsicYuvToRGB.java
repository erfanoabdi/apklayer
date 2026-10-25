package android.renderscript;

import java.nio.ByteBuffer;

/**
 * Converts a YUV 4:2:0 Allocation (camera preview frames, video decoder output)
 * into RGBA.
 *
 * Real implementation, using the platform's exact integer coefficients:
 *
 *   Y' = Y - 16, U' = U - 128, V' = V - 128
 *   R = (298 Y' + 409 V'            + 128) >> 8
 *   G = (298 Y' - 100 U' - 208 V'   + 128) >> 8
 *   B = (298 Y' + 516 U'            + 128) >> 8
 *   A = 255
 *
 * That is BT.601 with studio-swing (16..235) luma, which is what camera
 * hardware produces. Using the full-range or BT.709 coefficients instead
 * gives a picture that is subtly washed out or wrongly tinted rather than
 * obviously broken, so the constants are worth guarding.
 *
 * PLANE LAYOUT, and the one place this is an approximation. The input
 * Allocation is a U8 Type carrying a full-resolution Y plane followed by
 * half-resolution chroma:
 *
 *   NV21 and the legacy "no yuv format" case: Y, then INTERLEAVED V,U pairs.
 *   YV12: Y, then a whole V plane, then a whole U plane.
 *   YUV_420_888: treated as NV21, which is how the platform's own legacy path
 *                reads an Allocation with no separate plane pointers.
 *
 * A device driver rounds each chroma row's stride up to a 16-byte boundary for
 * YV12; Mac.apk packs the planes tightly instead, because our Allocation is
 * filled by the app's own copyFrom(byte[]) and camera NV21 buffers are tightly
 * packed. For a YV12 source whose width is not a multiple of 32 this will
 * differ from a device. Flagged rather than hidden.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsicYuvToRGB.java
 *         frameworks/rs/cpu_ref/rsCpuIntrinsicYuvToRGB.cpp
 */
public final class ScriptIntrinsicYuvToRGB extends ScriptIntrinsic {

    private Allocation mInput;

    private ScriptIntrinsicYuvToRGB(RenderScript rs) {
        super(rs);
    }

    /** @param e the OUTPUT element; must be compatible with U8_4. */
    public static ScriptIntrinsicYuvToRGB create(RenderScript rs, Element e) {
        return new ScriptIntrinsicYuvToRGB(rs);
    }

    /** @param ain a U8 Allocation, normally built with Type.Builder.setYuvFormat. */
    public void setInput(Allocation ain) {
        if (ain != null && !IntrinsicSupport.isU8(ain.getElement())) {
            throw new RSIllegalArgumentException("Element must be U8.");
        }
        mInput = ain;
    }

    public void forEach(Allocation aout) {
        if (mInput == null) {
            throw new RSIllegalArgumentException("YuvToRGB executed without an input.");
        }
        if (aout == null) {
            throw new RSIllegalArgumentException("YuvToRGB executed without an output.");
        }
        if (mRS != null && !aout.getElement().isCompatible(Element.U8_4(mRS))) {
            throw new RSIllegalArgumentException("Output must be compatible with uchar4.");
        }

        final int w = aout.mCurrentDimX;
        final int h = Math.max(1, aout.mCurrentDimY);
        final ByteBuffer src = IntrinsicSupport.bufferOf(mInput);
        final ByteBuffer dst = IntrinsicSupport.bufferOf(aout);
        final int sBase = mInput.baseOffset();
        final int dBase = aout.baseOffset();
        final int dCell = aout.getElement().getBytesSize();
        final int dRow = aout.rowStride();

        final int yStride = w;
        final int ySize = yStride * h;
        final int cw = (w + 1) / 2;
        final int ch = (h + 1) / 2;

        final int format = mInput.getType().getYuv();
        final boolean planar = format == android.graphics.ImageFormat.YV12;

        // NV21 stores the pair as V then U, so V is the even byte.
        final int vBase = sBase + ySize;
        final int uBase = planar ? sBase + ySize + cw * ch : vBase + 1;
        final int cStep = planar ? 1 : 2;
        // The interleaved chroma block uses the LUMA stride (the driver sets
        // lod[1].stride = lod[0].stride for NV21), which differs from cw * 2
        // when the width is odd.
        final int cStride = planar ? cw : yStride;

        for (int y = 0; y < h; y++) {
            final int yRow = sBase + y * yStride;
            final int cRow = (y >> 1) * cStride;
            int dp = dBase + y * dRow * dCell;
            for (int x = 0; x < w; x++, dp += dCell) {
                final int cx = (x >> 1) * cStep;
                final int yy = (src.get(yRow + x) & 0xff) - 16;
                final int uu = (src.get(uBase + cRow + cx) & 0xff) - 128;
                final int vv = (src.get(vBase + cRow + cx) & 0xff) - 128;

                int r = (yy * 298 + vv * 409 + 128) >> 8;
                int g = (yy * 298 - uu * 100 - vv * 208 + 128) >> 8;
                int b = (yy * 298 + uu * 516 + 128) >> 8;

                dst.put(dp, (byte) clamp(r));
                dst.put(dp + 1, (byte) clamp(g));
                dst.put(dp + 2, (byte) clamp(b));
                dst.put(dp + 3, (byte) 255);
            }
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 2, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(0, null);
    }
}
