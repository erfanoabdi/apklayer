// android.text.style.SuperscriptSpan -- AOSP span that raises the baseline of
// a run to render it as a superscript. AOSP:
//   frameworks/base/core/java/android/text/style/SuperscriptSpan.java
//   public class SuperscriptSpan extends MetricAffectingSpan implements ParcelableSpan
// Both updateDrawState and updateMeasureState do
//   textPaint.baselineShift += (int)(textPaint.ascent() / 2);
package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;

public class SuperscriptSpan extends MetricAffectingSpan implements ParcelableSpan {
    public SuperscriptSpan() {}
    /** AOSP SuperscriptSpan(Parcel src) -- public API, empty body. */
    public SuperscriptSpan(Parcel src) {}

    @Override
    public void updateDrawState(TextPaint textPaint) {
        textPaint.baselineShift += (int) (textPaint.ascent() / 2);
    }

    @Override
    public void updateMeasureState(TextPaint textPaint) {
        textPaint.baselineShift += (int) (textPaint.ascent() / 2);
    }

    // AOSP TextUtils.SUPERSCRIPT_SPAN == 17
    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return 17; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }
    @Override public void writeToParcelInternal(Parcel dest, int flags) {}
}
