// MacApkHostFonts -- the Mac's own Chinese, Japanese and Korean faces behind the
// CJK fallback families of the native-stack fonts.xml (MacApkSystemFonts).
//
// WHY THIS EXISTS. A device's /system/etc/fonts.xml ends its fallback list with
// four CJK families (frameworks/base data/fonts/fonts.xml at android11-release,
// lines 578-593):
//
//     <family lang="zh-Hans">         NotoSansCJK-Regular.ttc index 2
//     <family lang="zh-Hant,zh-Bopo"> NotoSansCJK-Regular.ttc index 3
//     <family lang="ja">              NotoSansCJK-Regular.ttc index 0
//     <family lang="ko">              NotoSansCJK-Regular.ttc index 1
//
// Our generated file had none of them, so a native text stack found NO face for
// a Han, kana or Hangul code point: Skia's SkFontMgr_Android walks its fallback
// families for one whose cmap covers the character
// (onMatchFamilyStyleCharacter -> find_family_style_character), found nothing,
// and Flutter drew every Chinese character as the default face's .notdef box
// (GitHub issue #27, a Flutter app with Simplified Chinese text). Java text never
// showed it: MacApkFont's chain ends in the JDK logical composite, which reaches
// the host's CJK fonts on its own.
//
// WHAT IT DOES (decision ladder rung 1: macOS already ships these scripts). The
// same four families, in the device's order and with the device's lang tags,
// backed by faces macOS ships for those scripts: PingFang SC / TC where it is
// drawable, else Hiragino Sans GB and Heiti TC for Chinese, Hiragino Sans for
// Japanese, Apple SD Gothic Neo for Korean. Nothing is copied or bundled.
// CoreText names the file behind each PostScript name (NativeCocoa.hostFontFile,
// passed in by Boot as a resolver so this framework class has no loader
// dependency), this class reads the collection's table directories and name
// tables to find the face's index, and MacApkSystemFonts links the file into the
// published directory under an ASCII name (the host files have spaces and
// Japanese characters in their names, which must never reach a guest-visible
// path).
//
// ONLY FACES A GUEST RASTERIZER CAN DRAW. The guest draws with its own FreeType
// (Skia's SkFontMgr_Android opens every fonts.xml face through FreeType), which
// reads TrueType 'glyf' and CFF / CFF2 outlines. macOS 26's PingFang
// (FontServices.framework/Resources/Reserved/PingFangUI.ttc) carries its glyphs
// in Apple's own 'hvgl' table and has neither: its cmap still covers every Han
// character, so Skia's fallback search would pick it and FreeType would then draw
// every Chinese character as NOTHING (measured with the JDK's FreeType scaler:
// canDisplay true, 0x0 outline). So a face counts only when its table directory
// holds glyf, CFF or CFF2, and each weight lists its candidates in preference
// order so the next one takes over: PingFang on a macOS whose PingFang is a
// plain TrueType/CFF face, Hiragino Sans GB / Heiti on this one.
//
// WEIGHTS. A device's Noto CJK is one Regular face (API 30) or one variable face
// (API 34, wght 100-900). The Mac's faces are separate cuts, so each family lists
// the cuts that exist (400 / 500 / 600 or 700); Skia's matchAStyle picks the
// nearest, as it does across a device's variable range. A PostScript name that
// is only a NAMED INSTANCE of a variable face (PingFangSC-Regular inside the
// PingFangSC-Medium face) is not matched: publishing it would need an <axis>
// element per coordinate, and no drawable CJK face on a current Mac needs one.
//
// Gate: MACAPK_SYSTEM_FONTS_HOST=0 leaves the CJK families out (the pre-fix
// fonts.xml, and the gate's positive control).
package android.graphics;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MacApkHostFonts {
    private MacApkHostFonts() {}

    public static final boolean ENABLED =
        !"0".equals(System.getenv("MACAPK_SYSTEM_FONTS_HOST"));

    /** One device CJK family: its fonts.xml lang attribute and, per weight, the
     *  host PostScript names to try in order (the first that resolves wins; the
     *  later ones are older macOS faces for the same script). */
    private static final Object[][] TABLE = {
        { "zh-Hans",
          new Object[] { 400, "PingFangSC-Regular", "HiraginoSansGB-W3", "STHeitiSC-Light" },
          new Object[] { 500, "PingFangSC-Medium" },
          new Object[] { 600, "PingFangSC-Semibold", "HiraginoSansGB-W6", "STHeitiSC-Medium" } },
        { "zh-Hant,zh-Bopo",
          new Object[] { 400, "PingFangTC-Regular", "STHeitiTC-Light" },
          new Object[] { 500, "PingFangTC-Medium" },
          new Object[] { 600, "PingFangTC-Semibold", "STHeitiTC-Medium" } },
        { "ja",
          new Object[] { 400, "HiraginoSans-W3", "HiraKakuProN-W3" },
          new Object[] { 600, "HiraginoSans-W6", "HiraKakuProN-W6" } },
        { "ko",
          new Object[] { 400, "AppleSDGothicNeo-Regular", "AppleGothic" },
          new Object[] { 500, "AppleSDGothicNeo-Medium" },
          new Object[] { 700, "AppleSDGothicNeo-Bold" } },
    };

    /** A resolved face: the host file, which face of it, and the ASCII name it is
     *  published under in /system/fonts. */
    public static final class Face {
        public final int weight;
        public final String psName;
        public final File file;
        public int index = -1;        // set by indexAll(); -1 = not in the file
        public String linkName;       // set by Families.assignLinkNames()
        Face(int weight, String psName, File file) {
            this.weight = weight; this.psName = psName; this.file = file;
        }
    }

    public static final class Family {
        public final String lang;
        /** The published faces, one per weight; filled by indexAll(). */
        public final List<Face> faces = new ArrayList<>();
        /** Per weight, every candidate the resolver found a file for, in
         *  preference order; indexAll() publishes the first drawable one. */
        final List<List<Face>> candidates = new ArrayList<>();
        Family(String lang) { this.lang = lang; }
    }

    /** The families with at least one candidate face on this host, in the
     *  device's order. Cheap (one resolver call and one stat per candidate name,
     *  no file reads), so a warm launch can key on the result. Never throws; an
     *  empty list when disabled or nothing resolves. */
    public static List<Family> resolve(Function<String, String> psToPath) {
        List<Family> out = new ArrayList<>();
        if (!ENABLED || psToPath == null) return out;
        for (Object[] row : TABLE) {
            Family fam = new Family((String) row[0]);
            for (int r = 1; r < row.length; r++) {
                Object[] w = (Object[]) row[r];
                List<Face> cands = new ArrayList<>();
                for (int c = 1; c < w.length; c++) {
                    String ps = (String) w[c];
                    String path;
                    try { path = psToPath.apply(ps); } catch (Throwable t) { path = null; }
                    if (path == null || path.isEmpty()) continue;
                    File f = new File(path);
                    if (f.isFile()) cands.add(new Face((Integer) w[0], ps, f));
                }
                if (!cands.isEmpty()) fam.candidates.add(cands);
            }
            if (!fam.candidates.isEmpty()) out.add(fam);
        }
        return out;
    }

    /** The identity of the resolved set, for MacApkSystemFonts' directory key:
     *  every candidate's PostScript name, path, size and mtime (the choice among
     *  them is a function of those files, so it needs no file reads here). A
     *  macOS update that moves or replaces a file yields a new key, so a
     *  published directory never needs to change. */
    public static String identity(List<Family> fams) {
        StringBuilder b = new StringBuilder();
        for (Family fam : fams) {
            b.append(fam.lang).append('\n');
            for (List<Face> cands : fam.candidates) {
                for (Face f : cands) {
                    b.append(f.weight).append(' ').append(f.psName).append(' ')
                     .append(f.file.getPath()).append(' ').append(f.file.length()).append(' ')
                     .append(f.file.lastModified()).append('\n');
                }
            }
        }
        return b.toString();
    }

    /** Per weight, publishes the first candidate whose file really holds that
     *  face with drawable outlines (a resolver answer that disagrees with the
     *  file's own name table, or an hvgl-only face, falls through to the next),
     *  then names the links. Reads only table directories and name tables. */
    public static void indexAll(List<Family> fams) {
        Map<File, String> linkFor = new LinkedHashMap<>();
        for (Family fam : fams) {
            fam.faces.clear();
            for (List<Face> cands : fam.candidates) {
                for (Face f : cands) {
                    try { f.index = faceIndex(f.file, f.psName); } catch (Throwable t) { f.index = -1; }
                    if (f.index < 0) continue;
                    String link = linkFor.get(f.file);
                    if (link == null) {
                        link = "Host-" + ascii(f.psName) + extension(f.file.getName());
                        linkFor.put(f.file, link);
                    }
                    f.linkName = link;
                    fam.faces.add(f);
                    break;
                }
            }
        }
        fams.removeIf(fam -> fam.faces.isEmpty());
    }

    private static String ascii(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                     || c == '-' || c == '_' ? c : '_');
        }
        return b.toString();
    }

    private static String extension(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".ttc")) return ".ttc";
        if (n.endsWith(".otc")) return ".otc";
        if (n.endsWith(".otf")) return ".otf";
        return ".ttf";
    }

    // ---------------------------------------------------------------
    // Collection index from the file's own tables (OpenType spec: "Font
    // Collections" for the 'ttcf' header, "Table Directory" for the sfnt header,
    // "name" for the naming table; nameID 6 is the PostScript name). CoreText has
    // no public API for a face's index inside a collection, and Skia's fonts.xml
    // needs one (the <font index="N"> attribute, SkFontMgr_android_parser.cpp).
    // ---------------------------------------------------------------

    /** Index of the face whose PostScript name is psName and whose outlines a
     *  guest FreeType can read, or -1. A plain sfnt (one face) answers 0 when it
     *  matches. */
    public static int faceIndex(File file, String psName) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            int tag = f.readInt();
            if (tag == 0x74746366) {            // 'ttcf'
                f.readInt();                     // version
                int n = f.readInt();
                if (n <= 0 || n > 1024) return -1;
                long[] offs = new long[n];
                for (int i = 0; i < n; i++) offs[i] = f.readInt() & 0xffffffffL;
                for (int i = 0; i < n; i++) {
                    if (psName.equals(drawablePostScriptName(f, offs[i]))) return i;
                }
                return -1;
            }
            return psName.equals(drawablePostScriptName(f, 0)) ? 0 : -1;
        }
    }

    /** nameID 6 of the sfnt whose table directory starts at off, preferring the
     *  Windows Unicode record, then the Mac Roman one; null when absent or when
     *  the face has no 'glyf', 'CFF ' or 'CFF2' outlines (an hvgl-only face). */
    static String drawablePostScriptName(RandomAccessFile f, long off) throws IOException {
        f.seek(off + 4);
        int numTables = f.readUnsignedShort();
        if (numTables <= 0 || numTables > 512) return null;
        long nameOff = -1;
        boolean outlines = false;
        for (int t = 0; t < numTables; t++) {
            f.seek(off + 12 + 16L * t);
            int tag = f.readInt();
            f.readInt();                         // checksum
            long o = f.readInt() & 0xffffffffL;
            if (tag == 0x6e616d65) nameOff = o;                       // 'name'
            if (tag == 0x676c7966 || tag == 0x43464620 || tag == 0x43464632)
                outlines = true;                                      // 'glyf' 'CFF ' 'CFF2'
        }
        if (nameOff < 0 || !outlines) return null;
        f.seek(nameOff);
        f.readUnsignedShort();                   // format
        int count = f.readUnsignedShort();
        int strOff = f.readUnsignedShort();
        String mac = null;
        for (int r = 0; r < count; r++) {
            f.seek(nameOff + 6 + 12L * r);
            int platform = f.readUnsignedShort();
            int encoding = f.readUnsignedShort();
            f.readUnsignedShort();               // language
            int nameId = f.readUnsignedShort();
            int len = f.readUnsignedShort();
            int so = f.readUnsignedShort();
            if (nameId != 6 || len == 0 || len > 512) continue;
            byte[] raw = new byte[len];
            f.seek(nameOff + strOff + so);
            f.readFully(raw);
            if ((platform == 3 && (encoding == 1 || encoding == 10)) || platform == 0) {
                return new String(raw, StandardCharsets.UTF_16BE);
            }
            if (platform == 1 && encoding == 0 && mac == null) {
                mac = new String(raw, StandardCharsets.ISO_8859_1);   // PS names are ASCII
            }
        }
        return mac;
    }
}
