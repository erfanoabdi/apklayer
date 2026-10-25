// android.graphics.NinePatch -- draws a stretchable bitmap with stretch zones
// encoded in a "np_tc" chunk (the source for NinePatchDrawable backgrounds used
// by dialogs, buttons, and many platform widgets).
//
// WHY this exists: NinePatchDrawable references NinePatch at the type level, so
// without this class any inflated 9-patch background throws NoClassDefFoundError
// before a single pixel is drawn.
//
// MACAPK_NINEPATCH_LATTICE (default ON; =0 restores the old whole-bitmap blit,
// SCALED to the destination rect, for bisecting). draw() now honours the chunk's
// stretch zones the way hwui does: NinePatchUtils::SetLatticeDivs (android14
// libs/hwui/NinePatchUtils.h:22-37) hands the chunk's xDivs/yDivs to Skia as an
// SkCanvas::Lattice, dropping a last div equal to the width/height, and
// SkLatticeIter (skia src/core/SkLatticeIter.cpp:55-168) walks it: the first
// patch is scalable iff the first div is 0, patches then alternate fixed /
// scalable, fixed patches keep their size, scalable ones share what is left in
// proportion to their source size, and when the fixed patches alone do not fit
// they are scaled down and the scalable ones get nothing. The old blit scaled the
// FIXED borders with the rest: a 101px popup_top_dark drawn 1000px wide put its
// 28px left shadow over ~277px, so every traditional dialog panel, btn_default
// button and text field frame rendered as a smeared bitmap (measured 2026-09-26,
// docs/alert-dialog-traditional-and-window-shadow_2026-09-26.md). The chunk here
// is the deserialized Res_png_9patch our Resources builds (Resources.macapk9Chunk:
// little-endian, xDivsOffset at +4, yDivsOffset at +8); colors[] (a draw hint)
// is not needed to draw correctly.
//
// Source: frameworks/base/graphics/java/android/graphics/NinePatch.java
package android.graphics;

public class NinePatch {
    private Bitmap mBitmap;
    private final byte[] mChunk;
    private String mSrcName;
    private Paint mPaint;

    public NinePatch(Bitmap bitmap, byte[] chunk) {
        this(bitmap, chunk, null);
    }

    public NinePatch(Bitmap bitmap, byte[] chunk, String srcName) {
        mBitmap = bitmap;
        mChunk = chunk;
        mSrcName = srcName;
    }

    public void draw(Canvas canvas, RectF location) {
        if (location == null) return;
        // Round the float rect to the integer Rect overload, matching AOSP's
        // eventual integer destination during raster.
        draw(canvas, new Rect((int) location.left, (int) location.top,
                (int) location.right, (int) location.bottom), mPaint);
    }

    public void draw(Canvas canvas, Rect location) {
        draw(canvas, location, mPaint);
    }

    static final boolean MACAPK_NINEPATCH_LATTICE =
        !"0".equals(System.getenv("MACAPK_NINEPATCH_LATTICE"));

    public void draw(Canvas canvas, Rect location, Paint paint) {
        if (canvas == null || mBitmap == null || location == null) return;
        if (MACAPK_NINEPATCH_LATTICE && macapkDrawLattice(canvas, location, paint)) return;
        // null src rect -> use the whole bitmap; the bitmap is scaled to fill the
        // destination (the pre-lattice behaviour, and the answer for a chunk we
        // cannot read).
        canvas.drawBitmap(mBitmap, (Rect) null, location, paint);
    }

    /** The lattice draw (see the header). False -- the caller blits the whole
     *  bitmap -- when the chunk has no divs or cannot be read. */
    private boolean macapkDrawLattice(Canvas canvas, Rect dst, Paint paint) {
        final int w = mBitmap.getWidth(), h = mBitmap.getHeight();
        if (w <= 0 || h <= 0 || dst.width() <= 0 || dst.height() <= 0) return true;
        int[][] divs = macapkDivs(mChunk);
        if (divs == null) return false;
        final int[] xDivs = macapkTrimLast(divs[0], w), yDivs = macapkTrimLast(divs[1], h);
        if (xDivs.length == 0 && yDivs.length == 0) return false;
        final int[] srcX = new int[xDivs.length + 2], srcY = new int[yDivs.length + 2];
        final float[] dstX = new float[xDivs.length + 2], dstY = new float[yDivs.length + 2];
        final int nx = macapkPoints(xDivs, 0, w, dst.left, dst.right, srcX, dstX);
        final int ny = macapkPoints(yDivs, 0, h, dst.top, dst.bottom, srcY, dstY);
        final Rect s = new Rect(), d = new Rect();
        for (int j = 0; j + 1 < ny; j++) {
            final int dt = Math.round(dstY[j]), db = Math.round(dstY[j + 1]);
            if (srcY[j + 1] <= srcY[j] || db <= dt) continue;
            for (int i = 0; i + 1 < nx; i++) {
                final int dl = Math.round(dstX[i]), dr = Math.round(dstX[i + 1]);
                if (srcX[i + 1] <= srcX[i] || dr <= dl) continue;
                s.set(srcX[i], srcY[j], srcX[i + 1], srcY[j + 1]);
                d.set(dl, dt, dr, db);
                canvas.drawBitmap(mBitmap, s, d, paint);
            }
        }
        return true;
    }

