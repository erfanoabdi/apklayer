// See BackendCamera.h. The capture paths follow Goldfish's GasQemuCamera.cpp, with the frame taken from the backend instead of the QEMU pipe.
#define FAILURE_DEBUG_PREFIX "BackendCamera"

#include "BackendCamera.h"

#include <algorithm>
#include <chrono>

#include <libyuv.h>
#include <log/log.h>
#include <ui/GraphicBufferMapper.h>

#include "goldfish/debug.h"

namespace android::hardware::camera::provider::implementation::hw {

BackendCamera::BackendCamera(const Parameters& params, std::string backendId, std::vector<Rect<uint16_t>> nativeSizes)
    : BaseQemuCamera(params), mBackendId(std::move(backendId)), mNativeSizes(std::move(nativeSizes)) {}

BackendCamera::~BackendCamera() { close(); }

uint32_t BackendCamera::getAvailableCapabilitiesBitmap() const {
    return (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) |
           (1U << ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS);
}

Span<const PixelFormat> BackendCamera::getSupportedPixelFormats() const {
    static const PixelFormat formats[] = {PixelFormat::IMPLEMENTATION_DEFINED, PixelFormat::YCBCR_420_888, PixelFormat::RGBA_8888, PixelFormat::BLOB};
    return {formats};
}

std::tuple<int32_t, int32_t, int32_t> BackendCamera::getMaxNumOutputStreams() const { return {0, 2, 1}; }  // raw, processed, jpeg

void BackendCamera::onFrame(void* user, const apklayer_camera_frame* f) {
    auto* self = static_cast<BackendCamera*>(user);
    const uint32_t w = f->width & ~1u, h = f->height & ~1u;
    std::lock_guard lock(self->mLock);
    Frame& out = self->mLatest;
    out.data.resize(w * h * 3 / 2);
    out.width = w, out.height = h;
    uint8_t *y = out.data.data(), *u = y + w * h, *v = u + w * h / 4;
    const auto* p0 = static_cast<const uint8_t*>(f->data[0]);
    int r = -1;
    switch (f->fourcc) {
        case APKLAYER_CAM_I420:
            r = libyuv::I420Copy(p0, f->stride[0], static_cast<const uint8_t*>(f->data[1]), f->stride[1], static_cast<const uint8_t*>(f->data[2]),
                                 f->stride[2], y, w, u, w / 2, v, w / 2, w, h);
            break;
        case APKLAYER_CAM_NV12:
            r = libyuv::NV12ToI420(p0, f->stride[0], static_cast<const uint8_t*>(f->data[1]), f->stride[1], y, w, u, w / 2, v, w / 2, w, h);
            break;
        case APKLAYER_CAM_YUYV: r = libyuv::YUY2ToI420(p0, f->stride[0], y, w, u, w / 2, v, w / 2, w, h); break;
        case APKLAYER_CAM_XRGB: r = libyuv::ARGBToI420(p0, f->stride[0], y, w, u, w / 2, v, w / 2, w, h); break;
        case APKLAYER_CAM_MJPEG: r = libyuv::MJPGToI420(p0, f->size[0], y, w, u, w / 2, v, w / 2, f->width, f->height, w, h); break;
    }
    if (r != 0) { out.width = out.height = 0; ALOGE("%s: frame format 0x%x not converted", __func__, f->fourcc); }
    self->mFrameCond.notify_all();
}

void BackendCamera::onError(void* user, int error) {
    auto* self = static_cast<BackendCamera*>(user);
    ALOGE("camera %s: the backend's stream failed (%d)", self->mBackendId.c_str(), error);
    std::lock_guard lock(self->mLock);
    self->mFailed = true;
    self->mFrameCond.notify_all();
}

bool BackendCamera::configure(const CameraMetadata& sessionParams, const size_t nStreams, const Stream* streams, const HalStream* halStreams) {
    Rect<uint16_t> need = {0, 0};  // the widest and the tallest stream
    mStreams.resize(nStreams);
    for (size_t i = 0; i < nStreams; ++i, ++streams, ++halStreams) {
        LOG_ALWAYS_FATAL_IF(streams->id != halStreams->id);
        StreamInfo& si = mStreams[i];
        si.id = streams->id;
        si.size.width = streams->width;
        si.size.height = streams->height;
        si.blobBufferSize = streams->bufferSize;
        si.format = halStreams->overrideFormat;
        need = {std::max(need.width, si.size.width), std::max(need.height, si.size.height)};
    }
    // the backend streams at the smallest of its own sizes that covers every stream (else its largest); the streams are scaled from it
    Rect<uint16_t> source = mNativeSizes.empty() ? need : mNativeSizes.back();
    for (auto it = mNativeSizes.rbegin(); it != mNativeSizes.rend(); ++it)
        if (it->width >= need.width && it->height >= need.height) source = *it;
    if (!mCamera) {
        const apklayer_camera_callbacks callbacks = {this, onFrame, onError};
        mCamera = apklayer_camera_open(mBackendId.c_str(), &callbacks);
        if (!mCamera) return FAILURE(false);
    }
    if (mStreaming) apklayer_camera_stop(mCamera);
    {
        std::lock_guard lock(mLock);
        mLatest.width = mLatest.height = 0;
        mFailed = false;
    }
    static const uint32_t fourccs[] = {APKLAYER_CAM_I420, APKLAYER_CAM_NV12, APKLAYER_CAM_YUYV, APKLAYER_CAM_XRGB, APKLAYER_CAM_MJPEG, 0};
    mStreaming = need.area() > 0 && apklayer_camera_start(mCamera, source.width, source.height, 30, fourccs) == 0;
    if (!mStreaming) return FAILURE(false);
    applyMetadata(sessionParams);
    return true;
}

void BackendCamera::close() {
    if (mCamera) apklayer_camera_close(mCamera);  // stops the stream: no callback runs after this
    mCamera = nullptr;
    mStreaming = false;
    mStreams.clear();
}

bool BackendCamera::latestFrame(const Rect<uint16_t> size, Frame* out) {
    std::unique_lock lock(mLock);
    // the first frame after a start takes a moment: a file decodes, a device opens (an iPhone as a webcam over USB: about 4 s)
    if (!mFrameCond.wait_for(lock, std::chrono::seconds(8), [this] { return mLatest.width || mFailed; }) || mFailed) return FAILURE(false);
    const uint32_t sw = mLatest.width, sh = mLatest.height, dw = size.width, dh = size.height;
    // the centre of the frame at the stream's aspect ratio
    uint32_t cw = sw, ch = sh;
    if (uint64_t(sw) * dh > uint64_t(sh) * dw) cw = (uint64_t(sh) * dw / dh) & ~1u;
    else ch = (uint64_t(sw) * dh / dw) & ~1u;
    const uint32_t x0 = (sw - cw) / 2 & ~1u, y0 = (sh - ch) / 2 & ~1u;
    const uint8_t *y = mLatest.data.data(), *u = y + sw * sh, *v = u + sw * sh / 4;
    out->data.resize(dw * dh * 3 / 2);
    out->width = dw, out->height = dh;
    uint8_t *oy = out->data.data(), *ou = oy + dw * dh, *ov = ou + dw * dh / 4;
    return libyuv::I420Scale(y + y0 * sw + x0, sw, u + y0 / 2 * (sw / 2) + x0 / 2, sw / 2, v + y0 / 2 * (sw / 2) + x0 / 2, sw / 2, cw, ch,
                             oy, dw, ou, dw / 2, ov, dw / 2, dw, dh, libyuv::kFilterBilinear) == 0;
}

std::tuple<int64_t, int64_t, CameraMetadata, std::vector<StreamBuffer>, std::vector<DelayedStreamBuffer>>
BackendCamera::processCaptureRequest(CameraMetadata metadataUpdate, Span<CachedStreamBuffer*> csbs) {
    CameraMetadata resultMetadata = metadataUpdate.metadata.empty() ? updateCaptureResultMetadata() : applyMetadata(std::move(metadataUpdate));
    std::vector<StreamBuffer> outputBuffers;
    std::vector<DelayedStreamBuffer> delayedOutputBuffers;
    outputBuffers.reserve(csbs.size());
    for (CachedStreamBuffer* csb : csbs) {
        LOG_ALWAYS_FATAL_IF(!csb);
        const StreamInfo* si = csb->getStreamInfo<StreamInfo>();
        if (!si) {
            const int32_t id = csb->getStreamId();
            const auto sii = std::find_if(mStreams.begin(), mStreams.end(), [id](const StreamInfo& s) { return id == s.id; });
            if (sii != mStreams.end()) csb->setStreamInfo(si = &*sii);
            else ALOGE("%s: could not find stream=%d in the cache", __func__, id);
        }
        if (!si) { outputBuffers.push_back(csb->finish(false)); continue; }
        switch (si->format) {
            case PixelFormat::YCBCR_420_888: outputBuffers.push_back(csb->finish(captureYUV(*si, csb))); break;
            case PixelFormat::RGBA_8888: outputBuffers.push_back(csb->finish(captureRGBA(*si, csb))); break;
            case PixelFormat::BLOB: delayedOutputBuffers.push_back(captureJpeg(*si, csb)); break;
            default:
                ALOGE("%s: unexpected format=%s", __func__, toString(si->format).c_str());
                outputBuffers.push_back(csb->finish(false));
        }
    }
    bool failed;
    {
        std::lock_guard lock(mLock);
        failed = mFailed || !mStreaming;
    }
    return make_tuple(failed ? FAILURE(-1) : mFrameDurationNs, mSensorExposureDurationNs, std::move(resultMetadata), std::move(outputBuffers),
                      std::move(delayedOutputBuffers));
}

bool BackendCamera::captureYUV(const StreamInfo& si, CachedStreamBuffer* csb) {
    Frame f;
    if (!csb->waitAcquireFence(mFrameDurationNs / 2000000) || !latestFrame(si.size, &f)) return FAILURE(false);
    const uint32_t w = si.size.width, h = si.size.height;
    android_ycbcr ycbcr;
    if (GraphicBufferMapper::get().lockYCbCr(csb->getBuffer(), static_cast<uint32_t>(BufferUsage::CPU_WRITE_OFTEN), {int32_t(w), int32_t(h)},
                                            &ycbcr) != NO_ERROR)
        return FAILURE(false);
    const uint8_t *y = f.data.data(), *u = y + w * h, *v = u + w * h / 4;
    auto *dy = static_cast<uint8_t*>(ycbcr.y), *dcb = static_cast<uint8_t*>(ycbcr.cb), *dcr = static_cast<uint8_t*>(ycbcr.cr);
    int r = -1;
    if (ycbcr.chroma_step == 1)
        r = libyuv::I420Copy(y, w, u, w / 2, v, w / 2, dy, ycbcr.ystride, dcb, ycbcr.cstride, dcr, ycbcr.cstride, w, h);
    else if (ycbcr.chroma_step == 2 && dcr == dcb + 1)  // NV12
        r = libyuv::I420ToNV12(y, w, u, w / 2, v, w / 2, dy, ycbcr.ystride, dcb, ycbcr.cstride, w, h);
    else if (ycbcr.chroma_step == 2 && dcb == dcr + 1)  // NV21
        r = libyuv::I420ToNV21(y, w, u, w / 2, v, w / 2, dy, ycbcr.ystride, dcr, ycbcr.cstride, w, h);
    LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(csb->getBuffer()) != NO_ERROR);
    return r == 0 ? true : FAILURE(false);
}

