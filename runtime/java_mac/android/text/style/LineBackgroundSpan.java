package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;


/**
 * Draws a background behind a whole line of text. Implemented by app spans;
 * Layout calls drawBackground before drawing the line's text. AOSP declares the
 * interface plus a concrete Standard. Both must exist so an app's implementing
 * span (and HtmlCompat / Compose interop) links.
 *
 * Source: frameworks/base/core/java/android/text/style/LineBackgroundSpan.java
 */
public interface LineBackgroundSpan extends ParagraphStyle {

    void drawBackground(Canvas canvas, Paint paint, int left, int right, int top,
            int baseline, int bottom, CharSequence text, int start, int end, int lineNumber);

    // 2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
    // Payload (a single int color) and type id 27 verified against
    // frameworks/base/core/java/android/text/style/LineBackgroundSpan.java
    // (android16-release) L84-111.
    class Standard implements LineBackgroundSpan, android.text.ParcelableSpan {
        private final int mColor;
        public Standard(int color) { mColor = color; }
        public int getColor() { return mColor; }
        @Override public void drawBackground(Canvas canvas, Paint paint, int left, int right,
                int top, int baseline, int bottom, CharSequence text, int start, int end,
                int lineNumber) {
            final int origColor = paint.getColor();
            paint.setColor(mColor);
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setColor(origColor);
        }

        @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
        @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.LINE_BACKGROUND_SPAN; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            writeToParcelInternal(dest, flags);
        }
        @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
            dest.writeInt(mColor);
        }
        public static final android.os.Parcelable.Creator<Standard> CREATOR =
                new android.os.Parcelable.Creator<Standard>() {
            @Override public Standard createFromParcel(android.os.Parcel in) {
                return new Standard(in.readInt());
            }
            @Override public Standard[] newArray(int size) { return new Standard[size]; }
        };
    }
}
