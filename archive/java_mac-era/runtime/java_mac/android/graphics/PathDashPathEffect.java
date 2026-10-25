// android.graphics.PathDashPathEffect -- AOSP PathEffect that stamps a
// shape (the "shape" Path) repeatedly along the drawn path, spaced by
// advance, offset by phase, oriented per the Style. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/PathDashPathEffect.java
// Stored faithfully (shape + advance + phase + style retained). Our
// software stroke path cannot stamp an arbitrary shape along the path, so
// it falls back to a plain stroke; the accessors expose the params.
package android.graphics;

public class PathDashPathEffect extends PathEffect {
    /** How the stamped shape is oriented along the path. */
    public enum Style {
        TRANSLATE, ROTATE, MORPH
    }

    private final Path shape;
    private final float advance;
    private final float phase;
    private final Style style;

    public PathDashPathEffect(Path shape, float advance, float phase, Style style) {
        this.shape = shape != null ? new Path(shape) : null;
        this.advance = advance;
        this.phase = phase;
        this.style = style;
    }

    public Path macapkShape()    { return shape; }
    public float macapkAdvance() { return advance; }
    public float macapkPhase()   { return phase; }
    public Style macapkStyle()   { return style; }
}
