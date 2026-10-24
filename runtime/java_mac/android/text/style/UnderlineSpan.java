// android.text.style.UnderlineSpan -- AOSP: underline the covered range.
// Compose AnnotatedString TextDecoration.Underline interop + HtmlCompat <u>.
// v1.0.423.
package android.text.style;
import android.text.TextPaint;
// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan. No
// payload -- AOSP's writeToParcelInternal body is empty (the span carries no
// state), type id 6. Matches TextUtils.java's writeSpanPayload default case.
public class UnderlineSpan extends CharacterStyle
        implements UpdateAppearance, android.text.ParcelableSpan {
    public UnderlineSpan() {}
    /** AOSP UnderlineSpan(Parcel src) -- public API, and empty because the span
     *  carries no state (AOSP's body is a bare `{}`). It exists so a caller can
     *  reconstruct the span from a Parcel directly, not only through CREATOR. */
    public UnderlineSpan(android.os.Parcel src) {}
    @Override public void updateDrawState(TextPaint tp) { tp.setUnderlineText(true); }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.UNDERLINE_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) { }
    public static final android.os.Parcelable.Creator<UnderlineSpan> CREATOR =
            new android.os.Parcelable.Creator<UnderlineSpan>() {
        @Override public UnderlineSpan createFromParcel(android.os.Parcel in) {
            return new UnderlineSpan(in);
        }
        @Override public UnderlineSpan[] newArray(int size) { return new UnderlineSpan[size]; }
    };
}
