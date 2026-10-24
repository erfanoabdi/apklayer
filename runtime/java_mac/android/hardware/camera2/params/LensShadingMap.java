// android.hardware.camera2.params.LensShadingMap -- the per-channel vignetting
// correction gain grid. Value type of CaptureResult.STATISTICS_LENS_SHADING_MAP.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// LensShadingMap.java (aosp-mirror, android16-release). The constructor is
// @hide in AOSP (only the camera framework builds one from HAL metadata); it is
// kept public here for the same reason AOSP keeps it public-but-hidden -- there
// is no other way to construct the value -- and it fabricates nothing on its
// own.
package android.hardware.camera2.params;

import android.hardware.camera2.utils.HashCodeHelpers;
import java.util.Arrays;
import java.util.Objects;

public final class LensShadingMap {

    /** AOSP LensShadingMap.java:43 -- gains are never attenuating. */
    public static final float MINIMUM_GAIN_FACTOR = 1.0f;

    private final int mRows;
    private final int mColumns;
    private final float[] mElements;

    public LensShadingMap(float[] elements, int rows, int columns) {
        if (rows <= 0) throw new IllegalArgumentException("rows must be positive");
        if (columns <= 0) throw new IllegalArgumentException("columns must be positive");
        mRows = rows;
        mColumns = columns;
        mElements = Objects.requireNonNull(elements, "elements must not be null");
        if (elements.length != getGainFactorCount()) {
            throw new IllegalArgumentException("elements must be " + getGainFactorCount()
                    + " length, received " + elements.length);
        }
        for (int i = 0; i < elements.length; i++) {
            if (Float.isNaN(elements[i]) || elements[i] < MINIMUM_GAIN_FACTOR) {
                throw new IllegalArgumentException(
                        "elements[" + i + "] is out of range of [" + MINIMUM_GAIN_FACTOR
                                + ", " + Float.MAX_VALUE + "]");
            }
        }
    }

    public int getRowCount() { return mRows; }
    public int getColumnCount() { return mColumns; }

    /** rows * columns * 4 (one gain per Bayer channel per grid cell). */
    public int getGainFactorCount() {
        return mRows * mColumns * RggbChannelVector.COUNT;
    }

    /** AOSP :117 */
    public float getGainFactor(int colorChannel, int column, int row) {
        if (colorChannel < 0 || colorChannel > RggbChannelVector.COUNT) {
            throw new IllegalArgumentException("colorChannel out of range");
        } else if (column < 0 || column >= mColumns) {
            throw new IllegalArgumentException("column out of range");
        } else if (row < 0 || row >= mRows) {
            throw new IllegalArgumentException("row out of range");
        }
        return mElements[colorChannel + (row * mColumns + column) * RggbChannelVector.COUNT];
    }

    /** AOSP :146 */
    public RggbChannelVector getGainFactorVector(int column, int row) {
        if (column < 0 || column >= mColumns) {
            throw new IllegalArgumentException("column out of range");
        } else if (row < 0 || row >= mRows) {
            throw new IllegalArgumentException("row out of range");
        }
        final int offset = (row * mColumns + column) * RggbChannelVector.COUNT;
        return new RggbChannelVector(
                mElements[RggbChannelVector.RED + offset],
                mElements[RggbChannelVector.GREEN_EVEN + offset],
                mElements[RggbChannelVector.GREEN_ODD + offset],
                mElements[RggbChannelVector.BLUE + offset]);
    }

    /** AOSP :176 -- the `length + offset` bound is AOSP's own, kept verbatim. */
    public void copyGainFactors(float[] destination, int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        Objects.requireNonNull(destination, "destination must not be null");
        if (destination.length + offset < getGainFactorCount()) {
            throw new ArrayIndexOutOfBoundsException("destination too small to fit elements");
        }
        System.arraycopy(mElements, 0, destination, offset, getGainFactorCount());
    }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof LensShadingMap) {
            final LensShadingMap other = (LensShadingMap) obj;
            return mRows == other.mRows && mColumns == other.mColumns
                    && Arrays.equals(mElements, other.mElements);
        }
        return false;
    }

    @Override public int hashCode() {
        int elemsHash = HashCodeHelpers.hashCode(mElements);
        return HashCodeHelpers.hashCode(mRows, mColumns, elemsHash);
    }

    @Override public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("LensShadingMap{");
        final String[] channelPrefix = {"R:(", "G_even:(", "G_odd:(", "B:("};
        for (int ch = 0; ch < RggbChannelVector.COUNT; ch++) {
            str.append(channelPrefix[ch]);
            for (int r = 0; r < mRows; r++) {
                str.append("[");
                for (int c = 0; c < mColumns; c++) {
                    str.append(getGainFactor(ch, c, r));
                    if (c < mColumns - 1) str.append(", ");
                }
                str.append("]");
                if (r < mRows - 1) str.append(", ");
            }
            str.append(")");
            if (ch < RggbChannelVector.COUNT - 1) str.append(", ");
        }
        str.append("}");
        return str.toString();
    }
}
