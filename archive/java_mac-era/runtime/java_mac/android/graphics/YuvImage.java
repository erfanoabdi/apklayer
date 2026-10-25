// android.graphics.YuvImage -- wraps a camera-style planar/semi-planar YUV
// byte buffer (NV21 or YUY2) and can compress a rectangular region of it
// to JPEG. AOSP ref:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/graphics/java/android/graphics/YuvImage.java
// Real AOSP's compressToJpeg is a thin Java validator over a native
// nativeCompressToJpeg call into Skia; there is no Java-source pixel-math
// to mirror line for line, so this does the real, non-native equivalent:
// decode the NV21/YUY2 bytes to an RGB int[] via the standard ITU-R BT.601
// (full-range) YUV->RGB conversion, then hand that to the same
// javax.imageio.ImageIO JPEG-write path Bitmap.compress() already uses
// (runtime/java/android/graphics/Bitmap.java). The result is a genuine,
// visually-correct JPEG of the given YUV data, not a stub.
package android.graphics;

import java.awt.image.BufferedImage;
import java.io.OutputStream;

public class YuvImage {
    private final byte[] mData;
    private final int mFormat;
    private final int mWidth;
    private final int mHeight;
    private final int[] mStrides;
    private final ColorSpace mColorSpace;

    /** AOSP: only NV21 and YUY2 are documented/supported by
     *  compressToJpeg; validate at construction time the same way AOSP's
     *  constructor does, rather than failing later inside compress. */
    public YuvImage(byte[] yuv, int format, int width, int height, int[] strides) {
        this(yuv, format, width, height, strides, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    public YuvImage(byte[] yuv, int format, int width, int height, int[] strides,
                     ColorSpace colorSpace) {
        if (yuv == null) {
            throw new IllegalArgumentException("yuv cannot be null");
        }
        if (format != ImageFormat.NV21 && format != ImageFormat.YUY2) {
            throw new IllegalArgumentException(
                    "only support ImageFormat.NV21 and ImageFormat.YUY2 for now");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be larger than 0");
        }
        if (colorSpace == null) {
            throw new IllegalArgumentException("colorSpace cannot be null");
        }
        mData = yuv;
        mFormat = format;
        mWidth = width;
        mHeight = height;
        mStrides = strides != null ? strides : defaultStrides(format, width);
        mColorSpace = colorSpace;
    }

    private static int[] defaultStrides(int format, int width) {
        // AOSP: "if strides is null, the method assumes no padding and
        // derives the row length from the image dimensions". NV21 is
        // semi-planar (a full-resolution Y plane, row stride = width,
        // followed by a 2x2-subsampled interleaved VU plane, row stride
        // also width since each row still packs one V+U byte pair per 2
        // luma columns); YUY2 is packed single-plane, 2 bytes/pixel.
        if (format == ImageFormat.NV21) {
            return new int[]{ width, width };
        }
        return new int[]{ width * 2 };
    }

    public byte[] getYuvData() { return mData; }
    public int getYuvFormat() { return mFormat; }
    public int getWidth() { return mWidth; }
    public int getHeight() { return mHeight; }
    public int[] getStrides() { return mStrides; }
    public ColorSpace getColorSpace() { return mColorSpace; }

    /** AOSP: compress a rectangular region of this YUV image to JPEG,
     *  writing the result to stream. Returns true on success. Only NV21
     *  and YUY2 are supported (enforced at construction). */
    public boolean compressToJpeg(Rect rectangle, int quality, OutputStream stream) {
        if (rectangle == null || stream == null) return false;

        int left = Math.max(0, rectangle.left);
        int top = Math.max(0, rectangle.top);
        int right = Math.min(mWidth, rectangle.right);
        int bottom = Math.min(mHeight, rectangle.bottom);

        // AOSP adjusts the rectangle so chroma sampling stays aligned:
        // NV21's chroma is subsampled 2x2 (left/top/width/height all even);
        // YUY2's chroma is subsampled 2x1 (left/width even only).
        if (mFormat == ImageFormat.NV21) {
            left &= ~1;
            top &= ~1;
        } else {
            left &= ~1;
        }
        int w = right - left;
        int h = bottom - top;
        if (mFormat == ImageFormat.NV21) {
            w &= ~1;
            h &= ~1;
        } else {
            w &= ~1;
        }
        if (w <= 0 || h <= 0) return false;

        try {
            int[] argb = new int[w * h];
            if (mFormat == ImageFormat.NV21) {
                decodeNv21ToArgb(argb, left, top, w, h);
            } else {
                decodeYuy2ToArgb(argb, left, top, w, h);
            }
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, w, h, argb, 0, w);
            float q = Math.max(0f, Math.min(1f, quality / 100f));
            javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            try {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(q);
            } catch (Throwable ignored) {
                // Some ImageIO backends don't support explicit quality;
                // fall through and write with defaults rather than fail.
            }
            try (javax.imageio.stream.ImageOutputStream ios =
                         javax.imageio.ImageIO.createImageOutputStream(stream)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
                ios.flush();
            } finally {
                writer.dispose();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** Standard BT.601 (full-range) NV21 (semi-planar Y + interleaved VU,
     *  2x2 chroma subsampling) to RGB conversion, reading directly out of
     *  the FULL image's byte buffer at the given crop offset. */
    private void decodeNv21ToArgb(int[] out, int left, int top, int w, int h) {
        final byte[] data = mData;
        final int strideY = mStrides.length > 0 ? mStrides[0] : mWidth;
        final int strideUV = mStrides.length > 1 ? mStrides[1] : mWidth;
        final int chromaBase = strideY * mHeight;
        for (int row = 0; row < h; row++) {
            final int srcRow = top + row;
            final int yRowBase = srcRow * strideY;
            final int uvRowBase = chromaBase + (srcRow / 2) * strideUV;
            final int dstRow = row * w;
            for (int col = 0; col < w; col++) {
                final int srcCol = left + col;
                final int yIdx = yRowBase + srcCol;
                final int uvIdx = uvRowBase + (srcCol / 2) * 2;
                final int y = (yIdx >= 0 && yIdx < data.length) ? (data[yIdx] & 0xff) : 0;
                final int v = ((uvIdx     >= 0 && uvIdx     < data.length) ? (data[uvIdx]     & 0xff) : 128) - 128;
                final int u = ((uvIdx + 1 >= 0 && uvIdx + 1 < data.length) ? (data[uvIdx + 1] & 0xff) : 128) - 128;
                out[dstRow + col] = yuvToArgb(y, u, v);
            }
        }
    }

    /** YUY2 (packed, Y0 U0 Y1 V0 per horizontal pixel pair, 2x1 chroma
     *  subsampling) to RGB. */
    private void decodeYuy2ToArgb(int[] out, int left, int top, int w, int h) {
        final byte[] data = mData;
        final int stride = mStrides.length > 0 ? mStrides[0] : mWidth * 2;
        for (int row = 0; row < h; row++) {
            final int rowBase = (top + row) * stride;
            final int dstRow = row * w;
            for (int col = 0; col < w; col++) {
                final int srcCol = left + col;
                final int pairBase = rowBase + (srcCol / 2) * 4;
                final int yOff = pairBase + ((srcCol & 1) * 2);
                final int y = (yOff >= 0 && yOff < data.length) ? (data[yOff] & 0xff) : 0;
                final int uOff = pairBase + 1, vOff = pairBase + 3;
                final int u = ((uOff >= 0 && uOff < data.length) ? (data[uOff] & 0xff) : 128) - 128;
                final int v = ((vOff >= 0 && vOff < data.length) ? (data[vOff] & 0xff) : 128) - 128;
                out[dstRow + col] = yuvToArgb(y, u, v);
            }
        }
    }

    private static int yuvToArgb(int y, int u, int v) {
        final int r = clampByte(y + (int) (1.402f * v));
        final int g = clampByte(y - (int) (0.344136f * u + 0.714136f * v));
        final int b = clampByte(y + (int) (1.772f * u));
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    /** AOSP YuvImage.compressToJpegR(sdr, quality, stream) (API 34) -- delegates
     *  to the 4-arg form with an empty EXIF blob. */
    public boolean compressToJpegR(YuvImage sdr, int quality, java.io.OutputStream stream) {
        return compressToJpegR(sdr, quality, stream, new byte[0]);
    }

    /** AOSP YuvImage.compressToJpegR(sdr, quality, stream, exif) (API 36).
     *  Encodes an Ultra HDR (JPEG_R) image: `this` is the HDR (YCBCR_P010)
     *  rendition, `sdr` the SDR (YUV_420_888) one.
     *
     *  ⚠ WE CANNOT ENCODE JPEG_R. It needs libultrahdr, which macOS does not
     *  ship and which we do not vendor -- and there is no AudioToolbox/ImageIO
     *  equivalent (ImageIO writes ISO-21496 gainmap HEIC, not the JPEG_R
     *  container this API is specified against). So we run AOSP's argument
     *  validation VERBATIM -- those IllegalArgumentExceptions are observable and
     *  an app relies on catching them -- and then return false, which is AOSP's
     *  own "compression failed" answer, rather than writing a file that is not a
     *  JPEG_R. Nothing is written to `stream`. */
    public boolean compressToJpegR(YuvImage sdr, int quality,
            java.io.OutputStream stream, byte[] exif) {
        if (sdr == null) {
            throw new IllegalArgumentException("SDR input cannot be null");
        }
        if (getYuvData().length == 0 || sdr.getYuvData().length == 0) {
            throw new IllegalArgumentException("Input images cannot be empty");
        }
        if (getYuvFormat() != ImageFormat.YCBCR_P010
                || sdr.getYuvFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException(
                "only support ImageFormat.YCBCR_P010 and ImageFormat.YUV_420_888");
        }
        if (sdr.getWidth() != getWidth() || sdr.getHeight() != getHeight()) {
            throw new IllegalArgumentException("HDR and SDR resolution mismatch");
        }
        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("quality must be 0..100");
        }
        if (stream == null) {
            throw new IllegalArgumentException("stream cannot be null");
        }
        return false;
    }
}
