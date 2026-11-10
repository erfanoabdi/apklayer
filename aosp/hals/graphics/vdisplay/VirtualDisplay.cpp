// apklayer's virtual display sink (PLAN archive P7.1, "one display per app"): a SurfaceFlinger virtual display whose output surface is a
// BufferQueue this process consumes. SurfaceFlinger composes the app's layer stack on the GPU (RenderEngine, no HWC involved) into
// gralloc buffers; each finished buffer goes to apklayer-shell as a dmabuf through the host display HAL, zero copy, as the
// composer does for the primary display. Resizing the window resizes the virtual display (new default buffer size, new display
// size and projection); SurfaceFlinger and the app follow.
// Where the compositor has no subsurfaces (Lipstick) the shell's navigation bar is a layer of this display: the shell sends its pixels
// (apklayer_display_set_navbar_callback) and they go on top of the app's layer stack at the bottom edge, as SystemUI's bar window does.
// btload --main libapklayer_vdisplay.so <window title> <layer stack> <width> <height>
#define LOG_TAG "apklayer.vdisplay"

#include <gui/BufferItem.h>
#include <gui/BufferItemConsumer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <log/log.h>
#include <ui/GraphicBuffer.h>
#include <ui/Rect.h>

#include <cstdio>
#include <cstdlib>
#include <climits>
#include <cstring>
#include <sys/mman.h>
#include <unistd.h>
#include <map>
#include <string>
#include <condition_variable>
#include <mutex>
#include <thread>

#include "apklayer/display.h"
#include "gralloc_handle.h"

using namespace android;
using namespace apklayer::gralloc;

namespace {

constexpr int kSlots = 8;

class Sink : public ConsumerBase::FrameAvailableListener {
  public:
    bool start(const char* title, uint32_t stack, uint32_t w, uint32_t h) {
        mStack = stack;
        mWidth = w;
        mHeight = h;
        auto [consumer, surface] = BufferItemConsumer::create(GraphicBuffer::USAGE_HW_COMPOSER | GraphicBuffer::USAGE_HW_TEXTURE, kSlots - 2);
        mConsumer = consumer;
        mSurface = surface;
        mConsumer->setName(String8("apklayer-vdisplay"));
        mConsumer->setDefaultBufferSize(w, h);
        mConsumer->setDefaultBufferFormat(PIXEL_FORMAT_RGBA_8888);
        mConsumer->setFrameAvailableListener(wp<ConsumerBase::FrameAvailableListener>::fromExisting(this));

        apklayer_display_callbacks cb = {};
        cb.user = this;
        cb.released = &Sink::onReleased;
        mDisplay = apklayer_display_open(title, w, h, &cb);
        if (mDisplay == nullptr) { ALOGE("cannot open the shell window (is apklayer-shell running?)"); return false; }

        mToken = SurfaceComposerClient::createVirtualDisplay(std::string(title), /*isSecure=*/false);
        if (mToken == nullptr) { ALOGE("createVirtualDisplay failed"); return false; }
        applyDisplay();
        // the display's token under a name, for `apklayer screenshot` (aosp/tools/screenshot.cpp: SurfaceFlinger's own captureDisplay of this display).
        // The entry outlives this process (its death link is SurfaceFlinger's); names are per-launch pids, so a stale one is never looked up
        std::string name = "apklayer.vdisplay." + std::to_string(stack);
        if (defaultServiceManager()->addService(String16(name.c_str()), mToken) != OK) ALOGW("cannot register %s (no screenshots)", name.c_str());
        ALOGI("virtual display \"%s\": layer stack %u, %ux%u", title, stack, w, h);
        // apklayer patch: only now, with the display existing; the shell's first (fitted) configure, if it came
        // meanwhile, is replayed here instead of being dropped (the display then kept the 720x1280 it opened at)
        apklayer_display_set_configure_callback(mDisplay, &Sink::onConfigure, this);
        apklayer_display_set_navbar_callback(mDisplay, &Sink::onNavbar, this);
        std::thread([this] { presentLoop(); }).detach();  // lives as long as the process (main joins the binder pool)
        return true;
    }

