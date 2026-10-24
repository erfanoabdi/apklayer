package android.util;

/**
 * IEEE 754-2008 binary16 (half precision) conversions.
 *
 * AOSP exposes this as public API and android.graphics.Color / Bitmap use it for
 * every wide-gamut (RGBA_F16 / long-packed color) path, so the framework needs
 * it in its own right rather than as a private helper.
 *
 * We cannot use Float.float16ToFloat / Float.floatToFloat16: those are JDK 20+
 * and the framework compiles with --release 21 (runtime/scripts/build.sh), so
 * they resolve at author time on a JDK 21 toolchain and then fail the real
 * build. The bit manipulation below mirrors AOSP's implementation exactly,
 * including its round-to-nearest-even behavior and the deliberate carry from
 * the significand into the exponent.
 *
 * Source: frameworks/base/core/java/android/util/Half.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/util/Half.java
 */
public final class Half extends Number implements Comparable<Half> {

    public static final short POSITIVE_INFINITY = (short) 0x7c00;
    public static final short NEGATIVE_INFINITY = (short) 0xfc00;
    public static final short NaN = (short) 0x7e00;
    public static final short POSITIVE_ZERO = (short) 0x0000;
    public static final short NEGATIVE_ZERO = (short) 0x8000;
    public static final short MIN_VALUE = (short) 0x0001;
    public static final short MIN_NORMAL = (short) 0x0400;
    public static final short MAX_VALUE = (short) 0x7bff;
    public static final short LOWEST_VALUE = (short) 0xfbff;

    public static final int SIZE = 16;
    public static final int MAX_EXPONENT = 15;
    public static final int MIN_EXPONENT = -14;
    /** Smallest h such that 1 + h != 1 (AOSP Half.java:105). */
    public static final short EPSILON = (short) 0x1400;

    private static final int SIGN_SHIFT = 15;
    private static final int SIGN_MASK = 0x8000;
    private static final int EXPONENT_SHIFT = 10;
    private static final int SHIFTED_EXPONENT_MASK = 0x1f;
    private static final int SIGNIFICAND_MASK = 0x3ff;
    private static final int EXPONENT_BIAS = 15;

    private static final int FP32_SIGN_SHIFT = 31;
    private static final int FP32_EXPONENT_SHIFT = 23;
    private static final int FP32_SHIFTED_EXPONENT_MASK = 0xff;
    private static final int FP32_SIGNIFICAND_MASK = 0x7fffff;
    private static final int FP32_EXPONENT_BIAS = 127;
    private static final int FP32_QNAN_MASK = 0x400000;
    private static final int FP32_DENORMAL_MAGIC = 126 << 23;
    private static final float FP32_DENORMAL_FLOAT =
            Float.intBitsToFloat(FP32_DENORMAL_MAGIC);

    // --- Boxed-object form (AOSP: Half extends Number). The static short-based
    // utilities above remain; these wrap one half-precision value as an object,
    // which is what Half.valueOf(...) returns. ---
    private final short mValue;
    public Half(short value)  { mValue = value; }
    public Half(float value)  { mValue = toHalf(value); }
    public Half(double value) { mValue = toHalf((float) value); }
    public Half(String value) throws NumberFormatException { mValue = toHalf(Float.parseFloat(value)); }

    public short halfValue()          { return mValue; }
    @Override public byte byteValue()    { return (byte) toFloat(mValue); }
    @Override public short shortValue()  { return (short) toFloat(mValue); }
    @Override public int intValue()      { return (int) toFloat(mValue); }
    @Override public long longValue()    { return (long) toFloat(mValue); }
    @Override public float floatValue()  { return toFloat(mValue); }
    @Override public double doubleValue(){ return toFloat(mValue); }
    public boolean isNaN()               { return isNaN(mValue); }
    public boolean isInfinite()          { return isInfinite(mValue); }
    @Override public int compareTo(Half h) { return compare(mValue, h.mValue); }
    @Override public boolean equals(Object o) {
        return (o instanceof Half) && ((Half) o).mValue == mValue;
    }
    @Override public int hashCode() { return hashCode(mValue); }
    @Override public String toString() { return toString(mValue); }

    /** AOSP valueOf factories -- box a value as a Half OBJECT (distinct from the
     *  static short-returning toHalf/toString utilities). */
    public static Half valueOf(float f)  { return new Half(toHalf(f)); }
    public static Half valueOf(short h)  { return new Half(h); }
    public static Half valueOf(String s) { return new Half(s); }


