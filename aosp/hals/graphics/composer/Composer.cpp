// android.hardware.graphics.composer3 for apklayer: a HWC with ONE display that is a window of apklayer-shell. Every layer is
// client composition: SurfaceFlinger composites on the GPU (RenderEngine), the finished client-target buffer is a dmabuf that
// goes to the compositor through the host display HAL (bridge/hals/graphics/display.h), zero copy. Vsync is the compositor's real
// frame callback; the period reported is measured from those callbacks, not the (possibly wrong) output mode.
#define LOG_TAG "apklayer.composer"

#include <cerrno>
#include <cstring>
#include <aidl/android/hardware/graphics/composer3/BnComposer.h>
#include <aidl/android/hardware/graphics/composer3/BnComposerClient.h>
#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <log/log.h>
#include <poll.h>

#include <atomic>
#include <map>
#include <mutex>
#include <vector>

#include "apklayer/display.h"
#include "gralloc_handle.h"

using namespace aidl::android::hardware::graphics::composer3;
using aidl::android::hardware::graphics::common::Dataspace;
using aidl::android::hardware::graphics::common::PixelFormat;
using ::ndk::ScopedAStatus;
using ::ndk::ScopedFileDescriptor;
using namespace apklayer::gralloc;

namespace {

constexpr int64_t kDisplay = 0;
constexpr int kMaxSlots = 8;

ScopedAStatus error(int32_t e) { return ScopedAStatus::fromServiceSpecificError(e); }
constexpr int32_t kBadDisplay = IComposerClient::EX_BAD_DISPLAY, kBadLayer = IComposerClient::EX_BAD_LAYER,
                  kUnsupported = IComposerClient::EX_UNSUPPORTED, kBadConfig = IComposerClient::EX_BAD_CONFIG;

int envInt(const char* name, int fallback) {
    const char* v = getenv(name);
    return v ? atoi(v) : fallback;
}

// debug (APKLAYER_RUNTIME_DEBUG): every 30th presented frame, its pixels at three points, read back through the driver
void logPixels(const apklayer_gfx_buffer& b) {
    static apklayer_gfx* gfx = apklayer_gfx_open(nullptr);
    static unsigned n;
    if (!gfx || (n++ % 30) != 0) return;
    uint32_t stride = 0;
    apklayer_gfx_mapping* map = nullptr;
    auto* p = static_cast<uint8_t*>(apklayer_gfx_map(gfx, &b, 0, false, &stride, &map));
    if (!p) { ALOGI("present %u: cannot map the frame (%s; fourcc 0x%x alloc 0x%x %ux%u packed %u stride %u)", n, strerror(errno), b.fourcc, b.alloc_fourcc, b.alloc_width, b.alloc_height, b.packed, b.stride[0]); return; }
    auto px = [&](uint32_t x, uint32_t y) { const uint8_t* q = p + y * stride + x * 4; return (q[0] << 24) | (q[1] << 16) | (q[2] << 8) | q[3]; };
    ALOGI("present %u: %ux%u pixels(bytes 0..3) (10,10)=%08x (center)=%08x (corner)=%08x", n, b.width, b.height,
          px(10, 10), px(b.width / 2, b.height / 2), px(b.width - 10, b.height - 10));
    apklayer_gfx_unmap(map);
}

class Client : public BnComposerClient {
  public:
    Client() {
        mWidth = envInt("APKLAYER_DISPLAY_WIDTH", 720);
        mHeight = envInt("APKLAYER_DISPLAY_HEIGHT", 1280);
        mDpi = envInt("APKLAYER_DISPLAY_DPI", 320);
        apklayer_display_callbacks cb = {this, &Client::onVsyncStatic, &Client::onRefreshStatic, &Client::onReleasedStatic};
        mDisplay = apklayer_display_open("apklayer", mWidth, mHeight, &cb);
        LOG_ALWAYS_FATAL_IF(mDisplay == nullptr, "cannot open the display (is apklayer-shell running?)");
        mPeriodNs = mRefreshMhz ? 1000000000000LL / mRefreshMhz : 16666667;
    }
    ~Client() override { apklayer_display_close(mDisplay); }

