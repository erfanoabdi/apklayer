// android.util.TypedValue -- public field bag describing a resource
// value's type and packed data, plus the static helpers that unpack the
// aapt "complex" encoding. Mirrors AOSP:
//   frameworks/base/core/java/android/util/TypedValue.java
//
// A TYPE_DIMENSION / TYPE_FRACTION value is NOT an IEEE-754 float. aapt
// packs it into `data` as
//     bits  0..3   unit       (COMPLEX_UNIT_*)
//     bits  4..5   radix      (COMPLEX_RADIX_*, selects the fixed point)
//     bits  8..31  mantissa   (24-bit signed fixed-point magnitude)
// so the value is `mantissa * RADIX_MULTS[radix]` in the given unit. The
// previous implementation invented its own decode -- it read the upper 28
// bits of `data` as a literal float bit pattern -- which matched neither
// aapt's encoding nor anything that produces a complex int, so every
// dimension read through this class came out as ~0. It also declared
// getDimension as returning `int`; AOSP declares `()F`, and the JVM is
// signature-strict, so any app compiled against the real SDK hit
// NoSuchMethodError on `getDimension(Landroid/util/DisplayMetrics;)F`.
package android.util;

public class TypedValue {
    public static final int TYPE_NULL         = 0x00;
    public static final int TYPE_REFERENCE    = 0x01;
    public static final int TYPE_ATTRIBUTE    = 0x02;
    public static final int TYPE_STRING       = 0x03;
    public static final int TYPE_FLOAT        = 0x04;
    public static final int TYPE_DIMENSION    = 0x05;
    public static final int TYPE_FRACTION     = 0x06;
    public static final int TYPE_FIRST_INT    = 0x10;
    public static final int TYPE_INT_DEC      = 0x10;
    public static final int TYPE_INT_HEX      = 0x11;
    public static final int TYPE_INT_BOOLEAN  = 0x12;
    public static final int TYPE_FIRST_COLOR_INT  = 0x1c;
    public static final int TYPE_INT_COLOR_ARGB8  = 0x1c;
    public static final int TYPE_INT_COLOR_RGB8   = 0x1d;
    public static final int TYPE_INT_COLOR_ARGB4  = 0x1e;
    public static final int TYPE_INT_COLOR_RGB4   = 0x1f;
    public static final int TYPE_LAST_COLOR_INT   = 0x1f;
    public static final int TYPE_LAST_INT         = 0x1f;

    /** Complex data: bit location of the unit identifier. */
    public static final int COMPLEX_UNIT_SHIFT  = 0;
    /** Complex data: mask to extract the unit from a shifted value. */
    public static final int COMPLEX_UNIT_MASK   = 0xf;
    public static final int COMPLEX_UNIT_PX     = 0;
    public static final int COMPLEX_UNIT_DIP    = 1;
    public static final int COMPLEX_UNIT_SP     = 2;
    public static final int COMPLEX_UNIT_PT     = 3;
    public static final int COMPLEX_UNIT_IN     = 4;
    public static final int COMPLEX_UNIT_MM     = 5;
    /** TYPE_FRACTION unit: a basic fraction of the overall size. */
    public static final int COMPLEX_UNIT_FRACTION = 0;
    /** TYPE_FRACTION unit: a fraction of the parent size. */
    public static final int COMPLEX_UNIT_FRACTION_PARENT = 1;

    /** Complex data: bit location of the radix selector. */
    public static final int COMPLEX_RADIX_SHIFT = 4;
    /** Complex data: mask to extract the radix from a shifted value. */
    public static final int COMPLEX_RADIX_MASK  = 0x3;
    public static final int COMPLEX_RADIX_23p0  = 0;
    public static final int COMPLEX_RADIX_16p7  = 1;
    public static final int COMPLEX_RADIX_8p15  = 2;
    public static final int COMPLEX_RADIX_0p23  = 3;

    /** Complex data: bit location of the mantissa. */
    public static final int COMPLEX_MANTISSA_SHIFT = 8;
    /** Complex data: mask to extract the mantissa from a shifted value. */
    public static final int COMPLEX_MANTISSA_MASK  = 0xffffff;

