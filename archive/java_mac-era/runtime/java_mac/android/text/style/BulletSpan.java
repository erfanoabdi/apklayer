// android.text.style.BulletSpan -- AOSP paragraph span that draws a bullet in
// the leading margin (HTML <li>, markdown lists). AOSP:
//   frameworks/base/core/java/android/text/style/BulletSpan.java
//   public class BulletSpan implements LeadingMarginSpan, ParcelableSpan
// We honor the leading-margin width and draw a filled bullet at the margin so
// list items render with a real bullet glyph + gap.
package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Parcel;
import android.text.Layout;
import android.text.ParcelableSpan;

public class BulletSpan implements LeadingMarginSpan, ParcelableSpan {
    public static final int STANDARD_GAP_WIDTH = 2;
    private static final int STANDARD_BULLET_RADIUS = 4;

    private final int mGapWidth;
    private final int mBulletRadius;
    private final int mColor;
    private final boolean mWantColor;

    public BulletSpan() {
        this(STANDARD_GAP_WIDTH, 0, false, STANDARD_BULLET_RADIUS);
    }
    public BulletSpan(int gapWidth) {
        this(gapWidth, 0, false, STANDARD_BULLET_RADIUS);
    }
    public BulletSpan(int gapWidth, int color) {
        this(gapWidth, color, true, STANDARD_BULLET_RADIUS);
    }
    public BulletSpan(int gapWidth, int color, int bulletRadius) {
        this(gapWidth, color, true, bulletRadius);
    }
    private BulletSpan(int gapWidth, int color, boolean wantColor, int bulletRadius) {
        mGapWidth = gapWidth;
        mColor = color;
        mWantColor = wantColor;
        mBulletRadius = bulletRadius;
    }

    public int getGapWidth() { return mGapWidth; }
    public int getBulletRadius() { return mBulletRadius; }
    public int getColor() { return mColor; }

    @Override
    public int getLeadingMargin(boolean first) {
        return 2 * mBulletRadius + mGapWidth;
    }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top,
                                  int baseline, int bottom, CharSequence text,
                                  int start, int end, boolean first, Layout layout) {
        // AOSP draws only on the first line of the paragraph.
        if (text instanceof android.text.Spanned
                && ((android.text.Spanned) text).getSpanStart(this) != start) {
            return;
        }
        if (c == null || p == null) return;
        int oldColor = 0;
        Paint.Style oldStyle = p.getStyle();
        if (mWantColor) { oldColor = p.getColor(); p.setColor(mColor); }
        p.setStyle(Paint.Style.FILL);
        float yPos = (top + bottom) / 2f;
        float xPos = x + dir * mBulletRadius;
        c.drawCircle(xPos, yPos, mBulletRadius, p);
        if (mWantColor) p.setColor(oldColor);
        p.setStyle(oldStyle);
    }

    // AOSP TextUtils.BULLET_SPAN == 8
    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return 8; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }
    @Override public void writeToParcelInternal(Parcel dest, int flags) {}
}
