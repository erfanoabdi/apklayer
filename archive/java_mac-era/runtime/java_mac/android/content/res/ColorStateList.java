// android.content.res.ColorStateList -- holds a state-keyed table of int
// colors that a View uses (e.g. text color across normal/pressed/focused
// /disabled states). AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/res/ColorStateList.java
//
// Previously every lookup returned the default color regardless of state,
// so a disabled/pressed/checked color was never honored (disabled text
// looked enabled, etc.). This version implements AOSP's real state-spec
// matching: each row's int[] state spec lists required attrs (positive =
// must be present in the view's stateSet, negative = ~attr = must be
// absent); getColorForState returns the first matching row's color. An
// empty spec ({}) matches any state (the default catch-all), exactly as
// AOSP's wildcard row works.
//
// createFromXml/createFromXmlInner now really inflate a <selector> color
// XML (see inflate() below) instead of returning a transparent one-row
// list. NOTE: Resources.getColorStateList(int) still collapses a color
// resource to ColorStateList.valueOf(one color) and does NOT route through
// here, so <selector>-authored color resources are still flat when reached
// by resource id -- that wiring lives in Resources.java.
package android.content.res;

public class ColorStateList implements android.os.Parcelable {
    // Parallel arrays: mStateSpecs[i] is the spec for color mColors[i].
    private final int[][] mStateSpecs;
    private final int[] mColors;
    private final int mDefaultColor;

    /** fwxml (docs/fwxml-platform-xml-typed-values_2026-09-26.md). Default ON;
     *  `MACAPK_CSL_THEMELESS_AOSP=0` restores the pre-change inflate (only
     *  android:color chased a theme reference; android:alpha / android:lStar
     *  were parsed as bare floats, so "@dimen/x" and "?attr/x" read as 1.0 /
     *  absent) and drops the unresolved-reference record below
     *  (canApplyTheme() false, obtainForTheme() returns this).
     *
     *  What it mirrors: AOSP reads each <item> through
     *  `Resources.obtainAttributes(r, theme, attrs, R.styleable.ColorStateListItem)`
     *  (android14-release ColorStateList.java :363-368), which resolves EVERY
     *  item value the same way -- a literal, a reference by id, or with a theme
     *  a theme attribute -- before getColor / getFloat read it. A theme
     *  attribute that does not resolve (every one, with no theme; one the
     *  theme lacks, with a theme) is extracted (`a.extractThemeAttrs()`, :365)
     *  and read as absent, so the colour is Color.MAGENTA, the alpha 1.0 and
     *  the lStar -1, and the list REMEMBERS it (mThemeAttrs, :405-423) so that
     *  applyTheme / obtainForTheme (:457-533) can finish the job once a theme
     *  that has it is known. MAGENTA is a placeholder on a device too; it is
     *  only ever seen when nothing applies such a theme. */
    static final boolean MACAPK_CSL_THEMELESS_AOSP =
        !"0".equals(System.getenv("MACAPK_CSL_THEMELESS_AOSP"));

    /** AOSP mThemeAttrs, by value expression instead of attr id (our Theme is
     *  name-keyed): per row, {color, alpha, lStar} theme references the
     *  inflate could not resolve (with no theme, or against a theme that lacks
     *  them), null entries for resolved values,
     *  a null row for a fully resolved row. Null when no row has any. */
    private String[][] mThemeAttrs;

