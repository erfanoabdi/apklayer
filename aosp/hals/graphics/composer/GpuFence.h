// A sync_file from the GPU, for hosts without a DRM render node (the Halium phones: Mali through Android's libEGL): an
// EGL_ANDROID_native_fence_sync fence on an empty flush of a 1x1 pbuffer context. It signals as soon as the GPU has nothing before it, so its time
// is about the call's. EGL and GLES are loaded only when this is used (dlopen: no build dependency); the types and enums are EGL's (Khronos ABI).
#pragma once

#include <dlfcn.h>

#include <cstdint>
#include <cstring>

#include <log/log.h>

namespace apklayer::composer {

class GpuFence {
  public:
    ~GpuFence() {
        if (mCtx) {
            mMakeCurrent(mDpy, nullptr, nullptr, nullptr);
            reinterpret_cast<unsigned (*)(void*, void*)>(sym("eglDestroyContext"))(mDpy, mCtx);
            reinterpret_cast<unsigned (*)(void*, void*)>(sym("eglDestroySurface"))(mDpy, mSurface);
        }
    }

    // a new signaled-soon sync_file, or -1
    int get() {
        if (!mTried) init();
        if (!mCtx) return -1;
        if (!mMakeCurrent(mDpy, mSurface, mSurface, mCtx)) return -1;  // the binder thread of this call (calls are serialized)
        int fd = -1;
        if (void* sync = mCreateSync(mDpy, kSyncNativeFence, nullptr)) {
            mFlush();  // the fence fd exists once the sync is flushed
            fd = mDupFence(mDpy, sync);
            mDestroySync(mDpy, sync);
        }
        mMakeCurrent(mDpy, nullptr, nullptr, nullptr);
        return fd;
    }

  private:
    static constexpr int32_t kNone = 0x3038, kSurfaceType = 0x3033, kPbufferBit = 0x1, kRenderableType = 0x3040, kEs2Bit = 0x4,
                             kWidth = 0x3057, kHeight = 0x3056, kClientVersion = 0x3098, kEsApi = 0x30A0, kSyncNativeFence = 0x3144;

    void* sym(const char* name) {
        void* f = dlsym(mEgl, name);
        if (!f && mGetProc) f = mGetProc(name);
        return f;
    }

    void init() {
        mTried = true;
        mEgl = dlopen("libEGL.so", RTLD_NOW | RTLD_LOCAL);
        void* gles = dlopen("libGLESv2.so", RTLD_NOW | RTLD_LOCAL);
        if (!mEgl || !gles) { ALOGW("present fences: no libEGL/libGLESv2 (%s)", dlerror()); return; }
        mGetProc = reinterpret_cast<void* (*)(const char*)>(dlsym(mEgl, "eglGetProcAddress"));
        auto getDisplay = reinterpret_cast<void* (*)(void*)>(sym("eglGetDisplay"));
        auto initialize = reinterpret_cast<unsigned (*)(void*, int32_t*, int32_t*)>(sym("eglInitialize"));
        auto queryString = reinterpret_cast<const char* (*)(void*, int32_t)>(sym("eglQueryString"));
        auto bindApi = reinterpret_cast<unsigned (*)(uint32_t)>(sym("eglBindAPI"));
        auto chooseConfig = reinterpret_cast<unsigned (*)(void*, const int32_t*, void**, int32_t, int32_t*)>(sym("eglChooseConfig"));
        auto createPbuffer = reinterpret_cast<void* (*)(void*, void*, const int32_t*)>(sym("eglCreatePbufferSurface"));
        auto createContext = reinterpret_cast<void* (*)(void*, void*, void*, const int32_t*)>(sym("eglCreateContext"));
        mMakeCurrent = reinterpret_cast<unsigned (*)(void*, void*, void*, void*)>(sym("eglMakeCurrent"));
        mCreateSync = reinterpret_cast<void* (*)(void*, uint32_t, const int32_t*)>(sym("eglCreateSyncKHR"));
        mDestroySync = reinterpret_cast<unsigned (*)(void*, void*)>(sym("eglDestroySyncKHR"));
        mDupFence = reinterpret_cast<int (*)(void*, void*)>(sym("eglDupNativeFenceFDANDROID"));
        mFlush = reinterpret_cast<void (*)()>(dlsym(gles, "glFlush"));
        if (!getDisplay || !initialize || !queryString || !bindApi || !chooseConfig || !createPbuffer || !createContext || !mMakeCurrent ||
            !mCreateSync || !mDestroySync || !mDupFence || !mFlush) { ALOGW("present fences: EGL lacks a native fence entry point"); return; }
        mDpy = getDisplay(nullptr);  // EGL_DEFAULT_DISPLAY
        if (!mDpy || !initialize(mDpy, nullptr, nullptr)) { ALOGW("present fences: no EGL display"); return; }
        const char* ext = queryString(mDpy, 0x3055 /* EGL_EXTENSIONS */);
        if (!ext || !strstr(ext, "EGL_ANDROID_native_fence_sync")) { ALOGW("present fences: EGL has no EGL_ANDROID_native_fence_sync"); return; }
        bindApi(kEsApi);
        const int32_t configAttrs[] = {kSurfaceType, kPbufferBit, kRenderableType, kEs2Bit, kNone};
        void* config = nullptr;
        int32_t n = 0;
        if (!chooseConfig(mDpy, configAttrs, &config, 1, &n) || n < 1) { ALOGW("present fences: no pbuffer config"); return; }
        const int32_t surfaceAttrs[] = {kWidth, 1, kHeight, 1, kNone};
        const int32_t contextAttrs[] = {kClientVersion, 2, kNone};
        mSurface = createPbuffer(mDpy, config, surfaceAttrs);
        void* ctx = mSurface ? createContext(mDpy, config, nullptr, contextAttrs) : nullptr;
        if (!ctx) { ALOGW("present fences: no EGL context"); return; }
        mCtx = ctx;
        ALOGW("present fences: no render node, GPU fences through EGL");
    }

    bool mTried = false;
    void* mEgl = nullptr;
    void* (*mGetProc)(const char*) = nullptr;
    void* mDpy = nullptr;
    void* mSurface = nullptr;
    void* mCtx = nullptr;
    unsigned (*mMakeCurrent)(void*, void*, void*, void*) = nullptr;
    void* (*mCreateSync)(void*, uint32_t, const int32_t*) = nullptr;
    unsigned (*mDestroySync)(void*, void*) = nullptr;
    int (*mDupFence)(void*, void*) = nullptr;
    void (*mFlush)() = nullptr;
};

}  // namespace apklayer::composer
