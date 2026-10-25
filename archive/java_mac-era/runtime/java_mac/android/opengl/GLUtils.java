// android.opengl.GLUtils -- Bitmap <-> GLES texture helpers. AndEngine
// uses texSubImage2D(target, level, x, y, Bitmap, format, type) to
// upload its sprite atlas, so this is the critical upload path for
// any AndEngine app.
//
// Implementation: pull the Bitmap's ARGB int buffer and reorder to
// RGBA bytes (OpenGL expects RGBA in memory), then forward to a
// native helper that calls real glTexImage2D / glTexSubImage2D.
//
// PREMULTIPLIED UPLOAD (2026-09-26, docs/telegram-intro-logo_2026-09-26.md
// §7.6). AOSP uploads the bitmap's OWN pixel memory: util_texImage2D and
// util_texSubImage2D pass `bitmap.getPixels()` straight to glTexImage2D /
// glTexSubImage2D (frameworks/base core/jni/android/opengl/util.cpp,
// android14-release, :638-660 and :662-683), and an ARGB_8888 bitmap whose
// isPremultiplied() is true stores PREMULTIPLIED bytes. Our backing store is
// non-premultiplied end to end (see Bitmap.java), so we used to upload
// STRAIGHT alpha: every partially transparent texel came out brighter than on
// a device, and a fully transparent one kept its colour. Engines that blend
// with (GL_ONE, GL_ONE_MINUS_SRC_ALPHA) because they know GLUtils uploads
// premultiplied (AndEngine's preMultiplyAlpha path, Telegram's intro) then
// drew halos. We now premultiply with Skia's own write-path rounding, the
// SkMulDiv255Round the AndroidBitmap_lockPixels bridge already uses (the abm
// gate holds it exact against Bitmap.java over all 65536 (a,c)). An
// unpremultiplied or opaque bitmap uploads unchanged, as its memory is.
// BISECT GATE: MACAPK_GLUTILS_PREMUL=0 restores the straight-alpha upload.
package android.opengl;

import android.graphics.Bitmap;

public final class GLUtils {
    private GLUtils() {}

    private static final boolean sPremultiply =
        !"0".equals(System.getenv("MACAPK_GLUTILS_PREMUL"));

    public static int getInternalFormat(Bitmap b) { return GLES20.GL_RGBA; }
    public static int getType(Bitmap b)           { return GLES20.GL_UNSIGNED_BYTE; }

    public static void texImage2D(int target, int level, Bitmap bitmap, int border) {
        texImage2D(target, level, getInternalFormat(bitmap), bitmap, getType(bitmap), border);
    }
    /** AOSP GLUtils.texImage2D(target, level, internalformat, bitmap, border)
     *  -- the overload that takes an explicit internal format but derives the
     *  TYPE from the bitmap. */
    public static void texImage2D(int target, int level, int internalformat,
                                  Bitmap bitmap, int border) {
        texImage2D(target, level, internalformat, bitmap, getType(bitmap), border);
    }
    public static void texImage2D(int target, int level, int internalformat,
                                  Bitmap bitmap, int type, int border) {
        if (bitmap == null) return;
        byte[] rgba = argbBufferToRgbaBytes(bitmap.macapkArgbBuffer(),
                                            bitmap.getWidth(), bitmap.getHeight(),
                                            premultipliedMemory(bitmap));
        int format = internalformat == 0 ? GLES20.GL_RGBA : internalformat;
        int t      = type == 0 ? GLES20.GL_UNSIGNED_BYTE : type;
        nativeTexImage2DRgba(target, level, format, bitmap.getWidth(), bitmap.getHeight(),
                             border, format, t, rgba);
    }
    public static void texSubImage2D(int target, int level, int xoffset, int yoffset, Bitmap bitmap) {
        texSubImage2D(target, level, xoffset, yoffset, bitmap,
                      getInternalFormat(bitmap), getType(bitmap));
    }
    public static void texSubImage2D(int target, int level, int xoffset, int yoffset,
                                     Bitmap bitmap, int format, int type) {
        if (bitmap == null) return;
        byte[] rgba = argbBufferToRgbaBytes(bitmap.macapkArgbBuffer(),
                                            bitmap.getWidth(), bitmap.getHeight(),
                                            premultipliedMemory(bitmap));
        int f = format == 0 ? GLES20.GL_RGBA : format;
        int t = type   == 0 ? GLES20.GL_UNSIGNED_BYTE : type;
        nativeTexSubImage2DRgba(target, level, xoffset, yoffset,
                                bitmap.getWidth(), bitmap.getHeight(), f, t, rgba);
    }

    public static String getEGLErrorString(int error) { return "EGL error " + Integer.toHexString(error); }

    /** Is this bitmap's device memory premultiplied (so that is what AOSP
     *  uploads)? Bitmap.isPremultiplied() is AOSP's own answer: true only for
     *  a bitmap with alpha that was not asked to stay unpremultiplied. */
    private static boolean premultipliedMemory(Bitmap b) {
        return sPremultiply && b.isPremultiplied();
    }

    /** Convert ARGB int[] (0xAARRGGBB per pixel, non-premultiplied, as our
     *  Bitmap stores it) to the packed RGBA bytes a device's bitmap memory
     *  holds, for glTexImage2D with format=GL_RGBA, type=GL_UNSIGNED_BYTE.
     *  Byte 0 = R, 1 = G, 2 = B, 3 = A; premultiplied when {@code premul}. */
    private static byte[] argbBufferToRgbaBytes(int[] argb, int w, int h, boolean premul) {
        if (argb == null) return null;
        int n = Math.min(argb.length, w * h);
        byte[] out = new byte[n * 4];
        for (int i = 0; i < n; ++i) {
            int p = argb[i];
            int a = (p >>> 24) & 0xff;
            int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
            if (premul && a != 0xff) {
                r = mulDiv255Round(r, a);
                g = mulDiv255Round(g, a);
                b = mulDiv255Round(b, a);
            }
            out[i * 4 + 0] = (byte) r;
            out[i * 4 + 1] = (byte) g;
            out[i * 4 + 2] = (byte) b;
            out[i * 4 + 3] = (byte) a;
        }
        return out;
    }

    /** Skia's SkMulDiv255Round, the rounding SkConvertPixels' premul fast path
     *  uses (bit-identical to Bitmap.macapkMulDiv255Round). a == 0 gives 0. */
    private static int mulDiv255Round(int c, int a) {
        int prod = c * a + 128;
        return (prod + (prod >> 8)) >> 8;
    }

    private static native void nativeTexImage2DRgba(int target, int level, int internalformat,
                                                    int width, int height, int border,
                                                    int format, int type, byte[] rgba);
    private static native void nativeTexSubImage2DRgba(int target, int level, int xoffset, int yoffset,
                                                       int width, int height, int format, int type,
                                                       byte[] rgba);
}
