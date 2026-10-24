// android.hardware.camera2.params.BlackLevelPattern -- the per-CFA-channel
// black level offsets of a raw sensor, the value type of
// CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL and
// CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// BlackLevelPattern.java (aosp-mirror/platform_frameworks_base,
// android16-release) verbatim in behaviour: the four offsets are stored in
// CFA order and indexed by the low bit of (row, column).
package android.hardware.camera2.params;

import java.util.Arrays;
import java.util.Objects;

public final class BlackLevelPattern {

    /** AOSP BlackLevelPattern.java:41 -- the number of offsets in a pattern. */
    public static final int COUNT = 4;

    private final int[] mCfaOffsets;

    /** AOSP :51 -- copies the first COUNT entries; rejects a short array. */
    public BlackLevelPattern(int[] offsets) {
        if (offsets == null) {
            throw new NullPointerException("Null offsets array passed to constructor");
        }
        if (offsets.length < COUNT) {
            throw new IllegalArgumentException("Invalid offsets array length");
        }
        mCfaOffsets = Arrays.copyOf(offsets, COUNT);
    }

    /** AOSP :74 -- ((row & 1) << 1) | (column & 1), i.e. the 2x2 CFA cell. */
    public int getOffsetForIndex(int column, int row) {
        if (row < 0 || column < 0) {
            throw new IllegalArgumentException("column, row arguments must be positive");
        }
        return mCfaOffsets[((row & 1) << 1) | (column & 1)];
    }

    /** AOSP :92 -- ArrayIndexOutOfBoundsException when the destination is short. */
    public void copyTo(int[] destination, int offset) {
        Objects.requireNonNull(destination, "destination must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("Null offset passed to copyTo");
        }
        if (destination.length - offset < COUNT) {
            throw new ArrayIndexOutOfBoundsException("destination too small to fit elements");
        }
        for (int i = 0; i < COUNT; ++i) {
            destination[offset + i] = mCfaOffsets[i];
        }
    }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof BlackLevelPattern) {
            return Arrays.equals(((BlackLevelPattern) obj).mCfaOffsets, mCfaOffsets);
        }
        return false;
    }

    @Override public int hashCode() { return Arrays.hashCode(mCfaOffsets); }

    @Override public String toString() {
        return String.format("BlackLevelPattern([%d, %d], [%d, %d])",
                mCfaOffsets[0], mCfaOffsets[1], mCfaOffsets[2], mCfaOffsets[3]);
    }
}
