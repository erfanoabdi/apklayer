// android.graphics.PorterDuffColorFilter -- AOSP-shape stub so
// Material widgets (MaterialShapeDrawable, BadgeDrawable, etc.) can
// construct one for tinting math. The actual color-filter compositing
// happens inside our Drawable.draw paths -- we don't implement it,
// but the construction must succeed for the BottomNav tinting chain
// to complete and not abort instantiation.
//
// v1.0.97: surfaced when getColorStateList started returning a real
// instance and the BottomNav <init> chain reached
// MaterialShapeDrawable.calculateTintColorTintFilter.
package android.graphics;

public class PorterDuffColorFilter extends ColorFilter {
    private final int color;
    private final PorterDuff.Mode mode;

    public PorterDuffColorFilter(int color, PorterDuff.Mode mode) {
        this.color = color;
        this.mode = mode;
    }
    public PorterDuffColorFilter() {
        this.color = 0;
        this.mode = null;
    }

    public int getColor()          { return color; }
    public PorterDuff.Mode getMode() { return mode; }
    public void setColor(int color) { /* AOSP is immutable but accept */ }
    public void setMode(PorterDuff.Mode mode) {}
}