    /** {@link #TYPE_NULL} data indicating the value was not specified.
     *  AOSP TypedValue.java :166-169 (android14-release):
     *      public static final int DATA_NULL_UNDEFINED = 0;
     *  It is what an absent attribute and a `@null` both carry once
     *  AttributeResolution.cpp's ApplyStyle has run (:321-325, "Deal with the
     *  special @null value -- it turns back to TYPE_NULL"). A public constant,
     *  so an app compiled against android.jar has it inlined either way; it is
     *  declared here for the API surface and so the framework names it instead
     *  of spelling the literal. */
    public static final int DATA_NULL_UNDEFINED = 0;
    /** {@link #TYPE_NULL} data indicating the value was explicitly set to null.
     *  AOSP TypedValue.java :170-173 (android14-release):
     *      public static final int DATA_NULL_EMPTY = 1;
     *  aapt2 compiles `@empty` to it (ResourceUtils.cpp MakeEmpty), and it is
     *  the one TYPE_NULL record TypedArray.hasValueOrEmpty reports and the
     *  ApplyStyle tier walk stops at (:275 / :287 / :306). */
    public static final int DATA_NULL_EMPTY = 1;

    private static final float INCHES_PER_PT = (1.0f / 72);
    private static final float INCHES_PER_MM = (1.0f / 25.4f);

    public int type;
    public int data;
    public CharSequence string;
    // v1.0.95: AOSP types are `int`, not `float`. Previously declared
    // as `float assetCookie` -- JVM is signature-strict on field
    // type, so any androidx code that reads it (e.g.
    // ResourceManagerInternal.createCacheKey) NoSuchFieldErrors and
    // the entire BottomNavigationView <init> path aborts.
    public int assetCookie = 1;
    public int resourceId;
    public int density;
    public int changingConfigurations = -1;
    // API 33. AOSP: "If the value came from a style resource or a
    // @android:style/... theme attribute, this is the id of the source style
    // that the value came from" -- 0 when it did not. Ours is populated by
    // ResourceIndex where a style is the source; a plain 0 is AOSP's own
    // answer for a direct resource lookup, so an unset value is not a lie.
    public int sourceResourceId;

    // The mantissa is a fixed-point magnitude: dividing by 2^MANTISSA_SHIFT
    // undoes the shift, and the radix multiplier places the binary point.
    private static final float MANTISSA_MULT =
        1.0f / (1 << COMPLEX_MANTISSA_SHIFT);
    private static final float[] RADIX_MULTS = new float[] {
        1.0f * MANTISSA_MULT,           1.0f / (1 << 7)  * MANTISSA_MULT,
        1.0f / (1 << 15) * MANTISSA_MULT, 1.0f / (1 << 23) * MANTISSA_MULT
    };

    /** AOSP complexToFloat: the units are ignored; this only undoes the
     *  mantissa/radix fixed-point packing. */
    public static float complexToFloat(int complex) {
        return (complex & (COMPLEX_MANTISSA_MASK << COMPLEX_MANTISSA_SHIFT))
            * RADIX_MULTS[(complex >> COMPLEX_RADIX_SHIFT) & COMPLEX_RADIX_MASK];
    }

    public static float complexToDimension(int data, DisplayMetrics metrics) {
        return applyDimension(
            (data >> COMPLEX_UNIT_SHIFT) & COMPLEX_UNIT_MASK,
            complexToFloat(data),
            metrics);
    }

    /** Truncating variant: used for offsets, where rounding would shift a
     *  layout by a pixel. */
    public static int complexToDimensionPixelOffset(int data, DisplayMetrics metrics) {
        return (int) applyDimension(
            (data >> COMPLEX_UNIT_SHIFT) & COMPLEX_UNIT_MASK,
            complexToFloat(data),
            metrics);
    }

    /** Rounding variant with AOSP's "a non-zero size is at least one pixel"
     *  clamp, so a 0.4px divider still draws instead of vanishing. */
    public static int complexToDimensionPixelSize(int data, DisplayMetrics metrics) {
        final float value = complexToFloat(data);
        final float f = applyDimension(
            (data >> COMPLEX_UNIT_SHIFT) & COMPLEX_UNIT_MASK,
            value,
            metrics);
        final int res = (int) ((f >= 0) ? (f + 0.5f) : (f - 0.5f));
        if (res != 0) return res;
        if (value == 0) return 0;
        if (value > 0) return 1;
        return -1;
    }

    /** @deprecated AOSP kept this API-1 accident as an alias. */
    @Deprecated
    public static float complexToDimensionNoisy(int data, DisplayMetrics metrics) {
        return complexToDimension(data, metrics);
    }

