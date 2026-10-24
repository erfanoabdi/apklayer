// android.graphics.ColorSpace -- color-space descriptor introduced in API 26.
// AOSP:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/graphics/java/android/graphics/ColorSpace.java
//
// This mirrors AOSP's structure, not just its names: ColorSpace is an abstract
// base whose concrete implementations are the nested Rgb class plus the private
// Xyz / Lab classes, and the stock spaces returned by get(Named) are REAL
// instances carrying their own primaries, white point and transfer function.
// That matters because Rgb and Rgb.TransferParameters are public API that apps
// construct directly (BitmapFactory.Options.inPreferredColorSpace and
// ImageDecoder color management for wide-gamut / HDR assets), and because
// library code routinely tests `cs instanceof ColorSpace.Rgb` and then reads
// getPrimaries()/getTransform()/getTransferParameters(). Answering those with a
// single sRGB stub for every Named entry was a fabrication; the math below is
// AOSP's own, so the descriptive answers are now genuinely correct.
//
// What stays an approximation is the PIXEL path: Mac.apk decodes through
// ImageIO, which is sRGB, so nothing here performs gamut remapping on actual
// bitmap data. toXyz/fromXyz are exact, but a decoded Bitmap is still sRGB.
package android.graphics;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

public abstract class ColorSpace {
    /** The minimum ID value a color space can have. AOSP: "Do not change". */
    public static final int MIN_ID = -1;
    /** The maximum ID value a color space can have. AOSP: "Do not change,
     *  used to encode in longs". */
    public static final int MAX_ID = 63;

    // Standard CIE 1931 2-degree illuminants, encoded in xyY. Values verbatim
    // from AOSP ColorSpace.java:151-192.
    public static final float[] ILLUMINANT_A   = { 0.44757f, 0.40745f };
    public static final float[] ILLUMINANT_B   = { 0.34842f, 0.35161f };
    public static final float[] ILLUMINANT_C   = { 0.31006f, 0.31616f };
    public static final float[] ILLUMINANT_D50 = { 0.34567f, 0.35850f };
    public static final float[] ILLUMINANT_D55 = { 0.33242f, 0.34743f };
    public static final float[] ILLUMINANT_D60 = { 0.32168f, 0.33767f };
    public static final float[] ILLUMINANT_D65 = { 0.31271f, 0.32902f };
    public static final float[] ILLUMINANT_D75 = { 0.29902f, 0.31485f };
    public static final float[] ILLUMINANT_E   = { 0.33333f, 0.33333f };

    private static final float[] SRGB_PRIMARIES =
            { 0.640f, 0.330f, 0.300f, 0.600f, 0.150f, 0.060f };
    private static final float[] NTSC_1953_PRIMARIES =
            { 0.67f, 0.33f, 0.21f, 0.71f, 0.14f, 0.08f };
    private static final float[] DCI_P3_PRIMARIES =
            { 0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f };
    private static final float[] BT2020_PRIMARIES =
            { 0.708f, 0.292f, 0.170f, 0.797f, 0.131f, 0.046f };
    /** A gray color space has no meaningful primaries; AOSP uses this set. */
    private static final float[] GRAY_PRIMARIES = { 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f };

    private static final float[] ILLUMINANT_D50_XYZ = { 0.964212f, 1.0f, 0.825188f };

    /** AOSP Named enum -- one entry per stock color space. The ORDINAL is the
     *  color space's id (AOSP builds its table with Named.X.ordinal()), so the
     *  order here is load-bearing and must stay in AOSP's order. */
    public enum Named {
        SRGB,
        LINEAR_SRGB,
        EXTENDED_SRGB,
        LINEAR_EXTENDED_SRGB,
        BT709,
        BT2020,
        DCI_P3,
        DISPLAY_P3,
        NTSC_1953,
        SMPTE_C,
        ADOBE_RGB,
        PRO_PHOTO_RGB,
        ACES,
        ACESCG,
        CIE_XYZ,
        CIE_LAB,
        // ⚠ ORDER CORRECTED 2026-08-24 to match AOSP (ColorSpace.java:733-808):
        // BT2020_HLG is 16 and BT2020_PQ is 17. We had them the other way round,
        // and the ordinal IS the color space id (getId() / getDataSpace() /
        // get(int) all key off it), so every one of those answered wrong for
        // these two spaces.
        BT2020_HLG,
        BT2020_PQ,
        // API 36, both behind AOSP feature flags (ok_lab_colorspace /
        // display_bt2020_colorspace). We build them unconditionally: a device
        // with the flag off throws from get(Named), and an app that names the
        // constant at all has already decided it wants the space.
        OK_LAB,
        DISPLAY_BT2020
    }

    /** Model -- the color-model family, each with its component count. */
    public enum Model {
        RGB(3),
        XYZ(3),
        LAB(3),
        CMYK(4);

        private final int mComponentCount;

        Model(int componentCount) { mComponentCount = componentCount; }

        public int getComponentCount() { return mComponentCount; }
    }

    private final String mName;
    private final Model  mModel;
    private final int    mId;

    /** AOSP's package-private constructor (ColorSpace.java:978). Apps cannot
     *  subclass ColorSpace on real Android; only the framework's own Rgb / Xyz
     *  / Lab do. */
    ColorSpace(String name, Model model, int id) {
        if (name == null || name.length() < 1) {
            throw new IllegalArgumentException("The name of a color space cannot be null and "
                    + "must contain at least 1 character");
        }
        if (model == null) {
            throw new IllegalArgumentException("A color space must have a model");
        }
        if (id < MIN_ID || id > MAX_ID) {
            throw new IllegalArgumentException("The id must be between "
                    + MIN_ID + " and " + MAX_ID);
        }
        mName = name;
        mModel = model;
        mId = id;
    }

    /** Legacy Mac.apk argument order, kept so any pre-existing subclass in the
     *  tree keeps compiling. Delegates to the AOSP-shaped constructor above. */
    protected ColorSpace(String name, int id, Model model) {
        this(name, model, id);
    }

    public String getName()  { return mName;  }
    public int    getId()    { return mId;    }
    public Model  getModel() { return mModel; }

    public int getComponentCount() { return mModel.getComponentCount(); }

    /** True only for the sRGB color space itself. Rgb overrides this with a
     *  real primaries/white-point/transfer-function comparison. */
    public boolean isSrgb() { return false; }

    /** AOSP ColorSpace.getDataSpace() (platform_frameworks_base main,
     *  graphics/java/android/graphics/ColorSpace.java:1530-1537), fetched
     *  2026-08-12:
     *
     *      public @ColorDataSpace int getDataSpace() {
     *          int index = sDataToColorSpaces.indexOfValue(getId());
     *          if (index != -1) {
     *              return sDataToColorSpaces.keyAt(index);
     *          } else {
     *              return DataSpace.DATASPACE_UNKNOWN;
     *          }
     *      }
     *
     *  sDataToColorSpaces is a SparseIntArray AOSP builds alongside
     *  sNamedColorSpaces in its static initializer (DataSpace int ->
     *  Named.ordinal()), and this reverse-looks-up by value: given this
     *  space's id, find the DataSpace key that maps to it.
     *
     *  Ported as a direct comparison against getId() (== the Named ordinal,
     *  the exact value the real map keys off -- see "the ORDINAL is the
     *  color space's id" note on the Named enum above) instead of adding a
     *  second SparseIntArray-backed map alongside sNamedColorSpaces for one
     *  getter: same input/output contract, no new field, no restructuring
     *  of the existing static block. android.hardware.DataSpace does not
     *  exist in this tree yet, so its DATASPACE_* values are inlined below
     *  rather than referenced -- each is a packed STANDARD|TRANSFER|RANGE
     *  triple (not a small ordinal), pasted verbatim from core/java/android/
     *  hardware/DataSpace.java (main, same fetch) rather than computed, per
     *  the struct-offset rule (never guess a stable ABI constant).
     *
     *  Only 11 of this class's 18 Named entries have a DataSpace -- AOSP's
     *  own table omits NTSC_1953, SMPTE_C, PRO_PHOTO_RGB, ACES, ACESCG,
     *  CIE_XYZ and CIE_LAB (there is no HAL buffer dataspace for them
     *  either), so those fall through to DATASPACE_UNKNOWN exactly like
     *  real Android does.
     *
     *  CROSS-REFERENCE, read before editing either side: the 11 packed
     *  integers below are a COPY of constants owned by
     *  frameworks/base/core/java/android/hardware/DataSpace.java (main),
     *  duplicated here only because that class has no port in this tree
     *  (grepped runtime/java/android/hardware/ on 2026-08-12: absent). If
     *  android.hardware.DataSpace is ever added under
     *  runtime/java/android/hardware/DataSpace.java, this method becomes a
     *  SECOND SOURCE OF TRUTH for the same 11 values -- switch this method
     *  to reference DataSpace.DATASPACE_* directly (as AOSP's own getDataSpace()
     *  does) and delete the inlined copies below, rather than leaving two
     *  places that can silently drift apart. */
    public int getDataSpace() {
        int id = getId();
        if (id == Named.SRGB.ordinal())                 return 142671872; // DATASPACE_SRGB
        if (id == Named.LINEAR_SRGB.ordinal())           return 138477568; // DATASPACE_SRGB_LINEAR
        if (id == Named.EXTENDED_SRGB.ordinal())         return 411107328; // DATASPACE_SCRGB
        if (id == Named.LINEAR_EXTENDED_SRGB.ordinal())  return 406913024; // DATASPACE_SCRGB_LINEAR
        if (id == Named.BT709.ordinal())                 return 281083904; // DATASPACE_BT709
        if (id == Named.BT2020.ordinal())                return 147193856; // DATASPACE_BT2020
        if (id == Named.DCI_P3.ordinal())                return 155844608; // DATASPACE_DCI_P3
        if (id == Named.DISPLAY_P3.ordinal())            return 143261696; // DATASPACE_DISPLAY_P3
        if (id == Named.ADOBE_RGB.ordinal())             return 151715840; // DATASPACE_ADOBE_RGB
        if (id == Named.BT2020_PQ.ordinal())             return 163971072; // DATASPACE_BT2020_PQ
        if (id == Named.BT2020_HLG.ordinal())            return 168165376; // DATASPACE_BT2020_HLG
        // DATASPACE_DISPLAY_BT2020 = STANDARD_BT2020 (6<<16) | TRANSFER_SRGB (2<<22)
        // | RANGE_FULL (1<<27). Derived from the same packing the eleven values
        // above use, and cross-checked against two of them: BT2020 = 393216 +
        // (3<<22) + 134217728 = 147193856, SRGB = 65536 + 8388608 + 134217728 =
        // 142671872. Both reproduce the constants already in this table.
        if (id == Named.DISPLAY_BT2020.ordinal())         return 142999552; // DATASPACE_DISPLAY_BT2020
        return 0; // DataSpace.DATASPACE_UNKNOWN
    }

