// android.text.style.ScaleXSpan -- horizontal glyph scaling (condensed/expanded
// text). Applies to both measure and draw so layout stays consistent with what
// is painted; a measure-only or draw-only application produces text that
// overflows or under-fills its line.
// Source: frameworks/base/core/java/android/text/style/ScaleXSpan.java
package android.text.style;

import android.text.TextPaint;

// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
// Payload (a single float) and type id 4 verified against
// frameworks/base/core/java/android/text/style/ScaleXSpan.java
// (android16-release), and matches TextUtils.java's own writeSpanPayload.
public class ScaleXSpan extends MetricAffectingSpan implements android.text.ParcelableSpan {

    private final float mProportion;

    public ScaleXSpan(float proportion) { mProportion = proportion; }

    public float getScaleX() { return mProportion; }

    @Override public void updateDrawState(TextPaint tp) { apply(tp); }

    @Override public void updateMeasureState(TextPaint tp) { apply(tp); }

    private void apply(TextPaint p) { p.setTextScaleX(p.getTextScaleX() * mProportion); }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.SCALE_X_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        dest.writeFloat(mProportion);
    }
    public static final android.os.Parcelable.Creator<ScaleXSpan> CREATOR =
            new android.os.Parcelable.Creator<ScaleXSpan>() {
        @Override public ScaleXSpan createFromParcel(android.os.Parcel in) {
            return new ScaleXSpan(in.readFloat());
        }
        @Override public ScaleXSpan[] newArray(int size) { return new ScaleXSpan[size]; }
    };
}
