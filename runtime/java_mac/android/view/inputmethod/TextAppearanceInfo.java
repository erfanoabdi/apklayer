// android.view.inputmethod.TextAppearanceInfo -- the editor's text-appearance
// attributes, carried on CursorAnchorInfo so an IME can draw its candidate view
// and its handwriting ink in the SAME style as the editor's own text.
// Source: frameworks/base
// core/java/android/view/inputmethod/TextAppearanceInfo.java (android16-release).
//
// Mac.apk runs no IME, so nothing consumes one -- but this is a plain immutable
// value object on AOSP too, every field round-trips, and a custom editor that
// builds one gets back exactly what it set.
package android.view.inputmethod;

public final class TextAppearanceInfo implements android.os.Parcelable {
    private final int mTextColor;
    private final int mHintTextColor;
    private final int mHighlightTextColor;
    private final int mLinkTextColor;
    private final boolean mAllCaps;
    private final String mSystemFontFamilyName;
    private final int mTextFontWeight;
    private final android.os.LocaleList mTextLocales;
    private final String mFontFeatureSettings;
    private final String mFontVariationSettings;
    private final int mLineBreakStyle;
    private final int mLineBreakWordStyle;
    private final float mTextScaleX;
    private final float mLetterSpacing;
    private final float mShadowDx;
    private final float mShadowDy;
    private final float mShadowRadius;
    private final int mShadowColor;
    private final boolean mElegantTextHeight;
    private final boolean mFallbackLineSpacing;
    private final float mTextSize;
    private final int mTextStyle;

    private TextAppearanceInfo(Builder b) {
        mTextColor = b.mTextColor;
        mHintTextColor = b.mHintTextColor;
        mHighlightTextColor = b.mHighlightTextColor;
        mLinkTextColor = b.mLinkTextColor;
        mAllCaps = b.mAllCaps;
        mSystemFontFamilyName = b.mSystemFontFamilyName;
        mTextFontWeight = b.mTextFontWeight;
        mTextLocales = b.mTextLocales;
        mFontFeatureSettings = b.mFontFeatureSettings;
        mFontVariationSettings = b.mFontVariationSettings;
        mLineBreakStyle = b.mLineBreakStyle;
        mLineBreakWordStyle = b.mLineBreakWordStyle;
        mTextScaleX = b.mTextScaleX;
        mLetterSpacing = b.mLetterSpacing;
        mShadowDx = b.mShadowDx;
        mShadowDy = b.mShadowDy;
        mShadowRadius = b.mShadowRadius;
        mShadowColor = b.mShadowColor;
        mElegantTextHeight = b.mElegantTextHeight;
        mFallbackLineSpacing = b.mFallbackLineSpacing;
        mTextSize = b.mTextSize;
        mTextStyle = b.mTextStyle;
    }

