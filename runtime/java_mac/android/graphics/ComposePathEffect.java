// android.graphics.ComposePathEffect -- AOSP PathEffect that applies the
// inner effect first, then the outer effect to that result. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/ComposePathEffect.java
// Our Canvas stroke path only maps DashPathEffect onto BasicStroke; a
// composed effect is stored faithfully (both children retained) so apps
// that build one resolve at the type level and can be inspected. The
// outer/inner accessors let the stroke site fall back to the dash child
// when present.
package android.graphics;

public class ComposePathEffect extends PathEffect {
    private final PathEffect outerpe;
    private final PathEffect innerpe;

    public ComposePathEffect(PathEffect outerpe, PathEffect innerpe) {
        this.outerpe = outerpe;
        this.innerpe = innerpe;
    }

    public PathEffect macapkOuter() { return outerpe; }
    public PathEffect macapkInner() { return innerpe; }
}
