// The Mac desktop picture, as the input Android's WallpaperManagerService.extractColors
// feeds WallpaperColors.fromBitmap. Two natives (runtime/src/wallpaper_bridge.mm) answer
// WHICH image the screen shows and WHAT PIXELS it shows at display resolution; everything
// from there on is AOSP's own maths (WallpaperColorsData / ColorScheme / MonetOverlay).
//
// The render+quantize step costs a few hundred milliseconds the first time a given picture
// is seen, so the finished answer is cached on disk under the picture's IDENTITY (path,
// size, mtime, placement, screen size, algorithm version) -- the same trigger AOSP uses:
// SystemUI only recomputes when WallpaperManager reports new colours, i.e. when the
// wallpaper changes. See docs/monet-wallpaper-seed_2026-09-26.md.
//
// The path crosses JNI as UTF-8 BYTES in both directions, never as a jstring: JNI's
// NewStringUTF/GetStringUTFChars speak MODIFIED UTF-8, which re-encodes any character outside
// the BMP (an emoji file name) as two 3-byte surrogates, so a round trip through them handed
// CGImageSource a path that named no file. See wallpaper_bridge.mm.
package com.macapk.loader.monet;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class HostWallpaper {
    private HostWallpaper() {}

    /** Largest screen rendered at backing resolution; mirrors wallpaper_bridge.mm's
     *  kMaxScreenPixels (an 8K display is 33 M pixels). */
    static final long MAX_SCREEN_PIXELS = 8192L * 8192L;

    /** UTF-8 "path\tscaling\tallowClipping\tfillArgb\tscreenPxW\tscreenPxH", or null. */
    static native byte[] nativeDesktopInfo();

    /** The desktop as the screen shows it (sw x sh), nearest-sampled to ow x oh, packed
     *  non-premultiplied sRGB ARGB (Bitmap.getPixels layout), or null. `path` is the
     *  file-system representation, UTF-8, no terminator. */
    static native int[] nativeRender(byte[] path, int scaling, int allowClipping,
            int fillArgb, int sw, int sh, int ow, int oh);

    /** What the desktop shows, as far as the placement is concerned. */
    public static final class Desktop {
        public final String path;
        /** The path exactly as the native side reported it, handed back verbatim. */
        final byte[] pathBytes;
        public final int scaling;
        public final int allowClipping;
        public final int fillArgb;
        public final int screenW;
        public final int screenH;

        public Desktop(String path, int scaling, int allowClipping, int fillArgb,
                int screenW, int screenH) {
            this(path, path.getBytes(StandardCharsets.UTF_8), scaling, allowClipping, fillArgb,
                    screenW, screenH);
        }

        Desktop(String path, byte[] pathBytes, int scaling, int allowClipping, int fillArgb,
                int screenW, int screenH) {
            this.path = path;
            this.pathBytes = pathBytes;
            this.scaling = scaling;
            this.allowClipping = allowClipping;
            this.fillArgb = fillArgb;
            this.screenW = screenW;
            this.screenH = screenH;
        }

        /** The cache identity: every input that changes the extracted colours. */
        String identity(String algo) {
            File f = new File(path);
            return path + "|" + f.length() + "|" + f.lastModified() + "|" + scaling + "|"
                    + allowClipping + "|" + Integer.toHexString(fillArgb) + "|" + screenW + "x"
                    + screenH + "|" + algo;
        }

        @Override
        public String toString() {
            return path + " (" + screenW + "x" + screenH + "px, scaling " + scaling
                    + (allowClipping != 0 ? ", clip" : "") + ")";
        }
    }

    /** Parse nativeDesktopInfo()'s answer; null when it is absent or malformed. The five
     *  numeric fields are taken from the END, so a path that itself contains a TAB (legal in
     *  a macOS file name) still parses. */
    static Desktop parse(byte[] b) {
        if (b == null) return null;
        int tab = b.length, seen = 0;
        while (seen < 5 && --tab >= 0) {
            if (b[tab] == '\t') seen++;
        }
        if (seen != 5 || tab <= 0) return null;
        byte[] pathBytes = Arrays.copyOf(b, tab);
        String path = new String(pathBytes, StandardCharsets.UTF_8);
        String[] f = new String(b, tab + 1, b.length - tab - 1, StandardCharsets.UTF_8)
                .split("\t", -1);
        if (f.length != 5) return null;
        try {
            int scaling = Integer.parseInt(f[0]);
            int clip = Integer.parseInt(f[1]);
            int fill = (int) Long.parseLong(f[2]);
            int w = Integer.parseInt(f[3]);
            int h = Integer.parseInt(f[4]);
            if (w <= 0 || h <= 0 || (long) w * h > MAX_SCREEN_PIXELS) return null;
            return new Desktop(path, pathBytes, scaling, clip, fill, w, h);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The desktop picture of the screen our window opens on, or null (no screen, no file).
     *  ⚠ Deliberately does NOT catch UnsatisfiedLinkError: natives that were never bound
     *  (RegisterWallpaperBridgeMethods not called, e.g. a forgotten registration, or an
     *  offline harness on a bare JVM) must stay distinguishable from a host that shows no
     *  picture, or a missing integration step reads as "headless" in the log. Monet.resolve
     *  catches it and says which. */
    static Desktop query() {
        return parse(nativeDesktopInfo());
    }

    /** WallpaperColors.fromBitmap over the desktop as shown: render at display resolution,
     *  nearest-downsample to calculateOptimalSize, quantize. Null on any failure. */
    static WallpaperColorsData extract(Desktop d) {
        int[] size = WallpaperColorsData.calculateOptimalSize(d.screenW, d.screenH);
        int[] px;
        try {
            px = nativeRender(d.pathBytes, d.scaling, d.allowClipping, d.fillArgb,
                    d.screenW, d.screenH, size[0], size[1]);
        } catch (LinkageError e) {
            return null;
        }
        if (px == null || px.length != size[0] * size[1]) return null;
        // The ORIGINAL bitmap's area sizes the cluster count (fromBitmap reads it before
        // scaling); the crop file AOSP decodes is display-sized, and so is ours.
        return WallpaperColorsData.fromPixels(px, d.screenW * d.screenH, 0f /* dimAmount */);
    }
}
