// android.graphics.Typeface -- the font-family handle apps pass to
// Paint.setTypeface. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/Typeface.java
//
// Previously every factory returned the shared DEFAULT instance, so a
// custom branded font loaded via createFromAsset / createFromFile was
// silently dropped and all text fell back to Roboto. This version makes
// each Typeface carry an optional real AWT base Font (loaded from the
// asset/file bytes via MacApkFont) plus its style/weight metadata. The
// Paint text path (Paint.resolveFontMetrics / Canvas.deriveFont) reads
// macapkBaseFont()/getStyle() so custom fonts both MEASURE and DRAW with
// the actual glyphs, matching Android.
//
// The named statics (DEFAULT / SANS_SERIF / SERIF / MONOSPACE) keep a
// null base font so the existing Roboto path is used for framework text;
// only app-supplied fonts get a non-null base.
package android.graphics;

import java.awt.Font;

public class Typeface {
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    /** AOSP Typeface.STYLE_MASK (Typeface.java:162). */
    private static final int STYLE_MASK = 0x03;

    // A Typeface is (a font collection, a base weight, an API style, a
    // resolved minikin style). We carry the same four:
    //
    //   baseFont    -- an APP-SUPPLIED font (createFromAsset/File/Builder).
    //                  Mutually exclusive with familyName in practice.
    //   familyName  -- a CONCRETE system family (a fonts.xml <family name>,
    //                  never an alias -- aliases are resolved at construction,
    //                  which is what turns their weight into a base weight).
    //   baseWeight  -- hwui Typeface::fBaseWeight.
    //   style       -- hwui Typeface::fAPIStyle, what getStyle() returns.
    //   weight      -- hwui Typeface::fStyle.weight(), what getWeight()
    //                  returns. This is the RESOLVED weight, not the base one:
    //                  nativeGetWeight reads fStyle, so a BOLD derivative of a
    //                  500 family answers 800, not 500.
    private final Font baseFont;   // null => a system family (or the Roboto path)
    private final String familyName;
    private final int baseWeight;
    private final int style;       // NORMAL/BOLD/ITALIC/BOLD_ITALIC
    private final int weight;      // 1..1000 (FontStyle weight)
    // The (weight, slant) the APP-SUPPLIED face declares in its own OS/2 table.
    // Meaningless when baseFont is null. Carried because minikin's fakery
    // question for a one-face family is "what does the face actually provide",
    // which is a property of the FILE, not of the API style the caller asked
    // for; see MacApkFont.select.
    private final int appFaceWeight;
    private final boolean appFaceItalic;

    // NOTE (cont.329): the AOSP `native_instance` field read-site hypothesis was REFUTED --
    // a 0x41424344 sentinel in that field did NOT change pairip's pass-16645 trajectory
    // (cursor2 stayed 0x418ff), so pairip does not read Typeface.DEFAULT.native_instance.
    // The field was removed to avoid a regression (a sentinel pointer other apps might deref).
    // The pass-16645 read-site is a libminikin data-segment offset (zygote-preloaded
    // collection), not a JNI field; see docs/pairip-523-crack-state cont.329.

    public Typeface() { this(null, null, 400, NORMAL, 400); }
    private Typeface(Font base, String familyName, int baseWeight, int style, int weight) {
        this(base, familyName, baseWeight, style, weight, 400, false);
    }
    private Typeface(Font base, String familyName, int baseWeight, int style, int weight,
                     int appFaceWeight, boolean appFaceItalic) {
        this.baseFont = base;
        this.familyName = familyName;
        this.baseWeight = baseWeight;
        this.style = style & STYLE_MASK;
        this.weight = weight;
        this.appFaceWeight = appFaceWeight;
        this.appFaceItalic = appFaceItalic;
    }

    /** The weight/slant the app-supplied face DECLARES (400/upright when there
     *  is no app face). Read by MacApkFont.select to answer minikin's fakery
     *  question honestly instead of from the requested style bits. */
    public int macapkAppFaceWeight()      { return appFaceWeight; }
    public boolean macapkAppFaceItalic()  { return appFaceItalic; }

