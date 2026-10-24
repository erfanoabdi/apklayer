package android.graphics.fonts;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.LocaleList;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * One font file (one face), with the metadata the text stack needs to pick it.
 *
 * Was a 1-line auto-generated stub, so the whole android.graphics.fonts API
 * introduced in API 29 -- and androidx.core's FontsContractCompat path that
 * uses it -- failed on construction. 27 of 37 corpus apps reference the
 * Builder.
 *
 * A Font is a value object here: it holds the bytes (or the file) plus the
 * declared weight/slant, and Typeface.CustomFallbackBuilder turns the first one
 * into a real AWT-backed Typeface via Typeface.createFromFile, so a custom font
 * built through this API actually renders rather than silently falling back.
 *
 * Source: frameworks/base/graphics/java/android/graphics/fonts/Font.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/graphics/java/android/graphics/fonts/Font.java
 */
public final class Font {

    private final File mFile;
    private final ByteBuffer mBuffer;
    private final FontStyle mStyle;
    private final int mTtcIndex;
    private final String mFontVariationSettings;
    private final LocaleList mLocaleList;

    Font(File file, ByteBuffer buffer, FontStyle style, int ttcIndex,
         String fontVariationSettings, LocaleList localeList) {
        mFile = file;
        mBuffer = buffer;
        mStyle = style;
        mTtcIndex = ttcIndex;
        mFontVariationSettings = fontVariationSettings;
        mLocaleList = localeList;
    }

    /**
     * The system font, as a Font value.
     *
     * AOSP hands out a real per-glyph Font from the shaper's font-fallback
     * chain. Mac.apk renders every run with one system face (see MacApkFont),
     * so this singleton is the accurate answer to "which font drew this glyph"
     * rather than a fabricated one: null file and null buffer say plainly that
     * it is the platform font, not something the app supplied.
     */
    public static Font macapkSystemFont() { return SYSTEM; }

    private static final Font SYSTEM = new Font(
            null, null, new FontStyle(FontStyle.FONT_WEIGHT_NORMAL,
                                      FontStyle.FONT_SLANT_UPRIGHT),
            0, null, null);

    /** The backing file, or null when the font came from a buffer/asset. */
    public File getFile() { return mFile; }

    public ByteBuffer getBuffer() { return mBuffer; }

    public FontStyle getStyle() { return mStyle; }

    public int getTtcIndex() { return mTtcIndex; }

    public String getFontVariationSettings() { return mFontVariationSettings; }

    /** AOSP Font.getAxes() (Font.java:633) -- the VARIATION AXES the caller
     *  requested on this Font, or null when it has none. AOSP reads them back
     *  out of the native font; we parse the settings string we were handed,
     *  which is the same set for every Font built through Font.Builder (the
     *  only way an app makes one). Returns null, not an empty array, when
     *  nothing was set -- the getter is @Nullable and callers test for null. */
    public FontVariationAxis[] getAxes() {
        if (mFontVariationSettings == null) return null;
        try {
            return FontVariationAxis.fromFontVariationSettings(mFontVariationSettings);
        } catch (Throwable t) {
            return null;
        }
    }

    /** AOSP Font.getSourceIdentifier() (Font.java:764) -- "an unique identifier
     *  for the font SOURCE DATA". AOSP's documented use is a cache key, paired
     *  with getTtcIndex(); two Fonts derived from the same file/buffer must
     *  share it even when their variation settings differ, and two unrelated
     *  sources must not collide.
     *
     *  So it is keyed on the SOURCE only -- the file path, or the buffer's
     *  identity -- and deliberately NOT on style/ttcIndex/variation settings,
     *  which is what makes the documented `id << 32 | ttcIndex` cache key work. */
    public int getSourceIdentifier() {
        if (mFile != null) return mFile.getAbsolutePath().hashCode();
        if (mBuffer != null) return System.identityHashCode(mBuffer);
        return 0;
    }

    /** AOSP Font.getMetrics(paint, outMetrics) (Font.java:703) -- fills in the
     *  font metrics THIS font would produce at the paint's size. We render every
     *  run through one AWT face selected from the Paint, so the honest answer is
     *  the paint's own metrics; a null out param is legal and means "just
     *  compute nothing" (AOSP passes it straight to native, which ignores it). */
    public void getMetrics(android.graphics.Paint paint,
                           android.graphics.Paint.FontMetrics outMetrics) {
        if (paint == null) throw new NullPointerException("paint must not be null");
        if (outMetrics == null) return;
        paint.getFontMetrics(outMetrics);
    }

    /** AOSP Font.getGlyphBounds(glyphId, paint, outBoundingBox) (Font.java:685)
     *  -- the glyph's advance, with its bounding box written into outBoundingBox.
     *
     *  ⚠ WE CANNOT ANSWER THIS. It is keyed by GLYPH ID, which is an index into
     *  the font's own glyf/CFF table; getting one requires a shaper, and we have
     *  none (see TextRunShaper/PositionedGlyphs in android.graphics.text, which
     *  are in the same position). Returning a fabricated advance would be worse
     *  than returning nothing, so this reports a zero advance and an EMPTY box,
     *  which is the "no such glyph" answer rather than a wrong glyph's. */
    public float getGlyphBounds(int glyphId, android.graphics.Paint paint,
                                android.graphics.RectF outBoundingBox) {
        if (paint == null) throw new NullPointerException("paint must not be null");
        if (outBoundingBox != null) outBoundingBox.setEmpty();
        return 0f;
    }

