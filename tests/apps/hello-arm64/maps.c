/* Real NDK callers, native on ARM64 and translated on x86_64. */
#define _GNU_SOURCE
#include <jni.h>
#include <dlfcn.h>
#include <link.h>
#include <stdint.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

static int art(FILE *f)
{
    if (!f) return 0;
    char line[4096]; int found = 0, leaked = 0, lines = 0;
    while (fgets(line, sizeof line, f)) {
        lines++;
        found |= strstr(line, "/apex/com.android.art/lib64/libart.so") != NULL;
        leaked |= strstr(line, "libjvm.so") != NULL || strstr(line, "libjava.so") != NULL ||
                  strstr(line, "/lib/modules") != NULL || strstr(line, ".jsa") != NULL;
    }
    int ok = found && !leaked && lines > 10 && !ferror(f);
    fclose(f);
    return ok;
}
struct modules { uintptr_t self; int count, leaked, own; };
static int module(struct dl_phdr_info *info, size_t size, void *data)
{
    (void)size;
    struct modules *m = data;
    const char *name = info->dlpi_name ? info->dlpi_name : "";
    m->count++;
    m->leaked |= strstr(name, "libjvm.so") != NULL || strstr(name, "libjava.so") != NULL;
    for (int i = 0; i < info->dlpi_phnum; i++) {
        const ElfW(Phdr) *p = info->dlpi_phdr + i;
        uintptr_t start = info->dlpi_addr + p->p_vaddr;
        if (p->p_type == PT_LOAD && m->self >= start && m->self < start + p->p_memsz) m->own++;
    }
    return 0;
}
JNIEXPORT jint JNICALL Java_hello_arm64_Main_artMaps(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    const char *paths[] = {"/proc/self/maps", "/proc/thread-self/maps"};
    int bits = 0;
    for (int i = 0; i < 2; i++) {
        bits |= art(fopen(paths[i], "r")) << (3*i);
        int fd = open(paths[i], O_RDONLY | O_CLOEXEC);
        bits |= (fd >= 0 && art(fdopen(fd, "r"))) << (3*i + 1);
        fd = syscall(SYS_openat, AT_FDCWD, paths[i], O_RDONLY | O_CLOEXEC, 0);
        bits |= (fd >= 0 && art(fdopen(fd, "r"))) << (3*i + 2);
    }
    struct modules modules = {.self = (uintptr_t)&Java_hello_arm64_Main_artMaps};
    Dl_info own;
    int listed = dl_iterate_phdr(module, &modules);
    int addressed = dladdr((void *)modules.self, &own);
    if (!listed && modules.count && !modules.leaked && modules.own && addressed
            && own.dli_fname && strstr(own.dli_fname, "libhello.so")) bits |= 64;
    FILE *numa = fopen("/proc/self/numa_maps", "r");
    if (numa ? art(numa) : errno == ENOENT) bits |= 128;
    return bits;
}
