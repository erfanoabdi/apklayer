package android.graphics;

/**
 * A DrawFilter that, on real Android, force-clears some Paint flags and force-sets
 * others for every drawing call made while it is installed on a Canvas. The common
 * use is forcing Paint.ANTI_ALIAS_FLAG / Paint.FILTER_BITMAP_FLAG on (or off) for a
 * whole render pass without touching each individual Paint, e.g. MapView and custom
 * Views that want crisp or smoothed output regardless of the caller's Paint setup.
 *
 * Mac.apk note: our Canvas is AWT-backed and software-only, so the clear/set masks
 * are not honored at raster time. The class exists so bytecode that constructs the
 * filter and installs it via Canvas.setDrawFilter()/getDrawFilter() links, runs, and
 * round-trips the public fields correctly. We mirror AOSP's public API surface
 * exactly: the two constructor args are exposed as public final int fields named
 * clearBits and setBits
 * (frameworks/base/graphics/java/android/graphics/PaintFlagsDrawFilter.java).
 */
public class PaintFlagsDrawFilter extends DrawFilter {

    // Public final fields are part of AOSP's documented surface: callers and some
    // libraries read filter.clearBits / filter.setBits directly, so the names and
    // access modifiers must match exactly.
    public final int clearBits;
    public final int setBits;

    /**
     * Construct a draw filter that clears the specified bits in the paint's flags,
     * and sets the specified bits.
     *
     * @param clearBits these bits will be cleared in the paint's flags
     * @param setBits   these bits will be set in the paint's flags
     */
    public PaintFlagsDrawFilter(int clearBits, int setBits) {
        // AOSP masks each argument with Paint.AVAILABLE_FLAGS before handing them to
        // the native constructor, but it stores the unmasked argument values in the
        // public fields. We have no native peer, so we simply retain the values; the
        // masking would only matter for raster behavior we do not implement.
        this.clearBits = clearBits;
        this.setBits = setBits;
    }
}
