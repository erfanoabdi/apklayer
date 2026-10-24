// android.hardware.camera2.CameraMetadata
//
// The abstract base of CameraCharacteristics / CaptureRequest / CaptureResult
// (all three extend it, matching the android-30 android.jar: each is
// `extends CameraMetadata<X$Key<?>>`, so the superclass is linkable ABI) and
// the home of the public enumeration constants (CONTROL_AF_MODE_*,
// LENS_FACING_*, etc.), which apps reference directly (e.g.
// CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE) when building requests --
// the constant surface must resolve with AOSP-exact values. Mirrors
// frameworks/base/core/java/android/hardware/camera2/CameraMetadata.java.
package android.hardware.camera2;

import java.util.Collections;
import java.util.List;

public abstract class CameraMetadata<TKey> {

    // Package-private, matching the android-30 jar (`CameraMetadata();`): no
    // app can subclass this on real Android, so none may here either.
    CameraMetadata() {}

    /** AOSP exposes the typed key list; subclasses override. Default empty. */
    public List<TKey> getKeys() { return Collections.emptyList(); }

    // --- LENS_FACING ---
    public static final int LENS_FACING_FRONT = 0;
    public static final int LENS_FACING_BACK = 1;
    public static final int LENS_FACING_EXTERNAL = 2;

    // --- INFO_SUPPORTED_HARDWARE_LEVEL ---
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED = 0;
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_FULL = 1;
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY = 2;
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_3 = 3;
    public static final int INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL = 4;

    // --- REQUEST_AVAILABLE_CAPABILITIES ---
    public static final int REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE = 0;
    public static final int REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR = 1;
    public static final int REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING = 2;
    public static final int REQUEST_AVAILABLE_CAPABILITIES_RAW = 3;
    public static final int REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS = 5;
    public static final int REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE = 6;
    public static final int REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING = 7;
    public static final int REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT = 8;
    public static final int REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO = 9;

    // --- CONTROL_MODE ---
    public static final int CONTROL_MODE_OFF = 0;
    public static final int CONTROL_MODE_AUTO = 1;
    public static final int CONTROL_MODE_USE_SCENE_MODE = 2;
    public static final int CONTROL_MODE_OFF_KEEP_STATE = 3;

    // --- CONTROL_AF_MODE ---
    public static final int CONTROL_AF_MODE_OFF = 0;
    public static final int CONTROL_AF_MODE_AUTO = 1;
    public static final int CONTROL_AF_MODE_MACRO = 2;
    public static final int CONTROL_AF_MODE_CONTINUOUS_VIDEO = 3;
    public static final int CONTROL_AF_MODE_CONTINUOUS_PICTURE = 4;
    public static final int CONTROL_AF_MODE_EDOF = 5;

    // --- CONTROL_AF_TRIGGER ---
    public static final int CONTROL_AF_TRIGGER_IDLE = 0;
    public static final int CONTROL_AF_TRIGGER_START = 1;
    public static final int CONTROL_AF_TRIGGER_CANCEL = 2;

    // --- CONTROL_AF_STATE ---
    public static final int CONTROL_AF_STATE_INACTIVE = 0;
    public static final int CONTROL_AF_STATE_PASSIVE_SCAN = 1;
    public static final int CONTROL_AF_STATE_PASSIVE_FOCUSED = 2;
    public static final int CONTROL_AF_STATE_ACTIVE_SCAN = 3;
    public static final int CONTROL_AF_STATE_FOCUSED_LOCKED = 4;
    public static final int CONTROL_AF_STATE_NOT_FOCUSED_LOCKED = 5;
    public static final int CONTROL_AF_STATE_PASSIVE_UNFOCUSED = 6;

    // --- CONTROL_AE_MODE ---
    public static final int CONTROL_AE_MODE_OFF = 0;
    public static final int CONTROL_AE_MODE_ON = 1;
    public static final int CONTROL_AE_MODE_ON_AUTO_FLASH = 2;
    public static final int CONTROL_AE_MODE_ON_ALWAYS_FLASH = 3;
    public static final int CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE = 4;

