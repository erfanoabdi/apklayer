// android.text.style.LineBreakConfigSpan (API 35) -- a character-level span
// that overrides the line-break configuration for the range it covers: "never
// break inside this run" (createNoBreakSpan) or "never hyphenate inside it"
// (createNoHyphenationSpan). Was absent entirely (NoClassDefFoundError).
// Mirrors frameworks/base/core/java/android/text/style/LineBreakConfigSpan.java.
//
// ⚠ The span is CARRIED but not yet ACTED ON: this tree's line breaker is a
// single greedy algorithm with no per-run configuration hook (see
// Layout.getLineBreakConfig()'s note). Storing and reporting the config is the
// whole of the API contract -- getLineBreakConfig() answers what was set, and
// the span round-trips through a Parcel -- and it is what lets an app attach
// one without crashing. Making the breaker honour it is a line-breaking change,
// not an API gap.
package android.text.style;

import android.graphics.text.LineBreakConfig;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextUtils;

public final class LineBreakConfigSpan implements ParcelableSpan {

    private final LineBreakConfig mLineBreakConfig;

    public LineBreakConfigSpan(LineBreakConfig lineBreakConfig) {
        if (lineBreakConfig == null) {
            throw new NullPointerException("lineBreakConfig must not be null");
        }
        mLineBreakConfig = lineBreakConfig;
    }

    /** AOSP LineBreakConfigSpan.getLineBreakConfig(). */
    public LineBreakConfig getLineBreakConfig() {
        return mLineBreakConfig;
    }

    /**
     * AOSP LineBreakConfigSpan.createNoBreakSpan(): a span configured with
     * LINE_BREAK_STYLE_NO_BREAK, i.e. the covered run is never split across
     * lines.
     */
    public static LineBreakConfigSpan createNoBreakSpan() {
        return new LineBreakConfigSpan(new LineBreakConfig.Builder()
                .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
                .build());
    }

    /**
     * AOSP LineBreakConfigSpan.createNoHyphenationSpan(): a span configured
     * with LINE_BREAK_WORD_STYLE_NO_BREAK, i.e. the covered run is never
     * hyphenated.
     */
    public static LineBreakConfigSpan createNoHyphenationSpan() {
        return new LineBreakConfigSpan(new LineBreakConfig.Builder()
                .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                .build());
    }

    @Override
    public int getSpanTypeId() {
        return getSpanTypeIdInternal();
    }

    @Override
    public int getSpanTypeIdInternal() {
        return TextUtils.LINE_BREAK_CONFIG_SPAN;
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
        dest.writeInt(mLineBreakConfig.getLineBreakStyle());
        dest.writeInt(mLineBreakConfig.getLineBreakWordStyle());
    }

    @Override
    public String toString() {
        return "LineBreakConfigSpan{config=" + mLineBreakConfig + "}";
    }

    public static final android.os.Parcelable.Creator<LineBreakConfigSpan> CREATOR =
            new android.os.Parcelable.Creator<LineBreakConfigSpan>() {
        @Override public LineBreakConfigSpan createFromParcel(Parcel source) {
            int style = source.readInt();
            int wordStyle = source.readInt();
            return new LineBreakConfigSpan(new LineBreakConfig.Builder()
                    .setLineBreakStyle(style)
                    .setLineBreakWordStyle(wordStyle)
                    .build());
        }
        @Override public LineBreakConfigSpan[] newArray(int size) {
            return new LineBreakConfigSpan[size];
        }
    };
}
