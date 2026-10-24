package android.renderscript;

import java.nio.ByteBuffer;

/**
 * The blend intrinsic: fifteen Porter-Duff and channel-wise operators over a
 * pair of U8_4 Allocations.
 *
 * Every one of them is IN PLACE ON THE DESTINATION. aout is both an input and
 * the output, which reads oddly against the rest of this package but is the
 * platform's contract: forEachSrcOver(src, dst) leaves the result in dst.
 *
 * FIDELITY NOTES.
 *
 *  - The arithmetic is the platform's integer arithmetic, including its
 *    quirks. RenderScript divides by 256 (a shift) where the algebra calls for
 *    255, so blending 255 over a transparent destination yields 254 and not
 *    255. That is what a device produces, so it is what this produces; using
 *    exact float math here would make us differ from every real Android
 *    result an app was tuned against.
 *  - The eight true alpha-compositing operators run on PREMULTIPLIED colour,
 *    which is the representation their formulas assume. A device gets that for
 *    free because its Allocation holds premultiplied Bitmap bytes; Mac.apk's
 *    Bitmap is non-premultiplied end to end, so the conversion happens around
 *    the operator. For fully opaque pixels (the common case) the round trip is
 *    exact and changes nothing. See IntrinsicSupport's note.
 *  - CLEAR, SRC, DST, XOR, MULTIPLY, ADD and SUBTRACT are channel-wise, not
 *    alpha-compositing, so they are applied to the stored bytes verbatim.
 *
 * The dozens of Photoshop-style modes in the native enum (NORMAL, SCREEN,
 * OVERLAY, HUE, ...) have no public Java entry point on any Android release,
 * and the driver asserts if reached, so there is nothing here to mirror.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptIntrinsicBlend.java
 *         frameworks/rs/cpu_ref/rsCpuIntrinsicBlend.cpp
 */
public class ScriptIntrinsicBlend extends ScriptIntrinsic {

    private ScriptIntrinsicBlend(RenderScript rs) {
        super(rs);
    }

    /**
     * @param e element type, ignored since API 19; the real check happens per
     *          launch, against the Allocations.
     */
    public static ScriptIntrinsicBlend create(RenderScript rs, Element e) {
        return new ScriptIntrinsicBlend(rs);
    }

    private void blend(int id, Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        if (ain == null || aout == null) {
            throw new RSIllegalArgumentException("Blend requires both allocations.");
        }
        if (mRS != null && !ain.getElement().isCompatible(Element.U8_4(mRS))) {
            throw new RSIllegalArgumentException("Input is not of expected format.");
        }
        if (mRS != null && !aout.getElement().isCompatible(Element.U8_4(mRS))) {
            throw new RSIllegalArgumentException("Output is not of expected format.");
        }
        IntrinsicSupport.requireSameDims(ain, aout);
        run(id, ain, aout, opt);
    }

    /** True for the operators whose formula is written for premultiplied colour. */
    private static boolean isAlphaComposite(int id) {
        switch (id) {
            case 3:  // SRC_OVER
            case 4:  // DST_OVER
            case 5:  // SRC_IN
            case 6:  // DST_IN
            case 7:  // SRC_OUT
            case 8:  // DST_OUT
            case 9:  // SRC_ATOP
            case 10: // DST_ATOP
                return true;
            default:
                return false;
        }
    }

    private static void run(int id, Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        if (id == 2) {
            // DST leaves the destination exactly as it was.
            return;
        }
        final ByteBuffer sb = IntrinsicSupport.bufferOf(ain);
        final ByteBuffer db = IntrinsicSupport.bufferOf(aout);
        final Element se = ain.getElement();
        final Element de = aout.getElement();
        final int sCell = se.getBytesSize();
        final int dCell = de.getBytesSize();
        final int sBase = ain.baseOffset();
        final int dBase = aout.baseOffset();
        final int sRow = ain.rowStride();
        final int dRow = aout.rowStride();
        final int sPlane = ain.planeStride();
        final int dPlane = aout.planeStride();
        final int[] b = IntrinsicSupport.bounds(aout, opt);
        final boolean premul = isAlphaComposite(id);

        final float[] s = new float[4];
        final float[] d = new float[4];
        final int[] si = new int[4];
        final int[] di = new int[4];

        for (int z = b[4]; z < b[5]; z++) {
            for (int y = b[2]; y < b[3]; y++) {
                int sp = sBase + (z * sPlane + y * sRow + b[0]) * sCell;
                int dp = dBase + (z * dPlane + y * dRow + b[0]) * dCell;
                for (int x = b[0]; x < b[1]; x++, sp += sCell, dp += dCell) {
                    IntrinsicSupport.readCell(sb, sp, se, s);
                    IntrinsicSupport.readCell(db, dp, de, d);
                    if (premul) {
                        IntrinsicSupport.premultiply(s);
                        IntrinsicSupport.premultiply(d);
                    }
                    for (int c = 0; c < 4; c++) {
                        si[c] = (int) (s[c] + 0.5f);
                        di[c] = (int) (d[c] + 0.5f);
                    }
                    apply(id, si, di);
                    for (int c = 0; c < 4; c++) {
                        d[c] = di[c];
                    }
                    if (premul) {
                        IntrinsicSupport.unpremultiply(d);
                    }
                    IntrinsicSupport.writeCell(db, dp, de, d);
                }
            }
        }
    }

