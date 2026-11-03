// libapklayer-gles1.so: OpenGL ES 1.1 for hosts whose driver has none (Ubuntu's and Debian's Mesa are built without it), on top of a host
// GLES 3 context. The ES 1 implementation is gfxstream's GLES_CM translator from the Android tree (hardware/google/gfxstream: what the
// Android emulator runs for guests that ask for ES 1; its CoreProfileEngine emulates the fixed-function pipeline with shaders). This file is
// the EGL side the translator expects (gfxstream's own egl_imp.cpp drives the same calls), reduced to what apklayer's libEGL needs: a context
// per EGL context, its share group, the thread's current context, the GL entry points. The caller makes the host context current first.
// Built on the machine that runs apklayer (glibc), loaded by apklayer's libEGL (Android side) through a C interface: tools/build gles1.
#include <EGL/egl.h>
#include <dlfcn.h>
#include <string.h>

#include <mutex>

#include "common/gl_library.h"
#include "common/gles_context.h"
#include "common/object_name_space.h"
#include "common/share_group.h"
#include "common/translator_ifaces.h"
#include "OpenGLESDispatch/GLESv1Dispatch.h"

extern "C" GLESiface* static_translator_glescm_getIfaces(const EGLiface*);

namespace {

struct Context {
    GLEScontext* gles;
    ShareGroupPtr group;
};

thread_local Context* tCurrent;

// the host's GLES (glvnd): core entry points by name, the rest through eglGetProcAddress
class HostGl : public GlLibrary {
    void* lib = dlopen("libGLESv2.so.2", RTLD_NOW | RTLD_GLOBAL);
    GlFunctionPointer findSymbol(const char* name) override {
        void* f = lib ? dlsym(lib, name) : nullptr;
        return reinterpret_cast<GlFunctionPointer>(f ? f : reinterpret_cast<void*>(eglGetProcAddress(name)));
    }
};

GLEScontext* currentGles() { return tCurrent ? tCurrent->gles : nullptr; }
ImagePtr noImage(unsigned int) { return ImagePtr(); }  // EGLImages into ES 1 textures: not wired (no ES 1 app seen using them)
GlLibrary* hostGl() { static HostGl lib; return &lib; }
bool noAux(EGLContext*, EGLSurface*) { return false; }  // auxiliary contexts serve gfxstream's snapshot loader only
bool noAuxDestroy(EGLContext, EGLSurface) { return false; }
bool noAuxBind(EGLContext, EGLSurface) { return false; }
bool noAuxUnbind() { return false; }

const EGLiface kEglIface = {
    .getGLESContext = currentGles,
    .getEGLImage = noImage,
    .eglGetGlLibrary = hostGl,
    .createAndBindAuxiliaryContext = noAux,
    .unbindAndDestroyAuxiliaryContext = noAuxDestroy,
    .bindAuxiliaryContext = noAuxBind,
    .unbindAuxiliaryContext = noAuxUnbind,
    .getProcAddress = [](const char* name) { return reinterpret_cast<void*>(eglGetProcAddress(name)); },
};

std::mutex lock;
GLESiface* iface;
GlobalNameSpace* names;
ObjectNameManager* groups;

GLESiface* cmIface() {  // lock held
    if (!iface) {
        iface = static_translator_glescm_getIfaces(&kEglIface);
        iface->initGLESx(true);  // the host is GLES, not desktop GL
        iface->setMaxGlesVersion(GLES_3_0);  // the fixed-function emulation needs GLES 3 entry points (vertex arrays) from the host
        names = new GlobalNameSpace();
        groups = new ObjectNameManager(names);
    }
    return iface;
}

}  // namespace

extern "C" {

// A new ES 1 context, sharing objects with `share` (another one of these, or null).
__attribute__((visibility("default"))) void* apklayer_gles1_create(void* share) {
    std::lock_guard<std::mutex> l(lock);
    auto* c = new Context{cmIface()->createGLESContext(1, 1, names, nullptr), {}};
    auto* other = static_cast<Context*>(share);
    c->group = other ? groups->attachShareGroup(c, other) : groups->createShareGroup(c, 0, nullptr, {});
    return c;
}

// Makes `ctx` (or nothing) the thread's ES 1 context; the host context behind it is current already.
__attribute__((visibility("default"))) void apklayer_gles1_make_current(void* ctx) {
    if (tCurrent && tCurrent != ctx) iface->flush();
    tCurrent = static_cast<Context*>(ctx);
    if (!tCurrent) return;
    std::lock_guard<std::mutex> l(lock);
    iface->initContext(tCurrent->gles, tCurrent->group, false, false);  // first time: GL limits and the shader engine, on the host context
    // the translator's limit getters (getMaxTexSize, getMaxLights...) read the GLES 2 caps, which only a GLES 2 translator context fills
    // (the emulator always has one); without it GL_MAX_TEXTURE_SIZE is 0 and every glTexImage2D fails: the ES 1 caps stand in
    GLSupport* global = GLEScontext::getCapsGlobal();
    if (!global->maxTexSize) *global = *tCurrent->gles->getCaps();
}

// Frees `ctx`; its host context must be current (the translator deletes its GL objects).
__attribute__((visibility("default"))) void apklayer_gles1_destroy(void* ctx) {
    auto* c = static_cast<Context*>(ctx);
    if (!c) return;
    std::lock_guard<std::mutex> l(lock);
    if (tCurrent == c) tCurrent = nullptr;
    iface->deleteGLESContext(c->gles);
    groups->deleteShareGroup(c);
    delete c;
}

// An ES 1.1 entry point (core or extension), null for names ES 1 does not have. Extensions need a current ES 1 context.
__attribute__((visibility("default"))) void* apklayer_gles1_proc(const char* name) {
#define APKLAYER_GLES1_ENTRY(ret, fn, sig, args) if (!strcmp(name, #fn)) return reinterpret_cast<void*>(::translator::gles1::fn);
    LIST_GLES1_FUNCTIONS(APKLAYER_GLES1_ENTRY, APKLAYER_GLES1_ENTRY)
#undef APKLAYER_GLES1_ENTRY
    return tCurrent ? reinterpret_cast<void*>(iface->getProcAddress(name)) : nullptr;
}

}  // extern "C"