    // --- CONTROL_AE_PRECAPTURE_TRIGGER ---
    public static final int CONTROL_AE_PRECAPTURE_TRIGGER_IDLE = 0;
    public static final int CONTROL_AE_PRECAPTURE_TRIGGER_START = 1;
    public static final int CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL = 2;

    // --- CONTROL_AE_STATE ---
    public static final int CONTROL_AE_STATE_INACTIVE = 0;
    public static final int CONTROL_AE_STATE_SEARCHING = 1;
    public static final int CONTROL_AE_STATE_CONVERGED = 2;
    public static final int CONTROL_AE_STATE_LOCKED = 3;
    public static final int CONTROL_AE_STATE_FLASH_REQUIRED = 4;
    public static final int CONTROL_AE_STATE_PRECAPTURE = 5;

    // --- CONTROL_AWB_MODE ---
    public static final int CONTROL_AWB_MODE_OFF = 0;
    public static final int CONTROL_AWB_MODE_AUTO = 1;
    public static final int CONTROL_AWB_MODE_INCANDESCENT = 2;
    public static final int CONTROL_AWB_MODE_FLUORESCENT = 3;
    public static final int CONTROL_AWB_MODE_WARM_FLUORESCENT = 4;
    public static final int CONTROL_AWB_MODE_DAYLIGHT = 5;
    public static final int CONTROL_AWB_MODE_CLOUDY_DAYLIGHT = 6;
    public static final int CONTROL_AWB_MODE_TWILIGHT = 7;
    public static final int CONTROL_AWB_MODE_SHADE = 8;

    // --- FLASH_MODE ---
    public static final int FLASH_MODE_OFF = 0;
    public static final int FLASH_MODE_SINGLE = 1;
    public static final int FLASH_MODE_TORCH = 2;

    // --- FLASH_STATE ---
    public static final int FLASH_STATE_UNAVAILABLE = 0;
    public static final int FLASH_STATE_CHARGING = 1;
    public static final int FLASH_STATE_READY = 2;
    public static final int FLASH_STATE_FIRED = 3;
    public static final int FLASH_STATE_PARTIAL = 4;

    // --- CONTROL_CAPTURE_INTENT ---
    public static final int CONTROL_CAPTURE_INTENT_CUSTOM = 0;
    public static final int CONTROL_CAPTURE_INTENT_PREVIEW = 1;
    public static final int CONTROL_CAPTURE_INTENT_STILL_CAPTURE = 2;
    public static final int CONTROL_CAPTURE_INTENT_VIDEO_RECORD = 3;
    public static final int CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT = 4;
    public static final int CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG = 5;
    public static final int CONTROL_CAPTURE_INTENT_MANUAL = 6;

    // --- NOISE_REDUCTION_MODE ---
    public static final int NOISE_REDUCTION_MODE_OFF = 0;
    public static final int NOISE_REDUCTION_MODE_FAST = 1;
    public static final int NOISE_REDUCTION_MODE_HIGH_QUALITY = 2;
    public static final int NOISE_REDUCTION_MODE_MINIMAL = 3;
    public static final int NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG = 4;

    // --- LENS_INFO_FOCUS_DISTANCE_CALIBRATION ---
    public static final int LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED = 0;
    public static final int LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE = 1;
    public static final int LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED = 2;

    // --- REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP (API 33) ---
    // These are a BITMAP, not an enumeration: params.DynamicRangeProfiles
    // iterates `for (profile = STANDARD; profile < MAX; profile <<= 1)` over
    // them, so the powers of two and the sentinel MAX are load-bearing, not
    // cosmetic. Values copied from
    // frameworks/base/core/java/android/hardware/camera2/CameraMetadata.java
    // (main) L1297-1395. AOSP declares them `int` and DynamicRangeProfiles
    // widens each to long at its own declaration site; that is preserved.
    public static final int REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_STANDARD = 0x1;
    public static final int REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_HLG10 = 0x2;
    public static final int REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_HDR10 = 0x4;
    public static final int REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_HDR10_PLUS = 0x8;
    public static final int REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_10B_HDR_REF =
            0x10;
    public static final int
            REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_10B_HDR_REF_PO = 0x20;
    public static final int REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_10B_HDR_OEM =
            0x40;
    public static final int
            REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_10B_HDR_OEM_PO = 0x80;
    public static final int REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_REF =
            0x100;
    public static final int
            REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_REF_PO = 0x200;
    public static final int REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_OEM =
            0x400;
    public static final int
            REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_OEM_PO = 0x800;
    public static final int REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_MAX = 0x1000;