    /**
     * One pixel, integer domain, result left in dst[].
     *
     * The shifts are the platform's, not a rounding shortcut of ours: see the
     * fidelity note in the class comment.
     */
    private static void apply(int id, int[] src, int[] dst) {
        switch (id) {
            case 0: // CLEAR
                dst[0] = 0;
                dst[1] = 0;
                dst[2] = 0;
                dst[3] = 0;
                break;
            case 1: // SRC
                System.arraycopy(src, 0, dst, 0, 4);
                break;
            case 3: // SRC_OVER
                for (int c = 0; c < 4; c++) {
                    dst[c] = clip(src[c] + ((dst[c] * (255 - src[3])) >> 8));
                }
                break;
            case 4: // DST_OVER
                for (int c = 0; c < 4; c++) {
                    dst[c] = clip(dst[c] + ((src[c] * (255 - dst[3])) >> 8));
                }
                break;
            case 5: { // SRC_IN
                int da = dst[3];
                for (int c = 0; c < 4; c++) {
                    dst[c] = clip((src[c] * da) >> 8);
                }
                break;
            }
            case 6: { // DST_IN
                int sa = src[3];
                for (int c = 0; c < 4; c++) {
                    dst[c] = clip((dst[c] * sa) >> 8);
                }
                break;
            }
            case 7: { // SRC_OUT
                int inv = 255 - dst[3];
                for (int c = 0; c < 4; c++) {
                    dst[c] = clip((src[c] * inv) >> 8);
                }
                break;
            }
            case 8: { // DST_OUT
                int inv = 255 - src[3];
                for (int c = 0; c < 4; c++) {
                    dst[c] = clip((dst[c] * inv) >> 8);
                }
                break;
            }
            case 9: { // SRC_ATOP, alpha untouched
                int da = dst[3];
                int inv = 255 - src[3];
                for (int c = 0; c < 3; c++) {
                    dst[c] = clip(((src[c] * da) + (dst[c] * inv)) >> 8);
                }
                break;
            }
            case 10: { // DST_ATOP, alpha becomes the source's
                int sa = src[3];
                int inv = 255 - dst[3];
                for (int c = 0; c < 3; c++) {
                    dst[c] = clip(((dst[c] * sa) + (src[c] * inv)) >> 8);
                }
                dst[3] = src[3];
                break;
            }
            case 11: // XOR, literally bitwise
                for (int c = 0; c < 4; c++) {
                    dst[c] = (src[c] ^ dst[c]) & 0xff;
                }
                break;
            case 14: // MULTIPLY
                for (int c = 0; c < 4; c++) {
                    dst[c] = clip((src[c] * dst[c]) >> 8);
                }
                break;
            case 34: // ADD
                for (int c = 0; c < 4; c++) {
                    dst[c] = Math.min(255, dst[c] + src[c]);
                }
                break;
            case 35: // SUBTRACT
                for (int c = 0; c < 4; c++) {
                    dst[c] = Math.max(0, dst[c] - src[c]);
                }
                break;
            default:
                throw new RSRuntimeException("Unsupported blend mode " + id);
        }
    }