    public int getTextColor() { return mTextColor; }
    public int getHintTextColor() { return mHintTextColor; }
    public int getHighlightTextColor() { return mHighlightTextColor; }
    public int getLinkTextColor() { return mLinkTextColor; }
    public boolean isAllCaps() { return mAllCaps; }
    public String getSystemFontFamilyName() { return mSystemFontFamilyName; }
    public int getTextFontWeight() { return mTextFontWeight; }
    public android.os.LocaleList getTextLocales() { return mTextLocales; }
    public String getFontFeatureSettings() { return mFontFeatureSettings; }
    public String getFontVariationSettings() { return mFontVariationSettings; }
    public int getLineBreakStyle() { return mLineBreakStyle; }
    public int getLineBreakWordStyle() { return mLineBreakWordStyle; }
    public float getTextScaleX() { return mTextScaleX; }
    public float getLetterSpacing() { return mLetterSpacing; }
    public float getShadowDx() { return mShadowDx; }
    public float getShadowDy() { return mShadowDy; }
    public float getShadowRadius() { return mShadowRadius; }
    public int getShadowColor() { return mShadowColor; }
    public boolean isElegantTextHeight() { return mElegantTextHeight; }
    public boolean isFallbackLineSpacing() { return mFallbackLineSpacing; }
    public float getTextSize() { return mTextSize; }
    public int getTextStyle() { return mTextStyle; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mTextColor);
        dest.writeInt(mHintTextColor);
        dest.writeInt(mHighlightTextColor);
        dest.writeInt(mLinkTextColor);
        dest.writeBoolean(mAllCaps);
        dest.writeString(mSystemFontFamilyName);
        dest.writeInt(mTextFontWeight);
        dest.writeString(mTextLocales == null ? null : mTextLocales.toLanguageTags());
        dest.writeString(mFontFeatureSettings);
        dest.writeString(mFontVariationSettings);
        dest.writeInt(mLineBreakStyle);
        dest.writeInt(mLineBreakWordStyle);
        dest.writeFloat(mTextScaleX);
        dest.writeFloat(mLetterSpacing);
        dest.writeFloat(mShadowDx);
        dest.writeFloat(mShadowDy);
        dest.writeFloat(mShadowRadius);
        dest.writeInt(mShadowColor);
        dest.writeBoolean(mElegantTextHeight);
        dest.writeBoolean(mFallbackLineSpacing);
        dest.writeFloat(mTextSize);
        dest.writeInt(mTextStyle);
    }

    public static final android.os.Parcelable.Creator<TextAppearanceInfo> CREATOR =
            new android.os.Parcelable.Creator<TextAppearanceInfo>() {
        @Override public TextAppearanceInfo createFromParcel(android.os.Parcel in) {
            Builder b = new Builder();
            b.setTextColor(in.readInt());
            b.setHintTextColor(in.readInt());
            b.setHighlightTextColor(in.readInt());
            b.setLinkTextColor(in.readInt());
            b.setAllCaps(in.readBoolean());
            b.setSystemFontFamilyName(in.readString());
            b.setTextFontWeight(in.readInt());
            String tags = in.readString();
            b.setTextLocales(tags == null ? android.os.LocaleList.getDefault()
                    : android.os.LocaleList.forLanguageTags(tags));
            b.setFontFeatureSettings(in.readString());
            b.setFontVariationSettings(in.readString());
            b.setLineBreakStyle(in.readInt());
            b.setLineBreakWordStyle(in.readInt());
            b.setTextScaleX(in.readFloat());
            b.setLetterSpacing(in.readFloat());
            b.setShadowDx(in.readFloat());
            b.setShadowDy(in.readFloat());
            b.setShadowRadius(in.readFloat());
            b.setShadowColor(in.readInt());
            b.setElegantTextHeight(in.readBoolean());
            b.setFallbackLineSpacing(in.readBoolean());
            b.setTextSize(in.readFloat());
            b.setTextStyle(in.readInt());
            return b.build();
        }
        @Override public TextAppearanceInfo[] newArray(int size) {
            return new TextAppearanceInfo[size];
        }
    };

    /** AOSP TextAppearanceInfo.Builder. */
    public static final class Builder {
        private int mTextColor = 0;
        private int mHintTextColor = 0;
        private int mHighlightTextColor = 0;
        private int mLinkTextColor = 0;
        private boolean mAllCaps = false;
        private String mSystemFontFamilyName = null;
        private int mTextFontWeight = android.graphics.fonts.FontStyle.FONT_WEIGHT_UNSPECIFIED;
        private android.os.LocaleList mTextLocales = android.os.LocaleList.getDefault();
        private String mFontFeatureSettings = null;
        private String mFontVariationSettings = null;
        private int mLineBreakStyle = 0;
        private int mLineBreakWordStyle = 0;
        private float mTextScaleX = 1f;
        private float mLetterSpacing = 0f;
        private float mShadowDx = 0f;
        private float mShadowDy = 0f;
        private float mShadowRadius = 0f;
        private int mShadowColor = 0;
        private boolean mElegantTextHeight = false;
        private boolean mFallbackLineSpacing = false;
        private float mTextSize = 0f;
        private int mTextStyle = 0;

        public Builder() {}
        public Builder setTextColor(int v) { mTextColor = v; return this; }
        public Builder setHintTextColor(int v) { mHintTextColor = v; return this; }
        public Builder setHighlightTextColor(int v) { mHighlightTextColor = v; return this; }
        public Builder setLinkTextColor(int v) { mLinkTextColor = v; return this; }
        public Builder setAllCaps(boolean v) { mAllCaps = v; return this; }
        public Builder setSystemFontFamilyName(String v) { mSystemFontFamilyName = v; return this; }
        public Builder setTextFontWeight(int v) { mTextFontWeight = v; return this; }
        public Builder setTextLocales(android.os.LocaleList v) { mTextLocales = v; return this; }
        public Builder setFontFeatureSettings(String v) { mFontFeatureSettings = v; return this; }
        public Builder setFontVariationSettings(String v) { mFontVariationSettings = v; return this; }
        public Builder setLineBreakStyle(int v) { mLineBreakStyle = v; return this; }
        public Builder setLineBreakWordStyle(int v) { mLineBreakWordStyle = v; return this; }
        public Builder setTextScaleX(float v) { mTextScaleX = v; return this; }
        public Builder setLetterSpacing(float v) { mLetterSpacing = v; return this; }
        public Builder setShadowDx(float v) { mShadowDx = v; return this; }
        public Builder setShadowDy(float v) { mShadowDy = v; return this; }
        public Builder setShadowRadius(float v) { mShadowRadius = v; return this; }
        public Builder setShadowColor(int v) { mShadowColor = v; return this; }
        public Builder setElegantTextHeight(boolean v) { mElegantTextHeight = v; return this; }
        public Builder setFallbackLineSpacing(boolean v) { mFallbackLineSpacing = v; return this; }
        public Builder setTextSize(float v) { mTextSize = v; return this; }
        public Builder setTextStyle(int v) { mTextStyle = v; return this; }
        public TextAppearanceInfo build() { return new TextAppearanceInfo(this); }
    }
}