    public static float applyDimension(int unit, float value, DisplayMetrics metrics) {
        // AOSP dereferences metrics unconditionally (it is @NonNull there).
        // Mac.apk keeps a null guard because framework code here can reach
        // applyDimension before a Display is attached; returning the raw
        // value is the identity conversion (PX) rather than an NPE.
        if (metrics == null) return value;
        switch (unit) {
            case COMPLEX_UNIT_PX:  return value;
            case COMPLEX_UNIT_DIP: return value * metrics.density;
            // AOSP consults metrics.fontScaleConverter for non-linear font
            // scaling (API 34); our DisplayMetrics has no converter, which is
            // exactly AOSP's else branch.
            case COMPLEX_UNIT_SP:  return value * metrics.scaledDensity;
            case COMPLEX_UNIT_PT:  return value * metrics.xdpi * INCHES_PER_PT;
            case COMPLEX_UNIT_IN:  return value * metrics.xdpi;
            case COMPLEX_UNIT_MM:  return value * metrics.xdpi * INCHES_PER_MM;
        }
        return 0;
    }

    /** AOSP deriveDimension: the inverse of applyDimension (px -> unit).
     *  Throws on an invalid unit, exactly as AOSP does -- a silent 0 would
     *  hide the caller's bug. */
    public static float deriveDimension(int unitToConvertTo, float pixelValue,
                                        DisplayMetrics metrics) {
        switch (unitToConvertTo) {
            case COMPLEX_UNIT_PX:
                return pixelValue;
            case COMPLEX_UNIT_DIP: {
                // Avoid divide-by-zero; 0 is what the forward function yields.
                if (metrics.density == 0) return 0;
                return pixelValue / metrics.density;
            }
            case COMPLEX_UNIT_SP: {
                if (metrics.scaledDensity == 0) return 0;
                return pixelValue / metrics.scaledDensity;
            }
            case COMPLEX_UNIT_PT: {
                if (metrics.xdpi == 0) return 0;
                return pixelValue / metrics.xdpi / INCHES_PER_PT;
            }
            case COMPLEX_UNIT_IN: {
                if (metrics.xdpi == 0) return 0;
                return pixelValue / metrics.xdpi;
            }
            case COMPLEX_UNIT_MM: {
                if (metrics.xdpi == 0) return 0;
                return pixelValue / metrics.xdpi / INCHES_PER_MM;
            }
            default:
                throw new IllegalArgumentException(
                    "Invalid unitToConvertTo " + unitToConvertTo);
        }
    }

    public static float convertPixelsToDimension(int unitToConvertTo, float pixelValue,
                                                 DisplayMetrics metrics) {
        return deriveDimension(unitToConvertTo, pixelValue, metrics);
    }

    public static float convertDimensionToPixels(int unitToConvertFrom, float value,
                                                 DisplayMetrics metrics) {
        return applyDimension(unitToConvertFrom, value, metrics);
    }

    /** AOSP complexToFraction: unit FRACTION multiplies `base`, unit
     *  FRACTION_PARENT multiplies `pbase` (the "nn%p" form). */
    public static float complexToFraction(int data, float base, float pbase) {
        switch ((data >> COMPLEX_UNIT_SHIFT) & COMPLEX_UNIT_MASK) {
            case COMPLEX_UNIT_FRACTION:        return complexToFloat(data) * base;
            case COMPLEX_UNIT_FRACTION_PARENT: return complexToFloat(data) * pbase;
        }
        return 0;
    }

    public static int getUnitFromComplexDimension(int complexDimension) {
        return COMPLEX_UNIT_MASK & (complexDimension >> COMPLEX_UNIT_SHIFT);
    }

    /** Pack a mantissa + radix into a complex int. AOSP validates both;
     *  a silently-truncated mantissa would decode to an unrelated value. */
    private static int createComplex(int mantissa, int radix) {
        if (mantissa < -0x800000 || mantissa >= 0x800000) {
            throw new IllegalArgumentException(
                "Magnitude of mantissa is too large: " + mantissa);
        }
        if (radix < COMPLEX_RADIX_23p0 || radix > COMPLEX_RADIX_0p23) {
            throw new IllegalArgumentException("Invalid radix: " + radix);
        }
        return ((mantissa & COMPLEX_MANTISSA_MASK) << COMPLEX_MANTISSA_SHIFT)
                | (radix << COMPLEX_RADIX_SHIFT);
    }

    /** Inverse of complexToFloat for an integral value. */
    public static int intToComplex(int value) {
        if (value < -0x800000 || value >= 0x800000) {
            throw new IllegalArgumentException(
                "Magnitude of the value is too large: " + value);
        }
        return createComplex(value, COMPLEX_RADIX_23p0);
    }

