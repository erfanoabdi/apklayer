// v1.0.124: AOSP `MetricAffectingSpan` -- span subclass that affects
// text measurement / metrics (e.g. font size, typeface, scale).
// Compose UI's TextLayoutResult walks spans of this type to recompute
// line metrics. Wraps via getUnderlying() so nested spans (per AOSP
// CharacterStyle.wrap pattern) round-trip correctly.
package android.text.style;

// AOSP: `public abstract class MetricAffectingSpan extends CharacterStyle
// implements UpdateLayout`. UpdateLayout (and the UpdateAppearance it extends)
// are EMPTY marker interfaces, so this cannot be a lie: there is no method to
// get wrong. They are how the text stack decides whether a span change needs a
// re-layout or only a re-draw, so the marker is the whole contract. Inherited by
// AbsoluteSizeSpan, RelativeSizeSpan, ScaleXSpan, StyleSpan, SubscriptSpan,
// SuperscriptSpan, TextAppearanceSpan, TypefaceSpan, LocaleSpan, ReplacementSpan,
// DynamicDrawableSpan and ImageSpan.
public abstract class MetricAffectingSpan extends CharacterStyle
        implements UpdateLayout {
    public abstract void updateMeasureState(android.text.TextPaint tp);

    @Override
    public MetricAffectingSpan getUnderlying() { return this; }
}
