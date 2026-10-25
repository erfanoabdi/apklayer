// android.hardware.camera2.CaptureResult -- metadata for a completed
// capture. Mac.apk never produces one (no camera), but the type + Key
// set must exist for capture-callback code to compile. Key<T> mirrors
// AOSP's typed metadata key.
package android.hardware.camera2;

import java.util.HashMap;
import java.util.Map;

// android-30 android.jar ground truth: `public class CaptureResult extends
// CameraMetadata<CaptureResult$Key<?>>`. App bytecode compiled against the
// SDK can pass a CaptureResult where a CameraMetadata is expected, so the
// superclass is part of the linkable ABI, not a styling choice.
public class CaptureResult extends CameraMetadata<CaptureResult.Key<?>> {
    public static final class Key<T> {
        private final String name;
        private final Class<T> type;
        public Key(String name, Class<T> type) { this.name = name; this.type = type; }
        public String getName() { return name; }
        @Override public int hashCode() { return name.hashCode(); }
        @Override public boolean equals(Object o) {
            return (o instanceof Key) && ((Key<?>) o).name.equals(name);
        }
        @Override public String toString() { return "CaptureResult.Key(" + name + ")"; }
    }

    public static final Key<Integer> CONTROL_AF_STATE =
            new Key<>("android.control.afState", Integer.class);
    public static final Key<Integer> CONTROL_AE_STATE =
            new Key<>("android.control.aeState", Integer.class);
    public static final Key<Integer> CONTROL_AWB_STATE =
            new Key<>("android.control.awbState", Integer.class);
    public static final Key<Long> SENSOR_TIMESTAMP =
            new Key<>("android.sensor.timestamp", Long.class);
    public static final Key<Long> SENSOR_EXPOSURE_TIME =
            new Key<>("android.sensor.exposureTime", Long.class);
    public static final Key<Integer> SENSOR_SENSITIVITY =
            new Key<>("android.sensor.sensitivity", Integer.class);

    protected final Map<Key<?>, Object> mResults;
    private final long mFrameNumber;
    private final CaptureRequest mRequest;
    private final String mCameraId;
    private final int mSequenceId;

    protected CaptureResult() {
        mResults = new HashMap<>();
        mFrameNumber = 0;
        mRequest = null;
        // getCameraId() is @NonNull in AOSP, so even the default form has to
        // answer something rather than null. "" is the honest stand-in for
        // "this result did not come from a camera": there is no id to name.
        mCameraId = "";
        mSequenceId = -1;
    }

    protected CaptureResult(Map<Key<?>, Object> results, CaptureRequest request, long frameNumber) {
        this(results, request, frameNumber, "", -1);
    }

    /** The fully populated form. cameraId and sequenceId are carried because
     *  AOSP exposes both (getCameraId, getSequenceId) and an app that routes
     *  results from several cameras through one callback uses the id to tell
     *  them apart -- a wrong answer there silently mixes streams. */
    CaptureResult(Map<Key<?>, Object> results, CaptureRequest request, long frameNumber,
            String cameraId, int sequenceId) {
        mResults = results != null ? results : new HashMap<>();
        mRequest = request;
        mFrameNumber = frameNumber;
        mCameraId = cameraId != null ? cameraId : "";
        mSequenceId = sequenceId;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) { return (T) mResults.get(key); }
    public long getFrameNumber() { return mFrameNumber; }
    public CaptureRequest getRequest() { return mRequest; }

    /** API 34. The id of the camera that produced this result, matching
     *  CameraDevice.getId(). AOSP declares it @NonNull. */
    public String getCameraId() { return mCameraId; }

    /** The id of the capture sequence this result belongs to -- the int the
     *  capture()/setRepeatingRequest() call returned, and the same value handed
     *  to onCaptureSequenceCompleted. */
    public int getSequenceId() { return mSequenceId; }

