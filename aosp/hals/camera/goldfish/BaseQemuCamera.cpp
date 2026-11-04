/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define FAILURE_DEBUG_PREFIX "QemuCamera"

#include <inttypes.h>
#include <cstdlib>

#include <log/log.h>
#include <system/camera_metadata.h>

#include "BaseQemuCamera.h"

#include "debug.h"
#include "metadata_utils.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace hw {

namespace {
constexpr char kClass[] = "BaseQemuCamera";

constexpr int kMinFPS = 2;
constexpr int kMedFPS = 15;
constexpr int kMaxFPS = 30;
constexpr int64_t kOneSecondNs = 1000000000;

constexpr int64_t kMinFrameDurationNs = kOneSecondNs / kMaxFPS;
constexpr int64_t kMaxFrameDurationNs = kOneSecondNs / kMinFPS;
constexpr int64_t kDefaultFrameDurationNs = kOneSecondNs / kMedFPS;

constexpr int64_t kMinSensorExposureTimeNs = kOneSecondNs / 20000;
constexpr int64_t kMaxSensorExposureTimeNs = kOneSecondNs / 2;
constexpr int64_t kDefaultSensorExposureTimeNs = kOneSecondNs / 100;

constexpr int32_t kMinSensorSensitivity = 25;
constexpr int32_t kMaxSensorSensitivity = 1600;
constexpr int32_t kDefaultSensorSensitivity = 200;

constexpr float   kMinAperture = 1.4;
constexpr float   kMaxAperture = 16.0;
constexpr float   kDefaultAperture = 4.0;

const float kColorCorrectionGains[4] = {1.0f, 1.0f, 1.0f, 1.0f};

const camera_metadata_rational_t kRationalZero = {
    .numerator = 0, .denominator = 128
};
const camera_metadata_rational_t kRationalOne = {
    .numerator = 128, .denominator = 128
};

const camera_metadata_rational_t kColorCorrectionTransform[9] = {
    kRationalOne, kRationalZero, kRationalZero,
    kRationalZero, kRationalOne, kRationalZero,
    kRationalZero, kRationalZero, kRationalOne
};

const camera_metadata_rational kNeutralColorPoint[3] = {
    {1023, 1}, {1023, 1}, {1023, 1}
};

const double kSensorNoiseProfile[8] = {
    1.0, .000001, 1.0, .000001, 1.0, .000001, 1.0, .000001
};

// system/media/camera/docs/docs.html#dynamic_android.statistics.lensShadingMap
const float kLensShadingMap[] = {
    1.3, 1.2, 1.15, 1.2, 1.2, 1.2, 1.15, 1.2,
    1.1, 1.2, 1.2, 1.2, 1.3, 1.2, 1.3, 1.3,
    1.2, 1.2, 1.25, 1.1, 1.1, 1.1, 1.1, 1.0,
    1.0, 1.0, 1.0, 1.0, 1.2, 1.3, 1.25, 1.2,
    1.3, 1.2, 1.2, 1.3, 1.2, 1.15, 1.1, 1.2,
    1.2, 1.1, 1.0, 1.2, 1.3, 1.15, 1.2, 1.3
};

constexpr BufferUsage usageOr(const BufferUsage a, const BufferUsage b) {
    return static_cast<BufferUsage>(static_cast<uint64_t>(a) | static_cast<uint64_t>(b));
}

constexpr bool usageTest(const BufferUsage a, const BufferUsage b) {
    return (static_cast<uint64_t>(a) & static_cast<uint64_t>(b)) != 0;
}

}  // namespace

BaseQemuCamera::BaseQemuCamera(const Parameters& params)
        : mParams(params)
        , mAFStateMachine(200, 1, 2)
{}

