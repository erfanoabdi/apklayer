// android.text.style.SuggestionRangeSpan -- highlights the range a suggestion
// popup is currently offering to replace, by painting a background colour
// behind it. Set by the editor while a SuggestionSpan's popup is open. Was
// absent entirely (NoClassDefFoundError). Mirrors
// frameworks/base/core/java/android/text/style/SuggestionRangeSpan.java.
package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

public final class SuggestionRangeSpan extends CharacterStyle implements ParcelableSpan {

    private int mBackgroundColor;

    public SuggestionRangeSpan() {
        // 0 is a fully transparent background, which is AOSP's default: the
        // editor sets a real colour before showing the popup.
        mBackgroundColor = 0;
    }

    /** AOSP SuggestionRangeSpan(Parcel src) -- @hide there, but the CREATOR
     *  below needs it and it costs nothing to keep it reachable. */
    public SuggestionRangeSpan(Parcel src) {
        mBackgroundColor = src.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }

    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeInt(mBackgroundColor);
    }

    @Override
    public int getSpanTypeId() {
        return getSpanTypeIdInternal();
    }

    @Override
    public int getSpanTypeIdInternal() {
        return TextUtils.SUGGESTION_RANGE_SPAN;
    }

    public void setBackgroundColor(int backgroundColor) {
        mBackgroundColor = backgroundColor;
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        tp.bgColor = mBackgroundColor;
    }

    public static final android.os.Parcelable.Creator<SuggestionRangeSpan> CREATOR =
            new android.os.Parcelable.Creator<SuggestionRangeSpan>() {
        @Override public SuggestionRangeSpan createFromParcel(Parcel source) {
            return new SuggestionRangeSpan(source);
        }
        @Override public SuggestionRangeSpan[] newArray(int size) {
            return new SuggestionRangeSpan[size];
        }
    };
}