bool BackendCamera::captureRGBA(const StreamInfo& si, CachedStreamBuffer* csb) {
    Frame f;
    if (!csb->waitAcquireFence(mFrameDurationNs / 2000000) || !latestFrame(si.size, &f)) return FAILURE(false);
    const uint32_t w = si.size.width, h = si.size.height;
    void* mem = nullptr;
    uint32_t stride = 0;  // in pixels
    if (GraphicBufferMapper::get().getStride(csb->getBuffer(), &stride) != NO_ERROR || stride < w) stride = w;
    if (GraphicBufferMapper::get().lock(csb->getBuffer(), static_cast<uint32_t>(BufferUsage::CPU_WRITE_OFTEN), {int32_t(w), int32_t(h)}, &mem) != NO_ERROR)
        return FAILURE(false);
    const uint8_t *y = f.data.data(), *u = y + w * h, *v = u + w * h / 4;
    // libyuv's ABGR is R, G, B, A in memory: Android's RGBA_8888
    const int r = libyuv::I420ToABGR(y, w, u, w / 2, v, w / 2, static_cast<uint8_t*>(mem), int(stride * 4), w, h);
    LOG_ALWAYS_FATAL_IF(GraphicBufferMapper::get().unlock(csb->getBuffer()) != NO_ERROR);
    return r == 0 ? true : FAILURE(false);
}

