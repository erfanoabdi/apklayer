// apklayer's virtual display sink (PLAN P7.1, "one display per app"): a SurfaceFlinger virtual display whose output surface is a
// BufferQueue this process consumes. SurfaceFlinger composes the app's layer stack on the GPU (RenderEngine, no HWC involved) into
// gralloc buffers; each finished buffer goes to apklayer-shell as a dmabuf through the host display HAL, zero copy, as the
// composer does for the primary display. Resizing the window resizes the virtual display (new default buffer size, new display
// size and projection); SurfaceFlinger and the app follow.
// btload --main libapklayer_vdisplay.so <window title> <layer stack> <width> <height>
#define LOG_TAG "apklayer.vdisplay"

#include <gui/BufferItem.h>
#include <gui/BufferItemConsumer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <log/log.h>
#include <ui/GraphicBuffer.h>
#include <ui/Rect.h>

#include <cstdio>
#include <cstdlib>
#include <map>
#include <mutex>

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
        apklayer_display_set_configure_callback(mDisplay, &Sink::onConfigure, this);

        mToken = SurfaceComposerClient::createVirtualDisplay(std::string(title), /*isSecure=*/false);
        if (mToken == nullptr) { ALOGE("createVirtualDisplay failed"); return false; }
        applyDisplay();
        ALOGI("virtual display \"%s\": layer stack %u, %ux%u", title, stack, w, h);
        return true;
    }

  private:
    void applyDisplay() {
        SurfaceComposerClient::Transaction t;
        t.setDisplaySurface(mToken, mSurface);
        t.setDisplayLayerStack(mToken, ui::LayerStack::fromValue(mStack));
        t.setDisplaySize(mToken, mWidth, mHeight);
        t.setDisplayProjection(mToken, ui::ROTATION_0, Rect(mWidth, mHeight), Rect(mWidth, mHeight));
        t.apply(/*synchronous=*/true);
    }

    // a finished frame of the virtual display
    void onFrameAvailable(const BufferItem&) override {
        std::lock_guard<std::mutex> lock(mLock);
        BufferItem item;
        if (mConsumer->acquireBuffer(&item, 0) != OK) return;
        if (item.mFence != nullptr) item.mFence->waitForever("apklayer-vdisplay");  // the GPU finished composing it
        HandleData d;
        if (item.mGraphicBuffer == nullptr || !decode(item.mGraphicBuffer->handle, &d)) {
            ALOGE("a frame whose buffer is not one of ours");
            mConsumer->releaseBuffer(item);
            return;
        }
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
    apklayer_display* mDisplay = nullptr;
    std::mutex mLock;
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
