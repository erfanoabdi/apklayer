// android.graphics.PorterDuff -- the named-mode blend operations
// every Drawable/Paint API references. Used by ColorFilter, Canvas
// clip ops, tint lists, etc.
//
// Stale-comment fix (v1.0.834): this used to say "we don't actually
// composite via these modes (Canvas renders are stubs)". That has not
// been true since v1.0.433/v1.0.500: Canvas.applyPaint/awtRuleForPaint
// map every mode below to a java.awt.AlphaComposite rule (the 12 Porter-
// Duff alpha modes map exactly; MULTIPLY/SCREEN/ADD get a real per-pixel
// blend in Canvas.multiplyColors/screenColors/addColors; the remaining
// DARKEN/LIGHTEN/OVERLAY fall back to SRC_OVER, same as BlendMode's
// compositeFor()). This class's job is still just the type symbols
// (ordinal values + nativeInt) that every UI-shaped app references --
// the actual compositing lives in Canvas.java -- but that compositing
// is real, not a stub.
package android.graphics;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class PorterDuff {
    /** AOSP PorterDuff declares NO constructor, so the synthesised no-arg one is
     *  PUBLIC and part of the API surface. */
    public PorterDuff() {}

    /** AOSP enum -- alpha-compositing modes (Porter & Duff 1984). The
     *  ordinal values are part of the public ABI; apps that round-trip
     *  these through SharedPreferences as ints rely on the exact
     *  values. */
    public enum Mode {
        CLEAR(0),
        SRC(1),
        DST(2),
        SRC_OVER(3),
        DST_OVER(4),
        SRC_IN(5),
        DST_IN(6),
        SRC_OUT(7),
        DST_OUT(8),
        SRC_ATOP(9),
        DST_ATOP(10),
        XOR(11),
        DARKEN(16),
        LIGHTEN(17),
        MULTIPLY(13),
        SCREEN(14),
        ADD(12),
        OVERLAY(15);

        public final int nativeInt;
        Mode(int n) { this.nativeInt = n; }

        public static Mode intToMode(int value) {
            for (Mode m : values()) if (m.nativeInt == value) return m;
            return SRC_OVER;
        }
        public static int modeToInt(Mode mode) {
            return mode == null ? SRC_OVER.nativeInt : mode.nativeInt;
        }
    }
}
