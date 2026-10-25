// com.macapk.icu.HostIcu -- the host's ICU (macOS libicucore) for the parts of
// android.icu.* that need CLDR data the JDK does not carry.
//
// WHY. android.icu.* on Mac.apk is a front over java.text / java.util, which
// share CLDR locale data with ICU for numbers, dates and collation. Unit
// formatting is the exception: the JDK ships no CLDR unit patterns, so
// "5 kilometer" (nl, WIDE) / "5 km" (SHORT) / "5km" (en, NARROW) cannot be
// produced from Java. macOS itself ships ICU4C as /usr/lib/libicucore.A.dylib
// and exports its C API, including the number-skeleton formatter
// (unumf_openForSkeletonAndLocale / unumf_formatDouble / unumf_formatDecimal /
// unumf_resultToString) -- i.e. the SAME engine and data family Android's
// android.icu (ICU4J) formats units with. Decision ladder rung 1: the host
// supports it natively, route it there. The natives are registered by
// runtime/src/icu_host_bridge.cpp (RegisterNatives at JVM start); in a bare JVM
// with nothing registered every call throws UnsupportedOperationException --
// loudly, never a made-up string.
//
// Also the home of MACAPK_ICU_PLATFORM_TYPES (default ON): android.icu
// factories return ICU's concrete types (DecimalFormat / SimpleDateFormat /
// RuleBasedCollator), formatToCharacterIterator carries ICU Field attributes,
// and DateFormat.getInstanceForSkeleton honours the skeleton. =0 restores the
// pre-change opaque wrappers, attribute-less iterators and the skeleton-blind
// getInstanceForSkeleton (= getDateInstance()), byte-identically.
// docs/android-icu-hermes-intl-surface_2026-10-01.md.
package com.macapk.icu;

public final class HostIcu {
    private HostIcu() {}

    public static final boolean PLATFORM_TYPES =
            !"0".equals(System.getenv("MACAPK_ICU_PLATFORM_TYPES"));

    // Bound by RegisterNatives (icu_host_bridge.cpp, kIcuHostMethods). Both throw
    // IllegalArgumentException("<u_errorName>") on an ICU failure status.
    private static native String nativeFormatDouble(String skeleton, String icuLocaleId, double value);
    private static native String nativeFormatDecimal(String skeleton, String icuLocaleId, String decimal);

    // android.icu.util.LocaleData over ICU4C's ulocdata_* (same file, kIcuHostMethods).
    // Lookup failures throw java.util.MissingResourceException, as ICU4J does.
    private static native int nativeMeasurementSystem(String icuLocaleId);
    private static native int[] nativePaperSize(String icuLocaleId);
    private static native int[] nativeCldrVersion();
    private static native String nativeDelimiter(String icuLocaleId, boolean noSubstitute, int type);
    private static native Object[] nativeExemplarSet(String icuLocaleId, boolean noSubstitute,
            int options, int icu4cExtype);
    private static native String nativeLocaleDisplayPattern(String icuLocaleId);
    private static native String nativeLocaleSeparator(String icuLocaleId);

    private static UnsupportedOperationException unbound(UnsatisfiedLinkError e) {
        UnsupportedOperationException x = new UnsupportedOperationException(
                "host ICU (libicucore) bridge is not registered in this process");
        x.initCause(e);
        return x;
    }

    /** ICU4C UMeasurementSystem: 0 SI, 1 US, 2 UK. Region from rg / region / likely subtags. */
    public static int measurementSystem(String icuLocaleId) {
        try { return nativeMeasurementSystem(icuLocaleId); }
        catch (UnsatisfiedLinkError e) { throw unbound(e); }
    }

    /** {height, width} in millimetres. */
    public static int[] paperSize(String icuLocaleId) {
        try { return nativePaperSize(icuLocaleId); }
        catch (UnsatisfiedLinkError e) { throw unbound(e); }
    }

    /** The host ICU's CLDR version, {major, minor, milli, micro}. */
    public static int[] cldrVersion() {
        try { return nativeCldrVersion(); }
        catch (UnsatisfiedLinkError e) { throw unbound(e); }
    }

    /** One quotation delimiter (ULocaleDataDelimiterType 0-3); null when there is none. */
    public static String delimiter(String icuLocaleId, boolean noSubstitute, int type) {
        try { return nativeDelimiter(icuLocaleId, noSubstitute, type); }
        catch (UnsatisfiedLinkError e) { throw unbound(e); }
    }

    /**
     * An exemplar set as {int[] start,end pairs, String[] multi-character strings};
     * null when ICU has none (or, with noSubstitute, only root has one).
     */
    public static Object[] exemplarSet(String icuLocaleId, boolean noSubstitute, int options,
            int icu4cExtype) {
        try { return nativeExemplarSet(icuLocaleId, noSubstitute, options, icu4cExtype); }
        catch (UnsatisfiedLinkError e) { throw unbound(e); }
    }

    public static String localeDisplayPattern(String icuLocaleId) {
        try { return nativeLocaleDisplayPattern(icuLocaleId); }
        catch (UnsatisfiedLinkError e) { throw unbound(e); }
    }

    public static String localeSeparator(String icuLocaleId) {
        try { return nativeLocaleSeparator(icuLocaleId); }
        catch (UnsatisfiedLinkError e) { throw unbound(e); }
    }

    /**
     * Format a number with an ICU number skeleton ("measure-unit/length-kilometer
     * unit-width-full-name .00") in an ICU locale ID ("nl_NL", "en@numbers=arab").
     */
    public static String formatSkeleton(String skeleton, String icuLocaleId, Number value) {
        try {
            if (value instanceof Double || value instanceof Float) {
                return nativeFormatDouble(skeleton, icuLocaleId, value.doubleValue());
            }
            String dec;
            if (value instanceof java.math.BigDecimal) dec = ((java.math.BigDecimal) value).toString();
            else if (value instanceof java.math.BigInteger) dec = value.toString();
            else if (value instanceof Long || value instanceof Integer
                    || value instanceof Short || value instanceof Byte) dec = Long.toString(value.longValue());
            else return nativeFormatDouble(skeleton, icuLocaleId, value.doubleValue());
            return nativeFormatDecimal(skeleton, icuLocaleId, dec);
        } catch (UnsatisfiedLinkError e) {
            UnsupportedOperationException x = new UnsupportedOperationException(
                    "host ICU (libicucore) bridge is not registered in this process");
            x.initCause(e);
            throw x;
        }
    }
}
