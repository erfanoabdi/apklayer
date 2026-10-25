// android.hardware.camera2.CameraCharacteristics -- immutable per-camera
// capability metadata returned by CameraManager.getCameraCharacteristics.
// Mac.apk has no camera, so getCameraCharacteristics throws
// CameraAccessException and this is never handed out -- but the type +
// Key constants must exist for camera apps to compile (they reference
// keys like LENS_FACING / SENSOR_ORIENTATION even before opening).
package android.hardware.camera2;

import android.graphics.Rect;
import android.util.Range;
import android.util.SizeF;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// android-30 android.jar ground truth: `public final class
// CameraCharacteristics extends CameraMetadata<CameraCharacteristics$Key<?>>`.
// The superclass is linkable ABI; getKeys() below overrides the base's.
public final class CameraCharacteristics extends CameraMetadata<CameraCharacteristics.Key<?>> {
    public static final class Key<T> {
        private final String name;
        private final Class<T> type;
        public Key(String name, Class<T> type) { this.name = name; this.type = type; }
        public String getName() { return name; }
        @Override public int hashCode() { return name.hashCode(); }
        @Override public boolean equals(Object o) {
            return (o instanceof Key) && ((Key<?>) o).name.equals(name);
        }
        @Override public String toString() { return "CameraCharacteristics.Key(" + name + ")"; }
    }

    public static final Key<Integer> LENS_FACING =
            new Key<>("android.lens.facing", Integer.class);
    public static final Key<Integer> SENSOR_ORIENTATION =
            new Key<>("android.sensor.orientation", Integer.class);
    public static final Key<Boolean> FLASH_INFO_AVAILABLE =
            new Key<>("android.flash.info.available", Boolean.class);
    public static final Key<Integer> INFO_SUPPORTED_HARDWARE_LEVEL =
            new Key<>("android.info.supportedHardwareLevel", Integer.class);
    public static final Key<int[]> REQUEST_AVAILABLE_CAPABILITIES =
            new Key<>("android.request.availableCapabilities", int[].class);
    public static final Key<int[]> CONTROL_AF_AVAILABLE_MODES =
            new Key<>("android.control.afAvailableModes", int[].class);
    public static final Key<int[]> CONTROL_AE_AVAILABLE_MODES =
            new Key<>("android.control.aeAvailableModes", int[].class);
    // AOSP core/java/android/hardware/camera2/CameraCharacteristics.java (main branch,
    // fetched verbatim via raw.githubusercontent.com), exact declarations:
    //   L4010 Key<Rect> SENSOR_INFO_ACTIVE_ARRAY_SIZE =
    //           new Key<>("android.sensor.info.activeArraySize", Rect.class)
    //   L4126 Key<SizeF> SENSOR_INFO_PHYSICAL_SIZE =
    //           new Key<>("android.sensor.info.physicalSize", SizeF.class)
    //   L905  Key<Range<Integer>[]> CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES =
    //           new Key<>("android.control.aeAvailableTargetFpsRanges", ...) (AOSP uses a
    //           TypeReference for the generic array; this tree's Key<T> only takes a raw
    //           Class<T> token, so Range[].class is used the same way int[].class already
    //           is above for CONTROL_AE_AVAILABLE_MODES)
    //   L1129 Key<Integer> CONTROL_MAX_REGIONS_AF =
    //           new Key<>("android.control.maxRegionsAf", int.class)
    //   L1703 Key<float[]> LENS_INFO_AVAILABLE_FOCAL_LENGTHS =
    //           new Key<>("android.lens.info.availableFocalLengths", float[].class)
    public static final Key<Rect> SENSOR_INFO_ACTIVE_ARRAY_SIZE =
            new Key<>("android.sensor.info.activeArraySize", Rect.class);
    public static final Key<SizeF> SENSOR_INFO_PHYSICAL_SIZE =
            new Key<>("android.sensor.info.physicalSize", SizeF.class);
    // Range<Integer>[].class is not legal Java (generic array class literals do not
    // exist, per erasure); the cast through Class<?> is the standard idiom for a
    // parameterized array's Class token and is what AOSP's own TypeReference wrapper
    // exists to paper over.
    @SuppressWarnings("unchecked")
    public static final Key<Range<Integer>[]> CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES =
            new Key<>("android.control.aeAvailableTargetFpsRanges",
                    (Class<Range<Integer>[]>) (Class<?>) Range[].class);
    public static final Key<Integer> CONTROL_MAX_REGIONS_AF =
            new Key<>("android.control.maxRegionsAf", int.class);
    public static final Key<float[]> LENS_INFO_AVAILABLE_FOCAL_LENGTHS =
            new Key<>("android.lens.info.availableFocalLengths", float[].class);
    // SCALER_STREAM_CONFIGURATION_MAP is NOT added here: its type
    // android.hardware.camera2.params.StreamConfigurationMap does not exist in this tree
    // yet (a separate gap, camera-04 in the same audit cluster); adding this key needs
    // that class to exist first.

    public static final int LENS_FACING_FRONT = 0;
    public static final int LENS_FACING_BACK = 1;
    public static final int LENS_FACING_EXTERNAL = 2;

    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED = 0;
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_FULL = 1;
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY = 2;
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_3 = 3;

    private final Map<Key<?>, Object> mProperties;
    private final List<CaptureRequest.Key<?>> mRequestKeys;
    private final List<CaptureResult.Key<?>> mResultKeys;

    CameraCharacteristics() { this(null, null, null); }
    CameraCharacteristics(Map<Key<?>, Object> properties) { this(properties, null, null); }