  private:
    void applyDisplay() {
        SurfaceComposerClient::Transaction t;
        t.setDisplaySurface(mToken, mSurface);
        t.setDisplayLayerStack(mToken, ui::LayerStack::fromValue(mStack));
        t.setDisplaySize(mToken, mWidth, mHeight);
        t.setDisplayProjection(mToken, ui::ROTATION_0, Rect(mWidth, mHeight), Rect(mWidth, mHeight));
        if (mBar != nullptr) t.setPosition(mBar, 0, static_cast<float>(mHeight) - mBarH);  // the bar stays at the bottom edge
        t.apply(/*synchronous=*/true);
    }

    // The shell's navigation bar (fd: w x h XRGB8888, -1: hidden): copied into a buffer of a layer over the app's, at the bottom edge.
    static void onNavbar(void* user, int fd, int32_t w, int32_t h) {
        auto* self = static_cast<Sink*>(user);
        std::lock_guard<std::mutex> lock(self->mLock);
        SurfaceComposerClient::Transaction t;
        if (fd < 0 || w <= 0 || h <= 0) {
            if (fd >= 0) close(fd);
            if (self->mBar != nullptr) t.hide(self->mBar).apply();
            return;
        }
        size_t len = static_cast<size_t>(w) * h * 4;
        void* px = mmap(nullptr, len, PROT_READ, MAP_SHARED, fd, 0);
        close(fd);
        if (px == MAP_FAILED) return;
        auto gb = sp<GraphicBuffer>::make(w, h, PIXEL_FORMAT_RGBA_8888, 1,
                                          GraphicBuffer::USAGE_SW_WRITE_OFTEN | GraphicBuffer::USAGE_HW_TEXTURE | GraphicBuffer::USAGE_HW_COMPOSER,
                                          "apklayer-navbar");
        void* dst = nullptr;
        if (gb->initCheck() != OK || gb->lock(GraphicBuffer::USAGE_SW_WRITE_OFTEN, &dst) != OK) {
            ALOGE("navigation bar: no %dx%d buffer", w, h);
            munmap(px, len);
            return;
        }
        for (int32_t y = 0; y < h; y++) {  // XRGB8888 (bytes B G R X) -> RGBA_8888 (R G B A), opaque
            const uint8_t* s = static_cast<const uint8_t*>(px) + static_cast<size_t>(y) * w * 4;
            uint8_t* d = static_cast<uint8_t*>(dst) + static_cast<size_t>(y) * gb->getStride() * 4;
            for (int32_t x = 0; x < w; x++, s += 4, d += 4) { d[0] = s[2]; d[1] = s[1]; d[2] = s[0]; d[3] = 0xff; }
        }
        gb->unlock();
        munmap(px, len);
        if (self->mBar == nullptr) {
            self->mBar = SurfaceComposerClient::getDefault()->createSurface(String8("apklayer-navbar"), w, h, PIXEL_FORMAT_RGBA_8888,
                                                                            ISurfaceComposerClient::eFXSurfaceBufferState);
            if (self->mBar == nullptr) { ALOGE("navigation bar: createSurface failed"); return; }
            t.setLayerStack(self->mBar, ui::LayerStack::fromValue(self->mStack));
            t.setLayer(self->mBar, INT_MAX);  // over every window of the app
            t.setFlags(self->mBar, layer_state_t::eLayerOpaque, layer_state_t::eLayerOpaque);
            ALOGI("navigation bar layer %dx%d", w, h);
        }
        self->mBarH = h;
        t.setPosition(self->mBar, 0, static_cast<float>(self->mHeight) - h);
        t.setBuffer(self->mBar, gb);
        if (self->mFramed) t.show(self->mBar);
        else t.hide(self->mBar);  // shown with the app's first frame (presentLoop): the shell's splash stays until then
        t.apply();
    }

