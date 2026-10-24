package android.graphics;

/**
 * AOSP-faithful BlurMaskFilter. Apps attach one via Paint.setMaskFilter(...)
 * to make a paint draw a blurred edge (shadows, glows, soft text). On real
 * Android all the work is native (SkBlurMaskFilter), so the public API exposes
 * only the constructor and the Blur enum - there are no getters for radius or
 * style.
 *
 * Our Canvas is software (AWT / Graphics2D), so we cannot offload the blur to
 * the GPU. We store the radius and style faithfully here and let the Canvas
 * read them back through the macapk* accessors below; a later pass can
 * approximate the blur with a java.awt.image.ConvolveOp. Storing only - no
 * approximation - keeps this class a faithful data holder.
 */
public class BlurMaskFilter extends MaskFilter {

    /**
     * Blur styles, in AOSP declaration order (the ordinal matters because
     * Android maps it straight to the native SkBlurStyle enum).
     *   NORMAL - blur inside and outside the original border.
     *   SOLID  - solid inside, blur outside.
     *   OUTER  - nothing inside, blur outside (the original shape is erased).
     *   INNER  - blur inside, nothing outside.
     */
    public enum Blur {
        NORMAL,
        SOLID,
        OUTER,
        INNER
    }

    // Stored faithfully for our software Canvas to read back. Not part of the
    // AOSP public surface; named macapk* so callers know these getters are ours.
    private final float mRadius;
    private final Blur mStyle;

    /**
     * @param radius the radius to extend the blur from the original mask, in px.
     * @param style  the Blur style to use.
     */
    public BlurMaskFilter(float radius, Blur style) {
        mRadius = radius;
        mStyle = style;
    }

    /** Our accessor (AOSP has no public radius getter - native only). */
    public float macapkRadius() {
        return mRadius;
    }

    /** Our accessor (AOSP has no public style getter - native only). */
    public Blur macapkStyle() {
        return mStyle;
    }
}
