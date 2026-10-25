// android.graphics.BitmapFactory -- decode bytes / files / streams
// to a Bitmap. We back this with javax.imageio.ImageIO so PNG, JPEG,
// GIF, BMP all decode for free; that handles everything AndEngine and
// most APKs throw at us. The returned Bitmap holds the pixel data
// (RGBA8) on the Java heap; once the GLES texture upload is wired we
// will hand the buffer to ANGLE via JNI.
package android.graphics;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class BitmapFactory {
    /** AOSP BitmapFactory declares NO constructor, so javac synthesises a PUBLIC
     *  no-arg one and it is part of the public API surface. Ours was private, so
     *  `new BitmapFactory()` (rare, but it compiles against the SDK) failed. */
    public BitmapFactory() {}

    public static class Options {
        public boolean inJustDecodeBounds = false;
        /** ⚠ AOSP declares this `public int inSampleSize;` with NO initializer,
         *  so a fresh Options reports 0, not 1 (BMP.scale.defaults). Apps read
         *  the field back -- Glide's Downsampler and every hand-rolled
         *  "efficient bitmap loading" helper compute `while (w/inSampleSize >
         *  target) inSampleSize *= 2`, which multiplies from whatever is
         *  already there. Any non-positive value is normalised to 1 at decode
         *  time (BitmapFactory.cpp:189-193 "Correct a non-positive
         *  sampleSize"), which is why the 0 default is harmless. */
        public int     inSampleSize;
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
        public boolean inDither           = false;
        public boolean inPremultiplied    = true;
        public boolean inMutable          = false;
        public boolean inScaled           = true;
        public int     inDensity          = 0;
        public int     inTargetDensity    = 0;
        public int     inScreenDensity    = 0;
        public boolean inPreferQualityOverSpeed = false;
        public byte[]  inTempStorage;
        public Bitmap  inBitmap;
        /** @deprecated AOSP: "As of {@link android.os.Build.VERSION_CODES#LOLLIPOP},
         *  this is ignored." Real Android still DECLARES these two fields on every
         *  API level Mac.apk targets -- an app can assign them without
         *  NoSuchFieldError, they just have zero effect. We had no fields at all,
         *  which is a regression relative to the real platform: a copy-pasted
         *  pre-Lollipop "efficient bitmap loading" helper (still common in older
         *  codebases) that assigns inPurgeable/inInputShareable threw here where
         *  real modern Android silently accepts and ignores the assignment. */
        @Deprecated
        public boolean inPurgeable;
        /** @deprecated AOSP: "As of {@link android.os.Build.VERSION_CODES#LOLLIPOP},
         *  this is ignored." See inPurgeable. */
        @Deprecated
        public boolean inInputShareable;
        /** ⚠ AOSP leaves these at the Java default 0 on a fresh Options and
         *  only writes -1 once a DECODE has been attempted and failed
         *  (BitmapFactory.cpp:201-202 initialises them to -1 "in case we
         *  fail"). We initialised them to -1, so an app that measured a fresh
         *  Options before decoding saw a size it never asked for
         *  (BMP.scale.defaults.out: emulator 0x0, we answered -1x-1). See
         *  macapkResetOut, which reproduces the native side's reset. */
        public int     outWidth;
        public int     outHeight;
        public String  outMimeType;
        public Bitmap.Config outConfig;
        /** v1.0.110: API 26+ field. Glide's Downsampler writes
         *  options.inPreferredColorSpace = ColorSpace.get(SRGB) so the
         *  decoded bitmap reports a known color space. Optional in
         *  AOSP; we accept the assignment but ignore the value (our
         *  ImageIO path is always sRGB). */
        public ColorSpace inPreferredColorSpace;
        public ColorSpace outColorSpace;
        public Options() {}

        /** @deprecated AOSP: "As of {@link android.os.Build.VERSION_CODES#N}, this
         *  will not affect the decode, though it will still set mCancel." Inert on
         *  every Android version Mac.apk targets -- we have no cancellable native
         *  decode loop to hook into (our decodes are synchronous ImageIO/native
         *  calls that already returned by the time a caller could invoke this), so
         *  a faithful no-op matches AOSP's own observable behavior rather than
         *  throwing NoSuchMethodError for the deprecated-but-still-present API. */
        /** AOSP BitmapFactory.Options.mCancel -- a public boolean the native decode
         *  loop polls. Public API since API 1; apps read it back after calling
         *  requestCancelDecode(). */
        public boolean mCancel;

        @Deprecated
        public void requestCancelDecode() { mCancel = true; }

        /** AOSP BitmapFactory.Options.validate (BitmapFactory.java:436-465),
         *  run at the TOP of every decode* entry point. These three rejections
         *  happen in JAVA, before a single byte is read, so they are pinned on
         *  every device and an app can rely on catching them:
         *   - a HARDWARE inBitmap ("Bitmaps with Config.HARDWARE are always
         *     immutable"),
         *   - a RECYCLED inBitmap ("Cannot reuse a recycled Bitmap") --
         *     note IllegalArgumentException, not the IllegalStateException the
         *     bitmap's own methods raise (BMP.err.recycledInBitmap),
         *   - inMutable together with inPreferredConfig=HARDWARE, which is a
         *     contradiction in terms (BMP.err.mutableHardware,
         *     BMP.hw.decodeMutable).
         *  We validated none of it and returned a bitmap. */
        static void validate(Options opts) {
            if (opts == null) return;
            if (opts.inBitmap != null) {
                if (opts.inBitmap.getConfig() == Bitmap.Config.HARDWARE) {
                    throw new IllegalArgumentException(
                            "Bitmaps with Config.HARDWARE are always immutable");
                }
                if (opts.inBitmap.isRecycled()) {
                    throw new IllegalArgumentException("Cannot reuse a recycled Bitmap");
                }
            }
            if (opts.inMutable && opts.inPreferredConfig == Bitmap.Config.HARDWARE) {
                throw new IllegalArgumentException("Bitmaps with Config.HARDWARE cannot be "
                        + "decoded into - they are immutable");
            }
        }
    }

    public static Bitmap decodeStream(InputStream stream) {
        return decodeStream(stream, null, null);
    }
    /** AOSP decodeStream (BitmapFactory.java:747-777). The SHAPE of this method
     *  is load-bearing and we had it wrong: the whole body used to sit inside a
     *  `catch (Throwable) { return null; }`, so every documented exception --
     *  Options.validate's three IllegalArgumentExceptions and the
     *  inBitmap-failure IAE below -- was swallowed and reported to the app as
     *  "could not decode". AOSP's order is: null stream returns null WITHOUT
     *  throwing ("we don't throw in this case, thus allowing the caller to only
     *  check the cache"), THEN validate, THEN decode, THEN -- if the decode
     *  produced nothing and an inBitmap was supplied -- convert the null into
     *  IllegalArgumentException("Problem decoding into existing bitmap"), THEN
     *  stamp the density. Only the DECODE ITSELF is allowed to swallow. */
    public static Bitmap decodeStream(InputStream stream, Rect outPadding, Options opts) {
        // we don't throw in this case, thus allowing the caller to only check
        // the cache, and not force the image to be decoded.
        if (stream == null) return null;
        Options.validate(opts);
        Bitmap bm = macapkDecodeStreamInternal(stream, outPadding, opts);
        if (bm == null && opts != null && opts.inBitmap != null) {
            throw new IllegalArgumentException("Problem decoding into existing bitmap");
        }
        macapkSetDensityFromOptions(bm, opts);
        return bm;
    }

    /** AOSP setDensityFromOptions (BitmapFactory.java:696-717), verbatim. This
     *  is the JAVA half of the density contract and we implemented none of it:
     *  every decoded bitmap kept the device default, so a drawable decoded out
     *  of an mdpi bucket claimed to be xhdpi and getScaledWidth() scaled from
     *  the wrong base. Note the THIRD guard, `density == opts.inScreenDensity`
     *  -- the density-compatibility escape hatch, which nothing else in Android
     *  reads and which is therefore the piece a reimplementation always misses
     *  (BMP.scale.screenEqDensity). Note also the else-branch: a bitmap that
     *  was REUSED via inBitmap has its density RESET to the default when
     *  inDensity is 0, so an app's pre-reuse setDensity is discarded
     *  (BMP.reuse.densityReset). */
    private static void macapkSetDensityFromOptions(Bitmap outputBitmap, Options opts) {
        if (outputBitmap == null || opts == null) return;
        final int density = opts.inDensity;
        if (density != 0) {
            outputBitmap.setDensity(density);
            final int targetDensity = opts.inTargetDensity;
            if (targetDensity == 0 || density == targetDensity
                    || density == opts.inScreenDensity) {
                return;
            }
            byte[] np = outputBitmap.getNinePatchChunk();
            final boolean isNinePatch = np != null && NinePatch.isNinePatchChunk(np);
            if (opts.inScaled || isNinePatch) {
                outputBitmap.setDensity(targetDensity);
            }
        } else if (opts.inBitmap != null) {
            // bitmap was reused, ensure density is reset
            outputBitmap.setDensity(Bitmap.macapkDefaultDensity());
        }
    }

    /** The out* fields are the DECODER's, not the Options object's: the native
     *  side sets them to -1 before it starts "in case we fail"
     *  (BitmapFactory.cpp:201-202) and overwrites them once the header is read.
     *  Reproducing that here is what makes a failed decode report -1x-1 while a
     *  never-used Options still reports 0x0. */
    private static void macapkResetOut(Options opts) {
        if (opts == null) return;
        opts.outWidth = -1;
        opts.outHeight = -1;
        opts.outMimeType = null;
        opts.outConfig = null;
    }

    private static Bitmap macapkDecodeStreamInternal(InputStream stream, Rect outPadding,
                                                     Options opts) {
        macapkResetOut(opts);
        byte[] bytes;
        boolean marked = false;
        try {
            // AOSP buffers the stream through JavaInputStreamAdaptor and stops
            // at the container's end, leaving the caller positioned right after
            // the encoded image ("The stream's position will be where ever it
            // was after the encoded data was read"). We have to read ahead to
            // hand ImageIO a seekable buffer, so we mark first and rewind to
            // the true encoded length afterwards (BMP.stream.positionAfter:
            // 16 trailing bytes must still be available). A stream that cannot
            // mark simply keeps the old all-consuming behaviour, which is what
            // AOSP's own non-rewindable fallback does too.
            if (stream.markSupported()) { stream.mark(Integer.MAX_VALUE); marked = true; }
            bytes = macapkReadTolerantly(stream);
        } catch (Throwable t) {
            return null;
        }
        BufferedImage img = null;
        String mime = null;
        int srcW = 0, srcH = 0;
        boolean srcHasAlpha = true, srcIsGray = false;
        final int sample = macapkSampleSize(opts);
        javax.imageio.stream.ImageInputStream iis = null;
        javax.imageio.ImageReader reader = null;
        try {
            iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
            if (iis != null) {
                java.util.Iterator<javax.imageio.ImageReader> it = ImageIO.getImageReaders(iis);
                if (it.hasNext()) {
                    reader = it.next();
                    reader.setInput(iis, true, true);
                    srcW = reader.getWidth(0);
                    srcH = reader.getHeight(0);
                    mime = "image/" + reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                }
            }
        } catch (Throwable ignored) { /* header unreadable -> native fallback */ }

        if (reader != null && srcW > 0 && srcH > 0) {
            // AOSP's sampled dimensions are INTEGER DIVISION floored at 1
            // (BitmapFactory.cpp:259-270 via SkSampledCodec's
            // get_scaled_dimension), NOT the javadoc's "rounded down to the
            // nearest power of 2" and NOT the ceil() that
            // ImageReadParam.setSourceSubsampling produces. On a 32-wide image
            // sample 3 is 10, where subsampling alone gives 11
            // (BMP.sample.rgba32.n3 / n5 / n6 / n7 and the rect30x20 rows).
            final int outW = Math.max(1, srcW / sample);
            final int outH = Math.max(1, srcH / sample);
            try {
                if (opts != null) {
                    opts.outWidth = outW;
                    opts.outHeight = outH;
                    opts.outMimeType = mime;
                }
                srcHasAlpha = macapkReaderHasAlpha(reader, bytes);
                srcIsGray = macapkReaderIsGray(reader, bytes);
                if (opts != null) {
                    opts.outConfig = macapkResolveConfig(
                            opts.inPreferredConfig, srcHasAlpha, srcIsGray);
                }
                // The out* fields are filled ABOVE the early return, and the
                // density scale is applied BELOW it, so outWidth reports the
                // SAMPLED size but never the density-scaled one
                // (BitmapFactory.cpp:305 sits between the two).
                if (opts != null && opts.inJustDecodeBounds) return null;
                javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                if (sample > 1) {
                    // setSourceSubsampling rejects a factor larger than the
                    // image and overflows on Integer.MAX_VALUE, so clamp it to
                    // the image and crop to the true sampled size afterwards
                    // (BMP.sample.maxInt must be 1x1, not null).
                    int k = Math.max(1, Math.min(sample, Math.min(srcW, srcH)));
                    // Skia starts a subsampled decode at source coordinate
                    // sampleFactor/2, not 0 -- external/skia @
                    // android11-release, src/codec/SkCodecPriv.h:42-55:
                    //   "Returns the first coordinate that we will keep
                    //   during a scaled decode ... static inline int
                    //   get_start_coord(int sampleFactor) { return
                    //   sampleFactor / 2; }"
                    // We started at offset 0, which shifts EVERY sampled
                    // pixel's source column/row by up to k/2 relative to
                    // Android whenever k does not evenly divide the source
                    // dimension (BMP.lossy.sample.rgb24.n5/n8, sample.gray.n3
                    // -- derived exactly in
                    // docs/fwprobe-bitmap-raster-divergences_2026-08-10.md
                    // cluster 2, kept-column sets {2,7,12,17} vs {0,5,10,15}
                    // for k=5). The offset must be < the period
                    // (ImageReadParam precondition); k/2 < k holds for every
                    // k >= 1 this branch can reach (sample > 1, then clamped
                    // down to >= 1 above).
                    param.setSourceSubsampling(k, k, k / 2, k / 2);
                }
                img = reader.read(0, param);
            } catch (Throwable t) {
                img = null;
            } finally {
                try { reader.dispose(); } catch (Throwable ignored) {}
                try { if (iis != null) iis.close(); } catch (Throwable ignored) {}
            }
            if (img != null) {
                int[] argb = macapkExtractArgb(img);
                int w = img.getWidth(), h = img.getHeight();
                if (w != outW || h != outH) {
                    argb = macapkCrop(argb, w, h, outW, outH);
                    w = outW; h = outH;
                }
                Bitmap out = macapkFinishDecode(w, h, argb, opts, srcHasAlpha, srcIsGray);
                macapkFillPadding(outPadding, out);
                if (marked) macapkRewind(stream, bytes);
                return out;
            }
        } else {
            try { if (reader != null) reader.dispose(); } catch (Throwable ignored) {}
            try { if (iis != null) iis.close(); } catch (Throwable ignored) {}
        }
        // No ImageIO reader (most importantly WEBP -- aapt's default raster
        // format, and the bulk of modern APK drawables) or a decode ImageIO
        // refused: fall through to the native macOS codec, which also produces
        // a PARTIAL image for a truncated file the way SkCodec does.
        Bitmap nb = macapkNativeDecode(bytes, opts);
        if (nb != null) {
            macapkFillPadding(outPadding, nb);
            if (marked) macapkRewind(stream, bytes);
            return nb;
        }
        android.util.Log.w("BitmapFactory",
            "decodeStream: no decoder for " + bytes.length + " bytes");
        return null;
    }

    /** AOSP's JavaInputStreamAdaptor "clears the pending exception and treats
     *  it as end-of-input", so a stream that throws partway yields a PARTIAL
     *  bitmap rather than propagating the IOException or collapsing to null
     *  (BMP.stream.throwingStream). readAllBytes() propagates, which is why we
     *  cannot use it. */
    private static byte[] macapkReadTolerantly(InputStream in) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        try {
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        } catch (Throwable treatAsEof) { /* deliberately swallowed; see above */ }
        return bos.toByteArray();
    }

    /** Re-position `stream` immediately after the encoded image. Only called
     *  when mark() succeeded. The encoded length is walked out of the container
     *  (PNG chunks to IEND, JPEG markers to EOI); anything else keeps the
     *  whole-buffer behaviour, which is what a codec that consumed everything
     *  would leave anyway. */
    private static void macapkRewind(InputStream stream, byte[] bytes) {
        int used = macapkEncodedLength(bytes);
        if (used >= bytes.length) return;
        try {
            stream.reset();
            long left = used;
            while (left > 0) {
                long skipped = stream.skip(left);
                if (skipped <= 0) break;
                left -= skipped;
            }
        } catch (Throwable ignored) { /* leave it where it is */ }
    }

    private static int macapkEncodedLength(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            int p = 8;
            while (p + 8 <= b.length) {
                int len = ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16)
                        | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff);
                if (len < 0) break;
                boolean iend = b[p + 4] == 'I' && b[p + 5] == 'E' && b[p + 6] == 'N'
                        && b[p + 7] == 'D';
                long next = (long) p + 12L + (long) len;
                if (next > b.length) break;
                p = (int) next;
                if (iend) return p;
            }
            return b.length;
        }
        if (b.length >= 2 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8) {
            for (int p = 2; p + 1 < b.length; p++) {
                if ((b[p] & 0xff) == 0xFF && (b[p + 1] & 0xff) == 0xD9) return p + 2;
            }
        }
        return b.length;
    }

    /** AOSP decodeStream javadoc: "If not null, return the padding rect for the
     *  bitmap if it exists, otherwise set padding to [-1,-1,-1,-1]" -- and, if
     *  no bitmap is returned, "padding is unchanged". We never touched the rect
     *  at all, so a caller that reused a Rect saw its own stale values and
     *  treated a plain PNG as a nine-patch (BMP.stream.padding.nonNinePatch). */
    private static void macapkFillPadding(Rect outPadding, Bitmap bm) {
        if (outPadding == null || bm == null) return;
        outPadding.set(-1, -1, -1, -1);
    }

    /** BitmapFactory.cpp:189-193, "Correct a non-positive sampleSize": anything
     *  <= 0 becomes 1 before any codec sees it, which is why inSampleSize=0
     *  (the field's own default) and even Integer.MIN_VALUE are value clamps
     *  and not errors. */
    private static int macapkSampleSize(Options opts) {
        if (opts == null) return 1;
        return opts.inSampleSize > 0 ? opts.inSampleSize : 1;
    }

    /** "the decoder will try to decode into this internal configuration. If it
     *  is null, or THE REQUEST CANNOT BE MET, the decoder will try to pick the
     *  best matching config" -- so inPreferredConfig is a HINT, and which hints
     *  are honoured is itself the answer. We honoured it literally, which is
     *  wrong in both directions: an RGBA source asked for RGB_565 silently LOST
     *  its alpha channel, and a colour source asked for ALPHA_8 lost its
     *  colour. AOSP's rules (BitmapFactory.cpp computeOutputColorType /
     *  :355-362):
     *   - RGB_565 is honoured only for an OPAQUE source; a source with alpha is
     *     upgraded to ARGB_8888 (BMP.cfg.rgba.565 = ARGB_8888 while
     *     BMP.cfg.rgb.565 = RGB_565).
     *   - ALPHA_8 is honoured only for a GRAYSCALE source -- the deliberate
     *     legacy kGray_8 -> kAlpha_8 fold, "in order to avoid a behavior
     *     change"; on a colour source computeOutputColorType refuses to drop
     *     the colour (BMP.cfg.gray.alpha8 = ALPHA_8 vs BMP.cfg.rgb.alpha8 =
     *     ARGB_8888).
     *   - ARGB_4444 is deprecated and has no decode target at all; it always
     *     becomes ARGB_8888 (BMP.cfg.rgb.4444). */
    private static Bitmap.Config macapkResolveConfig(Bitmap.Config pref,
            boolean srcHasAlpha, boolean srcIsGray) {
        if (pref == null) return Bitmap.Config.ARGB_8888;
        switch (pref) {
            case RGB_565:   return srcHasAlpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            case ALPHA_8:   return srcIsGray ? Bitmap.Config.ALPHA_8 : Bitmap.Config.ARGB_8888;
            case ARGB_4444: return Bitmap.Config.ARGB_8888;
            case RGBA_F16:  return Bitmap.Config.RGBA_F16;
            case HARDWARE:  return Bitmap.Config.HARDWARE;
            default:        return Bitmap.Config.ARGB_8888;
        }
    }

    /** Whether the ENCODED source carries an alpha channel. This is a property
     *  of the file header, never of the decoded pixels -- an RGB PNG whose
     *  pixels happen to be opaque and an RGBA PNG whose pixels happen to be
     *  opaque are different bitmaps (BMP.dec.rgb.shape alpha=0 vs
     *  BMP.dec.rgba.shape alpha=1). ImageIO's ColorModel reports exactly the
     *  codec's answer; the PNG-header sniff is the fallback for the native
     *  decode path, where there is no ColorModel to ask. */
    private static boolean macapkReaderHasAlpha(javax.imageio.ImageReader reader, byte[] bytes) {
        try {
            java.util.Iterator<javax.imageio.ImageTypeSpecifier> it = reader.getImageTypes(0);
            if (it != null && it.hasNext()) return it.next().getColorModel().hasAlpha();
        } catch (Throwable ignored) {}
        return macapkPngColourType(bytes) < 0 || macapkPngColourType(bytes) == 4
                || macapkPngColourType(bytes) == 6 || macapkPngColourType(bytes) == 3;
    }

    private static boolean macapkReaderIsGray(javax.imageio.ImageReader reader, byte[] bytes) {
        try {
            java.util.Iterator<javax.imageio.ImageTypeSpecifier> it = reader.getImageTypes(0);
            if (it != null && it.hasNext()) {
                java.awt.image.ColorModel cm = it.next().getColorModel();
                return cm.getColorSpace() != null
                        && cm.getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY;
            }
        } catch (Throwable ignored) {}
        int ct = macapkPngColourType(bytes);
        return ct == 0 || ct == 4;
    }

    /** IHDR byte 9 (RFC 2083): 0 = grey, 2 = RGB, 3 = palette, 4 = grey+alpha,
     *  6 = RGBA. -1 when the bytes are not a PNG. */
    private static int macapkPngColourType(byte[] b) {
        if (b == null || b.length < 26) return -1;
        if ((b[0] & 0xff) != 0x89 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') return -1;
        if (b[12] != 'I' || b[13] != 'H' || b[14] != 'D' || b[15] != 'R') return -1;
        return b[25] & 0xff;
    }

    /** Default ON. See macapkExtractArgb's grey+alpha branch. */
    static final boolean MACAPK_GRAY_ALPHA_RAW =
        !"0".equals(System.getenv("MACAPK_GRAY_ALPHA_RAW"));

    /** ⚠ img.getRGB() is WRONG for a grayscale image. A GRAY8 PNG decodes to
     *  TYPE_BYTE_GRAY, whose ColorSpace is CS_GRAY, and getRGB() runs a
     *  colour-space CONVERSION on the way out: the sample 0x40 comes back as
     *  0x898989, not 0x404040. Android does no such conversion -- Skia widens
     *  the grey sample into all three channels verbatim. MEASURED
     *  (BMP.dec.gray.hist / dec.gray.px / cfg.gray.*): every grey level in the
     *  fixture was wrong by the sRGB transfer function. Reading the raster
     *  sample directly is the fix, and it is why this method exists rather than
     *  a bare getRGB call. */
    private static int[] macapkExtractArgb(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] argb = new int[w * h];
        // GREY + ALPHA (PNG colour type 4) is the same trap one step over: ImageIO
        // hands it back as TYPE_CUSTOM over a ComponentColorModel in CS_GRAY, so
        // the TYPE_BYTE_GRAY test below never sees it and getRGB() brightens every
        // grey level by the sRGB transfer function. MEASURED on the platform's own
        // btn_default_small_normal.9.png (xhdpi, grey+alpha): raster sample 232
        // came back as 0xf5 (245), exactly encode_sRGB(232/255); the AVD draws the
        // face at sample value (its Menu face reads 237 -> 213 over white, which is
        // the raw samples 234 -> 211 at alpha 243). Skia widens the grey sample
        // verbatim for this colour type too. MACAPK_GRAY_ALPHA_RAW=0 restores the
        // getRGB conversion for a one-binary bisect. Checked offline by
        // debug/w5drawables_gate.sh (GR1/GR2 on hand-built PNGs, AB4 on the
        // platform button face), which also makes this a PREREQUISITE of
        // Resources' MACAPK_FW_BTN_TABLE: without it the table's classic face
        // reads 246 -> 236 where the AVD reads 237 -> 213.
        if (MACAPK_GRAY_ALPHA_RAW && img.getType() == BufferedImage.TYPE_CUSTOM) {
            java.awt.image.ColorModel cm = img.getColorModel();
            java.awt.image.Raster r = img.getRaster();
            if (cm != null && cm.getColorSpace() != null
                    && cm.getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY
                    && cm.hasAlpha() && !cm.isAlphaPremultiplied()
                    && r.getNumBands() == 2) {
                final int gMax = (1 << r.getSampleModel().getSampleSize(0)) - 1;
                final int aMax = (1 << r.getSampleModel().getSampleSize(1)) - 1;
                int i = 0;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int v = r.getSample(x, y, 0);
                        int a = r.getSample(x, y, 1);
                        if (gMax != 255) v = (v * 255 + gMax / 2) / gMax;
                        if (aMax != 255) a = (a * 255 + aMax / 2) / aMax;
                        v &= 0xff; a &= 0xff;
                        argb[i++] = (a << 24) | (v << 16) | (v << 8) | v;
                    }
                }
                return argb;
            }
        }
        if (img.getType() == BufferedImage.TYPE_BYTE_GRAY
                || img.getType() == BufferedImage.TYPE_USHORT_GRAY) {
            java.awt.image.Raster r = img.getRaster();
            final int max = img.getType() == BufferedImage.TYPE_USHORT_GRAY ? 65535 : 255;
            int i = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int v = r.getSample(x, y, 0);
                    if (max != 255) v = (v * 255) / max;
                    v &= 0xff;
                    argb[i++] = 0xff000000 | (v << 16) | (v << 8) | v;
                }
            }
            return argb;
        }
        img.getRGB(0, 0, w, h, argb, 0, w);
        return argb;
    }

    /** Trim a subsampled decode (which is ceil(w/n) wide) down to AOSP's
     *  floor(w/n). The difference is at most one row and one column, so taking
     *  the top-left block is exact rather than a resample. */
    private static int[] macapkCrop(int[] src, int w, int h, int outW, int outH) {
        int[] out = new int[outW * outH];
        for (int y = 0; y < outH && y < h; y++) {
            System.arraycopy(src, y * w, out, y * outW, Math.min(outW, w));
        }
        return out;
    }

    /** Decode `bytes` via the native macOS image codec (CGImageSource),
     *  which handles webp/heic and anything else Java's ImageIO can't.
     *  Returns null on failure so the caller can report "no decoder."
     *  v1.0.408: added so Jetpack Compose's painterResource (which THROWS
     *  ResourceResolutionException on a null Bitmap) renders webp drawables
     *  instead of aborting the whole composition (Reddit welcome-screen
     *  background was welcome_background_2.webp). */
    private static Bitmap macapkNativeDecode(byte[] bytes, Options opts) {
        try {
            int[] px = com.macapk.loader.NativeImageCodec.decode(bytes);
            if (px == null || px.length < 2) return null;
            int w = px[0], h = px[1];
            if (w <= 0 || h <= 0 || (long) px.length < 2L + (long) w * h) return null;
            // No ColorModel to ask on this path, so the source's alpha/grey
            // comes from the container header; see macapkPngColourType.
            final int ct = macapkPngColourType(bytes);
            final boolean srcHasAlpha = (ct < 0) || ct == 3 || ct == 4 || ct == 6;
            final boolean srcIsGray = (ct == 0 || ct == 4);
            final int sample = macapkSampleSize(opts);
            final int outW = Math.max(1, w / sample);
            final int outH = Math.max(1, h / sample);
            if (opts != null) {
                opts.outWidth = outW; opts.outHeight = outH;
                if (opts.outMimeType == null) opts.outMimeType = "image/webp";
                opts.outConfig = macapkResolveConfig(
                        opts.inPreferredConfig, srcHasAlpha, srcIsGray);
                if (opts.inJustDecodeBounds) return null;
            }
            int[] argb = new int[w * h];
            System.arraycopy(px, 2, argb, 0, w * h);
            if (outW != w || outH != h) {
                // Nearest sample: this path has no codec-level subsampling to
                // ask for, and the DIMENSIONS are what the contract pins.
                // The kept source index is sample*i + sample/2, matching
                // Skia's get_start_coord(sampleFactor) = sampleFactor/2
                // (external/skia @ android11-release,
                // src/codec/SkCodecPriv.h:42-55) -- see the sibling fix at
                // the ImageIO setSourceSubsampling call above, and
                // docs/fwprobe-bitmap-raster-divergences_2026-08-10.md
                // cluster 2. The existing Math.min(w-1/h-1, ...) clamp
                // already covers the tail once the +sample/2 term is added.
                int[] small = new int[outW * outH];
                for (int y = 0; y < outH; y++) {
                    int srow = Math.min(h - 1, y * sample + sample / 2) * w;
                    for (int x = 0; x < outW; x++) {
                        small[y * outW + x] = argb[srow + Math.min(w - 1, x * sample + sample / 2)];
                    }
                }
                argb = small; w = outW; h = outH;
            }
            return macapkFinishDecode(w, h, argb, opts, srcHasAlpha, srcIsGray);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        return decodeByteArray(data, offset, length, null);
    }
    /** AOSP decodeByteArray (BitmapFactory.java:655-687). Its own guard is
     *  `if ((offset | length) < 0 || data.length < offset + length) throw new
     *  ArrayIndexOutOfBoundsException()` -- note the OR of the two ints, which
     *  makes a negative in EITHER position the same answer, and note that it is
     *  AIOOBE and NOT IllegalArgumentException. A null array reaches
     *  `data.length` and is a NullPointerException. We returned null for all
     *  four, which is the same answer an unreadable image gives, so a caller
     *  could not tell a bad argument from a bad image
     *  (BMP.err.negOffset / negLength / lengthPastEnd / nullArray). */
    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options opts) {
        if ((offset | length) < 0 || data.length < offset + length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return decodeStream(new ByteArrayInputStream(data, offset, length), null, opts);
    }

    public static Bitmap decodeFile(String pathName) {
        return decodeFile(pathName, null);
    }
    public static Bitmap decodeFile(String pathName, Options opts) {
        if (pathName == null) return null;
        try (FileInputStream in = new FileInputStream(pathName)) {
            return decodeStream(in, null, opts);
        } catch (Throwable t) {
            android.util.Log.w("BitmapFactory", "decodeFile(" + pathName + "): " + t);
            return null;
        }
    }

    public static Bitmap decodeResource(android.content.res.Resources res, int id) {
        return decodeResource(res, id, null);
    }
    public static Bitmap decodeResource(android.content.res.Resources res, int id, Options opts) {
        // DIAGNOSTIC (MACAPK_TRACE_DECODERES=1): every decodeResource with its
        // outcome. tobsolitaire's game-grid thumbnails all come back null and the
        // card atlas never appears in the openRawResource log -- yet the only
        // path that skips openRawResource is `res == null`, which the app's own
        // init should have prevented. This says which it is, instead of another
        // round of bytecode reading (which has already produced a contradiction).
        final boolean trace = "1".equals(System.getenv("MACAPK_TRACE_DECODERES"));
        if (res == null) {
            if (trace) android.util.Log.w("Mac.apk.DecodeRes",
                "decodeResource(0x" + Integer.toHexString(id) + ") res==NULL -> null");
            return null;
        }
        try (InputStream in = res.openRawResource(id)) {
            Bitmap b = decodeStream(in, null, opts);
            if (trace) android.util.Log.i("Mac.apk.DecodeRes",
                "decodeResource(0x" + Integer.toHexString(id) + ") stream="
                + (in == null ? "NULL" : "ok") + " -> "
                + (b == null ? "NULL BITMAP" : (b.getWidth() + "x" + b.getHeight())));
            return b;
        } catch (Throwable t) {
            android.util.Log.w("BitmapFactory", "decodeResource(" + Integer.toHexString(id) + "): " + t);
            return null;
        }
    }

    /** AOSP BitmapFactory.decodeResourceStream
     *  (frameworks/base/graphics/java/android/graphics/BitmapFactory.java):
     *  decode a stream opened from a resource, applying the resource's density
     *  (value.density) so the bitmap is scaled to the target density. THIS is the
     *  method Jetpack Compose's `ImageBitmap.imageResource` / `painterResource`
     *  calls for a non-vector drawable (BitmapFactory.decodeResourceStream(res,
     *  value, openRawResource(id), null, opts)); without it the call is a
     *  NoSuchMethodError thrown INSIDE the composition, which aborts the whole
     *  Compose screen content (e.g. Reddit's WelcomeV2 SingleImage screen draws
     *  nothing -- its full-screen welcome_background_2.webp Image plus the
     *  sign-up/login buttons -- so the login screen paints blank). We delegate
     *  to decodeStream (which already handles webp/heic via NativeImageCodec);
     *  density scaling is approximated by setting inDensity/inTargetDensity so
     *  decodeStream's consumer sees the right size. Generic across any Compose /
     *  AOSP app that loads a bitmap drawable resource. */
    public static Bitmap decodeResourceStream(android.content.res.Resources res,
            android.util.TypedValue value, InputStream is, Rect pad, Options opts) {
        if (opts == null) opts = new Options();
        // Mirror AOSP: seed inDensity from the resource's density bucket and
        // inTargetDensity from the display, so a sampled/scaled decode lands at
        // the display density. Only fill when unset, so explicit opts win.
        try {
            // AOSP: density==0 means DENSITY_DEFAULT, density==0xffff (DENSITY_NONE)
            // means "no scaling". Only carry a real bucket density through.
            if (opts.inDensity == 0 && value != null
                && value.density != 0 && value.density != 0xffff) {
                opts.inDensity = value.density;
            }
            if (opts.inTargetDensity == 0 && res != null
                && res.getDisplayMetrics() != null) {
                opts.inTargetDensity = res.getDisplayMetrics().densityDpi;
            }
        } catch (Throwable ignored) {}
        Bitmap b = decodeStream(is, pad, opts);
        if (System.getenv("MACAPK_TRACE_COMPOSE_FALLBACK") != null) {
            android.util.Log.i("Mac.apk.BitmapDecode", "decodeResourceStream -> "
                + (b == null ? "NULL" : (b.getWidth() + "x" + b.getHeight()))
                + " density=" + (value != null ? value.density : -1));
        }
        return b;
    }

    public static Bitmap decodeFileDescriptor(java.io.FileDescriptor fd) {
        return decodeFileDescriptor(fd, null, null);
    }

    /** AOSP javadoc (frameworks/base graphics/java/android/graphics/BitmapFactory.java,
     *  fetched verbatim): "Decode a bitmap from the file descriptor. If the bitmap
     *  cannot be decoded return null. The position within the descriptor will not
     *  be changed when this returns, so the descriptor can be used again as-is."
     *  Was hardcoded to `return null` unconditionally, independent of whether `fd`
     *  was a valid, readable descriptor -- the standard path for a picked gallery /
     *  camera-roll / content:// image
     *  (ContentResolver.openFileDescriptor(uri) ->
     *  decodeFileDescriptor(pfd.getFileDescriptor())) always silently failed, and
     *  our own BitmapRegionDecoder.newInstance(FileDescriptor,...) calls through
     *  here too, so that entry point was transitively broken. FileInputStream
     *  (FileDescriptor) shares the caller's underlying open-file position (both
     *  wrap the same OS descriptor), so we save + restore it around the read to
     *  honor the "position unchanged" half of the contract; a non-seekable fd
     *  (pipe/socket) just skips the restore, matching AOSP's own non-seekable
     *  fallback (buffered decode, no position guarantee there either). */
    public static Bitmap decodeFileDescriptor(java.io.FileDescriptor fd, Rect outPadding, Options opts) {
        if (fd == null || !fd.valid()) return null;
        java.nio.channels.FileChannel ch = null;
        long savedPos = -1;
        try {
            FileInputStream in = new FileInputStream(fd);
            try { ch = in.getChannel(); savedPos = ch.position(); }
            catch (Throwable notSeekable) { ch = null; }
            // Deliberately do NOT close `in`: AOSP's contract leaves the caller's
            // descriptor open and usable afterward, and closing a FileInputStream
            // built over a caller-owned FileDescriptor closes the shared native
            // fd too (it is not a private copy).
            return decodeStream(in, outPadding, opts);
        } catch (Throwable t) {
            android.util.Log.w("BitmapFactory", "decodeFileDescriptor: " + t);
            return null;
        } finally {
            if (ch != null) {
                try { ch.position(savedPos); } catch (Throwable ignored) {}
            }
        }
    }

    // ⚠ THE DENSITY-SCALE STEP MOVED into macapkFinishDecode. It has to run
    // AFTER the sample step and BELOW the inJustDecodeBounds early return, and
    // it needed the inScreenDensity guard this version never had. The measured
    // history is kept here because it is why the step exists at all:
    //
    // AOSP Options.inDensity/inTargetDensity/inScaled (graphics/java/
        // android/graphics/BitmapFactory.java, fetched 2026-07-31): "if
        // inScaled is set (which it is by default) and this density does
        // not match inTargetDensity, then the bitmap will be scaled to the
        // target density before being returned." This is the SECOND, FINE-
        // GRAINED half of AOSP's decode-time scaling contract -- inSampleSize
        // (handled above, coarse power-of-2 source subsampling) is the
        // first half. We only implemented the first half; every actual
        // pixel decode ignored these three fields entirely.
        // MEASURED gap (Fossify Gallery bring-up, 2026-07-31): Glide's
        // Downsampler deliberately relies on BOTH halves together --
        // inSampleSize gets it CLOSE (a power-of-2 reduction), then
        // inDensity/inTargetDensity is set to a precise (often large,
        // non-"real"-density-looking) ratio to land EXACTLY on the target
        // size, which is a documented, standard Glide technique, not a bug
        // in Glide. When Glide's power-of-2 step alone is already
        // sufficient (sample size 1), it relies ENTIRELY on this density
        // scale to shrink the image -- e.g. a 768x768 PNG scaled to a
        // 402x402 `inBitmap`. Without this fix we returned the full
        // 768x768 decode, whose byte count (2,359,296) vastly exceeds the
        // 402x402 pooled bitmap's true allocation (646,416,
        // correctly-tracked as of this session's Bitmap.java fix), so
        // BitmapFactory correctly-per-the-check-but-wrongly-per-root-cause
        // threw "Problem decoding into existing bitmap: bitmap is too
        // small" -- the LAST of four layered bugs found chasing this one
        // Fossify symptom (see docs/fossify-newpipe_2026-07-31.md).
        // Generic: any caller (Glide, Coil, hand-rolled AOSP-style
        // "efficient bitmap loading" code) that sets inDensity/
        // inTargetDensity hits this identically; nothing here is
        // Fossify-specific.

    /** Resize a decoded BufferedImage to (w,h) for the inDensity/
     *  inTargetDensity scale step. Bilinear, because that is what this AOSP step
     *  uses, and specifically SKIA'S bilinear rather than Java2D's.
     *
     *  The legacy 4-bit-weight sampler is not merely likely here, it is forced:
     *  BitmapFactory.cpp builds the resize canvas as
     *  `SkCanvas canvas(outputBitmap, SkCanvas::ColorBehavior::kLegacy)`, which
     *  is exactly the path SkBitmapProcState serves. Java2D's full-precision,
     *  rounding lerp answered a decode at inDensity=160 / inTargetDensity=240
     *  with 3-4 per channel of error against a real device
     *  (BMP.lossy.resize.up15x). Bitmap.macapkResample carries the contract and
     *  the citations; this call site just supplies an axis-aligned scale. */
    private static BufferedImage macapkScaleImage(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int sw = src.getWidth(), sh = src.getHeight();
        if (w > 0 && h > 0 && sw > 0 && sh > 0) {
            int[] in = new int[sw * sh];
            src.getRGB(0, 0, sw, sh, in, 0, sw);
            // Skia scales the CANVAS by dst/src and draws the bitmap at the
            // origin, so the forward transform is exactly this.
            java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform(
                    w / (double) sw, 0, 0, h / (double) sh, 0, 0);
            int[] px = android.graphics.Bitmap.macapkResample(in, sw, sh, w, h, at, true, false);
            out.setRGB(0, 0, w, h, px, 0, w);
        }
        return out;
    }

    /** Sniff the encoded image format from raw bytes for Options.outMimeType,
     *  reusing the same ImageIO reader lookup the sampled/bounds decode path
     *  above already does (so both paths agree on the same format for the same
     *  bytes). Returns null when no registered ImageIO reader recognizes the
     *  bytes (e.g. webp -- macapkNativeDecode sets outMimeType itself for that
     *  path); the caller falls back to a safe default in that case. */
    private static String macapkSniffMimeType(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (javax.imageio.stream.ImageInputStream iis =
                 ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) return null;
            java.util.Iterator<javax.imageio.ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) return null;
            javax.imageio.ImageReader reader = it.next();
            try {
                return "image/" + reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
            } finally {
                reader.dispose();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** Shared tail of every decode path: build the Bitmap from freshly decoded
     *  ARGB pixels, apply the requested Config / mutability / inBitmap reuse, and
     *  populate outConfig. AOSP: BitmapFactory.decode* returns an IMMUTABLE
     *  bitmap unless Options.inMutable is set, while Bitmap.createBitmap(w,h,
     *  Config) always returns a MUTABLE one (see Bitmap.java's own "createBitmap
     *  (...) returns a MUTABLE bitmap; BitmapFactory.decode* returns an
     *  IMMUTABLE one" contract comment) -- and inPreferredConfig/outConfig were
     *  read/written nowhere in this file, so every decode hardcoded ARGB_8888 +
     *  mutable=true regardless of what Options asked for. We still store pixels
     *  as a plain ARGB int[] (createBitmap(ARGB_8888) is the only allocation
     *  path that never throws, even for a Config.HARDWARE request, since
     *  Bitmap.createBitmap rejects HARDWARE outright) and RETAG via
     *  reconfigure() to whatever Config was actually requested -- the same
     *  "config is a metadata tag over the same storage" pattern
     *  createBitmap(w,h,Config) already uses for every non-decode bitmap.
     *  copy(config,false) sheds mutability for the (default) immutable case;
     *  Bitmap.java's checkHardware() gate already makes a HARDWARE-tagged
     *  bitmap correctly refuse getPixels()/setPixel() while our own internal
     *  macapkArgbBuffer() (what the GL upload path reads) stays exempt, so
     *  tagging HARDWARE here doesn't break rendering. */
    private static Bitmap macapkFinishDecode(int w, int h, int[] argb, Options opts,
                                             boolean srcHasAlpha, boolean srcIsGray) {
        // THE DENSITY RESIZE, the native half of the scaling contract
        // (BitmapFactory.cpp:214-220). Note the FOURTH clause, `density !=
        // screenDensity`: it is Android's density-compatibility escape hatch,
        // and because nothing else in the framework ever reads inScreenDensity
        // it is the one guard a reimplementation always omits -- ours did, so a
        // decode with inDensity == inScreenDensity was scaled when AOSP leaves
        // it alone (BMP.scale.screenEqDensity: emulator 24x24 dens=160, we
        // answered 48x48 dens=320). The rounding is `(int)(w * scale + 0.5f)`,
        // which for a positive scale is Math.round.
        if (opts != null && opts.inScaled && opts.inDensity != 0 && opts.inTargetDensity != 0
                && opts.inDensity != opts.inScreenDensity) {
            float scale = (float) opts.inTargetDensity / (float) opts.inDensity;
            if (scale != 1.0f) {
                int scaledW = Math.max(1, Math.round(w * scale));
                int scaledH = Math.max(1, Math.round(h * scale));
                if (scaledW != w || scaledH != h) {
                    BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    tmp.setRGB(0, 0, w, h, argb, 0, w);
                    BufferedImage big = macapkScaleImage(tmp, scaledW, scaledH);
                    argb = new int[scaledW * scaledH];
                    big.getRGB(0, 0, scaledW, scaledH, argb, 0, scaledW);
                    w = scaledW; h = scaledH;
                }
            }
        }
        Bitmap.Config cfg = macapkResolveConfig(
                opts != null ? opts.inPreferredConfig : null, srcHasAlpha, srcIsGray);
        if (cfg == Bitmap.Config.ALPHA_8) {
            // BitmapFactory.cpp:355-362 -- the legacy kGray_8 -> kAlpha_8 fold.
            // The grey LEVEL becomes the ALPHA and the colour channels go to
            // zero, which is what an ALPHA_8 bitmap can hold
            // (BMP.cfg.gray.alpha8.hist = 00000000/40000000/80000000/ff000000).
            for (int i = 0; i < argb.length; i++) argb[i] = (argb[i] & 0xff) << 24;
        }
        Bitmap fresh = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        fresh.macapkSetArgbBuffer(argb);
        // Retag (not reconfigure()): width/height are UNCHANGED here, only
        // the Config label is -- and reconfigure()'s new allocation check
        // (added 2026-07-31, see Bitmap.java) would spuriously reject an
        // RGBA_F16 request (8 bytes/pixel) against the 4-bytes/pixel
        // ARGB_8888 allocation this method always creates first, even
        // though nothing is actually unsafe (our backing store is always a
        // plain int[] regardless of the Config tag). See
        // macapkRetagConfigAfterCreate's own doc comment.
        if (cfg != Bitmap.Config.ARGB_8888) fresh.macapkRetagConfigAfterCreate(cfg);
        // ⚠ ORDER: the alpha stamp has to come AFTER the config retag, because
        // the alpha type is validated against the COLOUR TYPE (an RGB_565
        // bitmap can only be opaque). Stamping first and retagging second
        // leaves a 565 bitmap claiming premultiplied alpha (BMP.cfg.mutable565).
        //
        // The decoded bitmap's alpha type comes from the SOURCE's opacity and
        // the app's inPremultiplied request, not from the config alone: an RGB
        // PNG decodes kOpaque even into an ARGB_8888 bitmap (hasAlpha() 0,
        // BMP.dec.rgb.shape), and inPremultiplied=false asks for
        // unpremultiplied storage (BMP.cfg.premul.rgba.false). An ALPHA_8
        // result carries alpha by construction whatever the source was
        // (BMP.cfg.gray.alpha8.shape = alpha=1 premul=1).
        fresh.macapkApplyDecodedAlpha(srcHasAlpha || cfg == Bitmap.Config.ALPHA_8,
                opts == null || opts.inPremultiplied);
        if (opts != null) opts.outConfig = cfg;
        // ⚠ An inBitmap that cannot be reused is DROPPED, not fatal, and the
        // decode then follows the ordinary rules -- including immutability,
        // which is why the fall-through goes to the same tail below rather than
        // returning `fresh` (a createBitmap result, and therefore mutable).
        // BMP.reuse.immutable.shape measures exactly that: mut=0.
        Bitmap reuse = (opts != null) ? opts.inBitmap : null;
        if (reuse != null && !reuse.isRecycled() && reuse.isMutable()) {
            return macapkReuseInBitmap(reuse, fresh);
        }
        boolean mutable = opts != null && opts.inMutable;
        return mutable ? fresh : fresh.copy(cfg, false);
    }

    /** AOSP Options.inBitmap contract (javadoc, fetched verbatim): "decode
     *  methods that take the Options object will attempt to reuse this bitmap
     *  when loading content ... If the decode operation cannot use this bitmap,
     *  the decode method will throw an IllegalArgumentException." KitKat+ rule:
     *  any MUTABLE, non-recycled bitmap can be reused as long as the freshly
     *  decoded result's byte count fits within the reused bitmap's allocation
     *  (a HARDWARE-config inBitmap can never satisfy this -- Bitmap.isMutable()
     *  already reports false for Config.HARDWARE, so the mutability check alone
     *  covers that case too). `inBitmap` was declared but grep-confirmed to be
     *  read nowhere else in this file -- every decode silently allocated a
     *  fresh, disconnected Bitmap no matter what was passed. `fresh` already
     *  carries the correctly decoded pixels + resolved config; reconfigure
     *  `reuse` to that shape and hand its pixels over (matching AOSP's in-place
     *  reuse, returning the SAME object the caller passed in), or throw per the
     *  documented failure contract. */
    private static Bitmap macapkReuseInBitmap(Bitmap reuse, Bitmap fresh) {
        // ⚠ AN IMMUTABLE inBitmap IS NOT AN ERROR -- see the caller, which
        // filters that case out before we get here. Options.validate rejects a
        // recycled or HARDWARE inBitmap in Java, but immutability is not
        // checked there: the native side simply LOGS and drops the reuse
        // (BitmapFactory.cpp:320-326) and the decode proceeds as an ordinary
        // one. Throwing was wrong in the direction that breaks apps -- a pool
        // that hands back an immutable bitmap got an exception where Android
        // returns a perfectly good image (BMP.reuse.immutable.*: emulator
        // answers a 24x24 bitmap with same=0, we answered NULL because
        // decodeStream swallowed the throw).
        //
        // A reuse that CANNOT fit returns null from the native decode, and the
        // decode* entry point turns that null into
        // IllegalArgumentException("Problem decoding into existing bitmap")
        // (BitmapFactory.java:669-671). Returning null here rather than
        // throwing directly puts the exception where AOSP raises it, which
        // matters because the same conversion also has to fire for a decode
        // that failed for an unrelated reason -- a MALFORMED image with an
        // inBitmap set is an IAE, not a null (BMP.reuse.malformedWithInBitmap,
        // BMP.stream.reuse.malformed, BMP.reuse.tooSmall.*, smallerConfig.*).
        if (fresh.getByteCount() > reuse.getAllocationByteCount()) return null;
        reuse.reconfigure(fresh.getWidth(), fresh.getHeight(), fresh.getConfig());
        reuse.macapkSetArgbBuffer(fresh.macapkArgbBuffer());
        // AOSP: the immutability stamp is guarded by `javaBitmap == NULL`
        // (BitmapFactory.cpp:478), so a REUSED bitmap keeps the mutability it
        // already had regardless of inMutable -- and it takes the freshly
        // decoded alpha state.
        reuse.macapkApplyDecodedAlpha(fresh.hasAlpha(), fresh.isPremultiplied());
        return reuse;
    }
}