    /** SkLatticeIter's set_points for one axis: fills src/dst boundaries and
     *  returns how many there are (divs.length + 2 after the first-div rule). */
    private static int macapkPoints(int[] divs, int srcStart, int srcEnd, float dstStart, float dstEnd,
                                    int[] src, float[] dst) {
        int count = divs.length, off = 0;
        final boolean firstScalable = count > 0 && divs[0] == srcStart;
        if (firstScalable) { off = 1; count--; }
        // count_scalable_pixels
        int scalable;
        if (count == 0) {
            scalable = firstScalable ? srcEnd - srcStart : 0;
        } else {
            int i;
            if (firstScalable) { scalable = divs[off] - srcStart; i = 1; }
            else { scalable = 0; i = 0; }
            for (; i < count; i += 2) {
                final int left = divs[off + i];
                final int right = (i + 1 < count) ? divs[off + i + 1] : srcEnd;
                scalable += right - left;
            }
        }
        final int fixed = (srcEnd - srcStart) - scalable;
        final float dstLen = dstEnd - dstStart;
        final float scale = fixed <= dstLen
                ? (scalable > 0 ? (dstLen - fixed) / (float) scalable : 0f)
                : dstLen / (float) fixed;
        boolean isScalable = firstScalable;
        src[0] = srcStart;
        dst[0] = dstStart;
        for (int i = 0; i < count; i++) {
            src[i + 1] = divs[off + i];
            final int srcDelta = src[i + 1] - src[i];
            final float dstDelta = fixed <= dstLen
                    ? (isScalable ? scale * srcDelta : srcDelta)
                    : (isScalable ? 0f : scale * srcDelta);
            dst[i + 1] = dst[i] + dstDelta;
            isScalable = !isScalable;
        }
        src[count + 1] = srcEnd;
        dst[count + 1] = dstEnd;
        return count + 2;
    }

    /** NinePatchUtils::SetLatticeDivs drops a last div equal to the size. */
    private static int[] macapkTrimLast(int[] divs, int size) {
        if (divs.length > 0 && divs[divs.length - 1] == size) {
            return java.util.Arrays.copyOf(divs, divs.length - 1);
        }
        return divs;
    }

    /** {xDivs, yDivs} out of a deserialized Res_png_9patch (little-endian;
     *  numXDivs at +1, numYDivs at +2, xDivsOffset at +4, yDivsOffset at +8),
     *  or null when the chunk is not one. */
    private static int[][] macapkDivs(byte[] chunk) {
        if (chunk == null || chunk.length < 32 || chunk[0] == 0) return null;
        try {
            final java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(chunk)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            final int nx = chunk[1] & 0xff, ny = chunk[2] & 0xff;
            int xOff = bb.getInt(4), yOff = bb.getInt(8);
            if (xOff < 32 || xOff + nx * 4 > chunk.length) xOff = 32;
            if (yOff < 32 || yOff + ny * 4 > chunk.length) yOff = 32 + nx * 4;
            if (xOff + nx * 4 > chunk.length || yOff + ny * 4 > chunk.length) return null;
            final int[] x = new int[nx], y = new int[ny];
            for (int i = 0; i < nx; i++) x[i] = bb.getInt(xOff + i * 4);
            for (int i = 0; i < ny; i++) y[i] = bb.getInt(yOff + i * 4);
            return new int[][] { x, y };
        } catch (Throwable t) {
            return null;
        }
    }

    /** AOSP NinePatch.getDensity() -- the density the chunk was authored at,
     *  which AOSP reads off the backing bitmap (mBitmap.mNativeDensity). */
    public int getDensity() {
        return mBitmap == null ? Bitmap.DENSITY_NONE : mBitmap.getDensity();
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    public int getWidth() {
        return mBitmap == null ? 0 : mBitmap.getWidth();
    }

    public int getHeight() {
        return mBitmap == null ? 0 : mBitmap.getHeight();
    }

    public String getName() {
        return mSrcName;
    }

    public Paint getPaint() {
        return mPaint;
    }

    public void setPaint(Paint p) {
        mPaint = p;
    }

    public boolean hasAlpha() {
        return mBitmap != null && mBitmap.hasAlpha();
    }

    public Region getTransparentRegion(Rect bounds) {
        // We do not decode the chunk's transparency info; returning null tells
        // callers "no known transparent region", which is a safe default.
        return null;
    }

    public static boolean isNinePatchChunk(byte[] chunk) {
        // AOSP's chunk begins with a non-zero "wasDeserialized" byte; an all-zero
        // or empty chunk is not a valid 9-patch.
        return chunk != null && chunk.length > 0 && chunk[0] != 0;
    }
}
