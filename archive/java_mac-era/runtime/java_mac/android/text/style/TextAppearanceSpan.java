// android.text.style.TextAppearanceSpan -- AOSP span that applies a text
// appearance (family / style / size / color / link color, plus shadow,
// letter-spacing, font features) to a run. AOSP:
//   frameworks/base/core/java/android/text/style/TextAppearanceSpan.java
//   public class TextAppearanceSpan extends MetricAffectingSpan
//       implements ParcelableSpan
//
// The (String family, int style, int size, ColorStateList color,
// ColorStateList linkColor) constructor is fully honored. The (Context,
// styleResId[, colorList]) constructor reads the appearance style through
// Context.obtainStyledAttributes(resId, TextAppearance) exactly as AOSP does
// (it used to construct an inert appearance -- see that constructor).
package android.text.style;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.LocaleList;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;

public class TextAppearanceSpan extends MetricAffectingSpan implements ParcelableSpan {
    private final String mTypeface;
    private final int mStyle;
    private final int mTextSize;
    private final ColorStateList mTextColor;
    private final ColorStateList mTextColorLink;
    private Typeface mFontTypeface;

    private int mTextFontWeight = -1;
    private LocaleList mTextLocales;
    private float mShadowRadius;
    private float mShadowDx;
    private float mShadowDy;
    private int mShadowColor;
    private String mFontFeatureSettings;
    private String mFontVariationSettings;
    private boolean mElegantTextHeight;
    /** AOSP mHasElegantTextHeight: only an appearance that DECLARES
     *  elegantTextHeight may set it on the paint. */
    private boolean mHasElegantTextHeight;
    private float mLetterSpacing = Float.MAX_VALUE;

    public TextAppearanceSpan(Context context, int appearance) {
        this(context, appearance, -1);
    }

    /** BISECT GATE (2026-09-26, default ON; lane B-inflater, cluster B5 of
     *  docs/widget-fidelity-cluster_2026-09-25.md). MACAPK_TA_SPAN_REAL=0
     *  restores the pre-change span: the (Context, int[, int]) constructor builds
     *  an INERT appearance again, and updateMeasureState / updateDrawState use
     *  their pre-change bodies (macapkLegacy*). */
    static final boolean MACAPK_TA_SPAN_REAL =
        !"0".equals(System.getenv("MACAPK_TA_SPAN_REAL"));

    /** Set by the first construction that passes a colorList, so the warning
     *  below is printed once per process rather than once per span (a list row
     *  can build one span per bind). */
    private static final java.util.concurrent.atomic.AtomicBoolean MACAPK_COLORLIST_WARNED =
        new java.util.concurrent.atomic.AtomicBoolean();

    /** The members of AOSP's R.styleable.TextAppearance this span reads
     *  (attrs.xml android14-release `<declare-styleable name="TextAppearance">`),
     *  by framework attr id. Ids verified against public-final.xml
     *  (android14-release); the order here is ours, the TypedArray is indexed by
     *  position so it need not be the styleable's. */
    private static final int[] MACAPK_TEXT_APPEARANCE = {
        0x01010095, // 0  textSize
        0x01010096, // 1  typeface
        0x01010097, // 2  textStyle
        0x01010098, // 3  textColor
        0x0101009b, // 4  textColorLink
        0x01010161, // 5  shadowColor
        0x01010162, // 6  shadowDx
        0x01010163, // 7  shadowDy
        0x01010164, // 8  shadowRadius
        0x010103ac, // 9  fontFamily
        0x0101045d, // 10 elegantTextHeight
        0x010104b6, // 11 letterSpacing
        0x010104b7, // 12 fontFeatureSettings
        0x01010570, // 13 fontVariationSettings
        0x01010585, // 14 textFontWeight
        0x01010592, // 15 textLocale
    };