    /** hwui Typeface.cpp:54-60 (android11-release):
     *
     *      static minikin::FontStyle computeRelativeStyle(int baseWeight,
     *                                                     Typeface::Style relativeStyle) {
     *          int weight = baseWeight;
     *          if ((relativeStyle & Typeface::kBold) != 0) { weight += 300; }
     *          bool italic = (relativeStyle & Typeface::kItalic) != 0;
     *          return computeMinikinStyle(weight, italic);
     *      }
     *
     *  The +300 is the whole reason a BOLD request against sans-serif-medium
     *  resolves to Roboto-BLACK: 500 + 300 = 800, and fonts.xml lists 900
     *  before 700 so the tie at |9-8| == |7-8| goes to Black. */
    private static int computeRelativeWeight(int baseWeight, int relativeStyle) {
        int w = baseWeight;
        if ((relativeStyle & BOLD) != 0) w += 300;
        return clampWeight(w);   // hwui computeMinikinStyle clamps to [1,1000]
    }

    /** hwui Typeface.cpp:39-45: BOLD iff weight >= SkFontStyle::kSemiBold_Weight
     *  (600). Note this is NOT applied to the alias path -- sans-serif-black
     *  keeps fAPIStyle NORMAL while answering getWeight() == 900. */
    private static int computeAPIStyle(int weight, boolean italic) {
        return ((weight >= 600) ? BOLD : NORMAL) | (italic ? ITALIC : NORMAL);
    }

    // AOSP's sSystemFontMap, populated lazily rather than in one static sweep:
    // an entry costs nothing until a name is asked for, and the face behind it
    // costs nothing until that face wins a match.
    // ⚠ Declared BEFORE the named statics on purpose -- static initializers run
    // in textual order, and DEFAULT's construction reaches this map.
    private static final java.util.concurrent.ConcurrentHashMap<String, Typeface>
        sSystemFontMap = new java.util.concurrent.ConcurrentHashMap<>();

    /** One entry of AOSP's sSystemFontMap. A plain <family> resolves to base
     *  weight 400 / style NORMAL (createFromFamilies with RESOLVE_BY_FONT_TABLE
     *  reads the default-style closest match, which is the 400 upright face in
     *  every named family on the image); a weight-bearing <alias> instead gets
     *  its weight as the BASE weight, with the target's API style inherited
     *  (nativeCreateWeightAlias -> Typeface::createWithDifferentBaseWeight,
     *  hwui Typeface.cpp:112-123).
     *
     *  Returns null when the table is gated off or the name is unknown -- both
     *  of which mean "no system family", not "error". */
    private static Typeface systemFamily(String name) {
        String fam = MacApkFontFamilies.familyOf(name);
        if (fam == null) return null;
        int bw = MacApkFontFamilies.baseWeightOf(name);
        Typeface tf = new Typeface(null, fam, bw, NORMAL, clampWeight(bw));
        Typeface prev = sSystemFontMap.putIfAbsent(name, tf);
        return prev != null ? prev : tf;
    }

    /** The legacy family-less typeface: what every Typeface was before the
     *  fonts.xml table existed, and what MACAPK_FONT_FAMILY=0 restores. Paint
     *  and Canvas fall back to the single bundled Roboto-Regular for it. */
    private static Typeface legacy(int style, int weight) {
        return new Typeface(null, null, weight, style, weight);
    }

    // Named system families. Backed by the real fonts.xml table now, so
    // DEFAULT_BOLD is Roboto-Bold rather than an algorithmically emboldened
    // Roboto-Regular. AOSP builds these the same way in its static block
    // (Typeface.java:1138-1152): DEFAULT = create((String) null, 0), which
    // falls through getSystemDefaultTypeface(null) to the sans-serif entry.
    private static Typeface familyOrLegacy(String name, int style, int weight) {
        Typeface tf = systemFamily(name);
        return tf != null ? tf : legacy(style, weight);
    }
    public static final Typeface DEFAULT      = familyOrLegacy("sans-serif", NORMAL, 400);
    public static final Typeface DEFAULT_BOLD = create(DEFAULT, BOLD);
    public static final Typeface SANS_SERIF   = DEFAULT;
    public static final Typeface SERIF        = familyOrLegacy("serif", NORMAL, 400);
    public static final Typeface MONOSPACE    = familyOrLegacy("monospace", NORMAL, 400);

    /** create(familyName, style) is literally
     *      create(getSystemDefaultTypeface(familyName), style)
     *  on AOSP (Typeface.java:809-811), where getSystemDefaultTypeface is
     *      Typeface tf = sSystemFontMap.get(familyName);
     *      return tf == null ? Typeface.DEFAULT : tf;      (Typeface.java:1101-1104)
     *
     *  Doing the family lookup FIRST is what makes
     *  android:fontFamily="sans-serif-medium" + android:textStyle="bold" land
     *  on weight 800 rather than 700. An unknown name is NOT an error and is
     *  NOT guessed at: "roboto" and "sans-serif-regular" are not families or
     *  aliases in fonts.xml, so a device gives them plain DEFAULT. Mapping them
     *  here would be a divergence. */
    public static Typeface create(String familyName, int style) {
        return create(getSystemDefaultTypeface(familyName), style);
    }