    // ---------------------------------------------------------------------
    // The remaining public CaptureResult keys of API 36, mirrored from
    // frameworks/base/core/java/android/hardware/camera2/CaptureResult.java
    // (aosp-mirror/platform_frameworks_base, android16-release), each with
    // AOSP's own metadata tag string.
    //
    // get(Key) returns null for a tag this capture did not carry, which is
    // AOSP's documented behaviour for any result key outside the camera's
    // availableResultKeys -- so an app reading one of these off a Mac.apk
    // CaptureResult takes the same branch it takes on a device whose HAL omits
    // the tag.
    // ---------------------------------------------------------------------

    /** AOSP CaptureResult.java:5790 -- metadata tag "android.blackLevel.lock". */
    public static final Key<Boolean> BLACK_LEVEL_LOCK =
            new Key<>("android.blackLevel.lock", boolean.class);

    /** AOSP CaptureResult.java:600 -- metadata tag "android.colorCorrection.aberrationMode". */
    public static final Key<Integer> COLOR_CORRECTION_ABERRATION_MODE =
            new Key<>("android.colorCorrection.aberrationMode", int.class);

    /** AOSP CaptureResult.java:625 -- metadata tag "android.colorCorrection.colorTemperature". */
    public static final Key<Integer> COLOR_CORRECTION_COLOR_TEMPERATURE =
            new Key<>("android.colorCorrection.colorTemperature", int.class);

    /** AOSP CaptureResult.java:654 -- metadata tag "android.colorCorrection.colorTint". */
    public static final Key<Integer> COLOR_CORRECTION_COLOR_TINT =
            new Key<>("android.colorCorrection.colorTint", int.class);

    /** AOSP CaptureResult.java:566 -- metadata tag "android.colorCorrection.gains". */
    public static final Key<android.hardware.camera2.params.RggbChannelVector> COLOR_CORRECTION_GAINS =
            new Key<>("android.colorCorrection.gains", android.hardware.camera2.params.RggbChannelVector.class);

    /** AOSP CaptureResult.java:510 -- metadata tag "android.colorCorrection.mode". */
    public static final Key<Integer> COLOR_CORRECTION_MODE =
            new Key<>("android.colorCorrection.mode", int.class);

    /** AOSP CaptureResult.java:538 -- metadata tag "android.colorCorrection.transform". */
    public static final Key<android.hardware.camera2.params.ColorSpaceTransform> COLOR_CORRECTION_TRANSFORM =
            new Key<>("android.colorCorrection.transform", android.hardware.camera2.params.ColorSpaceTransform.class);

    /** AOSP CaptureResult.java:712 -- metadata tag "android.control.aeAntibandingMode". */
    public static final Key<Integer> CONTROL_AE_ANTIBANDING_MODE =
            new Key<>("android.control.aeAntibandingMode", int.class);

    /** AOSP CaptureResult.java:746 -- metadata tag "android.control.aeExposureCompensation". */
    public static final Key<Integer> CONTROL_AE_EXPOSURE_COMPENSATION =
            new Key<>("android.control.aeExposureCompensation", int.class);

    /** AOSP CaptureResult.java:797 -- metadata tag "android.control.aeLock". */
    public static final Key<Boolean> CONTROL_AE_LOCK =
            new Key<>("android.control.aeLock", boolean.class);

    /** AOSP CaptureResult.java:865 -- metadata tag "android.control.aeMode". */
    public static final Key<Integer> CONTROL_AE_MODE =
            new Key<>("android.control.aeMode", int.class);

    /** AOSP CaptureResult.java:1071 -- metadata tag "android.control.aePrecaptureTrigger". */
    public static final Key<Integer> CONTROL_AE_PRECAPTURE_TRIGGER =
            new Key<>("android.control.aePrecaptureTrigger", int.class);

    /** AOSP CaptureResult.java:2995 -- metadata tag "android.control.aePriorityMode". */
    public static final Key<Integer> CONTROL_AE_PRIORITY_MODE =
            new Key<>("android.control.aePriorityMode", int.class);

    /** AOSP CaptureResult.java:957 -- metadata tag "android.control.aeRegions". */
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AE_REGIONS =
            new Key<>("android.control.aeRegions", android.hardware.camera2.params.MeteringRectangle[].class);

