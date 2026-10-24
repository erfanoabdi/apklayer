// android.text.style.SubscriptSpan -- AOSP span that lowers the baseline of a
// run to render it as a subscript. AOSP:
//   frameworks/base/core/java/android/text/style/SubscriptSpan.java
//   public class SubscriptSpan extends MetricAffectingSpan implements ParcelableSpan
// Both updateDrawState and updateMeasureState do
//   textPaint.baselineShift -= (int)(textPaint.ascent() / 2);
package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;

public class SubscriptSpan extends MetricAffectingSpan implements ParcelableSpan {
    public SubscriptSpan() {}
    /** AOSP SubscriptSpan(Parcel src) -- public API, empty body: the span
     *  carries no state (its writeToParcelInternal is empty too). */
    public SubscriptSpan(Parcel src) {}

    @Override
    public void updateDrawState(TextPaint textPaint) {
        textPaint.baselineShift -= (int) (textPaint.ascent() / 2);
    }

    @Override
    public void updateMeasureState(TextPaint textPaint) {
        textPaint.baselineShift -= (int) (textPaint.ascent() / 2);
    }

    // AOSP TextUtils.SUBSCRIPT_SPAN == 16
    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return 16; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }
    @Override public void writeToParcelInternal(Parcel dest, int flags) {}
}
