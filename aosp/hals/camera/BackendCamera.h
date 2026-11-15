// A camera of the instance's camera backend (bridge/hals/camera: libapklayer-camera-<backend>.so) behind Goldfish's AIDL camera provider
// (goldfish/: CameraProvider, CameraDevice, CameraDeviceSession, the metadata of BaseQemuCamera). Modelled on Goldfish's GasQemuCamera: the
// backend streams I420 frames, the latest is kept and each request's buffers are filled from it (scaled, rotated and cropped as asked,
// YUV/RGBA, JPEG through HwCamera).
#pragma once

#include <condition_variable>
#include <mutex>
#include <vector>

#include <apklayer/camera.h>

#include "goldfish/BaseQemuCamera.h"

namespace android::hardware::camera::provider::implementation::hw {

struct BackendCamera : public BaseQemuCamera {
    // nativeSizes: what the backend streams (smallest first); the other advertised sizes are scaled from one of them
    BackendCamera(const Parameters& params, std::string backendId, std::vector<Rect<uint16_t>> nativeSizes);
    ~BackendCamera() override;

    bool configure(const CameraMetadata& sessionParams, size_t nStreams, const Stream* streams, const HalStream* halStreams) override;
    void close() override;
    std::tuple<int64_t, int64_t, CameraMetadata, std::vector<StreamBuffer>, std::vector<DelayedStreamBuffer>>
        processCaptureRequest(CameraMetadata, Span<CachedStreamBuffer*>) override;

    // no RAW: the backends deliver processed frames
    uint32_t getAvailableCapabilitiesBitmap() const override;
    Span<const PixelFormat> getSupportedPixelFormats() const override;
    std::tuple<int32_t, int32_t, int32_t> getMaxNumOutputStreams() const override;

  private:
    struct StreamInfo {
        int32_t id;
        uint32_t blobBufferSize;
        PixelFormat format;
        Rect<uint16_t> size;
    };
    struct Frame {  // I420, tightly packed
        std::vector<uint8_t> data;
        uint32_t width = 0, height = 0;
        int64_t timestampNs = 0;  // the backend's capture time (CLOCK_MONOTONIC)
    };

    static void onFrame(void* user, const apklayer_camera_frame* frame);
    static void onError(void* user, int error);
    // the latest frame scaled to `size` (cropped to its aspect, rotated and cropped as the request asks) into `out`; false if no frame came in time
    bool latestFrame(Rect<uint16_t> size, Frame* out);
    bool captureYUV(const StreamInfo& si, CachedStreamBuffer* csb);
    bool captureRGBA(const StreamInfo& si, CachedStreamBuffer* csb);
    DelayedStreamBuffer captureJpeg(const StreamInfo& si, CachedStreamBuffer* csb);

    const std::string mBackendId;
    const std::vector<Rect<uint16_t>> mNativeSizes;
    apklayer_camera* mCamera = nullptr;
    bool mStreaming = false;
    std::vector<StreamInfo> mStreams;
    std::mutex mLock;
    std::condition_variable mFrameCond;
    Frame mLatest;  // guarded by mLock
    int64_t mUsedFrameNs = 0;  // timestamp of the frame the request's buffers were last filled from (guarded by mLock)
    bool mFailed = false;
    uint8_t mRotateAndCrop = ANDROID_SCALER_ROTATE_AND_CROP_NONE;  // the requests' ANDROID_SCALER_ROTATE_AND_CROP (cameraserver resolves AUTO)
};

}  // namespace android::hardware::camera::provider::implementation::hw
