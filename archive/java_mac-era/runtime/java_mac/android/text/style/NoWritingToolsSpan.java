// android.text.style.NoWritingToolsSpan (API 36) -- marks a range that the
// system "writing tools" (rewrite / proofread / summarize) must leave alone.
// An app attaches it to a code block, a quoted message, or anything else that
// must not be silently reworded. Was absent entirely (NoClassDefFoundError).
// Mirrors frameworks/base/core/java/android/text/style/NoWritingToolsSpan.java,
// which is a pure MARKER: it carries no state, and its whole contract is that
// it exists on the range and survives a Parcel.
//
// Mac.apk has no writing-tools provider, so nothing consults the marker today.
// That is not a divergence: a device with no writing-tools provider ignores it
// too, and the span's observable API (identity, type id, parcelability) is
// exactly reproduced.
package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextUtils;

public final class NoWritingToolsSpan implements ParcelableSpan {

    public NoWritingToolsSpan() {
    }

    @Override
    public int getSpanTypeId() {
        return getSpanTypeIdInternal();
    }

    @Override
    public int getSpanTypeIdInternal() {
        return TextUtils.NO_WRITING_TOOLS_SPAN;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }

    /** Empty: the span carries no state, exactly as on AOSP. */
    @Override
    public void writeToParcelInternal(Parcel dest, int flags) {
    }

    @Override
    public String toString() {
        return "NoWritingToolsSpan{}";
    }

    public static final android.os.Parcelable.Creator<NoWritingToolsSpan> CREATOR =
            new android.os.Parcelable.Creator<NoWritingToolsSpan>() {
        @Override public NoWritingToolsSpan createFromParcel(Parcel source) {
            return new NoWritingToolsSpan();
        }
        @Override public NoWritingToolsSpan[] newArray(int size) {
            return new NoWritingToolsSpan[size];
        }
    };
}