    /** AOSP CaptureResult.java:991 -- metadata tag "android.control.aeTargetFpsRange". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Range<Integer>> CONTROL_AE_TARGET_FPS_RANGE =
            new Key<>("android.control.aeTargetFpsRange", (Class<android.util.Range<Integer>>) (Class<?>) android.util.Range.class);

    /** AOSP CaptureResult.java:1364 -- metadata tag "android.control.afMode". */
    public static final Key<Integer> CONTROL_AF_MODE =
            new Key<>("android.control.afMode", int.class);

    /** AOSP CaptureResult.java:1457 -- metadata tag "android.control.afRegions". */
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AF_REGIONS =
            new Key<>("android.control.afRegions", android.hardware.camera2.params.MeteringRectangle[].class);

    /** AOSP CaptureResult.java:2594 -- metadata tag "android.control.afSceneChange". */
    public static final Key<Integer> CONTROL_AF_SCENE_CHANGE =
            new Key<>("android.control.afSceneChange", int.class);

    /** AOSP CaptureResult.java:1496 -- metadata tag "android.control.afTrigger". */
    public static final Key<Integer> CONTROL_AF_TRIGGER =
            new Key<>("android.control.afTrigger", int.class);

    /** AOSP CaptureResult.java:2855 -- metadata tag "android.control.autoframing". */
    public static final Key<Integer> CONTROL_AUTOFRAMING =
            new Key<>("android.control.autoframing", int.class);

    /** AOSP CaptureResult.java:2892 -- metadata tag "android.control.autoframingState". */
    public static final Key<Integer> CONTROL_AUTOFRAMING_STATE =
            new Key<>("android.control.autoframingState", int.class);

    /** AOSP CaptureResult.java:1938 -- metadata tag "android.control.awbLock". */
    public static final Key<Boolean> CONTROL_AWB_LOCK =
            new Key<>("android.control.awbLock", boolean.class);

    /** AOSP CaptureResult.java:2000 -- metadata tag "android.control.awbMode". */
    public static final Key<Integer> CONTROL_AWB_MODE =
            new Key<>("android.control.awbMode", int.class);

    /** AOSP CaptureResult.java:2093 -- metadata tag "android.control.awbRegions". */
    public static final Key<android.hardware.camera2.params.MeteringRectangle[]> CONTROL_AWB_REGIONS =
            new Key<>("android.control.awbRegions", android.hardware.camera2.params.MeteringRectangle[].class);

    /** AOSP CaptureResult.java:2139 -- metadata tag "android.control.captureIntent". */
    public static final Key<Integer> CONTROL_CAPTURE_INTENT =
            new Key<>("android.control.captureIntent", int.class);

    /** AOSP CaptureResult.java:2325 -- metadata tag "android.control.effectMode". */
    public static final Key<Integer> CONTROL_EFFECT_MODE =
            new Key<>("android.control.effectMode", int.class);

    /** AOSP CaptureResult.java:2570 -- metadata tag "android.control.enableZsl". */
    public static final Key<Boolean> CONTROL_ENABLE_ZSL =
            new Key<>("android.control.enableZsl", boolean.class);

    /** AOSP CaptureResult.java:2640 -- metadata tag "android.control.extendedSceneMode". */
    public static final Key<Integer> CONTROL_EXTENDED_SCENE_MODE =
            new Key<>("android.control.extendedSceneMode", int.class);

    /** AOSP CaptureResult.java:2916 -- metadata tag "android.control.lowLightBoostState". */
    public static final Key<Integer> CONTROL_LOW_LIGHT_BOOST_STATE =
            new Key<>("android.control.lowLightBoostState", int.class);

    /** AOSP CaptureResult.java:2369 -- metadata tag "android.control.mode". */
    public static final Key<Integer> CONTROL_MODE =
            new Key<>("android.control.mode", int.class);

    /** AOSP CaptureResult.java:2536 -- metadata tag "android.control.postRawSensitivityBoost". */
    public static final Key<Integer> CONTROL_POST_RAW_SENSITIVITY_BOOST =
            new Key<>("android.control.postRawSensitivityBoost", int.class);

    /** AOSP CaptureResult.java:2438 -- metadata tag "android.control.sceneMode". */
    public static final Key<Integer> CONTROL_SCENE_MODE =
            new Key<>("android.control.sceneMode", int.class);

