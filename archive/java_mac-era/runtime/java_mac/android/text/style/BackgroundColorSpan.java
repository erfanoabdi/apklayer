// android.text.style.BackgroundColorSpan -- AOSP span that paints a
// background color behind the covered range. Per frameworks/base/
// core/java/android/text/style/BackgroundColorSpan.java:
// updateDrawState does textPaint.bgColor = mColor. Sibling of
// ForegroundColorSpan (Calculator's result highlight-on-copy uses
// the pair).
package android.text.style;

import android.text.TextPaint;

// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
// Payload (a single int color) and type id 12 verified against
// frameworks/base/core/java/android/text/style/BackgroundColorSpan.java
// (android16-release), and matches android/text/TextUtils.java's own
// writeSpanPayload for BACKGROUND_COLOR_SPAN.
public class BackgroundColorSpan extends CharacterStyle
        implements UpdateAppearance, android.text.ParcelableSpan {
    private final int mColor;

    public BackgroundColorSpan(int color) { mColor = color; }

    public int getBackgroundColor() { return mColor; }

    @Override
    public void updateDrawState(TextPaint textPaint) {
        textPaint.bgColor = mColor;
    }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.BACKGROUND_COLOR_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        dest.writeInt(mColor);
    }
    public static final android.os.Parcelable.Creator<BackgroundColorSpan> CREATOR =
            new android.os.Parcelable.Creator<BackgroundColorSpan>() {
        @Override public BackgroundColorSpan createFromParcel(android.os.Parcel in) {
            return new BackgroundColorSpan(in.readInt());
        }
        @Override public BackgroundColorSpan[] newArray(int size) { return new BackgroundColorSpan[size]; }
    };
}
