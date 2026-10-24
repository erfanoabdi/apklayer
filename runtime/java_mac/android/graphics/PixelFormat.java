package android.graphics;

/**
 * AOSP-faithful android.graphics.PixelFormat. A bag of int constants plus the
 * getPixelFormatInfo()/formatHasAlpha() helpers. Used everywhere a Window /
 * SurfaceHolder / Drawable reasons about translucency (the opacity codes
 * TRANSLUCENT/TRANSPARENT/OPAQUE in particular are returned by Drawable
 * subclasses). Constants and bytes-per-pixel mirror
 * frameworks/base/graphics/java/android/graphics/PixelFormat.java.
 */
public class PixelFormat {
    public static final int UNKNOWN     = 0;
    public static final int TRANSLUCENT = -3;
    public static final int TRANSPARENT = -2;
    public static final int OPAQUE      = -1;

    public static final int RGBA_8888   = 1;
    public static final int RGBX_8888   = 2;
    public static final int RGB_888     = 3;
    public static final int RGB_565     = 4;

    @Deprecated public static final int RGBA_5551 = 6;
    @Deprecated public static final int RGBA_4444 = 7;
    @Deprecated public static final int A_8       = 8;
    @Deprecated public static final int L_8       = 9;
    @Deprecated public static final int LA_88     = 0xA;
    @Deprecated public static final int RGB_332   = 0xB;

    public static final int RGBA_F16    = 0x16;
    public static final int RGBA_1010102 = 0x2B;

    @Deprecated public static final int JPEG     = 0x100;
    public static final int YCbCr_422_SP        = 0x10;
    public static final int YCbCr_420_SP        = 0x11;
    public static final int YCbCr_422_I         = 0x14;

    public int bytesPerPixel;
    public int bitsPerPixel;

    public static void getPixelFormatInfo(int format, PixelFormat info) {
        if (info == null) {
            throw new NullPointerException("info");
        }
        switch (format) {
            case RGBA_8888:
            case RGBX_8888:
            case RGBA_1010102:
                info.bitsPerPixel = 32;
                info.bytesPerPixel = 4;
                break;
            case RGB_888:
                info.bitsPerPixel = 24;
                info.bytesPerPixel = 3;
                break;
            case RGB_565:
            case RGBA_5551:
            case RGBA_4444:
            case LA_88:
            case RGBA_F16:
                info.bitsPerPixel = 16;
                info.bytesPerPixel = 2;
                break;
            case A_8:
            case L_8:
            case RGB_332:
                info.bitsPerPixel = 8;
                info.bytesPerPixel = 1;
                break;
            case YCbCr_422_SP:
            case YCbCr_420_SP:
            case YCbCr_422_I:
                info.bitsPerPixel = 16;
                info.bytesPerPixel = 1;
                break;
            default:
                throw new IllegalArgumentException("unknown pixel format " + format);
        }
    }

    public static boolean formatHasAlpha(int format) {
        switch (format) {
            case PixelFormat.A_8:
            case PixelFormat.LA_88:
            case PixelFormat.RGBA_4444:
            case PixelFormat.RGBA_5551:
            case PixelFormat.RGBA_8888:
            case PixelFormat.RGBA_F16:
            case PixelFormat.RGBA_1010102:
            case PixelFormat.TRANSLUCENT:
            case PixelFormat.TRANSPARENT:
                return true;
        }
        return false;
    }

    /** AOSP @hide helper retained because some apps call it reflectively. */
    public static boolean isPublicFormat(int format) {
        switch (format) {
            case RGBA_8888:
            case RGBX_8888:
            case RGB_888:
            case RGB_565:
            case RGBA_F16:
            case RGBA_1010102:
                return true;
        }
        return false;
    }
}
