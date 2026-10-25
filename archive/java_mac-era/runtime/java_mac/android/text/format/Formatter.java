// android.text.format.Formatter -- byte-count / IP-address formatter.
// AOSP source:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/text/format/Formatter.java
//
// Glide's MemorySizeCalculator.toMb logs the chosen pool size using
// Formatter.formatFileSize(Context, long). When that method is absent,
// Glide.initializeGlide throws NoSuchMethodError on the first
// Glide.with(...) call, which propagates up and aborts every
// RecyclerView bind (no name/summary/icon set on any card).
//
// The AOSP impl is locale-aware via ICU MeasureFormat; we use a simple
// SI-units (base-1000) formatter sufficient for human-readable logs
// and any "Used: 1.2 MB" UI strings that callers might surface.
package android.text.format;

import android.content.Context;

import java.util.Locale;

public final class Formatter {

    /** AOSP flag: base-1000 SI units (kB, MB, GB). Default. */
    public static final int FLAG_SI_UNITS  = 1 << 2;
    /** AOSP flag: base-1024 IEC units (KiB, MiB, GiB). */
    public static final int FLAG_IEC_UNITS = 1 << 3;
    /** AOSP flag: drop the fractional digit (e.g. "1 MB" not "1.3 MB"). */
    public static final int FLAG_SHORTER   = 1 << 0;
    /** AOSP flag: drop the unit suffix entirely. */
    public static final int FLAG_CALCULATE_ROUNDED = 1 << 1;

    /** AOSP declares no constructor for Formatter, so the implicit PUBLIC
     *  no-arg one is public API (`ctor public Formatter()` in the signature
     *  file). Ours was private, which is a NoSuchMethodError for
     *  `new Formatter()`. The class is stateless -- every method is static --
     *  so an instance does nothing, exactly as on a device. */
    public Formatter() {}

    /** Format a byte count to a human-readable size string.
     *  Mirrors AOSP's overload that defaults to SI units (base-1000). */
    public static String formatFileSize(Context context, long sizeBytes) {
        return formatFileSize(context, sizeBytes, FLAG_SI_UNITS);
    }

    /** Format a byte count to a SHORTER human-readable size string
     *  (drops the fractional digit). */
    public static String formatShortFileSize(Context context, long sizeBytes) {
        return formatFileSize(context, sizeBytes, FLAG_SI_UNITS | FLAG_SHORTER);
    }

    /** Format a byte count to a human-readable size string with the
     *  given flag set. Implements the visible portion of AOSP's
     *  Formatter.formatBytes (the rounded mantissa + unit suffix).
     *  AOSP (frameworks/base core/java/android/text/format/Formatter.java:
     *  100-107) returns "" for a null Context. */
    public static String formatFileSize(Context context, long sizeBytes, int flags) {
        if (context == null) return "";
        BytesResult r = computeBytesResult(sizeBytes, flags);
        return r.value + " " + r.units;
    }

    /** AOSP Formatter.BytesResult: the split value + units + rounded bytes,
     *  returned by formatBytes for callers that want the pieces separately. */
    public static class BytesResult {
        public final String value;
        public final String units;
        public final long roundedBytes;
        public BytesResult(String value, String units, long roundedBytes) {
            this.value = value;
            this.units = units;
            this.roundedBytes = roundedBytes;
        }
    }

    /** AOSP Formatter.formatBytes: like formatFileSize but exposes the value
     *  and unit separately plus the rounded byte count. */
    public static BytesResult formatBytes(android.content.res.Resources res,
                                          long sizeBytes, int flags) {
        return computeBytesResult(sizeBytes, flags);
    }

    private static final String[] SI_UNITS  = { "B", "kB", "MB", "GB", "TB", "PB" };
    private static final String[] IEC_UNITS = { "B", "KiB", "MiB", "GiB", "TiB", "PiB" };

    /** Shared core of formatFileSize/formatBytes, ported from AOSP
     *  Formatter.formatBytes (frameworks/base core/java/android/text/format/
     *  Formatter.java:125-199). Two behaviors the previous impl got wrong,
     *  both verified against that source:
     *
     *  1. The unit bumps once the CURRENT value exceeds 900, not once it
     *     reaches the unit base (1000/1024): "if (result > 900) { ...;
     *     result = result / unit; }", five times (B->kB->MB->GB->TB->PB).
     *     900,000 bytes is 900 kB by the old "divide while >= 1000" rule,
     *     which stays in the kB tier; the REAL threshold has ALREADY bumped
     *     it to the MB tier once the kB value passed 900, so it prints
     *     "0.9 MB", never a value drifting up toward an awkward "999 kB".
     *  2. The rounding-precision table is not a flat "SHORTER means integer,
     *     otherwise one decimal". AOSP: result<1 always gets 2 decimals
     *     (even when SHORTER); [1,10) gets 2 decimals normally, 1 decimal
     *     when SHORTER; [10,100) gets 2 decimals normally, 0 decimals when
     *     SHORTER; >=100 (or the byte tier) always gets 0 decimals. */
    private static BytesResult computeBytesResult(long sizeBytes, int flags) {
        boolean iec = (flags & FLAG_IEC_UNITS) != 0;
        boolean shorter = (flags & FLAG_SHORTER) != 0;
        int unit = iec ? 1024 : 1000;
        String[] units = iec ? IEC_UNITS : SI_UNITS;
        boolean isNegative = sizeBytes < 0;
        double result = isNegative ? -sizeBytes : sizeBytes;
        long mult = 1;
        int unitIdx = 0;
        while (result > 900 && unitIdx < units.length - 1) {
            mult *= unit;
            result /= unit;
            unitIdx++;
        }
        final int roundFactor;
        final String roundFormat;
        if (mult == 1 || result >= 100) {
            roundFactor = 1; roundFormat = "%.0f";
        } else if (result < 1) {
            roundFactor = 100; roundFormat = "%.2f";
        } else if (result < 10) {
            if (shorter) { roundFactor = 10; roundFormat = "%.1f"; }
            else         { roundFactor = 100; roundFormat = "%.2f"; }
        } else { // 10 <= result < 100
            if (shorter) { roundFactor = 1; roundFormat = "%.0f"; }
            else         { roundFactor = 100; roundFormat = "%.2f"; }
        }
        if (isNegative) result = -result;
        String value = String.format(Locale.ROOT, roundFormat, result);
        long roundedBytes = (flags & FLAG_CALCULATE_ROUNDED) == 0 ? 0
            : (Math.round(result * roundFactor) * mult / roundFactor);
        return new BytesResult(value, units[unitIdx], roundedBytes);
    }

    /** Format a 4-byte IPv4 address (legacy AOSP API, deprecated but
     *  still referenced by some app code). */
    public static String formatIpAddress(int ipv4Address) {
        return (ipv4Address & 0xFF) + "."
             + ((ipv4Address >> 8) & 0xFF) + "."
             + ((ipv4Address >> 16) & 0xFF) + "."
             + ((ipv4Address >> 24) & 0xFF);
    }
}
