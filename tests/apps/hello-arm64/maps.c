/* Real NDK callers, native on ARM64 and translated on x86_64. */
#include <jni.h>
#include <fcntl.h>
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
    return bits;
}
