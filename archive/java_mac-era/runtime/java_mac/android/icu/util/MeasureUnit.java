package android.icu.util;

/**
 * A unit of measure (metre, kilogram, hour, byte, ...).
 *
 * ICU's real MeasureUnit carries locale-specific display names in several
 * widths; we carry the type/subtype identity, which is what the formatter API
 * needs to round-trip a unit through format() and getOutputUnit(). Display
 * names fall back to the subtype id -- visible and self-explanatory, rather
 * than an empty string.
 *
 * Source: https://developer.android.com/reference/android/icu/util/MeasureUnit
 */
public class MeasureUnit {

    private final String mType;
    private final String mSubtype;

    protected MeasureUnit(String type, String subtype) {
        mType = type;
        mSubtype = subtype;
    }

    public String getType() { return mType; }

    public String getSubtype() { return mSubtype; }

    @Override public String toString() { return mType + "-" + mSubtype; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MeasureUnit)) return false;
        MeasureUnit m = (MeasureUnit) o;
        return mType.equals(m.mType) && mSubtype.equals(m.mSubtype);
    }

    @Override public int hashCode() { return mType.hashCode() * 31 + mSubtype.hashCode(); }

    private static MeasureUnit u(String t, String s) { return new MeasureUnit(t, s); }

    // The units the corpus actually reaches for; ICU defines many more.
    public static final MeasureUnit PERCENT      = u("concentr", "percent");
    public static final MeasureUnit METER        = u("length", "meter");
    public static final MeasureUnit KILOMETER    = u("length", "kilometer");
    public static final MeasureUnit CENTIMETER   = u("length", "centimeter");
    public static final MeasureUnit MILE         = u("length", "mile");
    public static final MeasureUnit FOOT         = u("length", "foot");
    public static final MeasureUnit INCH         = u("length", "inch");
    public static final MeasureUnit GRAM         = u("mass", "gram");
    public static final MeasureUnit KILOGRAM     = u("mass", "kilogram");
    public static final MeasureUnit POUND        = u("mass", "pound");
    public static final MeasureUnit SECOND       = u("duration", "second");
    public static final MeasureUnit MINUTE       = u("duration", "minute");
    public static final MeasureUnit HOUR         = u("duration", "hour");
    public static final MeasureUnit DAY          = u("duration", "day");
    public static final MeasureUnit WEEK         = u("duration", "week");
    public static final MeasureUnit MONTH        = u("duration", "month");
    public static final MeasureUnit YEAR         = u("duration", "year");
    public static final MeasureUnit BYTE         = u("digital", "byte");
    public static final MeasureUnit KILOBYTE     = u("digital", "kilobyte");
    public static final MeasureUnit MEGABYTE     = u("digital", "megabyte");
    public static final MeasureUnit GIGABYTE     = u("digital", "gigabyte");
    public static final MeasureUnit CELSIUS      = u("temperature", "celsius");
    public static final MeasureUnit FAHRENHEIT   = u("temperature", "fahrenheit");
    // The rest of ECMA-402's sanctioned single units (ecma402 #table-sanctioned-single-unit-identifiers)
    // plus the three speed units ICU defines, under ICU's own type names
    // (icu4j MeasureUnit: ACRE "area", BIT "digital", DEGREE "angle", LITER "volume", ...),
    // so an Intl.NumberFormat {style:"unit"} can resolve every unit the spec allows.
    public static final MeasureUnit ACRE         = u("area", "acre");
    public static final MeasureUnit HECTARE      = u("area", "hectare");
    public static final MeasureUnit BIT          = u("digital", "bit");
    public static final MeasureUnit KILOBIT      = u("digital", "kilobit");
    public static final MeasureUnit MEGABIT      = u("digital", "megabit");
    public static final MeasureUnit GIGABIT      = u("digital", "gigabit");
    public static final MeasureUnit TERABIT      = u("digital", "terabit");
    public static final MeasureUnit TERABYTE     = u("digital", "terabyte");
    public static final MeasureUnit PETABYTE     = u("digital", "petabyte");
    public static final MeasureUnit DEGREE       = u("angle", "degree");
    public static final MeasureUnit MILLIMETER   = u("length", "millimeter");
    public static final MeasureUnit YARD         = u("length", "yard");
    public static final MeasureUnit MILE_SCANDINAVIAN = u("length", "mile-scandinavian");
    public static final MeasureUnit NANOSECOND   = u("duration", "nanosecond");
    public static final MeasureUnit MICROSECOND  = u("duration", "microsecond");
    public static final MeasureUnit MILLISECOND  = u("duration", "millisecond");
    public static final MeasureUnit LITER        = u("volume", "liter");
    public static final MeasureUnit MILLILITER   = u("volume", "milliliter");
    public static final MeasureUnit GALLON       = u("volume", "gallon");
    public static final MeasureUnit FLUID_OUNCE  = u("volume", "fluid-ounce");
    public static final MeasureUnit OUNCE        = u("mass", "ounce");
    public static final MeasureUnit STONE        = u("mass", "stone");
    public static final MeasureUnit KILOMETER_PER_HOUR = u("speed", "kilometer-per-hour");
    public static final MeasureUnit METER_PER_SECOND   = u("speed", "meter-per-second");
    public static final MeasureUnit MILE_PER_HOUR      = u("speed", "mile-per-hour");

    private static volatile java.util.Set<MeasureUnit> sAvailable;

    /** ICU: every unit this layer defines (the public constants above), unmodifiable. */
    public static java.util.Set<MeasureUnit> getAvailable() {
        java.util.Set<MeasureUnit> a = sAvailable;
        if (a == null) {
            java.util.LinkedHashSet<MeasureUnit> out = new java.util.LinkedHashSet<>();
            for (java.lang.reflect.Field f : MeasureUnit.class.getFields()) {
                if (f.getType() != MeasureUnit.class
                        || !java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try { out.add((MeasureUnit) f.get(null)); } catch (IllegalAccessException ignored) { }
            }
            a = java.util.Collections.unmodifiableSet(out);
            sAvailable = a;
        }
        return a;
    }

    /** ICU: the units of one type ("length", "duration", ...). */
    public static java.util.Set<MeasureUnit> getAvailable(String type) {
        java.util.LinkedHashSet<MeasureUnit> out = new java.util.LinkedHashSet<>();
        for (MeasureUnit m : getAvailable()) if (m.mType.equals(type)) out.add(m);
        return java.util.Collections.unmodifiableSet(out);
    }

    /** ICU: the unit types present. */
    public static java.util.Set<String> getAvailableTypes() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (MeasureUnit m : getAvailable()) out.add(m.mType);
        return java.util.Collections.unmodifiableSet(out);
    }

    public String getIdentifier() { return getType() + "-" + getSubtype(); }
}