    // ---- display description ----
    ScopedAStatus getActiveConfig(int64_t d, int32_t* out) override { if (d != kDisplay) return error(kBadDisplay); *out = 0; return ScopedAStatus::ok(); }
    ScopedAStatus getDisplayConfigs(int64_t d, std::vector<int32_t>* out) override { if (d != kDisplay) return error(kBadDisplay); *out = {0}; return ScopedAStatus::ok(); }
    ScopedAStatus getDisplayAttribute(int64_t d, int32_t config, DisplayAttribute attr, int32_t* out) override {
        if (d != kDisplay) return error(kBadDisplay);
        if (config != 0) return error(kBadConfig);
        switch (attr) {
            case DisplayAttribute::WIDTH: *out = mWidth; break;
            case DisplayAttribute::HEIGHT: *out = mHeight; break;
            case DisplayAttribute::VSYNC_PERIOD: *out = static_cast<int32_t>(mPeriodNs); break;
            case DisplayAttribute::DPI_X: case DisplayAttribute::DPI_Y: *out = mDpi * 1000; break;
            case DisplayAttribute::CONFIG_GROUP: *out = 0; break;
            default: return error(kUnsupported);
        }
        return ScopedAStatus::ok();
    }
    ScopedAStatus getDisplayConfigurations(int64_t d, int32_t, std::vector<DisplayConfiguration>* out) override {
        if (d != kDisplay) return error(kBadDisplay);
        DisplayConfiguration c;
        c.configId = 0;
        c.width = mWidth;
        c.height = mHeight;
        c.dpi = DisplayConfiguration::Dpi{static_cast<float>(mDpi), static_cast<float>(mDpi)};
        c.configGroup = 0;
        c.vsyncPeriod = static_cast<int32_t>(mPeriodNs);
        out->push_back(c);
        return ScopedAStatus::ok();
    }
    ScopedAStatus getDisplayVsyncPeriod(int64_t d, int32_t* out) override { if (d != kDisplay) return error(kBadDisplay); *out = static_cast<int32_t>(mPeriodNs); return ScopedAStatus::ok(); }
    ScopedAStatus getDisplayName(int64_t, std::string* out) override { *out = "apklayer"; return ScopedAStatus::ok(); }
    ScopedAStatus getDisplayConnectionType(int64_t, DisplayConnectionType* out) override { *out = DisplayConnectionType::INTERNAL; return ScopedAStatus::ok(); }
    ScopedAStatus getDisplayIdentificationData(int64_t, DisplayIdentification* out) override { out->port = 0; return ScopedAStatus::ok(); }
    ScopedAStatus getDisplayCapabilities(int64_t, std::vector<DisplayCapability>* out) override { out->clear(); return ScopedAStatus::ok(); }
    ScopedAStatus getDisplayPhysicalOrientation(int64_t, aidl::android::hardware::graphics::common::Transform* out) override {
        *out = aidl::android::hardware::graphics::common::Transform::NONE;
        return ScopedAStatus::ok();
    }
    ScopedAStatus getColorModes(int64_t, std::vector<ColorMode>* out) override { *out = {ColorMode::NATIVE}; return ScopedAStatus::ok(); }
    ScopedAStatus getRenderIntents(int64_t, ColorMode, std::vector<RenderIntent>* out) override { *out = {RenderIntent::COLORIMETRIC}; return ScopedAStatus::ok(); }
    ScopedAStatus setColorMode(int64_t, ColorMode, RenderIntent) override { return ScopedAStatus::ok(); }
    ScopedAStatus getSupportedContentTypes(int64_t, std::vector<ContentType>* out) override { out->clear(); return ScopedAStatus::ok(); }
    ScopedAStatus setContentType(int64_t, ContentType) override { return ScopedAStatus::ok(); }
    ScopedAStatus getHdrCapabilities(int64_t, HdrCapabilities* out) override { *out = {}; return ScopedAStatus::ok(); }
    ScopedAStatus getOverlaySupport(OverlayProperties* out) override { *out = {}; return ScopedAStatus::ok(); }
    ScopedAStatus getDataspaceSaturationMatrix(Dataspace, std::vector<float>* out) override {
        *out = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        return ScopedAStatus::ok();
    }
    ScopedAStatus getDisplayDecorationSupport(int64_t, std::optional<aidl::android::hardware::graphics::common::DisplayDecorationSupport>* out) override {
        out->reset();
        return ScopedAStatus::ok();
    }
    ScopedAStatus getPerFrameMetadataKeys(int64_t, std::vector<PerFrameMetadataKey>* out) override { out->clear(); return ScopedAStatus::ok(); }
    ScopedAStatus getDisplayedContentSample(int64_t, int64_t, int64_t, DisplayContentSample*) override { return error(kUnsupported); }
    ScopedAStatus getDisplayedContentSamplingAttributes(int64_t, DisplayContentSamplingAttributes*) override { return error(kUnsupported); }
    ScopedAStatus setDisplayedContentSamplingEnabled(int64_t, bool, FormatColorComponent, int64_t) override { return error(kUnsupported); }
    ScopedAStatus getReadbackBufferAttributes(int64_t, ReadbackBufferAttributes*) override { return error(kUnsupported); }
    ScopedAStatus getReadbackBufferFence(int64_t, ScopedFileDescriptor*) override { return error(kUnsupported); }
    ScopedAStatus setReadbackBuffer(int64_t, const aidl::android::hardware::common::NativeHandle&, const ScopedFileDescriptor&) override { return error(kUnsupported); }

