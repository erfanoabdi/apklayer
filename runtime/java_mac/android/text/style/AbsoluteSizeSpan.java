// android.text.style.AbsoluteSizeSpan -- AOSP: set an absolute text size (px or
// dip) on the covered range. Compose SpanStyle(fontSize=) interop. v1.0.423.
package android.text.style;
import android.text.TextPaint;
// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
// Payload (size, dip) and type id 16 (TextUtils.ABSOLUTE_SIZE_SPAN) verified
// against frameworks/base/core/java/android/text/style/AbsoluteSizeSpan.java
// (android16-release) L61-90, and already independently verified in-tree by
// android/text/TextUtils.java's writeSpanPayload/readSpanPayload (whose own
// comment states the payload matches AOSP "for every listed type"). NOTE for
// whoever retires TextUtils's marshalableSpanTypeId workaround: this class
// now reports its id correctly, one of the "colour/style/size spans" that
// comment's removal condition is waiting on.
public class AbsoluteSizeSpan extends MetricAffectingSpan implements android.text.ParcelableSpan {
    private final int mSize; private final boolean mDip;
    public AbsoluteSizeSpan(int size) { this(size, false); }
    public AbsoluteSizeSpan(int size, boolean dip) { mSize = size; mDip = dip; }
    public int getSize() { return mSize; }
    public boolean getDip() { return mDip; }
    @Override public void updateDrawState(TextPaint tp) { apply(tp); }
    @Override public void updateMeasureState(TextPaint tp) { apply(tp); }
    private void apply(TextPaint p) {
        // dip sizes scale by the device density (AOSP multiplies by the
        // TextPaint's density, which a TextView sets from its DisplayMetrics).
        p.setTextSize(mDip ? mSize * android.util.DisplayMetrics.macapkDeviceDensity() : mSize);
    }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.ABSOLUTE_SIZE_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        dest.writeInt(mSize);
        dest.writeInt(mDip ? 1 : 0);
    }
    public static final android.os.Parcelable.Creator<AbsoluteSizeSpan> CREATOR =
            new android.os.Parcelable.Creator<AbsoluteSizeSpan>() {
        @Override public AbsoluteSizeSpan createFromParcel(android.os.Parcel in) {
            return new AbsoluteSizeSpan(in.readInt(), in.readInt() != 0);
        }
        @Override public AbsoluteSizeSpan[] newArray(int size) { return new AbsoluteSizeSpan[size]; }
    };
}
