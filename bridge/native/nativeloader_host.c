/* libnativeloader and libnativebridge for Android's own libraries (bionic_translation.cfg maps both names here): what
 * libnativeloader_lazy.so and libnativebridge_lazy.so look up when framework code opens an app library itself. The caller that matters is
 * android.app.NativeActivity (android_app_NativeActivity.cpp loadNativeCode: OpenNativeLibrary, then NativeBridgeGetTrampoline for
 * ANativeActivity_onCreate when the library needs the bridge): every 3DMark benchmark is one, with arm64 libraries on an x86_64 host.
 * App libraries are opened the way apklayer.host.NativeLoader opens them: through the native bridge in the app's guest namespace when
 * their ELF is another CPU's, otherwise through bionic_translation's linker. The bridge itself stays the JNI shim's (native_bridge.c),
 * which binds its functions here once it is up (apklayer_nativeloader_bind). Signatures: art/libnativeloader/include/nativeloader/native_loader.h,
 * art/libnativebridge/include/nativebridge/native_bridge.h. */
#define _GNU_SOURCE
#include <elf.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

extern void *bionic_dlopen(const char *filename, int flag);
extern char *bionic_dlerror(void);
#define RTLD_NOW_BIONIC 2

struct apklayer_bridge_ops {
    void *(*load)(const char *path);                                                          /* the app's guest namespace */
    void *(*trampoline)(void *handle, const char *name, const char *shorty, uint32_t len);
    const char *(*error)(void);
};
static struct apklayer_bridge_ops bridge;

__attribute__((visibility("default"))) void apklayer_nativeloader_bind(const struct apklayer_bridge_ops *ops) { if (ops) bridge = *ops; }

/* Whether the library is another CPU's (the bridge's): its ELF machine is not this build's. */
static bool foreign(const char *path)
{
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    Elf64_Ehdr h;
    bool other = read(fd, &h, sizeof h) == (ssize_t)sizeof h && !memcmp(h.e_ident, ELFMAG, SELFMAG)
#if defined(__x86_64__)
                 && h.e_machine != EM_X86_64;
#else
                 && h.e_machine != EM_AARCH64;
#endif
    close(fd);
    return other;
}

__attribute__((visibility("default")))
void *OpenNativeLibrary(void *env, int32_t target_sdk_version, const char *path, void *class_loader, const char *caller_location,
                        void *library_path, bool *needs_native_bridge, char **error_msg)  /* JNIEnv*, jobject, jstring */
{
    (void)env; (void)target_sdk_version; (void)class_loader; (void)caller_location; (void)library_path;
    bool guest = bridge.load && foreign(path);
    void *h = guest ? bridge.load(path) : bionic_dlopen(path, RTLD_NOW_BIONIC);
    if (needs_native_bridge) *needs_native_bridge = guest;
    if (!h && error_msg) {
        const char *e = guest ? (bridge.error ? bridge.error() : NULL) : bionic_dlerror();
        *error_msg = strdup(e ? e : "cannot open the library");
    }
    return h;
}

/* Libraries stay loaded (apklayer.host.NativeLoader never unloads either): ART's unloading only follows a class loader's collection. */
__attribute__((visibility("default"))) bool CloseNativeLibrary(void *handle, const bool needs_native_bridge, char **error_msg)
{ (void)handle; (void)needs_native_bridge; (void)error_msg; return true; }
__attribute__((visibility("default"))) void NativeLoaderFreeErrorMessage(char *msg) { free(msg); }
__attribute__((visibility("default"))) void *FindNativeLoaderNamespaceByClassLoader(void *env, void *class_loader) { (void)env; (void)class_loader; return NULL; }

__attribute__((visibility("default"))) void *NativeBridgeGetTrampoline(void *handle, const char *name, const char *shorty, uint32_t len)
{ return bridge.trampoline ? bridge.trampoline(handle, name, shorty, len) : NULL; }
__attribute__((visibility("default"))) const char *NativeBridgeGetError(void) { return bridge.error ? bridge.error() : "no native bridge"; }
__attribute__((visibility("default"))) bool NativeBridgeAvailable(void) { return bridge.load != NULL; }
__attribute__((visibility("default"))) bool NativeBridgeInitialized(void) { return bridge.load != NULL; }
