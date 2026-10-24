// android.text.style.DynamicDrawableSpan -- AOSP base for spans that draw a
// Drawable inline in text (ImageSpan's superclass). AOSP:
//   frameworks/base/core/java/android/text/style/DynamicDrawableSpan.java
//   public abstract class DynamicDrawableSpan extends ReplacementSpan
//
// getSize() advances the run by the drawable's bounds width and adjusts the
// FontMetricsInt so the line grows to fit the image; draw() blits the drawable
// at the run position honoring the vertical-alignment mode. We mirror AOSP's
// observable measure + draw so chat / markdown inline images occupy and render
// the correct box.
package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public abstract class DynamicDrawableSpan extends ReplacementSpan {
    public static final int ALIGN_BOTTOM = 0;
    public static final int ALIGN_BASELINE = 1;
    public static final int ALIGN_CENTER = 2;

    protected final int mVerticalAlignment;
    private WeakDrawableRef mDrawableRef;

    public DynamicDrawableSpan() {
        mVerticalAlignment = ALIGN_BOTTOM;
    }

    protected DynamicDrawableSpan(int verticalAlignment) {
        mVerticalAlignment = verticalAlignment;
    }

    public int getVerticalAlignment() { return mVerticalAlignment; }

    public abstract Drawable getDrawable();

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                       Paint.FontMetricsInt fm) {
        Drawable d = getCachedDrawable();
        Rect rect = (d != null) ? d.getBounds() : new Rect(0, 0, 0, 0);
        if (fm != null) {
            // AOSP: grow the line so the image fits above the baseline.
            fm.ascent = -rect.bottom;
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = 0;
        }
        return rect.right;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        Drawable b = getCachedDrawable();
        if (b == null) return;
        canvas.save();
        int transY = bottom - b.getBounds().bottom;
        if (mVerticalAlignment == ALIGN_BASELINE) {
            transY -= (paint != null) ? paint.getFontMetricsInt().descent : 0;
        } else if (mVerticalAlignment == ALIGN_CENTER) {
            transY = top + (bottom - top) / 2 - b.getBounds().height() / 2;
        }
        canvas.translate(x, transY);
        b.draw(canvas);
        canvas.restore();
    }

    // AOSP caches the resolved Drawable behind a WeakReference so repeated
    // measure/draw passes don't re-resolve. We mirror that.
    private Drawable getCachedDrawable() {
        Drawable d = (mDrawableRef != null) ? mDrawableRef.get() : null;
        if (d == null) {
            d = getDrawable();
            mDrawableRef = new WeakDrawableRef(d);
        }
        return d;
    }

    private static final class WeakDrawableRef
            extends java.lang.ref.WeakReference<Drawable> {
        WeakDrawableRef(Drawable d) { super(d); }
    }
}
