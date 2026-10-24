// android.text.style.StyleSpan -- AOSP: apply a Typeface style (BOLD/ITALIC)
// to the covered range. Compose SpanStyle(fontWeight=Bold/fontStyle=Italic)
// interop + HtmlCompat <b>/<i>. Software approximation: fake-bold + skew for
// italic (matches our TextView bold/italic rendering). v1.0.423.
package android.text.style;
import android.text.TextPaint;
import android.graphics.Typeface;
// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
// Payload (mStyle) and type id 7 verified against frameworks/base/core/java/
// android/text/style/StyleSpan.java (android16-release), and matches
// TextUtils.java's own writeSpanPayload for STYLE_SPAN. Current AOSP (API 31+)
// ALSO writes a second int, mFontWeightAdjustment (StyleSpan.java L114-116);
// this class has no such field (font-weight adjustment is not modeled here),
// so only mStyle is written -- a self-consistent 1-field payload rather than
// a padded 2-field one, matching what TextUtils.java already verified.
public class StyleSpan extends MetricAffectingSpan implements android.text.ParcelableSpan {

    /** AOSP Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED (Integer.MAX_VALUE)
     *  -- "no adjustment requested". Duplicated as a private constant because
     *  android.content.res.Configuration in this tree has the
     *  `fontWeightAdjustment` FIELD but not this CONSTANT; when that lane adds
     *  it, this should reference it instead. */
    private static final int FONT_WEIGHT_ADJUSTMENT_UNDEFINED = Integer.MAX_VALUE;

    private final int mStyle;
    private final int mFontWeightAdjustment;

    public StyleSpan(int style) { this(style, FONT_WEIGHT_ADJUSTMENT_UNDEFINED); }

    /**
     * AOSP StyleSpan(int style, int fontWeightAdjustment) (StyleSpan.java:75-78)
     * -- the API-31 constructor. `fontWeightAdjustment` is ADDED to the
     * typeface's own weight (Typeface.getWeight()) and reflects the user's
     * "bold text" accessibility preference.
     *
     * ⚠ KNOWN PARTIAL, and deliberately NOT closed here: the adjustment is
     * stored, parcelled and reported by getFontWeightAdjustment(), but apply()
     * below does not act on it. AOSP's apply() (StyleSpan.java:147-190) does
     * considerably more than ours does even without the adjustment -- it
     * resolves a real Typeface via Typeface.create(old, want) and only
     * synthesizes the bits that resolve could not satisfy, where ours goes
     * straight to fake-bold/skew. Rewriting apply() to AOSP's version is a
     * RENDERING change affecting every bold/italic span in every app, and this
     * lane cannot run the Canvas fleet to measure it. See the lane report:
     * it is written up as a measured follow-up, with AOSP's exact body, rather
     * than landed blind.
     */
    public StyleSpan(int style, int fontWeightAdjustment) {
        mStyle = style;
        mFontWeightAdjustment = fontWeightAdjustment;
    }

    /** AOSP StyleSpan(Parcel src) (StyleSpan.java:85-88): TWO ints, style then
     *  font-weight adjustment. */
    public StyleSpan(android.os.Parcel src) {
        mStyle = src.readInt();
        mFontWeightAdjustment = src.readInt();
    }

    public int getStyle() { return mStyle; }

    /** AOSP StyleSpan.getFontWeightAdjustment() (StyleSpan.java:129-131).
     *  FONT_WEIGHT_ADJUSTMENT_UNDEFINED when the one-argument constructor was
     *  used. */
    public int getFontWeightAdjustment() { return mFontWeightAdjustment; }
    @Override public void updateDrawState(TextPaint tp) { apply(tp); }
    @Override public void updateMeasureState(TextPaint tp) { apply(tp); }
    private void apply(TextPaint p) {
        if ((mStyle & Typeface.BOLD) != 0)   p.setFakeBoldText(true);
        if ((mStyle & Typeface.ITALIC) != 0) p.setTextSkewX(-0.25f);
    }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.STYLE_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        // AOSP StyleSpan.writeToParcelInternal (StyleSpan.java:113-116) writes
        // BOTH ints. The file header used to explain that we wrote only mStyle
        // because there was no second field; there is now, so the payload
        // matches AOSP's. TextUtils.writeSpanPayload/readSpanPayload's
        // STYLE_SPAN case was widened to two ints in the same change -- the two
        // must move together or a round trip through
        // TextUtils.CHAR_SEQUENCE_CREATOR desynchronises the read cursor.
        dest.writeInt(mStyle);
        dest.writeInt(mFontWeightAdjustment);
    }
    public static final android.os.Parcelable.Creator<StyleSpan> CREATOR =
            new android.os.Parcelable.Creator<StyleSpan>() {
        @Override public StyleSpan createFromParcel(android.os.Parcel in) {
            return new StyleSpan(in);
        }
        @Override public StyleSpan[] newArray(int size) { return new StyleSpan[size]; }
    };
}
