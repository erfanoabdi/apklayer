// android.graphics.text.LineBreakConfig -- AOSP config describing CJK line-
// break style / word style / hyphenation for the line breaker (API 33, extended
// through API 35/36). AOSP:
//   frameworks/base/graphics/java/android/graphics/text/LineBreakConfig.java
// We don't apply CJK-specific break rules, so this carries the values
// faithfully for callers that build one (MeasuredText.Builder.appendStyleRun
// takes it) without changing the (Latin-style) break behavior.
package android.graphics.text;

import android.os.Parcel;
import android.os.Parcelable;

public final class LineBreakConfig implements Parcelable {
    public static final int LINE_BREAK_STYLE_UNSPECIFIED = -1;
    public static final int LINE_BREAK_STYLE_NONE = 0;
    public static final int LINE_BREAK_STYLE_LOOSE = 1;
    public static final int LINE_BREAK_STYLE_NORMAL = 2;
    public static final int LINE_BREAK_STYLE_STRICT = 3;
    /** API 35 -- "no line break is performed" (com.android.text.flags
     *  .no_break_no_hyphenation_span). */
    public static final int LINE_BREAK_STYLE_NO_BREAK = 4;
    /** API 36 -- the framework picks the style from the text
     *  (com.android.text.flags.word_style_auto). */
    public static final int LINE_BREAK_STYLE_AUTO = 5;

    public static final int LINE_BREAK_WORD_STYLE_UNSPECIFIED = -1;
    public static final int LINE_BREAK_WORD_STYLE_NONE = 0;
    public static final int LINE_BREAK_WORD_STYLE_PHRASE = 1;
    /** API 36 (com.android.text.flags.word_style_auto). */
    public static final int LINE_BREAK_WORD_STYLE_AUTO = 2;

    /** API 35 hyphenation preference. UNSPECIFIED resolves to ENABLED --
     *  see getResolvedHyphenation. */
    public static final int HYPHENATION_UNSPECIFIED = -1;
    public static final int HYPHENATION_DISABLED = 0;
    public static final int HYPHENATION_ENABLED = 1;

    /** AOSP :448 builds NONE through the Builder, setting only the two style
     *  fields -- so its hyphenation stays UNSPECIFIED, not DISABLED. */
    public static final LineBreakConfig NONE =
            new Builder().setLineBreakStyle(LINE_BREAK_STYLE_NONE)
                    .setLineBreakWordStyle(LINE_BREAK_WORD_STYLE_NONE).build();

    private final int mLineBreakStyle;
    private final int mLineBreakWordStyle;
    private final int mHyphenation;

    private LineBreakConfig(int lineBreakStyle, int lineBreakWordStyle, int hyphenation) {
        mLineBreakStyle = lineBreakStyle;
        mLineBreakWordStyle = lineBreakWordStyle;
        mHyphenation = hyphenation;
    }

    public int getLineBreakStyle() { return mLineBreakStyle; }
    public int getLineBreakWordStyle() { return mLineBreakWordStyle; }
    /** AOSP :544. */
    public int getHyphenation() { return mHyphenation; }

    /** AOSP :556 -- UNSPECIFIED means ENABLED, and a null config means ENABLED
     *  too. Public in AOSP's @hide surface; kept because the resolution rule is
     *  the thing callers actually need and duplicating it invites drift. */
    public static int getResolvedHyphenation(LineBreakConfig config) {
        if (config == null) return HYPHENATION_ENABLED;
        return config.mHyphenation == HYPHENATION_UNSPECIFIED
                ? HYPHENATION_ENABLED : config.mHyphenation;
    }

    /** AOSP :589 -- a NEW config where each field of `config` wins UNLESS it is
     *  UNSPECIFIED, in which case this config's value survives. */
    public LineBreakConfig merge(LineBreakConfig config) {
        return new LineBreakConfig(
                config.mLineBreakStyle == LINE_BREAK_STYLE_UNSPECIFIED
                        ? mLineBreakStyle : config.mLineBreakStyle,
                config.mLineBreakWordStyle == LINE_BREAK_WORD_STYLE_UNSPECIFIED
                        ? mLineBreakWordStyle : config.mLineBreakWordStyle,
                config.mHyphenation == HYPHENATION_UNSPECIFIED
                        ? mHyphenation : config.mHyphenation);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LineBreakConfig)) return false;
        LineBreakConfig that = (LineBreakConfig) o;
        return mLineBreakStyle == that.mLineBreakStyle
                && mLineBreakWordStyle == that.mLineBreakWordStyle
                && mHyphenation == that.mHyphenation;
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(Integer.valueOf(mLineBreakStyle),
                Integer.valueOf(mLineBreakWordStyle), Integer.valueOf(mHyphenation));
    }

    @Override public String toString() {
        return "LineBreakConfig{mLineBreakStyle=" + mLineBreakStyle
                + ", mLineBreakWordStyle=" + mLineBreakWordStyle
                + ", mHyphenation=" + mHyphenation + '}';
    }

    // ---- Parcelable (AOSP :626-653). Three ints, in field order. ----
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mLineBreakStyle);
        dest.writeInt(mLineBreakWordStyle);
        dest.writeInt(mHyphenation);
    }

    public static final Parcelable.Creator<LineBreakConfig> CREATOR =
            new Parcelable.Creator<LineBreakConfig>() {
        @Override public LineBreakConfig createFromParcel(Parcel source) {
            final int lineBreakStyle = source.readInt();
            final int lineBreakWordStyle = source.readInt();
            final int hyphenation = source.readInt();
            return new LineBreakConfig(lineBreakStyle, lineBreakWordStyle, hyphenation);
        }
        @Override public LineBreakConfig[] newArray(int size) {
            return new LineBreakConfig[size];
        }
    };

    public static final class Builder {
        // AOSP :276-282 -- every field starts UNSPECIFIED, NOT NONE. That is
        // what makes merge() able to tell "not set" from "explicitly none".
        private int mLineBreakStyle = LINE_BREAK_STYLE_UNSPECIFIED;
        private int mLineBreakWordStyle = LINE_BREAK_WORD_STYLE_UNSPECIFIED;
        private int mHyphenation = HYPHENATION_UNSPECIFIED;

        public Builder() {}

        /** AOSP :322 -- unlike the setters, an UNSPECIFIED field in `config`
         *  LEAVES the builder's value alone rather than resetting it. */
        public Builder merge(LineBreakConfig config) {
            if (config.mLineBreakStyle != LINE_BREAK_STYLE_UNSPECIFIED) {
                mLineBreakStyle = config.mLineBreakStyle;
            }
            if (config.mLineBreakWordStyle != LINE_BREAK_WORD_STYLE_UNSPECIFIED) {
                mLineBreakWordStyle = config.mLineBreakWordStyle;
            }
            if (config.mHyphenation != HYPHENATION_UNSPECIFIED) {
                mHyphenation = config.mHyphenation;
            }
            return this;
        }

        public Builder setLineBreakStyle(int lineBreakStyle) {
            mLineBreakStyle = lineBreakStyle; return this;
        }
        public Builder setLineBreakWordStyle(int lineBreakWordStyle) {
            mLineBreakWordStyle = lineBreakWordStyle; return this;
        }
        /** AOSP :412. */
        public Builder setHyphenation(int hyphenation) {
            mHyphenation = hyphenation; return this;
        }
        public LineBreakConfig build() {
            return new LineBreakConfig(mLineBreakStyle, mLineBreakWordStyle, mHyphenation);
        }
    }
}