    /** Widen a binary16 bit pattern to a float. */
    public static float toFloat(short h) {
        int bits = h & 0xffff;
        int s = bits & SIGN_MASK;
        int e = (bits >>> EXPONENT_SHIFT) & SHIFTED_EXPONENT_MASK;
        int m = bits & SIGNIFICAND_MASK;

        int outE = 0;
        int outM = 0;

        if (e == 0) {
            if (m != 0) {
                // Denormal half. Adding it to a magic constant renormalizes it
                // as a float, then the same constant is subtracted back out.
                float o = Float.intBitsToFloat(FP32_DENORMAL_MAGIC + m);
                o -= FP32_DENORMAL_FLOAT;
                return s == 0 ? o : -o;
            }
            // else +/-0, which falls through with outE == outM == 0
        } else {
            outM = m << 13;
            if (e == 0x1f) {
                outE = 0xff;
                if (outM != 0) {
                    // Signalling NaNs are quieted, matching AOSP.
                    outM |= FP32_QNAN_MASK;
                }
            } else {
                outE = e - EXPONENT_BIAS + FP32_EXPONENT_BIAS;
            }
        }

        int out = (s << 16) | (outE << FP32_EXPONENT_SHIFT) | outM;
        return Float.intBitsToFloat(out);
    }

    /** Narrow a float to a binary16 bit pattern, round-to-nearest-even. */
    public static short toHalf(float f) {
        int bits = Float.floatToRawIntBits(f);
        int s = bits >>> FP32_SIGN_SHIFT;
        int e = (bits >>> FP32_EXPONENT_SHIFT) & FP32_SHIFTED_EXPONENT_MASK;
        int m = bits & FP32_SIGNIFICAND_MASK;

        int outE = 0;
        int outM = 0;

        if (e == 0xff) {
            outE = 0x1f;
            outM = m != 0 ? 0x200 : 0;
        } else {
            e = e - FP32_EXPONENT_BIAS + EXPONENT_BIAS;
            if (e >= 0x1f) {
                // Overflows the half exponent range: saturate to infinity.
                outE = 0x1f;
            } else if (e <= 0) {
                if (e >= -10) {
                    // Representable as a half denormal. Restore the implicit
                    // leading 1 and shift it down into denormal position.
                    m = m | 0x800000;
                    int shift = 14 - e;
                    outM = m >> shift;
                    int lowm = m & ((1 << shift) - 1);
                    int hway = 1 << (shift - 1);
                    if (lowm + (outM & 1) > hway) {
                        outM++;
                    }
                }
                // else the magnitude is below MIN_VALUE: flush to +/-0.
            } else {
                outE = e;
                outM = m >> 13;
                if ((m & 0x1fff) + (outM & 0x1) > 0x1000) {
                    outM++;
                }
            }
        }

        // outM is ADDED rather than OR'd: rounding above can carry out of the
        // significand into the exponent, which is the correct result.
        return (short) ((s << SIGN_SHIFT) | (outE << EXPONENT_SHIFT) + outM);
    }

    public static boolean isNaN(short h) {
        return (h & 0x7fff) > 0x7c00;
    }

    public static boolean isInfinite(short h) {
        return (h & 0x7fff) == 0x7c00;
    }

    // ------------------------------------------------------------------
    // The rest of the public surface. AOSP's android.util.Half is a thin
    // forwarder onto libcore.util.FP16 (Half.java:416-762 is one delegation
    // per method), so these bodies are ported from
    //   libcore/luni/src/main/java/libcore/util/FP16.java (android11-release)
    // rather than from frameworks/base. They were MISSING here, which is worse
    // than wrong: android.graphics.Color's half-float packing and every
    // wide-gamut/RGBA_F16 path calls them, and a missing static method is a
    // NoSuchMethodError at the call site rather than a bad pixel.
    //
    // Note the ORDERING TRICK these all share: a binary16 bit pattern is only
    // monotonic in its unsigned value for POSITIVE numbers. FP16 maps a
    // negative pattern to `0x8000 - bits`, which folds the two signed halves
    // into one totally-ordered integer line, and every comparator below uses
    // that same mapping. A comparator written against Float.compare(toFloat(x),
    // ...) agrees on ordinary values and disagrees on the edges these exist for.
    // ------------------------------------------------------------------

    /** Mask for the exponent+significand, i.e. the magnitude. */
    private static final int EXPONENT_SIGNIFICAND_MASK = 0x7fff;
    private static final int EXPONENT_MASK = 0x7c00;

