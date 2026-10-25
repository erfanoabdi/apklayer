// android.widget.MacApkBlendModes -- INTERNAL (package-private) helper, not
// part of the AOSP public surface.
//
// Why it exists: AOSP's widget tint APIs come in pairs -- setXTintMode(
// PorterDuff.Mode) and setXTintBlendMode(BlendMode) -- backed by ONE stored
// BlendMode, converted with BlendMode.fromValue(int) /
// BlendMode.blendModeToPorterDuffMode(BlendMode). Both of those are @hide
// statics on android.graphics.BlendMode, so they are not on our BlendMode and
// android.graphics is owned by another lane. Rather than duplicate the same
// 18-arm switch into every widget that has a tint pair (AbsSeekBar,
// AnalogClock, CheckedTextView, CompoundButton, ImageView, ProgressBar,
// Switch, TextView), the mapping lives here once.
//
// The mapping is copied verbatim from AOSP:
//   frameworks/base/graphics/java/android/graphics/BlendMode.java
//     blendModeToPorterDuffMode(BlendMode)   (lines ~526-571, android16)
//     fromValue(int)                          -- the inverse, matched on the
//                                                PorterDuff nativeInt
// Note the two deliberately-asymmetric arms AOSP calls out with b/73224934:
// PorterDuff.Mode.MULTIPLY is Skia's *modulate*, so it pairs with
// BlendMode.MODULATE (not BlendMode.MULTIPLY), and PorterDuff.Mode.ADD pairs
// with BlendMode.PLUS. Blend modes with no PorterDuff equivalent (MULTIPLY,
// HUE, SATURATION, COLOR, LUMINOSITY, COLOR_DODGE, ...) convert to null,
// exactly as AOSP does.
package android.widget;

import android.graphics.BlendMode;
import android.graphics.PorterDuff;

final class MacApkBlendModes {
    private MacApkBlendModes() {}

    /** AOSP BlendMode.blendModeToPorterDuffMode(BlendMode). */
    static PorterDuff.Mode toPorterDuff(BlendMode mode) {
        if (mode == null) return null;
        switch (mode) {
            case CLEAR:    return PorterDuff.Mode.CLEAR;
            case SRC:      return PorterDuff.Mode.SRC;
            case DST:      return PorterDuff.Mode.DST;
            case SRC_OVER: return PorterDuff.Mode.SRC_OVER;
            case DST_OVER: return PorterDuff.Mode.DST_OVER;
            case SRC_IN:   return PorterDuff.Mode.SRC_IN;
            case DST_IN:   return PorterDuff.Mode.DST_IN;
            case SRC_OUT:  return PorterDuff.Mode.SRC_OUT;
            case DST_OUT:  return PorterDuff.Mode.DST_OUT;
            case SRC_ATOP: return PorterDuff.Mode.SRC_ATOP;
            case DST_ATOP: return PorterDuff.Mode.DST_ATOP;
            case XOR:      return PorterDuff.Mode.XOR;
            case DARKEN:   return PorterDuff.Mode.DARKEN;
            case LIGHTEN:  return PorterDuff.Mode.LIGHTEN;
            // b/73224934: PorterDuff Multiply maps to Skia Modulate.
            case MODULATE: return PorterDuff.Mode.MULTIPLY;
            case SCREEN:   return PorterDuff.Mode.SCREEN;
            case PLUS:     return PorterDuff.Mode.ADD;
            case OVERLAY:  return PorterDuff.Mode.OVERLAY;
            default:       return null;
        }
    }

    /** AOSP BlendMode.fromValue(mode.nativeInt) -- the exact inverse of the
     *  switch above, so a set/get round trip through either overload is
     *  lossless for every mode that has a pair. */
    static BlendMode fromPorterDuff(PorterDuff.Mode mode) {
        if (mode == null) return null;
        switch (mode) {
            case CLEAR:    return BlendMode.CLEAR;
            case SRC:      return BlendMode.SRC;
            case DST:      return BlendMode.DST;
            case SRC_OVER: return BlendMode.SRC_OVER;
            case DST_OVER: return BlendMode.DST_OVER;
            case SRC_IN:   return BlendMode.SRC_IN;
            case DST_IN:   return BlendMode.DST_IN;
            case SRC_OUT:  return BlendMode.SRC_OUT;
            case DST_OUT:  return BlendMode.DST_OUT;
            case SRC_ATOP: return BlendMode.SRC_ATOP;
            case DST_ATOP: return BlendMode.DST_ATOP;
            case XOR:      return BlendMode.XOR;
            case DARKEN:   return BlendMode.DARKEN;
            case LIGHTEN:  return BlendMode.LIGHTEN;
            case MULTIPLY: return BlendMode.MODULATE;
            case SCREEN:   return BlendMode.SCREEN;
            case ADD:      return BlendMode.PLUS;
            case OVERLAY:  return BlendMode.OVERLAY;
            default:       return null;
        }
    }
}
