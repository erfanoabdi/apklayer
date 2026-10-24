// NativeImageCodec -- decode image byte streams that Java's javax.imageio
// cannot, by falling through to macOS's native ImageIO (CGImageSource).
//
// Why this exists: Android apps ship drawables in formats Java's bundled
// ImageIO readers don't support -- most importantly WEBP (the default
// `aapt` output format for PNG/JPEG drawables since Android Studio 2.3,
// so the MAJORITY of modern APK raster assets are webp), plus HEIC. When
// `ImageIO.read` returns null for those, BitmapFactory.decodeStream used
// to return null, and any caller that treats a null Bitmap as fatal
// (Jetpack Compose's `ImageBitmap.imageResource` -> painterResource
// THROWS `ResourceResolutionException`) aborts. Reddit's welcome/login
// screen background is `welcome_background_2.webp`; its painterResource
// throw aborted the whole Compose composition + measure pass, collapsing
// the content view to 0x0 (a blank screen).
//
// macOS's ImageIO framework decodes webp (since macOS 11), heic, and
// every other CGImageSource format natively. This bridge hands the raw
// bytes to a CGImageSource and gets back ARGB_8888 pixels. Generic:
// keyed on "Java couldn't decode it," never on any app.
//
// The native method is registered from native_methods.cpp at boot
// (RegisterAllNativeMethods), so no System.loadLibrary is needed.
package com.macapk.loader;

public final class NativeImageCodec {
    private NativeImageCodec() {}

    /** Decode `data` (a complete encoded image file: webp/heic/png/...)
     *  to ARGB_8888. Returns an int[] laid out as
     *  [width, height, argb(0,0), argb(1,0), ... row-major], or null if
     *  the native decoder can't read the bytes (corrupt / unknown
     *  format / unavailable). Each pixel is 0xAARRGGBB, matching
     *  android.graphics.Color / Bitmap.getPixels. */
    public static native int[] decode(byte[] data);
}