    // ---- configuration and state ----
    ScopedAStatus setActiveConfig(int64_t, int32_t config) override { return config == 0 ? ScopedAStatus::ok() : error(kBadConfig); }
    ScopedAStatus setActiveConfigWithConstraints(int64_t, int32_t, const VsyncPeriodChangeConstraints&, VsyncPeriodChangeTimeline*) override { return error(kUnsupported); }
    ScopedAStatus setBootDisplayConfig(int64_t, int32_t) override { return error(kUnsupported); }
    ScopedAStatus clearBootDisplayConfig(int64_t) override { return error(kUnsupported); }
    ScopedAStatus getPreferredBootDisplayConfig(int64_t, int32_t* out) override { *out = 0; return ScopedAStatus::ok(); }
    ScopedAStatus setAutoLowLatencyMode(int64_t, bool) override { return error(kUnsupported); }
    ScopedAStatus setPowerMode(int64_t d, PowerMode) override { return d == kDisplay ? ScopedAStatus::ok() : error(kBadDisplay); }
    ScopedAStatus setIdleTimerEnabled(int64_t, int32_t) override { return error(kUnsupported); }
    ScopedAStatus setRefreshRateChangedCallbackDebugEnabled(int64_t, bool) override { return error(kUnsupported); }
    ScopedAStatus setClientTargetSlotCount(int64_t d, int32_t) override { return d == kDisplay ? ScopedAStatus::ok() : error(kBadDisplay); }
    ScopedAStatus getMaxVirtualDisplayCount(int32_t* out) override { *out = 0; return ScopedAStatus::ok(); }
    ScopedAStatus createVirtualDisplay(int32_t, int32_t, PixelFormat, int32_t, VirtualDisplay*) override { return error(kUnsupported); }
    ScopedAStatus destroyVirtualDisplay(int64_t) override { return error(kUnsupported); }
    ScopedAStatus getHdrConversionCapabilities(std::vector<aidl::android::hardware::graphics::common::HdrConversionCapability>* out) override { out->clear(); return ScopedAStatus::ok(); }
    ScopedAStatus setHdrConversionStrategy(const aidl::android::hardware::graphics::common::HdrConversionStrategy&, aidl::android::hardware::graphics::common::Hdr*) override { return error(kUnsupported); }
    ScopedAStatus notifyExpectedPresent(int64_t, const ClockMonotonicTimestamp&, int32_t) override { return ScopedAStatus::ok(); }
    ScopedAStatus getMaxLayerPictureProfiles(int64_t, int32_t* out) override { *out = 0; return ScopedAStatus::ok(); }
    ScopedAStatus startHdcpNegotiation(int64_t, const aidl::android::hardware::drm::HdcpLevels&) override { return error(kUnsupported); }
    ScopedAStatus getLuts(int64_t, const std::vector<Buffer>&, std::vector<Luts>*) override { return error(kUnsupported); }
    ScopedAStatus getDisplayKnownVsyncSample(int64_t, VsyncSample*) override { return error(kUnsupported); }

    ScopedAStatus registerCallback(const std::shared_ptr<IComposerCallback>& callback) override {
        {
            std::lock_guard<std::mutex> l(mLock);
            mCallback = callback;
        }
        callback->onHotplugEvent(kDisplay, aidl::android::hardware::graphics::common::DisplayHotplugEvent::CONNECTED);
        return ScopedAStatus::ok();
    }

    ScopedAStatus setVsyncEnabled(int64_t d, bool enabled) override {
        if (d != kDisplay) return error(kBadDisplay);
        if (enabled && !mVsyncEnabled.exchange(true)) apklayer_display_request_vsync(mDisplay);
        if (!enabled) mVsyncEnabled = false;
        return ScopedAStatus::ok();
    }

