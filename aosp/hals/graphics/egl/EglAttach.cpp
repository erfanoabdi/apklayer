// Zero-copy present of a GLES window (docs/PLAN.md "Graphics performance" (c)): the host renders into a gbm_surface (its GBM platform cannot render
// into somebody else's buffer), and instead of copying each finished frame into a dequeued buffer, the gbm buffer itself is wrapped as one of our
// gralloc buffers and attached to the window's BufferQueue. Mesa's gbm surface has four buffers and fails the next frame when none is free, so each
// one comes back (detachNextBuffer) once SurfaceFlinger released it, and when all four are out a release is waited for (dequeueBuffer) before the
// swap returns. Anything this cannot do (no libgui Surface, a format or size the window does not ask for, a failed attach) falls back to the copy.
#define LOG_TAG "apklayer.egl"

#include "EglAttach.h"

#include <dlfcn.h>
#include <drm_fourcc.h>
#include <log/log.h>
#include <sys/mman.h>
#include <ui/ANativeObjectBase.h>
#include <ui/Fence.h>
#include <unistd.h>

#include <atomic>

#include "EglHost.h"
#include "gralloc_handle.h"

using android::Fence;
using android::GraphicBuffer;
using android::sp;
using namespace apklayer::gralloc;

namespace apklayer::egl {
namespace {

// Surface's layout up to its ANativeWindow (RefBase first, as the ABI places the dynamic base): static_cast to it finds the Surface object
struct SurfaceShape : android::ANativeObjectBase<ANativeWindow, SurfaceShape, android::RefBase> {};

// libgui's Surface members (non-virtual calls of the Surface implementations: `this` first)
struct Gui {
    int (*attach)(void*, ANativeWindowBuffer*);
    int (*detachNext)(void*, sp<GraphicBuffer>*, sp<Fence>*);
    int (*detach)(void*, const sp<GraphicBuffer>&);
    int (*owned)(const void*, const sp<GraphicBuffer>&, bool*);
};
const Gui* gui() {
    static const Gui g = [] {
        Gui r = {};
        void* lib = dlopen("libgui.so", RTLD_NOW);
        if (!lib) return r;
        r.attach = reinterpret_cast<decltype(r.attach)>(dlsym(lib, "_ZN7android7Surface12attachBufferEP19ANativeWindowBuffer"));
        r.detachNext = reinterpret_cast<decltype(r.detachNext)>(dlsym(lib, "_ZN7android7Surface16detachNextBufferEPNS_2spINS_13GraphicBufferEEEPNS1_INS_5FenceEEE"));
        r.detach = reinterpret_cast<decltype(r.detach)>(dlsym(lib, "_ZN7android7Surface12detachBufferERKNS_2spINS_13GraphicBufferEEE"));
        r.owned = reinterpret_cast<decltype(r.owned)>(dlsym(lib, "_ZNK7android7Surface13isBufferOwnedERKNS_2spINS_13GraphicBufferEEEPb"));
        if (!r.attach || !r.detachNext || !r.detach || !r.owned) { ALOGW("libgui without Surface::attachBuffer and friends: windows are copied"); r = {}; }
        return r;
    }();
    return g.attach ? &g : nullptr;
}

constexpr uint64_t kProducerUsage = 0x200 | 0x100;  // GRALLOC_USAGE_HW_RENDER | HW_TEXTURE, as eglCreateWindowSurface asks
std::atomic<uint32_t> gNextId{1};

// The gbm buffer as one of our gralloc buffers (gralloc_handle.h, as the allocator makes them), labelled with the window's format (1 RGBA_8888 or
// 2 RGBX_8888): what SurfaceFlinger samples is read through the handle's DRM format, so BGRA memory (Mesa's ARGB8888) is shown right.
sp<GraphicBuffer> wrap(void* bo, uint32_t w, uint32_t h, int format, uint64_t usage) {
    uint32_t stride = gbmFn<uint32_t (*)(void*)>("gbm_bo_get_stride")(bo);
    native_handle_t* h2 = native_handle_create(2, kNumInts);
    if (!h2) return nullptr;
    h2->data[0] = gbmFn<int (*)(void*)>("gbm_bo_get_fd")(bo);
    h2->data[1] = memfd_create("apklayer-gralloc-metadata", MFD_CLOEXEC);
    sp<GraphicBuffer> gb;
    if (h2->data[0] >= 0 && h2->data[1] >= 0 && ftruncate(h2->data[1], kMetadataSize) == 0) {
        if (SharedMetadata* meta = mapMetadata(h2)) {
            *meta = {};
            meta->blendMode = 2;  // PREMULTIPLIED, as the allocator's
            strcpy(meta->name, "apklayer-egl-window");
            unmapMetadata(meta);
        }
        uint32_t fourcc = gbmFn<uint32_t (*)(void*)>("gbm_bo_get_format")(bo);
        HandleData d = {};
        d.magic = kMagic;
        d.version = kVersion;
        d.width = d.allocWidth = w;
        d.height = d.allocHeight = h;
        d.layerCount = 1;
        d.androidFormat = format;
        d.fourcc = d.allocFourcc = fourcc;
        d.numPlanes = 1;
        d.stride[0] = stride;
        d.offset[0] = gbmFn<uint32_t (*)(void*, int)>("gbm_bo_get_offset")(bo, 0);
        d.usage = usage;
        d.modifier = gbmFn<uint64_t (*)(void*)>("gbm_bo_get_modifier")(bo);
        d.id = (static_cast<uint64_t>(getpid()) << 32) | 0x80000000u | gNextId++;  // the allocator's ids are its own pid's
        encode(h2, d);
        gb = sp<GraphicBuffer>::make(h2, GraphicBuffer::CLONE_HANDLE, w, h, format, 1, usage, stride / 4);
        if (gb->initCheck() != android::OK) { ALOGE("window buffer %ux%u: gralloc import failed", w, h); gb = nullptr; }
        else ALOGI("attached buffer id %llx: %ux%u fourcc 0x%x android format %d stride %u modifier 0x%llx", (unsigned long long)d.id, w, h, fourcc,
                   format, stride, (unsigned long long)d.modifier);
    }
    native_handle_close(h2);
    native_handle_delete(h2);
    return gb;
}

}  // namespace

WindowAttach* WindowAttach::create(ANativeWindow* nw) {
    int type = 0;
    if (!gui() || nw->query(nw, NATIVE_WINDOW_CONCRETE_TYPE, &type) != 0 || type != NATIVE_WINDOW_SURFACE) return nullptr;
    void* surface = static_cast<SurfaceShape*>(nw);
    Dl_info info = {};  // the object's vtable is libgui's (Surface or BBQSurface): the cast found a Surface
    if (!dladdr(*static_cast<void**>(surface), &info) || !info.dli_fname || !strstr(info.dli_fname, "libgui")) {
        ALOGW("window %p: not a libgui Surface (%s): copied", nw, info.dli_fname ? info.dli_fname : "?");
        return nullptr;
    }
    auto* a = new WindowAttach(nw);
    a->mSurface = surface;
    int consumer = 0;
    nw->query(nw, NATIVE_WINDOW_CONSUMER_USAGE_BITS, &consumer);
    a->mConsumerUsage = static_cast<uint32_t>(consumer);
    return a;
}

std::list<WindowAttach::Entry>::iterator WindowAttach::find(ANativeWindowBuffer* b) {
    for (auto e = mEntries.begin(); e != mEntries.end(); ++e) if (e->buffer->getNativeBuffer() == b) return e;
    return mEntries.end();
}

// A buffer out of the queue: back to its gbm surface, or dropped when that surface is gone.
void WindowAttach::giveBack(std::list<Entry>::iterator e) {
    e->queued = false;
    if (e->gs) gbmFn<void (*)(void*, void*)>("gbm_surface_release_buffer")(e->gs, e->bo);
    else mEntries.erase(e);
}

// Every buffer SurfaceFlinger released, out of the queue (an attach takes a free slot first, so a free buffer would otherwise stay there).
void WindowAttach::reclaim() {
    for (int i = 0; i < 64; i++) {
        sp<GraphicBuffer> gb;
        sp<Fence> fence;
        if (gui()->detachNext(mSurface, &gb, &fence) != android::OK || gb == nullptr) return;
        auto e = find(gb->getNativeBuffer());
        if (e == mEntries.end()) continue;  // one the queue allocated for a copied frame: dropped
        if (fence != nullptr && fence->isValid()) fence->wait(1000);
        giveBack(e);
    }
}

std::list<WindowAttach::Entry>::iterator WindowAttach::entryFor(void* gs, void* bo, int format, uint32_t w, uint32_t h) {
    for (auto e = mEntries.begin(); e != mEntries.end(); ++e) {
        if (e->gs != gs || e->bo != bo) continue;
        if (e->format == format) return e;
        mEntries.erase(e);  // the window asked for the other format since
        break;
    }
    sp<GraphicBuffer> gb = wrap(bo, w, h, format, kProducerUsage | mConsumerUsage);
    if (gb == nullptr) return mEntries.end();
    mEntries.push_back({gs, bo, format, gb, false});
    return std::prev(mEntries.end());
}

void WindowAttach::surfaceGone(void* gs) {
    for (auto e = mEntries.begin(); e != mEntries.end();) {
        if (e->gs != gs) { ++e; continue; }
        if (e->queued) { e->gs = nullptr; ++e; }
        else e = mEntries.erase(e);
    }
}

bool WindowAttach::present(EGLDisplay dpy, void* gs, void* bo, uint32_t w, uint32_t h, ANativeWindowBuffer** dst, int* dstFence) {
    reclaim();
    int format = 0, qw = 0, qh = 0;
    mNative->query(mNative, NATIVE_WINDOW_FORMAT, &format);
    mNative->query(mNative, NATIVE_WINDOW_WIDTH, &qw);  // the queue's own size (a resize changes it before the swap sees it)
    mNative->query(mNative, NATIVE_WINDOW_HEIGHT, &qh);
    uint32_t fourcc = gbmFn<uint32_t (*)(void*)>("gbm_bo_get_format")(bo);
    bool rgb8 = fourcc == DRM_FORMAT_ARGB8888 || fourcc == DRM_FORMAT_XRGB8888 || fourcc == DRM_FORMAT_ABGR8888 || fourcc == DRM_FORMAT_XBGR8888;
    if (!rgb8 || (format != 1 && format != 2) || qw != (int)w || qh != (int)h) return false;

    if (!gbmFn<int (*)(void*)>("gbm_surface_has_free_buffers")(gs)) {
        // all four gbm buffers are out: wait for SurfaceFlinger to release one (the queue blocks as for a copy) and take it back
        if (mNative->dequeueBuffer(mNative, dst, dstFence) != 0) { *dst = nullptr; *dstFence = -1; return false; }
        auto e = find(*dst);
        if (e == mEntries.end()) return false;  // the queue allocated one: the caller copies into it
        if (gui()->detach(mSurface, e->buffer) != android::OK) return false;  // still dequeued: the caller copies into it (it is that gbm buffer)
        if (*dstFence >= 0) { sp<Fence>::make(*dstFence)->wait(1000); }  // the Fence closes it
        *dst = nullptr;
        *dstFence = -1;
        giveBack(e);
    }

    auto e = entryFor(gs, bo, format, w, h);
    if (e == mEntries.end()) return false;
    if (int r = gui()->attach(mSurface, e->buffer->getNativeBuffer()); r != android::OK) {
        static bool told = false;
        if (!told) { told = true; ALOGW("window %p: attachBuffer failed (%d): copied", mNative, r); }
        return false;
    }
    e->queued = true;
    // shortcut: an attach that waited for a release takes the released buffer's slot and drops its release fence (BufferQueueProducer::attachBuffer);
    // SurfaceFlinger's last read of it was a composition before the latch that released it, and the kernel's implicit sync orders the dmabuf anyway.
    // A wrong-order read would show as tearing; then attach only into slots dequeued first.
    for (auto o = mEntries.begin(); o != mEntries.end();) {
        auto next = std::next(o);
        bool owned = true;
        if (o != e && o->queued && gui()->owned(mSurface, o->buffer, &owned) == android::OK && !owned) giveBack(o);
        o = next;
    }
    int r = mNative->queueBuffer(mNative, e->buffer->getNativeBuffer(), gpuFence(dpy));
    if (r != 0) {
        ALOGE("window %p: queueBuffer of an attached buffer failed (%d)", mNative, r);
        gui()->detach(mSurface, e->buffer);
        e->queued = false;
        *dst = nullptr;
        return false;  // the caller copies; the bo is still the caller's
    }
    return true;
}

}  // namespace apklayer::egl
