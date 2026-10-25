package android.graphics;

/**
 * AOSP-faithful EmbossMaskFilter. Apps attach one via Paint.setMaskFilter(...)
 * to give a paint a 3D embossed look (a directional light raised-edge effect).
 * On real Android all the work is native (SkEmbossMaskFilter), so the public
 * API exposes only the constructor - there are no getters.
 *
 * Our Canvas is software (AWT / Graphics2D) with no emboss primitive, so we
 * store the parameters faithfully as a data holder and the Canvas can read
 * them back through the macapk* accessors if a later pass approximates the
 * effect. Mirrors
 * frameworks/base/graphics/java/android/graphics/EmbossMaskFilter.java.
 */
public class EmbossMaskFilter extends MaskFilter {
    // Stored faithfully for our software Canvas; not part of the AOSP public
    // surface (native-only on Android) so the getters are named macapk*.
    private final float[] mDirection;
    private final float mAmbient;
    private final float mSpecular;
    private final float mBlurRadius;

    /**
     * Create an emboss maskfilter.
     *
     * @param direction array of 3 scalars [x,y,z] specifying the light source.
     * @param ambient   0..1 amount of ambient light.
     * @param specular  coefficient for specular highlights (e.g. 8).
     * @param blurRadius amount to blur before applying lighting (e.g. 3).
     */
    public EmbossMaskFilter(float[] direction, float ambient, float specular, float blurRadius) {
        if (direction == null || direction.length < 3) {
            throw new IllegalArgumentException("direction must be a vector of length 3");
        }
        mDirection = new float[] { direction[0], direction[1], direction[2] };
        mAmbient = ambient;
        mSpecular = specular;
        mBlurRadius = blurRadius;
    }

    /** Our accessor (AOSP has no public getter - native only). */
    public float[] macapkDirection() { return mDirection.clone(); }
    /** Our accessor (AOSP has no public getter - native only). */
    public float macapkAmbient() { return mAmbient; }
    /** Our accessor (AOSP has no public getter - native only). */
    public float macapkSpecular() { return mSpecular; }
    /** Our accessor (AOSP has no public getter - native only). */
    public float macapkBlurRadius() { return mBlurRadius; }
}
