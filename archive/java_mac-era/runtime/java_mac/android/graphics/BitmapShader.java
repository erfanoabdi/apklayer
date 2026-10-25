// android.graphics.BitmapShader -- a Shader that tiles/clamps a Bitmap as a fill
// (rounded-corner images, textured fills: Paint.setShader(new BitmapShader(...))).
// v1.0.426 Tier-2: was an empty stub that did NOT extend Shader, so passing one
// to Paint.setShader / constructing it where a Shader is expected failed at the
// type level. Now extends Shader and stores the bitmap + tile modes. Stale-
// comment fix: this used to say "our Canvas doesn't yet sample the shader" --
// not true since v1.0.433/434: Canvas.awtPaintForShader samples it (since
// v1.0.2271 through MacApkBitmapShaderPaint, honouring the local matrix and the
// tile modes, when either matters; a TexturePaint otherwise).
// Source: frameworks/base/graphics/java/android/graphics/BitmapShader.java
package android.graphics;

public class BitmapShader extends Shader {
    public final Bitmap bitmap;
    public final TileMode tileX;
    public final TileMode tileY;

    // AOSP added FilterMode at API 33 (frameworks/base graphics/java/android/
    // graphics/BitmapShader.java:39-80, android13-release): it picks how the
    // shader samples between texels, overriding Paint#isFilterBitmap once set
    // to a non-default value. AOSP's setFilterMode backs this by discarding a
    // native Skia instance so the next draw rebuilds it with new
    // SamplingOptions. Since v1.0.2271 Canvas.awtPaintForShader reads it when it
    // samples through MacApkBitmapShaderPaint (a shader with a local matrix or a
    // CLAMP / DECAL axis): LINEAR = bilinear, NEAREST = nearest, DEFAULT = the
    // Paint's FILTER_BITMAP flag. A plain REPEAT / MIRROR shader with no matrix
    // is still a java.awt.TexturePaint, whose interpolation is the Graphics2D
    // hint. There is no native instance to discard.
    public static final int FILTER_MODE_DEFAULT = 0;
    public static final int FILTER_MODE_NEAREST = 1;
    public static final int FILTER_MODE_LINEAR = 2;
    private int filterMode = FILTER_MODE_DEFAULT;

    public BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY) {
        this.bitmap = bitmap; this.tileX = tileX; this.tileY = tileY;
    }
    public Bitmap getBitmap() { return bitmap; }

    /** AOSP BitmapShader.getFilterMode(): the filter mode last set, or
     *  FILTER_MODE_DEFAULT if setFilterMode was never called. */
    public int getFilterMode() { return filterMode; }
    /** AOSP BitmapShader.setFilterMode(int): one of the FILTER_MODE_* values
     *  above. AOSP guards this with "if (mode != mFilterMode)" purely to skip
     *  the native discard when nothing changed; we have no native instance to
     *  discard, so the guard has nothing to protect and is omitted. */
    public void setFilterMode(int mode) { filterMode = mode; }

    /** AOSP BitmapShader.setMaxAnisotropy(int) (API 35). Enables anisotropic
     *  filtering at the given sample count; AOSP notes it OVERRIDES the filter
     *  mode when > 1. Skia does the sampling; java.awt's TexturePaint has no
     *  anisotropy control, so this is stored and read back (same posture as
     *  filterMode above, which Canvas.awtPaintForShader also does not consult). */
    public void setMaxAnisotropy(int maxAnisotropy) { mMaxAnisotropy = maxAnisotropy; }

    /** AOSP BitmapShader.getMaxAnisotropy(). 0 when never set. */
    public int getMaxAnisotropy() { return mMaxAnisotropy; }
    private int mMaxAnisotropy;

    /** AOSP BitmapShader.setOverrideGainmap(Gainmap) (API 36) -- substitutes a
     *  gainmap for the bitmap's own during HDR tone mapping. We have no HDR
     *  draw path (see Gainmap's own header), so this retains the value. */
    public void setOverrideGainmap(Gainmap overrideGainmap) { mOverrideGainmap = overrideGainmap; }
    private Gainmap mOverrideGainmap;
}
