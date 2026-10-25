// android.hardware.camera2.params.MandatoryStreamCombination -- one stream
// combination the CDD requires a camera of a given hardware level to support.
// Element type of CameraCharacteristics.SCALER_MANDATORY_*_STREAM_COMBINATIONS.
// API 29.
//
// Mirrors the PUBLIC surface of frameworks/base/core/java/android/hardware/
// camera2/params/MandatoryStreamCombination.java (aosp-mirror,
// android16-release): getDescription/isReprocessable/getStreamsInformation and
// the nested MandatoryStreamInformation.
//
// What is deliberately NOT reproduced: AOSP's ~2000-line
// MandatoryStreamCombination.Builder, which DERIVES the mandatory tables from a
// camera's hardware level and capabilities. Those tables are a statement about
// what a real camera guarantees. Mac.apk's AVFoundation backend does not claim
// a CDD hardware level, so the characteristics keys resolve to null and an app
// takes its "no mandatory combination info" branch -- the same branch a device
// whose HAL omits the tag gives it. Synthesising a table here would tell an app
// that a combination is guaranteed to configure when nothing guarantees it.
package android.hardware.camera2.params;

import android.graphics.ImageFormat;
import android.hardware.camera2.utils.HashCodeHelpers;
import android.util.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MandatoryStreamCombination {

    private final ArrayList<MandatoryStreamInformation> mStreamsInformation = new ArrayList<>();
    private final String mDescription;
    private final boolean mIsReprocessable;

    /** AOSP :380. @hide there (only the internal Builder calls it). */
    public MandatoryStreamCombination(List<MandatoryStreamInformation> streamsInformation,
            String description, boolean isReprocessable) {
        if (streamsInformation.isEmpty()) {
            throw new IllegalArgumentException("Empty stream information");
        }
        mStreamsInformation.addAll(streamsInformation);
        mDescription = description;
        mIsReprocessable = isReprocessable;
    }

    /** AOSP :406 */
    public CharSequence getDescription() { return mDescription; }

    /** AOSP :418 */
    public boolean isReprocessable() { return mIsReprocessable; }

    /** AOSP :428 */
    public List<MandatoryStreamInformation> getStreamsInformation() {
        return Collections.unmodifiableList(mStreamsInformation);
    }

    // AOSP :432 -- note it compares mDescription by REFERENCE (!=), because the
    // descriptions are interned literals from its own table. Kept verbatim.
    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof MandatoryStreamCombination) {
            final MandatoryStreamCombination other = (MandatoryStreamCombination) obj;
            if ((mDescription != other.mDescription)
                    || (mIsReprocessable != other.mIsReprocessable)
                    || (mStreamsInformation.size() != other.mStreamsInformation.size())) {
                return false;
            }
            return mStreamsInformation.equals(other.mStreamsInformation);
        }
        return false;
    }

    @Override public int hashCode() {
        return HashCodeHelpers.hashCode(Boolean.hashCode(mIsReprocessable),
                mDescription.hashCode(), mStreamsInformation.hashCode());
    }

    /** AOSP :60 -- one stream inside a mandatory combination. */
    public static final class MandatoryStreamInformation {
        private final int mFormat;
        private final ArrayList<Size> mAvailableSizes = new ArrayList<Size>();
        private final boolean mIsInput;
        private final boolean mIsUltraHighResolution;
        private final boolean mIsMaximumSize;
        private final boolean mIs10BitCapable;
        private final long mStreamUseCase;

        // CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT
        private static final long STREAM_USE_CASE_DEFAULT = 0L;

        public MandatoryStreamInformation(List<Size> availableSizes, int format,
                boolean isMaximumSize) {
            this(availableSizes, format, isMaximumSize, false, false);
        }

        public MandatoryStreamInformation(List<Size> availableSizes, int format,
                boolean isMaximumSize, boolean isInput) {
            this(availableSizes, format, isMaximumSize, isInput, false);
        }

        public MandatoryStreamInformation(List<Size> availableSizes, int format,
                boolean isMaximumSize, boolean isInput, boolean isUltraHighResolution) {
            this(availableSizes, format, isMaximumSize, isInput, isUltraHighResolution, false);
        }

        public MandatoryStreamInformation(List<Size> availableSizes, int format,
                boolean isMaximumSize, boolean isInput, boolean isUltraHighResolution,
                boolean is10BitCapable) {
            this(availableSizes, format, isMaximumSize, isInput, isUltraHighResolution,
                    is10BitCapable, STREAM_USE_CASE_DEFAULT);
        }

        public MandatoryStreamInformation(List<Size> availableSizes, int format,
                boolean isMaximumSize, boolean isInput, boolean isUltraHighResolution,
                boolean is10BitCapable, long streamUseCase) {
            if (availableSizes.isEmpty()) {
                throw new IllegalArgumentException("No available sizes");
            }
            mAvailableSizes.addAll(availableSizes);
            // AOSP calls StreamConfigurationMap.checkArgumentFormat here, which
            // validates against its internal ImageFormat/PixelFormat table. This
            // tree carries no such table, so the format is stored as given
            // rather than rejected against an incomplete list -- rejecting a
            // format we simply do not know about would be a fabricated failure.
            mFormat = format;
            mIsMaximumSize = isMaximumSize;
            mIsInput = isInput;
            mIsUltraHighResolution = isUltraHighResolution;
            mIs10BitCapable = is10BitCapable;
            mStreamUseCase = streamUseCase;
        }

        public boolean isInput() { return mIsInput; }
        public boolean isUltraHighResolution() { return mIsUltraHighResolution; }
        public boolean isMaximumSize() { return mIsMaximumSize; }
        public boolean is10BitCapable() { return mIs10BitCapable; }

        public List<Size> getAvailableSizes() {
            return Collections.unmodifiableList(mAvailableSizes);
        }

        /** AOSP :276 -- a 10-bit-capable P010 stream reports its 8-bit face
         *  here; get10BitFormat() is the one that reports P010. */
        public int getFormat() {
            if (mIs10BitCapable && (mFormat == ImageFormat.YCBCR_P010)) {
                return ImageFormat.YUV_420_888;
            }
            return mFormat;
        }

        /** AOSP :296 -- UnsupportedOperationException when not 10-bit capable. */
        public int get10BitFormat() {
            if (!mIs10BitCapable) {
                throw new UnsupportedOperationException("10-bit output is not supported!");
            }
            return mFormat;
        }

        public long getStreamUseCase() { return mStreamUseCase; }

        // AOSP :327 -- mIsMaximumSize and mIs10BitCapable are deliberately NOT
        // compared; kept as AOSP has it.
        @Override public boolean equals(Object obj) {
            if (obj == null) return false;
            if (this == obj) return true;
            if (obj instanceof MandatoryStreamInformation) {
                final MandatoryStreamInformation other = (MandatoryStreamInformation) obj;
                if ((mFormat != other.mFormat) || (mIsInput != other.mIsInput)
                        || (mIsUltraHighResolution != other.mIsUltraHighResolution)
                        || (mStreamUseCase != other.mStreamUseCase)
                        || (mAvailableSizes.size() != other.mAvailableSizes.size())) {
                    return false;
                }
                return mAvailableSizes.equals(other.mAvailableSizes);
            }
            return false;
        }

        @Override public int hashCode() {
            return HashCodeHelpers.hashCode(mFormat, Boolean.hashCode(mIsInput),
                    Boolean.hashCode(mIsUltraHighResolution), mAvailableSizes.hashCode(),
                    Long.hashCode(mStreamUseCase));
        }
    }
}