    /** The populated form, built by CameraBackend from a real AVFoundation
     *  device. `requestKeys` is the set of CaptureRequest keys the capture
     *  session ACTS on and `resultKeys` the set it actually FILLS IN -- not
     *  every key an app may legally set or read. See the two getters below for
     *  why that distinction is behaviour rather than bookkeeping. */
    CameraCharacteristics(Map<Key<?>, Object> properties,
            List<CaptureRequest.Key<?>> requestKeys,
            List<CaptureResult.Key<?>> resultKeys) {
        mProperties = properties != null ? properties : new HashMap<>();
        mRequestKeys = requestKeys != null
                ? Collections.unmodifiableList(new java.util.ArrayList<>(requestKeys))
                : Collections.<CaptureRequest.Key<?>>emptyList();
        mResultKeys = resultKeys != null
                ? Collections.unmodifiableList(new java.util.ArrayList<>(resultKeys))
                : Collections.<CaptureResult.Key<?>>emptyList();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) { return (T) mProperties.get(key); }

    public List<Key<?>> getKeys() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(mProperties.keySet()));
    }

    /** The request keys this camera honours. Only keys the capture session
     *  actually applies are listed -- CONTROL_AE_TARGET_FPS_RANGE, whose upper
     *  bound is passed to the native configure(). Naming a key we ignore would
     *  be worse than naming none: an app that consults this list would set the
     *  key, see it advertised, and believe it took effect. */
    public List<CaptureRequest.Key<?>> getAvailableCaptureRequestKeys() {
        return mRequestKeys;
    }

    /** The result keys this camera fills in -- SENSOR_TIMESTAMP and nothing
     *  else. The AVFoundation capture path carries no per-frame exposure time,
     *  ISO or 3A state, and a key listed here but absent from every
     *  CaptureResult would send an app looking for metadata that never
     *  arrives. */
    public List<CaptureResult.Key<?>> getAvailableCaptureResultKeys() {
        return mResultKeys;
    }

    // The three key lists below are all "which of this camera's settings are
    // X", and they are all empty for the same reason getAvailableCaptureRequestKeys
    // above is: the answer comes from the camera's own metadata, and there is no
    // camera. AOSP returns an empty list from each of them too whenever the
    // corresponding metadata tag is absent, so an app iterating the result --
    // which is what all three are for -- takes the same branch here as there.
    // Naming an unsupported key would be worse than naming none: the app would
    // put it in a request that the (eventual) HAL then rejects.
    //
    // Sources, all frameworks/base/core/java/android/hardware/camera2/
    // CameraCharacteristics.java (aosp-mirror, main):
    //   getAvailableSessionKeys                  L537  (API 28)
    //   getAvailableSessionCharacteristicsKeys   L570  (API 35)
    //   getAvailablePhysicalCameraRequestKeys    L611  (API 28)

    /** The subset of request keys that must be set at session-configuration
     *  time because changing them later stalls the camera. API 28. */
    public List<CaptureRequest.Key<?>> getAvailableSessionKeys() {
        return Collections.emptyList();
    }

    /** The characteristics keys whose value can differ per session
     *  configuration, queried through CameraDeviceSetup. API 35. */
    public List<CameraCharacteristics.Key<?>> getAvailableSessionCharacteristicsKeys() {
        return Collections.emptyList();
    }

    /** The request keys that may be set per PHYSICAL camera of a logical
     *  multi-camera. Necessarily empty here: getPhysicalCameraIds() above is
     *  already empty, so there is no physical camera to address. API 28. */
    public List<CaptureRequest.Key<?>> getAvailablePhysicalCameraRequestKeys() {
        return Collections.emptyList();
    }

    // AOSP (core/java/android/hardware/camera2/CameraCharacteristics.java, fetched
    // verbatim via raw.githubusercontent.com): `public Set<String>
    // getPhysicalCameraIds() { return mProperties.getPhysicalCameraIds(); }` -- the set
    // of physical camera ids backing a logical multi-camera device. Mac.apk exposes no
    // cameras at all (getCameraIdList() is honestly empty), so there are never any
    // physical camera ids to report; Collections.emptySet() is the correct answer for
    // that state, not a placeholder guess.
    public Set<String> getPhysicalCameraIds() {
        return Collections.emptySet();
    }

    /**
     * API 28. The subset of characteristics keys whose value is only readable
     * with the CAMERA permission.
     *
     * AOSP (CameraCharacteristics.java:374) builds this from the
     * REQUEST_CHARACTERISTIC_KEYS_NEEDING_PERMISSION tag and returns an
     * unmodifiable EMPTY list when the tag is absent, saying so in its own
     * javadoc ("The list can be empty in case there are no currently present
     * keys that need additional permission"). This backend reports no such tag,
     * so empty is the answer AOSP itself produces here, not a placeholder.
     */
    public List<Key<?>> getKeysNeedingPermission() {
        return Collections.emptyList();
    }

    /**
     * API 29. The HAL's RECOMMENDED stream configurations for one usecase.
     *
     * AOSP (CameraCharacteristics.java:472) validates the usecase against the
     * two legal ranges -- USECASE_PREVIEW..USECASE_10BIT_OUTPUT and
     * USECASE_VENDOR_START..MAX_USECASE_COUNT -- throwing
     * IllegalArgumentException("Invalid use case: %d") outside them, and
     * otherwise returns null when the camera published no recommendations. Its
     * javadoc is explicit that null is a normal answer.
     *
     * The AVFoundation backend publishes none: a recommendation is the HAL's
     * opinion about which sizes are cheap on that silicon, and inventing one
     * would send an app to a resolution nothing measured. So the range check is
     * reproduced exactly and the answer is null, which is the branch every app
     * already has for a device that omits the tag.
     */
    public android.hardware.camera2.params.RecommendedStreamConfigurationMap
            getRecommendedStreamConfigurationMap(int usecase) {
        final int previewUseCase =
                android.hardware.camera2.params.RecommendedStreamConfigurationMap.USECASE_PREVIEW;
        final int tenBitUseCase = android.hardware.camera2.params
                .RecommendedStreamConfigurationMap.USECASE_10BIT_OUTPUT;
        final int vendorStart = android.hardware.camera2.params
                .RecommendedStreamConfigurationMap.USECASE_VENDOR_START;
        final int maxUseCase = android.hardware.camera2.params
                .RecommendedStreamConfigurationMap.MAX_USECASE_COUNT;
        if (((usecase >= previewUseCase) && (usecase <= tenBitUseCase))
                || ((usecase >= vendorStart) && (usecase < maxUseCase))) {
            return null;
        }
        throw new IllegalArgumentException(String.format("Invalid use case: %d", usecase));
    }

    public static final Key<android.hardware.camera2.params.StreamConfigurationMap> SCALER_STREAM_CONFIGURATION_MAP =
        new Key<>("android.scaler.streamConfigurationMap", android.hardware.camera2.params.StreamConfigurationMap.class);

    // ---------------------------------------------------------------------
    // The remaining public CameraCharacteristics keys of API 36, mirrored from
    // frameworks/base/core/java/android/hardware/camera2/CameraCharacteristics.java
    // (aosp-mirror/platform_frameworks_base, android16-release). Each carries
    // AOSP's own metadata tag string, which is the key's identity: equals() and
    // hashCode() are defined on it, so a key built here and a key built by AOSP
    // for the same tag are interchangeable.
    //
    // A Key CONSTANT is an identifier, not a claim about hardware. AOSP's
    // contract for get(Key) is that it returns null for any tag this camera
    // does not report (CameraCharacteristics.java get() -> mProperties.get(key)
    // -> null), and every app is written against that. So declaring a key we
    // cannot answer is exactly right, and populating one with an invented value
    // would be exactly wrong.
    // ---------------------------------------------------------------------

    /** AOSP CameraCharacteristics.java:6021 -- metadata tag "android.automotive.lens.facing". */
    public static final Key<int[]> AUTOMOTIVE_LENS_FACING =
            new Key<>("android.automotive.lens.facing", int[].class);

    /** AOSP CameraCharacteristics.java:6083 -- metadata tag "android.automotive.location". */
    public static final Key<Integer> AUTOMOTIVE_LOCATION =
            new Key<>("android.automotive.location", int.class);

    /** AOSP CameraCharacteristics.java:774 -- metadata tag "android.colorCorrection.availableAberrationModes". */
    public static final Key<int[]> COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES =
            new Key<>("android.colorCorrection.availableAberrationModes", int[].class);

    /** AOSP CameraCharacteristics.java:814 -- metadata tag "android.colorCorrection.availableModes". */
    public static final Key<int[]> COLOR_CORRECTION_AVAILABLE_MODES =
            new Key<>("android.colorCorrection.availableModes", int[].class);

    /** AOSP CameraCharacteristics.java:795 -- metadata tag "android.colorCorrection.colorTemperatureRange". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Range<Integer>> COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE =
            new Key<>("android.colorCorrection.colorTemperatureRange", (Class<android.util.Range<Integer>>) (Class<?>) android.util.Range.class);

    /** AOSP CameraCharacteristics.java:833 -- metadata tag "android.control.aeAvailableAntibandingModes". */
    public static final Key<int[]> CONTROL_AE_AVAILABLE_ANTIBANDING_MODES =
            new Key<>("android.control.aeAvailableAntibandingModes", int[].class);

    /** AOSP CameraCharacteristics.java:1453 -- metadata tag "android.control.aeAvailablePriorityModes". */
    public static final Key<int[]> CONTROL_AE_AVAILABLE_PRIORITY_MODES =
            new Key<>("android.control.aeAvailablePriorityModes", int[].class);

    /** AOSP CameraCharacteristics.java:926 -- metadata tag "android.control.aeCompensationRange". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Range<Integer>> CONTROL_AE_COMPENSATION_RANGE =
            new Key<>("android.control.aeCompensationRange", (Class<android.util.Range<Integer>>) (Class<?>) android.util.Range.class);

    /** AOSP CameraCharacteristics.java:944 -- metadata tag "android.control.aeCompensationStep". */
    public static final Key<android.util.Rational> CONTROL_AE_COMPENSATION_STEP =
            new Key<>("android.control.aeCompensationStep", android.util.Rational.class);

    /** AOSP CameraCharacteristics.java:1198 -- metadata tag "android.control.aeLockAvailable". */
    public static final Key<Boolean> CONTROL_AE_LOCK_AVAILABLE =
            new Key<>("android.control.aeLockAvailable", boolean.class);

    /** AOSP CameraCharacteristics.java:1414 -- metadata tag "android.control.autoframingAvailable". */
    public static final Key<Boolean> CONTROL_AUTOFRAMING_AVAILABLE =
            new Key<>("android.control.autoframingAvailable", boolean.class);

    /** AOSP CameraCharacteristics.java:993 -- metadata tag "android.control.availableEffects". */
    public static final Key<int[]> CONTROL_AVAILABLE_EFFECTS =
            new Key<>("android.control.availableEffects", int[].class);

    /** AOSP CameraCharacteristics.java:1334 -- metadata tag "android.control.availableExtendedSceneModeCapabilities". */
    public static final Key<android.hardware.camera2.params.Capability[]> CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES =
            new Key<>("android.control.availableExtendedSceneModeCapabilities", android.hardware.camera2.params.Capability[].class);

    /** AOSP CameraCharacteristics.java:1228 -- metadata tag "android.control.availableModes". */
    public static final Key<int[]> CONTROL_AVAILABLE_MODES =
            new Key<>("android.control.availableModes", int[].class);

    /** AOSP CameraCharacteristics.java:1017 -- metadata tag "android.control.availableSceneModes". */
    public static final Key<int[]> CONTROL_AVAILABLE_SCENE_MODES =
            new Key<>("android.control.availableSceneModes", int[].class);

    /** AOSP CameraCharacteristics.java:1398 -- metadata tag "android.control.availableSettingsOverrides". */
    public static final Key<int[]> CONTROL_AVAILABLE_SETTINGS_OVERRIDES =
            new Key<>("android.control.availableSettingsOverrides", int[].class);

    /** AOSP CameraCharacteristics.java:1032 -- metadata tag "android.control.availableVideoStabilizationModes". */
    public static final Key<int[]> CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES =
            new Key<>("android.control.availableVideoStabilizationModes", int[].class);

    /** AOSP CameraCharacteristics.java:1057 -- metadata tag "android.control.awbAvailableModes". */
    public static final Key<int[]> CONTROL_AWB_AVAILABLE_MODES =
            new Key<>("android.control.awbAvailableModes", int[].class);

    /** AOSP CameraCharacteristics.java:1211 -- metadata tag "android.control.awbLockAvailable". */
    public static final Key<Boolean> CONTROL_AWB_LOCK_AVAILABLE =
            new Key<>("android.control.awbLockAvailable", boolean.class);

    /** AOSP CameraCharacteristics.java:1435 -- metadata tag "android.control.lowLightBoostInfoLuminanceRange". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Range<Float>> CONTROL_LOW_LIGHT_BOOST_INFO_LUMINANCE_RANGE =
            new Key<>("android.control.lowLightBoostInfoLuminanceRange", (Class<android.util.Range<Float>>) (Class<?>) android.util.Range.class);

    /** AOSP CameraCharacteristics.java:1095 -- metadata tag "android.control.maxRegionsAe". */
    public static final Key<Integer> CONTROL_MAX_REGIONS_AE =
            new Key<>("android.control.maxRegionsAe", int.class);

    /** AOSP CameraCharacteristics.java:1112 -- metadata tag "android.control.maxRegionsAwb". */
    public static final Key<Integer> CONTROL_MAX_REGIONS_AWB =
            new Key<>("android.control.maxRegionsAwb", int.class);

    /** AOSP CameraCharacteristics.java:1249 -- metadata tag "android.control.postRawSensitivityBoostRange". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Range<Integer>> CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE =
            new Key<>("android.control.postRawSensitivityBoostRange", (Class<android.util.Range<Integer>>) (Class<?>) android.util.Range.class);

    /** AOSP CameraCharacteristics.java:1355 -- metadata tag "android.control.zoomRatioRange". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Range<Float>> CONTROL_ZOOM_RATIO_RANGE =
            new Key<>("android.control.zoomRatioRange", (Class<android.util.Range<Float>>) (Class<?>) android.util.Range.class);

    /** AOSP CameraCharacteristics.java:5487 -- metadata tag "android.depth.depthIsExclusive". */
    public static final Key<Boolean> DEPTH_DEPTH_IS_EXCLUSIVE =
            new Key<>("android.depth.depthIsExclusive", boolean.class);

    /** AOSP CameraCharacteristics.java:5729 -- metadata tag "android.distortionCorrection.availableModes". */
    public static final Key<int[]> DISTORTION_CORRECTION_AVAILABLE_MODES =
            new Key<>("android.distortionCorrection.availableModes", int[].class);

    /** AOSP CameraCharacteristics.java:1474 -- metadata tag "android.edge.availableEdgeModes". */
    public static final Key<int[]> EDGE_AVAILABLE_EDGE_MODES =
            new Key<>("android.edge.availableEdgeModes", int[].class);

    /** AOSP CameraCharacteristics.java:1528 -- metadata tag "android.flash.info.strengthDefaultLevel". */
    public static final Key<Integer> FLASH_INFO_STRENGTH_DEFAULT_LEVEL =
            new Key<>("android.flash.info.strengthDefaultLevel", int.class);

    /** AOSP CameraCharacteristics.java:1505 -- metadata tag "android.flash.info.strengthMaximumLevel". */
    public static final Key<Integer> FLASH_INFO_STRENGTH_MAXIMUM_LEVEL =
            new Key<>("android.flash.info.strengthMaximumLevel", int.class);

    /** AOSP CameraCharacteristics.java:1560 -- metadata tag "android.flash.singleStrengthDefaultLevel". */
    public static final Key<Integer> FLASH_SINGLE_STRENGTH_DEFAULT_LEVEL =
            new Key<>("android.flash.singleStrengthDefaultLevel", int.class);

    /** AOSP CameraCharacteristics.java:1545 -- metadata tag "android.flash.singleStrengthMaxLevel". */
    public static final Key<Integer> FLASH_SINGLE_STRENGTH_MAX_LEVEL =
            new Key<>("android.flash.singleStrengthMaxLevel", int.class);

    /** AOSP CameraCharacteristics.java:1598 -- metadata tag "android.flash.torchStrengthDefaultLevel". */
    public static final Key<Integer> FLASH_TORCH_STRENGTH_DEFAULT_LEVEL =
            new Key<>("android.flash.torchStrengthDefaultLevel", int.class);

    /** AOSP CameraCharacteristics.java:1583 -- metadata tag "android.flash.torchStrengthMaxLevel". */
    public static final Key<Integer> FLASH_TORCH_STRENGTH_MAX_LEVEL =
            new Key<>("android.flash.torchStrengthMaxLevel", int.class);

    /** AOSP CameraCharacteristics.java:1613 -- metadata tag "android.hotPixel.availableHotPixelModes". */
    public static final Key<int[]> HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES =
            new Key<>("android.hotPixel.availableHotPixelModes", int[].class);

    /** AOSP CameraCharacteristics.java:5108 -- metadata tag "android.info.deviceStateSensorOrientationMap". */
    public static final Key<android.hardware.camera2.params.DeviceStateSensorOrientationMap> INFO_DEVICE_STATE_SENSOR_ORIENTATION_MAP =
            new Key<>("android.info.deviceStateSensorOrientationMap", android.hardware.camera2.params.DeviceStateSensorOrientationMap.class);

    /** AOSP CameraCharacteristics.java:5312 -- metadata tag "android.info.sessionConfigurationQueryVersion". */
    public static final Key<Integer> INFO_SESSION_CONFIGURATION_QUERY_VERSION =
            new Key<>("android.info.sessionConfigurationQueryVersion", int.class);

    /** AOSP CameraCharacteristics.java:5084 -- metadata tag "android.info.version". */
    public static final Key<String> INFO_VERSION =
            new Key<>("android.info.version", String.class);

    /** AOSP CameraCharacteristics.java:1641 -- metadata tag "android.jpeg.availableThumbnailSizes". */
    public static final Key<android.util.Size[]> JPEG_AVAILABLE_THUMBNAIL_SIZES =
            new Key<>("android.jpeg.availableThumbnailSizes", android.util.Size[].class);

    /** AOSP CameraCharacteristics.java:2129 -- metadata tag "android.lens.distortion". */
    public static final Key<float[]> LENS_DISTORTION =
            new Key<>("android.lens.distortion", float[].class);

    /** AOSP CameraCharacteristics.java:2149 -- metadata tag "android.lens.distortionMaximumResolution". */
    public static final Key<float[]> LENS_DISTORTION_MAXIMUM_RESOLUTION =
            new Key<>("android.lens.distortionMaximumResolution", float[].class);

    /** AOSP CameraCharacteristics.java:1662 -- metadata tag "android.lens.info.availableApertures". */
    public static final Key<float[]> LENS_INFO_AVAILABLE_APERTURES =
            new Key<>("android.lens.info.availableApertures", float[].class);

    /** AOSP CameraCharacteristics.java:1684 -- metadata tag "android.lens.info.availableFilterDensities". */
    public static final Key<float[]> LENS_INFO_AVAILABLE_FILTER_DENSITIES =
            new Key<>("android.lens.info.availableFilterDensities", float[].class);

    /** AOSP CameraCharacteristics.java:1723 -- metadata tag "android.lens.info.availableOpticalStabilization". */
    public static final Key<int[]> LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION =
            new Key<>("android.lens.info.availableOpticalStabilization", int[].class);

    /** AOSP CameraCharacteristics.java:1826 -- metadata tag "android.lens.info.focusDistanceCalibration". */
    public static final Key<Integer> LENS_INFO_FOCUS_DISTANCE_CALIBRATION =
            new Key<>("android.lens.info.focusDistanceCalibration", int.class);

    /** AOSP CameraCharacteristics.java:1746 -- metadata tag "android.lens.info.hyperfocalDistance". */
    public static final Key<Float> LENS_INFO_HYPERFOCAL_DISTANCE =
            new Key<>("android.lens.info.hyperfocalDistance", float.class);

    /** AOSP CameraCharacteristics.java:1768 -- metadata tag "android.lens.info.minimumFocusDistance". */
    public static final Key<Float> LENS_INFO_MINIMUM_FOCUS_DISTANCE =
            new Key<>("android.lens.info.minimumFocusDistance", float.class);

    /** AOSP CameraCharacteristics.java:2011 -- metadata tag "android.lens.intrinsicCalibration". */
    public static final Key<float[]> LENS_INTRINSIC_CALIBRATION =
            new Key<>("android.lens.intrinsicCalibration", float[].class);

    /** AOSP CameraCharacteristics.java:2171 -- metadata tag "android.lens.intrinsicCalibrationMaximumResolution". */
    public static final Key<float[]> LENS_INTRINSIC_CALIBRATION_MAXIMUM_RESOLUTION =
            new Key<>("android.lens.intrinsicCalibrationMaximumResolution", float[].class);

    /** AOSP CameraCharacteristics.java:2087 -- metadata tag "android.lens.poseReference". */
    public static final Key<Integer> LENS_POSE_REFERENCE =
            new Key<>("android.lens.poseReference", int.class);

    /** AOSP CameraCharacteristics.java:1890 -- metadata tag "android.lens.poseRotation". */
    public static final Key<float[]> LENS_POSE_ROTATION =
            new Key<>("android.lens.poseRotation", float[].class);

    /** AOSP CameraCharacteristics.java:1937 -- metadata tag "android.lens.poseTranslation". */
    public static final Key<float[]> LENS_POSE_TRANSLATION =
            new Key<>("android.lens.poseTranslation", float[].class);

    /** AOSP CameraCharacteristics.java:2059 -- metadata tag "android.lens.radialDistortion". */
    public static final Key<float[]> LENS_RADIAL_DISTORTION =
            new Key<>("android.lens.radialDistortion", float[].class);

    /** AOSP CameraCharacteristics.java:5713 -- metadata tag "android.logicalMultiCamera.sensorSyncType". */
    public static final Key<Integer> LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE =
            new Key<>("android.logicalMultiCamera.sensorSyncType", int.class);

    /** AOSP CameraCharacteristics.java:2193 -- metadata tag "android.noiseReduction.availableNoiseReductionModes". */
    public static final Key<int[]> NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES =
            new Key<>("android.noiseReduction.availableNoiseReductionModes", int[].class);

    /** AOSP CameraCharacteristics.java:5388 -- metadata tag "android.reprocess.maxCaptureStall". */
    public static final Key<Integer> REPROCESS_MAX_CAPTURE_STALL =
            new Key<>("android.reprocess.maxCaptureStall", int.class);

    /** AOSP CameraCharacteristics.java:2735 -- metadata tag "android.request.availableColorSpaceProfiles". */
    public static final Key<android.hardware.camera2.params.ColorSpaceProfiles> REQUEST_AVAILABLE_COLOR_SPACE_PROFILES =
            new Key<>("android.request.availableColorSpaceProfiles", android.hardware.camera2.params.ColorSpaceProfiles.class);

    /** AOSP CameraCharacteristics.java:2652 -- metadata tag "android.request.availableDynamicRangeProfiles". */
    public static final Key<android.hardware.camera2.params.DynamicRangeProfiles> REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES =
            new Key<>("android.request.availableDynamicRangeProfiles", android.hardware.camera2.params.DynamicRangeProfiles.class);

    /** AOSP CameraCharacteristics.java:2371 -- metadata tag "android.request.maxNumInputStreams". */
    public static final Key<Integer> REQUEST_MAX_NUM_INPUT_STREAMS =
            new Key<>("android.request.maxNumInputStreams", int.class);

    /** AOSP CameraCharacteristics.java:2317 -- metadata tag "android.request.maxNumOutputProc". */
    public static final Key<Integer> REQUEST_MAX_NUM_OUTPUT_PROC =
            new Key<>("android.request.maxNumOutputProc", int.class);

    /** AOSP CameraCharacteristics.java:2344 -- metadata tag "android.request.maxNumOutputProcStalling". */
    public static final Key<Integer> REQUEST_MAX_NUM_OUTPUT_PROC_STALLING =
            new Key<>("android.request.maxNumOutputProcStalling", int.class);

    /** AOSP CameraCharacteristics.java:2280 -- metadata tag "android.request.maxNumOutputRaw". */
    public static final Key<Integer> REQUEST_MAX_NUM_OUTPUT_RAW =
            new Key<>("android.request.maxNumOutputRaw", int.class);

    /** AOSP CameraCharacteristics.java:2420 -- metadata tag "android.request.partialResultCount". */
    public static final Key<Integer> REQUEST_PARTIAL_RESULT_COUNT =
            new Key<>("android.request.partialResultCount", int.class);

    /** AOSP CameraCharacteristics.java:2396 -- metadata tag "android.request.pipelineMaxDepth". */
    public static final Key<Byte> REQUEST_PIPELINE_MAX_DEPTH =
            new Key<>("android.request.pipelineMaxDepth", byte.class);

    /** AOSP CameraCharacteristics.java:2712 -- metadata tag "android.request.recommendedTenBitDynamicRangeProfile". */
    public static final Key<Long> REQUEST_RECOMMENDED_TEN_BIT_DYNAMIC_RANGE_PROFILE =
            new Key<>("android.request.recommendedTenBitDynamicRangeProfile", long.class);

    /** AOSP CameraCharacteristics.java:2844 -- metadata tag "android.scaler.availableMaxDigitalZoom". */
    public static final Key<Float> SCALER_AVAILABLE_MAX_DIGITAL_ZOOM =
            new Key<>("android.scaler.availableMaxDigitalZoom", float.class);

    /** AOSP CameraCharacteristics.java:3587 -- metadata tag "android.scaler.availableRotateAndCropModes". */
    public static final Key<int[]> SCALER_AVAILABLE_ROTATE_AND_CROP_MODES =
            new Key<>("android.scaler.availableRotateAndCropModes", int[].class);

    /** AOSP CameraCharacteristics.java:3948 -- metadata tag "android.scaler.availableStreamUseCases". */
    public static final Key<long[]> SCALER_AVAILABLE_STREAM_USE_CASES =
            new Key<>("android.scaler.availableStreamUseCases", long[].class);

    /** AOSP CameraCharacteristics.java:3480 -- metadata tag "android.scaler.croppingType". */
    public static final Key<Integer> SCALER_CROPPING_TYPE =
            new Key<>("android.scaler.croppingType", int.class);

    /** AOSP CameraCharacteristics.java:3610 -- metadata tag "android.scaler.defaultSecureImageSize". */
    public static final Key<android.util.Size> SCALER_DEFAULT_SECURE_IMAGE_SIZE =
            new Key<>("android.scaler.defaultSecureImageSize", android.util.Size.class);

    /** AOSP CameraCharacteristics.java:3570 -- metadata tag "android.scaler.mandatoryConcurrentStreamCombinations". */
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_CONCURRENT_STREAM_COMBINATIONS =
            new Key<>("android.scaler.mandatoryConcurrentStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);

    /** AOSP CameraCharacteristics.java:3805 -- metadata tag "android.scaler.mandatoryMaximumResolutionStreamCombinations". */
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_MAXIMUM_RESOLUTION_STREAM_COMBINATIONS =
            new Key<>("android.scaler.mandatoryMaximumResolutionStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);

    /** AOSP CameraCharacteristics.java:3856 -- metadata tag "android.scaler.mandatoryPreviewStabilizationOutputStreamCombinations". */
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_PREVIEW_STABILIZATION_OUTPUT_STREAM_COMBINATIONS =
            new Key<>("android.scaler.mandatoryPreviewStabilizationOutputStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);

    /** AOSP CameraCharacteristics.java:3547 -- metadata tag "android.scaler.mandatoryStreamCombinations". */
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_STREAM_COMBINATIONS =
            new Key<>("android.scaler.mandatoryStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);

    /** AOSP CameraCharacteristics.java:3831 -- metadata tag "android.scaler.mandatoryTenBitOutputStreamCombinations". */
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_TEN_BIT_OUTPUT_STREAM_COMBINATIONS =
            new Key<>("android.scaler.mandatoryTenBitOutputStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);

    /** AOSP CameraCharacteristics.java:3971 -- metadata tag "android.scaler.mandatoryUseCaseStreamCombinations". */
    public static final Key<android.hardware.camera2.params.MandatoryStreamCombination[]> SCALER_MANDATORY_USE_CASE_STREAM_COMBINATIONS =
            new Key<>("android.scaler.mandatoryUseCaseStreamCombinations", android.hardware.camera2.params.MandatoryStreamCombination[].class);

    /** AOSP CameraCharacteristics.java:3679 -- metadata tag "android.scaler.multiResolutionStreamConfigurationMap". */
    public static final Key<android.hardware.camera2.params.MultiResolutionStreamConfigurationMap> SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP =
            new Key<>("android.scaler.multiResolutionStreamConfigurationMap", android.hardware.camera2.params.MultiResolutionStreamConfigurationMap.class);

    /** AOSP CameraCharacteristics.java:3761 -- metadata tag "android.scaler.streamConfigurationMapMaximumResolution". */
    public static final Key<android.hardware.camera2.params.StreamConfigurationMap> SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION =
            new Key<>("android.scaler.streamConfigurationMapMaximumResolution", android.hardware.camera2.params.StreamConfigurationMap.class);

    /** AOSP CameraCharacteristics.java:4760 -- metadata tag "android.sensor.availableTestPatternModes". */
    public static final Key<int[]> SENSOR_AVAILABLE_TEST_PATTERN_MODES =
            new Key<>("android.sensor.availableTestPatternModes", int[].class);

    /** AOSP CameraCharacteristics.java:4694 -- metadata tag "android.sensor.blackLevelPattern". */
    public static final Key<android.hardware.camera2.params.BlackLevelPattern> SENSOR_BLACK_LEVEL_PATTERN =
            new Key<>("android.sensor.blackLevelPattern", android.hardware.camera2.params.BlackLevelPattern.class);

    /** AOSP CameraCharacteristics.java:4525 -- metadata tag "android.sensor.calibrationTransform1". */
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_CALIBRATION_TRANSFORM1 =
            new Key<>("android.sensor.calibrationTransform1", android.hardware.camera2.params.ColorSpaceTransform.class);

    /** AOSP CameraCharacteristics.java:4551 -- metadata tag "android.sensor.calibrationTransform2". */
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_CALIBRATION_TRANSFORM2 =
            new Key<>("android.sensor.calibrationTransform2", android.hardware.camera2.params.ColorSpaceTransform.class);

    /** AOSP CameraCharacteristics.java:4578 -- metadata tag "android.sensor.colorTransform1". */
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_COLOR_TRANSFORM1 =
            new Key<>("android.sensor.colorTransform1", android.hardware.camera2.params.ColorSpaceTransform.class);

    /** AOSP CameraCharacteristics.java:4607 -- metadata tag "android.sensor.colorTransform2". */
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_COLOR_TRANSFORM2 =
            new Key<>("android.sensor.colorTransform2", android.hardware.camera2.params.ColorSpaceTransform.class);

    /** AOSP CameraCharacteristics.java:4632 -- metadata tag "android.sensor.forwardMatrix1". */
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_FORWARD_MATRIX1 =
            new Key<>("android.sensor.forwardMatrix1", android.hardware.camera2.params.ColorSpaceTransform.class);

    /** AOSP CameraCharacteristics.java:4659 -- metadata tag "android.sensor.forwardMatrix2". */
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> SENSOR_FORWARD_MATRIX2 =
            new Key<>("android.sensor.forwardMatrix2", android.hardware.camera2.params.ColorSpaceTransform.class);

    /** AOSP CameraCharacteristics.java:4317 -- metadata tag "android.sensor.info.activeArraySizeMaximumResolution". */
    public static final Key<android.graphics.Rect> SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION =
            new Key<>("android.sensor.info.activeArraySizeMaximumResolution", android.graphics.Rect.class);

    /** AOSP CameraCharacteristics.java:4405 -- metadata tag "android.sensor.info.binningFactor". */
    public static final Key<android.util.Size> SENSOR_INFO_BINNING_FACTOR =
            new Key<>("android.sensor.info.binningFactor", android.util.Size.class);

    /** AOSP CameraCharacteristics.java:4065 -- metadata tag "android.sensor.info.colorFilterArrangement". */
    public static final Key<Integer> SENSOR_INFO_COLOR_FILTER_ARRANGEMENT =
            new Key<>("android.sensor.info.colorFilterArrangement", int.class);

    /** AOSP CameraCharacteristics.java:4086 -- metadata tag "android.sensor.info.exposureTimeRange". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Range<Long>> SENSOR_INFO_EXPOSURE_TIME_RANGE =
            new Key<>("android.sensor.info.exposureTimeRange", (Class<android.util.Range<Long>>) (Class<?>) android.util.Range.class);

    /** AOSP CameraCharacteristics.java:4226 -- metadata tag "android.sensor.info.lensShadingApplied". */
    public static final Key<Boolean> SENSOR_INFO_LENS_SHADING_APPLIED =
            new Key<>("android.sensor.info.lensShadingApplied", boolean.class);

    /** AOSP CameraCharacteristics.java:4111 -- metadata tag "android.sensor.info.maxFrameDuration". */
    public static final Key<Long> SENSOR_INFO_MAX_FRAME_DURATION =
            new Key<>("android.sensor.info.maxFrameDuration", long.class);

    /** AOSP CameraCharacteristics.java:4152 -- metadata tag "android.sensor.info.pixelArraySize". */
    public static final Key<android.util.Size> SENSOR_INFO_PIXEL_ARRAY_SIZE =
            new Key<>("android.sensor.info.pixelArraySize", android.util.Size.class);

    /** AOSP CameraCharacteristics.java:4344 -- metadata tag "android.sensor.info.pixelArraySizeMaximumResolution". */
    public static final Key<android.util.Size> SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION =
            new Key<>("android.sensor.info.pixelArraySizeMaximumResolution", android.util.Size.class);

    /** AOSP CameraCharacteristics.java:4292 -- metadata tag "android.sensor.info.preCorrectionActiveArraySize". */
    public static final Key<android.graphics.Rect> SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE =
            new Key<>("android.sensor.info.preCorrectionActiveArraySize", android.graphics.Rect.class);

    /** AOSP CameraCharacteristics.java:4368 -- metadata tag "android.sensor.info.preCorrectionActiveArraySizeMaximumResolution". */
    public static final Key<android.graphics.Rect> SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION =
            new Key<>("android.sensor.info.preCorrectionActiveArraySizeMaximumResolution", android.graphics.Rect.class);

    /** AOSP CameraCharacteristics.java:4030 -- metadata tag "android.sensor.info.sensitivityRange". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Range<Integer>> SENSOR_INFO_SENSITIVITY_RANGE =
            new Key<>("android.sensor.info.sensitivityRange", (Class<android.util.Range<Integer>>) (Class<?>) android.util.Range.class);

    /** AOSP CameraCharacteristics.java:4202 -- metadata tag "android.sensor.info.timestampSource". */
    public static final Key<Integer> SENSOR_INFO_TIMESTAMP_SOURCE =
            new Key<>("android.sensor.info.timestampSource", int.class);

    /** AOSP CameraCharacteristics.java:4181 -- metadata tag "android.sensor.info.whiteLevel". */
    public static final Key<Integer> SENSOR_INFO_WHITE_LEVEL =
            new Key<>("android.sensor.info.whiteLevel", int.class);

    /** AOSP CameraCharacteristics.java:4714 -- metadata tag "android.sensor.maxAnalogSensitivity". */
    public static final Key<Integer> SENSOR_MAX_ANALOG_SENSITIVITY =
            new Key<>("android.sensor.maxAnalogSensitivity", int.class);

    /** AOSP CameraCharacteristics.java:4787 -- metadata tag "android.sensor.opticalBlackRegions". */
    public static final Key<android.graphics.Rect[]> SENSOR_OPTICAL_BLACK_REGIONS =
            new Key<>("android.sensor.opticalBlackRegions", android.graphics.Rect[].class);

    /** AOSP CameraCharacteristics.java:4824 -- metadata tag "android.sensor.readoutTimestamp". */
    public static final Key<Integer> SENSOR_READOUT_TIMESTAMP =
            new Key<>("android.sensor.readoutTimestamp", int.class);

    /** AOSP CameraCharacteristics.java:4476 -- metadata tag "android.sensor.referenceIlluminant1". */
    public static final Key<Integer> SENSOR_REFERENCE_ILLUMINANT1 =
            new Key<>("android.sensor.referenceIlluminant1", int.class);

    /** AOSP CameraCharacteristics.java:4502 -- metadata tag "android.sensor.referenceIlluminant2". */
    public static final Key<Byte> SENSOR_REFERENCE_ILLUMINANT2 =
            new Key<>("android.sensor.referenceIlluminant2", byte.class);

    /** AOSP CameraCharacteristics.java:4841 -- metadata tag "android.shading.availableModes". */
    public static final Key<int[]> SHADING_AVAILABLE_MODES =
            new Key<>("android.shading.availableModes", int[].class);

    /** AOSP CameraCharacteristics.java:4856 -- metadata tag "android.statistics.info.availableFaceDetectModes". */
    public static final Key<int[]> STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES =
            new Key<>("android.statistics.info.availableFaceDetectModes", int[].class);

    /** AOSP CameraCharacteristics.java:4887 -- metadata tag "android.statistics.info.availableHotPixelMapModes". */
    public static final Key<boolean[]> STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES =
            new Key<>("android.statistics.info.availableHotPixelMapModes", boolean[].class);

    /** AOSP CameraCharacteristics.java:4905 -- metadata tag "android.statistics.info.availableLensShadingMapModes". */
    public static final Key<int[]> STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES =
            new Key<>("android.statistics.info.availableLensShadingMapModes", int[].class);

    /** AOSP CameraCharacteristics.java:4921 -- metadata tag "android.statistics.info.availableOisDataModes". */
    public static final Key<int[]> STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES =
            new Key<>("android.statistics.info.availableOisDataModes", int[].class);

    /** AOSP CameraCharacteristics.java:4870 -- metadata tag "android.statistics.info.maxFaceCount". */
    public static final Key<Integer> STATISTICS_INFO_MAX_FACE_COUNT =
            new Key<>("android.statistics.info.maxFaceCount", int.class);

    /** AOSP CameraCharacteristics.java:5352 -- metadata tag "android.sync.maxLatency". */
    public static final Key<Integer> SYNC_MAX_LATENCY =
            new Key<>("android.sync.maxLatency", int.class);

    /** AOSP CameraCharacteristics.java:4968 -- metadata tag "android.tonemap.availableToneMapModes". */
    public static final Key<int[]> TONEMAP_AVAILABLE_TONE_MAP_MODES =
            new Key<>("android.tonemap.availableToneMapModes", int[].class);

    /** AOSP CameraCharacteristics.java:4943 -- metadata tag "android.tonemap.maxCurvePoints". */
    public static final Key<Integer> TONEMAP_MAX_CURVE_POINTS =
            new Key<>("android.tonemap.maxCurvePoints", int.class);
}