    /** AOSP CaptureResult.java:2821 -- metadata tag "android.control.settingsOverride". */
    public static final Key<Integer> CONTROL_SETTINGS_OVERRIDE =
            new Key<>("android.control.settingsOverride", int.class);

    /** AOSP CaptureResult.java:2500 -- metadata tag "android.control.videoStabilizationMode". */
    public static final Key<Integer> CONTROL_VIDEO_STABILIZATION_MODE =
            new Key<>("android.control.videoStabilizationMode", int.class);

    /** AOSP CaptureResult.java:2955 -- metadata tag "android.control.zoomMethod". */
    public static final Key<Integer> CONTROL_ZOOM_METHOD =
            new Key<>("android.control.zoomMethod", int.class);

    /** AOSP CaptureResult.java:2741 -- metadata tag "android.control.zoomRatio". */
    public static final Key<Float> CONTROL_ZOOM_RATIO =
            new Key<>("android.control.zoomRatio", float.class);

    /** AOSP CaptureResult.java:6046 -- metadata tag "android.distortionCorrection.mode". */
    public static final Key<Integer> DISTORTION_CORRECTION_MODE =
            new Key<>("android.distortionCorrection.mode", int.class);

    /** AOSP CaptureResult.java:3045 -- metadata tag "android.edge.mode". */
    public static final Key<Integer> EDGE_MODE =
            new Key<>("android.edge.mode", int.class);

    /** AOSP CaptureResult.java:6070 -- metadata tag "android.extension.currentType". */
    public static final Key<Integer> EXTENSION_CURRENT_TYPE =
            new Key<>("android.extension.currentType", int.class);

    /** AOSP CaptureResult.java:6135 -- metadata tag "android.extension.nightModeIndicator". */
    public static final Key<Integer> EXTENSION_NIGHT_MODE_INDICATOR =
            new Key<>("android.extension.nightModeIndicator", int.class);

    /** AOSP CaptureResult.java:6103 -- metadata tag "android.extension.strength". */
    public static final Key<Integer> EXTENSION_STRENGTH =
            new Key<>("android.extension.strength", int.class);

    /** AOSP CaptureResult.java:3082 -- metadata tag "android.flash.mode". */
    public static final Key<Integer> FLASH_MODE =
            new Key<>("android.flash.mode", int.class);

    /** AOSP CaptureResult.java:3127 -- metadata tag "android.flash.state". */
    public static final Key<Integer> FLASH_STATE =
            new Key<>("android.flash.state", int.class);

    /** AOSP CaptureResult.java:3177 -- metadata tag "android.flash.strengthLevel". */
    public static final Key<Integer> FLASH_STRENGTH_LEVEL =
            new Key<>("android.flash.strengthLevel", int.class);

    /** AOSP CaptureResult.java:3203 -- metadata tag "android.hotPixel.mode". */
    public static final Key<Integer> HOT_PIXEL_MODE =
            new Key<>("android.hotPixel.mode", int.class);

    /** AOSP CaptureResult.java:3217 -- metadata tag "android.jpeg.gpsLocation". */
    public static final Key<android.location.Location> JPEG_GPS_LOCATION =
            new Key<>("android.jpeg.gpsLocation", android.location.Location.class);

    /** AOSP CaptureResult.java:3300 -- metadata tag "android.jpeg.orientation". */
    public static final Key<Integer> JPEG_ORIENTATION =
            new Key<>("android.jpeg.orientation", int.class);

    /** AOSP CaptureResult.java:3314 -- metadata tag "android.jpeg.quality". */
    public static final Key<Byte> JPEG_QUALITY =
            new Key<>("android.jpeg.quality", byte.class);

    /** AOSP CaptureResult.java:3327 -- metadata tag "android.jpeg.thumbnailQuality". */
    public static final Key<Byte> JPEG_THUMBNAIL_QUALITY =
            new Key<>("android.jpeg.thumbnailQuality", byte.class);

