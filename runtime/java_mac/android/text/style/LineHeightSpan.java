// v1.0.124: AOSP LineHeightSpan -- paragraph span that adjusts line height.
// Compose UI's AndroidParagraphIntrinsics walks paragraph spans to
// compute line spacing; without this interface on the classpath the
// text-measure pass throws NoClassDefFoundError.
package android.text.style;

// AOSP: `public interface LineHeightSpan extends ParagraphStyle,
// WrapTogetherSpan`. The second super-interface was missing, and it is not
// cosmetic: StaticLayout checks `instanceof WrapTogetherSpan` when deciding
// whether a paragraph's spans force it to be laid out as one unit, so the
// missing interface makes every LineHeightSpan answer that check the wrong way.
public interface LineHeightSpan extends ParagraphStyle, WrapTogetherSpan {
    void chooseHeight(CharSequence text, int start, int end,
                      int spanstartv, int v,
                      android.graphics.Paint.FontMetricsInt fm);

    interface WithDensity extends LineHeightSpan {
        void chooseHeight(CharSequence text, int start, int end,
                          int spanstartv, int v,
                          android.graphics.Paint.FontMetricsInt fm,
                          android.text.TextPaint paint);
    }

    /**
     * Default implementation of LineHeightSpan: changes the line height of
     * the attached paragraph to a fixed number of physical pixels (affects
     * the WHOLE paragraph even when the span covers only part of it, same
     * as AOSP).
     *
     * AOSP: frameworks/base/core/java/android/text/style/LineHeightSpan.java
     * (Standard), whose chooseHeight algorithm this reproduces exactly, and
     * whose class declaration is `class Standard implements LineHeightSpan,
     * ParcelableSpan`. We now declare ParcelableSpan too (added together
     * with the android.text.TextUtils#marshalableSpanTypeId table row for
     * this class): that table is a fixed per-class dispatch keyed off
     * concrete type, not real `instanceof ParcelableSpan` resolution, so the
     * interface may only be declared here once a row exists for it --
     * otherwise `instanceof ParcelableSpan` would report true while
     * TextUtils silently dropped the span during writeToParcel (see the
     * comment above marshalableSpanTypeId). The row exists now, so this is
     * safe.
     */
    class Standard implements LineHeightSpan, android.text.ParcelableSpan {
        private final int mHeight;

        /** Sets the line height of the paragraph to {@code height} physical pixels. */
        public Standard(int height) {
            if (height <= 0) {
                throw new IllegalArgumentException("Height:" + height + "must be positive");
            }
            mHeight = height;
        }

        /** Returns the line height specified by this span. */
        public int getHeight() {
            return mHeight;
        }

        @Override
        public void chooseHeight(CharSequence text, int start, int end,
                                  int spanstartv, int v,
                                  android.graphics.Paint.FontMetricsInt fm) {
            final int originHeight = fm.descent - fm.ascent;
            // AOSP: if original height is not positive, do nothing.
            if (originHeight <= 0) {
                return;
            }
            final float ratio = mHeight * 1.0f / originHeight;
            fm.descent = Math.round(fm.descent * ratio);
            fm.ascent = fm.descent - mHeight;
        }

        // ---- ParcelableSpan -- AOSP TextUtils.LINE_HEIGHT_SPAN == 28 -------
        // (frameworks/base/core/java/android/text/TextUtils.java). The real
        // marshalling TextUtils#writeToParcel/CHAR_SEQUENCE_CREATOR use goes
        // through the LINE_HEIGHT_SPAN case wired into TextUtils' own
        // writeSpanPayload/readSpanPayload (calling getHeight() / the (int)
        // constructor directly), matching the majority convention in this
        // package (TextAppearanceSpan, Su[bp]erscriptSpan, SuggestionSpan,
        // QuoteSpan, BulletSpan): these methods exist so `instanceof
        // ParcelableSpan` and a direct writeToParcel() call are honest, not
        // because anything here calls writeToParcelInternal.
        @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
        @Override public int getSpanTypeIdInternal() { return 28; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            writeToParcelInternal(dest, flags);
        }
        @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
            // No IPC in Mac.apk; spans never cross a process boundary. Honest no-op.
        }
    }
}