    /** Fold a bit pattern onto a totally-ordered integer line. */
    private static int ordered(short v) {
        return (v & SIGN_MASK) != 0 ? 0x8000 - (v & 0xffff) : v & 0xffff;
    }

    /** FP16.compare: like Float.compare, but preserving the -0.0 < +0.0 order
     *  and collapsing every NaN onto one value. */
    public static int compare(short x, short y) {
        if (less(x, y)) return -1;
        if (greater(x, y)) return 1;

        // Collapse NaNs but keep SIGNED short types, so -0.0 still orders
        // below +0.0.
        short xBits = isNaN(x) ? NaN : x;
        short yBits = isNaN(y) ? NaN : y;
        return (xBits == yBits ? 0 : (xBits < yBits ? -1 : 1));
    }

    /** Canonicalizing bit view: every NaN reads back as the canonical NaN. */
    public static short halfToShortBits(short h) {
        return (h & EXPONENT_SIGNIFICAND_MASK) > POSITIVE_INFINITY ? NaN : h;
    }

    public static int halfToIntBits(short h) {
        return (h & EXPONENT_SIGNIFICAND_MASK) > POSITIVE_INFINITY ? NaN : h & 0xffff;
    }

    /** Raw view: a signalling NaN survives verbatim. */
    public static int halfToRawIntBits(short h) {
        return h & 0xffff;
    }

    public static short intBitsToHalf(int bits) {
        return (short) (bits & 0xffff);
    }

    public static short copySign(short magnitude, short sign) {
        return (short) ((sign & SIGN_MASK) | (magnitude & EXPONENT_SIGNIFICAND_MASK));
    }

    public static short abs(short h) {
        return (short) (h & EXPONENT_SIGNIFICAND_MASK);
    }

    /** Round to the nearest integral value, ties to EVEN (FP16.rint). */
    public static short round(short h) {
        int bits = h & 0xffff;
        int abs = bits & EXPONENT_SIGNIFICAND_MASK;
        int result = bits;

        if (abs < 0x3c00) {
            result &= SIGN_MASK;
            if (abs > 0x3800) {
                result |= 0x3c00;
            }
        } else if (abs < 0x6400) {
            int exp = 25 - (abs >> 10);
            int mask = (1 << exp) - 1;
            result += ((1 << (exp - 1)) - (~(abs >> exp) & 1));
            result &= ~mask;
        }
        if (isNaN((short) result)) {
            // Quiet the NaN, matching what ARM64/Intel hardware produces.
            result |= NaN;
        }
        return (short) result;
    }

    public static short ceil(short h) {
        int bits = h & 0xffff;
        int abs = bits & EXPONENT_SIGNIFICAND_MASK;
        int result = bits;

        if (abs < 0x3c00) {
            result &= SIGN_MASK;
            result |= 0x3c00 & -(~(bits >> 15) & (abs != 0 ? 1 : 0));
        } else if (abs < 0x6400) {
            abs = 25 - (abs >> 10);
            int mask = (1 << abs) - 1;
            result += mask & ((bits >> 15) - 1);
            result &= ~mask;
        }
        if (isNaN((short) result)) {
            result |= NaN;
        }
        return (short) result;
    }

    public static short floor(short h) {
        int bits = h & 0xffff;
        int abs = bits & EXPONENT_SIGNIFICAND_MASK;
        int result = bits;

        if (abs < 0x3c00) {
            result &= SIGN_MASK;
            result |= 0x3c00 & (bits > 0x8000 ? 0xffff : 0x0);
        } else if (abs < 0x6400) {
            abs = 25 - (abs >> 10);
            int mask = (1 << abs) - 1;
            result += mask & -(bits >> 15);
            result &= ~mask;
        }
        if (isNaN((short) result)) {
            result |= NaN;
        }
        return (short) result;
    }

    /** Round toward zero. */
    public static short trunc(short h) {
        int bits = h & 0xffff;
        int abs = bits & EXPONENT_SIGNIFICAND_MASK;
        int result = bits;

        if (abs < 0x3c00) {
            result &= SIGN_MASK;
        } else if (abs < 0x6400) {
            abs = 25 - (abs >> 10);
            int mask = (1 << abs) - 1;
            result &= ~mask;
        }
        return (short) result;
    }

    public static short min(short x, short y) {
        if (isNaN(x)) return NaN;
        if (isNaN(y)) return NaN;

        // Both zero: the sign decides, so min(-0, +0) is -0.
        if ((x & EXPONENT_SIGNIFICAND_MASK) == 0 && (y & EXPONENT_SIGNIFICAND_MASK) == 0) {
            return (x & SIGN_MASK) != 0 ? x : y;
        }
        return ordered(x) < ordered(y) ? x : y;
    }

