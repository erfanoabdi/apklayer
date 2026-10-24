// android.hardware.DataSpace -- how to interpret the pixels in a buffer: which
// colour primaries (STANDARD_*), which transfer function (TRANSFER_*), and
// which numeric range (RANGE_*), plus named combinations for the common colour
// spaces (DATASPACE_SRGB, DATASPACE_DISPLAY_P3, DATASPACE_BT2020_PQ ...).
//
// Mirrors frameworks/base/core/java/android/hardware/DataSpace.java
// (android16-release). Every value below is taken from the android16
// signature file (core/api/current.txt) rather than recomputed, because these
// are BIT-PACKED constants that travel across the JNI boundary into
// ImageReader/ImageWriter/HardwareBuffer/SurfaceTexture: a value that is one
// bit wrong describes a different colour space and produces a wrong-looking
// image with nothing to point at.
//
// This class is pure arithmetic -- no device state is involved -- so it is a
// complete and faithful implementation, not a stand-in. pack/getStandard/
// getTransfer/getRange are exactly AOSP's, including pack()'s three
// IllegalArgumentException guards, which are how an app finds out it ORed a
// value into the wrong field.
package android.hardware;

public final class DataSpace {

    // AOSP's private field masks (DataSpace.java:85/241/357). They are what
    // pack() validates against and what the three getters extract with.
    private static final int STANDARD_MASK = 63 << 16;
    private static final int TRANSFER_MASK = 31 << 22;
    private static final int RANGE_MASK = 7 << 27;

    // Colour primaries.
    public static final int STANDARD_ADOBE_RGB                     = 0xb0000;
    public static final int STANDARD_BT2020                        = 0x60000;
    public static final int STANDARD_BT2020_CONSTANT_LUMINANCE     = 0x70000;
    public static final int STANDARD_BT470M                        = 0x80000;
    public static final int STANDARD_BT601_525                     = 0x40000;
    public static final int STANDARD_BT601_525_UNADJUSTED          = 0x50000;
    public static final int STANDARD_BT601_625                     = 0x20000;
    public static final int STANDARD_BT601_625_UNADJUSTED          = 0x30000;
    public static final int STANDARD_BT709                         = 0x10000;
    public static final int STANDARD_DCI_P3                        = 0xa0000;
    public static final int STANDARD_FILM                          = 0x90000;
    public static final int STANDARD_UNSPECIFIED                   = 0x0;

    // Transfer functions.
    public static final int TRANSFER_GAMMA2_2                      = 0x1000000;
    public static final int TRANSFER_GAMMA2_6                      = 0x1400000;
    public static final int TRANSFER_GAMMA2_8                      = 0x1800000;
    public static final int TRANSFER_HLG                           = 0x2000000;
    public static final int TRANSFER_LINEAR                        = 0x400000;
    public static final int TRANSFER_SMPTE_170M                    = 0xc00000;
    public static final int TRANSFER_SRGB                          = 0x800000;
    public static final int TRANSFER_ST2084                        = 0x1c00000;
    public static final int TRANSFER_UNSPECIFIED                   = 0x0;

    // Numeric ranges.
    public static final int RANGE_EXTENDED                         = 0x18000000;
    public static final int RANGE_FULL                             = 0x8000000;
    public static final int RANGE_LIMITED                          = 0x10000000;
    public static final int RANGE_UNSPECIFIED                      = 0x0;

    // Named combinations.
    public static final int DATASPACE_ADOBE_RGB                    = 0x90b0000;
    public static final int DATASPACE_BT2020                       = 0x8c60000;
    public static final int DATASPACE_BT2020_HLG                   = 0xa060000;
    public static final int DATASPACE_BT2020_PQ                    = 0x9c60000;
    public static final int DATASPACE_BT601_525                    = 0x10c40000;
    public static final int DATASPACE_BT601_625                    = 0x10c20000;
    public static final int DATASPACE_BT709                        = 0x10c10000;
    public static final int DATASPACE_DCI_P3                       = 0x94a0000;
    public static final int DATASPACE_DEPTH                        = 0x1000;
    public static final int DATASPACE_DISPLAY_BT2020               = 0x8860000;
    public static final int DATASPACE_DISPLAY_P3                   = 0x88a0000;
    public static final int DATASPACE_DYNAMIC_DEPTH                = 0x1002;
    public static final int DATASPACE_HEIF                         = 0x1004;
    public static final int DATASPACE_HEIF_ULTRAHDR                = 0x1006;
    public static final int DATASPACE_JFIF                         = 0x8c20000;
    public static final int DATASPACE_JPEG_R                       = 0x1005;
    public static final int DATASPACE_SCRGB                        = 0x18810000;
    public static final int DATASPACE_SCRGB_LINEAR                 = 0x18410000;
    public static final int DATASPACE_SRGB                         = 0x8810000;
    public static final int DATASPACE_SRGB_LINEAR                  = 0x8410000;
    public static final int DATASPACE_UNKNOWN                      = 0x0;

    private DataSpace() { }

    /**
     * AOSP DataSpace.java:719. Combine a standard, a transfer and a range into
     * one dataspace value.
     *
     * The three guards are AOSP's and are not defensive noise: each checks that
     * the argument has bits ONLY inside its own field, so passing a TRANSFER_*
     * where a STANDARD_* belongs is caught here instead of silently producing a
     * dataspace that names a colour space nobody intended.
     */
    public static int pack(int standard, int transfer, int range) {
        if ((standard & STANDARD_MASK) != standard) {
            throw new IllegalArgumentException("Invalid standard " + standard);
        }
        if ((transfer & TRANSFER_MASK) != transfer) {
            throw new IllegalArgumentException("Invalid transfer " + transfer);
        }
        if ((range & RANGE_MASK) != range) {
            throw new IllegalArgumentException("Invalid range " + range);
        }
        return standard | transfer | range;
    }

    /** AOSP :739 -- the STANDARD_* component, already shifted into place, so
     *  the result compares directly against a STANDARD_* constant. */
    public static int getStandard(int dataSpace) {
        return dataSpace & STANDARD_MASK;
    }

    /** AOSP :751. */
    public static int getTransfer(int dataSpace) {
        return dataSpace & TRANSFER_MASK;
    }

    /** AOSP :763. */
    public static int getRange(int dataSpace) {
        return dataSpace & RANGE_MASK;
    }
}