    public LocaleList getLocaleList() {
        return mLocaleList != null ? mLocaleList : LocaleList.getDefault();
    }

    @Override public String toString() {
        return "Font { file=" + mFile + ", style=" + mStyle
                + ", ttcIndex=" + mTtcIndex + "}";
    }

    public static final class Builder {

        private File mFile;
        private ByteBuffer mBuffer;
        private int mWeight = FontStyle.FONT_WEIGHT_UNSPECIFIED;
        private int mSlant = -1;
        private int mTtcIndex;
        private String mFontVariationSettings;
        private LocaleList mLocaleList;
        private final Throwable mException;

        public Builder(File path) { mFile = path; mException = null; }

        public Builder(ByteBuffer buffer) { mBuffer = buffer; mException = null; }

        public Builder(Font font) {
            mFile = font.mFile;
            mBuffer = font.mBuffer;
            mWeight = font.mStyle != null ? font.mStyle.getWeight() : FontStyle.FONT_WEIGHT_UNSPECIFIED;
            mSlant = font.mStyle != null ? font.mStyle.getSlant() : -1;
            mTtcIndex = font.mTtcIndex;
            mFontVariationSettings = font.mFontVariationSettings;
            mLocaleList = font.mLocaleList;
            mException = null;
        }

        /**
         * AOSP defers the failure: a Builder constructed from an unreadable
         * source records the exception and rethrows it from build(), rather
         * than throwing here. Callers rely on that ordering.
         */
        public Builder(AssetManager am, String path) {
            Throwable err = null;
            try (InputStream is = am.open(path)) {
                mBuffer = readFully(is);
            } catch (Throwable t) { err = t; }
            mException = err;
        }

        public Builder(Resources res, int resId) {
            Throwable err = null;
            try (InputStream is = res.openRawResource(resId)) {
                mBuffer = readFully(is);
            } catch (Throwable t) { err = t; }
            mException = err;
        }

        public Builder(ParcelFileDescriptor pfd) {
            this(pfd, 0, -1);
        }

        /** AOSP Font.Builder(ParcelFileDescriptor, offset, size) (Font.java:167).
         *  size == -1 means "to the end of the file, from offset". AOSP memory-
         *  maps the region; we read it, which is observationally the same for a
         *  ByteBuffer the caller only reads. */
        public Builder(ParcelFileDescriptor pfd, long offset, long size) {
            Throwable err = null;
            ByteBuffer buf = null;
            try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor())) {
                java.nio.channels.FileChannel fc = fis.getChannel();
                long len = (size == -1) ? fc.size() - offset : size;
                buf = fc.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, offset, len);
            } catch (Throwable t) { err = t; }
            mBuffer = buf;
            mException = err;
        }

        private static ByteBuffer readFully(InputStream is) throws java.io.IOException {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return ByteBuffer.wrap(bos.toByteArray());
        }

        public Builder setWeight(int weight) {
            if (weight < FontStyle.FONT_WEIGHT_MIN || weight > FontStyle.FONT_WEIGHT_MAX) {
                throw new IllegalArgumentException("weight must be [1, 1000]");
            }
            mWeight = weight;
            return this;
        }

        public Builder setSlant(int slant) {
            if (slant != FontStyle.FONT_SLANT_UPRIGHT && slant != FontStyle.FONT_SLANT_ITALIC) {
                throw new IllegalArgumentException("slant must be FONT_SLANT_UPRIGHT or FONT_SLANT_ITALIC");
            }
            mSlant = slant;
            return this;
        }

        public Builder setTtcIndex(int ttcIndex) {
            if (ttcIndex < 0) throw new IllegalArgumentException("ttcIndex must not be negative");
            mTtcIndex = ttcIndex;
            return this;
        }

        public Builder setFontVariationSettings(String settings) {
            mFontVariationSettings = settings;
            return this;
        }

        public Builder setFontVariationSettings(FontVariationAxis[] axes) {
            mFontVariationSettings = FontVariationAxis.toFontVariationSettings(axes);
            return this;
        }

        public Font build() throws java.io.IOException {
            if (mException != null) {
                throw new java.io.IOException("Failed to read font contents", mException);
            }
            int weight = mWeight == FontStyle.FONT_WEIGHT_UNSPECIFIED
                    ? FontStyle.FONT_WEIGHT_NORMAL : mWeight;
            int slant = mSlant == -1 ? FontStyle.FONT_SLANT_UPRIGHT : mSlant;
            return new Font(mFile, mBuffer, new FontStyle(weight, slant),
                    mTtcIndex, mFontVariationSettings, mLocaleList);
        }
    }
}
