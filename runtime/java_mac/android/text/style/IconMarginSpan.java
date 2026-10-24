// android.text.style.IconMarginSpan -- a paragraph span that draws a Bitmap in
// the leading margin, with optional padding between it and the text. Was absent
// entirely (NoClassDefFoundError). Mirrors
// frameworks/base/core/java/android/text/style/IconMarginSpan.java verbatim,
// including the chooseHeight arithmetic, which is the non-obvious part: it
// grows descent/bottom only when the paragraph is not already tall enough for
// the icon, measured against the paragraph's accumulated height `v` rather
// than the current line's.
package android.text.style;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spanned;

public class IconMarginSpan implements LeadingMarginSpan, LineHeightSpan {

    private final Bitmap mBitmap;
    private final int mPad;

    public IconMarginSpan(Bitmap bitmap) {
        this(bitmap, 0);
    }

    public IconMarginSpan(Bitmap bitmap, int pad) {
        mBitmap = bitmap;
        mPad = pad;
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return mBitmap.getWidth() + mPad;
    }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir,
            int top, int baseline, int bottom,
            CharSequence text, int start, int end,
            boolean first, Layout layout) {
        int st = ((Spanned) text).getSpanStart(this);
        int itop = layout.getLineTop(layout.getLineForOffset(st));

        if (dir < 0) {
            x -= mBitmap.getWidth();
        }

        c.drawBitmap(mBitmap, x, itop, p);
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end,
            int istartv, int v, Paint.FontMetricsInt fm) {
        if (end == ((Spanned) text).getSpanEnd(this)) {
            int ht = mBitmap.getHeight();

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
        return "IconMarginSpan{bitmap=" + getBitmap() + ", padding=" + getPadding() + '}';
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public int getPadding() {
        return mPad;
    }
}
