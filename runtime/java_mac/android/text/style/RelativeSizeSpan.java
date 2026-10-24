// android.text.style.RelativeSizeSpan -- AOSP: scale text size by a proportion.
// Compose SpanStyle(fontSize=...em) interop + HtmlCompat <big>/<small>. v1.0.423.
package android.text.style;
import android.text.TextPaint;
// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
// Payload (a single float) and type id 3 verified against
// frameworks/base/core/java/android/text/style/RelativeSizeSpan.java
// (android16-release), and matches TextUtils.java's own writeSpanPayload.
public class RelativeSizeSpan extends MetricAffectingSpan implements android.text.ParcelableSpan {
    private final float mProportion;
    public RelativeSizeSpan(float proportion) { mProportion = proportion; }
    public float getSizeChange() { return mProportion; }
    @Override public void updateDrawState(TextPaint tp) { apply(tp); }
    @Override public void updateMeasureState(TextPaint tp) { apply(tp); }
    private void apply(TextPaint p) { p.setTextSize(p.getTextSize() * mProportion); }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.RELATIVE_SIZE_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        dest.writeFloat(mProportion);
    }
    public static final android.os.Parcelable.Creator<RelativeSizeSpan> CREATOR =
            new android.os.Parcelable.Creator<RelativeSizeSpan>() {
        @Override public RelativeSizeSpan createFromParcel(android.os.Parcel in) {
            return new RelativeSizeSpan(in.readFloat());
        }
        @Override public RelativeSizeSpan[] newArray(int size) { return new RelativeSizeSpan[size]; }
    };
}
