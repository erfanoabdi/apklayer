// OpenGL ES 1 for apklayer's libEGL. Android's libGLESv1_CM (staged, mapped to itself) jumps through a gl_hooks_t table that the thread's
// TLS_SLOT_OPENGL_API points at; Android's EGL loader fills it from the driver (Loader::init_api) and stores it on eglMakeCurrent, and so
// does this file: from the host's libGLESv1_CM.so.1 when the host driver has ES 1, else from libapklayer-gles1.so (gfxstream's GLES_CM
// translator on a host GLES 3 context, bridge/hals/graphics/gles1). Like Android's EGL (egl.cpp gHooks / gHooksNoContext), the slot is set on
// every eglMakeCurrent: ES 2/3 contexts get the host's GLES entry points (libGLESv1_CM and libGLESv2 share names such as glClear, and a GLES 3
// caller's symbol may bind to the ES 1 trampoline; its glGetString reads the table without a null check), no context a table of no-ops.
#define LOG_TAG "apklayer.egl"

#include "Gles1.h"

#include <dlfcn.h>
#include <log/log.h>

#include <cstring>
#include <map>
#include <mutex>

namespace apklayer::egl {
namespace {

// Android's gl_hooks_t (frameworks/native/opengl/libs/hooks.h): one pointer per entries.in line, then the extension slots
#define GL_ENTRY(_r, _api, ...) #_api,
const char* const kNames[] = {
#include "entries.in"
};
#undef GL_ENTRY
struct Hooks {
    void* gl[sizeof(kNames) / sizeof(kNames[0])];
    void* ext[256];  // MAX_NUMBER_OF_GL_EXTENSIONS
};

long noop() { return 0; }  // what an entry point the implementation lacks does

struct Translator {  // libapklayer-gles1.so's C interface
    void* (*create)(void*);
    void (*makeCurrent)(void*);
    void (*destroy)(void*);
    void* (*proc)(const char*);
};
const Translator* translator() {
    static Translator t = [] {
        Translator r{};
        // a glibc library in the native dir: not on the bionic search path, so bionic_translation's dlopen leaves it to glibc (LD_LIBRARY_PATH)
        void* lib = dlopen("libapklayer-gles1.so", RTLD_NOW);
        if (!lib) { ALOGW("no libapklayer-gles1.so (tools/build gles1): no OpenGL ES 1 on this host"); return r; }
        r.create = reinterpret_cast<void* (*)(void*)>(dlsym(lib, "apklayer_gles1_create"));
        r.makeCurrent = reinterpret_cast<void (*)(void*)>(dlsym(lib, "apklayer_gles1_make_current"));
        r.destroy = reinterpret_cast<void (*)(void*)>(dlsym(lib, "apklayer_gles1_destroy"));
        r.proc = reinterpret_cast<void* (*)(const char*)>(dlsym(lib, "apklayer_gles1_proc"));
        if (!r.create || !r.makeCurrent || !r.destroy || !r.proc) r = Translator{};
        return r;
    }();
    return t.create ? &t : nullptr;
}

void fill(Hooks* h, void* (*find)(const char*)) {
    for (size_t i = 0; i < sizeof(kNames) / sizeof(kNames[0]); i++) {
        void* f = find(kNames[i]);
        h->gl[i] = f ? f : reinterpret_cast<void*>(noop);
    }
}

// the host's glvnd libraries (not our eglGetProcAddress: it hands out Android's trampolines for ES 1)
void* hostProc(void* lib, const char* name) {
    static auto proc = reinterpret_cast<void* (*)(const char*)>(dlsym(dlopen("libEGL.so.1", RTLD_NOW), "eglGetProcAddress"));
    void* f = lib ? dlsym(lib, name) : nullptr;
    return f ? f : proc ? proc(name) : nullptr;
}
void* hostGles1(const char* name) {
    static void* lib = dlopen("libGLESv1_CM.so.1", RTLD_NOW);
    return hostProc(lib, name);
}
void* hostGles2(const char* name) {
    static void* lib = dlopen("libGLESv2.so.2", RTLD_NOW);
    return hostProc(lib, name);
}
void* none(const char*) { return nullptr; }

struct Gles1 {
    void* translated;  // libapklayer-gles1's context, null for a host ES 1 context
};
std::mutex lock;
std::map<EGLContext, Gles1> contexts;
Hooks hostHooks, translatorHooks, gles2Hooks, noContextHooks;
bool hostFilled, translatorFilled, gles2Filled;
thread_local bool tTranslated;

void setThreadHooks(const Hooks* h) {
#if defined(__aarch64__)
    reinterpret_cast<const void**>(__builtin_thread_pointer())[4] = h;  // TLS_SLOT_OPENGL_API: bridge/native/bionic_tls.c reserves it per thread
#else
    (void)h;  // no ES 1 hooks on x86_64: its bionic slots are glibc's TCB words (bridge/native/bionic_tls.c), none is reserved for them
#endif
}

__attribute__((constructor)) void initHooks() {  // Android's early_egl_init: the loading thread starts with the no-context table
    fill(&noContextHooks, none);
    setThreadHooks(&noContextHooks);
}

}  // namespace

bool wantsGles1(const std::vector<EGLint>& attribs) {
    for (size_t i = 0; i + 1 < attribs.size(); i += 2)
        if (attribs[i] == EGL_CONTEXT_CLIENT_VERSION) return attribs[i + 1] == 1;  // (= EGL_CONTEXT_MAJOR_VERSION)
    return true;
}

EGLContext gles1CreateContext(EGLDisplay dpy, EGLConfig cfg, EGLContext share, const std::vector<EGLint>& attribs, HostCreateContext create) {
    EGLContext ctx = create(dpy, cfg, share, attribs.data());
    if (ctx != EGL_NO_CONTEXT) {  // the host driver has ES 1
        std::lock_guard<std::mutex> l(lock);
        contexts[ctx] = Gles1{nullptr};
        return ctx;
    }
    const Translator* t = translator();
    if (!t) return EGL_NO_CONTEXT;  // the host's error stays
    std::vector<EGLint> three;
    for (size_t i = 0; i + 1 < attribs.size(); i += 2)
        if (attribs[i] != EGL_CONTEXT_CLIENT_VERSION && attribs[i] != EGL_CONTEXT_MINOR_VERSION) { three.push_back(attribs[i]); three.push_back(attribs[i + 1]); }
    three.insert(three.end(), {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE});
    std::lock_guard<std::mutex> l(lock);
    auto shared = contexts.find(share);
    if (share != EGL_NO_CONTEXT && (shared == contexts.end() || !shared->second.translated)) return EGL_NO_CONTEXT;  // ES 1 shares with ES 1 only
    ctx = create(dpy, cfg, share, three.data());
    if (ctx == EGL_NO_CONTEXT) return ctx;
    contexts[ctx] = Gles1{t->create(share != EGL_NO_CONTEXT ? shared->second.translated : nullptr)};
    return ctx;
}

void gles1MadeCurrent(EGLContext ctx) {
    std::lock_guard<std::mutex> l(lock);
    auto it = contexts.find(ctx);
    const Translator* t = (tTranslated || (it != contexts.end() && it->second.translated)) ? translator() : nullptr;
    if (it == contexts.end()) {
        if (t) t->makeCurrent(nullptr);
        tTranslated = false;
        if (ctx != EGL_NO_CONTEXT && !gles2Filled) { fill(&gles2Hooks, hostGles2); gles2Filled = true; }
        setThreadHooks(ctx != EGL_NO_CONTEXT ? &gles2Hooks : &noContextHooks);
        return;
    }
    if (!it->second.translated) {
        if (t) t->makeCurrent(nullptr);
        tTranslated = false;
        if (!hostFilled) { fill(&hostHooks, hostGles1); hostFilled = true; }
        setThreadHooks(&hostHooks);
        return;
    }
    t->makeCurrent(it->second.translated);
    tTranslated = true;
    if (!translatorFilled) { fill(&translatorHooks, t->proc); translatorFilled = true; }  // extension entry points need the current context
    setThreadHooks(&translatorHooks);
}

void gles1DestroyContext(EGLDisplay dpy, EGLContext ctx) {
    void* translated = nullptr;
    {
        std::lock_guard<std::mutex> l(lock);
        auto it = contexts.find(ctx);
        if (it == contexts.end()) return;
        translated = it->second.translated;
        if (!translated) { contexts.erase(it); return; }
    }
    // the translator deletes its GL objects: with the context current (surfaceless), then the caller's current one back.
    // Destroying the thread's current context keeps the bindings (EGL frees it on release); its ES 1 calls are no-ops from here.
    EGLDisplay curDpy = eglGetCurrentDisplay();
    EGLContext cur = eglGetCurrentContext();
    EGLSurface draw = eglGetCurrentSurface(EGL_DRAW), read = eglGetCurrentSurface(EGL_READ);
    if (cur != ctx) eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx);  // fails if another thread holds ctx: its objects then leak with it
    translator()->destroy(translated);
    {
        std::lock_guard<std::mutex> l(lock);
        contexts.erase(ctx);
    }
    if (cur == ctx) return;
    if (cur != EGL_NO_CONTEXT) eglMakeCurrent(curDpy, draw, read, cur);
    else eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
}

bool gles1QueryContext(EGLContext ctx, EGLint attribute, EGLint* value) {
    if (attribute != EGL_CONTEXT_CLIENT_VERSION || !value) return false;
    std::lock_guard<std::mutex> l(lock);
    if (!contexts.count(ctx)) return false;
    *value = 1;
    return true;
}

void* gles1ProcAddress(const char* name) {
    {
        std::lock_guard<std::mutex> l(lock);
        if (!contexts.count(eglGetCurrentContext())) return nullptr;
    }
    static void* lib = dlopen("libGLESv1_CM.so", RTLD_NOW);  // Android's (mapped to itself)
    return lib ? dlsym(lib, name) : nullptr;
}

}  // namespace apklayer::egl
