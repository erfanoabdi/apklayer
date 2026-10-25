package android.graphics;

import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * AOSP-faithful android.graphics.BitmapRegionDecoder. On real Android this
 * decodes only the requested sub-rectangle of a (possibly huge) image without
 * loading the whole thing into memory. We have no streaming codec, so we decode
 * the full image once via BitmapFactory and crop on demand - observably
 * identical output, just without the memory win. Mirrors
 * frameworks/base/graphics/java/android/graphics/BitmapRegionDecoder.java.
 */
public final class BitmapRegionDecoder {
    private Bitmap mFull;
    private boolean mRecycled;

    private BitmapRegionDecoder(Bitmap full) {
        mFull = full;
    }

    private static BitmapRegionDecoder make(Bitmap bm) throws IOException {
        if (bm == null) throw new IOException("BitmapRegionDecoder: failed to decode source");
        return new BitmapRegionDecoder(bm);
    }

    public static BitmapRegionDecoder newInstance(byte[] data, int offset, int length)
            throws IOException {
        return make(BitmapFactory.decodeByteArray(data, offset, length));
    }

    @Deprecated
    public static BitmapRegionDecoder newInstance(byte[] data, int offset, int length,
            boolean isShareable) throws IOException {
        return newInstance(data, offset, length);
    }

    public static BitmapRegionDecoder newInstance(ParcelFileDescriptor pfd) throws IOException {
        if (pfd == null) throw new IOException("null pfd");
        return newInstance(pfd.getFileDescriptor(), false);
    }

    @Deprecated
    public static BitmapRegionDecoder newInstance(FileDescriptor fd, boolean isShareable)
            throws IOException {
        return make(BitmapFactory.decodeFileDescriptor(fd));
    }

    public static BitmapRegionDecoder newInstance(InputStream is) throws IOException {
        if (is == null) return null;
        // Buffer the stream so decode failures are reported as IOException and
        // not as a half-consumed stream surprising the caller.
        byte[] all = readAll(is);
        return make(BitmapFactory.decodeByteArray(all, 0, all.length));
    }

    @Deprecated
    public static BitmapRegionDecoder newInstance(InputStream is, boolean isShareable)
            throws IOException {
        return newInstance(is);
    }

    public static BitmapRegionDecoder newInstance(String pathName) throws IOException {
        return make(BitmapFactory.decodeFile(pathName));
    }

    @Deprecated
    public static BitmapRegionDecoder newInstance(String pathName, boolean isShareable)
            throws IOException {
        return newInstance(pathName);
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    public Bitmap decodeRegion(Rect rect, BitmapFactory.Options options) {
        checkRecycled();
        if (rect == null) throw new IllegalArgumentException("rect is null");
        int fw = mFull.getWidth(), fh = mFull.getHeight();
        int l = Math.max(0, rect.left), t = Math.max(0, rect.top);
        int r = Math.min(fw, rect.right), b = Math.min(fh, rect.bottom);
        int w = r - l, h = b - t;
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("rectangle is outside the image");
        }
        int sample = (options != null && options.inSampleSize > 1) ? options.inSampleSize : 1;
        int[] src = new int[w * h];
        mFull.getPixels(src, 0, w, l, t, w, h);
        if (sample == 1) {
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            out.macapkSetArgbBuffer(src);
            return out;
        }
        int ow = Math.max(1, w / sample), oh = Math.max(1, h / sample);
        int[] dst = new int[ow * oh];
        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                dst[y * ow + x] = src[(y * sample) * w + (x * sample)];
            }
        }
        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        out.macapkSetArgbBuffer(dst);
        return out;
    }

    public int getWidth() {
        checkRecycled();
        return mFull.getWidth();
    }

    public int getHeight() {
        checkRecycled();
        return mFull.getHeight();
    }

    public void recycle() {
        if (!mRecycled) {
            mRecycled = true;
            mFull = null;
        }
    }

    public boolean isRecycled() {
        return mRecycled;
    }

    private void checkRecycled() {
        if (mRecycled) {
            throw new IllegalStateException("Cannot use a recycled BitmapRegionDecoder");
        }
    }
}