    // ---- layers: all client composition, so only their lifetime and composition types matter ----
    ScopedAStatus createLayer(int64_t d, int32_t, int64_t* out) override {
        if (d != kDisplay) return error(kBadDisplay);
        std::lock_guard<std::mutex> l(mLock);
        *out = ++mNextLayer;
        mLayers[*out] = Composition::INVALID;
        return ScopedAStatus::ok();
    }
    ScopedAStatus destroyLayer(int64_t d, int64_t layer) override {
        if (d != kDisplay) return error(kBadDisplay);
        std::lock_guard<std::mutex> l(mLock);
        return mLayers.erase(layer) ? ScopedAStatus::ok() : error(kBadLayer);
    }

    ScopedAStatus executeCommands(const std::vector<DisplayCommand>& commands, std::vector<CommandResultPayload>* out) override {
        for (size_t i = 0; i < commands.size(); i++) execute(commands[i], static_cast<int32_t>(i), out);
        return ScopedAStatus::ok();
    }

  private:
    void execute(const DisplayCommand& c, int32_t index, std::vector<CommandResultPayload>* out) {
        auto fail = [&](int32_t code) {
            CommandError e;
            e.commandIndex = index;
            e.errorCode = code;
            out->push_back(CommandResultPayload::make<CommandResultPayload::Tag::error>(e));
        };
        if (c.display != kDisplay) return fail(kBadDisplay);
        std::lock_guard<std::mutex> l(mLock);

        for (const LayerCommand& lc : c.layers) {
            if (lc.layerLifecycleBatchCommandType == LayerLifecycleBatchCommandType::CREATE) mLayers.emplace(lc.layer, Composition::INVALID);
            if (lc.layerLifecycleBatchCommandType == LayerLifecycleBatchCommandType::DESTROY) { mLayers.erase(lc.layer); continue; }
            auto it = mLayers.find(lc.layer);
            if (it == mLayers.end()) return fail(kBadLayer);
            if (lc.composition) it->second = lc.composition->composition;
        }
        if (c.clientTarget) {
            const Buffer& b = c.clientTarget->buffer;
            if (b.slot < 0 || b.slot >= kMaxSlots) return fail(IComposerClient::EX_BAD_PARAMETER);
            if (b.handle) {  // a new buffer for the slot
                mClientTargets[b.slot].reset(::android::makeFromAidl(*b.handle));
                apklayer_display_invalidate_slot(mDisplay, b.slot);
            }
            mClientSlot = b.slot;
            mClientDamage = c.clientTarget->damage;
            mClientFence = dup(b.fence.get());
        }
        if (c.validateDisplay || c.presentOrValidateDisplay) validate(out);
        if (c.acceptDisplayChanges) {}  // everything is client composition already
        if (c.presentDisplay) present(out, fail);
    }

    // SurfaceFlinger asked for device/sideband/etc: composite all of it on the GPU instead.
    void validate(std::vector<CommandResultPayload>* out) {
        ChangedCompositionTypes changed;
        changed.display = kDisplay;
        for (auto& [id, type] : mLayers) {
            if (type == Composition::CLIENT) continue;
            changed.layers.push_back({id, Composition::CLIENT});
            type = Composition::CLIENT;
        }
        if (!changed.layers.empty()) out->push_back(CommandResultPayload::make<CommandResultPayload::Tag::changedCompositionTypes>(changed));
        ClientTargetPropertyWithBrightness p;
        p.display = kDisplay;
        p.clientTargetProperty.pixelFormat = PixelFormat::RGBA_8888;
        p.clientTargetProperty.dataspace = Dataspace::UNKNOWN;
        p.brightness = 1.f;
        out->push_back(CommandResultPayload::make<CommandResultPayload::Tag::clientTargetProperty>(p));
    }

