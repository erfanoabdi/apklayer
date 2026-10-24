// android.util.DisplayMetrics -- public field bag describing the screen.
// Apps inspect these directly. We populate them from the launcher's
// "phone size" config at the launch screen's density: 2.0 (xhdpi) on a
// Retina screen, 1.0 (mdpi) on a 1x monitor -- one dp per Mac point (v1.0.2187).
package android.util;

public class DisplayMetrics {
    // Density buckets, AOSP DisplayMetrics.java:45-219 (android14-release).
    // The full ladder, not just the six "named" tiers: the in-between values
    // are ordinary public SDK constants and app code does reference them
    // (DENSITY_420 and DENSITY_560 are common Pixel-class buckets).
    public static final int DENSITY_LOW       = 120;
    public static final int DENSITY_140       = 140;
    public static final int DENSITY_MEDIUM    = 160;
    public static final int DENSITY_180       = 180;
    public static final int DENSITY_200       = 200;
    public static final int DENSITY_TV        = 213;
    public static final int DENSITY_220       = 220;
    public static final int DENSITY_HIGH      = 240;
    public static final int DENSITY_260       = 260;
    public static final int DENSITY_280       = 280;
    public static final int DENSITY_300       = 300;
    public static final int DENSITY_XHIGH     = 320;
    public static final int DENSITY_340       = 340;
    public static final int DENSITY_360       = 360;
    public static final int DENSITY_400       = 400;
    public static final int DENSITY_420       = 420;
    public static final int DENSITY_440       = 440;
    public static final int DENSITY_450       = 450;
    public static final int DENSITY_XXHIGH    = 480;
    public static final int DENSITY_520       = 520;
    public static final int DENSITY_560       = 560;
    public static final int DENSITY_600       = 600;
    public static final int DENSITY_XXXHIGH   = 640;
    public static final int DENSITY_DEFAULT   = DENSITY_MEDIUM;

    /** AOSP DisplayMetrics.java:227-240. "The device's current density. This
     *  value reflects any changes made to the device density." @Deprecated and
     *  @hide-annotated on AOSP but NOT final -- the platform reassigns it when
     *  a runtime density override is applied.
     *
     *  Mac.apk applies no runtime density override, so it never diverges from
     *  DENSITY_DEVICE_STABLE. It is declared non-final to match AOSP's shape:
     *  guest code (and the platform's own compat layer) can assign it. */
    @Deprecated
    public static int DENSITY_DEVICE = getDeviceDensity();

    /** AOSP DisplayMetrics.java:242-249:
     *      public static final int DENSITY_DEVICE_STABLE = getDeviceDensity();
     *  with the javadoc "The device's stable density. This value is constant at
     *  run time and may not reflect the current display density. To obtain the
     *  current density for a specific display, use densityDpi."
     *
     *  WHAT IT MEANS, and why our answer is honest rather than a placeholder:
     *  it is the density the DEVICE was manufactured at, as opposed to
     *  {@link #densityDpi}, which is the CURRENT density of a particular
     *  display and can change (user display-size setting, a resized freeform
     *  window, a secondary display). Apps use it as the invariant denominator
     *  when they need a size that must not move when the display is rescaled.
     *
     *  Mac.apk models exactly ONE display whose density is decided once, at
     *  launch, from the screen the app opens on (v1.0.2187; 320 on Retina, 160
     *  on a 1x monitor, so one dp is one Mac point on both), and has no
     *  display-size setting and no runtime density override, so the device's
     *  stable density and the current density are genuinely the same number
     *  here. It is read through the same ro.sf.lcd_density property AOSP
     *  reads, so `getprop` and this field agree.
     *
     *  WHERE WE STILL DIFFER FROM A DEVICE: on a real phone this is whatever
     *  that phone shipped with (Pixel 8 = 420, Pixel 8 Pro = 480, an emulator's
     *  Pixel 4 AVD = 440), and an app that computes a physical size from it
     *  gets a phone-specific answer. Ours follows the Mac screen's POINT scale,
     *  not its physical pixels per inch.
     *
     *  MEASURED need: com.zhiliaoapp.musically 46.4.3 reads it in a constructor
     *  reached from a main-looper Runnable and threw `NoSuchFieldError: Class
     *  android.util.DisplayMetrics does not have member field
     *  'int DENSITY_DEVICE_STABLE'` on every dispatch. */
    public static final int DENSITY_DEVICE_STABLE = getDeviceDensity();