    /** AOSP TextAppearanceSpan(Context, int, int) (android14-release
     *  core/java/android/text/style/TextAppearanceSpan.java:102-203):
     *      TypedArray a = context.obtainStyledAttributes(appearance,
     *              com.android.internal.R.styleable.TextAppearance);
     *      textColor = a.getColorStateList(TextAppearance_textColor);
     *      mTextColorLink = a.getColorStateList(TextAppearance_textColorLink);
     *      mTextSize = a.getDimensionPixelSize(TextAppearance_textSize, -1);
     *      mStyle = a.getInt(TextAppearance_textStyle, 0);
     *      mTypeface = a.getFont(TextAppearance_fontFamily);   // font RESOURCE
     *      family = mTypeface == null ? a.getString(fontFamily)
     *               : null, else the `typeface` enum (1 sans, 2 serif, 3 monospace)
     *      mTextFontWeight = a.getInt(textFontWeight, FONT_WEIGHT_UNSPECIFIED);
     *      ... textLocale, shadow*, elegantTextHeight, letterSpacing, font
     *      feature / variation settings ...
     *
     *  The old body built an INERT appearance ("we don't resolve arbitrary style
     *  resources") although Theme.obtainStyledAttributes(resId, int[]) has
     *  resolved style resources since the Anuto fix. MEASURED victim:
     *  sgtpuzzles' GameChooser styles each game name with
     *  `new TextAppearanceSpan(ctx, R.style.ChooserItemName)` -- parent
     *  `@android:style/TextAppearance.Medium` (18sp), fontFamily
     *  sans-serif-light -- and the names drew at the row's base size and weight.
     *  Corpus (60 converted apps): the (Context, int) form is called by
     *  sgtpuzzles, Signal (5 sites), Duolingo and Wikipedia; the 3-arg form by
     *  NONE.
     *
     *  ⚠ ONE PART CANNOT BE HONOURED: `colorList >= 0`. AOSP reads it as an
     *  index into the INTERNAL R.styleable.Theme of
     *  com.android.internal.R.style.Theme. That array interleaves private attrs
     *  whose ids are assigned per platform BUILD, so the index -> attribute map
     *  is not recoverable from public-final.xml or framework-res.apk, and an app
     *  cannot even name it from the SDK (android.R.styleable is not public). We
     *  keep the appearance's own textColor and say so once, loudly, rather than
     *  guess an attribute. No app in the corpus passes one. */
    public TextAppearanceSpan(Context context, int appearance, int colorList) {
        String family = null;
        int style = 0;
        int size = -1;
        ColorStateList color = null;
        ColorStateList link = null;
        if (MACAPK_TA_SPAN_REAL && context != null) {
            android.content.res.TypedArray a = null;
            try {
                a = context.obtainStyledAttributes(appearance, MACAPK_TEXT_APPEARANCE);
            } catch (Throwable ignored) {}
            if (a != null) {
                try { color = a.getColorStateList(3); } catch (Throwable ignored) {}
                try { link = a.getColorStateList(4); } catch (Throwable ignored) {}
                try { size = a.getDimensionPixelSize(0, -1); } catch (Throwable ignored) {}
                style = macapkReadTextStyle(a, 2);
                // A font RESOURCE first (AOSP getFont); getFont throws for a value
                // that names no font, where AOSP's loadFont returns null for a
                // non-"res/" string, so a throw is "no font resource".
                try { mFontTypeface = a.getFont(9); } catch (Throwable ignored) { mFontTypeface = null; }
                if (mFontTypeface == null) {
                    String fam = null;
                    try { fam = a.getString(9); } catch (Throwable ignored) {}
                    if (fam != null) {
                        family = fam;
                    } else {
                        int tf = 0;
                        try { tf = a.getInt(1, 0); } catch (Throwable ignored) {}
                        if (tf == 0) tf = macapkTypefaceEnum(a, 1);
                        switch (tf) {
                            case 1: family = "sans"; break;
                            case 2: family = "serif"; break;
                            case 3: family = "monospace"; break;
                            default: family = null; break;
                        }
                    }
                }
                try { mTextFontWeight = a.getInt(14, -1); } catch (Throwable ignored) {}
                String locale = null;
                try { locale = a.getString(15); } catch (Throwable ignored) {}
                if (locale != null) {
                    LocaleList ll = LocaleList.forLanguageTags(locale);
                    mTextLocales = (ll != null && !ll.isEmpty()) ? ll : null;
                }
                try { mShadowRadius = a.getFloat(8, 0.0f); } catch (Throwable ignored) {}
                try { mShadowDx = a.getFloat(6, 0.0f); } catch (Throwable ignored) {}
                try { mShadowDy = a.getFloat(7, 0.0f); } catch (Throwable ignored) {}
                // AOSP reads shadowColor with getInt, which on a device returns the
                // TYPE_INT_COLOR data; our TypedArray holds the colour as text, so
                // getColor is the read that yields the same int.
                try { mShadowColor = a.getColor(5, 0); } catch (Throwable ignored) {}
                try {
                    mHasElegantTextHeight = a.hasValue(10);
                    mElegantTextHeight = a.getBoolean(10, false);
                } catch (Throwable ignored) {}
                try {
                    if (a.hasValue(11)) mLetterSpacing = a.getFloat(11, 0.0f);
                } catch (Throwable ignored) {}
                try { mFontFeatureSettings = a.getString(12); } catch (Throwable ignored) {}
                try { mFontVariationSettings = a.getString(13); } catch (Throwable ignored) {}
                try { a.recycle(); } catch (Throwable ignored) {}
            }
            if (colorList >= 0 && MACAPK_COLORLIST_WARNED.compareAndSet(false, true)) {
                com.macapk.loader.NativeLog.w("Mac.apk.TextAppearanceSpan",
                    "colorList=" + colorList + " indexes the INTERNAL R.styleable.Theme,"
                    + " which is build-specific and not reproducible here; keeping the"
                    + " appearance's own textColor");
            }
        }
        mTypeface = family;
        mStyle = style;
        mTextSize = size;
        mTextColor = color;
        mTextColorLink = link;
    }

