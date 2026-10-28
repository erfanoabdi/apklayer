/* bionic's thread-pointer slots (PLAN P7.1). Android code reads and writes fixed offsets from the thread pointer (tp+2 .. tp+7: app,
 * OpenGL, OpenGL API, stack guard, sanitizer, ART thread self); on glibc that memory belongs to its own thread structure, so without
 * reserved slots bionic code corrupts it, first visible in threads that make GL calls. bionic_translation's README solves it with a
 * thread-local array in the MAIN EXECUTABLE (main_executable/bionic_compat.c): static TLS of the executable sits right after the
 * thread control block. We cannot relink `java`, but this library is LD_PRELOADed and the executables have no TLS segment, so
 * the first static TLS module is this one and its array lands at the same place (tp+16 = slot 2). Checked at startup. */
#include <stdint.h>
#include <stdio.h>

_Thread_local uintptr_t apklayer_bionic_tls[] = {
    0,                  /* tp+2  TLS_SLOT_APP: free for apps; Go's runtime keeps its g here and takes any other value for a goroutine (a Go signal handler on a JVM thread then crashed on 0x5555...) */
    0x5555555555555555, /* tp+3  TLS_SLOT_OPENGL */
    0x5555555555555555, /* tp+4  TLS_SLOT_OPENGL_API */
    0x5555555555555555, /* tp+5  TLS_SLOT_STACK_GUARD (constant, as on ATL) */
    0x5555555555555555, /* tp+6  TLS_SLOT_SANITIZER */
    0x5555555555555555, /* tp+7  TLS_SLOT_ART_THREAD_SELF */
};

__attribute__((constructor)) static void check_bionic_tls(void)
{
    uintptr_t tp = (uintptr_t)__builtin_thread_pointer(), at = (uintptr_t)apklayer_bionic_tls;
    if (at != tp + 16)
        fprintf(stderr, "[bionic-tls] slots are at tp%+ld, bionic expects tp+16: another TLS module is loaded before this library "
                        "(put libapklayer-preload.so first in LD_PRELOAD)\n", (long)(at - tp));
}
