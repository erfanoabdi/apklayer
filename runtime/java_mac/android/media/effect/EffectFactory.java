package android.media.effect;

/**
 * Creates the platform's built-in GLES image effects (sepia, vignette, red-eye,
 * ...). API 14.
 *
 * Every one of these is implemented by AOSP's private mediaframework filter
 * graph (androidx-era apps use RenderEffect or their own shaders instead), and
 * Mac.apk ships none of it. So isEffectSupported() reports FALSE for every name
 * and createEffect() throws IllegalArgumentException -- which is exactly AOSP's
 * documented behaviour for an unsupported effect name, and is the pair of
 * answers an app's capability check is written against.
 *
 * The name constants are still declared, and verbatim: they are the argument an
 * app passes to isEffectSupported(), so they must resolve for the check to run
 * at all. Declaring a name is not claiming the effect -- the answer to
 * "is it supported" is where the claim would live, and that answer is no.
 *
 * Source: frameworks/base/media/java/android/media/effect/EffectFactory.java
 */
public class EffectFactory {

    public static final String EFFECT_AUTOFIX = "android.media.effect.effects.AutoFixEffect";
    public static final String EFFECT_BACKDROPPER = "android.media.effect.effects.BackDropperEffect";
    public static final String EFFECT_BITMAPOVERLAY =
            "android.media.effect.effects.BitmapOverlayEffect";
    public static final String EFFECT_BLACKWHITE = "android.media.effect.effects.BlackWhiteEffect";
    public static final String EFFECT_BRIGHTNESS = "android.media.effect.effects.BrightnessEffect";
    public static final String EFFECT_CONTRAST = "android.media.effect.effects.ContrastEffect";
    public static final String EFFECT_CROP = "android.media.effect.effects.CropEffect";
    public static final String EFFECT_CROSSPROCESS =
            "android.media.effect.effects.CrossProcessEffect";
    public static final String EFFECT_DOCUMENTARY =
            "android.media.effect.effects.DocumentaryEffect";
    public static final String EFFECT_DUOTONE = "android.media.effect.effects.DuotoneEffect";
    public static final String EFFECT_FILLLIGHT = "android.media.effect.effects.FillLightEffect";
    public static final String EFFECT_FISHEYE = "android.media.effect.effects.FisheyeEffect";
    public static final String EFFECT_FLIP = "android.media.effect.effects.FlipEffect";
    public static final String EFFECT_GRAIN = "android.media.effect.effects.GrainEffect";
    public static final String EFFECT_GRAYSCALE = "android.media.effect.effects.GrayscaleEffect";
    public static final String EFFECT_LOMOISH = "android.media.effect.effects.LomoishEffect";
    public static final String EFFECT_NEGATIVE = "android.media.effect.effects.NegativeEffect";
    public static final String EFFECT_POSTERIZE = "android.media.effect.effects.PosterizeEffect";
    public static final String EFFECT_REDEYE = "android.media.effect.effects.RedEyeEffect";
    public static final String EFFECT_ROTATE = "android.media.effect.effects.RotateEffect";
    public static final String EFFECT_SATURATE = "android.media.effect.effects.SaturateEffect";
    public static final String EFFECT_SEPIA = "android.media.effect.effects.SepiaEffect";
    public static final String EFFECT_SHARPEN = "android.media.effect.effects.SharpenEffect";
    public static final String EFFECT_STRAIGHTEN = "android.media.effect.effects.StraightenEffect";
    public static final String EFFECT_TEMPERATURE =
            "android.media.effect.effects.ColorTemperatureEffect";
    public static final String EFFECT_TINT = "android.media.effect.effects.TintEffect";
    public static final String EFFECT_VIGNETTE = "android.media.effect.effects.VignetteEffect";

    EffectFactory() { }

    /** AOSP: "@return true if the effect is supported on this platform." */
    public static boolean isEffectSupported(String effectName) {
        java.util.Objects.requireNonNull(effectName, "effectName must not be null");
        return false;
    }

    /** AOSP: "@throws IllegalArgumentException if the effect with the specified
     *  name is not supported." Nothing is, so every name throws -- and an app
     *  that checked isEffectSupported() first never reaches this. */
    public Effect createEffect(String effectName) {
        java.util.Objects.requireNonNull(effectName, "effectName must not be null");
        throw new IllegalArgumentException("Cannot instantiate unknown effect '"
                + effectName + "'!");
    }
}
