// android.text.style.StrikethroughSpan -- AOSP: strike through the range.
// Compose TextDecoration.LineThrough interop + HtmlCompat <s>/<strike>. v1.0.423.
package android.text.style;
import android.text.TextPaint;
// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan. No
// payload -- AOSP's writeToParcelInternal body is empty (the span carries no
// state), type id 5. Matches TextUtils.java's writeSpanPayload default case.
public class StrikethroughSpan extends CharacterStyle
        implements UpdateAppearance, android.text.ParcelableSpan {
    public StrikethroughSpan() {}
    /** AOSP StrikethroughSpan(Parcel src) -- public API, empty body: the span
     *  carries no state. */
    public StrikethroughSpan(android.os.Parcel src) {}
    @Override public void updateDrawState(TextPaint tp) { tp.setStrikeThruText(true); }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.STRIKETHROUGH_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) { }
    public static final android.os.Parcelable.Creator<StrikethroughSpan> CREATOR =
            new android.os.Parcelable.Creator<StrikethroughSpan>() {
        @Override public StrikethroughSpan createFromParcel(android.os.Parcel in) {
            return new StrikethroughSpan();
        }
        @Override public StrikethroughSpan[] newArray(int size) { return new StrikethroughSpan[size]; }
    };
}
