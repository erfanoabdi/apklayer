// The zero-copy present of a GLES window (EglAttach.cpp), used by EglWindow.cpp.
#pragma once

#include <EGL/egl.h>
#include <system/window.h>

#include <list>
#include <ui/GraphicBuffer.h>

namespace apklayer::egl {

// The gbm_surface's finished buffer itself handed to the Android window: attached to the window's BufferQueue (libgui's Surface::attachBuffer,
// through dlsym: libgui links libEGL, so our libEGL cannot link it), and detached again (detachNextBuffer) once SurfaceFlinger released it, to go
// back to the gbm_surface. One per Android window.
class WindowAttach {
  public:
    static WindowAttach* create(ANativeWindow* nw);  // null when the window is not a libgui Surface: the caller copies every frame
    // Presents `bo`, the locked front buffer of gbm surface `gs` (w x h): true when it was queued (the bo is ours then). False: the caller copies the
    // frame into a buffer of the window, into *dst (already dequeued, its fence *dstFence) when that is set, else into one it dequeues.
    bool present(EGLDisplay dpy, void* gs, void* bo, uint32_t w, uint32_t h, ANativeWindowBuffer** dst, int* dstFence);
    void surfaceGone(void* gs);  // gs is about to be destroyed: its buffers still in the queue are dropped when they come back

  private:
    struct Entry {
        void* gs;  // null once that gbm surface is gone
        void* bo;
        int format;
        android::sp<android::GraphicBuffer> buffer;
        bool queued;  // in the BufferQueue (attached, not yet detached)
    };
    explicit WindowAttach(ANativeWindow* nw) : mNative(nw) {}
    void reclaim();
    void giveBack(std::list<Entry>::iterator e);
    std::list<Entry>::iterator find(ANativeWindowBuffer* b);
    std::list<Entry>::iterator entryFor(void* gs, void* bo, int format, uint32_t w, uint32_t h);

    ANativeWindow* mNative;
    void* mSurface = nullptr;  // the android::Surface behind mNative
    uint64_t mConsumerUsage = 0;
    std::list<Entry> mEntries;
};

}  // namespace apklayer::egl