    private static Typeface getSystemDefaultTypeface(String familyName) {
        if (familyName == null) return DEFAULT;
        Typeface cached = sSystemFontMap.get(familyName);
        if (cached != null) return cached;
        Typeface tf = systemFamily(familyName);
        return tf != null ? tf : DEFAULT;
    }

    /** create(family, style): AOSP Typeface.java:830-863. */
    public static Typeface create(Typeface family, int style) {
        // AOSP NORMALIZES an out-of-range style to NORMAL; it does not mask it
        // down to the low two bits:
        //     if ((style & ~STYLE_MASK) != 0) { style = NORMAL; }
        // The difference is visible for any style with a bit above 0x3 --
        // create(f, 7) is NORMAL on a device and was BOLD_ITALIC here, so a
        // caller passing a garbage int got bold italic text instead of the
        // plain fallback the platform gives. Probe row
        // TXT.typeface.create_invalidStyleMasked.
        if ((style & ~STYLE_MASK) != 0) style = NORMAL;
        if (family == null) family = DEFAULT;

        // ⚠ LOAD-BEARING, and the single easiest thing to drop in a port.
        // AOSP: "Return early if we're asked for the same face/style"
        //     if (family.mStyle == style) { return family; }
        // Without it, create(sans-serif-medium, NORMAL) -- which is exactly
        // what TextView.setTypeface(tf, style) does for an unstyled Medium
        // label -- rebuilds the face and, in any implementation that derives
        // weight from the style bits alone, silently collapses 500 -> 400.
        // The whole fontFamily fix dies quietly right here.
        // Probe row TXT.tf.roundTrip.mediumNormal (500 on a device).
        if (family.style == style) return family;

        Typeface cached = family.styledCache().get(style);
        if (cached != null) return cached;

        // nativeCreateFromTypeface -> Typeface::createRelative (hwui
        // Typeface.cpp:68-79): the font collection AND the base weight come
        // from the source face; only the API style is replaced.
        Typeface tf = new Typeface(family.baseFont, family.familyName,
                                   family.baseWeight, style,
                                   computeRelativeWeight(family.baseWeight, style),
                                   family.appFaceWeight, family.appFaceItalic);
        Typeface prev = family.styledCache().putIfAbsent(style, tf);
        return prev != null ? prev : tf;
    }

    // Mirrors sStyledTypefaceCache: derived faces are interned per source
    // typeface, so repeated create(tf, BOLD) hands back the SAME object.
    // TextView.setTypeface(Typeface) compares by identity before nulling its
    // layouts, so a fresh instance per call would be a per-frame relayout.
    private volatile java.util.concurrent.ConcurrentHashMap<Integer, Typeface> mStyled;
    private java.util.concurrent.ConcurrentHashMap<Integer, Typeface> styledCache() {
        java.util.concurrent.ConcurrentHashMap<Integer, Typeface> m = mStyled;
        if (m == null) {
            synchronized (this) {
                m = mStyled;
                if (m == null) mStyled = m = new java.util.concurrent.ConcurrentHashMap<>();
            }
        }
        return m;
    }

    // API 28+ weight+italic overload. androidx.compose.ui.text.font.
    // PlatformTypefacesApi28.createAndroidTypefaceApi28 calls this during
    // the Compose text MEASURE pass; a missing overload threw
    // NoSuchMethodError so ALL Compose text failed to render (v1.0.429).
    //
    // hwui Typeface::createAbsolute (Typeface.cpp:80-91): unlike createRelative
    // this sets fStyle from the REQUESTED weight directly (no +300) and derives
    // fAPIStyle from it, while still inheriting fBaseWeight.
    public static Typeface create(Typeface family, int weight, boolean italic) {
        if (family == null) family = DEFAULT;
        int w = clampWeight(weight);
        return new Typeface(family.baseFont, family.familyName, family.baseWeight,
                            computeAPIStyle(w, italic), w,
                            family.appFaceWeight, family.appFaceItalic);
    }