    /** AOSP CaptureResult.java:3367 -- metadata tag "android.jpeg.thumbnailSize". */
    public static final Key<android.util.Size> JPEG_THUMBNAIL_SIZE =
            new Key<>("android.jpeg.thumbnailSize", android.util.Size.class);

    /** AOSP CaptureResult.java:3405 -- metadata tag "android.lens.aperture". */
    public static final Key<Float> LENS_APERTURE =
            new Key<>("android.lens.aperture", float.class);

    /** AOSP CaptureResult.java:3861 -- metadata tag "android.lens.distortion". */
    public static final Key<float[]> LENS_DISTORTION =
            new Key<>("android.lens.distortion", float[].class);

    /** AOSP CaptureResult.java:3436 -- metadata tag "android.lens.filterDensity". */
    public static final Key<Float> LENS_FILTER_DENSITY =
            new Key<>("android.lens.filterDensity", float.class);

    /** AOSP CaptureResult.java:3465 -- metadata tag "android.lens.focalLength". */
    public static final Key<Float> LENS_FOCAL_LENGTH =
            new Key<>("android.lens.focalLength", float.class);

    /** AOSP CaptureResult.java:3485 -- metadata tag "android.lens.focusDistance". */
    public static final Key<Float> LENS_FOCUS_DISTANCE =
            new Key<>("android.lens.focusDistance", float.class);

    /** AOSP CaptureResult.java:3507 -- metadata tag "android.lens.focusRange". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Pair<Float,Float>> LENS_FOCUS_RANGE =
            new Key<>("android.lens.focusRange", (Class<android.util.Pair<Float,Float>>) (Class<?>) android.util.Pair.class);

    /** AOSP CaptureResult.java:3771 -- metadata tag "android.lens.intrinsicCalibration". */
    public static final Key<float[]> LENS_INTRINSIC_CALIBRATION =
            new Key<>("android.lens.intrinsicCalibration", float[].class);

    /** AOSP CaptureResult.java:3557 -- metadata tag "android.lens.opticalStabilizationMode". */
    public static final Key<Integer> LENS_OPTICAL_STABILIZATION_MODE =
            new Key<>("android.lens.opticalStabilizationMode", int.class);

    /** AOSP CaptureResult.java:3650 -- metadata tag "android.lens.poseRotation". */
    public static final Key<float[]> LENS_POSE_ROTATION =
            new Key<>("android.lens.poseRotation", float[].class);

    /** AOSP CaptureResult.java:3697 -- metadata tag "android.lens.poseTranslation". */
    public static final Key<float[]> LENS_POSE_TRANSLATION =
            new Key<>("android.lens.poseTranslation", float[].class);

    /** AOSP CaptureResult.java:3819 -- metadata tag "android.lens.radialDistortion". */
    public static final Key<float[]> LENS_RADIAL_DISTORTION =
            new Key<>("android.lens.radialDistortion", float[].class);

    /** AOSP CaptureResult.java:3606 -- metadata tag "android.lens.state". */
    public static final Key<Integer> LENS_STATE =
            new Key<>("android.lens.state", int.class);

    /** AOSP CaptureResult.java:5934 -- metadata tag "android.logicalMultiCamera.activePhysicalId". */
    public static final Key<String> LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID =
            new Key<>("android.logicalMultiCamera.activePhysicalId", String.class);

    /** AOSP CaptureResult.java:5982 -- metadata tag "android.logicalMultiCamera.activePhysicalSensorCropRegion". */
    public static final Key<android.graphics.Rect> LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_SENSOR_CROP_REGION =
            new Key<>("android.logicalMultiCamera.activePhysicalSensorCropRegion", android.graphics.Rect.class);

    /** AOSP CaptureResult.java:3918 -- metadata tag "android.noiseReduction.mode". */
    public static final Key<Integer> NOISE_REDUCTION_MODE =
            new Key<>("android.noiseReduction.mode", int.class);

    /** AOSP CaptureResult.java:5912 -- metadata tag "android.reprocess.effectiveExposureFactor". */
    public static final Key<Float> REPROCESS_EFFECTIVE_EXPOSURE_FACTOR =
            new Key<>("android.reprocess.effectiveExposureFactor", float.class);