    template <typename Fail>
    void present(std::vector<CommandResultPayload>* out, Fail&& fail) {
        HandleData d;
        native_handle_t* h = mClientSlot >= 0 ? mClientTargets[mClientSlot].get() : nullptr;
        if (h == nullptr || !decode(h, &d)) {
            ALOGE("presentDisplay: no usable client target (slot %d, handle %p): nothing to show", mClientSlot, h);
            return fail(IComposerClient::EX_BAD_PARAMETER);
        }
        if (mClientFence >= 0) {  // wait for the GPU to finish the composition
            pollfd p = {mClientFence, POLLIN, 0};
            poll(&p, 1, 1000);
            close(mClientFence);
            mClientFence = -1;
        }
        apklayer_gfx_buffer b = {};
        b.width = d.width;
        b.height = d.height;
        b.fourcc = d.fourcc;
        b.modifier = d.modifier;
        b.num_planes = d.numPlanes;
        b.packed = d.packed;
        b.alloc_fourcc = d.allocFourcc;
        b.alloc_width = d.allocWidth;
        b.alloc_height = d.allocHeight;
        for (int i = 0; i < kMaxPlanes; i++) b.fd[i] = i < static_cast<int>(d.numPlanes) ? h->data[i] : -1;
        for (uint32_t i = 0; i < d.numPlanes; i++) { b.stride[i] = d.stride[i]; b.offset[i] = d.offset[i]; }
        if (getenv("APKLAYER_RUNTIME_DEBUG")) logPixels(b);
        int32_t damage[4] = {0, 0, 0, 0};
        if (!mClientDamage.empty()) {
            const auto& r = mClientDamage[0];
            damage[0] = r.left; damage[1] = r.top; damage[2] = r.right - r.left; damage[3] = r.bottom - r.top;
        }
        if (apklayer_display_present(mDisplay, mClientSlot, &b, damage) != 0) return fail(IComposerClient::EX_NO_RESOURCES);
        PresentFence pf;
        pf.display = kDisplay;  // an invalid fence: shown as soon as the compositor takes it
        out->push_back(CommandResultPayload::make<CommandResultPayload::Tag::presentFence>(std::move(pf)));
        ReleaseFences rf;
        rf.display = kDisplay;
        out->push_back(CommandResultPayload::make<CommandResultPayload::Tag::releaseFences>(std::move(rf)));
    }

    // ---- the display's callbacks (the host display HAL's reader thread) ----
    static void onVsyncStatic(void* u, int64_t ts) { static_cast<Client*>(u)->onVsync(ts); }
    static void onRefreshStatic(void* u, uint32_t mhz) { static_cast<Client*>(u)->mRefreshMhz = mhz; }
    static void onReleasedStatic(void*, int) {}

    void onVsync(int64_t ts) {
        int64_t last = mLastVsync.exchange(ts);
        if (last != 0 && ts > last && ts - last < 100000000) mPeriodNs = (mPeriodNs * 7 + (ts - last)) / 8;  // measured, smoothed
        std::shared_ptr<IComposerCallback> cb;
        {
            std::lock_guard<std::mutex> l(mLock);
            cb = mCallback;
        }
        if (!mVsyncEnabled) return;
        if (cb) cb->onVsync(kDisplay, ts, static_cast<int32_t>(mPeriodNs));
        apklayer_display_request_vsync(mDisplay);
    }

    struct HandleDeleter {
        void operator()(native_handle_t* h) const { if (h) { native_handle_close(h); native_handle_delete(h); } }
    };

    apklayer_display* mDisplay = nullptr;
    int32_t mWidth, mHeight, mDpi;
    std::atomic<int64_t> mPeriodNs{16666667};
    std::atomic<uint32_t> mRefreshMhz{0};
    std::atomic<int64_t> mLastVsync{0};
    std::atomic<bool> mVsyncEnabled{false};
    std::mutex mLock;
    std::shared_ptr<IComposerCallback> mCallback;
    std::map<int64_t, Composition> mLayers;
    int64_t mNextLayer = 0;
    std::unique_ptr<native_handle_t, HandleDeleter> mClientTargets[kMaxSlots];
    int mClientSlot = -1;
    int mClientFence = -1;
    std::vector<aidl::android::hardware::graphics::common::Rect> mClientDamage;
};

class Composer : public BnComposer {
  public:
    ScopedAStatus createClient(std::shared_ptr<IComposerClient>* out) override {
        if (!mClient) mClient = ndk::SharedRefBase::make<Client>();
        *out = mClient;
        return ScopedAStatus::ok();
    }
    ScopedAStatus getCapabilities(std::vector<Capability>* out) override { out->clear(); return ScopedAStatus::ok(); }

  private:
    std::shared_ptr<Client> mClient;
};

}  // namespace

extern "C" int main(int, char**) {
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();
    auto composer = ndk::SharedRefBase::make<Composer>();
    const std::string name = std::string(Composer::descriptor) + "/default";
    binder_status_t st = AServiceManager_addService(composer->asBinder().get(), name.c_str());
    LOG_ALWAYS_FATAL_IF(st != STATUS_OK, "cannot register %s: %d", name.c_str(), st);
    ABinderProcess_joinThreadPool();
    return 1;
}
