// android.text.TextPaint -- subclass of Paint that adds text-related
// fields (link color, draw-state flags, baseline shift, density).
// Apps construct one to measure / draw text in custom views and
// preference widgets. The androidx.preference cascade in HCR 1.68
// hits NoClassDefFoundError on this class during inflate; without
// it, the class loader's failure propagates as a StartupException
// during InitializationProvider.
//
// Mirrors AOSP frameworks/base/core/java/android/text/TextPaint.java:
// public fields baselineShift / linkColor / drawableState / density /
// underlineColor / underlineThickness. The set(Paint) override copies
// font + color state from a Paint to a TextPaint -- semantics matter
// for text-layout code, but the inflater path only touches the
// constructor.
package android.text;

import android.graphics.Paint;

public class TextPaint extends Paint {
    /** Subscript / superscript baseline pixel shift. */
    public int   baselineShift = 0;
    /** Background color drawn behind the text run; 0 = none. AOSP
     *  public field, written by BackgroundColorSpan.updateDrawState. */
    public int   bgColor = 0;
    /** Color used for clickable spans (e.g. links). */
    public int   linkColor = 0xff_3478f6;
    /** Per-pixel-density scale. Apps multiply text sizes by this. */
    public float density = 1.0f;
    /** android.R.style drawable-state vector. */
    public int[] drawableState;
    /** Underline color override; 0 = use text color. */
    public int   underlineColor = 0;
    /** Underline thickness in pixels; 0 = engine default. */
    public float underlineThickness = 0f;

    public TextPaint() { super(); }
    public TextPaint(int flags) { super(flags); }
    public TextPaint(Paint p) { super(p); }

    /** AOSP set(TextPaint other) copies all parent-Paint state plus
     *  the text-specific fields. */
    public void set(TextPaint tp) {
        super.set(tp);
        if (tp == null) return;
        // AOSP TextPaint.set copies bgColor FIRST; ours omitted it, so a
        // BackgroundColorSpan's bgColor survived a set() that was supposed to
        // reset the paint. That matters now that the draw path snapshots and
        // restores the WHOLE paint rather than a hand-listed set of fields.
        this.bgColor            = tp.bgColor;
        this.baselineShift      = tp.baselineShift;
        this.linkColor          = tp.linkColor;
        this.density            = tp.density;
        this.drawableState      = tp.drawableState;
        this.underlineColor     = tp.underlineColor;
        this.underlineThickness = tp.underlineThickness;
    }

    /** AOSP TextPaint.getFontVariationSettings (API 26): the current fvar axis
     *  settings string, or null when none were set. Mac.apk does not vary font
     *  axes, so this reports null (no override). */
    public String getFontVariationSettings() { return null; }


    // getTextRunCursor is DELIBERATELY NOT overridden here. AOSP's TextPaint
    // does not declare it either: it is Paint's, and Paint implements the real
    // UAX#29 grapheme-cluster walk plus AOSP's argument validation. This class
    // used to shadow it with a one-code-unit step, and because the identical
    // descriptor made the override win virtual dispatch, every caller holding a
    // TextPaint (which is what TextView, StaticLayout and Compose all hold) got
    // the naive version -- splitting surrogate pairs and combining sequences,
    // and silently accepting the out-of-range arguments AOSP rejects.

    private String mFontVariationSettings;
    public boolean setFontVariationSettings(String fontVariationSettings) {
        mFontVariationSettings = fontVariationSettings; return true;
    }
}


