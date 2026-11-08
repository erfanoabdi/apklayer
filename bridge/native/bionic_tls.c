/* bionic's thread-pointer slots (PLAN P7.1). Android code reads and writes fixed offsets from the thread pointer (tp+2 .. tp+7: app,
 * OpenGL, OpenGL API, stack guard, sanitizer, ART thread self); on glibc that memory belongs to its own thread structure, so without
 * reserved slots bionic code corrupts it, first visible in threads that make GL calls. bionic_translation's README solves it with a
 * thread-local array in the MAIN EXECUTABLE (main_executable/bionic_compat.c): static TLS of the executable sits right after the
 * thread control block. We cannot relink `java`, but this library is LD_PRELOADed and the executables have no TLS segment, so
 * the first static TLS module is this one and its array lands at the same place (tp+16 = slot 2). Checked at startup. */
#include <stdint.h>
#include <stdio.h>

#if defined(__aarch64__) || defined(__arm__)  /* 32-bit ARM (the 32-bit app process): the same variant I layout, slots of 4 bytes from tp+8 */
#define POISON ((uintptr_t)0x5555555555555555ull)
_Thread_local uintptr_t apklayer_bionic_tls[] = {
    0,                  /* tp+2  TLS_SLOT_APP: free for apps; Go's runtime keeps its g here and takes any other value for a goroutine (a Go signal handler on a JVM thread then crashed on 0x5555...) */
    POISON,             /* tp+3  TLS_SLOT_OPENGL */
    0,                  /* tp+4  TLS_SLOT_OPENGL_API: Android's GLES 1 trampolines return on null (no context yet); our libEGL stores the hook table here */
    POISON,             /* tp+5  TLS_SLOT_STACK_GUARD (constant, as on ATL) */
    POISON,             /* tp+6  TLS_SLOT_SANITIZER */
    POISON,             /* tp+7  TLS_SLOT_ART_THREAD_SELF */
};

__attribute__((constructor)) static void check_bionic_tls(void)
{
    uintptr_t tp = (uintptr_t)__builtin_thread_pointer(), at = (uintptr_t)apklayer_bionic_tls;
    if (at != tp + 2 * sizeof(uintptr_t))
        fprintf(stderr, "[bionic-tls] slots are at tp%+ld, bionic expects slot 2: another TLS module is loaded before this library "
                        "(put libapklayer-preload.so first in LD_PRELOAD)\n", (long)(at - tp));
}
#else
/* x86_64: glibc's static TLS block is BELOW the thread pointer (variant II), so no module can land at tp+16; and the words bionic calls slots live in glibc's thread control block
 * (fs:0x10 is glibc's own thread pointer, fs:0x18 multiple_threads, fs:0x20 sysinfo, fs:0x30 the pointer guard, fs:0x28 the stack guard bionic and glibc both read). Android code that
 * only reads them (libc's stack protector) works; code that writes TLS_SLOT_APP / OPENGL / SANITIZER / ART_THREAD_SELF would corrupt glibc: none of the libraries this runtime
 * loads does (ART is not present). TLS_SLOT_OPENGL_API (fs:0x20) is glibc's `sysinfo`, which only i386 uses: our libEGL keeps the GLES hook table there for Android's
 * libGLESv1_CM trampolines (aosp/hals/graphics/egl/Gles1.cpp), as on aarch64. */
#endif