std::tuple<PixelFormat, BufferUsage, Dataspace, int32_t>
BaseQemuCamera::overrideStreamParams(const PixelFormat format,
                                 const BufferUsage usage,
                                 const Dataspace dataspace) const {
    constexpr BufferUsage kExtraUsage = usageOr(BufferUsage::CAMERA_OUTPUT,
                                                BufferUsage::CPU_WRITE_OFTEN);

    switch (format) {
    case PixelFormat::IMPLEMENTATION_DEFINED:
        if (usageTest(usage, BufferUsage::VIDEO_ENCODER)) {
            return {PixelFormat::YCBCR_420_888, usageOr(usage, kExtraUsage),
                    Dataspace::JFIF, 8};
        } else {
            return {PixelFormat::RGBA_8888, usageOr(usage, kExtraUsage),
                    Dataspace::UNKNOWN, 4};
        }

    case PixelFormat::YCBCR_420_888:
        return {PixelFormat::YCBCR_420_888, usageOr(usage, kExtraUsage),
                Dataspace::JFIF, usageTest(usage, BufferUsage::VIDEO_ENCODER) ? 8 : 4};

    case PixelFormat::RAW16:
        return {PixelFormat::RAW16, usageOr(usage, kExtraUsage),
                Dataspace::SRGB_LINEAR, 4};

    case PixelFormat::RGBA_8888:
        return {PixelFormat::RGBA_8888, usageOr(usage, kExtraUsage),
                Dataspace::UNKNOWN, usageTest(usage, BufferUsage::VIDEO_ENCODER) ? 8 : 4};

    case PixelFormat::BLOB:
        switch (dataspace) {
        case Dataspace::JFIF:
            return {PixelFormat::BLOB, usageOr(usage, kExtraUsage),
                    Dataspace::JFIF, 4};  // JPEG
        default:
            return {format, usage, dataspace, FAILURE(kErrorBadDataspace)};
        }

    default:
        return {format, usage, dataspace, FAILURE(kErrorBadFormat)};
    }
}

float BaseQemuCamera::calculateExposureComp(const int64_t exposureNs,
                                        const int sensorSensitivity,
                                        const float aperture) {
    return (double(exposureNs) * sensorSensitivity
                * kDefaultAperture * kDefaultAperture) /
           (double(kDefaultSensorExposureTimeNs) * kDefaultSensorSensitivity
                * aperture * aperture);
}