    /** textStyle is a FLAG attribute (normal 0, bold 1, italic 2). getInt answers
     *  a compiled int; a value that reaches us as the symbol text ("bold|italic")
     *  is decoded here rather than read as 0. */
    private static int macapkReadTextStyle(android.content.res.TypedArray a, int index) {
        String raw = null;
        try { raw = a.getString(index); } catch (Throwable ignored) {}
        if (raw == null) return 0;
        String t = raw.trim();
        try { return Integer.decode(t); } catch (NumberFormatException ignored) {}
        int s = 0;
        for (String part : t.split("\\|")) {
            switch (part.trim()) {
                case "bold": s |= Typeface.BOLD; break;
                case "italic": s |= Typeface.ITALIC; break;
                default: break;
            }
        }
        return s;
    }

    /** The `typeface` ENUM (normal 0, sans 1, serif 2, monospace 3) when it
     *  reaches us as its symbol text. */
    private static int macapkTypefaceEnum(android.content.res.TypedArray a, int index) {
        String raw = null;
        try { raw = a.getString(index); } catch (Throwable ignored) {}
        if (raw == null) return 0;
        switch (raw.trim()) {
            case "sans": return 1;
            case "serif": return 2;
            case "monospace": return 3;
            default:
                try { return Integer.decode(raw.trim()); } catch (NumberFormatException e) { return 0; }
        }
    }

    public TextAppearanceSpan(String family, int style, int size,
                              ColorStateList color, ColorStateList linkColor) {
        mTypeface = family;
        mStyle = style;
        mTextSize = size;
        mTextColor = color;
        mTextColorLink = linkColor;
    }

    /**
     * AOSP TextAppearanceSpan(Parcel src) (TextAppearanceSpan.java:236-258) --
     * the public un-parcelling constructor, reading the fields in this exact
     * order: familyName, style, textSize, textColor, textColorLink, typeface,
     * textFontWeight, textLocales, shadowRadius, shadowDx, shadowDy,
     * shadowColor, hasElegantTextHeight, elegantTextHeight, hasLetterSpacing,
     * letterSpacing, fontFeatureSettings, fontVariationSettings.
     *
     * ⚠ ONE DIVERGENCE, and it is in the wire format rather than the API:
     * AOSP writes each ColorStateList with ColorStateList.CREATOR, but
     * android.content.res.ColorStateList in this tree is not Parcelable (no
     * CREATOR, and that package belongs to another lane), so the two colour
     * fields are carried as a present-flag plus the DEFAULT COLOUR and rebuilt
     * with ColorStateList.valueOf(). A state-dependent colour list therefore
     * collapses to its default colour across a parcel round trip. Everything
     * else round-trips exactly, and writeToParcelInternal below is the precise
     * inverse of this constructor, so an in-process round trip is lossless
     * apart from that one collapse. Mac.apk has no IPC, so this format is
     * never read by anything but its own writer.
     */
    public TextAppearanceSpan(Parcel src) {
        mTypeface = src.readString();
        mStyle = src.readInt();
        mTextSize = src.readInt();
        mTextColor = src.readInt() != 0 ? ColorStateList.valueOf(src.readInt()) : null;
        mTextColorLink = src.readInt() != 0 ? ColorStateList.valueOf(src.readInt()) : null;
        mFontTypeface = TypefaceSpan.macapkReadTypeface(src);
        mTextFontWeight = src.readInt();
        mTextLocales = src.readParcelable(LocaleList.class.getClassLoader(), LocaleList.class);
        mShadowRadius = src.readFloat();
        mShadowDx = src.readFloat();
        mShadowDy = src.readFloat();
        mShadowColor = src.readInt();
        mElegantTextHeight = src.readBoolean();
        // The wire format carries no has-flag (see the class note above); a true
        // value can only have come from a declared one.
        mHasElegantTextHeight = mElegantTextHeight;
        mLetterSpacing = src.readFloat();
        mFontFeatureSettings = src.readString();
        mFontVariationSettings = src.readString();
    }