    /** A Typeface over an app-supplied face, taking its base weight and slant
     *  from the FILE the way AOSP does: createFromAsset/File pass
     *  RESOLVE_BY_FONT_TABLE, and hwui Typeface::createFromFamilies then reads
     *  the face's own SkFontStyle (i.e. its OS/2 table) --
     *      weightFromFont = style.weight();
     *      italicFromFont = style.slant() != kUpright_Slant;
     *      result->fBaseWeight = weight;
     *      result->fAPIStyle   = computeAPIStyle(weight, italic);
     *  So an app shipping MyFont-Bold.ttf gets getWeight() == 700 and
     *  getStyle() == BOLD, and minikin knows not to embolden it again. We used
     *  to hardcode 400/NORMAL here, which made getWeight() lie and pushed the
     *  fakery decision onto the requested style bits instead of the face. */
    private static Typeface fromLoadedFace(MacApkFont.LoadedFace f) {
        if (f == null || f.font == null) return DEFAULT;
        int w = clampWeight(f.weight);
        return new Typeface(f.font, null, w, computeAPIStyle(w, f.italic), w, w, f.italic);
    }

    public static Typeface createFromAsset(android.content.res.AssetManager mgr, String path) {
        if (mgr != null && path != null) {
            try (java.io.InputStream in = mgr.open(path)) {
                MacApkFont.LoadedFace f = MacApkFont.loadFace(in);
                if (f != null) return fromLoadedFace(f);
            } catch (Throwable ignored) {}
        }
        return DEFAULT;
    }

    public static Typeface createFromFile(java.io.File path) {
        return fromLoadedFace(MacApkFont.loadFace(path));
    }

    public static Typeface createFromFile(String path) {
        return path != null ? createFromFile(new java.io.File(path)) : DEFAULT;
    }

    // ---- AOSP query API ----
    /** AOSP Typeface.getSystemFontFamilyName() (Typeface.java:325, API 31):
     *  "the system font family name if the typeface was created from a system
     *  font family, otherwise null". Our `familyName` carries exactly that --
     *  systemFamily() sets it, fromLoadedFace() (createFromAsset/File, an APP
     *  font) passes null. Declared final in AOSP. */
    public final String getSystemFontFamilyName() { return familyName; }

    public int getStyle()     { return style; }
    public boolean isBold()   { return (style & BOLD) != 0; }
    public boolean isItalic() { return (style & ITALIC) != 0; }
    public int getWeight()    { return weight; }

    /** Mac.apk accessor for the Paint/Canvas text path: an APP-SUPPLIED base
     *  Font to derive from, or null when this typeface is a system family (or
     *  the table is off, in which case the legacy single-Roboto path runs). */
    public Font macapkBaseFont() { return baseFont; }

    /** The system face this typeface resolves to, plus the synthetic styling
     *  minikin still applies on top of it -- or null when there is no system
     *  family to resolve (an app font, or the table gated off).
     *
     *  This is the accessor the text path must use instead of reading
     *  isBold()/isItalic() and AWT-emboldening: once a real Roboto-Bold exists,
     *  emboldening it as well is both wrong and visibly wrong (measured, an AWT
     *  algorithmic embolden is ~4px WIDER than real Roboto-Bold on the fwprobe
     *  sentence while simultaneously reading THINNER -- a smear, not a weight). */
    public MacApkFontFamilies.Resolved macapkResolvedFace() {
        if (baseFont != null || familyName == null) return null;
        return MacApkFontFamilies.resolve(familyName, weight, (style & ITALIC) != 0);
    }

    /** The concrete fonts.xml family behind this typeface, or null. Diagnostics
     *  (MACAPK_TRACE_TYPEFACE) and the fwprobe cases read it. */
    public String macapkFamilyName() { return familyName; }

