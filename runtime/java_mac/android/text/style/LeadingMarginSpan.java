// android.text.style.LeadingMarginSpan -- a paragraph span that indents the
// leading margin (bullets, blockquotes, list indents). Compose's
// AndroidParagraphIntrinsics walks paragraph spans during the text MEASURE pass
// and references this type; a missing class threw NoClassDefFoundError there
// (Jetsnack). Faithful AOSP shape. v1.0.429.
// Source: frameworks/base/core/java/android/text/style/LeadingMarginSpan.java
package android.text.style;

public interface LeadingMarginSpan extends ParagraphStyle {
    int getLeadingMargin(boolean first);
    void drawLeadingMargin(android.graphics.Canvas c, android.graphics.Paint p, int x, int dir,
                           int top, int baseline, int bottom, CharSequence text,
                           int start, int end, boolean first, android.text.Layout layout);

    /** A LeadingMarginSpan whose margin applies for a fixed number of lines. */
    interface LeadingMarginSpan2 extends LeadingMarginSpan, WrapTogetherSpan {
        int getLeadingMarginLineCount();
    }

    /** The standard implementation: a first-line margin + a rest-of-paragraph margin.
     *
     *  2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
     *  Payload (first, rest) and type id 10 verified against
     *  frameworks/base/core/java/android/text/style/LeadingMarginSpan.java
     *  (android16-release) L124-148, matching this class's own field order. */
    class Standard implements LeadingMarginSpan, android.text.ParcelableSpan {
        private final int mFirst, mRest;
        public Standard(int first, int rest) { mFirst = first; mRest = rest; }
        public Standard(int every) { this(every, every); }
        @Override public int getLeadingMargin(boolean first) { return first ? mFirst : mRest; }
        @Override public void drawLeadingMargin(android.graphics.Canvas c, android.graphics.Paint p,
                int x, int dir, int top, int baseline, int bottom, CharSequence text,
                int start, int end, boolean first, android.text.Layout layout) {}

        @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
        @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.LEADING_MARGIN_SPAN; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            writeToParcelInternal(dest, flags);
        }
        @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
            dest.writeInt(mFirst);
            dest.writeInt(mRest);
        }
        public static final android.os.Parcelable.Creator<Standard> CREATOR =
                new android.os.Parcelable.Creator<Standard>() {
            @Override public Standard createFromParcel(android.os.Parcel in) {
                return new Standard(in.readInt(), in.readInt());
            }
            @Override public Standard[] newArray(int size) { return new Standard[size]; }
        };
    }
}