CameraMetadata BaseQemuCamera::applyMetadata(const CameraMetadata& metadata) {
    const camera_metadata_t* const raw =
        reinterpret_cast<const camera_metadata_t*>(metadata.metadata.data());
    camera_metadata_ro_entry_t entry;

    mFrameDurationNs = getFrameDuration(raw, kDefaultFrameDurationNs,
                                        kMinFrameDurationNs, kMaxFrameDurationNs);

    if (find_camera_metadata_ro_entry(raw, ANDROID_SENSOR_EXPOSURE_TIME, &entry)) {
        mSensorExposureDurationNs = std::min(mFrameDurationNs, kDefaultSensorExposureTimeNs);
    } else {
        mSensorExposureDurationNs = entry.data.i64[0];
    }

    if (find_camera_metadata_ro_entry(raw, ANDROID_SENSOR_SENSITIVITY, &entry)) {
        mSensorSensitivity = kDefaultSensorSensitivity;
    } else {
        mSensorSensitivity = entry.data.i32[0];
    }

    if (find_camera_metadata_ro_entry(raw, ANDROID_LENS_APERTURE, &entry)) {
        mAperture = kDefaultAperture;
    } else {
        mAperture = entry.data.f[0];
    }

    const camera_metadata_enum_android_control_af_mode_t afMode =
        find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_MODE, &entry) ?
            ANDROID_CONTROL_AF_MODE_OFF :
            static_cast<camera_metadata_enum_android_control_af_mode_t>(entry.data.u8[0]);

    const camera_metadata_enum_android_control_af_trigger_t afTrigger =
        find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_TRIGGER, &entry) ?
            ANDROID_CONTROL_AF_TRIGGER_IDLE :
            static_cast<camera_metadata_enum_android_control_af_trigger_t>(entry.data.u8[0]);

    const auto af = mAFStateMachine(afMode, afTrigger);

    mExposureComp = calculateExposureComp(mSensorExposureDurationNs,
                                          mSensorSensitivity, mAperture);

    CameraMetadataMap m = parseCameraMetadataMap(metadata);

    m[ANDROID_COLOR_CORRECTION_GAINS] = kColorCorrectionGains;
    m[ANDROID_COLOR_CORRECTION_TRANSFORM] = kColorCorrectionTransform;
    m[ANDROID_CONTROL_AE_STATE] = uint8_t(ANDROID_CONTROL_AE_STATE_CONVERGED);
    m[ANDROID_CONTROL_AF_STATE] = uint8_t(af.first);
    m[ANDROID_CONTROL_AWB_STATE] = uint8_t(ANDROID_CONTROL_AWB_STATE_CONVERGED);
    m[ANDROID_FLASH_STATE] = uint8_t(ANDROID_FLASH_STATE_UNAVAILABLE);
    m[ANDROID_LENS_APERTURE] = mAperture;
    m[ANDROID_LENS_FOCUS_DISTANCE] = af.second;
    m[ANDROID_LENS_STATE] = uint8_t(getAfLensState(af.first));
    m[ANDROID_REQUEST_PIPELINE_DEPTH] = uint8_t(4);
    m[ANDROID_SENSOR_FRAME_DURATION] = mFrameDurationNs;
    m[ANDROID_SENSOR_EXPOSURE_TIME] = mSensorExposureDurationNs;
    m[ANDROID_SENSOR_SENSITIVITY] = mSensorSensitivity;
    m[ANDROID_SENSOR_TIMESTAMP] = int64_t(0);
    m[ANDROID_SENSOR_NEUTRAL_COLOR_POINT] = kNeutralColorPoint;
    m[ANDROID_SENSOR_NOISE_PROFILE] = kSensorNoiseProfile;
    m[ANDROID_SENSOR_ROLLING_SHUTTER_SKEW] = kMinSensorExposureTimeNs;
    m[ANDROID_STATISTICS_SCENE_FLICKER] = uint8_t(ANDROID_STATISTICS_SCENE_FLICKER_NONE);

    if (!find_camera_metadata_ro_entry(raw, ANDROID_STATISTICS_LENS_SHADING_MAP_MODE, &entry)
        && (entry.data.u8[0] == ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON)) {
        m[ANDROID_STATISTICS_LENS_SHADING_MAP] = kLensShadingMap;
    }

    std::optional<CameraMetadata> maybeSerialized =
        serializeCameraMetadataMap(m);

    if (maybeSerialized) {
        mCaptureResultMetadata = std::move(maybeSerialized.value());
    }

    {   // reset ANDROID_CONTROL_AF_TRIGGER to IDLE
        camera_metadata_t* const raw =
            reinterpret_cast<camera_metadata_t*>(mCaptureResultMetadata.metadata.data());

        camera_metadata_ro_entry_t entry;
        const auto newTriggerValue = ANDROID_CONTROL_AF_TRIGGER_IDLE;

        if (find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_TRIGGER, &entry)) {
            return mCaptureResultMetadata;
        } else if (entry.data.i32[0] == newTriggerValue) {
            return mCaptureResultMetadata;
        } else {
            CameraMetadata result = mCaptureResultMetadata;

            if (update_camera_metadata_entry(raw, entry.index, &newTriggerValue, 1, nullptr)) {
                ALOGW("%s:%s:%d: update_camera_metadata_entry(ANDROID_CONTROL_AF_TRIGGER) "
                      "failed", kClass, __func__, __LINE__);
            }

            return result;
        }
    }
}

CameraMetadata BaseQemuCamera::updateCaptureResultMetadata() {
    camera_metadata_t* const raw =
        reinterpret_cast<camera_metadata_t*>(mCaptureResultMetadata.metadata.data());

    const auto af = mAFStateMachine();

    camera_metadata_ro_entry_t entry;

    if (find_camera_metadata_ro_entry(raw, ANDROID_CONTROL_AF_STATE, &entry)) {
        ALOGW("%s:%s:%d: find_camera_metadata_ro_entry(ANDROID_CONTROL_AF_STATE) failed",
              kClass, __func__, __LINE__);
    } else if (update_camera_metadata_entry(raw, entry.index, &af.first, 1, nullptr)) {
        ALOGW("%s:%s:%d: update_camera_metadata_entry(ANDROID_CONTROL_AF_STATE) failed",
              kClass, __func__, __LINE__);
    }

    if (find_camera_metadata_ro_entry(raw, ANDROID_LENS_FOCUS_DISTANCE, &entry)) {
        ALOGW("%s:%s:%d: find_camera_metadata_ro_entry(ANDROID_LENS_FOCUS_DISTANCE) failed",
              kClass, __func__, __LINE__);
    } else if (update_camera_metadata_entry(raw, entry.index, &af.second, 1, nullptr)) {
        ALOGW("%s:%s:%d: update_camera_metadata_entry(ANDROID_LENS_FOCUS_DISTANCE) failed",
              kClass, __func__, __LINE__);
    }

    return metadataCompact(mCaptureResultMetadata);
}

