// android.graphics.drawable.ColorStateListDrawable -- a Drawable that paints a
// solid color taken from a ColorStateList (the color can vary by view state:
// pressed/disabled/etc). Material's AppBarLayout and several widget backgrounds
// inflate one for their tint; without the class their inflation throws
// NoClassDefFoundError and LayoutInflater swaps in a bare FrameLayout (losing
// the chrome). We paint the ColorStateList's current color over the bounds.
// Mirrors AOSP frameworks/base graphics/java/android/graphics/drawable/ColorStateListDrawable.java.
//
// v1.0.962 -- AOSP's real ColorStateListDrawable (:35-49) is a THIN WRAPPER: it
// holds a nested `ColorDrawable mColorDrawable`, delegates draw/alpha/opacity/
// colorFilter/bounds to it, and implements Drawable.Callback so that inner
// ColorDrawable's own invalidateSelf() (fired from its setColor/setState/
// setTintList) bubbles out through us to whoever hosts this drawable. Our
// implementation takes a different, simpler shape: it paints the resolved
// color directly with a Paint in draw() and holds no child Drawable at all, so
// there is no nested invalidateSelf() that could ever reach these methods --
// unlike the LayerDrawable/StateListDrawable cases in this package, this is
// not a dropped signal, there is no signal to drop. Declared for assignability
// (AndroidX / app code that casts a Drawable to Drawable.Callback) with
// genuinely empty bodies.
package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;

public class ColorStateListDrawable extends Drawable implements Drawable.Callback {
    private ColorStateList colorStateList;
    private int alpha = 0xFF;
    private final Paint paint = new Paint();

    public ColorStateListDrawable() {
        this.colorStateList = ColorStateList.valueOf(Color.TRANSPARENT);
    }
    public ColorStateListDrawable(ColorStateList colorStateList) {
        this.colorStateList = colorStateList != null
                ? colorStateList : ColorStateList.valueOf(Color.TRANSPARENT);
    }

    /** Wave 5 (2026-09-26), docs/w5-inflater-fixes_2026-09-26.md F6. Default ON;
     *  `MACAPK_COLOR_FILE_DRAWABLE=0` restores the inherited answer (stateful only
     *  through a tint), the same env var that gates LayoutInflater's and
     *  Resources' colour-file -> ColorStateListDrawable change. */
    static final boolean MACAPK_COLOR_FILE_DRAWABLE =
        !"0".equals(System.getenv("MACAPK_COLOR_FILE_DRAWABLE"));

    /** AOSP ColorStateListDrawable (android14-release :69-71, :278-281):
     *      public boolean isStateful() { return mState.isStateful(); }
     *      // state: (mColor != null && mColor.isStateful()) || (mTint != null && mTint.isStateful())
     *  We inherited Drawable's tint-only answer, and View.drawableStateChanged
     *  calls setState only on a stateful background, so a selector colour never
     *  saw a state: SGT Puzzles' activated chooser tile stayed transparent. */
    @Override public boolean isStateful() {
        if (!MACAPK_COLOR_FILE_DRAWABLE) return super.isStateful();
        return colorStateList.isStateful() || super.isStateful();
    }

    /** AOSP :152-168: pick getColorForState(state, default) and report a change
     *  when it differs from the colour showing. draw() already reads getState(),
     *  so only the change report (which makes the View invalidate) is needed. */
    private int macapkStateColor = Integer.MIN_VALUE;
    @Override public boolean onStateChange(int[] state) {
        boolean base = super.onStateChange(state);
        if (!MACAPK_COLOR_FILE_DRAWABLE) return base;
        int c = colorStateList.getColorForState(state, colorStateList.getDefaultColor());
        boolean changed = c != macapkStateColor;
        macapkStateColor = c;
        if (changed) invalidateSelf();
        return changed || base;
    }

    public ColorStateList getColorStateList() { return colorStateList; }
    public void setColorStateList(ColorStateList colorStateList) {
        this.colorStateList = colorStateList != null
                ? colorStateList : ColorStateList.valueOf(Color.TRANSPARENT);
        invalidateSelf();
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b == null || b.width() <= 0 || b.height() <= 0) return;
        int color = colorStateList.getColorForState(getState(), colorStateList.getDefaultColor());
        int a = (Color.alpha(color) * alpha) / 255;
        final ColorFilter cf = MACAPK_DRAWABLE_COLOR_FILTER ? mColorFilter : null;
        if (cf != null) {
            // AOSP ColorDrawable.draw (what ColorStateListDrawable delegates to)
            // paints through its paint's colour filter, and paints even a
            // transparent colour when a filter is set:
            //     if ((mColorState.mUseColor >>> 24) != 0 || colorFilter != null
            //             || mBlendModeColorFilter != null) { ... drawRect ... }
            // The filter is applied to the one flat colour here, exactly
            // (Drawable.macapkFilterColor); a filter it cannot model rides on
            // the paint for Canvas to resolve.
            int c = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
            Integer f = macapkFilterColor(cf, c);
            paint.setColorFilter(f == null ? cf : null);
            int out = f == null ? c : f;
            if (f != null && (out >>> 24) == 0) return;
            paint.setColor(out);
            canvas.drawRect(b.left, b.top, b.right, b.bottom, paint);
            return;
        }
        if (a == 0) return;
        paint.setColorFilter(null);
        paint.setColor(Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawRect(b.left, b.top, b.right, b.bottom, paint);
    }

    @Override public void setAlpha(int alpha) { this.alpha = alpha; mHasAlpha = true; }
    /** AOSP ColorStateListDrawable.clearAlpha() -- "Remove the alpha override,
     *  reverting to the alpha defined on each state." */
    public void clearAlpha() { mHasAlpha = false; this.alpha = 255; }
    private boolean mHasAlpha;
    /** AOSP ColorStateListDrawable (android14-release :130-139) hands the filter
     *  to its inner ColorDrawable and reads it back from there:
     *      public ColorFilter getColorFilter() { return mColorDrawable.getColorFilter(); }
     *      public void setColorFilter(ColorFilter f) { mColorDrawable.setColorFilter(f); }
     *  We paint without an inner drawable (see the header), so the filter is
     *  held here. Both were empty; MACAPK_DRAWABLE_COLOR_FILTER=0 restores that. */
    private ColorFilter mColorFilter;
    @Override public void setColorFilter(ColorFilter cf) {
        if (!MACAPK_DRAWABLE_COLOR_FILTER) return;
        mColorFilter = cf;
    }
    @Override public ColorFilter getColorFilter() {
        return MACAPK_DRAWABLE_COLOR_FILTER ? mColorFilter : null;
    }
    @Override public int getOpacity() { return 0; /* TRANSLUCENT */ }

    // Drawable.Callback: genuinely empty. This class holds no child Drawable
    // (see the header comment), so nothing ever calls back into us through
    // these -- there is no nested drawable whose setCallback(this) could
    // route here. Declared only so `instanceof Drawable.Callback` /
    // `(Drawable.Callback) this` casts succeed, matching AOSP's type shape.
    @Override public void invalidateDrawable(Drawable who) {}
    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {}
    @Override public void unscheduleDrawable(Drawable who, Runnable what) {}
}