    /** AOSP CaptureResult.java:3994 -- metadata tag "android.request.pipelineDepth". */
    public static final Key<Byte> REQUEST_PIPELINE_DEPTH =
            new Key<>("android.request.pipelineDepth", byte.class);

    /** AOSP CaptureResult.java:4122 -- metadata tag "android.scaler.cropRegion". */
    public static final Key<android.graphics.Rect> SCALER_CROP_REGION =
            new Key<>("android.scaler.cropRegion", android.graphics.Rect.class);

    /** AOSP CaptureResult.java:4273 -- metadata tag "android.scaler.rawCropRegion". */
    public static final Key<android.graphics.Rect> SCALER_RAW_CROP_REGION =
            new Key<>("android.scaler.rawCropRegion", android.graphics.Rect.class);

    /** AOSP CaptureResult.java:4216 -- metadata tag "android.scaler.rotateAndCrop". */
    public static final Key<Integer> SCALER_ROTATE_AND_CROP =
            new Key<>("android.scaler.rotateAndCrop", int.class);

    /** AOSP CaptureResult.java:4687 -- metadata tag "android.sensor.dynamicBlackLevel". */
    public static final Key<float[]> SENSOR_DYNAMIC_BLACK_LEVEL =
            new Key<>("android.sensor.dynamicBlackLevel", float[].class);

    /** AOSP CaptureResult.java:4711 -- metadata tag "android.sensor.dynamicWhiteLevel". */
    public static final Key<Integer> SENSOR_DYNAMIC_WHITE_LEVEL =
            new Key<>("android.sensor.dynamicWhiteLevel", int.class);

    /** AOSP CaptureResult.java:4388 -- metadata tag "android.sensor.frameDuration". */
    public static final Key<Long> SENSOR_FRAME_DURATION =
            new Key<>("android.sensor.frameDuration", long.class);

    /** AOSP CaptureResult.java:4554 -- metadata tag "android.sensor.greenSplit". */
    public static final Key<Float> SENSOR_GREEN_SPLIT =
            new Key<>("android.sensor.greenSplit", float.class);

    /** AOSP CaptureResult.java:4481 -- metadata tag "android.sensor.neutralColorPoint". */
    public static final Key<android.util.Rational[]> SENSOR_NEUTRAL_COLOR_POINT =
            new Key<>("android.sensor.neutralColorPoint", android.util.Rational[].class);

    /** AOSP CaptureResult.java:4511 -- metadata tag "android.sensor.noiseProfile". */
    @SuppressWarnings("unchecked")
    public static final Key<android.util.Pair<Double,Double>[]> SENSOR_NOISE_PROFILE =
            new Key<>("android.sensor.noiseProfile", (Class<android.util.Pair<Double,Double>[]>) (Class<?>) android.util.Pair[].class);

    /** AOSP CaptureResult.java:4784 -- metadata tag "android.sensor.pixelMode". */
    public static final Key<Integer> SENSOR_PIXEL_MODE =
            new Key<>("android.sensor.pixelMode", int.class);

    /** AOSP CaptureResult.java:4800 -- metadata tag "android.sensor.rawBinningFactorUsed". */
    public static final Key<Boolean> SENSOR_RAW_BINNING_FACTOR_USED =
            new Key<>("android.sensor.rawBinningFactorUsed", boolean.class);

    /** AOSP CaptureResult.java:4645 -- metadata tag "android.sensor.rollingShutterSkew". */
    public static final Key<Long> SENSOR_ROLLING_SHUTTER_SKEW =
            new Key<>("android.sensor.rollingShutterSkew", long.class);

    /** AOSP CaptureResult.java:4572 -- metadata tag "android.sensor.testPatternData". */
    public static final Key<int[]> SENSOR_TEST_PATTERN_DATA =
            new Key<>("android.sensor.testPatternData", int[].class);

    /** AOSP CaptureResult.java:4609 -- metadata tag "android.sensor.testPatternMode". */
    public static final Key<Integer> SENSOR_TEST_PATTERN_MODE =
            new Key<>("android.sensor.testPatternMode", int.class);