    /**
     * A Typeface over an APP-SUPPLIED family -- a `res/font/<name>.xml`
     * `<font-family>` with one entry per variant, registered through
     * MacApkFontFamilies.registerAppFamily.
     *
     * AOSP's Typeface.createFromResources builds a real FontFamily over every
     * `<font>` entry and then
     *     createFromFamiliesWithDefault(families, DEFAULT_FAMILY,
     *                                   RESOLVE_BY_FONT_TABLE, RESOLVE_BY_FONT_TABLE)
     * so the base weight comes from whichever face wins the DEFAULT-style match,
     * not from a constant -- an app family with only 300 and 700 entries has a
     * base weight of 300, and asking it for bold gives 600, not 700.
     *
     * The point of routing app families through the same table as the system
     * ones is that `Typeface.create(thisFamily, BOLD)` then resolves to the
     * app's OWN Bold FILE instead of algorithmically emboldening its Regular.
     *
     * @return null when the family could not be registered (table gated off, or
     *         nothing loadable), so the caller can fall back.
     */
    public static Typeface macapkFromAppFamily(String registeredFamilyName) {
        if (registeredFamilyName == null) return null;
        if (!MacApkFontFamilies.isKnown(registeredFamilyName)) return null;
        int bw = clampWeight(MacApkFontFamilies.defaultWeightOf(registeredFamilyName));
        boolean italic = false;
        MacApkFontFamilies.Resolved r =
            MacApkFontFamilies.resolve(registeredFamilyName, 400, false);
        if (r != null) italic = r.faceItalic;
        return new Typeface(null, registeredFamilyName, bw,
                            computeAPIStyle(bw, italic), bw);
    }

    private static int clampWeight(int w) {
        if (w < 1) return 1;
        if (w > 1000) return 1000;
        return w;
    }

    // android.graphics.Typeface.Builder (API 26). Compose / AppCompat /
    // download-fonts paths construct one. AOSP signature surface:
    //   Builder(AssetManager, String) / Builder(File) / Builder(String) /
    //   setWeight / setItalic / setFontVariationSettings / setTtcIndex /
    //   setFallback / build().
    public static final class Builder {
        /** AOSP's RESOLVE_BY_FONT_TABLE: "not set by the caller, read it out of
         *  the font file". Both fields start here, so a Builder that never
         *  calls setWeight/setItalic reports the face's OWN style rather than a
         *  hardcoded Regular. */
        private static final int RESOLVE_BY_FONT_TABLE = -1;

        private MacApkFont.LoadedFace face;
        private int weight = RESOLVE_BY_FONT_TABLE;
        private int italic = RESOLVE_BY_FONT_TABLE;

        public Builder(android.content.res.AssetManager assets, String path) {
            if (assets != null && path != null) {
                try (java.io.InputStream in = assets.open(path)) {
                    face = MacApkFont.loadFace(in);
                } catch (Throwable ignored) {}
            }
        }
        public Builder(java.io.File path)  { face = MacApkFont.loadFace(path); }
        public Builder(String path)        { if (path != null) face = MacApkFont.loadFace(new java.io.File(path)); }
        public Builder(java.io.FileDescriptor fd) { /* fd-backed load unsupported; null face */ }
        private Builder(MacApkFont.LoadedFace face) { this.face = face; }

        /** A Builder over font bytes read from a stream -- how Resources
         *  loads an app's `res/font/*.ttf` straight out of the installed
         *  apk (v1.0.1590), where AOSP's ResourcesImpl.loadFont hands the
         *  asset-manager path to `Builder(AssetManager, path)`. Not AOSP
         *  API; the macapk prefix marks it. */
        public static Builder macapkFromStream(java.io.InputStream in) {
            return new Builder(in != null ? MacApkFont.loadFace(in) : null);
        }

        public Builder setWeight(int weight) { this.weight = clampWeight(weight); return this; }
        public Builder setItalic(boolean italic) { this.italic = italic ? 1 : 0; return this; }
        public Builder setTtcIndex(int ttcIndex) { return this; }
        public Builder setFontVariationSettings(String variationSettings) { return this; }
        public Builder setFontVariationSettings(android.graphics.fonts.FontVariationAxis[] axes) { return this; }
        public Builder setFallback(String familyName) { return this; }

        public Typeface build() {
            // hwui Typeface::createFromFamilies (Typeface.cpp:127-168): an
            // unset weight/italic is filled in from the face's own table, THEN
            // fBaseWeight/fAPIStyle/fStyle are computed from the result.
            int faceW = (face != null) ? face.weight : 400;
            boolean faceI = (face != null) && face.italic;
            int w  = (weight == RESOLVE_BY_FONT_TABLE) ? faceW : clampWeight(weight);
            boolean it = (italic == RESOLVE_BY_FONT_TABLE) ? faceI : (italic != 0);
            if (face == null || face.font == null) {
                // AOSP's Builder falls back to setFallback()'s family (default
                // sans-serif); a family-less typeface would drop the requested
                // weight on the floor and render Regular.
                return new Typeface(null, DEFAULT.familyName, w, computeAPIStyle(w, it), w);
            }
            return new Typeface(face.font, null, w, computeAPIStyle(w, it), w, faceW, faceI);
        }
    }