////////////////////////////////////////////////////////////////////////////////

Span<const std::pair<int32_t, int32_t>> BaseQemuCamera::getTargetFpsRanges() const {
    // ordered to satisfy testPreviewFpsRangeByCamera
    static const std::pair<int32_t, int32_t> targetFpsRanges[] = {
        {kMinFPS, kMedFPS},
        {kMedFPS, kMedFPS},
        {kMinFPS, kMaxFPS},
        {kMaxFPS, kMaxFPS},
    };

    return targetFpsRanges;
}

Span<const Rect<uint16_t>> BaseQemuCamera::getAvailableThumbnailSizes() const {
    return {mParams.availableThumbnailResolutions.begin(),
            mParams.availableThumbnailResolutions.end()};
}

bool BaseQemuCamera::isBackFacing() const {
    return mParams.isBackFacing;
}

Span<const float> BaseQemuCamera::getAvailableApertures() const {
    static const float availableApertures[] = {
        1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0
    };

    return availableApertures;
}

std::tuple<int32_t, int32_t, int32_t> BaseQemuCamera::getMaxNumOutputStreams() const {
    return {
        1,  // raw
        2,  // processed
        1,  // jpeg
    };
}

uint32_t BaseQemuCamera::getAvailableCapabilitiesBitmap() const {
    return
        (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) |
        (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS) |
        (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW);
}

Span<const PixelFormat> BaseQemuCamera::getSupportedPixelFormats() const {
    static const PixelFormat supportedPixelFormats[] = {
        PixelFormat::IMPLEMENTATION_DEFINED,
        PixelFormat::YCBCR_420_888,
        PixelFormat::RGBA_8888,
        PixelFormat::RAW16,
        PixelFormat::BLOB,
    };

    return {supportedPixelFormats};
}

int64_t BaseQemuCamera::getMinFrameDurationNs() const {
    return kMinFrameDurationNs;
}

Rect<uint16_t> BaseQemuCamera::getSensorSize() const {
    return mParams.sensorSize;
}

int32_t BaseQemuCamera::getSensorOrientation() const {
    return mParams.sensorOrientation;
}

uint8_t BaseQemuCamera::getSensorColorFilterArrangement() const {
    return ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB;
}

std::pair<int32_t, int32_t> BaseQemuCamera::getSensorSensitivityRange() const {
    return {kMinSensorSensitivity, kMaxSensorSensitivity};
}

std::pair<int64_t, int64_t> BaseQemuCamera::getSensorExposureTimeRange() const {
    return {kMinSensorExposureTimeNs, kMaxSensorExposureTimeNs};
}

int64_t BaseQemuCamera::getSensorMaxFrameDuration() const {
    return kMaxSensorExposureTimeNs;
}

Span<const Rect<uint16_t>> BaseQemuCamera::getSupportedResolutions() const {
    return {mParams.supportedResolutions.begin(), mParams.supportedResolutions.end()};
}

std::pair<int32_t, int32_t> BaseQemuCamera::getDefaultTargetFpsRange(const RequestTemplate tpl) const {
    switch (tpl) {
    case RequestTemplate::PREVIEW:
    case RequestTemplate::VIDEO_RECORD:
    case RequestTemplate::VIDEO_SNAPSHOT:
        return {kMaxFPS, kMaxFPS};

    default:
        return {kMinFPS, kMaxFPS};
    }
}

float BaseQemuCamera::getDefaultAperture() const {
    return kDefaultAperture;
}

int64_t BaseQemuCamera::getDefaultSensorExpTime() const {
    return kDefaultSensorExposureTimeNs;
}

int64_t BaseQemuCamera::getDefaultSensorFrameDuration() const {
    return kMinFrameDurationNs;
}

int32_t BaseQemuCamera::getDefaultSensorSensitivity() const {
    return kDefaultSensorSensitivity;
}

}  // namespace hw
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