    /** AOSP CaptureResult.java:4855 -- metadata tag "android.shading.mode". */
    public static final Key<Integer> SHADING_MODE =
            new Key<>("android.shading.mode", int.class);

    /** AOSP CaptureResult.java:4998 -- metadata tag "android.statistics.faces". */
    public static final Key<android.hardware.camera2.params.Face[]> STATISTICS_FACES =
            new Key<>("android.statistics.faces", android.hardware.camera2.params.Face[].class);

    /** AOSP CaptureResult.java:4882 -- metadata tag "android.statistics.faceDetectMode". */
    public static final Key<Integer> STATISTICS_FACE_DETECT_MODE =
            new Key<>("android.statistics.faceDetectMode", int.class);

    /** AOSP CaptureResult.java:5308 -- metadata tag "android.statistics.hotPixelMap". */
    public static final Key<android.graphics.Point[]> STATISTICS_HOT_PIXEL_MAP =
            new Key<>("android.statistics.hotPixelMap", android.graphics.Point[].class);

    /** AOSP CaptureResult.java:5276 -- metadata tag "android.statistics.hotPixelMapMode". */
    public static final Key<Boolean> STATISTICS_HOT_PIXEL_MAP_MODE =
            new Key<>("android.statistics.hotPixelMapMode", boolean.class);

    /** AOSP CaptureResult.java:5464 -- metadata tag "android.statistics.lensIntrinsicsSamples". */
    public static final Key<android.hardware.camera2.params.LensIntrinsicsSample[]> STATISTICS_LENS_INTRINSICS_SAMPLES =
            new Key<>("android.statistics.lensIntrinsicsSamples", android.hardware.camera2.params.LensIntrinsicsSample[].class);

    /** AOSP CaptureResult.java:5079 -- metadata tag "android.statistics.lensShadingCorrectionMap". */
    public static final Key<android.hardware.camera2.params.LensShadingMap> STATISTICS_LENS_SHADING_CORRECTION_MAP =
            new Key<>("android.statistics.lensShadingCorrectionMap", android.hardware.camera2.params.LensShadingMap.class);

    /** AOSP CaptureResult.java:5338 -- metadata tag "android.statistics.lensShadingMapMode". */
    public static final Key<Integer> STATISTICS_LENS_SHADING_MAP_MODE =
            new Key<>("android.statistics.lensShadingMapMode", int.class);

    /** AOSP CaptureResult.java:5366 -- metadata tag "android.statistics.oisDataMode". */
    public static final Key<Integer> STATISTICS_OIS_DATA_MODE =
            new Key<>("android.statistics.oisDataMode", int.class);

    /** AOSP CaptureResult.java:5441 -- metadata tag "android.statistics.oisSamples". */
    public static final Key<android.hardware.camera2.params.OisSample[]> STATISTICS_OIS_SAMPLES =
            new Key<>("android.statistics.oisSamples", android.hardware.camera2.params.OisSample[].class);

    /** AOSP CaptureResult.java:5260 -- metadata tag "android.statistics.sceneFlicker". */
    public static final Key<Integer> STATISTICS_SCENE_FLICKER =
            new Key<>("android.statistics.sceneFlicker", int.class);

    /** AOSP CaptureResult.java:5651 -- metadata tag "android.tonemap.curve". */
    public static final Key<android.hardware.camera2.params.TonemapCurve> TONEMAP_CURVE =
            new Key<>("android.tonemap.curve", android.hardware.camera2.params.TonemapCurve.class);

    /** AOSP CaptureResult.java:5726 -- metadata tag "android.tonemap.gamma". */
    public static final Key<Float> TONEMAP_GAMMA =
            new Key<>("android.tonemap.gamma", float.class);

    /** AOSP CaptureResult.java:5702 -- metadata tag "android.tonemap.mode". */
    public static final Key<Integer> TONEMAP_MODE =
            new Key<>("android.tonemap.mode", int.class);

    /** AOSP CaptureResult.java:5753 -- metadata tag "android.tonemap.presetCurve". */
    public static final Key<Integer> TONEMAP_PRESET_CURVE =
            new Key<>("android.tonemap.presetCurve", int.class);
}
