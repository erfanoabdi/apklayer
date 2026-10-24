// android.hardware.camera2.params.MultiResolutionStreamConfigurationMap -- the
// per-format multi-resolution stream sizes of a logical multi-camera. Value
// type of CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP.
// API 31.
//
// Public surface mirrored from frameworks/base/core/java/android/hardware/
// camera2/params/MultiResolutionStreamConfigurationMap.java (aosp-mirror,
// android16-release): getOutputFormats/getInputFormats/getOutputInfo/getInputInfo.
//
// AOSP's constructor takes Map<String, StreamConfiguration[]>, where
// StreamConfiguration is @hide and carries the HAL's internal format ids. This
// tree has no StreamConfiguration, so the constructor here takes the already
// grouped MultiResolutionStreamInfo lists instead. That is a construction-side
// difference only: everything an app can observe goes through the four public
// getters, which behave exactly as AOSP's do, including
// Collections.emptyList() for a format with no entries.
//
// Nothing constructs one today: the AVFoundation backend advertises no logical
// multi-camera, so SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP resolves to
// null and an app takes its "no multi-resolution support" branch -- which is
// what a real single-camera device gives it too.
package android.hardware.camera2.params;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MultiResolutionStreamConfigurationMap {

    private final Map<Integer, List<MultiResolutionStreamInfo>> mOutputConfigs;
    private final Map<Integer, List<MultiResolutionStreamInfo>> mInputConfigs;

    MultiResolutionStreamConfigurationMap(
            Map<Integer, List<MultiResolutionStreamInfo>> outputConfigs,
            Map<Integer, List<MultiResolutionStreamInfo>> inputConfigs) {
        mOutputConfigs = outputConfigs != null ? outputConfigs : new LinkedHashMap<>();
        mInputConfigs = inputConfigs != null ? inputConfigs : new LinkedHashMap<>();
    }

    /** AOSP :141 */
    public int[] getOutputFormats() { return formatsOf(mOutputConfigs); }

    /** AOSP :154 */
    public int[] getInputFormats() { return formatsOf(mInputConfigs); }

    private static int[] formatsOf(Map<Integer, List<MultiResolutionStreamInfo>> configs) {
        int[] formats = new int[configs.size()];
        int i = 0;
        for (Integer format : configs.keySet()) {
            formats[i++] = format;
        }
        return formats;
    }

    /** AOSP :177 -- an unsupported format yields an empty collection, not null. */
    public Collection<MultiResolutionStreamInfo> getOutputInfo(int format) {
        return infoOf(mOutputConfigs, format);
    }

    /** AOSP :191 */
    public Collection<MultiResolutionStreamInfo> getInputInfo(int format) {
        return infoOf(mInputConfigs, format);
    }

    private static Collection<MultiResolutionStreamInfo> infoOf(
            Map<Integer, List<MultiResolutionStreamInfo>> configs, int format) {
        List<MultiResolutionStreamInfo> list = configs.get(format);
        if (list == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(list);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MultiResolutionStreamConfigurationMap)) return false;
        MultiResolutionStreamConfigurationMap other = (MultiResolutionStreamConfigurationMap) obj;
        return mOutputConfigs.equals(other.mOutputConfigs)
                && mInputConfigs.equals(other.mInputConfigs);
    }

    @Override public int hashCode() {
        return mOutputConfigs.hashCode() * 31 + mInputConfigs.hashCode();
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder("MultiResolutionStreamConfigurationMap(");
        appendConfigurations(sb, true);
        sb.append("; ");
        appendConfigurations(sb, false);
        sb.append(")");
        return sb.toString();
    }

    private void appendConfigurations(StringBuilder sb, boolean output) {
        sb.append(output ? "Outputs(" : "Inputs(");
        for (int format : formatsOf(output ? mOutputConfigs : mInputConfigs)) {
            for (MultiResolutionStreamInfo info : infoOf(
                    output ? mOutputConfigs : mInputConfigs, format)) {
                sb.append("[format:").append(format)
                        .append(", width:").append(info.getWidth())
                        .append(", height:").append(info.getHeight())
                        .append(", id:").append(info.getPhysicalCameraId()).append("], ");
            }
        }
        sb.append(")");
    }

    // Kept so that a future backend which does enumerate multi-resolution
    // streams has the grouping helper AOSP's constructor performs inline.
    static Map<Integer, List<MultiResolutionStreamInfo>> group(
            Map<Integer, List<MultiResolutionStreamInfo>> source) {
        Map<Integer, List<MultiResolutionStreamInfo>> dest = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<Integer, List<MultiResolutionStreamInfo>> e : source.entrySet()) {
                dest.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
        return dest;
    }
}