    // --- SCALER_AVAILABLE_STREAM_USE_CASES (API 33) ---
    // Consumed by params.OutputConfiguration.setStreamUseCase, whose range
    // check is `> CROPPED_RAW && < VENDOR_START` -- so both bounds are
    // behaviour, not documentation. Values from the same AOSP file, L1436-1531.
    public static final int SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT = 0x0;
    public static final int SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW = 0x1;
    public static final int SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE = 0x2;
    public static final int SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD = 0x3;
    public static final int SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL = 0x4;
    public static final int SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL = 0x5;
    public static final int SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW = 0x6;
    public static final int SCALER_AVAILABLE_STREAM_USE_CASES_VENDOR_START = 0x10000;

    // --- SENSOR_PIXEL_MODE (API 31) ---
    // params.OutputConfiguration.addSensorPixelModeUsed accepts exactly these
    // two and rejects anything else. AOSP CameraMetadata.java L3833, L3840.
    public static final int SENSOR_PIXEL_MODE_DEFAULT = 0;
    public static final int SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION = 1;

    // ---------------------------------------------------------------------
    // The remaining public CameraMetadata enum constants of API 36, mirrored
    // from frameworks/base/core/java/android/hardware/camera2/CameraMetadata.java
    // (aosp-mirror/platform_frameworks_base, android16-release). Each carries
    // the AOSP line it was taken from, and the VALUE is the one in AOSP's own
    // API signature file -- these are wire values that the HAL and the app must
    // agree on, so they are copied, never chosen.
    //
    // javac inlines an int constant into its caller, so a missing one here can
    // never be observed at runtime by an already-compiled app. They are added
    // anyway for three reasons that are not hypothetical: reflective lookups
    // (CameraMetadata.class.getField) DO see them; our own framework code
    // compares against them (OutputConfiguration.setStreamUseCase and
    // addSensorPixelModeUsed already do); and a value defined in one place is
    // one that cannot drift.
    //
    // A constant here is a NAME FOR A VALUE, not a claim that this backend
    // supports the mode it names. Whether a mode is available is answered by
    // the corresponding CameraCharacteristics key, and those return null for
    // hardware we do not have.
    // ---------------------------------------------------------------------