    // a finished frame of the virtual display: SurfaceFlinger's queueBuffer (a binder call into this process) only wakes the present thread,
    // which waits for the composition's fence and hands the frame over; the fence wait no longer holds SurfaceFlinger's composition up
    void onFrameAvailable(const BufferItem&) override {
        std::lock_guard<std::mutex> lock(mQueueLock);
        mQueued++;
        mQueueCv.notify_one();
    }

    void presentLoop() {
        for (;;) {
            {
                std::unique_lock<std::mutex> lock(mQueueLock);
                mQueueCv.wait(lock, [this] { return mQueued > 0; });
                mQueued--;
            }
            BufferItem item;
            {
                std::lock_guard<std::mutex> lock(mLock);
                if (mConsumer->acquireBuffer(&item, 0) != OK) continue;
            }
            if (item.mFence != nullptr) item.mFence->waitForever("apklayer-vdisplay");  // the GPU finished composing it
            std::lock_guard<std::mutex> lock(mLock);
            present(item);
            if (!mFramed) {  // the app's first frame (no other layer is shown before it): the bar joins from the next one
                mFramed = true;
                if (mBar != nullptr) SurfaceComposerClient::Transaction().show(mBar).apply();
            }
        }
    }

    void present(BufferItem& item) {  // mLock held
        HandleData d;
        if (item.mGraphicBuffer == nullptr) { mConsumer->releaseBuffer(item); return; }
        if (!decode(item.mGraphicBuffer->handle, &d)) return presentDevice(item);
        // An old-size frame can arrive after configure. Never let it change the
        // shell's geometry back to the size we just left.
        if (d.width != mWidth || d.height != mHeight) {
            mConsumer->releaseBuffer(item);
            return;
        }
        int slot = slotFor(d.id);
        if (slot < 0) { mConsumer->releaseBuffer(item); return; }  // every slot is still on screen: drop this frame
        const native_handle_t* h = item.mGraphicBuffer->handle;
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
        for (int i = 0; i < APKLAYER_GFX_MAX_PLANES; i++) b.fd[i] = i < static_cast<int>(d.numPlanes) ? h->data[i] : -1;
        for (uint32_t i = 0; i < d.numPlanes; i++) { b.stride[i] = d.stride[i]; b.offset[i] = d.offset[i]; }
        int32_t damage[4] = {0, 0, 0, 0};
        if (apklayer_display_present(mDisplay, slot, &b, damage) != 0) { mConsumer->releaseBuffer(item); return; }
        if (mHeld[slot].mGraphicBuffer != nullptr) mConsumer->releaseBuffer(mHeld[slot]);  // the one it replaces on that slot
        mHeld[slot] = item;
        mBusy[slot] = true;
    }

    // A buffer of the device's own gralloc (a Halium device, docs/HALIUM.md: graphics provider "device"): its native_handle goes to the
    // shell as it is, which hands it to the compositor through android_wlegl.
    void presentDevice(BufferItem& item) {
        const sp<GraphicBuffer>& gb = item.mGraphicBuffer;
        if (gb->getWidth() != mWidth || gb->getHeight() != mHeight) { mConsumer->releaseBuffer(item); return; }
        int slot = slotFor(gb->getId());
        if (slot < 0) { mConsumer->releaseBuffer(item); return; }
        const native_handle_t* h = gb->handle;
        apklayer_native_buffer nb{gb->getWidth(), gb->getHeight(), gb->getStride(), static_cast<uint32_t>(gb->getPixelFormat()),
                                  gb->getUsage(), h->numFds, h->data, h->numInts, h->data + h->numFds};
        int32_t damage[4] = {0, 0, 0, 0};
        if (apklayer_display_present_native(mDisplay, slot, &nb, damage) != 0) { mConsumer->releaseBuffer(item); return; }
        if (mHeld[slot].mGraphicBuffer != nullptr) mConsumer->releaseBuffer(mHeld[slot]);
        mHeld[slot] = item;
        mBusy[slot] = true;
    }

