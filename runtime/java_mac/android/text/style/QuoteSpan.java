// android.text.style.QuoteSpan -- AOSP paragraph span that draws a colored
// stripe in the leading margin (HTML <blockquote>). AOSP:
//   frameworks/base/core/java/android/text/style/QuoteSpan.java
//   public class QuoteSpan implements LeadingMarginSpan, ParcelableSpan
package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Parcel;
import android.text.Layout;
import android.text.ParcelableSpan;

public class QuoteSpan implements LeadingMarginSpan, ParcelableSpan {
    public static final int STANDARD_COLOR = 0xff0000ff;
    /** AOSP QuoteSpan.STANDARD_STRIPE_WIDTH_PX / STANDARD_GAP_WIDTH_PX, both 2.
     *  They are the defaults the no-argument and one-argument constructors use,
     *  and public API in their own right (an app that lays out around a quote
     *  reads them to compute its own indent). */
    public static final int STANDARD_STRIPE_WIDTH_PX = 2;
    public static final int STANDARD_GAP_WIDTH_PX = 2;

    private final int mColor;
    private final int mStripeWidth;
    private final int mGapWidth;

    public QuoteSpan() {
        this(STANDARD_COLOR, STANDARD_STRIPE_WIDTH_PX, STANDARD_GAP_WIDTH_PX);
    }
    public QuoteSpan(int color) {
        this(color, STANDARD_STRIPE_WIDTH_PX, STANDARD_GAP_WIDTH_PX);
    }
    public QuoteSpan(int color, int stripeWidth, int gapWidth) {
        mColor = color;
        mStripeWidth = stripeWidth;
        mGapWidth = gapWidth;
    }

    public int getColor() { return mColor; }
    public int getStripeWidth() { return mStripeWidth; }
    public int getGapWidth() { return mGapWidth; }

    @Override
    public int getLeadingMargin(boolean first) { return mStripeWidth + mGapWidth; }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top,
                                  int baseline, int bottom, CharSequence text,
                                  int start, int end, boolean first, Layout layout) {
        if (c == null || p == null) return;
        Paint.Style style = p.getStyle();
        int color = p.getColor();
        p.setStyle(Paint.Style.FILL);
        p.setColor(mColor);
        c.drawRect(x, top, x + dir * mStripeWidth, bottom, p);
        p.setStyle(style);
        p.setColor(color);
    }

    // AOSP TextUtils.QUOTE_SPAN == 9
    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return 9; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }
    @Override public void writeToParcelInternal(Parcel dest, int flags) {}
}
