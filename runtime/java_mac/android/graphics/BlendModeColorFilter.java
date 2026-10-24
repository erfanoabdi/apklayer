// android.graphics.BlendModeColorFilter -- AOSP API-29 replacement
// for the older PorterDuffColorFilter. ImageView / MaterialButton /
// ProgressBar / tint-aware Drawables construct one as
//   new BlendModeColorFilter(color, BlendMode.SRC_IN)
// to tint their content. AOSP signature:
//   BlendModeColorFilter(@ColorInt int color, @NonNull BlendMode mode)
// We accept and store the values; the actual tint compositing
// happens elsewhere (or not at all on Mac.apk -- many tinted
// drawables still render correctly via their underlying bitmap
// without explicit tint application).
package android.graphics;

public class BlendModeColorFilter extends ColorFilter {
    private final int color;
    private final BlendMode mode;

    public BlendModeColorFilter(int color, BlendMode mode) {
        this.color = color;
        this.mode = mode;
    }

    public int getColor() { return color; }
    public BlendMode getMode() { return mode; }

    /** Legacy zero-arg ctor kept for v1.0.92 stub callers. */
    public BlendModeColorFilter() {
        this(0, BlendMode.SRC_OVER);
    }
}
