// android.text.style.DrawableMarginSpan -- the Drawable-valued sibling of
// IconMarginSpan: draws a Drawable in the leading margin of the paragraph it is
// attached to. Was absent entirely (NoClassDefFoundError). Mirrors
// frameworks/base/core/java/android/text/style/DrawableMarginSpan.java,
// chooseHeight arithmetic included -- see IconMarginSpan for what it does.
package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spanned;

public class DrawableMarginSpan implements LeadingMarginSpan, LineHeightSpan {

    private static final int STANDARD_PAD_WIDTH = 0;

    private final Drawable mDrawable;
    private final int mPad;

    public DrawableMarginSpan(Drawable drawable) {
        this(drawable, STANDARD_PAD_WIDTH);
    }

    public DrawableMarginSpan(Drawable drawable, int pad) {
        mDrawable = drawable;
        mPad = pad;
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return mDrawable.getIntrinsicWidth() + mPad;
    }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir,
            int top, int baseline, int bottom,
            CharSequence text, int start, int end,
            boolean first, Layout layout) {
        int st = ((Spanned) text).getSpanStart(this);
        int ix = (int) x;
        int itop = (int) layout.getLineTop(layout.getLineForOffset(st));

        int dw = mDrawable.getIntrinsicWidth();
        int dh = mDrawable.getIntrinsicHeight();

        // AOSP's own comment here is "XXX What to do about Paint?" -- the
        // Drawable carries its own paint, so `p` is deliberately unused.
        mDrawable.setBounds(ix, itop, ix + dw, itop + dh);
        mDrawable.draw(c);
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end,
            int istartv, int v, Paint.FontMetricsInt fm) {
        if (end == ((Spanned) text).getSpanEnd(this)) {
            int ht = mDrawable.getIntrinsicHeight();

            int need = ht - (v + fm.descent - fm.ascent - istartv);
            if (need > 0) {
                fm.descent += need;
            }

            need = ht - (v + fm.bottom - fm.top - istartv);
            if (need > 0) {
                fm.bottom += need;
            }
        }
    }

    @Override
    public String toString() {
        return "DrawableMarginSpan{drawable=" + mDrawable + ", padding=" + mPad + '}';
    }

    public Drawable getDrawable() {
        return mDrawable;
    }

    public int getPadding() {
        return mPad;
    }
}