    // ---- Parcelable (AOSP core/java/android/content/res/ColorStateList.java
    // :773). The wire format is AOSP's exactly: the spec COUNT, then each
    // int[] spec, then the parallel colour array. The default colour is NOT
    // written -- AOSP's CREATOR rebuilds it by calling the (specs, colors)
    // constructor, which re-derives it, and ours does the same.
    //
    // This matters beyond parity: a ColorStateList travels through a Bundle
    // whenever a widget saves instance state or an app passes a tint across an
    // Intent, and without `implements Parcelable` that fails a TYPE check
    // rather than throwing a diagnosable NoSuchMethodError.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        final int n = mStateSpecs.length;
        dest.writeInt(n);
        for (int i = 0; i < n; i++) {
            dest.writeIntArray(mStateSpecs[i]);
        }
        dest.writeIntArray(mColors);
    }

    public static final Creator<ColorStateList> CREATOR = new Creator<ColorStateList>() {
        @Override public ColorStateList[] newArray(int size) { return new ColorStateList[size]; }
        @Override public ColorStateList createFromParcel(android.os.Parcel source) {
            final int n = source.readInt();
            final int[][] stateSpecs = new int[n][];
            for (int i = 0; i < n; i++) {
                stateSpecs[i] = source.createIntArray();
            }
            final int[] colors = source.createIntArray();
            return new ColorStateList(stateSpecs, colors);
        }
    };

    public ColorStateList() {
        this.mStateSpecs = new int[0][];
        this.mColors = new int[0];
        this.mDefaultColor = 0;
    }

    /** Single-color list: matches any state. */
    public ColorStateList(int color) {
        this.mStateSpecs = new int[][]{ new int[0] };
        this.mColors = new int[]{ color };
        this.mDefaultColor = color;
    }

    public ColorStateList(int[][] states, int[] colors) {
        this.mStateSpecs = (states != null) ? states : new int[0][];
        this.mColors = (colors != null) ? colors : new int[0];
        // AOSP onColorsChanged, reached from this ctor:
        //     int defaultColor = DEFAULT_COLOR;
        //     final int N = states.length;
        //     if (N > 0) {
        //         defaultColor = colors[0];
        //         for (int i = N - 1; i > 0; i--) {
        //             if (states[i].length == 0) { defaultColor = colors[i]; break; }
        //         }
        //     }
        // Read it precisely: seed from `colors[0]`, then scan BACKWARD, and the
        // loop bound is `i > 0`, NOT `i >= 0` -- row 0 is only ever the seed.
        //
        // The old code here seeded from the LAST colour and scanned FORWARD for
        // the FIRST wildcard, which inverts TWO things: the no-wildcard default
        // (285 stateful-with-no-wildcard selectors exist across the corpus, and
        // every one of them resolved its default from the wrong end), and which of
        // several wildcards wins.
        //
        // Landed together with state_checked (CompoundButton.onCreateDrawableState)
        // and state_accelerated on purpose: with those in place the default only
        // surfaces when NO row matches a live state array, so a changed default is
        // a fallback rather than the answer. Landing this rule ALONE is what made
        // it measure flat twice while still being a latent fleet-wide change.
        //
        // min() guards a mismatched pair of arrays where AOSP would throw;
        // well-formed callers always pass parallel arrays.
        int def = DEFAULT_COLOR;
        final int n = Math.min(mStateSpecs.length, mColors.length);
        if (n > 0) {
            def = mColors[0];
            for (int i = n - 1; i > 0; i--) {
                if (mStateSpecs[i] != null && mStateSpecs[i].length == 0) {
                    def = mColors[i];
                    break;
                }
            }
        }
        this.mDefaultColor = def;
    }

    /** Internal ctor used by the XML inflater, which computes the default
     *  color with AOSP's inflate() rule rather than the public ctor's:
     *  AOSP seeds the default from the FIRST <item> and then lets any
     *  wildcard (state-less) row override it. */
    private ColorStateList(int[][] states, int[] colors, int defaultColor) {
        this.mStateSpecs = (states != null) ? states : new int[0][];
        this.mColors = (colors != null) ? colors : new int[0];
        this.mDefaultColor = defaultColor;
    }

    /** Static factory: a state-less list returning color for any state. */
    public static ColorStateList valueOf(int color) {
        return new ColorStateList(color);
    }

    public int getDefaultColor() { return mDefaultColor; }

    /** AOSP getColorForState: returns the color of the first row whose
     *  state spec is satisfied by stateSet, else the supplied default. */
    public int getColorForState(int[] stateSet, int defaultColor) {
        final int setLength = mStateSpecs.length;
        for (int i = 0; i < setLength && i < mColors.length; i++) {
            int[] spec = mStateSpecs[i];
            if (stateSetMatches(spec, stateSet)) {
                return mColors[i];
            }
        }
        return defaultColor;
    }

    public boolean isStateful() {
        // AOSP (android11-release ColorStateList.java:526-528):
        //     return mStateSpecs.length >= 1 && mStateSpecs[0].length > 0;
        // Only the FIRST spec is consulted. A selector whose index-0 row is a
        // wildcard reports NOT stateful even when later rows carry state specs
        // (getColorForState can never reach past a leading wildcard anyway, so
        // Android treats such a list as effectively fixed). Our old any-spec
        // scan answered 1 for exactly that shape; the fwprobe golden
        // (CSL.wild0.stateful ref=0) is the discriminating case.
        return mStateSpecs.length >= 1
            && mStateSpecs[0] != null && mStateSpecs[0].length > 0;
    }

    public boolean isOpaque() {
        for (int c : mColors) {
            if (((c >>> 24) & 0xff) != 0xff) return false;
        }
        return mColors.length > 0 ? true : ((mDefaultColor >>> 24) & 0xff) == 0xff;
    }

    /** Returns a derived list with every color's alpha replaced. */
    public ColorStateList withAlpha(int alpha) {
        int[] colors = new int[mColors.length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = (mColors[i] & 0x00FFFFFF) | ((alpha & 0xff) << 24);
        }
        if (colors.length == 0) {
            int c = (mDefaultColor & 0x00FFFFFF) | ((alpha & 0xff) << 24);
            return new ColorStateList(c);
        }
        return new ColorStateList(deepCopy(mStateSpecs), colors);
    }

    /** AOSP API-31 ColorStateList.withLStar: a derived list with every colour's
     *  LIGHTNESS replaced by lStar, keeping each colour's CAM16 hue and chroma.
     *
     *  This was an honest no-op ("we do not have full Lab conversion") until
     *  2026-08-20, and it had zero callers, so lStar was unimplemented in both
     *  senses. It is not a "minor visual refinement": it is the mechanism
     *  Material3 uses to build its entire tonal palette on API 31-33, and
     *  ignoring it collapses every tone in a palette onto its shared base. */
    public ColorStateList withLStar(float lStar) {
        if (mColors.length == 0) {
            return new ColorStateList(com.android.internal.graphics.cam.CamColor
                    .modulateColor(mDefaultColor, 1.0f, lStar));
        }
        int[] colors = new int[mColors.length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = com.android.internal.graphics.cam.CamColor
                    .modulateColor(mColors[i], 1.0f, lStar);
        }
        return new ColorStateList(deepCopy(mStateSpecs), colors);
    }

    /** AOSP createFromXml: seek the parser to the first START_TAG, then
     *  inflate it as a <selector>. This used to `return new ColorStateList(0)`
     *  -- a single fully-TRANSPARENT row -- for any input, so every caller that
     *  parses a color selector XML itself (rather than going through
     *  Resources) got an invisible color and no state response at all. */
    public static ColorStateList createFromXml(Resources r, org.xmlpull.v1.XmlPullParser parser)
            throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {
        return createFromXml(r, parser, null);
    }

    public static ColorStateList createFromXml(Resources r, org.xmlpull.v1.XmlPullParser parser,
                                               Resources.Theme theme)
            throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {
        final android.util.AttributeSet attrs = android.util.Xml.asAttributeSet(parser);
        int type;
        while ((type = parser.next()) != org.xmlpull.v1.XmlPullParser.START_TAG
                && type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            // Seek parser to start tag.
        }
        if (type != org.xmlpull.v1.XmlPullParser.START_TAG) {
            throw new org.xmlpull.v1.XmlPullParserException("No start tag found");
        }
        return createFromXmlInner(r, parser, attrs, theme);
    }

    /** AOSP createFromXmlInner: the parser is already positioned on the tag,
     *  which must be <selector>. */
    static ColorStateList createFromXmlInner(Resources r, org.xmlpull.v1.XmlPullParser parser,
                                             android.util.AttributeSet attrs,
                                             Resources.Theme theme)
            throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {
        final String name = parser.getName();
        if (!"selector".equals(name)) {
            throw new org.xmlpull.v1.XmlPullParserException(
                parser.getPositionDescription() + ": invalid color state list tag " + name);
        }
        return inflate(r, parser, theme);
    }

    /** AOSP ColorStateList.inflate(): walk the <selector>'s direct <item>
     *  children, turning each one's android:state_* attributes into a state
     *  spec (positive = must be present, negative = ~attr = must be absent)
     *  and its android:color into that row's color. The default color is
     *  seeded from the FIRST row and overridden by any state-less (wildcard)
     *  row, matching AOSP's `if (listSize == 0 || stateSpec.length == 0)`. */
    private static ColorStateList inflate(Resources r, org.xmlpull.v1.XmlPullParser parser,
                                          Resources.Theme theme)
            throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {
        // v1.0.890 probe (MACAPK_TRACE_CSL_INFLATE=1). THE load-bearing question
        // for the whole un-flattening effort: for an app's res/color/*.xml, does
        // OUR inflater run, or the app's own bundled
        // androidx.core.content.res.ColorStateListInflaterCompat?
        // It decides a large piece of work. Ours keys state specs on the
        // attribute's LOCAL NAME (see the stateAttrId call below) and therefore
        // builds CORRECT specs today. Androidx keys them on
        // AttributeSet.getAttributeNameResource, which returns 0 in our tree, so
        // ITS specs come out all-zero and every row matches everything. If ours
        // wins for the real fleet apps, the un-flattening work is NOT gated on
        // getAttributeNameResource at all; if androidx wins, it is. Static
        // analysis could not settle it -- two lanes reasoned to opposite
        // conclusions from the same code -- so this is the one-run answer.
        if (System.getenv("MACAPK_TRACE_CSL_INFLATE") != null) {
            try {
                com.macapk.loader.NativeLog.i("Mac.apk.CSL",
                    "OUR inflate() running for " + parser.getPositionDescription());
            } catch (Throwable ignored) {}
        }
        final int innerDepth = parser.getDepth() + 1;
        int depth;
        int type;

        int defaultColor = DEFAULT_COLOR;
        java.util.ArrayList<int[]> specs = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> colors = new java.util.ArrayList<>();
        java.util.ArrayList<String[]> themeRows = new java.util.ArrayList<>();
        boolean anyThemeAttrs = false;

        while ((type = parser.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                    || type != org.xmlpull.v1.XmlPullParser.END_TAG)) {
            if (type != org.xmlpull.v1.XmlPullParser.START_TAG || depth > innerDepth
                    || !"item".equals(parser.getName())) {
                continue;
            }

            // AOSP reads color/alpha/lStar through a styleable TypedArray and
            // treats every OTHER attribute as a state specifier, keyed on the
            // attribute's resource id. Our generic pull-parser adapter reports
            // no attribute resource ids (Xml.ParserAttributeSet returns 0), so
            // we key on the attribute's local name instead and map it to the
            // same framework attr id the View side emits in getDrawableState.
            int baseColor = android.graphics.Color.MAGENTA;   // AOSP's absent-color default
            float alphaMod = 1.0f;
            float lStar = -1.0f;
            // The theme references this inflate cannot resolve (AOSP
            // extractThemeAttrs, :365); recorded per row below.
            String thColor = null, thAlpha = null, thLStar = null;

            final int numAttrs = parser.getAttributeCount();
            int[] stateSpec = new int[numAttrs < 0 ? 0 : numAttrs];
            int j = 0;
            for (int i = 0; i < numAttrs; i++) {
                final String local = localName(parser.getAttributeName(i));
                final String value = parser.getAttributeValue(i);
                if (local == null) continue;
                // A theme reference that did not resolve is recorded WHETHER OR
                // NOT a theme was passed: AOSP runs `a.extractThemeAttrs()`
                // (:365) on every item's TypedArray, and with a theme that
                // lacks the attribute obtainStyledAttributes leaves the entry
                // TYPE_ATTRIBUTE exactly as a theme-less obtainAttributes does,
                // so the row takes the absent default AND stays recorded for a
                // later applyTheme / obtainForTheme (:405-407, no theme == null
                // condition). A reference the theme DOES resolve reads as its
                // value and is not recorded. With no theme nothing resolves, so
                // that case is unchanged.
                if ("color".equals(local)) {
                    if (MACAPK_CSL_THEMELESS_AOSP && isThemeRef(value)) {
                        int c = resolveColor(r, value, MACAPK_UNRESOLVED, theme);
                        if (c == MACAPK_UNRESOLVED) thColor = value.trim();   // MAGENTA stands
                        else baseColor = c;
                    } else {
                        baseColor = resolveColor(r, value, baseColor, theme);
                    }
                    continue;
                }
                if ("alpha".equals(local)) {
                    if (!MACAPK_CSL_THEMELESS_AOSP) {
                        alphaMod = parseFloat(value, 1.0f);
                    } else if (isThemeRef(value)) {
                        float a = resolveFloat(r, value, Float.NaN, theme);
                        if (Float.isNaN(a)) thAlpha = value.trim();   // absent: 1.0
                        else alphaMod = a;
                    } else {
                        alphaMod = resolveFloat(r, value, 1.0f, theme);
                    }
                    continue;
                }
                if ("lStar".equals(local)) {
                    if (!MACAPK_CSL_THEMELESS_AOSP) {
                        lStar = parseFloat(value, -1.0f);
                    } else if (isThemeRef(value)) {
                        float l = resolveFloat(r, value, Float.NaN, theme);
                        if (Float.isNaN(l)) thLStar = value.trim();   // absent: -1
                        else lStar = l;
                    } else {
                        lStar = resolveFloat(r, value, -1.0f, theme);
                    }
                    continue;
                }
                final int stateAttr = stateAttrId(r, local);
                if (stateAttr == 0) continue;   // not a state attribute
                stateSpec[j++] = parseBoolean(value) ? stateAttr : -stateAttr;
            }
            stateSpec = android.util.StateSet.trimStateSet(stateSpec, j);

            final int color = modulateColor(baseColor, alphaMod, lStar);
            if (colors.isEmpty() || stateSpec.length == 0) {
                defaultColor = color;
            }
            specs.add(stateSpec);
            colors.add(color);
            themeRows.add((thColor != null || thAlpha != null || thLStar != null)
                ? new String[] { thColor, thAlpha, thLStar } : null);
            if (thColor != null || thAlpha != null || thLStar != null) anyThemeAttrs = true;
        }

        int[][] specArray = specs.toArray(new int[specs.size()][]);
        int[] colorArray = new int[colors.size()];
        for (int i = 0; i < colorArray.length; i++) colorArray[i] = colors.get(i);
        ColorStateList out = new ColorStateList(specArray, colorArray, defaultColor);
        if (anyThemeAttrs) out.mThemeAttrs = themeRows.toArray(new String[themeRows.size()][]);
        return out;
    }

    private static boolean isThemeRef(String value) {
        return value != null && value.trim().startsWith("?");
    }

    /** An item's float value (android:alpha / android:lStar) as
     *  obtainAttributes + TypedArray.getFloat would read it: a literal; a
     *  reference by id ("@dimen/material_emphasis_disabled", a
     *  `<item type="dimen" format="float">`, which is how Material writes every
     *  disabled / emphasis alpha); or, with a theme, a theme attribute
     *  ("?android:disabledAlpha", "?attr/..."). AOSP getFloat takes TYPE_FLOAT
     *  as its float and an integer type as its int (TypedArray.java,
     *  android14-release). Anything unreadable is `def`, the value AOSP's
     *  getFloat default gives an absent attribute. */
    private static float resolveFloat(Resources r, String value, float def, Resources.Theme theme) {
        if (value == null) return def;
        String v = value.trim();
        for (int hop = 0; hop < 20 && !v.isEmpty(); hop++) {
            char c0 = v.charAt(0);
            if (c0 == '?') {
                if (theme == null) return def;
                String raw = Resources.macapkResolveThemeAttrRaw(v, theme);
                if (raw == null) return def;
                v = raw.trim();
                continue;
            }
            if (c0 == '@') {
                if (r == null) return def;
                int id = Resources.macapkResRefToId(v);
                if (id == 0) return def;
                android.util.TypedValue tv = new android.util.TypedValue();
                try { r.getValue(id, tv, true); } catch (Throwable t) { return def; }
                if (tv.type == android.util.TypedValue.TYPE_FLOAT) return tv.getFloat();
                if (tv.type >= android.util.TypedValue.TYPE_FIRST_INT
                        && tv.type <= android.util.TypedValue.TYPE_LAST_INT) return tv.data;
                if (tv.type == android.util.TypedValue.TYPE_STRING && tv.string != null) {
                    return parseFloat(tv.string.toString(), def);
                }
                return def;
            }
            return parseFloat(v, def);
        }
        return def;
    }

    /** AOSP canApplyTheme (android14-release :443-445): true while the
     *  inflate (or a later applyTheme) left theme references unresolved. */
    public boolean canApplyTheme() {
        return mThemeAttrs != null;
    }

    /** AOSP obtainForTheme (:526-533): this list when there is nothing to
     *  apply, else a copy with applyTheme(t) run on it. */
    public ColorStateList obtainForTheme(Resources.Theme t) {
        if (t == null || !canApplyTheme()) return this;
        return applyThemeCopy(t);
    }

    /** AOSP applyTheme (:457-512) on a copy (our colour array is final, so the
     *  copy IS the result). Per unresolved row: the colour defaults to the
     *  current (MAGENTA placeholder) one, and the alpha default depends on
     *  whether the colour is still to come --
     *      if (themeAttrsList[i][R.styleable.ColorStateListItem_color] != 0) {
     *          defaultAlphaMod = Color.alpha(mColors[i]) / 255.0f;
     *      } else {
     *          defaultAlphaMod = 1.0f;
     *      }
     *  -- because a still-unresolved colour's alpha channel already carries
     *  the literal alpha modulation. A reference that still does not resolve
     *  stays recorded, as AOSP's re-extracted themeAttrsList does. */
    private ColorStateList applyThemeCopy(Resources.Theme t) {
        final Resources r = t.getResources();
        final int n = mColors.length;
        final int[] colors = mColors.clone();
        final String[][] left = new String[n][];
        boolean unresolved = false;
        for (int i = 0; i < n && i < mThemeAttrs.length; i++) {
            final String[] ta = mThemeAttrs[i];
            if (ta == null) continue;
            final float defaultAlphaMod = ta[0] != null
                ? android.graphics.Color.alpha(colors[i]) / 255.0f : 1.0f;
            String[] still = new String[3];
            int baseColor = colors[i];
            if (ta[0] != null) {
                int c = (r == null) ? MACAPK_UNRESOLVED
                                    : r.macapkResolveColorString(ta[0], MACAPK_UNRESOLVED, t);
                if (c == MACAPK_UNRESOLVED) still[0] = ta[0]; else baseColor = c;
            }
            float alphaMod = defaultAlphaMod;
            if (ta[1] != null) {
                float a = resolveFloat(r, ta[1], Float.NaN, t);
                if (Float.isNaN(a)) still[1] = ta[1]; else alphaMod = a;
            }
            float lStar = -1.0f;
            if (ta[2] != null) {
                float l = resolveFloat(r, ta[2], Float.NaN, t);
                if (Float.isNaN(l)) still[2] = ta[2]; else lStar = l;
            }
            colors[i] = modulateColor(baseColor, alphaMod, lStar);
            if (still[0] != null || still[1] != null || still[2] != null) {
                left[i] = still;
                unresolved = true;
            }
        }
        // AOSP applyTheme ends in onColorsChanged(), whose default rule is the
        // public constructor's (seed colors[0], last wildcard wins).
        ColorStateList out = new ColorStateList(deepCopy(mStateSpecs), colors);
        if (unresolved) out.mThemeAttrs = left;
        return out;
    }

    /** "Did not resolve" for applyThemeCopy's colour read; a colour no
     *  resource or theme produces (the same idea as Resources.MACAPK_NO_COLOR). */
    private static final int MACAPK_UNRESOLVED = 0x00BADC0D;

    /** AOSP DEFAULT_COLOR: the sentinel an empty/failed selector falls back
     *  to, so a broken color list is visibly wrong rather than invisible. */
    private static final int DEFAULT_COLOR = android.graphics.Color.RED;

    /** Strip a namespace prefix ("android:state_pressed" -> "state_pressed").
     *  XmlPullParser reports the local name when namespace processing is on
     *  and the qualified name when it is off; handle both. */
    private static String localName(String attrName) {
        if (attrName == null) return null;
        int colon = attrName.indexOf(':');
        return colon >= 0 ? attrName.substring(colon + 1) : attrName;
    }

    /** The public framework android.R.attr.state_* ids, which are what
     *  View.getDrawableState() puts in the state set. Values verified against
     *  AOSP core/res/res/values/public-final.xml. An app-defined state attr
     *  (mergeDrawableStates with the app's own R.attr) is resolved through the
     *  app's resource index instead, so this stays app-agnostic. */
    // Package-private (not private): Resources.macapkParseStateSet parses the
    // same android:state_* attrs for <selector> DRAWABLES and must give the
    // same answers as the ColorStateList path.
    /** v1.0.889: the FRAMEWORK-namespace attr id for a local name, for callers
     *  outside this package (DomXmlResourceParser.getAttributeNameResource).
     *  Pass r == null into stateAttrId deliberately: an `android:`-namespaced
     *  attribute must come from the frozen table, never from the app's own attr
     *  index, or an app that declares `state_enabled` in res-auto would shadow
     *  the framework id.
     *  The three non-state ids are the ones androidx's ColorStateListInflaterCompat
     *  guards on before treating an attribute as a state specifier; they are
     *  cross-checked against the constants compiled into its own bytecode
     *  (`if_icmpeq` vs 16843173 / 16843551) and AOSP public-final.xml. */
    public static int macapkFrameworkAttrId(String local) {
        if (local == null) return 0;
        switch (local) {
            case "color": return 0x010101a5;  // android.R.attr.color = 16843173
            case "alpha": return 0x0101031f;  // android.R.attr.alpha = 16843551
            case "lStar": return 0x01010647;  // android.R.attr.lStar = 16844359
        }
        return stateAttrId(null, local);
    }

    static int stateAttrId(Resources r, String local) {
        switch (local) {
            case "state_focused":         return 0x0101009c;
            case "state_window_focused":  return 0x0101009d;
            case "state_enabled":         return 0x0101009e;
            case "state_checkable":       return 0x0101009f;
            case "state_checked":         return 0x010100a0;
            case "state_selected":        return 0x010100a1;
            case "state_active":          return 0x010100a2;
            case "state_single":          return 0x010100a3;
            case "state_first":           return 0x010100a4;
            case "state_middle":          return 0x010100a5;
            case "state_last":            return 0x010100a6;
            case "state_pressed":         return 0x010100a7;
            case "state_expanded":        return 0x010100a8;
            case "state_empty":           return 0x010100a9;
            case "state_above_anchor":    return 0x010100aa;
            case "state_long_pressable":  return 0x0101023c;
            case "state_activated":       return 0x010102fe;
            case "state_accelerated":     return 0x0101031b;
            case "state_multiline":       return 0x0101034d;
            case "state_hovered":         return 0x01010367;
            case "state_drag_can_accept": return 0x01010368;
            case "state_drag_hovered":    return 0x01010369;
        }
        if (r != null && local.startsWith("state_")) {
            try { return r.getIdentifier(local, "attr", null); }
            catch (Throwable ignored) {}
        }
        return 0;
    }

    /** Resolve an android:color attribute value: a literal (#AARRGGBB), a
     *  theme attr (?attr/x), or a resource reference (@color/x, or a raw
     *  numeric id when the value arrives already compiled).
     *
     *  docs/attr-theme-threading_2026-07-31.md: `theme` was already accepted
     *  end-to-end (createFromXml -> createFromXmlInner -> inflate all take
     *  one) but silently dropped the moment inflate() called this method
     *  without it -- so a <selector> color XML's `?attr/x` item always
     *  resolved against the static application theme regardless of which
     *  Theme the caller passed to createFromXml. Threading it one more hop
     *  makes the existing signature do what it already claimed to do. */
    private static int resolveColor(Resources r, String value, int def, Resources.Theme theme) {
        if (value == null) return def;
        value = value.trim();
        if (value.isEmpty()) return def;
        if (value.charAt(0) == '#') {
            try { return android.graphics.Color.macapkParseResourceColor(value); }
            catch (Throwable t) { return def; }
        }
        // A `?attr` item colour with NO theme to chase it stays at the
        // caller's default (Color.MAGENTA). AOSP's inflate reads the row via
        // obtainAttributes(r, theme, ...) and
        //     a.getColor(R.styleable.ColorStateListItem_color, Color.MAGENTA)
        // (android11-release ColorStateList.java, inflate) -- with a null
        // theme the attribute reference cannot resolve, the entry reads as
        // null, and the MAGENTA default stands; the row is still appended
        // with its state spec (the unresolved attr is only re-modulated later
        // by applyTheme). The emulator golden shows exactly that: every row
        // of the theme-attr selector loaded theme-less answers 0xffff00ff.
        // Falling through to macapkResolveColorString here would instead
        // consult the STATIC application theme -- an answer real Android
        // never gives on this path.
        if (value.charAt(0) == '?' && theme == null) return def;
        // "@2130903040": a resource id already resolved to its numeric form.
        if (value.charAt(0) == '@' || isAllDigits(value)) {
            String body = value.charAt(0) == '@' ? value.substring(1) : value;
            if (isAllDigits(body)) {
                try {
                    return Resources.macapkColorById(Integer.parseInt(body), def);
                } catch (Throwable ignored) {}
            }
        }
        if (r == null) return def;
        // Handles "@[pkg:]color/name" and "?[pkg:]attr/name" (same package, so
        // we can reuse Resources' own resolver rather than re-deriving it).
        return r.macapkResolveColorString(value, def, theme);
    }

    private static boolean isAllDigits(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static float parseFloat(String value, float def) {
        if (value == null) return def;
        try { return Float.parseFloat(value.trim()); }
        catch (Throwable t) { return def; }
    }

    /** AOSP uses AttributeSet.getAttributeBooleanValue(i, false), so anything
     *  that is not literally "true" reads as false (a NEGATED state). */
    private static boolean parseBoolean(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    /** AOSP modulateColor: scale the base color's alpha by android:alpha.
     *  The android:lStar (API 31) branch additionally re-maps the color's
     *  CAM16 lightness; we have no CAM16 conversion in the tree, so an
     *  lStar item keeps its own RGB and only the alpha is applied. That is
     *  visible here rather than silently mis-colored. */
    /** AOSP ColorStateList.modulateColor. Delegates to the CAM16 implementation
     *  so the XML inflate path, withLStar() and ResourceIndex's file-selector
     *  reader all apply lStar the SAME way -- three places parsed it before
     *  2026-08-20 and all three dropped it. This body in particular computed
     *  `validLStar` and then never used it, applying only the alpha. */
    private static int modulateColor(int baseColor, float alphaMod, float lStar) {
        return com.android.internal.graphics.cam.CamColor
                .modulateColor(baseColor, alphaMod, lStar);
    }

    @Override public String toString() {
        return "ColorStateList{default=#" + Integer.toHexString(mDefaultColor)
            + " rows=" + mColors.length + "}";
    }

    // ---- helpers ----

    /** AOSP StateSet.stateSetMatches: stateSpec is satisfied by stateSet when
     *  every positive attr in the spec is present in stateSet and every
     *  negative one is absent. The negative encoding is ARITHMETIC negation
     *  (-attr), which is what AOSP's ColorStateList.inflate writes
     *  (`stateSpec[j++] = ... ? stateResId : -stateResId`), what
     *  androidx's ColorStateListInflaterCompat writes, and what this tree's
     *  own selector parsers emit (Resources.macapkParseStateSet). This used
     *  to un-negate with `~specState`, which is off by one (~x == -x-1), so a
     *  `state_enabled="false"` row was matched against state_window_focused
     *  (0x0101009d instead of 0x0101009e) and every negated row in every
     *  app-supplied ColorStateList resolved against the wrong attribute.
     *
     *  A 0 entry terminates the spec (aapt pads the array); a 0 in the state
     *  set likewise terminates it, and AOSP treats "ran off the end" as
     *  "state absent". */
    private static boolean stateSetMatches(int[] stateSpec, int[] stateSet) {
        if (stateSet == null) {
            return stateSpec == null || stateSpec.length == 0;
        }
        final int stateSpecSize = (stateSpec == null) ? 0 : stateSpec.length;
        final int stateSetSize = stateSet.length;
        for (int i = 0; i < stateSpecSize; i++) {
            int stateSpecState = stateSpec[i];
            if (stateSpecState == 0) {
                // Reached the padding terminator: everything after is unset.
                return true;
            }
            final boolean mustMatch;
            if (stateSpecState > 0) {
                mustMatch = true;
            } else {
                mustMatch = false;
                stateSpecState = -stateSpecState;
            }
            boolean found = false;
            for (int j = 0; j < stateSetSize; j++) {
                final int s = stateSet[j];
                if (s == 0) {
                    // End of the state set: a required state is missing, a
                    // forbidden one is confirmed absent.
                    if (mustMatch) return false;
                    break;
                }
                if (s == stateSpecState) { found = true; break; }
            }
            if (mustMatch && !found) return false;
            if (!mustMatch && found) return false;
        }
        return true;
    }

    private static int[][] deepCopy(int[][] src) {
        int[][] out = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            out[i] = (src[i] != null) ? src[i].clone() : null;
        }
        return out;
    }

    public int getChangingConfigurations() { return 0; }
}