    /** AOSP DisplayMetrics.java:455-463, mirrored including the property it
     *  reads and the fallback:
     *      private static int getDeviceDensity() {
     *          return SystemProperties.getInt("qemu.sf.lcd_density",
     *                  SystemProperties.getInt("ro.sf.lcd_density",
     *                                          DENSITY_DEFAULT));
     *      }
     *  Our SystemProperties answers ro.sf.lcd_density with the launch density
     *  (320 or 160, see above) and leaves qemu.sf.lcd_density unset (it is an
     *  EMULATOR-only override, and Mac.apk is not an emulator). The DENSITY_DEFAULT (160)
     *  fallback is kept rather than short-circuited so that if the property
     *  ever goes missing the failure is AOSP's failure, not a new one. */
    private static int getDeviceDensity() {
        return android.os.SystemProperties.getInt("qemu.sf.lcd_density",
                android.os.SystemProperties.getInt("ro.sf.lcd_density",
                                                   DENSITY_DEFAULT));
    }

    /** Mac.apk: the device density as px per dp (DENSITY_DEVICE_STABLE / 160)
     *  -- 2.0 on a Retina screen, 1.0 on a 1x monitor. For framework code that
     *  needs a density with no Resources in hand; anything that has a
     *  Resources reads its DisplayMetrics instead, as AOSP does. */
    public static float macapkDeviceDensity() {
        return DENSITY_DEVICE_STABLE / (float) DENSITY_DEFAULT;
    }

    public int   widthPixels;
    public int   heightPixels;
    // The device density (v1.0.2187; was a fixed 2.0 / 320). AOSP leaves these
    // zero until setToDefaults; ours start at the defaults so a bare
    // `new DisplayMetrics()` is already a usable answer.
    public float density       = DENSITY_DEVICE_STABLE / (float) DENSITY_DEFAULT;
    public int   densityDpi    = DENSITY_DEVICE_STABLE;
    public float scaledDensity = DENSITY_DEVICE_STABLE / (float) DENSITY_DEFAULT;
    public float xdpi          = DENSITY_DEVICE_STABLE;
    public float ydpi          = DENSITY_DEVICE_STABLE;

    /** AOSP DisplayMetrics.equals(DisplayMetrics): field-wise comparison. Note
     *  this is an OVERLOAD, not an override of Object.equals -- AOSP declares
     *  both, and the typed one is what callers bind to at compile time.
     *  Referenced by YouTube. */
    public boolean equals(DisplayMetrics other) {
        return other != null
            && widthPixels   == other.widthPixels
            && heightPixels  == other.heightPixels
            && density       == other.density
            && densityDpi    == other.densityDpi
            && scaledDensity == other.scaledDensity
            && xdpi          == other.xdpi
            && ydpi          == other.ydpi;
    }

    @Override public boolean equals(Object o) {
        return (o instanceof DisplayMetrics) && equals((DisplayMetrics) o);
    }

    @Override public int hashCode() {
        // Consistent with equals(): same seven fields, in the same order.
        int h = widthPixels;
        h = 31 * h + heightPixels;
        h = 31 * h + Float.floatToIntBits(density);
        h = 31 * h + densityDpi;
        h = 31 * h + Float.floatToIntBits(scaledDensity);
        h = 31 * h + Float.floatToIntBits(xdpi);
        h = 31 * h + Float.floatToIntBits(ydpi);
        return h;
    }

    public DisplayMetrics() {}

    public void setTo(DisplayMetrics o) {
        widthPixels = o.widthPixels;
        heightPixels = o.heightPixels;
        density = o.density;
        densityDpi = o.densityDpi;
        scaledDensity = o.scaledDensity;
        xdpi = o.xdpi;
        ydpi = o.ydpi;
    }

    /** AOSP DisplayMetrics.setToDefaults (android14-release):
     *      density = DENSITY_DEVICE / (float) DENSITY_DEFAULT;
     *      densityDpi = DENSITY_DEVICE; scaledDensity = density;
     *      xdpi = DENSITY_DEVICE; ydpi = DENSITY_DEVICE;
     *  The 720x1280 size is ours (AOSP zeroes it); kept as before. */
    public void setToDefaults() {
        widthPixels = 720;
        heightPixels = 1280;
        density = DENSITY_DEVICE / (float) DENSITY_DEFAULT;
        densityDpi = DENSITY_DEVICE;
        scaledDensity = density;
        xdpi = DENSITY_DEVICE;
        ydpi = DENSITY_DEVICE;
    }
}