    /** A Typeface over an app-supplied face, or -- when that face could not be
     *  loaded -- over the default SYSTEM family at the same weight/slant. AOSP's
     *  Builder does the equivalent via setFallback(), which defaults to
     *  sans-serif; falling back to a family-less typeface instead would drop the
     *  requested weight on the floor and render Regular. */
    private static Typeface withBaseOrDefaultFamily(Font base, int style, int weight) {
        if (base != null) return new Typeface(base, null, weight, style, weight);
        return new Typeface(null, DEFAULT.familyName, weight, style, weight);
    }

    /**
     * Builds a Typeface from explicit android.graphics.fonts families, with the
     * system font as the last-resort fallback.
     *
     * This is NOT decorative: the first font of the first family is loaded into
     * a real AWT base font (via the same MacApkFont path createFromFile uses),
     * so text drawn with the result uses the app's actual glyphs. Returning
     * DEFAULT here would be a silent no-op -- the app would ship a custom font
     * and quietly render in Roboto.
     *
     * AOSP caps the custom fallback chain at 64 families and rejects an empty
     * one; we enforce the same limits so a caller that would fail on a device
     * fails here too.
     *
     * Source: frameworks/base/graphics/java/android/graphics/Typeface.java
     * (nested class CustomFallbackBuilder)
     */
    public static final class CustomFallbackBuilder {

        private static final int MAX_CUSTOM_FALLBACK = 64;

        private final java.util.ArrayList<android.graphics.fonts.FontFamily> mFamilies =
                new java.util.ArrayList<>();
        private String mSystemFallbackName;
        private android.graphics.fonts.FontStyle mStyle;

        public CustomFallbackBuilder(android.graphics.fonts.FontFamily family) {
            if (family == null) throw new NullPointerException("family can not be null");
            mFamilies.add(family);
        }

        public static int getMaxCustomFallbackCount() { return MAX_CUSTOM_FALLBACK; }

        public CustomFallbackBuilder addCustomFallback(
                android.graphics.fonts.FontFamily family) {
            if (family == null) throw new NullPointerException("family can not be null");
            if (mFamilies.size() >= MAX_CUSTOM_FALLBACK) {
                throw new IllegalArgumentException(
                        "Custom fallback limit exceeded(" + MAX_CUSTOM_FALLBACK + ")");
            }
            mFamilies.add(family);
            return this;
        }

        public CustomFallbackBuilder setSystemFallback(String familyName) {
            if (familyName == null) throw new NullPointerException("familyName can not be null");
            mSystemFallbackName = familyName;
            return this;
        }

        public CustomFallbackBuilder setStyle(android.graphics.fonts.FontStyle style) {
            mStyle = style;
            return this;
        }

        public Typeface build() {
            int weight = 400;
            int st = NORMAL;
            if (mStyle != null) {
                weight = mStyle.getWeight();
                st = ((weight >= 600) ? BOLD : NORMAL)
                        | ((mStyle.getSlant() == android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC)
                            ? ITALIC : NORMAL);
            }

            // Load the primary face for real so the custom font actually draws.
            MacApkFont.LoadedFace face = null;
            android.graphics.fonts.FontFamily first = mFamilies.get(0);
            if (first.getSize() > 0) {
                android.graphics.fonts.Font f = first.getFont(0);
                if (f.getFile() != null) {
                    face = MacApkFont.loadFace(f.getFile());
                } else if (f.getBuffer() != null) {
                    java.nio.ByteBuffer b = f.getBuffer().duplicate();
                    byte[] bytes = new byte[b.remaining()];
                    b.get(bytes);
                    face = MacApkFont.loadFace(bytes);
                }
            }
            if (face == null || face.font == null)
                return withBaseOrDefaultFamily(null, st, clampWeight(weight));
            // No setStyle() => the face's own table decides, same
            // RESOLVE_BY_FONT_TABLE rule the plain Builder follows.
            int w  = (mStyle != null) ? clampWeight(weight) : face.weight;
            int st2 = (mStyle != null) ? st : computeAPIStyle(face.weight, face.italic);
            return new Typeface(face.font, null, w, st2, w, face.weight, face.italic);
        }
    }

    /** AOSP Typeface.defaultFromStyle (API 1): the default family in the given
     *  style (NORMAL/BOLD/ITALIC/BOLD_ITALIC). */
    public static Typeface defaultFromStyle(int style) {
        return create(DEFAULT, style);
    }

}