DelayedStreamBuffer BackendCamera::captureJpeg(const StreamInfo& si, CachedStreamBuffer* csb) {
    auto image = std::make_shared<Frame>();
    if (!latestFrame(si.size, image.get())) image.reset();
    const Rect<uint16_t> size = si.size;
    const uint32_t jpegBufferSize = si.blobBufferSize;
    const int64_t frameDurationNs = mFrameDurationNs;
    CameraMetadata metadata = mCaptureResultMetadata;
    return [csb, image, size, metadata = std::move(metadata), jpegBufferSize, frameDurationNs](const bool ok) -> StreamBuffer {
        if (!ok || !image || !csb->waitAcquireFence(frameDurationNs / 1000000)) return csb->finish(false);
        const uint32_t w = size.width, h = size.height;
        android_ycbcr ycbcr = {};
        ycbcr.y = image->data.data();
        ycbcr.cb = image->data.data() + w * h;
        ycbcr.cr = image->data.data() + w * h * 5 / 4;
        ycbcr.ystride = w;
        ycbcr.cstride = w / 2;
        ycbcr.chroma_step = 1;
        return csb->finish(compressJpeg(size, ycbcr, metadata, csb->getBuffer(), jpegBufferSize));
    };
}

}  // namespace android::hardware::camera::provider::implementation::hw