    public abstract boolean isWideGamut();

    public abstract float getMinValue(int component);
    public abstract float getMaxValue(int component);

    /** Converts a color from this space to CIE XYZ (D50-relative), in place. */
    public abstract float[] toXyz(float[] v);
    /** Converts a CIE XYZ (D50-relative) color into this space, in place. */
    public abstract float[] fromXyz(float[] v);

    public final float[] toXyz(float r, float g, float b) {
        return toXyz(new float[] { r, g, b });
    }

    public final float[] fromXyz(float x, float y, float z) {
        float[] xyz = new float[mModel.getComponentCount()];
        xyz[0] = x;
        xyz[1] = y;
        xyz[2] = z;
        return fromXyz(xyz);
    }

    @Override
    public String toString() {
        return mName + " (id=" + mId + ", model=" + mModel + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ColorSpace that = (ColorSpace) o;
        if (mId != that.mId) return false;
        if (!mName.equals(that.mName)) return false;
        return mModel == that.mModel;
    }

    @Override
    public int hashCode() {
        int result = mName.hashCode();
        result = 31 * result + mModel.hashCode();
        result = 31 * result + mId;
        return result;
    }

    // ---- stock color spaces -------------------------------------------------
    // Built in Named order because Rgb's mIsSrgb computation looks up
    // get(Named.SRGB); AOSP's static block has the same ordering requirement.
    private static final ColorSpace[] sNamedColorSpaces = new ColorSpace[Named.values().length];

    private static final Rgb.TransferParameters SRGB_TRANSFER_PARAMETERS =
            new Rgb.TransferParameters(1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4);

    private static final Rgb.TransferParameters SMPTE_170M_TRANSFER_PARAMETERS =
            new Rgb.TransferParameters(1 / 1.099, 0.099 / 1.099, 1 / 4.5, 0.081, 1 / 0.45);

    /** AOSP ColorSpace.java:228 -- Skia packs the HLG curve into the seven ICC
     *  slots and flags it with the TYPE_HLGish sentinel g. */
    private static final Rgb.TransferParameters BT2020_HLG_TRANSFER_PARAMETERS =
            new Rgb.TransferParameters(2.0, 2.0, 1 / 0.17883277, 0.28466892, 0.55991073,
                    -0.685490157, Rgb.TransferParameters.TYPE_HLGish);

    /** AOSP ColorSpace.java:233 -- PQ transfer with an SDR whitepoint of 203 nits. */
    private static final Rgb.TransferParameters BT2020_PQ_TRANSFER_PARAMETERS =
            new Rgb.TransferParameters(-1.555223, 1.860454, 32 / 2523.0, 2413 / 128.0,
                    -2392 / 128.0, 8192 / 1305.0, Rgb.TransferParameters.TYPE_PQish);

    // AOSP ColorSpace.java:1776-1828. Skia's packing, unpacked back out.
    private static double transferHLGOETF(Rgb.TransferParameters params, double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x *= sign;
        final double R = 1.0 / params.a;
        final double G = 1.0 / params.b;
        final double a = 1.0 / params.c;
        final double b = params.d;
        final double c = params.e;
        final double K = params.f + 1.0;
        x /= K;
        return sign * (x <= 1 ? R * Math.pow(x, G) : a * Math.log(x - b) + c);
    }

    private static double transferHLGEOTF(Rgb.TransferParameters params, double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x *= sign;
        final double R = params.a;
        final double G = params.b;
        final double a = params.c;
        final double b = params.d;
        final double c = params.e;
        final double K = params.f + 1.0;
        return K * sign * (x * R <= 1 ? Math.pow(x * R, G) : Math.exp((x - c) * a) + b);
    }

    private static double transferST2048OETF(Rgb.TransferParameters params, double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x *= sign;
        double a = -params.a;
        double b = params.d;
        double c = 1.0 / params.f;
        double d = params.b;
        double e = -params.e;
        double f = 1.0 / params.c;
        double tmp = Math.max(a + b * Math.pow(x, c), 0);
        return sign * Math.pow(tmp / (d + e * Math.pow(x, c)), f);
    }

    private static double transferST2048EOTF(Rgb.TransferParameters pq, double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x *= sign;
        double tmp = Math.max(pq.a + pq.b * Math.pow(x, pq.c), 0);
        return sign * Math.pow(tmp / (pq.d + pq.e * Math.pow(x, pq.c)), pq.f);
    }

