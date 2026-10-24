// android.hardware.camera2.params.RecommendedStreamConfigurationMap -- the
// subset of a camera's stream configurations that the HAL RECOMMENDS for one
// usecase (preview, record, snapshot, ...). Returned by
// CameraCharacteristics.getRecommendedStreamConfigurationMap(int). API 29.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// RecommendedStreamConfigurationMap.java (aosp-mirror, android16-release): a
// thin, null-returning view over an underlying StreamConfigurationMap. Every
// method below delegates exactly as AOSP's does, including the two behaviours
// an app can actually branch on -- an EMPTY underlying array yields null rather
// than an empty Set, and the Class<T>-keyed queries return null / 0 when the
// recommendation does not cover implementation-defined (private) formats.
package android.hardware.camera2.params;

import android.util.Range;
import android.util.Size;
import android.view.Surface;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class RecommendedStreamConfigurationMap {

    /** AOSP :62 */
    public static final int MAX_USECASE_COUNT = 32;
    /** AOSP :70 */
    public static final int USECASE_PREVIEW = 0x0;
    /** AOSP :78 */
    public static final int USECASE_RECORD = 0x1;
    /** AOSP :86 */
    public static final int USECASE_VIDEO_SNAPSHOT = 0x2;
    /** AOSP :94 */
    public static final int USECASE_SNAPSHOT = 0x3;
    /** AOSP :103 */
    public static final int USECASE_ZSL = 0x4;
    /** AOSP :111 */
    public static final int USECASE_RAW = 0x5;
    /** AOSP :119 */
    public static final int USECASE_LOW_LATENCY_SNAPSHOT = 0x6;
    /** AOSP :127 */
    public static final int USECASE_10BIT_OUTPUT = 0x8;
    /** AOSP :135. @hide in AOSP; vendor usecases start here. */
    public static final int USECASE_VENDOR_START = 0x18;

    private final int mUsecase;
    private final boolean mSupportsPrivate;
    private final StreamConfigurationMap mRecommendedMap;

    /** AOSP :156. @hide there -- only CameraCharacteristics builds one. */
    RecommendedStreamConfigurationMap(StreamConfigurationMap recommendedMap, int usecase,
            boolean supportsPrivate) {
        mRecommendedMap = recommendedMap;
        mUsecase = usecase;
        mSupportsPrivate = supportsPrivate;
    }

    public int getRecommendedUseCase() { return mUsecase; }

    // AOSP :175 -- null, not an empty Set, when the array is null or empty.
    private static Set<Integer> unmodifiableIntegerSet(int[] intArray) {
        if (intArray != null && intArray.length > 0) {
            LinkedHashSet<Integer> set = new LinkedHashSet<>(intArray.length);
            for (int e : intArray) set.add(e);
            return Collections.unmodifiableSet(set);
        }
        return null;
    }

    private static Set<Size> unmodifiableSizeSet(Size[] sizeArray) {
        if (sizeArray != null && sizeArray.length > 0) {
            LinkedHashSet<Size> set = new LinkedHashSet<>(Arrays.asList(sizeArray));
            return Collections.unmodifiableSet(set);
        }
        return null;
    }

    private static Set<Range<Integer>> unmodifiableRangeSet(Range<Integer>[] rangeArray) {
        if (rangeArray != null && rangeArray.length > 0) {
            LinkedHashSet<Range<Integer>> set = new LinkedHashSet<>(Arrays.asList(rangeArray));
            return Collections.unmodifiableSet(set);
        }
        return null;
    }

    public Set<Integer> getOutputFormats() {
        return unmodifiableIntegerSet(mRecommendedMap.getOutputFormats());
    }

    public Set<Integer> getValidOutputFormatsForInput(int inputFormat) {
        return unmodifiableIntegerSet(mRecommendedMap.getValidOutputFormatsForInput(inputFormat));
    }

    public Set<Integer> getInputFormats() {
        return unmodifiableIntegerSet(mRecommendedMap.getInputFormats());
    }

    public Set<Size> getInputSizes(int format) {
        return unmodifiableSizeSet(mRecommendedMap.getInputSizes(format));
    }

    public boolean isOutputSupportedFor(int format) {
        return mRecommendedMap.isOutputSupportedFor(format);
    }

    public Set<Size> getOutputSizes(int format) {
        return unmodifiableSizeSet(mRecommendedMap.getOutputSizes(format));
    }

    public Set<Size> getHighSpeedVideoSizes() {
        return unmodifiableSizeSet(mRecommendedMap.getHighSpeedVideoSizes());
    }

    public Set<Range<Integer>> getHighSpeedVideoFpsRangesFor(Size size) {
        return unmodifiableRangeSet(mRecommendedMap.getHighSpeedVideoFpsRangesFor(size));
    }

    public Set<Range<Integer>> getHighSpeedVideoFpsRanges() {
        return unmodifiableRangeSet(mRecommendedMap.getHighSpeedVideoFpsRanges());
    }

    public Set<Size> getHighSpeedVideoSizesFor(Range<Integer> fpsRange) {
        return unmodifiableSizeSet(mRecommendedMap.getHighSpeedVideoSizesFor(fpsRange));
    }

    public Set<Size> getHighResolutionOutputSizes(int format) {
        return unmodifiableSizeSet(mRecommendedMap.getHighResolutionOutputSizes(format));
    }

    public long getOutputMinFrameDuration(int format, Size size) {
        return mRecommendedMap.getOutputMinFrameDuration(format, size);
    }

    public long getOutputStallDuration(int format, Size size) {
        return mRecommendedMap.getOutputStallDuration(format, size);
    }

    /** AOSP :392 -- null when the recommendation carries no private formats. */
    public <T> Set<Size> getOutputSizes(Class<T> klass) {
        if (mSupportsPrivate) {
            return unmodifiableSizeSet(mRecommendedMap.getOutputSizes(klass));
        }
        return null;
    }

    public <T> long getOutputMinFrameDuration(Class<T> klass, Size size) {
        if (mSupportsPrivate) {
            return mRecommendedMap.getOutputMinFrameDuration(klass, size);
        }
        return 0;
    }

    public <T> long getOutputStallDuration(Class<T> klass, Size size) {
        if (mSupportsPrivate) {
            return mRecommendedMap.getOutputStallDuration(klass, size);
        }
        return 0;
    }

    public boolean isOutputSupportedFor(Surface surface) {
        return mRecommendedMap.isOutputSupportedFor(surface);
    }
}