    /** Inverse of complexToFloat: picks the coarsest radix that still holds
     *  the magnitude, matching aapt's own choice so a round-trip is stable. */
    public static int floatToComplex(float value) {
        if (value < (float) -0x800000 - .5f || value >= (float) 0x800000 - .5f) {
            throw new IllegalArgumentException(
                "Magnitude of the value is too large: " + value);
        }
        try {
            // No fraction: the integer representation is clearer.
            if (value == (float) (int) value) {
                return createComplex((int) value, COMPLEX_RADIX_23p0);
            }
            float absValue = Math.abs(value);
            if (absValue < 1f) {
                return createComplex(Math.round(value * (1 << 23)), COMPLEX_RADIX_0p23);
            }
            if (absValue < (float) (1 << 8)) {
                return createComplex(Math.round(value * (1 << 15)), COMPLEX_RADIX_8p15);
            }
            if (absValue < (float) (1 << 16)) {
                return createComplex(Math.round(value * (1 << 7)), COMPLEX_RADIX_16p7);
            }
            return createComplex(Math.round(value), COMPLEX_RADIX_23p0);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "Unable to convert value to complex: " + value, ex);
        }
    }

    /** Build a TYPE_DIMENSION-shaped complex int. This is how a caller (or
     *  our own TypedArray) must populate `data` for a dimension so that
     *  getDimension/complexToDimensionPixelSize decode it correctly. */
    public static int createComplexDimension(int value, int units) {
        if (units < COMPLEX_UNIT_PX || units > COMPLEX_UNIT_MM) {
            throw new IllegalArgumentException("Must be a valid COMPLEX_UNIT_*: " + units);
        }
        return intToComplex(value) | units;
    }

    public static int createComplexDimension(float value, int units) {
        if (units < COMPLEX_UNIT_PX || units > COMPLEX_UNIT_MM) {
            throw new IllegalArgumentException("Must be a valid COMPLEX_UNIT_*: " + units);
        }
        return floatToComplex(value) | units;
    }

    /** AOSP isColorType: TYPE_FIRST_COLOR_INT..TYPE_LAST_COLOR_INT. */
    public boolean isColorType() {
        return (type >= TYPE_FIRST_COLOR_INT && type <= TYPE_LAST_COLOR_INT);
    }

    // ------------------------------------------------------------------
    // Mac.apk internal: compile a NUMERIC resource literal exactly the way
    // aapt does, so a value we parse at runtime carries the same type+data
    // the emulator's resources.arsc carries. This exists because our
    // resource pipeline keeps values as SOURCE TEXT (apktool output) where
    // real Android keeps aapt's compiled encodings; every TypedValue we
    // hand to an app must nevertheless match the compiled form bit-exactly
    // (measured against the emulator by debug/fwprobe.sh).
    //
    // Mirrors, verbatim in structure (android11-release):
    //  - libs/androidfw/ResourceTypes.cpp U16StringToInt (:5195): decimal
    //    -> TYPE_INT_DEC, "0x..." -> TYPE_INT_HEX, no negative hex, 32-bit
    //    range guards.
    //  - libs/androidfw/ResourceTypes.cpp stringToFloat (:5289): unit
    //    suffix table px/dip/dp/sp/pt/in/mm -> TYPE_DIMENSION, %/%p ->
    //    TYPE_FRACTION with scale 1/100; bits = (uint64)(f*(1<<23)+.5f);
    //    radix picked by magnitude (23p0 when no fraction bits, else
    //    0p23/8p15/16p7/23p0); mantissa = (bits>>shift)&0xffffff, negated
    //    mantissa masked back in; data = unit | radix<<4 | mantissa<<8.
    //    NOT the same rounding as floatToComplex() above (Math.round per
    //    radix); aapt rounds ONCE at 23 fraction bits then shifts, and the
    //    golden table was produced by aapt, so this parser must too.
    // ------------------------------------------------------------------

    /** Parse an integer / float / dimension / fraction literal into
     *  `out.type` + `out.data` exactly as aapt compiles it. Returns false
     *  (out untouched) for anything that is not such a literal. */
    public static boolean macapkParseNumericLiteral(String s, TypedValue out) {
        if (s == null) return false;
        s = s.trim();
        if (s.isEmpty()) return false;
        char c0 = s.charAt(0);
        if ((c0 < '0' || c0 > '9') && c0 != '.' && c0 != '-' && c0 != '+') return false;

        // Integer first: aapt2 coercion order tries int before float, which
        // is why <item>7</item> is TYPE_INT_DEC and not TYPE_FLOAT.
        if (macapkParseIntLiteral(s, out)) return true;

        // Split a numeric prefix from a unit suffix. strtof semantics: the
        // longest leading float; we scan sign/digits/dot (no exponent in the
        // unit form -- "1e2dp" is not a thing aapt input contains).
        int i = 0;
        int n = s.length();
        if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
        int numStart = i;
        boolean dot = false;
        while (i < n) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') { i++; continue; }
            if (c == '.' && !dot) { dot = true; i++; continue; }
            break;
        }
        if (i == numStart) return false;
        float f;
        try { f = Float.parseFloat(s.substring(0, i)); }
        catch (NumberFormatException e) { return false; }

        String suffix = s.substring(i).trim();
        if (suffix.isEmpty()) {
            // Bare float. (Exponent forms fall here too via the full-string
            // parse below, matching strtof eating the whole token.)
            out.type = TYPE_FLOAT;
            out.data = Float.floatToIntBits(f);
            return true;
        }
        int type;
        int unit;
        float scale = 1.0f;
        switch (suffix) {
            case "px":  type = TYPE_DIMENSION; unit = COMPLEX_UNIT_PX;  break;
            case "dip":
            case "dp":  type = TYPE_DIMENSION; unit = COMPLEX_UNIT_DIP; break;
            case "sp":  type = TYPE_DIMENSION; unit = COMPLEX_UNIT_SP;  break;
            case "pt":  type = TYPE_DIMENSION; unit = COMPLEX_UNIT_PT;  break;
            case "in":  type = TYPE_DIMENSION; unit = COMPLEX_UNIT_IN;  break;
            case "mm":  type = TYPE_DIMENSION; unit = COMPLEX_UNIT_MM;  break;
            case "%":   type = TYPE_FRACTION;  unit = COMPLEX_UNIT_FRACTION;        scale = 1.0f / 100; break;
            case "%p":  type = TYPE_FRACTION;  unit = COMPLEX_UNIT_FRACTION_PARENT; scale = 1.0f / 100; break;
            default:
                // A full-string float ("1.5e2") has no unit branch in aapt
                // either; strtof would have consumed it above only without
                // an exponent, so retry the whole token as a float here.
                try {
                    out.type = TYPE_FLOAT;
                    out.data = Float.floatToIntBits(Float.parseFloat(s));
                    return true;
                } catch (NumberFormatException e) { return false; }
        }
        f *= scale;
        final boolean neg = f < 0;
        if (neg) f = -f;
        long bits = (long) (f * (1 << 23) + .5f);
        int radix;
        int shift;
        if ((bits & 0x7fffffL) == 0) {
            // Always use 23p0 if there is no fraction (aapt's own comment).
            radix = COMPLEX_RADIX_23p0; shift = 23;
        } else if ((bits & 0xffffffffff800000L) == 0) {
            radix = COMPLEX_RADIX_0p23; shift = 0;
        } else if ((bits & 0xffffffff80000000L) == 0) {
            radix = COMPLEX_RADIX_8p15; shift = 8;
        } else if ((bits & 0xffffff8000000000L) == 0) {
            radix = COMPLEX_RADIX_16p7; shift = 16;
        } else {
            radix = COMPLEX_RADIX_23p0; shift = 23;
        }
        int mantissa = (int) ((bits >> shift) & COMPLEX_MANTISSA_MASK);
        if (neg) mantissa = (-mantissa) & COMPLEX_MANTISSA_MASK;
        out.type = type;
        out.data = (unit << COMPLEX_UNIT_SHIFT)
                 | (radix << COMPLEX_RADIX_SHIFT)
                 | (mantissa << COMPLEX_MANTISSA_SHIFT);
        return true;
    }

    /** U16StringToInt: decimal -> TYPE_INT_DEC, 0x-prefixed -> TYPE_INT_HEX
     *  (negative hex rejected), with aapt's 32-bit range guards. */
    private static boolean macapkParseIntLiteral(String s, TypedValue out) {
        int i = 0;
        final int len = s.length();
        boolean neg = false;
        if (s.charAt(0) == '-') { neg = true; i++; }
        if (i >= len) return false;
        char c = s.charAt(i);
        if (c < '0' || c > '9') return false;
        long val = 0;
        if (len - i > 1 && c == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
            if (neg) return false;
            i += 2;
            if (i == len) return false;
            while (i < len) {
                int d = Character.digit(s.charAt(i), 16);
                if (d < 0) return false;
                val = (val * 16) + d;
                if (val > 0xffffffffL) return false;
                i++;
            }
            out.type = TYPE_INT_HEX;
            out.data = (int) val;
            return true;
        }
        while (i < len) {
            c = s.charAt(i);
            if (c < '0' || c > '9') return false;
            val = (val * 10) + (c - '0');
            if ((neg && -val < Integer.MIN_VALUE) || (!neg && val > Integer.MAX_VALUE)) return false;
            i++;
        }
        out.type = TYPE_INT_DEC;
        out.data = (int) (neg ? -val : val);
        return true;
    }

    public float getFloat() { return Float.intBitsToFloat(data); }

    /** AOSP declares `public float getDimension(DisplayMetrics)`. */
    public float getDimension(DisplayMetrics metrics) {
        return complexToDimension(data, metrics);
    }

    public int getComplexUnit() { return getUnitFromComplexDimension(data); }

    public float getFraction(float base, float pbase) {
        return complexToFraction(data, base, pbase);
    }

    /**
     * Regardless of the actual type of the value, try to convert it to a string.
     * A colour becomes "#aarrggbb", a dimension "16.0dip", a fraction "50.0%",
     * a reference "@<id>" and an attribute "?<id>". Returns null for TYPE_NULL
     * and for any type outside the known ranges.
     *
     * AOSP: frameworks/base/core/java/android/util/TypedValue.java:472-479. Note
     * that TYPE_STRING returns the `string` FIELD, not a rendering of `data` --
     * every other type ignores `string` entirely.
     *
     * Was missing entirely. It is the method a resource dumper, a
     * Resources.getText fallback, and every "why is this attribute wrong"
     * diagnostic path reach for, so its absence NoSuchMethodError'd rather than
     * degrading.
     */
    public final CharSequence coerceToString() {
        int t = type;
        if (t == TYPE_STRING) {
            return string;
        }
        return coerceToString(t, data);
    }

    private static final String[] DIMENSION_UNIT_STRS = new String[] {
        "px", "dip", "sp", "pt", "in", "mm"
    };
    private static final String[] FRACTION_UNIT_STRS = new String[] {
        "%", "%p"
    };

    /**
     * coerceToString on an explicitly supplied type and data.
     * AOSP: TypedValue.java:498-528.
     *
     * The unit lookups are UNCHECKED array indexes on purpose: a complex value
     * whose unit field names a slot past the end of the table throws
     * ArrayIndexOutOfBoundsException rather than inventing a suffix, which is
     * how a corrupt resource announces itself instead of silently rendering.
     */
    public static final String coerceToString(int type, int data) {
        switch (type) {
        case TYPE_NULL:
            return null;
        case TYPE_REFERENCE:
            return "@" + data;
        case TYPE_ATTRIBUTE:
            return "?" + data;
        case TYPE_FLOAT:
            return Float.toString(Float.intBitsToFloat(data));
        case TYPE_DIMENSION:
            return Float.toString(complexToFloat(data)) + DIMENSION_UNIT_STRS[
                (data >> COMPLEX_UNIT_SHIFT) & COMPLEX_UNIT_MASK];
        case TYPE_FRACTION:
            return Float.toString(complexToFloat(data) * 100) + FRACTION_UNIT_STRS[
                (data >> COMPLEX_UNIT_SHIFT) & COMPLEX_UNIT_MASK];
        case TYPE_INT_HEX:
            return "0x" + Integer.toHexString(data);
        case TYPE_INT_BOOLEAN:
            return data != 0 ? "true" : "false";
        }

        if (type >= TYPE_FIRST_COLOR_INT && type <= TYPE_LAST_COLOR_INT) {
            return "#" + Integer.toHexString(data);
        } else if (type >= TYPE_FIRST_INT && type <= TYPE_LAST_INT) {
            return Integer.toString(data);
        }

        return null;
    }

    /** AOSP TypedValue.java:530-538: copies every field, including assetCookie
     *  and density, which callers that re-resolve a value depend on. */
    public void setTo(TypedValue other) {
        type = other.type;
        string = other.string;
        data = other.data;
        assetCookie = other.assetCookie;
        resourceId = other.resourceId;
        density = other.density;
    }
}