    static {
        sNamedColorSpaces[Named.SRGB.ordinal()] = new Rgb(
                "sRGB IEC61966-2.1",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                null,
                SRGB_TRANSFER_PARAMETERS,
                Named.SRGB.ordinal());
        sNamedColorSpaces[Named.LINEAR_SRGB.ordinal()] = new Rgb(
                "sRGB IEC61966-2.1 (Linear)",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                1.0,
                0.0f, 1.0f,
                Named.LINEAR_SRGB.ordinal());
        sNamedColorSpaces[Named.EXTENDED_SRGB.ordinal()] = new Rgb(
                "scRGB-nl IEC 61966-2-2:2003",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                null,
                x -> absRcpResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4),
                x -> absResponse(x, 1 / 1.055, 0.055 / 1.055, 1 / 12.92, 0.04045, 2.4),
                -0.799f, 2.399f,
                SRGB_TRANSFER_PARAMETERS,
                Named.EXTENDED_SRGB.ordinal());
        sNamedColorSpaces[Named.LINEAR_EXTENDED_SRGB.ordinal()] = new Rgb(
                "scRGB IEC 61966-2-2:2003",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                1.0,
                -0.5f, 7.499f,
                Named.LINEAR_EXTENDED_SRGB.ordinal());
        sNamedColorSpaces[Named.BT709.ordinal()] = new Rgb(
                "Rec. ITU-R BT.709-5",
                SRGB_PRIMARIES,
                ILLUMINANT_D65,
                null,
                SMPTE_170M_TRANSFER_PARAMETERS,
                Named.BT709.ordinal());
        sNamedColorSpaces[Named.BT2020.ordinal()] = new Rgb(
                "Rec. ITU-R BT.2020-1",
                BT2020_PRIMARIES,
                ILLUMINANT_D65,
                null,
                new Rgb.TransferParameters(1 / 1.0993, 0.0993 / 1.0993, 1 / 4.5, 0.08145, 1 / 0.45),
                Named.BT2020.ordinal());
        sNamedColorSpaces[Named.DCI_P3.ordinal()] = new Rgb(
                "SMPTE RP 431-2-2007 DCI (P3)",
                DCI_P3_PRIMARIES,
                new float[] { 0.314f, 0.351f },
                2.6,
                0.0f, 1.0f,
                Named.DCI_P3.ordinal());
        sNamedColorSpaces[Named.DISPLAY_P3.ordinal()] = new Rgb(
                "Display P3",
                DCI_P3_PRIMARIES,
                ILLUMINANT_D65,
                null,
                SRGB_TRANSFER_PARAMETERS,
                Named.DISPLAY_P3.ordinal());
        sNamedColorSpaces[Named.NTSC_1953.ordinal()] = new Rgb(
                "NTSC (1953)",
                NTSC_1953_PRIMARIES,
                ILLUMINANT_C,
                null,
                SMPTE_170M_TRANSFER_PARAMETERS,
                Named.NTSC_1953.ordinal());
        sNamedColorSpaces[Named.SMPTE_C.ordinal()] = new Rgb(
                "SMPTE-C RGB",
                new float[] { 0.630f, 0.340f, 0.310f, 0.595f, 0.155f, 0.070f },
                ILLUMINANT_D65,
                null,
                SMPTE_170M_TRANSFER_PARAMETERS,
                Named.SMPTE_C.ordinal());
        sNamedColorSpaces[Named.ADOBE_RGB.ordinal()] = new Rgb(
                "Adobe RGB (1998)",
                new float[] { 0.64f, 0.33f, 0.21f, 0.71f, 0.15f, 0.06f },
                ILLUMINANT_D65,
                2.2,
                0.0f, 1.0f,
                Named.ADOBE_RGB.ordinal());
        sNamedColorSpaces[Named.PRO_PHOTO_RGB.ordinal()] = new Rgb(
                "ROMM RGB ISO 22028-2:2013",
                new float[] { 0.7347f, 0.2653f, 0.1596f, 0.8404f, 0.0366f, 0.0001f },
                ILLUMINANT_D50,
                null,
                new Rgb.TransferParameters(1.0, 0.0, 1 / 16.0, 0.031248, 1.8),
                Named.PRO_PHOTO_RGB.ordinal());
        sNamedColorSpaces[Named.ACES.ordinal()] = new Rgb(
                "SMPTE ST 2065-1:2012 ACES",
                new float[] { 0.73470f, 0.26530f, 0.0f, 1.0f, 0.00010f, -0.0770f },
                ILLUMINANT_D60,
                1.0,
                -65504.0f, 65504.0f,
                Named.ACES.ordinal());
        sNamedColorSpaces[Named.ACESCG.ordinal()] = new Rgb(
                "Academy S-2014-004 ACEScg",
                new float[] { 0.713f, 0.293f, 0.165f, 0.830f, 0.128f, 0.044f },
                ILLUMINANT_D60,
                1.0,
                -65504.0f, 65504.0f,
                Named.ACESCG.ordinal());
        sNamedColorSpaces[Named.CIE_XYZ.ordinal()] = new Xyz(
                "Generic XYZ",
                Named.CIE_XYZ.ordinal());
        sNamedColorSpaces[Named.CIE_LAB.ordinal()] = new Lab(
                "Generic L*a*b*",
                Named.CIE_LAB.ordinal());
        // These four were declared in Named but NEVER built, so get(Named.BT2020_PQ)
        // and friends returned null and every caller NPE'd. AOSP builds all four in
        // the same static block (ColorSpace.java:1730-1773).
        sNamedColorSpaces[Named.BT2020_HLG.ordinal()] = new Rgb(
                "Hybrid Log Gamma encoding",
                BT2020_PRIMARIES,
                ILLUMINANT_D65,
                null,
                x -> transferHLGOETF(BT2020_HLG_TRANSFER_PARAMETERS, x),
                x -> transferHLGEOTF(BT2020_HLG_TRANSFER_PARAMETERS, x),
                0.0f, 1.0f,
                BT2020_HLG_TRANSFER_PARAMETERS,
                Named.BT2020_HLG.ordinal());
        sNamedColorSpaces[Named.BT2020_PQ.ordinal()] = new Rgb(
                "Perceptual Quantizer encoding",
                BT2020_PRIMARIES,
                ILLUMINANT_D65,
                null,
                x -> transferST2048OETF(BT2020_PQ_TRANSFER_PARAMETERS, x),
                x -> transferST2048EOTF(BT2020_PQ_TRANSFER_PARAMETERS, x),
                0.0f, 1.0f,
                BT2020_PQ_TRANSFER_PARAMETERS,
                Named.BT2020_PQ.ordinal());
        sNamedColorSpaces[Named.OK_LAB.ordinal()] = new OkLab(
                "Oklab",
                Named.OK_LAB.ordinal());
        sNamedColorSpaces[Named.DISPLAY_BT2020.ordinal()] = new Rgb(
                "Display BT. 2020",
                BT2020_PRIMARIES,
                ILLUMINANT_D65,
                null,
                SRGB_TRANSFER_PARAMETERS,
                Named.DISPLAY_BT2020.ordinal());
    }

    public static ColorSpace get(Named named) {
        return sNamedColorSpaces[named.ordinal()];
    }

    /** AOSP get(int id) looks the space up by its id, which is the Named
     *  ordinal. An unknown id is a programming error there; we fall back to
     *  sRGB rather than throwing, because an out-of-range id here means our
     *  Named table is short of AOSP's, not that the app is wrong. */
    public static ColorSpace get(int id) {
        if (id >= 0 && id < sNamedColorSpaces.length && sNamedColorSpaces[id] != null) {
            return sNamedColorSpaces[id];
        }
        return sNamedColorSpaces[Named.SRGB.ordinal()];
    }

    // ---- Adaptation / RenderIntent / connect / adapt (AOSP ColorSpace.java) ----

    /** AOSP ColorSpace.RenderIntent (ColorSpace.java:820). Order is ABI
     *  (ordinal() is observable): PERCEPTUAL, RELATIVE, SATURATION, ABSOLUTE.
     *  PERCEPTUAL and SATURATION are documented as "not implemented and behaves
     *  like RELATIVE" on real Android too, so only ABSOLUTE changes the math. */
    public enum RenderIntent {
        PERCEPTUAL,
        RELATIVE,
        SATURATION,
        ABSOLUTE
    }

    /** AOSP ColorSpace.Adaptation (ColorSpace.java:893) -- the XYZ-to-LMS
     *  matrices used by the von Kries chromatic-adaptation transform. Matrices
     *  verbatim from AOSP. */
    public enum Adaptation {
        BRADFORD(new float[] {
                 0.8951f, -0.7502f,  0.0389f,
                 0.2664f,  1.7135f, -0.0685f,
                -0.1614f,  0.0367f,  1.0296f
        }),
        VON_KRIES(new float[] {
                 0.40024f, -0.22630f, 0.00000f,
                 0.70760f,  1.16532f, 0.00000f,
                -0.08081f,  0.04570f, 0.91822f
        }),
        CIECAT02(new float[] {
                 0.7328f, -0.7036f,  0.0030f,
                 0.4296f,  1.6975f,  0.0136f,
                -0.1624f,  0.0061f,  0.9834f
        });

        final float[] mTransform;

        Adaptation(float[] transform) { mTransform = transform; }
    }

    /** AOSP ColorSpace.connect(source, destination) -- defaults to PERCEPTUAL. */
    public static Connector connect(ColorSpace source, ColorSpace destination) {
        return connect(source, destination, RenderIntent.PERCEPTUAL);
    }

    /** AOSP ColorSpace.connect(source, destination, intent) (ColorSpace.java:1327). */
    public static Connector connect(ColorSpace source, ColorSpace destination,
            RenderIntent intent) {
        if (source.equals(destination)) return Connector.identity(source);
        if (source.getModel() == Model.RGB && destination.getModel() == Model.RGB) {
            return new Connector.Rgb((Rgb) source, (Rgb) destination, intent);
        }
        return new Connector(source, destination, intent);
    }

    /** AOSP ColorSpace.connect(source) -- destination is sRGB. */
    public static Connector connect(ColorSpace source) {
        return connect(source, RenderIntent.PERCEPTUAL);
    }

    /** AOSP ColorSpace.connect(source, intent) (ColorSpace.java:1380). */
    public static Connector connect(ColorSpace source, RenderIntent intent) {
        if (source.isSrgb()) return Connector.identity(source);
        if (source.getModel() == Model.RGB) {
            return new Connector.Rgb((Rgb) source, (Rgb) get(Named.SRGB), intent);
        }
        return new Connector(source, get(Named.SRGB), intent);
    }

    /** AOSP ColorSpace.adapt(colorSpace, whitePoint) -- Bradford by default. */
    public static ColorSpace adapt(ColorSpace colorSpace, float[] whitePoint) {
        return adapt(colorSpace, whitePoint, Adaptation.BRADFORD);
    }

    /** AOSP ColorSpace.adapt(colorSpace, whitePoint, adaptation)
     *  (ColorSpace.java:1438). Non-RGB spaces are returned unchanged. */
    public static ColorSpace adapt(ColorSpace colorSpace, float[] whitePoint,
            Adaptation adaptation) {
        if (colorSpace.getModel() == Model.RGB) {
            ColorSpace.Rgb rgb = (ColorSpace.Rgb) colorSpace;
            if (compare(rgb.mWhitePoint, whitePoint)) return colorSpace;

            float[] xyz = whitePoint.length == 3
                    ? java.util.Arrays.copyOf(whitePoint, 3) : xyYToXyz(whitePoint);
            float[] adaptationTransform = chromaticAdaptation(adaptation.mTransform,
                    xyYToXyz(rgb.getWhitePoint()), xyz);
            float[] transform = mul3x3(adaptationTransform, rgb.mTransform);

            return new ColorSpace.Rgb(rgb, transform, whitePoint);
        }
        return colorSpace;
    }

    /** AOSP ColorSpace.cctToXyz(cct) (ColorSpace.java:2060) -- Kim et al.'s
     *  cubic approximation of the Planckian locus, then xyY -> XYZ. */
    public static float[] cctToXyz(int cct) {
        if (cct < 1) {
            throw new IllegalArgumentException("Temperature must be greater than 0");
        }
        final float icct = 1e3f / cct;
        final float icct2 = icct * icct;
        final float x = cct <= 4000.0f
            ? 0.179910f + 0.8776956f * icct - 0.2343589f * icct2 - 0.2661239f * icct2 * icct
            : 0.240390f + 0.2226347f * icct + 2.1070379f * icct2 - 3.0258469f * icct2 * icct;

        final float x2 = x * x;
        final float y = cct <= 2222.0f
            ? -0.20219683f + 2.18555832f * x - 1.34811020f * x2 - 1.1063814f * x2 * x
            : cct <= 4000.0f
            ? -0.16748867f + 2.09137015f * x - 1.37418593f * x2 - 0.9549476f * x2 * x
            : -0.37001483f + 3.75112997f * x - 5.8733867f * x2 + 3.0817580f * x2 * x;

        return xyYToXyz(new float[] { x, y });
    }

    /** AOSP ColorSpace.chromaticAdaptation(adaptation, src, dst)
     *  (ColorSpace.java:2097). White points may be xy (2 floats) or XYZ (3). */
    public static float[] chromaticAdaptation(Adaptation adaptation,
            float[] srcWhitePoint, float[] dstWhitePoint) {
        if ((srcWhitePoint.length != 2 && srcWhitePoint.length != 3)
                || (dstWhitePoint.length != 2 && dstWhitePoint.length != 3)) {
            throw new IllegalArgumentException("A white point array must have 2 or 3 floats");
        }
        float[] srcXyz = srcWhitePoint.length == 3
                ? java.util.Arrays.copyOf(srcWhitePoint, 3) : xyYToXyz(srcWhitePoint);
        float[] dstXyz = dstWhitePoint.length == 3
                ? java.util.Arrays.copyOf(dstWhitePoint, 3) : xyYToXyz(dstWhitePoint);

        if (compare(srcXyz, dstXyz)) {
            return new float[] {
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f
            };
        }
        return chromaticAdaptation(adaptation.mTransform, srcXyz, dstXyz);
    }

    /** AOSP ColorSpace.getFromDataSpace(dataSpace) (ColorSpace.java:1511).
     *  AOSP keeps an explicit SparseIntArray built alongside the named table;
     *  ours inverts getDataSpace() over that same table, which yields the same
     *  answers because getDataSpace() is the only thing that populated it.
     *  Returns null for an unmapped value, including DATASPACE_UNKNOWN (0). */
    public static ColorSpace getFromDataSpace(int dataSpace) {
        if (dataSpace == 0) return null;
        for (int i = 0; i < sNamedColorSpaces.length; i++) {
            ColorSpace cs = sNamedColorSpaces[i];
            if (cs != null && cs.getDataSpace() == dataSpace) return cs;
        }
        return null;
    }

    /** AOSP ColorSpace.match(toXYZD50, function) (ColorSpace.java:1578) -- finds
     *  the named RGB space whose D50-adapted transform and transfer function both
     *  match, or null. */
    public static ColorSpace match(float[] toXYZD50, Rgb.TransferParameters function) {
        for (int i = 0; i < sNamedColorSpaces.length; i++) {
            ColorSpace colorSpace = sNamedColorSpaces[i];
            if (colorSpace != null && colorSpace.getModel() == Model.RGB) {
                ColorSpace.Rgb rgb = (ColorSpace.Rgb) adapt(colorSpace, ILLUMINANT_D50_XYZ);
                if (compare(toXYZD50, rgb.mTransform)
                        && compare(function, rgb.mTransferParameters)) {
                    return colorSpace;
                }
            }
        }
        return null;
    }

    // ---- shared math (AOSP ColorSpace.java:1831-2010) ------------------------

    /** ICC parametric curve type 3 EOTF: Y = (aX + b)^g for X >= d, cX below. */
    private static double response(double x, double a, double b, double c, double d, double g) {
        return x >= d ? Math.pow(a * x + b, g) : c * x;
    }

    /** Type 4 EOTF: the type 3 curve plus the e / f offsets. */
    private static double response(double x, double a, double b, double c, double d,
            double e, double f, double g) {
        return x >= d ? Math.pow(a * x + b, g) + e : c * x + f;
    }

    /** Inverse of response(): the OETF. */
    private static double rcpResponse(double x, double a, double b, double c, double d, double g) {
        return x >= d * c ? (Math.pow(x, 1.0 / g) - b) / a : x / c;
    }

    private static double rcpResponse(double x, double a, double b, double c, double d,
            double e, double f, double g) {
        return x >= d * c ? (Math.pow(x - e, 1.0 / g) - b) / a : (x - f) / c;
    }

    /** Sign-preserving variants, used by the extended-range sRGB spaces where
     *  the curve must be mirrored through the origin. */
    private static double absResponse(double x, double a, double b, double c, double d, double g) {
        return Math.copySign(response(x < 0.0 ? -x : x, a, b, c, d, g), x);
    }

    private static double absRcpResponse(double x, double a, double b, double c, double d,
            double g) {
        return Math.copySign(rcpResponse(x < 0.0 ? -x : x, a, b, c, d, g), x);
    }

    private static float[] inverse3x3(float[] m) {
        float a = m[0];
        float b = m[3];
        float c = m[6];
        float d = m[1];
        float e = m[4];
        float f = m[7];
        float g = m[2];
        float h = m[5];
        float i = m[8];

        float A = e * i - f * h;
        float B = f * g - d * i;
        float C = d * h - e * g;

        float det = a * A + b * B + c * C;

        float[] inverted = new float[m.length];
        inverted[0] = A / det;
        inverted[1] = B / det;
        inverted[2] = C / det;
        inverted[3] = (c * h - b * i) / det;
        inverted[4] = (a * i - c * g) / det;
        inverted[5] = (b * g - a * h) / det;
        inverted[6] = (b * f - c * e) / det;
        inverted[7] = (c * d - a * f) / det;
        inverted[8] = (a * e - b * d) / det;
        return inverted;
    }

    /** Multiplies a 3-vector by a column-major 3x3 matrix, in place. */
    private static float[] mul3x3Float3(float[] lhs, float[] rhs) {
        float r0 = rhs[0];
        float r1 = rhs[1];
        float r2 = rhs[2];
        rhs[0] = lhs[0] * r0 + lhs[3] * r1 + lhs[6] * r2;
        rhs[1] = lhs[1] * r0 + lhs[4] * r1 + lhs[7] * r2;
        rhs[2] = lhs[2] * r0 + lhs[5] * r1 + lhs[8] * r2;
        return rhs;
    }

    /** AOSP mul3x3 -- column-major 3x3 * 3x3. */
    private static float[] mul3x3(float[] lhs, float[] rhs) {
        float[] r = new float[9];
        r[0] = lhs[0] * rhs[0] + lhs[3] * rhs[1] + lhs[6] * rhs[2];
        r[1] = lhs[1] * rhs[0] + lhs[4] * rhs[1] + lhs[7] * rhs[2];
        r[2] = lhs[2] * rhs[0] + lhs[5] * rhs[1] + lhs[8] * rhs[2];
        r[3] = lhs[0] * rhs[3] + lhs[3] * rhs[4] + lhs[6] * rhs[5];
        r[4] = lhs[1] * rhs[3] + lhs[4] * rhs[4] + lhs[7] * rhs[5];
        r[5] = lhs[2] * rhs[3] + lhs[5] * rhs[4] + lhs[8] * rhs[5];
        r[6] = lhs[0] * rhs[6] + lhs[3] * rhs[7] + lhs[6] * rhs[8];
        r[7] = lhs[1] * rhs[6] + lhs[4] * rhs[7] + lhs[7] * rhs[8];
        r[8] = lhs[2] * rhs[6] + lhs[5] * rhs[7] + lhs[8] * rhs[8];
        return r;
    }

    /** AOSP mul3x3Diag -- a diagonal matrix (stored as 3 floats) times a 3x3. */
    private static float[] mul3x3Diag(float[] lhs, float[] rhs) {
        return new float[] {
                lhs[0] * rhs[0], lhs[1] * rhs[1], lhs[2] * rhs[2],
                lhs[0] * rhs[3], lhs[1] * rhs[4], lhs[2] * rhs[5],
                lhs[0] * rhs[6], lhs[1] * rhs[7], lhs[2] * rhs[8]
        };
    }

    /** AOSP xyYToXyz -- an xy chromaticity to XYZ at Y = 1. */
    private static float[] xyYToXyz(float[] xyY) {
        return new float[] { xyY[0] / xyY[1], 1.0f, (1 - xyY[0] - xyY[1]) / xyY[1] };
    }

    /** AOSP chromaticAdaptation(matrix, srcWhitePoint, dstWhitePoint)
     *  (ColorSpace.java:2035) -- the von Kries transform A^-1 . D . A. */
    private static float[] chromaticAdaptation(float[] matrix,
            float[] srcWhitePoint, float[] dstWhitePoint) {
        float[] srcLMS = mul3x3Float3(matrix, srcWhitePoint);
        float[] dstLMS = mul3x3Float3(matrix, dstWhitePoint);
        float[] LMS = { dstLMS[0] / srcLMS[0], dstLMS[1] / srcLMS[1], dstLMS[2] / srcLMS[2] };
        return mul3x3(inverse3x3(matrix), mul3x3Diag(LMS, matrix));
    }

    /** AOSP compare(TransferParameters, TransferParameters) (ColorSpace.java:1873).
     *  Note d gets 2e-3 rather than 1e-3 -- AOSP's own "special case for
     *  variations in sRGB OETF/EOTF". */
    private static boolean compare(Rgb.TransferParameters a, Rgb.TransferParameters b) {
        if (a == null && b == null) return true;
        return a != null && b != null
                && Math.abs(a.a - b.a) < 1e-3
                && Math.abs(a.b - b.b) < 1e-3
                && Math.abs(a.c - b.c) < 1e-3
                && Math.abs(a.d - b.d) < 2e-3
                && Math.abs(a.e - b.e) < 1e-3
                && Math.abs(a.f - b.f) < 1e-3
                && Math.abs(a.g - b.g) < 1e-3;
    }

    /** AOSP ColorSpace.clamp(x, min, max) (ColorSpace.java:2236). */
    private static float macapkClamp(float x, float min, float max) {
        return x < min ? min : x > max ? max : x;
    }

    /** Approximate float-array comparison; AOSP uses it to decide sRGB-ness. */
    private static boolean compare(float[] a, float[] b) {
        if (a == b) return true;
        for (int i = 0; i < a.length; i++) {
            if (Float.compare(a[i], b[i]) != 0 && Math.abs(a[i] - b[i]) > 1e-3f) return false;
        }
        return true;
    }

    // ---- Xyz / Lab (AOSP keeps these private; only get(Named) hands them out) -

    private static final class Xyz extends ColorSpace {
        private Xyz(String name, int id) { super(name, Model.XYZ, id); }

        @Override public boolean isWideGamut() { return true; }
        @Override public float getMinValue(int component) { return -2.0f; }
        @Override public float getMaxValue(int component) { return 2.0f; }

        @Override public float[] toXyz(float[] v) {
            v[0] = clamp(v[0]);
            v[1] = clamp(v[1]);
            v[2] = clamp(v[2]);
            return v;
        }

        @Override public float[] fromXyz(float[] v) {
            v[0] = clamp(v[0]);
            v[1] = clamp(v[1]);
            v[2] = clamp(v[2]);
            return v;
        }

        private static float clamp(float x) {
            return x < -2.0f ? -2.0f : x > 2.0f ? 2.0f : x;
        }
    }

    private static final class Lab extends ColorSpace {
        private static final float A = 216.0f / 24389.0f;
        private static final float B = 841.0f / 108.0f;
        private static final float C = 4.0f / 29.0f;
        private static final float D = 6.0f / 29.0f;

        private Lab(String name, int id) { super(name, Model.LAB, id); }

        @Override public boolean isWideGamut() { return true; }
        @Override public float getMinValue(int component) {
            return component == 0 ? 0.0f : -128.0f;
        }
        @Override public float getMaxValue(int component) {
            return component == 0 ? 100.0f : 128.0f;
        }

        @Override public float[] toXyz(float[] v) {
            v[0] = clamp(v[0], 0.0f, 100.0f);
            v[1] = clamp(v[1], -128.0f, 128.0f);
            v[2] = clamp(v[2], -128.0f, 128.0f);

            float fy = (v[0] + 16.0f) / 116.0f;
            float fx = fy + (v[1] * 0.002f);
            float fz = fy - (v[2] * 0.005f);
            float X = fx > D ? fx * fx * fx : (1.0f / B) * (fx - C);
            float Y = fy > D ? fy * fy * fy : (1.0f / B) * (fy - C);
            float Z = fz > D ? fz * fz * fz : (1.0f / B) * (fz - C);

            v[0] = X * ILLUMINANT_D50_XYZ[0];
            v[1] = Y * ILLUMINANT_D50_XYZ[1];
            v[2] = Z * ILLUMINANT_D50_XYZ[2];
            return v;
        }

        @Override public float[] fromXyz(float[] v) {
            float X = v[0] / ILLUMINANT_D50_XYZ[0];
            float Y = v[1] / ILLUMINANT_D50_XYZ[1];
            float Z = v[2] / ILLUMINANT_D50_XYZ[2];

            float fx = X > A ? (float) Math.pow(X, 1.0 / 3.0) : B * X + C;
            float fy = Y > A ? (float) Math.pow(Y, 1.0 / 3.0) : B * Y + C;
            float fz = Z > A ? (float) Math.pow(Z, 1.0 / 3.0) : B * Z + C;

            float L = 116.0f * fy - 16.0f;
            float a = 500.0f * (fx - fy);
            float b = 200.0f * (fy - fz);

            v[0] = clamp(L, 0.0f, 100.0f);
            v[1] = clamp(a, -128.0f, 128.0f);
            v[2] = clamp(b, -128.0f, 128.0f);
            return v;
        }

        private static float clamp(float x, float min, float max) {
            return x < min ? min : x > max ? max : x;
        }
    }

    /** AOSP ColorSpace.OkLab (ColorSpace.java:2244) -- Bjorn Ottosson's Oklab,
     *  a perceptual Lab space with a D65 white point. Private in AOSP too; only
     *  get(Named.OK_LAB) hands it out. */
    private static final class OkLab extends ColorSpace {
        private OkLab(String name, int id) { super(name, Model.LAB, id); }

        @Override public boolean isWideGamut() { return true; }
        @Override public float getMinValue(int component) {
            return component == 0 ? 0.0f : -0.5f;
        }
        @Override public float getMaxValue(int component) {
            return component == 0 ? 1.0f : 0.5f;
        }

        @Override public float[] toXyz(float[] v) {
            v[0] = macapkClamp(v[0], 0.0f, 1.0f);
            v[1] = macapkClamp(v[1], -0.5f, 0.5f);
            v[2] = macapkClamp(v[2], -0.5f, 0.5f);

            mul3x3Float3(INVERSE_M2, v);
            v[0] = v[0] * v[0] * v[0];
            v[1] = v[1] * v[1] * v[1];
            v[2] = v[2] * v[2] * v[2];

            mul3x3Float3(INVERSE_M1, v);
            return v;
        }

        @Override public float[] fromXyz(float[] v) {
            mul3x3Float3(M1, v);
            v[0] = (float) Math.cbrt(v[0]);
            v[1] = (float) Math.cbrt(v[1]);
            v[2] = (float) Math.cbrt(v[2]);
            mul3x3Float3(M2, v);
            return v;
        }

        // XYZ(D65) -> LMS. AOSP folds the D50 -> D65 Bradford adaptation into it
        // because ColorSpace's PCS is D50-relative.
        private static final float[] M1TMP = {
                0.8189330101f, 0.0329845436f, 0.0482003018f,
                0.3618667424f, 0.9293118715f, 0.2643662691f,
                -0.1288597137f, 0.0361456387f, 0.6338517070f
        };
        private static final float[] M1 = mul3x3(
                M1TMP,
                chromaticAdaptation(Adaptation.BRADFORD, ILLUMINANT_D50, ILLUMINANT_D65));
        private static final float[] M2 = {
                0.2104542553f, 1.9779984951f, 0.0259040371f,
                0.7936177850f, -2.4285922050f, 0.7827717662f,
                -0.0040720468f, 0.4505937099f, -0.8086757660f
        };
        private static final float[] INVERSE_M1 = inverse3x3(M1);
        private static final float[] INVERSE_M2 = inverse3x3(M2);
    }

    // ---- ColorSpace.Rgb -----------------------------------------------------

    /** An RGB color space defined by primaries, a white point and a transfer
     *  function. This is the class apps actually construct
     *  (`new ColorSpace.Rgb(name, primaries, whitePoint, transferParameters)`)
     *  and the one library code instanceof-tests before reading the transform.
     *  Signatures and math verbatim from AOSP ColorSpace.java:2461-3600. */
    public static class Rgb extends ColorSpace {

        /** The ICC parametric curve parameters. Seven public final doubles by
         *  AOSP contract (ColorSpace.java:2486-2504); apps read them directly. */
        public static class TransferParameters {

            // Skia packs the PQ / HLG curves into the same 7 slots and flags
            // them with these sentinel g values, so they bypass the ICC range
            // validation below.
            private static final double TYPE_PQish  = -2.0;
            private static final double TYPE_HLGish = -3.0;

            public final double a;
            public final double b;
            public final double c;
            public final double d;
            public final double e;
            public final double f;
            public final double g;

            private static boolean isSpecialG(double g) {
                return g == TYPE_PQish || g == TYPE_HLGish;
            }

            /** ICC parametric curve type 3; equivalent to setting e and f to 0. */
            public TransferParameters(double a, double b, double c, double d, double g) {
                this(a, b, c, d, 0.0, 0.0, g);
            }

            /** ICC parametric curve type 4 (ICC.1:2004-10 section 10.15). */
            public TransferParameters(double a, double b, double c, double d, double e,
                    double f, double g) {
                if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c)
                        || Double.isNaN(d) || Double.isNaN(e) || Double.isNaN(f)
                        || Double.isNaN(g)) {
                    throw new IllegalArgumentException("Parameters cannot be NaN");
                }
                if (!isSpecialG(g)) {
                    // AOSP tolerates one ulp past 1.0f because the native
                    // representation of these parameters is float, not double.
                    if (!(d >= 0.0 && d <= 1.0f + Math.ulp(1.0f))) {
                        throw new IllegalArgumentException(
                                "Parameter d must be in the range [0..1], " + "was " + d);
                    }
                    if (d == 0.0 && (a == 0.0 || g == 0.0)) {
                        throw new IllegalArgumentException(
                                "Parameter a or g is zero, the transfer function is constant");
                    }
                    if (d >= 1.0 && c == 0.0) {
                        throw new IllegalArgumentException(
                                "Parameter c is zero, the transfer function is constant");
                    }
                    if ((a == 0.0 || g == 0.0) && c == 0.0) {
                        throw new IllegalArgumentException("Parameter a or g is zero,"
                                + " and c is zero, the transfer function is constant");
                    }
                    if (c < 0.0) {
                        throw new IllegalArgumentException(
                                "The transfer function must be increasing");
                    }
                    if (a < 0.0 || g < 0.0) {
                        throw new IllegalArgumentException(
                                "The transfer function must be positive or increasing");
                    }
                }
                this.a = a;
                this.b = b;
                this.c = c;
                this.d = d;
                this.e = e;
                this.f = f;
                this.g = g;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                TransferParameters that = (TransferParameters) o;

                if (Double.compare(that.a, a) != 0) return false;
                if (Double.compare(that.b, b) != 0) return false;
                if (Double.compare(that.c, c) != 0) return false;
                if (Double.compare(that.d, d) != 0) return false;
                if (Double.compare(that.e, e) != 0) return false;
                if (Double.compare(that.f, f) != 0) return false;
                return Double.compare(that.g, g) == 0;
            }

            @Override
            public int hashCode() {
                int result;
                long temp;
                temp = Double.doubleToLongBits(a);
                result = (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(b);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(c);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(d);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(e);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(f);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                temp = Double.doubleToLongBits(g);
                result = 31 * result + (int) (temp ^ (temp >>> 32));
                return result;
            }
        }

        private final float[] mWhitePoint;
        private final float[] mPrimaries;
        private final float[] mTransform;
        private final float[] mInverseTransform;

        private final DoubleUnaryOperator mOetf;
        private final DoubleUnaryOperator mEotf;
        private final DoubleUnaryOperator mClampedOetf;
        private final DoubleUnaryOperator mClampedEotf;

        private final float mMin;
        private final float mMax;

        private final boolean mIsWideGamut;
        private final boolean mIsSrgb;

        private final TransferParameters mTransferParameters;

        public Rgb(String name, float[] toXYZ, DoubleUnaryOperator oetf,
                DoubleUnaryOperator eotf) {
            this(name, computePrimaries(toXYZ), computeWhitePoint(toXYZ), null,
                    oetf, eotf, 0.0f, 1.0f, null, MIN_ID);
        }

        public Rgb(String name, float[] primaries, float[] whitePoint,
                DoubleUnaryOperator oetf, DoubleUnaryOperator eotf, float min, float max) {
            this(name, primaries, whitePoint, null, oetf, eotf, min, max, null, MIN_ID);
        }

        public Rgb(String name, float[] toXYZ, TransferParameters function) {
            // AOSP note: when isGray() is false this passes a null transform for
            // consistency with the other constructors, which derive the matrix
            // from the primaries and white point.
            this(name, isGray(toXYZ) ? GRAY_PRIMARIES : computePrimaries(toXYZ),
                    computeWhitePoint(toXYZ), isGray(toXYZ) ? toXYZ : null, function, MIN_ID);
        }

        public Rgb(String name, float[] primaries, float[] whitePoint,
                TransferParameters function) {
            this(name, primaries, whitePoint, null, function, MIN_ID);
        }

        public Rgb(String name, float[] toXYZ, double gamma) {
            this(name, computePrimaries(toXYZ), computeWhitePoint(toXYZ), gamma,
                    0.0f, 1.0f, MIN_ID);
        }

        public Rgb(String name, float[] primaries, float[] whitePoint, double gamma) {
            this(name, primaries, whitePoint, gamma, 0.0f, 1.0f, MIN_ID);
        }

        private Rgb(String name, float[] primaries, float[] whitePoint, float[] transform,
                TransferParameters function, int id) {
            this(name, primaries, whitePoint, transform,
                    generateOETF(function), generateEOTF(function),
                    0.0f, 1.0f, function, id);
        }

        private Rgb(String name, float[] primaries, float[] whitePoint, double gamma,
                float min, float max, int id) {
            this(name, primaries, whitePoint, null,
                    gamma == 1.0 ? DoubleUnaryOperator.identity()
                                 : x -> Math.pow(x < 0.0 ? 0.0 : x, 1 / gamma),
                    gamma == 1.0 ? DoubleUnaryOperator.identity()
                                 : x -> Math.pow(x < 0.0 ? 0.0 : x, gamma),
                    min, max, new TransferParameters(1.0, 0.0, 0.0, 0.0, gamma), id);
        }

        private Rgb(String name, float[] primaries, float[] whitePoint, float[] transform,
                DoubleUnaryOperator oetf, DoubleUnaryOperator eotf, float min, float max,
                TransferParameters transferParameters, int id) {

            super(name, Model.RGB, id);

            if (primaries == null || (primaries.length != 6 && primaries.length != 9)) {
                throw new IllegalArgumentException("The color space's primaries must be "
                        + "defined as an array of 6 floats in xyY or 9 floats in XYZ");
            }
            if (whitePoint == null || (whitePoint.length != 2 && whitePoint.length != 3)) {
                throw new IllegalArgumentException("The color space's white point must be "
                        + "defined as an array of 2 floats in xyY or 3 float in XYZ");
            }
            if (oetf == null || eotf == null) {
                throw new IllegalArgumentException("The transfer functions of a color space "
                        + "cannot be null");
            }
            if (min >= max) {
                throw new IllegalArgumentException("Invalid range: min=" + min + ", max=" + max
                        + "; min must be strictly < max");
            }

            mWhitePoint = xyWhitePoint(whitePoint);
            mPrimaries = xyPrimaries(primaries);

            if (transform == null) {
                mTransform = computeXYZMatrix(mPrimaries, mWhitePoint);
            } else {
                if (transform.length != 9) {
                    throw new IllegalArgumentException("Transform must have 9 entries! Has "
                            + transform.length);
                }
                mTransform = transform;
            }
            mInverseTransform = inverse3x3(mTransform);

            mOetf = oetf;
            mEotf = eotf;

            mMin = min;
            mMax = max;

            DoubleUnaryOperator clamp = this::clamp;
            mClampedOetf = oetf.andThen(clamp);
            mClampedEotf = clamp.andThen(eotf);

            mTransferParameters = transferParameters;

            // Wide gamut if the primaries cover >90% of NTSC 1953 and fully
            // contain sRGB, or if the range itself extends past [0..1].
            mIsWideGamut = isWideGamut(mPrimaries, min, max);
            mIsSrgb = isSrgb(mPrimaries, mWhitePoint, oetf, eotf, min, max, id);
        }

        public float[] getWhitePoint(float[] whitePoint) {
            whitePoint[0] = mWhitePoint[0];
            whitePoint[1] = mWhitePoint[1];
            return whitePoint;
        }

        public float[] getWhitePoint() {
            return Arrays.copyOf(mWhitePoint, mWhitePoint.length);
        }

        public float[] getPrimaries(float[] primaries) {
            System.arraycopy(mPrimaries, 0, primaries, 0, mPrimaries.length);
            return primaries;
        }

        public float[] getPrimaries() {
            return Arrays.copyOf(mPrimaries, mPrimaries.length);
        }

        public float[] getTransform(float[] transform) {
            System.arraycopy(mTransform, 0, transform, 0, mTransform.length);
            return transform;
        }

        public float[] getTransform() {
            return Arrays.copyOf(mTransform, mTransform.length);
        }

        public float[] getInverseTransform(float[] inverseTransform) {
            System.arraycopy(mInverseTransform, 0, inverseTransform, 0, mInverseTransform.length);
            return inverseTransform;
        }

        public float[] getInverseTransform() {
            return Arrays.copyOf(mInverseTransform, mInverseTransform.length);
        }

        /** The OETF, clamped to this space's range (linear -> "gamma space"). */
        public DoubleUnaryOperator getOetf() { return mClampedOetf; }

        /** The EOTF, clamped to this space's range ("gamma space" -> linear). */
        public DoubleUnaryOperator getEotf() { return mClampedEotf; }

        /** Null when the transfer functions do not match the ICC parametric
         *  curves, which is what AOSP returns for a caller-supplied
         *  DoubleUnaryOperator pair. */
        public TransferParameters getTransferParameters() { return mTransferParameters; }

        /** AOSP ColorSpace.Rgb private constructor (ColorSpace.java:3178), used
         *  ONLY by adapt(): same primaries / transfer functions / range as the
         *  source, a caller-supplied chromatically-adapted transform and white
         *  point, and MIN_ID because the result is no longer a named space. */
        private Rgb(Rgb colorSpace, float[] transform, float[] whitePoint) {
            this(colorSpace.getName(), colorSpace.mPrimaries, whitePoint, transform,
                    colorSpace.mOetf, colorSpace.mEotf, colorSpace.mMin, colorSpace.mMax,
                    colorSpace.mTransferParameters, MIN_ID);
        }

        /** AOSP ColorSpace.Rgb.toLinear(r,g,b) (ColorSpace.java:3462). */
        public float[] toLinear(float r, float g, float b) {
            return toLinear(new float[] { r, g, b });
        }

        /** AOSP ColorSpace.Rgb.toLinear(float[]) -- applies the CLAMPED EOTF
         *  IN PLACE and returns the same array the caller passed. */
        public float[] toLinear(float[] v) {
            v[0] = (float) mClampedEotf.applyAsDouble(v[0]);
            v[1] = (float) mClampedEotf.applyAsDouble(v[1]);
            v[2] = (float) mClampedEotf.applyAsDouble(v[2]);
            return v;
        }

        /** AOSP ColorSpace.Rgb.fromLinear(r,g,b) (ColorSpace.java:3511). */
        public float[] fromLinear(float r, float g, float b) {
            return fromLinear(new float[] { r, g, b });
        }

        /** AOSP ColorSpace.Rgb.fromLinear(float[]) -- clamped OETF, in place. */
        public float[] fromLinear(float[] v) {
            v[0] = (float) mClampedOetf.applyAsDouble(v[0]);
            v[1] = (float) mClampedOetf.applyAsDouble(v[1]);
            v[2] = (float) mClampedOetf.applyAsDouble(v[2]);
            return v;
        }

        @Override public boolean isSrgb() { return mIsSrgb; }
        @Override public boolean isWideGamut() { return mIsWideGamut; }
        @Override public float getMinValue(int component) { return mMin; }
        @Override public float getMaxValue(int component) { return mMax; }

        @Override public float[] toXyz(float[] v) {
            v[0] = (float) mClampedEotf.applyAsDouble(v[0]);
            v[1] = (float) mClampedEotf.applyAsDouble(v[1]);
            v[2] = (float) mClampedEotf.applyAsDouble(v[2]);
            return mul3x3Float3(mTransform, v);
        }

        @Override public float[] fromXyz(float[] v) {
            mul3x3Float3(mInverseTransform, v);
            v[0] = (float) mClampedOetf.applyAsDouble(v[0]);
            v[1] = (float) mClampedOetf.applyAsDouble(v[1]);
            v[2] = (float) mClampedOetf.applyAsDouble(v[2]);
            return v;
        }

        private double clamp(double x) {
            return x < mMin ? mMin : x > mMax ? mMax : x;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            Rgb rgb = (Rgb) o;

            if (Float.compare(rgb.mMin, mMin) != 0) return false;
            if (Float.compare(rgb.mMax, mMax) != 0) return false;
            if (!Arrays.equals(mWhitePoint, rgb.mWhitePoint)) return false;
            if (!Arrays.equals(mPrimaries, rgb.mPrimaries)) return false;
            if (mTransferParameters != null) {
                return mTransferParameters.equals(rgb.mTransferParameters);
            } else if (rgb.mTransferParameters == null) {
                return true;
            }
            if (!mOetf.equals(rgb.mOetf)) return false;
            return mEotf.equals(rgb.mEotf);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + Arrays.hashCode(mWhitePoint);
            result = 31 * result + Arrays.hashCode(mPrimaries);
            result = 31 * result + (mMin != +0.0f ? Float.floatToIntBits(mMin) : 0);
            result = 31 * result + (mMax != +0.0f ? Float.floatToIntBits(mMax) : 0);
            result = 31 * result
                    + (mTransferParameters != null ? mTransferParameters.hashCode() : 0);
            if (mTransferParameters == null) {
                result = 31 * result + mOetf.hashCode();
                result = 31 * result + mEotf.hashCode();
            }
            return result;
        }

        private static DoubleUnaryOperator generateOETF(TransferParameters function) {
            return function.e == 0.0 && function.f == 0.0
                    ? x -> rcpResponse(x, function.a, function.b,
                            function.c, function.d, function.g)
                    : x -> rcpResponse(x, function.a, function.b, function.c,
                            function.d, function.e, function.f, function.g);
        }

        private static DoubleUnaryOperator generateEOTF(TransferParameters function) {
            return function.e == 0.0 && function.f == 0.0
                    ? x -> response(x, function.a, function.b,
                            function.c, function.d, function.g)
                    : x -> response(x, function.a, function.b, function.c,
                            function.d, function.e, function.f, function.g);
        }

        private static boolean isSrgb(float[] primaries, float[] whitePoint,
                DoubleUnaryOperator OETF, DoubleUnaryOperator EOTF,
                float min, float max, int id) {
            if (id == 0) return true;
            if (!ColorSpace.compare(primaries, SRGB_PRIMARIES)) return false;
            if (!ColorSpace.compare(whitePoint, ILLUMINANT_D65)) return false;
            if (min != 0.0f) return false;
            if (max != 1.0f) return false;

            // Safe to reference sRGB here: id == 0 already returned above, so
            // the table entry for sRGB is populated by the time we get here.
            Rgb srgb = (Rgb) get(Named.SRGB);

            for (double x = 0.0; x <= 1.0; x += 1 / 255.0) {
                if (!compare(x, OETF, srgb.mOetf)) return false;
                if (!compare(x, EOTF, srgb.mEotf)) return false;
            }
            return true;
        }

        private static boolean compare(double point, DoubleUnaryOperator a, DoubleUnaryOperator b) {
            double rA = a.applyAsDouble(point);
            double rB = b.applyAsDouble(point);
            return Math.abs(rA - rB) <= 1e-3;
        }

        private static boolean isWideGamut(float[] primaries, float min, float max) {
            return (area(primaries) / area(NTSC_1953_PRIMARIES) > 0.9f
                    && contains(primaries, SRGB_PRIMARIES)) || (min < 0.0f && max > 1.0f);
        }

        /** Area of the triangle the primaries describe in CIE xyY. */
        private static float area(float[] primaries) {
            float Rx = primaries[0];
            float Ry = primaries[1];
            float Gx = primaries[2];
            float Gy = primaries[3];
            float Bx = primaries[4];
            float By = primaries[5];
            float det = Rx * Gy + Ry * Bx + Gx * By - Gy * Bx - Ry * Gx - Rx * By;
            float r = 0.5f * det;
            return r < 0.0f ? -r : r;
        }

        private static float cross(float ax, float ay, float bx, float by) {
            return ax * by - ay * bx;
        }

        /** True when the triangle p1 fully contains the triangle p2. */
        private static boolean contains(float[] p1, float[] p2) {
            // Translate p1's vertices into the coordinate system whose origin
            // is p2's vertices.
            float[] p0 = new float[] {
                    p1[0] - p2[0], p1[1] - p2[1],
                    p1[2] - p2[2], p1[3] - p2[3],
                    p1[4] - p2[4], p1[5] - p2[5],
            };
            if (cross(p0[0], p0[1], p2[0] - p2[4], p2[1] - p2[5]) < 0
                    || cross(p2[0] - p2[2], p2[1] - p2[3], p0[0], p0[1]) < 0) {
                return false;
            }
            if (cross(p0[2], p0[3], p2[2] - p2[0], p2[3] - p2[1]) < 0
                    || cross(p2[2] - p2[4], p2[3] - p2[5], p0[2], p0[3]) < 0) {
                return false;
            }
            if (cross(p0[4], p0[5], p2[4] - p2[2], p2[5] - p2[3]) < 0
                    || cross(p2[4] - p2[0], p2[5] - p2[1], p0[4], p0[5]) < 0) {
                return false;
            }
            return true;
        }

        /** True when the RGB->XYZ matrix is diagonal, i.e. a gray space. */
        private static boolean isGray(float[] toXYZ) {
            return toXYZ.length == 9 && toXYZ[1] == 0 && toXYZ[2] == 0 && toXYZ[3] == 0
                    && toXYZ[5] == 0 && toXYZ[6] == 0 && toXYZ[7] == 0;
        }

        private static float[] computePrimaries(float[] toXYZ) {
            float[] r = mul3x3Float3(toXYZ, new float[] { 1.0f, 0.0f, 0.0f });
            float[] g = mul3x3Float3(toXYZ, new float[] { 0.0f, 1.0f, 0.0f });
            float[] b = mul3x3Float3(toXYZ, new float[] { 0.0f, 0.0f, 1.0f });

            float rSum = r[0] + r[1] + r[2];
            float gSum = g[0] + g[1] + g[2];
            float bSum = b[0] + b[1] + b[2];

            return new float[] {
                    r[0] / rSum, r[1] / rSum,
                    g[0] / gSum, g[1] / gSum,
                    b[0] / bSum, b[1] / bSum,
            };
        }

        private static float[] computeWhitePoint(float[] toXYZ) {
            float[] w = mul3x3Float3(toXYZ, new float[] { 1.0f, 1.0f, 1.0f });
            float sum = w[0] + w[1] + w[2];
            return new float[] { w[0] / sum, w[1] / sum };
        }

        /** Normalizes primaries to xyY; a 9-float input is XYZ and is divided
         *  through by each primary's component sum. */
        private static float[] xyPrimaries(float[] primaries) {
            float[] xyPrimaries = new float[6];

            if (primaries.length == 9) {
                float sum;

                sum = primaries[0] + primaries[1] + primaries[2];
                xyPrimaries[0] = primaries[0] / sum;
                xyPrimaries[1] = primaries[1] / sum;

                sum = primaries[3] + primaries[4] + primaries[5];
                xyPrimaries[2] = primaries[3] / sum;
                xyPrimaries[3] = primaries[4] / sum;

                sum = primaries[6] + primaries[7] + primaries[8];
                xyPrimaries[4] = primaries[6] / sum;
                xyPrimaries[5] = primaries[7] / sum;
            } else {
                System.arraycopy(primaries, 0, xyPrimaries, 0, 6);
            }

            return xyPrimaries;
        }

        /** Same normalization for the white point (3 floats = XYZ). */
        private static float[] xyWhitePoint(float[] whitePoint) {
            float[] xyWhitePoint = new float[2];

            if (whitePoint.length == 3) {
                float sum = whitePoint[0] + whitePoint[1] + whitePoint[2];
                xyWhitePoint[0] = whitePoint[0] / sum;
                xyWhitePoint[1] = whitePoint[1] / sum;
            } else {
                System.arraycopy(whitePoint, 0, xyWhitePoint, 0, 2);
            }

            return xyWhitePoint;
        }

        /** Derives the column-major RGB->XYZ matrix from xyY primaries and an
         *  xyY white point (Y implied to be 1). */
        private static float[] computeXYZMatrix(float[] primaries, float[] whitePoint) {
            float Rx = primaries[0];
            float Ry = primaries[1];
            float Gx = primaries[2];
            float Gy = primaries[3];
            float Bx = primaries[4];
            float By = primaries[5];
            float Wx = whitePoint[0];
            float Wy = whitePoint[1];

            float oneRxRy = (1 - Rx) / Ry;
            float oneGxGy = (1 - Gx) / Gy;
            float oneBxBy = (1 - Bx) / By;
            float oneWxWy = (1 - Wx) / Wy;

            float RxRy = Rx / Ry;
            float GxGy = Gx / Gy;
            float BxBy = Bx / By;
            float WxWy = Wx / Wy;

            float BY = ((oneWxWy - oneRxRy) * (GxGy - RxRy) - (WxWy - RxRy) * (oneGxGy - oneRxRy))
                     / ((oneBxBy - oneRxRy) * (GxGy - RxRy) - (BxBy - RxRy) * (oneGxGy - oneRxRy));
            float GY = (WxWy - RxRy - BY * (BxBy - RxRy)) / (GxGy - RxRy);
            float RY = 1 - GY - BY;

            float RYRy = RY / Ry;
            float GYGy = GY / Gy;
            float BYBy = BY / By;

            return new float[] {
                    RYRy * Rx, RY, RYRy * (1 - Rx - Ry),
                    GYGy * Gx, GY, GYGy * (1 - Gx - Gy),
                    BYBy * Bx, BY, BYBy * (1 - Bx - By)
            };
        }
    }

    // ---- ColorSpace.Connector (AOSP ColorSpace.java:4005) --------------------

    /** AOSP ColorSpace.Connector -- a one-way conversion from a source color
     *  space to a destination one. Obtained from ColorSpace.connect(). */
    public static class Connector {
        private final ColorSpace mSource;
        private final ColorSpace mDestination;
        private final ColorSpace mTransformSource;
        private final ColorSpace mTransformDestination;
        private final RenderIntent mIntent;
        private final float[] mTransform;

        Connector(ColorSpace source, ColorSpace destination, RenderIntent intent) {
            this(source, destination,
                    source.getModel() == Model.RGB ? adapt(source, ILLUMINANT_D50_XYZ) : source,
                    destination.getModel() == Model.RGB
                            ? adapt(destination, ILLUMINANT_D50_XYZ) : destination,
                    intent, computeTransform(source, destination, intent));
        }

        private Connector(ColorSpace source, ColorSpace destination,
                ColorSpace transformSource, ColorSpace transformDestination,
                RenderIntent intent, float[] transform) {
            mSource = source;
            mDestination = destination;
            mTransformSource = transformSource;
            mTransformDestination = transformDestination;
            mIntent = intent;
            mTransform = transform;
        }

        /** Only ABSOLUTE produces a whitepoint-scaling vector, and only when
         *  exactly one side is RGB. Everything else returns null (AOSP:4036). */
        private static float[] computeTransform(ColorSpace source,
                ColorSpace destination, RenderIntent intent) {
            if (intent != RenderIntent.ABSOLUTE) return null;

            boolean srcRGB = source.getModel() == Model.RGB;
            boolean dstRGB = destination.getModel() == Model.RGB;

            if (srcRGB && dstRGB) return null;

            if (srcRGB || dstRGB) {
                ColorSpace.Rgb rgb = (ColorSpace.Rgb) (srcRGB ? source : destination);
                float[] srcXYZ = srcRGB ? xyYToXyz(rgb.mWhitePoint) : ILLUMINANT_D50_XYZ;
                float[] dstXYZ = dstRGB ? xyYToXyz(rgb.mWhitePoint) : ILLUMINANT_D50_XYZ;
                return new float[] {
                        srcXYZ[0] / dstXYZ[0],
                        srcXYZ[1] / dstXYZ[1],
                        srcXYZ[2] / dstXYZ[2],
                };
            }
            return null;
        }

        public ColorSpace getSource() { return mSource; }
        public ColorSpace getDestination() { return mDestination; }
        public RenderIntent getRenderIntent() { return mIntent; }

        public float[] transform(float r, float g, float b) {
            return transform(new float[] { r, g, b });
        }

        /** Converts IN PLACE and returns the same array (AOSP does the same). */
        public float[] transform(float[] v) {
            float[] xyz = mTransformSource.toXyz(v);
            if (mTransform != null) {
                xyz[0] *= mTransform[0];
                xyz[1] *= mTransform[1];
                xyz[2] *= mTransform[2];
            }
            return mTransformDestination.fromXyz(xyz);
        }

        /** The RGB-to-RGB fast path: a single 3x3 in linear light, skipping XYZ
         *  entirely when the two white points agree (AOSP:4150). */
        private static class Rgb extends Connector {
            private final ColorSpace.Rgb mSource;
            private final ColorSpace.Rgb mDestination;
            private final float[] mTransform;

            Rgb(ColorSpace.Rgb source, ColorSpace.Rgb destination, RenderIntent intent) {
                super(source, destination, source, destination, intent, null);
                mSource = source;
                mDestination = destination;
                mTransform = computeTransform(source, destination, intent);
            }

            @Override public float[] transform(float[] rgb) {
                rgb[0] = (float) mSource.mClampedEotf.applyAsDouble(rgb[0]);
                rgb[1] = (float) mSource.mClampedEotf.applyAsDouble(rgb[1]);
                rgb[2] = (float) mSource.mClampedEotf.applyAsDouble(rgb[2]);
                mul3x3Float3(mTransform, rgb);
                rgb[0] = (float) mDestination.mClampedOetf.applyAsDouble(rgb[0]);
                rgb[1] = (float) mDestination.mClampedOetf.applyAsDouble(rgb[1]);
                rgb[2] = (float) mDestination.mClampedOetf.applyAsDouble(rgb[2]);
                return rgb;
            }

            private static float[] computeTransform(ColorSpace.Rgb source,
                    ColorSpace.Rgb destination, RenderIntent intent) {
                if (compare(source.mWhitePoint, destination.mWhitePoint)) {
                    // Same white point: use the PCS of both spaces directly.
                    return mul3x3(destination.mInverseTransform, source.mTransform);
                } else {
                    // Different white points: go through CIE XYZ D50 as the PCS.
                    float[] transform = source.mTransform;
                    float[] inverseTransform = destination.mInverseTransform;

                    float[] srcXYZ = xyYToXyz(source.mWhitePoint);
                    float[] dstXYZ = xyYToXyz(destination.mWhitePoint);

                    if (!compare(source.mWhitePoint, ILLUMINANT_D50)) {
                        float[] srcAdaptation = chromaticAdaptation(
                                Adaptation.BRADFORD.mTransform, srcXYZ,
                                java.util.Arrays.copyOf(ILLUMINANT_D50_XYZ, 3));
                        transform = mul3x3(srcAdaptation, source.mTransform);
                    }

                    if (!compare(destination.mWhitePoint, ILLUMINANT_D50)) {
                        float[] dstAdaptation = chromaticAdaptation(
                                Adaptation.BRADFORD.mTransform, dstXYZ,
                                java.util.Arrays.copyOf(ILLUMINANT_D50_XYZ, 3));
                        inverseTransform =
                                inverse3x3(mul3x3(dstAdaptation, destination.mTransform));
                    }

                    if (intent == RenderIntent.ABSOLUTE) {
                        transform = mul3x3Diag(
                                new float[] {
                                        srcXYZ[0] / dstXYZ[0],
                                        srcXYZ[1] / dstXYZ[1],
                                        srcXYZ[2] / dstXYZ[2],
                                }, transform);
                    }

                    return mul3x3(inverseTransform, transform);
                }
            }
        }

        static Connector identity(ColorSpace source) {
            return new Connector(source, source, RenderIntent.RELATIVE) {
                @Override public float[] transform(float[] v) { return v; }
            };
        }
    }
}