    /** AOSP CameraMetadata.java:1958 */
    public static final int AUTOMOTIVE_LENS_FACING_EXTERIOR_FRONT = 1;
    /** AOSP CameraMetadata.java:1970 */
    public static final int AUTOMOTIVE_LENS_FACING_EXTERIOR_LEFT = 3;
    /** AOSP CameraMetadata.java:1952 */
    public static final int AUTOMOTIVE_LENS_FACING_EXTERIOR_OTHER = 0;
    /** AOSP CameraMetadata.java:1964 */
    public static final int AUTOMOTIVE_LENS_FACING_EXTERIOR_REAR = 2;
    /** AOSP CameraMetadata.java:1976 */
    public static final int AUTOMOTIVE_LENS_FACING_EXTERIOR_RIGHT = 4;
    /** AOSP CameraMetadata.java:1987 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_OTHER = 5;
    /** AOSP CameraMetadata.java:1999 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_CENTER = 7;
    /** AOSP CameraMetadata.java:1993 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_LEFT = 6;
    /** AOSP CameraMetadata.java:2005 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_RIGHT = 8;
    /** AOSP CameraMetadata.java:2017 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_CENTER = 10;
    /** AOSP CameraMetadata.java:2011 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_LEFT = 9;
    /** AOSP CameraMetadata.java:2023 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_RIGHT = 11;
    /** AOSP CameraMetadata.java:2035 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_CENTER = 13;
    /** AOSP CameraMetadata.java:2029 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_LEFT = 12;
    /** AOSP CameraMetadata.java:2041 */
    public static final int AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_RIGHT = 14;
    /** AOSP CameraMetadata.java:2067 */
    public static final int AUTOMOTIVE_LOCATION_EXTERIOR_FRONT = 2;
    /** AOSP CameraMetadata.java:2079 */
    public static final int AUTOMOTIVE_LOCATION_EXTERIOR_LEFT = 4;
    /** AOSP CameraMetadata.java:2061 */
    public static final int AUTOMOTIVE_LOCATION_EXTERIOR_OTHER = 1;
    /** AOSP CameraMetadata.java:2073 */
    public static final int AUTOMOTIVE_LOCATION_EXTERIOR_REAR = 3;
    /** AOSP CameraMetadata.java:2085 */
    public static final int AUTOMOTIVE_LOCATION_EXTERIOR_RIGHT = 5;
    /** AOSP CameraMetadata.java:2102 */
    public static final int AUTOMOTIVE_LOCATION_EXTRA_FRONT = 7;
    /** AOSP CameraMetadata.java:2115 */
    public static final int AUTOMOTIVE_LOCATION_EXTRA_LEFT = 9;
    /** AOSP CameraMetadata.java:2095 */
    public static final int AUTOMOTIVE_LOCATION_EXTRA_OTHER = 6;
    /** AOSP CameraMetadata.java:2109 */
    public static final int AUTOMOTIVE_LOCATION_EXTRA_REAR = 8;
    /** AOSP CameraMetadata.java:2121 */
    public static final int AUTOMOTIVE_LOCATION_EXTRA_RIGHT = 10;
    /** AOSP CameraMetadata.java:2051 */
    public static final int AUTOMOTIVE_LOCATION_INTERIOR = 0;
    /** AOSP CameraMetadata.java:2235 */
    public static final int COLOR_CORRECTION_ABERRATION_MODE_FAST = 1;
    /** AOSP CameraMetadata.java:2242 */
    public static final int COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY = 2;
    /** AOSP CameraMetadata.java:2228 */
    public static final int COLOR_CORRECTION_ABERRATION_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:2218 */
    public static final int COLOR_CORRECTION_MODE_CCT = 3;
    /** AOSP CameraMetadata.java:2187 */
    public static final int COLOR_CORRECTION_MODE_FAST = 1;
    /** AOSP CameraMetadata.java:2202 */
    public static final int COLOR_CORRECTION_MODE_HIGH_QUALITY = 2;
    /** AOSP CameraMetadata.java:2173 */
    public static final int COLOR_CORRECTION_MODE_TRANSFORM_MATRIX = 0;
    /** AOSP CameraMetadata.java:2260 */
    public static final int CONTROL_AE_ANTIBANDING_MODE_50HZ = 1;
    /** AOSP CameraMetadata.java:2268 */
    public static final int CONTROL_AE_ANTIBANDING_MODE_60HZ = 2;
    /** AOSP CameraMetadata.java:2277 */
    public static final int CONTROL_AE_ANTIBANDING_MODE_AUTO = 3;
    /** AOSP CameraMetadata.java:2253 */
    public static final int CONTROL_AE_ANTIBANDING_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:2395 */
    public static final int CONTROL_AE_MODE_ON_EXTERNAL_FLASH = 5;
    /** AOSP CameraMetadata.java:2435 */
    public static final int CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY = 6;
    /** AOSP CameraMetadata.java:3440 */
    public static final int CONTROL_AE_PRIORITY_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3471 */
    public static final int CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY = 2;
    /** AOSP CameraMetadata.java:3455 */
    public static final int CONTROL_AE_PRIORITY_MODE_SENSOR_SENSITIVITY_PRIORITY = 1;
    /** AOSP CameraMetadata.java:4220 */
    public static final int CONTROL_AF_SCENE_CHANGE_DETECTED = 1;
    /** AOSP CameraMetadata.java:4214 */
    public static final int CONTROL_AF_SCENE_CHANGE_NOT_DETECTED = 0;
    /** AOSP CameraMetadata.java:3390 */
    public static final int CONTROL_AUTOFRAMING_OFF = 0;
    /** AOSP CameraMetadata.java:3396 */
    public static final int CONTROL_AUTOFRAMING_ON = 1;
    /** AOSP CameraMetadata.java:4243 */
    public static final int CONTROL_AUTOFRAMING_STATE_CONVERGED = 2;
    /** AOSP CameraMetadata.java:4236 */
    public static final int CONTROL_AUTOFRAMING_STATE_FRAMING = 1;
    /** AOSP CameraMetadata.java:4230 */
    public static final int CONTROL_AUTOFRAMING_STATE_INACTIVE = 0;
    /** AOSP CameraMetadata.java:4198 */
    public static final int CONTROL_AWB_STATE_CONVERGED = 2;
    /** AOSP CameraMetadata.java:4182 */
    public static final int CONTROL_AWB_STATE_INACTIVE = 0;
    /** AOSP CameraMetadata.java:4204 */
    public static final int CONTROL_AWB_STATE_LOCKED = 3;
    /** AOSP CameraMetadata.java:4191 */
    public static final int CONTROL_AWB_STATE_SEARCHING = 1;
    /** AOSP CameraMetadata.java:2818 */
    public static final int CONTROL_CAPTURE_INTENT_MOTION_TRACKING = 7;
    /** AOSP CameraMetadata.java:2886 */
    public static final int CONTROL_EFFECT_MODE_AQUA = 8;
    /** AOSP CameraMetadata.java:2880 */
    public static final int CONTROL_EFFECT_MODE_BLACKBOARD = 7;
    /** AOSP CameraMetadata.java:2836 */
    public static final int CONTROL_EFFECT_MODE_MONO = 1;
    /** AOSP CameraMetadata.java:2843 */
    public static final int CONTROL_EFFECT_MODE_NEGATIVE = 2;
    /** AOSP CameraMetadata.java:2828 */
    public static final int CONTROL_EFFECT_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:2866 */
    public static final int CONTROL_EFFECT_MODE_POSTERIZE = 5;
    /** AOSP CameraMetadata.java:2858 */
    public static final int CONTROL_EFFECT_MODE_SEPIA = 4;
    /** AOSP CameraMetadata.java:2851 */
    public static final int CONTROL_EFFECT_MODE_SOLARIZE = 3;
    /** AOSP CameraMetadata.java:2873 */
    public static final int CONTROL_EFFECT_MODE_WHITEBOARD = 6;
    /** AOSP CameraMetadata.java:3330 */
    public static final int CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS = 2;
    /** AOSP CameraMetadata.java:3321 */
    public static final int CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE = 1;
    /** AOSP CameraMetadata.java:3312 */
    public static final int CONTROL_EXTENDED_SCENE_MODE_DISABLED = 0;
    /** AOSP CameraMetadata.java:4261 */
    public static final int CONTROL_LOW_LIGHT_BOOST_STATE_ACTIVE = 1;
    /** AOSP CameraMetadata.java:4254 */
    public static final int CONTROL_LOW_LIGHT_BOOST_STATE_INACTIVE = 0;
    /** AOSP CameraMetadata.java:2960 */
    public static final int CONTROL_MODE_USE_EXTENDED_SCENE_MODE = 4;
    /** AOSP CameraMetadata.java:2997 */
    public static final int CONTROL_SCENE_MODE_ACTION = 2;
    /** AOSP CameraMetadata.java:3089 */
    public static final int CONTROL_SCENE_MODE_BARCODE = 16;
    /** AOSP CameraMetadata.java:3035 */
    public static final int CONTROL_SCENE_MODE_BEACH = 8;
    /** AOSP CameraMetadata.java:3081 */
    public static final int CONTROL_SCENE_MODE_CANDLELIGHT = 15;
    /** AOSP CameraMetadata.java:2970 */
    public static final int CONTROL_SCENE_MODE_DISABLED = 0;
    /** AOSP CameraMetadata.java:2990 */
    public static final int CONTROL_SCENE_MODE_FACE_PRIORITY = 1;
    /** AOSP CameraMetadata.java:3060 */
    public static final int CONTROL_SCENE_MODE_FIREWORKS = 12;
    /** AOSP CameraMetadata.java:3222 */
    public static final int CONTROL_SCENE_MODE_HDR = 18;
    /** AOSP CameraMetadata.java:3174 */
    @Deprecated
    public static final int CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO = 17;
    /** AOSP CameraMetadata.java:3009 */
    public static final int CONTROL_SCENE_MODE_LANDSCAPE = 4;
    /** AOSP CameraMetadata.java:3015 */
    public static final int CONTROL_SCENE_MODE_NIGHT = 5;
    /** AOSP CameraMetadata.java:3022 */
    public static final int CONTROL_SCENE_MODE_NIGHT_PORTRAIT = 6;
    /** AOSP CameraMetadata.java:3074 */
    public static final int CONTROL_SCENE_MODE_PARTY = 14;
    /** AOSP CameraMetadata.java:3003 */
    public static final int CONTROL_SCENE_MODE_PORTRAIT = 3;
    /** AOSP CameraMetadata.java:3041 */
    public static final int CONTROL_SCENE_MODE_SNOW = 9;
    /** AOSP CameraMetadata.java:3067 */
    public static final int CONTROL_SCENE_MODE_SPORTS = 13;
    /** AOSP CameraMetadata.java:3054 */
    public static final int CONTROL_SCENE_MODE_STEADYPHOTO = 11;
    /** AOSP CameraMetadata.java:3047 */
    public static final int CONTROL_SCENE_MODE_SUNSET = 10;
    /** AOSP CameraMetadata.java:3029 */
    public static final int CONTROL_SCENE_MODE_THEATRE = 7;
    /** AOSP CameraMetadata.java:3348 */
    public static final int CONTROL_SETTINGS_OVERRIDE_OFF = 0;
    /** AOSP CameraMetadata.java:3373 */
    public static final int CONTROL_SETTINGS_OVERRIDE_ZOOM = 1;
    /** AOSP CameraMetadata.java:3284 */
    public static final int CONTROL_VIDEO_STABILIZATION_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3290 */
    public static final int CONTROL_VIDEO_STABILIZATION_MODE_ON = 1;
    /** AOSP CameraMetadata.java:3302 */
    public static final int CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION = 2;
    /** AOSP CameraMetadata.java:3419 */
    public static final int CONTROL_ZOOM_METHOD_AUTO = 0;
    /** AOSP CameraMetadata.java:3429 */
    public static final int CONTROL_ZOOM_METHOD_ZOOM_RATIO = 1;
    /** AOSP CameraMetadata.java:4016 */
    public static final int DISTORTION_CORRECTION_MODE_FAST = 1;
    /** AOSP CameraMetadata.java:4023 */
    public static final int DISTORTION_CORRECTION_MODE_HIGH_QUALITY = 2;
    /** AOSP CameraMetadata.java:4008 */
    public static final int DISTORTION_CORRECTION_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3489 */
    public static final int EDGE_MODE_FAST = 1;
    /** AOSP CameraMetadata.java:3495 */
    public static final int EDGE_MODE_HIGH_QUALITY = 2;
    /** AOSP CameraMetadata.java:3481 */
    public static final int EDGE_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3521 */
    public static final int EDGE_MODE_ZERO_SHUTTER_LAG = 3;
    /** AOSP CameraMetadata.java:4414 */
    public static final int EXTENSION_NIGHT_MODE_INDICATOR_OFF = 1;
    /** AOSP CameraMetadata.java:4423 */
    public static final int EXTENSION_NIGHT_MODE_INDICATOR_ON = 2;
    /** AOSP CameraMetadata.java:4405 */
    public static final int EXTENSION_NIGHT_MODE_INDICATOR_UNKNOWN = 0;
    /** AOSP CameraMetadata.java:3569 */
    public static final int HOT_PIXEL_MODE_FAST = 1;
    /** AOSP CameraMetadata.java:3579 */
    public static final int HOT_PIXEL_MODE_HIGH_QUALITY = 2;
    /** AOSP CameraMetadata.java:3559 */
    public static final int HOT_PIXEL_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3589 */
    public static final int LENS_OPTICAL_STABILIZATION_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3595 */
    public static final int LENS_OPTICAL_STABILIZATION_MODE_ON = 1;
    /** AOSP CameraMetadata.java:425 */
    public static final int LENS_POSE_REFERENCE_AUTOMOTIVE = 3;
    /** AOSP CameraMetadata.java:402 */
    public static final int LENS_POSE_REFERENCE_GYROSCOPE = 1;
    /** AOSP CameraMetadata.java:393 */
    public static final int LENS_POSE_REFERENCE_PRIMARY_CAMERA = 0;
    /** AOSP CameraMetadata.java:416 */
    public static final int LENS_POSE_REFERENCE_UNDEFINED = 2;
    /** AOSP CameraMetadata.java:4328 */
    public static final int LENS_STATE_MOVING = 1;
    /** AOSP CameraMetadata.java:4314 */
    public static final int LENS_STATE_STATIONARY = 0;
    /** AOSP CameraMetadata.java:1928 */
    public static final int LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE_APPROXIMATE = 0;
    /** AOSP CameraMetadata.java:1936 */
    public static final int LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE_CALIBRATED = 1;
    /** AOSP CameraMetadata.java:1286 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_COLOR_SPACE_PROFILES = 20;
    /** AOSP CameraMetadata.java:1233 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT = 18;
    /** AOSP CameraMetadata.java:1071 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA = 11;
    /** AOSP CameraMetadata.java:1086 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME = 12;
    /** AOSP CameraMetadata.java:892 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING = 10;
    /** AOSP CameraMetadata.java:1148 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING = 15;
    /** AOSP CameraMetadata.java:640 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING = 4;
    /** AOSP CameraMetadata.java:1211 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING = 17;
    /** AOSP CameraMetadata.java:1094 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA = 13;
    /** AOSP CameraMetadata.java:1268 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE = 19;
    /** AOSP CameraMetadata.java:1102 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA = 14;
    /** AOSP CameraMetadata.java:1169 */
    public static final int REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR = 16;
    /** AOSP CameraMetadata.java:1417 */
    public static final int SCALER_CROPPING_TYPE_CENTER_ONLY = 0;
    /** AOSP CameraMetadata.java:1423 */
    public static final int SCALER_CROPPING_TYPE_FREEFORM = 1;
    /** AOSP CameraMetadata.java:3677 */
    public static final int SCALER_ROTATE_AND_CROP_180 = 2;
    /** AOSP CameraMetadata.java:3684 */
    public static final int SCALER_ROTATE_AND_CROP_270 = 3;
    /** AOSP CameraMetadata.java:3670 */
    public static final int SCALER_ROTATE_AND_CROP_90 = 1;
    /** AOSP CameraMetadata.java:3703 */
    public static final int SCALER_ROTATE_AND_CROP_AUTO = 4;
    /** AOSP CameraMetadata.java:3663 */
    public static final int SCALER_ROTATE_AND_CROP_NONE = 0;
    /** AOSP CameraMetadata.java:1555 */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR = 3;
    /** AOSP CameraMetadata.java:1550 */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG = 2;
    /** AOSP CameraMetadata.java:1545 */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG = 1;
    /** AOSP CameraMetadata.java:1572 */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO = 5;
    /** AOSP CameraMetadata.java:1580 */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR = 6;
    /** AOSP CameraMetadata.java:1563 */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB = 4;
    /** AOSP CameraMetadata.java:1540 */
    public static final int SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB = 0;
    /** AOSP CameraMetadata.java:1619 */
    public static final int SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME = 1;
    /** AOSP CameraMetadata.java:1601 */
    public static final int SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN = 0;
    /** AOSP CameraMetadata.java:1742 */
    public static final int SENSOR_READOUT_TIMESTAMP_HARDWARE = 1;
    /** AOSP CameraMetadata.java:1734 */
    public static final int SENSOR_READOUT_TIMESTAMP_NOT_SUPPORTED = 0;
    /** AOSP CameraMetadata.java:1654 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER = 10;
    /** AOSP CameraMetadata.java:1677 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT = 14;
    /** AOSP CameraMetadata.java:1718 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_D50 = 23;
    /** AOSP CameraMetadata.java:1703 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_D55 = 20;
    /** AOSP CameraMetadata.java:1708 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_D65 = 21;
    /** AOSP CameraMetadata.java:1713 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_D75 = 22;
    /** AOSP CameraMetadata.java:1628 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT = 1;
    /** AOSP CameraMetadata.java:1665 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT = 12;
    /** AOSP CameraMetadata.java:1671 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT = 13;
    /** AOSP CameraMetadata.java:1649 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER = 9;
    /** AOSP CameraMetadata.java:1644 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_FLASH = 4;
    /** AOSP CameraMetadata.java:1633 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT = 2;
    /** AOSP CameraMetadata.java:1723 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN = 24;
    /** AOSP CameraMetadata.java:1659 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_SHADE = 11;
    /** AOSP CameraMetadata.java:1688 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A = 17;
    /** AOSP CameraMetadata.java:1693 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B = 18;
    /** AOSP CameraMetadata.java:1698 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C = 19;
    /** AOSP CameraMetadata.java:1639 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN = 3;
    /** AOSP CameraMetadata.java:1683 */
    public static final int SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT = 15;
    /** AOSP CameraMetadata.java:3770 */
    public static final int SENSOR_TEST_PATTERN_MODE_COLOR_BARS = 2;
    /** AOSP CameraMetadata.java:3788 */
    public static final int SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY = 3;
    /** AOSP CameraMetadata.java:3823 */
    public static final int SENSOR_TEST_PATTERN_MODE_CUSTOM1 = 256;
    /** AOSP CameraMetadata.java:3715 */
    public static final int SENSOR_TEST_PATTERN_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3799 */
    public static final int SENSOR_TEST_PATTERN_MODE_PN9 = 4;
    /** AOSP CameraMetadata.java:3733 */
    public static final int SENSOR_TEST_PATTERN_MODE_SOLID_COLOR = 1;
    /** AOSP CameraMetadata.java:3857 */
    public static final int SHADING_MODE_FAST = 1;
    /** AOSP CameraMetadata.java:3864 */
    public static final int SHADING_MODE_HIGH_QUALITY = 2;
    /** AOSP CameraMetadata.java:3850 */
    public static final int SHADING_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3889 */
    public static final int STATISTICS_FACE_DETECT_MODE_FULL = 2;
    /** AOSP CameraMetadata.java:3875 */
    public static final int STATISTICS_FACE_DETECT_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3881 */
    public static final int STATISTICS_FACE_DETECT_MODE_SIMPLE = 1;
    /** AOSP CameraMetadata.java:3899 */
    public static final int STATISTICS_LENS_SHADING_MAP_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3905 */
    public static final int STATISTICS_LENS_SHADING_MAP_MODE_ON = 1;
    /** AOSP CameraMetadata.java:3915 */
    public static final int STATISTICS_OIS_DATA_MODE_OFF = 0;
    /** AOSP CameraMetadata.java:3925 */
    public static final int STATISTICS_OIS_DATA_MODE_ON = 1;
    /** AOSP CameraMetadata.java:4346 */
    public static final int STATISTICS_SCENE_FLICKER_50HZ = 1;
    /** AOSP CameraMetadata.java:4353 */
    public static final int STATISTICS_SCENE_FLICKER_60HZ = 2;
    /** AOSP CameraMetadata.java:4339 */
    public static final int STATISTICS_SCENE_FLICKER_NONE = 0;
    /** AOSP CameraMetadata.java:1905 */
    public static final int SYNC_MAX_LATENCY_PER_FRAME_CONTROL = 0;
    /** AOSP CameraMetadata.java:1916 */
    public static final int SYNC_MAX_LATENCY_UNKNOWN = -1;
    /** AOSP CameraMetadata.java:3943 */
    public static final int TONEMAP_MODE_CONTRAST_CURVE = 0;
    /** AOSP CameraMetadata.java:3950 */
    public static final int TONEMAP_MODE_FAST = 1;
    /** AOSP CameraMetadata.java:3969 */
    public static final int TONEMAP_MODE_GAMMA_VALUE = 3;
    /** AOSP CameraMetadata.java:3957 */
    public static final int TONEMAP_MODE_HIGH_QUALITY = 2;
    /** AOSP CameraMetadata.java:3982 */
    public static final int TONEMAP_MODE_PRESET_CURVE = 4;
    /** AOSP CameraMetadata.java:3998 */
    public static final int TONEMAP_PRESET_CURVE_REC709 = 1;
    /** AOSP CameraMetadata.java:3992 */
    public static final int TONEMAP_PRESET_CURVE_SRGB = 0;
}
