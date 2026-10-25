// android.graphics.BlendMode -- AOSP API-29 enum of Porter-Duff and
// Skia blend modes. AOSP made this a real `enum BlendMode` (extending
// java.lang.Enum<BlendMode>) rather than the older PorterDuff.Mode int.
// F-Droid + androidx widgets (ImageView, MaterialButton, ProgressBar)
// reference field constants like `BlendMode.SRC_IN`; some code also
// switch()es on a BlendMode, or calls BlendMode.valueOf(name) /
// BlendMode.values() / mode.ordinal() / mode.name(). A plain class with
// static-final instances satisfies the getfield/getstatic callers but
// NOT the enum-method callers (valueOf/values/ordinal are synthesized by
// javac only for real enums) -- a switch on BlendMode or a values()
// lookup would NoSuchMethodError at runtime. So this is now a real enum,
// matching AOSP exactly.
//
// Declaration order is load-bearing: AOSP maps ordinal() straight to the
// native SkBlendMode enum, and the order below is the canonical AOSP
// order from frameworks/base/graphics/java/android/graphics/BlendMode.java.
//
// (The prior class form claimed legacy `new BlendMode()` callers; no such
// call exists anywhere in runtime/java, so the enum conversion is safe.)
package android.graphics;

public enum BlendMode {
    CLEAR,
    SRC,
    DST,
    SRC_OVER,
    DST_OVER,
    SRC_IN,
    DST_IN,
    SRC_OUT,
    DST_OUT,
    SRC_ATOP,
    DST_ATOP,
    XOR,
    PLUS,
    MODULATE,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    COLOR_BURN,
    HARD_LIGHT,
    SOFT_LIGHT,
    DIFFERENCE,
    EXCLUSION,
    MULTIPLY,
    HUE,
    SATURATION,
    COLOR,
    LUMINOSITY;

    /** AOSP convenience used by Paint.setBlendMode -> native: map a
     *  BlendMode to the equivalent legacy PorterDuff.Mode where one
     *  exists (the first 13 modes line up). Returns null for the modes
     *  with no PorterDuff equivalent (the separable/non-separable Skia
     *  blends). Mirrors BlendMode.toPorterDuffMode behavior. */
    public android.graphics.PorterDuff.Mode toPorterDuffModeOrNull() {
        switch (this) {
            case CLEAR:    return android.graphics.PorterDuff.Mode.CLEAR;
            case SRC:      return android.graphics.PorterDuff.Mode.SRC;
            case DST:      return android.graphics.PorterDuff.Mode.DST;
            case SRC_OVER: return android.graphics.PorterDuff.Mode.SRC_OVER;
            case DST_OVER: return android.graphics.PorterDuff.Mode.DST_OVER;
            case SRC_IN:   return android.graphics.PorterDuff.Mode.SRC_IN;
            case DST_IN:   return android.graphics.PorterDuff.Mode.DST_IN;
            case SRC_OUT:  return android.graphics.PorterDuff.Mode.SRC_OUT;
            case DST_OUT:  return android.graphics.PorterDuff.Mode.DST_OUT;
            case SRC_ATOP: return android.graphics.PorterDuff.Mode.SRC_ATOP;
            case DST_ATOP: return android.graphics.PorterDuff.Mode.DST_ATOP;
            case XOR:      return android.graphics.PorterDuff.Mode.XOR;
            case SCREEN:   return android.graphics.PorterDuff.Mode.SCREEN;
            case OVERLAY:  return android.graphics.PorterDuff.Mode.OVERLAY;
            case DARKEN:   return android.graphics.PorterDuff.Mode.DARKEN;
            case LIGHTEN:  return android.graphics.PorterDuff.Mode.LIGHTEN;
            case MULTIPLY: return android.graphics.PorterDuff.Mode.MULTIPLY;
            default:       return null;
        }
    }
}