    public static short max(short x, short y) {
        if (isNaN(x)) return NaN;
        if (isNaN(y)) return NaN;

        if ((x & EXPONENT_SIGNIFICAND_MASK) == 0 && (y & EXPONENT_SIGNIFICAND_MASK) == 0) {
            return (x & SIGN_MASK) != 0 ? y : x;
        }
        return ordered(x) > ordered(y) ? x : y;
    }

    // Every comparator is FALSE when either operand is NaN, which is IEEE-754's
    // unordered rule and NOT what a naive bit compare produces.
    public static boolean less(short x, short y) {
        if (isNaN(x) || isNaN(y)) return false;
        return ordered(x) < ordered(y);
    }

    public static boolean lessEquals(short x, short y) {
        if (isNaN(x) || isNaN(y)) return false;
        return ordered(x) <= ordered(y);
    }

    public static boolean greater(short x, short y) {
        if (isNaN(x) || isNaN(y)) return false;
        return ordered(x) > ordered(y);
    }

    public static boolean greaterEquals(short x, short y) {
        if (isNaN(x) || isNaN(y)) return false;
        return ordered(x) >= ordered(y);
    }

    /** IEEE-754 equality: NaN equals nothing, and -0.0 EQUALS +0.0 (the
     *  `((x|y) & magnitude) == 0` term), unlike the boxed Half.equals(Object)
     *  above, which is bit equality. */
    public static boolean equals(short x, short y) {
        if (isNaN(x) || isNaN(y)) return false;
        return x == y || ((x | y) & EXPONENT_SIGNIFICAND_MASK) == 0;
    }

    /** 1 for a positive value INCLUDING +0, -1 for a negative one including -0. */
    public static int getSign(short h) {
        return (h & SIGN_MASK) == 0 ? 1 : -1;
    }

    /** The UNBIASED exponent: -15 for zero/subnormal, 16 for Inf/NaN. */
    public static int getExponent(short h) {
        return ((h >>> EXPONENT_SHIFT) & SHIFTED_EXPONENT_MASK) - EXPONENT_BIAS;
    }

    public static int getSignificand(short h) {
        return h & SIGNIFICAND_MASK;
    }

    /** True only for a normalized value: not zero, not subnormal, not Inf/NaN. */
    public static boolean isNormalized(short h) {
        return (h & EXPONENT_MASK) != 0 && (h & EXPONENT_MASK) != EXPONENT_MASK;
    }

    public static short parseHalf(String s) throws NumberFormatException {
        return toHalf(Float.parseFloat(s));
    }

    /** Hexadecimal-significand form, e.g. "0x1.0p0", "0x0.1p-14" for the
     *  smallest subnormal, "NaN"/"Infinity"/"-Infinity". FP16.toHexString. */
    public static String toHexString(short h) {
        StringBuilder o = new StringBuilder();

        int bits = h & 0xffff;
        int s = (bits >>> SIGN_SHIFT);
        int e = (bits >>> EXPONENT_SHIFT) & SHIFTED_EXPONENT_MASK;
        int m = (bits) & SIGNIFICAND_MASK;

        if (e == 0x1f) { // Infinite or NaN
            if (m == 0) {
                if (s != 0) o.append('-');
                o.append("Infinity");
            } else {
                o.append("NaN");
            }
        } else {
            if (s == 1) o.append('-');
            if (e == 0) {
                if (m == 0) {
                    o.append("0x0.0p0");
                } else {
                    o.append("0x0.");
                    String significand = Integer.toHexString(m);
                    // Trims a run of TWO OR MORE trailing zeros, so "0x1.0p0"
                    // keeps its single zero. Not a typo.
                    o.append(significand.replaceFirst("0{2,}$", ""));
                    o.append("p-14");
                }
            } else {
                o.append("0x1.");
                String significand = Integer.toHexString(m);
                o.append(significand.replaceFirst("0{2,}$", ""));
                o.append('p');
                o.append(Integer.toString(e - EXPONENT_BIAS));
            }
        }

        return o.toString();
    }

    /** AOSP Half.hashCode(short): canonicalize -0 and NaN so equal values hash
     *  equal. */
    public static int hashCode(short h) {
        if (isNaN(h)) return NaN & 0xffff;
        if (h == NEGATIVE_ZERO) return POSITIVE_ZERO;
        return h & 0xffff;
    }


    public static String toString(short h) {
        return Float.toString(toFloat(h));
    }
}