    private static int clip(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** Sets dst = {0, 0, 0, 0}. */
    public void forEachClear(Allocation ain, Allocation aout) {
        forEachClear(ain, aout, null);
    }

    /** Sets dst = {0, 0, 0, 0}. */
    public void forEachClear(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(0, ain, aout, opt);
    }

    public Script.KernelID getKernelIDClear() {
        return createKernelID(0, 3, null, null);
    }

    /** Sets dst = src. */
    public void forEachSrc(Allocation ain, Allocation aout) {
        forEachSrc(ain, aout, null);
    }

    /** Sets dst = src. */
    public void forEachSrc(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(1, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrc() {
        return createKernelID(1, 3, null, null);
    }

    /** Sets dst = dst. This is a no-op by definition, but the Element checks still run so a caller passing the wrong format finds out. */
    public void forEachDst(Allocation ain, Allocation aout) {
        forEachDst(ain, aout, null);
    }

    /** Sets dst = dst. This is a no-op by definition, but the Element checks still run so a caller passing the wrong format finds out. */
    public void forEachDst(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(2, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDst() {
        return createKernelID(2, 3, null, null);
    }

    /** Sets dst = src + dst * (1.0 - src.a). */
    public void forEachSrcOver(Allocation ain, Allocation aout) {
        forEachSrcOver(ain, aout, null);
    }

    /** Sets dst = src + dst * (1.0 - src.a). */
    public void forEachSrcOver(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(3, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrcOver() {
        return createKernelID(3, 3, null, null);
    }

    /** Sets dst = dst + src * (1.0 - dst.a). */
    public void forEachDstOver(Allocation ain, Allocation aout) {
        forEachDstOver(ain, aout, null);
    }

    /** Sets dst = dst + src * (1.0 - dst.a). */
    public void forEachDstOver(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(4, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDstOver() {
        return createKernelID(4, 3, null, null);
    }

    /** Sets dst = src * dst.a. */
    public void forEachSrcIn(Allocation ain, Allocation aout) {
        forEachSrcIn(ain, aout, null);
    }

    /** Sets dst = src * dst.a. */
    public void forEachSrcIn(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(5, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrcIn() {
        return createKernelID(5, 3, null, null);
    }

    /** Sets dst = dst * src.a. */
    public void forEachDstIn(Allocation ain, Allocation aout) {
        forEachDstIn(ain, aout, null);
    }

    /** Sets dst = dst * src.a. */
    public void forEachDstIn(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(6, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDstIn() {
        return createKernelID(6, 3, null, null);
    }

    /** Sets dst = src * (1.0 - dst.a). */
    public void forEachSrcOut(Allocation ain, Allocation aout) {
        forEachSrcOut(ain, aout, null);
    }

    /** Sets dst = src * (1.0 - dst.a). */
    public void forEachSrcOut(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(7, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrcOut() {
        return createKernelID(7, 3, null, null);
    }

    /** Sets dst = dst * (1.0 - src.a). */
    public void forEachDstOut(Allocation ain, Allocation aout) {
        forEachDstOut(ain, aout, null);
    }

    /** Sets dst = dst * (1.0 - src.a). */
    public void forEachDstOut(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(8, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDstOut() {
        return createKernelID(8, 3, null, null);
    }

    /** Sets dst.rgb = src.rgb * dst.a + dst.rgb * (1.0 - src.a), leaving dst.a alone. */
    public void forEachSrcAtop(Allocation ain, Allocation aout) {
        forEachSrcAtop(ain, aout, null);
    }

    /** Sets dst.rgb = src.rgb * dst.a + dst.rgb * (1.0 - src.a), leaving dst.a alone. */
    public void forEachSrcAtop(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(9, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrcAtop() {
        return createKernelID(9, 3, null, null);
    }

    /** Sets dst.rgb = dst.rgb * src.a + src.rgb * (1.0 - dst.a) and dst.a = src.a. */
    public void forEachDstAtop(Allocation ain, Allocation aout) {
        forEachDstAtop(ain, aout, null);
    }

    /** Sets dst.rgb = dst.rgb * src.a + src.rgb * (1.0 - dst.a) and dst.a = src.a. */
    public void forEachDstAtop(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(10, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDstAtop() {
        return createKernelID(10, 3, null, null);
    }

    /** Sets dst = {src.r ^ dst.r, src.g ^ dst.g, src.b ^ dst.b, src.a ^ dst.a}. A literal bitwise xor, not an alpha-compositing operator. */
    public void forEachXor(Allocation ain, Allocation aout) {
        forEachXor(ain, aout, null);
    }

    /** Sets dst = {src.r ^ dst.r, src.g ^ dst.g, src.b ^ dst.b, src.a ^ dst.a}. A literal bitwise xor, not an alpha-compositing operator. */
    public void forEachXor(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(11, ain, aout, opt);
    }

    public Script.KernelID getKernelIDXor() {
        return createKernelID(11, 3, null, null);
    }

    /** Sets dst = src * dst. */
    public void forEachMultiply(Allocation ain, Allocation aout) {
        forEachMultiply(ain, aout, null);
    }

    /** Sets dst = src * dst. */
    public void forEachMultiply(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(14, ain, aout, opt);
    }

    public Script.KernelID getKernelIDMultiply() {
        return createKernelID(14, 3, null, null);
    }

    /** Sets dst = min(src + dst, 255). */
    public void forEachAdd(Allocation ain, Allocation aout) {
        forEachAdd(ain, aout, null);
    }

    /** Sets dst = min(src + dst, 255). */
    public void forEachAdd(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(34, ain, aout, opt);
    }

    public Script.KernelID getKernelIDAdd() {
        return createKernelID(34, 3, null, null);
    }

    /** Sets dst = max(dst - src, 0). */
    public void forEachSubtract(Allocation ain, Allocation aout) {
        forEachSubtract(ain, aout, null);
    }

    /** Sets dst = max(dst - src, 0). */
    public void forEachSubtract(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(35, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSubtract() {
        return createKernelID(35, 3, null, null);
    }
}