    public String getFamily() { return mTypeface; }
    public ColorStateList getTextColor() { return mTextColor; }
    public ColorStateList getLinkTextColor() { return mTextColorLink; }
    public int getTextSize() { return mTextSize; }
    public int getTextStyle() { return mStyle; }
    public int getTextFontWeight() { return mTextFontWeight; }
    public LocaleList getTextLocales() { return mTextLocales; }
    public Typeface getTypeface() { return mFontTypeface; }
    public int getShadowColor() { return mShadowColor; }
    public float getShadowDx() { return mShadowDx; }
    public float getShadowDy() { return mShadowDy; }
    public float getShadowRadius() { return mShadowRadius; }
    public String getFontFeatureSettings() { return mFontFeatureSettings; }
    public String getFontVariationSettings() { return mFontVariationSettings; }
    public boolean isElegantTextHeight() { return mElegantTextHeight; }
    public float getLetterSpacing() { return mLetterSpacing; }

    /** AOSP updateDrawState (TextAppearanceSpan.java android14-release
     *  :454-468). The colour is chosen for the paint's drawableState; our
     *  TextView does not publish its drawable state on the paint, so a null
     *  state falls back to the list's default colour, which is what AOSP's
     *  null-state match returns for any list with a wildcard row. The shadow is
     *  gated on its COLOUR, as AOSP gates it (the old body gated on radius). */
    @Override
    public void updateDrawState(TextPaint ds) {
        if (!MACAPK_TA_SPAN_REAL) { macapkLegacyUpdateDrawState(ds); return; }
        updateMeasureState(ds);
        if (mTextColor != null) {
            ds.setColor(ds.drawableState != null
                ? mTextColor.getColorForState(ds.drawableState, 0)
                : mTextColor.getDefaultColor());
        }
        if (mTextColorLink != null) {
            ds.linkColor = ds.drawableState != null
                ? mTextColorLink.getColorForState(ds.drawableState, 0)
                : mTextColorLink.getDefaultColor();
        }
        if (mShadowColor != 0) {
            ds.setShadowLayer(mShadowRadius, mShadowDx, mShadowDy, mShadowColor);
        }
    }

