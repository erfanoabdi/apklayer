// MacApkFontFamilies -- the system font family table, i.e. our stand-in for
// Typeface.sSystemFontMap plus the minikin FontFamily each entry points at.
//
// WHY THIS EXISTS. Typeface.create(String familyName, int style) used to ignore
// familyName entirely and collapse every request to weight 400 or 700, so
// android:fontFamily was dropped on the floor across the fleet. 38 of the 61
// corpus apps take the string-name path (`sans-serif` 220 refs,
// `sans-serif-medium` 178, `sans-serif-light` 49, plus ~600 @string/
// indirections -- Material 3's m3_ref_typeface_plain_medium resolves to
// "sans-serif-medium"), so every Material label, button and app-bar title
// rendered Regular where Android renders Medium: narrower AND lighter.
//
// THE TABLE IS TRANSCRIBED FROM THE DEVICE, not from memory: it is
// /system/etc/fonts.xml as read off the golden AVD (fwprobe-api30-320,
// familyset version="23", Android 11 / API 30 -- the same image the checked-in
// fwprobe golden was captured on). Every .ttf it names is pulled from that same
// image into runtime/fonts/ and bundled into android-framework.jar by
// runtime/scripts/build.sh's glob.
//
// FACE ORDER INSIDE A FAMILY IS LOAD-BEARING and is preserved verbatim. Quoting
// the comment at the head of the device's own fonts.xml:
//
//     Order of appearance is also the tiebreaker for weight matching. This is
//     the reason why the 900 weights of Roboto precede the 700 weights - we
//     prefer the former when an 800 weight is requested.
//
// So Black(900) really does come before Bold(700) below; sorting this table
// would silently change which face an 800-weight request resolves to.
package android.graphics;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MacApkFontFamilies {
    private MacApkFontFamilies() {}

    /** Gate: the name -> family lookup. ON by default; MACAPK_FONT_FAMILY=0
     *  restores the pre-fix behaviour where every family name resolved to the
     *  single bundled Roboto-Regular. Kept independent of the three other text
     *  gates so a fleet delta stays attributable. */
    public static final boolean ENABLED =
        !"0".equals(System.getenv("MACAPK_FONT_FAMILY"));

    // ---------------------------------------------------------------
    // One <font> row.
    // ---------------------------------------------------------------
    private static final class Face {
        final int weight;
        final boolean italic;
        final String res;          // resource name under /fonts/ in the jar
        private volatile Font base;
        private volatile boolean tried;

        Face(int weight, boolean italic, String res) {
            this.weight = weight; this.italic = italic; this.res = res;
        }

        /** A face whose Font is already in hand -- an app's own
         *  `res/font/*.xml` `<font-family>` entry, which has no jar resource to
         *  lazily open. */
        Face(int weight, boolean italic, Font preloaded) {
            this.weight = weight; this.italic = italic; this.res = null;
            this.base = preloaded; this.tried = true;
        }

        /** The unsized AWT font, loaded on first use, or null when the face is
         *  not bundled. Loading is deliberately deferred until a face WINS a
         *  match: matching itself reads only the declared (weight, italic), so
         *  resolving one label does not drag all 12 Roboto faces into memory. */
        Font base() {
            if (tried) return base;
            synchronized (this) {
                if (tried) return base;
                Font f = null;
                try (java.io.InputStream in =
                         MacApkFontFamilies.class.getResourceAsStream("/fonts/" + res)) {
                    if (in != null) {
                        // The BYTES, not the stream: MacApkFont.verticalExtents
                        // needs this face's `head` table for Paint's top/bottom,
                        // and Font.createFont consumes the stream without
                        // exposing any of the face's vertical metrics.
                        byte[] bytes = in.readAllBytes();
                        // Through the content-addressed store, never the
                        // InputStream overload: that leaks a $TMPDIR/+~JF copy
                        // per face per launch (see MacApkFont.createFontFromBytes).
                        f = MacApkFont.createFontFromBytes(bytes);
                        MacApkFont.rememberExtents(f, bytes);
                        // Record what this FACE declares, so fallback selection
                        // can ask the file rather than the AWT style bit. A real
                        // bold cut is handed out with style PLAIN (synthetic
                        // styling is only applied when no real cut exists), so
                        // Font.isBold() is false exactly when a genuine bold face
                        // is primary -- which is when a bold fallback is wanted.
                        MacApkFont.rememberDeclaredBold(f, weight >= 600);
                    }
                } catch (Throwable ignored) {}
                base = f;
                tried = true;
                return f;
            }
        }
    }

    private static Face n(int w, String res) { return new Face(w, false, res); }
    private static Face i(int w, String res) { return new Face(w, true,  res); }

    // ---------------------------------------------------------------
    // <family name="..."> -- verbatim from the device, in file order.
    // ---------------------------------------------------------------
    // ConcurrentHashMap, not HashMap: app font families are registered at
    // RUNTIME (see registerAppFamily) while other threads are resolving faces.
    private static final Map<String, Face[]> FAMILIES = new ConcurrentHashMap<>();
    /** alias name -> the family it points at. */
    private static final Map<String, String> ALIAS_TO = new HashMap<>();
    /** alias name -> its weight override (absent == 400, i.e. no override). */
    private static final Map<String, Integer> ALIAS_WEIGHT = new HashMap<>();

    static {
        FAMILIES.put("sans-serif", new Face[] {
            n(100, "Roboto-Thin.ttf"),    i(100, "Roboto-ThinItalic.ttf"),
            n(300, "Roboto-Light.ttf"),   i(300, "Roboto-LightItalic.ttf"),
            n(400, "Roboto-Regular.ttf"), i(400, "Roboto-Italic.ttf"),
            n(500, "Roboto-Medium.ttf"),  i(500, "Roboto-MediumItalic.ttf"),
            // 900 BEFORE 700 -- see the header note. Do not sort.
            n(900, "Roboto-Black.ttf"),   i(900, "Roboto-BlackItalic.ttf"),
            n(700, "Roboto-Bold.ttf"),    i(700, "Roboto-BoldItalic.ttf"),
        });
        FAMILIES.put("sans-serif-condensed", new Face[] {
            n(300, "RobotoCondensed-Light.ttf"),   i(300, "RobotoCondensed-LightItalic.ttf"),
            n(400, "RobotoCondensed-Regular.ttf"), i(400, "RobotoCondensed-Italic.ttf"),
            n(500, "RobotoCondensed-Medium.ttf"),  i(500, "RobotoCondensed-MediumItalic.ttf"),
            n(700, "RobotoCondensed-Bold.ttf"),    i(700, "RobotoCondensed-BoldItalic.ttf"),
        });
        FAMILIES.put("serif", new Face[] {
            n(400, "NotoSerif-Regular.ttf"), n(700, "NotoSerif-Bold.ttf"),
            i(400, "NotoSerif-Italic.ttf"),  i(700, "NotoSerif-BoldItalic.ttf"),
        });
        FAMILIES.put("monospace",       new Face[] { n(400, "DroidSansMono.ttf") });
        FAMILIES.put("serif-monospace", new Face[] { n(400, "CutiveMono.ttf") });
        FAMILIES.put("casual",          new Face[] { n(400, "ComingSoon.ttf") });
        FAMILIES.put("cursive", new Face[] {
            n(400, "DancingScript-Regular.ttf"), n(700, "DancingScript-Bold.ttf"),
        });
        FAMILIES.put("sans-serif-smallcaps",
                     new Face[] { n(400, "CarroisGothicSC-Regular.ttf") });
        FAMILIES.put("source-sans-pro", new Face[] {
            n(400, "SourceSansPro-Regular.ttf"),  i(400, "SourceSansPro-Italic.ttf"),
            n(600, "SourceSansPro-SemiBold.ttf"), i(600, "SourceSansPro-SemiBoldItalic.ttf"),
            n(700, "SourceSansPro-Bold.ttf"),     i(700, "SourceSansPro-BoldItalic.ttf"),
        });

        // <alias>. A weight-bearing alias is the whole reason sans-serif-medium
        // differs from sans-serif: Typeface.initSystemDefaultTypefaces builds it
        // as nativeCreateWeightAlias(base, weight), which sets the new face's
        // BASE WEIGHT (frameworks/base/graphics/java/android/graphics/
        // Typeface.java:1114-1129 at android11-release).
        alias("sans-serif-thin",   "sans-serif", 100);
        alias("sans-serif-light",  "sans-serif", 300);
        alias("sans-serif-medium", "sans-serif", 500);
        alias("sans-serif-black",  "sans-serif", 900);
        alias("arial",     "sans-serif", 400);
        alias("helvetica", "sans-serif", 400);
        alias("tahoma",    "sans-serif", 400);
        alias("verdana",   "sans-serif", 400);
        alias("sans-serif-condensed-light",  "sans-serif-condensed", 300);
        alias("sans-serif-condensed-medium", "sans-serif-condensed", 500);
        alias("serif-bold",      "serif", 700);
        alias("times",           "serif", 400);
        alias("times new roman", "serif", 400);
        alias("palatino",        "serif", 400);
        alias("georgia",         "serif", 400);
        alias("baskerville",     "serif", 400);
        alias("goudy",           "serif", 400);
        alias("fantasy",         "serif", 400);
        alias("ITC Stone Serif", "serif", 400);
        alias("sans-serif-monospace", "monospace", 400);
        alias("monaco",               "monospace", 400);
        alias("courier",     "serif-monospace", 400);
        alias("courier new", "serif-monospace", 400);
        alias("source-sans-pro-semi-bold", "source-sans-pro", 600);
    }

    private static void alias(String name, String to, int weight) {
        ALIAS_TO.put(name, to);
        if (weight != 400) ALIAS_WEIGHT.put(name, weight);
    }

    // ---------------------------------------------------------------
    // The same table as a device fonts.xml, for native text stacks.
    // ---------------------------------------------------------------
    /** The system families in the device file's order. FAMILIES also holds app
     *  families registered at runtime, so the XML walks this list, not the map.
     *  The first entry is the default family a native font manager falls back
     *  to (Skia's SkFontMgr_Android takes the first <family>). */
    private static final String[] SYSTEM_ORDER = {
        "sans-serif", "sans-serif-condensed", "serif", "monospace", "serif-monospace",
        "casual", "cursive", "sans-serif-smallcaps", "source-sans-pro",
    };
    /** Nameless fallback families: the faces MacApkFont chains to after the
     *  named ones (MacApkFont's fallback list), tagged the way the device file
     *  tags them. Each row is {lang, file...}. */
    private static final String[][] SYSTEM_FALLBACKS = {
        { "und-Zsym", "NotoSansSymbols-Regular-Subsetted.ttf" },
        { "und-Zsym", "NotoSansSymbols-Regular-Subsetted2.ttf" },
        { "und-Hebr", "NotoSansHebrew-Regular.ttf", "NotoSansHebrew-Bold.ttf" },
    };

    /** /system/etc/fonts.xml for MacApkSystemFonts: the table above in the
     *  device's own format (familyset version 23, the API 30 file it was
     *  transcribed from), so a native font manager sees the families, faces,
     *  weights and aliases Java text already uses. */
    static String systemFontsXml() {
        return systemFontsXml(java.util.List.of());
    }

    /** The same file with the host's CJK families (MacApkHostFonts) after the
     *  bundled fallbacks, where the device file has its four CJK families: each
     *  face by its published link name and its index in the collection. */
    static String systemFontsXml(java.util.List<MacApkHostFonts.Family> host) {
        StringBuilder b = new StringBuilder(4096);
        b.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        b.append("<!-- Mac.apk: generated from android.graphics.MacApkFontFamilies -->\n");
        b.append("<familyset version=\"23\">\n");
        for (String name : SYSTEM_ORDER) {
            Face[] faces = FAMILIES.get(name);
            if (faces == null) continue;
            b.append("    <family name=\"").append(name).append("\">\n");
            for (Face f : faces) appendFont(b, f.weight, f.italic, f.res);
            b.append("    </family>\n");
        }
        for (Map.Entry<String, String> e : new java.util.TreeMap<>(ALIAS_TO).entrySet()) {
            b.append("    <alias name=\"").append(e.getKey()).append("\" to=\"")
             .append(e.getValue()).append('"');
            Integer w = ALIAS_WEIGHT.get(e.getKey());
            if (w != null) b.append(" weight=\"").append(w).append('"');
            b.append(" />\n");
        }
        for (String[] fb : SYSTEM_FALLBACKS) {
            b.append("    <family lang=\"").append(fb[0]).append("\">\n");
            for (int k = 1; k < fb.length; k++)
                appendFont(b, fb[k].contains("-Bold") ? 700 : 400, false, fb[k]);
            b.append("    </family>\n");
        }
        for (MacApkHostFonts.Family fam : host) {
            b.append("    <family lang=\"").append(fam.lang).append("\">\n");
            for (MacApkHostFonts.Face f : fam.faces) {
                if (f.linkName == null || f.index < 0) continue;
                b.append("        <font weight=\"").append(f.weight)
                 .append("\" style=\"normal\" index=\"").append(f.index).append("\">")
                 .append(f.linkName).append("</font>\n");
            }
            b.append("    </family>\n");
        }
        b.append("</familyset>\n");
        return b.toString();
    }

    /** Every font file systemFontsXml() names, each once. */
    static java.util.List<String> systemFontFiles() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String name : SYSTEM_ORDER) {
            Face[] faces = FAMILIES.get(name);
            if (faces != null) for (Face f : faces) if (f.res != null) out.add(f.res);
        }
        for (String[] fb : SYSTEM_FALLBACKS)
            for (int k = 1; k < fb.length; k++) out.add(fb[k]);
        return new java.util.ArrayList<>(out);
    }

    private static void appendFont(StringBuilder b, int weight, boolean italic, String file) {
        if (file == null) return;
        b.append("        <font weight=\"").append(weight).append("\" style=\"")
         .append(italic ? "italic" : "normal").append("\">").append(file).append("</font>\n");
    }

    // ---------------------------------------------------------------
    // Lookup. Exact, case-sensitive string match -- AOSP's
    // getSystemDefaultTypeface is `sSystemFontMap.get(familyName)`, a plain
    // HashMap get, and an unknown name returns Typeface.DEFAULT
    // (Typeface.java:1101-1104). That is why "sans-serif-regular" and "roboto"
    // must NOT be mapped here: they are not families or aliases in fonts.xml,
    // so on a device they fall through to DEFAULT. Adding them "helpfully"
    // would be a divergence.
    // ---------------------------------------------------------------

    // ---------------------------------------------------------------
    // App-supplied families (res/font/<name>.xml <font-family>)
    // ---------------------------------------------------------------
    //
    // AOSP builds a real minikin FontFamily over EVERY <font> entry in the
    // descriptor and then matches into it, so an app that ships Regular + Bold
    // and asks for bold gets its own Bold FILE. Registering the family here
    // rather than special-casing it means it goes through the identical
    // getClosestMatch / computeFakery path as a system family -- including the
    // file-order tiebreak and the fakery residual -- instead of a parallel
    // implementation that would drift.
    //
    // The names are synthetic and cannot collide with fonts.xml: no family or
    // alias on the device contains a space followed by a digit.
    private static final java.util.concurrent.atomic.AtomicInteger sAppFamilySeq =
        new java.util.concurrent.atomic.AtomicInteger();

    /** Register an app font family from (weight, italic, font) triples, in
     *  DESCRIPTOR ORDER -- order is the tiebreak, exactly as in fonts.xml.
     *  Returns the synthetic family name, or null when there is nothing usable. */
    public static String registerAppFamily(java.util.List<Object[]> faces) {
        if (!ENABLED || faces == null || faces.isEmpty()) return null;
        java.util.ArrayList<Face> out = new java.util.ArrayList<>();
        for (Object[] f : faces) {
            if (f == null || f.length < 3 || !(f[2] instanceof Font)) continue;
            int w = (f[0] instanceof Integer) ? (Integer) f[0] : 400;
            boolean it = (f[1] instanceof Boolean) && (Boolean) f[1];
            if (w < 1 || w > 1000) w = 400;
            out.add(new Face(w, it, (Font) f[2]));
        }
        if (out.isEmpty()) return null;
        String name = "app font " + sAppFamilySeq.incrementAndGet();
        FAMILIES.put(name, out.toArray(new Face[0]));
        return name;
    }

    /** True when `name` names a family or alias the device would know. */
    public static boolean isKnown(String name) {
        if (name == null || !ENABLED) return false;
        return FAMILIES.containsKey(name) || ALIAS_TO.containsKey(name);
    }

    /** The concrete family a name resolves to, or null when unknown. */
    public static String familyOf(String name) {
        if (name == null || !ENABLED) return null;
        if (FAMILIES.containsKey(name)) return name;
        return ALIAS_TO.get(name);   // null when unknown
    }

    /** The base weight `name` carries: an alias's weight override, else 400.
     *  Meaningless unless isKnown(name). */
    public static int baseWeightOf(String name) {
        Integer w = (name == null) ? null : ALIAS_WEIGHT.get(name);
        return w == null ? 400 : w;
    }

    /** The weight a family RESOLVES TO by default, i.e. the weight of the face
     *  closest to (400, upright).
     *
     *  For every fonts.xml family that is 400, which is why baseWeightOf can
     *  hardcode it -- but an APP family need not contain a 400 face at all, and
     *  hwui's RESOLVE_BY_FONT_TABLE path reads the weight off the winner of
     *  exactly this default-style match (Typeface.cpp:131-146), not off a
     *  constant. */
    public static int defaultWeightOf(String familyName) {
        Resolved r = resolve(familyName, 400, false);
        return (r != null && r.faceWeight >= 1) ? r.faceWeight : 400;
    }

    // ---------------------------------------------------------------
    // The matcher, transcribed from minikin.
    // frameworks/minikin/libs/minikin/FontFamily.cpp:127-157 (android11-release)
    //
    //     static int computeMatch(FontStyle style1, FontStyle style2) {
    //         if (style1 == style2) return 0;
    //         int score = abs(style1.weight() / 100 - style2.weight() / 100);
    //         if (style1.slant() != style2.slant()) { score += 2; }
    //         return score;
    //     }
    //
    //     FakedFont FontFamily::getClosestMatch(FontStyle style) const {
    //         const Font* bestFont = &mFonts[0];
    //         int bestMatch = computeMatch(bestFont->style(), style);
    //         for (size_t i = 1; i < mFonts.size(); i++) {
    //             const Font& font = mFonts[i];
    //             int match = computeMatch(font.style(), style);
    //             if (i == 0 || match < bestMatch) { bestFont = &font; bestMatch = match; }
    //         }
    //         return FakedFont{bestFont, computeFakery(style, bestFont->style())};
    //     }
    //
    // Two details that decide real cases:
    //   - the weight term is INTEGER division by 100, so 500 and 599 tie;
    //   - the comparison is STRICTLY LESS, so on a tie the FIRST face in file
    //     order wins. Combined with Black-before-Bold that is what makes a
    //     weight-800 request resolve to Roboto-Black (|9-8| == |7-8| == 1).
    // ---------------------------------------------------------------
    private static int computeMatch(int w1, boolean it1, int w2, boolean it2) {
        if (w1 == w2 && it1 == it2) return 0;
        int score = Math.abs(w1 / 100 - w2 / 100);
        if (it1 != it2) score += 2;
        return score;
    }

    /** The face a request resolves to, plus the synthetic styling minikin
     *  still applies on top of it. */
    public static final class Resolved {
        /** Unsized AWT font for the winning face. Never null. */
        public final Font base;
        /** minikin computeFakery: the residual the chosen face cannot provide. */
        public final boolean fakeBold;
        public final boolean fakeItalic;
        /** The winning face's own (weight, italic) and its fonts.xml file name.
         *  Diagnostics only -- but load-bearing for the gate, which asserts
         *  WHICH FILE wins rather than just that something did. */
        public final int faceWeight;
        public final boolean faceItalic;
        public final String faceName;

        Resolved(Font base, boolean fb, boolean fi, int fw, boolean fit, String name) {
            this.base = base; this.fakeBold = fb; this.fakeItalic = fi;
            this.faceWeight = fw; this.faceItalic = fit; this.faceName = name;
        }
    }

    /**
     * minikin FontFamily.cpp:136-144 (android11-release):
     *
     *     static FontFakery computeFakery(FontStyle wanted, FontStyle actual) {
     *         bool isFakeBold = wanted.weight() >= 600 && (wanted.weight() - actual.weight()) >= 200;
     *         bool isFakeItalic = wanted.slant() == ITALIC && actual.slant() == UPRIGHT;
     *         return FontFakery(isFakeBold, isFakeItalic);
     *     }
     *
     * This is the ONLY sanctioned source of synthetic bolding on the family
     * path. Emboldening because the caller asked for BOLD is wrong once the
     * table exists: a bold request against sans-serif resolves to the real
     * Roboto-Bold and needs no fakery, while the same request against
     * `monospace` (whose single face is 400) legitimately does get it.
     */
    public static Resolved closestMatch(String familyName, int weight, boolean italic) {
        if (!ENABLED) return null;
        String fam = familyOf(familyName);
        if (fam == null) return null;
        Face[] faces = FAMILIES.get(fam);
        if (faces == null || faces.length == 0) return null;

        Face best = faces[0];
        int bestMatch = computeMatch(best.weight, best.italic, weight, italic);
        for (int k = 1; k < faces.length; k++) {
            int m = computeMatch(faces[k].weight, faces[k].italic, weight, italic);
            if (m < bestMatch) { best = faces[k]; bestMatch = m; }
        }

        // Deliberately load ONLY the winner. A face that is not bundled makes
        // the whole lookup fail rather than silently promoting the runner-up:
        // quietly answering with a different face would be a divergence we
        // could not see, whereas returning null falls back to the documented
        // pre-table Roboto path. Every face this table names IS bundled, so
        // this is a safety net, not a live path.
        Font base = best.base();
        if (base == null) return null;

        boolean fakeBold = weight >= 600 && (weight - best.weight) >= 200;
        boolean fakeItalic = italic && !best.italic;
        return new Resolved(base, fakeBold, fakeItalic, best.weight, best.italic, best.res);
    }

    // Resolved instances are immutable and the inputs are a small closed set,
    // so cache them: Paint resolves a face on every measure and every draw.
    private static final ConcurrentHashMap<String, Resolved> sCache =
        new ConcurrentHashMap<>();
    private static final Resolved MISS =
        new Resolved(new Font(Font.SANS_SERIF, Font.PLAIN, 1), false, false, 0, false, null);

    /** Cached closestMatch. Returns null for an unknown family (the caller then
     *  takes the pre-table path). */
    public static Resolved resolve(String familyName, int weight, boolean italic) {
        if (!ENABLED || familyName == null) return null;
        String key = familyName + ' ' + weight + (italic ? 'i' : 'n');
        Resolved r = sCache.get(key);
        if (r == null) {
            r = closestMatch(familyName, weight, italic);
            sCache.put(key, r == null ? MISS : r);
            return r;
        }
        return r == MISS ? null : r;
    }
}
