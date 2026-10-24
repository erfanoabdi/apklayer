// android.graphics.LinearGradient -- a Shader that draws a linear color
// gradient between two points. Reddit's brand-lockup vector (ic_brand_lockup,
// the welcome/login logo) fills paths with a <gradient> that Compose's vector
// parser turns into a LinearGradient; without this class the composition throws
// NoClassDefFoundError and the whole welcome screen goes blank. v1.0.406.
//
// We store the gradient parameters so a shader-aware Canvas can render them;
// the constructors mirror AOSP (int[] / two-color / API-26 long-color forms).
package android.graphics;

public class LinearGradient extends Shader {
    public final float x0, y0, x1, y1;
    public final int[] colors;
    public final float[] positions;
    public final TileMode tile;

    public LinearGradient(float x0, float y0, float x1, float y1,
                          int[] colors, float[] positions, TileMode tile) {
        this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        this.colors = colors; this.positions = positions;
        this.tile = tile != null ? tile : TileMode.CLAMP;
    }

    public LinearGradient(float x0, float y0, float x1, float y1,
                          int color0, int color1, TileMode tile) {
        this(x0, y0, x1, y1, new int[] { color0, color1 }, null, tile);
    }

    // API 26 long-color (wide-gamut) forms.
    //
    // ⚠ v1.0.1105 FIX. These used to take `(int) color`, i.e. the LOW 32 bits, on the stated
    // reasoning that "the low 32 bits are the sRGB approximation enough for a visible gradient".
    // That is exactly backwards: AOSP `Color.pack(int)` is `(argb & 0xffffffffL) << 32`, so for an
    // sRGB ColorLong the ARGB int is in the HIGH 32 bits and **the low 32 bits are ZERO**. Every
    // long-color gradient therefore had stop colours of 0x00000000 -- TRANSPARENT BLACK -- and
    // rendered as a darkening wash instead of the app's colours.
    //
    // The identical bug was already found and fixed in Bitmap.eraseColor(long) (see its comment,
    // "MEASURED (BMP.state.erase.longColour): the emulator answers ffff8000, we answered
    // 00000000") and never propagated here.
    //
    // MEASURED (Now in Android, emulator A/B): the app's near-white background gradient composited
    // over black instead of over the surface. Solving `ours = src*a` against
    // `device = src*a + 255*(1-a)` down a pure-gradient column gave src ~= 250-254 with alpha
    // 0.64 at mid-screen rising to 0.88 at the edges -- i.e. the right SHAPE with the colour
    // zeroed. Whole-screen ratio dipped to 0.635; opaque elements (cards, nav bar) matched the
    // device exactly at 1.000, which is what localised it to the translucent gradient.
    //
    // Color.toArgb(long) is the documented inverse and handles the non-sRGB spaces too.
    public LinearGradient(float x0, float y0, float x1, float y1,
                          long[] colors, float[] positions, TileMode tile) {
        this(x0, y0, x1, y1, longsToInts(colors), positions, tile);
    }

    public LinearGradient(float x0, float y0, float x1, float y1,
                          long color0, long color1, TileMode tile) {
        this(x0, y0, x1, y1,
             new int[] { Color.toArgb(color0), Color.toArgb(color1) }, null, tile);
    }

    static int[] longsToInts(long[] in) {
        if (in == null) return null;
        int[] out = new int[in.length];
        for (int i = 0; i < in.length; i++) out[i] = Color.toArgb(in[i]);
        return out;
    }
}