    /** AOSP updateMeasureState (TextAppearanceSpan.java android14-release
     *  :471-544): a font RESOURCE wins; otherwise a family and/or style is
     *  MERGED with the paint's current face (the paint's style bits OR'd in, the
     *  paint's face kept when no family is named), textFontWeight re-weights the
     *  result, and any style the chosen face cannot supply is faked (bold ->
     *  fakeBold, italic -> skew -0.25). The old body dropped the paint's own
     *  style and never faked, so a bold run lost its weight under a family span.
     *  Paint.setTypeface here returns the PREVIOUS face (AOSP returns the new
     *  one), so the ready face is computed first and set once. */
    @Override
    public void updateMeasureState(TextPaint ds) {
        if (!MACAPK_TA_SPAN_REAL) { macapkLegacyUpdateMeasureState(ds); return; }
        final Typeface styledTypeface;
        int style = 0;
        if (mFontTypeface != null) {
            style = mStyle;
            styledTypeface = Typeface.create(mFontTypeface, style);
        } else if (mTypeface != null || mStyle != 0) {
            Typeface tf = ds.getTypeface();
            if (tf != null) style = tf.getStyle();
            style |= mStyle;
            if (mTypeface != null) {
                styledTypeface = Typeface.create(mTypeface, style);
            } else if (tf == null) {
                styledTypeface = Typeface.defaultFromStyle(style);
            } else {
                styledTypeface = Typeface.create(tf, style);
            }
        } else {
            styledTypeface = null;
        }
        if (styledTypeface != null) {
            final Typeface readyTypeface;
            if (mTextFontWeight >= 0) {
                final int weight = Math.min(android.graphics.fonts.FontStyle.FONT_WEIGHT_MAX,
                                            mTextFontWeight);
                final boolean italic = (style & Typeface.ITALIC) != 0;
                readyTypeface = Typeface.create(styledTypeface, weight, italic);
            } else {
                readyTypeface = styledTypeface;
            }
            int fake = style & ~readyTypeface.getStyle();
            if ((fake & Typeface.BOLD) != 0) ds.setFakeBoldText(true);
            if ((fake & Typeface.ITALIC) != 0) ds.setTextSkewX(-0.25f);
            ds.setTypeface(readyTypeface);
        }
        if (mTextSize > 0) ds.setTextSize(mTextSize);
        if (mTextLocales != null) ds.setTextLocales(mTextLocales);
        if (mHasElegantTextHeight) ds.setElegantTextHeight(mElegantTextHeight);
        if (mLetterSpacing != Float.MAX_VALUE) ds.setLetterSpacing(mLetterSpacing);
        if (mFontFeatureSettings != null) ds.setFontFeatureSettings(mFontFeatureSettings);
        if (mFontVariationSettings != null) ds.setFontVariationSettings(mFontVariationSettings);
    }

    /** The pre-change bodies, kept verbatim for MACAPK_TA_SPAN_REAL=0. */
    private void macapkLegacyUpdateDrawState(TextPaint ds) {
        macapkLegacyUpdateMeasureState(ds);
        if (mTextColor != null) ds.setColor(mTextColor.getDefaultColor());
        if (mTextColorLink != null) ds.linkColor = mTextColorLink.getDefaultColor();
        if (mShadowRadius != 0f) {
            ds.setShadowLayer(mShadowRadius, mShadowDx, mShadowDy, mShadowColor);
        }
    }

    private void macapkLegacyUpdateMeasureState(TextPaint ds) {
        // Typeface / style.
        if (mTypeface != null || mStyle != 0) {
            Typeface tf = (mTypeface != null)
                ? Typeface.create(mTypeface, mStyle) : Typeface.create((Typeface) null, mStyle);
            ds.setTypeface(tf);
        } else if (mFontTypeface != null) {
            ds.setTypeface(mFontTypeface);
        }
        if (mTextSize > 0) ds.setTextSize(mTextSize);
        if (mLetterSpacing != Float.MAX_VALUE) ds.setLetterSpacing(mLetterSpacing);
        if (mFontFeatureSettings != null) ds.setFontFeatureSettings(mFontFeatureSettings);
    }

    // ---- ParcelableSpan -- AOSP TextUtils.TEXT_APPEARANCE_SPAN == 5 -------
    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return 5; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }
    /** The exact inverse of TextAppearanceSpan(Parcel) above. It used to be an
     *  empty body, so a TextAppearanceSpan written to a Parcel came back as a
     *  span with every field at its default -- silently inert. */
    @Override public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeString(mTypeface);
        dest.writeInt(mStyle);
        dest.writeInt(mTextSize);
        if (mTextColor != null) {
            dest.writeInt(1);
            dest.writeInt(mTextColor.getDefaultColor());
        } else {
            dest.writeInt(0);
        }
        if (mTextColorLink != null) {
            dest.writeInt(1);
            dest.writeInt(mTextColorLink.getDefaultColor());
        } else {
            dest.writeInt(0);
        }
        TypefaceSpan.macapkWriteTypeface(mFontTypeface, dest);
        dest.writeInt(mTextFontWeight);
        dest.writeParcelable(mTextLocales, flags);
        dest.writeFloat(mShadowRadius);
        dest.writeFloat(mShadowDx);
        dest.writeFloat(mShadowDy);
        dest.writeInt(mShadowColor);
        dest.writeBoolean(mElegantTextHeight);
        dest.writeFloat(mLetterSpacing);
        dest.writeString(mFontFeatureSettings);
        dest.writeString(mFontVariationSettings);
    }
}
