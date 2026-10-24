// android.graphics.SweepGradient -- a Shader drawing a sweep (angular) color
// gradient around a center point. Provided so vector/Compose gradient fills
// that use a sweep don't NoClassDefFoundError. v1.0.406.
package android.graphics;

public class SweepGradient extends Shader {
    public final float cx, cy;
    public final int[] colors;
    public final float[] positions;

    public SweepGradient(float cx, float cy, int[] colors, float[] positions) {
        this.cx = cx; this.cy = cy; this.colors = colors; this.positions = positions;
    }

    public SweepGradient(float cx, float cy, int color0, int color1) {
        this(cx, cy, new int[] { color0, color1 }, null);
    }

    public SweepGradient(float cx, float cy, long[] colors, float[] positions) {
        this(cx, cy, LinearGradient.longsToInts(colors), positions);
    }

    public SweepGradient(float cx, float cy, long color0, long color1) {
        // See LinearGradient's long-color comment: `(int) color` takes the LOW 32 bits, which are
        // ZERO for an sRGB ColorLong (Color.pack shifts the ARGB int into the HIGH half).
        this(cx, cy, new int[] { Color.toArgb(color0), Color.toArgb(color1) }, null);
    }
}
