// android.content.res.FontScaleConverter (API 34) -- the non-linear font-scale
// curve the platform applies at large accessibility font scales, so that body
// text grows more than headlines.
//
// AOSP ref: frameworks/base/core/java/android/content/res/FontScaleConverter.java
// (android16-release). The interface is ported verbatim; its two static
// methods delegate upstream to the @hide FontScaleConverterFactory, which
// holds a per-scale lookup table generated from the platform's own curves.
//
// ⚠ STATED GAP. Mac.apk ships neither that table nor a non-linear scaling
// pass: TypedValue/DisplayMetrics apply the plain LINEAR sp -> px conversion
// (px = sp * fontScale * density). So isNonLinearFontScalingActive() is false
// for every scale and forScale() returns null -- which is exactly what a
// device reports for a scale the table does not cover (AOSP's factory returns
// null below 1.03x and for any scale it has no curve for), and it is the
// answer that keeps a caller on the linear path it would otherwise take.
// Returning a fabricated converter would silently resize text by a curve
// nothing here actually applies.
package android.content.res;

public interface FontScaleConverter {
    /** Converts a dimension in sp to dp. */
    float convertSpToDp(float sp);

    /** Converts a dimension in dp back to sp. */
    float convertDpToSp(float dp);

    /** AOSP: "returns true if non-linear font scaling curves would be in
     *  effect for the given scale". */
    static boolean isNonLinearFontScalingActive(float fontScale) {
        return false;
    }

    /** AOSP: @Nullable -- "null if the scale is not non-linear". */
    static FontScaleConverter forScale(float fontScale) {
        return null;
    }
}
