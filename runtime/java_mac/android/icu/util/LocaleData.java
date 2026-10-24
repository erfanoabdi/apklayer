// android.icu.util.LocaleData -- per-locale CLDR data: measurement system, paper
// size, exemplar sets, quotation delimiters, locale display pattern.
//
// AOSP: external/icu/android_icu4j/.../android/icu/util/LocaleData.java, i.e.
// ICU4J's com.ibm.icu.util.LocaleData repackaged (public API since API 24).
//
// WHY. GitHub #14: the MapLibre Compose demo picks map units with
//   LocaleData.getMeasurementSystem(ULocale.forLocale(locale))
// and its Compose layout died on NoClassDefFoundError: android/icu/util/LocaleData,
// leaving the window blank. The answers come from CLDR supplementalData
// (measurementData, paperSize) keyed by the region ICU infers for the locale
// (the rg keyword, else the region subtag, else likely subtags: "en" -> US,
// "my" -> MM). The JDK exposes none of that, so every lookup goes to the host's
// own ICU4C (macOS libicucore) through com.macapk.icu.HostIcu: decision ladder
// rung 1, the same engine and data family as Android's ICU4J. Nothing here is
// hard-coded; a missing bridge throws UnsupportedOperationException, loudly.
package android.icu.util;

import android.icu.text.UnicodeSet;

import com.macapk.icu.HostIcu;

public final class LocaleData {
    public static final int ES_STANDARD = 0;
    public static final int ES_AUXILIARY = 1;
    public static final int ES_INDEX = 2;
    /** @deprecated The currency exemplar set is no longer in CLDR. */
    @Deprecated public static final int ES_CURRENCY = 3;
    public static final int ES_PUNCTUATION = 4;
    /** @deprecated */
    @Deprecated public static final int ES_COUNT = 5;

    public static final int QUOTATION_START = 0;
    public static final int QUOTATION_END = 1;
    public static final int ALT_QUOTATION_START = 2;
    public static final int ALT_QUOTATION_END = 3;
    /** @deprecated */
    @Deprecated public static final int DELIMITER_COUNT = 4;

    private final ULocale locale;
    private boolean noSubstitute;

    private LocaleData(ULocale locale) {
        this.locale = locale;
    }

    public static UnicodeSet getExemplarSet(ULocale locale, int options) {
        return getInstance(locale).getExemplarSet(options, ES_STANDARD);
    }

    public static UnicodeSet getExemplarSet(ULocale locale, int options, int extype) {
        return getInstance(locale).getExemplarSet(options, extype);
    }

    /**
     * ICU4J: the set as the locale data defines it (IGNORE_SPACE always added to
     * the pattern options); root's set when the locale has none, or null under
     * noSubstitute; IllegalArgumentException for an extype outside 0..4.
     */
    public UnicodeSet getExemplarSet(int options, int extype) {
        if (extype < ES_STANDARD || extype > ES_PUNCTUATION) {
            throw new IllegalArgumentException(new ArrayIndexOutOfBoundsException(extype));
        }
        if (extype == ES_CURRENCY) {
            return noSubstitute ? null : new UnicodeSet();
        }
        // ICU4C numbers PUNCTUATION 3: it never had the currency slot Java keeps.
        int c4 = extype == ES_PUNCTUATION ? 3 : extype;
        Object[] items = HostIcu.exemplarSet(locale.getName(), noSubstitute, options, c4);
        if (items == null) return noSubstitute ? null : new UnicodeSet();
        UnicodeSet set = new UnicodeSet();
        int[] ranges = (int[]) items[0];
        for (int i = 0; i + 1 < ranges.length; i += 2) set.add(ranges[i], ranges[i + 1]);
        for (Object s : (Object[]) items[1]) set.add((String) s);
        return set;
    }

    public static final LocaleData getInstance(ULocale locale) {
        return new LocaleData(locale != null ? locale : ULocale.getDefault(ULocale.Category.FORMAT));
    }

    public static final LocaleData getInstance() {
        return getInstance(ULocale.getDefault(ULocale.Category.FORMAT));
    }

    public void setNoSubstitute(boolean setting) {
        noSubstitute = setting;
    }

    public boolean getNoSubstitute() {
        return noSubstitute;
    }

    public String getDelimiter(int type) {
        if (type < QUOTATION_START || type > ALT_QUOTATION_END) {
            throw new ArrayIndexOutOfBoundsException(type);
        }
        return HostIcu.delimiter(locale.getName(), noSubstitute, type);
    }

    public static final class MeasurementSystem {
        public static final MeasurementSystem SI = new MeasurementSystem();
        public static final MeasurementSystem US = new MeasurementSystem();
        public static final MeasurementSystem UK = new MeasurementSystem();

        private MeasurementSystem() {}

        @Override
        public String toString() {
            if (this == SI) return "SI";
            if (this == US) return "US";
            if (this == UK) return "UK";
            return "Unknown MeasurementSystem: " + super.toString();
        }
    }

    public static final MeasurementSystem getMeasurementSystem(ULocale locale) {
        switch (HostIcu.measurementSystem(locale.getName())) {
            case 0: return MeasurementSystem.SI;
            case 1: return MeasurementSystem.US;
            case 2: return MeasurementSystem.UK;
            default: return null;
        }
    }

    public static final class PaperSize {
        private final int height;
        private final int width;

        private PaperSize(int h, int w) {
            height = h;
            width = w;
        }

        public int getHeight() {
            return height;
        }

        public int getWidth() {
            return width;
        }
    }

    /** In millimetres, e.g. A4 = 297 x 210, US Letter = 279 x 216. */
    public static final PaperSize getPaperSize(ULocale locale) {
        int[] hw = HostIcu.paperSize(locale.getName());
        return new PaperSize(hw[0], hw[1]);
    }

    /** e.g. "{0} ({1})". */
    public String getLocaleDisplayPattern() {
        return HostIcu.localeDisplayPattern(locale.getName());
    }

    /** The part of the CLDR separator pattern between {0} and {1}, e.g. ", ". */
    public String getLocaleSeparator() {
        return HostIcu.localeSeparator(locale.getName());
    }

    private static volatile VersionInfo gCLDRVersion;

    public static VersionInfo getCLDRVersion() {
        VersionInfo v = gCLDRVersion;
        if (v == null) {
            int[] a = HostIcu.cldrVersion();
            v = VersionInfo.getInstance(a[0], a[1], a[2], a[3]);
            gCLDRVersion = v;
        }
        return v;
    }
}
