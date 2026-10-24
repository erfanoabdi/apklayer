// android.text.style.ForegroundColorSpan -- AOSP span that changes
// the text color of the covered range. Per frameworks/base/core/java/
// android/text/style/ForegroundColorSpan.java: updateDrawState does
// textPaint.setColor(mColor). Calculator's CalculatorResult
// constructor instantiates one for its exponent styling.
package android.text.style;

import android.text.TextPaint;

// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
// Payload (a single int color) and type id 2 verified against
// frameworks/base/core/java/android/text/style/ForegroundColorSpan.java
// (android16-release), and matches android/text/TextUtils.java's own
// writeSpanPayload for FOREGROUND_COLOR_SPAN.
public class ForegroundColorSpan extends CharacterStyle
        implements UpdateAppearance, android.text.ParcelableSpan {
    private final int mColor;

    public ForegroundColorSpan(int color) { mColor = color; }

    public int getForegroundColor() { return mColor; }

    @Override
    public void updateDrawState(TextPaint textPaint) {
        textPaint.setColor(mColor);
    }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.FOREGROUND_COLOR_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        dest.writeInt(mColor);
    }
    public static final android.os.Parcelable.Creator<ForegroundColorSpan> CREATOR =
            new android.os.Parcelable.Creator<ForegroundColorSpan>() {
        @Override public ForegroundColorSpan createFromParcel(android.os.Parcel in) {
            return new ForegroundColorSpan(in.readInt());
        }
        @Override public ForegroundColorSpan[] newArray(int size) { return new ForegroundColorSpan[size]; }
    };
}