    // Slots name buffers for the display HAL (its fds travel once per slot): one per distinct buffer, a free one reused.
    int slotFor(uint64_t id) {
        auto it = mSlotOf.find(id);
        if (it != mSlotOf.end()) return it->second;
        for (int s = 0; s < kSlots; s++) {
            if (mUsed[s] && mBusy[s]) continue;
            if (mUsed[s]) {
                mSlotOf.erase(mIdOf[s]);
                apklayer_display_invalidate_slot(mDisplay, s);
            }
            mUsed[s] = true;
            mIdOf[s] = id;
            mSlotOf[id] = s;
            return s;
        }
        return -1;
    }

    static void onReleased(void* user, int slot) {
        auto* self = static_cast<Sink*>(user);
        std::lock_guard<std::mutex> lock(self->mLock);
        if (slot < 0 || slot >= kSlots) return;
        // apklayer patch: wl_buffer.release ends the consumer's ownership too.
        // Keeping old buffers acquired until their slot is replaced prevents the
        // producer from recycling them, especially when a resize reallocates them.
        if (self->mHeld[slot].mGraphicBuffer != nullptr) {
            self->mConsumer->releaseBuffer(self->mHeld[slot]);
            self->mHeld[slot] = BufferItem();
        }
        self->mBusy[slot] = false;
    }

    // the window changed size: the display follows
    static void onConfigure(void* user, int32_t width, int32_t height, int32_t scalePercent) {
        auto* self = static_cast<Sink*>(user);
        uint32_t w = static_cast<uint32_t>(width * scalePercent / 100), h = static_cast<uint32_t>(height * scalePercent / 100);
        std::lock_guard<std::mutex> lock(self->mLock);
        if (w == 0 || h == 0 || (w == self->mWidth && h == self->mHeight) || self->mToken == nullptr) return;
        ALOGI("window resized: virtual display %ux%u -> %ux%u", self->mWidth, self->mHeight, w, h);
        self->mWidth = w;
        self->mHeight = h;
        self->mConsumer->setDefaultBufferSize(w, h);
        self->applyDisplay();
    }

    sp<BufferItemConsumer> mConsumer;
    sp<Surface> mSurface;
    sp<IBinder> mToken;
    sp<SurfaceControl> mBar;  // the navigation bar's layer, once the shell sent one
    int32_t mBarH = 0;
    bool mFramed = false;  // the display presented a frame: until then the bar stays hidden, or its layer alone makes the first frame (no splash)
    apklayer_display* mDisplay = nullptr;
    std::mutex mLock;
    std::mutex mQueueLock;  // frames SurfaceFlinger queued that the present thread has not acquired yet
    std::condition_variable mQueueCv;
    int mQueued = 0;
    uint32_t mStack = 0, mWidth = 0, mHeight = 0;
    BufferItem mHeld[kSlots];
    bool mUsed[kSlots] = {}, mBusy[kSlots] = {};
    uint64_t mIdOf[kSlots] = {};
    std::map<uint64_t, int> mSlotOf;
};

}  // namespace

extern "C" int main(int argc, char** argv) {
    if (argc < 5) { fprintf(stderr, "usage: vdisplay <window title> <layer stack> <width> <height>\n"); return 2; }
    ProcessState::self()->startThreadPool();
    sp<Sink> sink = sp<Sink>::make();
    if (!sink->start(argv[1], static_cast<uint32_t>(atoi(argv[2])), static_cast<uint32_t>(atoi(argv[3])), static_cast<uint32_t>(atoi(argv[4])))) return 1;
    IPCThreadState::self()->joinThreadPool();
    return 0;
}
