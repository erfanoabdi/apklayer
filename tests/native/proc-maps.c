#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

static void *a, *b;

static void check(FILE *f, int filtered)
{
    assert(f);
    char *line = NULL; size_t n = 0; int art = 0, jvm = 0, other = 0, ranges = 0;
    while (getline(&line, &n, f) >= 0) {
        unsigned long start, end, offset; char perms[5];
        assert(sscanf(line, "%lx-%lx %4s %lx", &start, &end, perms, &offset) == 4 && start < end);
        if (start == (unsigned long)a || start == (unsigned long)b) {
            assert(end == start + 4096 && !strcmp(perms, "r--p") && offset == 0); ranges++;
        }
        art += strstr(line, sizeof(void *) == 8 ? "/apex/com.android.art/lib64/libart.so" : "/apex/com.android.art/lib/libart.so") != NULL;
        jvm += strstr(line, "/lib/server/libjvm.so") != NULL;
        other += strstr(line, "/lib/libjava.so") != NULL;
    }
    assert(!ferror(f) && ranges == 2); free(line);
    assert(filtered ? art && !jvm && !other : !art && jvm && other);
    rewind(f); assert(fgetc(f) != EOF);
    errno = 0; assert(write(fileno(f), "x", 1) == -1 && errno == EBADF);
    fclose(f);
}
static void *mapping(const char *path)
{
    int fd = open(path, O_RDWR | O_CREAT | O_EXCL, 0600); assert(fd >= 0);
    assert(!ftruncate(fd, 4096));
    void *p = mmap(NULL, 4096, PROT_READ, MAP_PRIVATE, fd, 0); assert(p != MAP_FAILED); close(fd); return p;
}
int main(void)
{
    char root[] = "/tmp/apklayer-maps-XXXXXX", path[512], jvm[512], java[512]; assert(mkdtemp(root));
    snprintf(path, sizeof path, "%s/lib", root); assert(!mkdir(path, 0700));
    snprintf(path, sizeof path, "%s/lib/server", root); assert(!mkdir(path, 0700));
    snprintf(jvm, sizeof jvm, "%s/lib/server/libjvm.so", root);
    snprintf(java, sizeof java, "%s/lib/libjava.so", root);
    a = mapping(jvm); b = mapping(java);
    int filtered = getenv("APKLAYER_OWN_DATA") && !getenv("APKLAYER_PROC_VIEW");
    const char *paths[] = {"/proc/self/maps", "/proc/thread-self/maps", path};
    snprintf(path, sizeof path, "/proc/%d/task/%ld/maps", getpid(), syscall(SYS_gettid));
    for (int i = 0; i < 3; i++) {
        check(fopen(paths[i], "re"), filtered);
        int fd = open(paths[i], O_RDONLY | O_CLOEXEC); assert(fd >= 0 && fcntl(fd, F_GETFD) == FD_CLOEXEC);
        check(fdopen(fd, "r"), filtered);
        fd = syscall(SYS_openat, AT_FDCWD, paths[i], O_RDONLY, 0); assert(fd >= 0 && fcntl(fd, F_GETFD) == 0);
        check(fdopen(fd, "r"), filtered);
    }
    int dir = open("/proc/self", O_DIRECTORY | O_RDONLY); assert(dir >= 0);
    check(fdopen(openat(dir, "maps", O_RDONLY), "r"), filtered); close(dir);
    FILE *reopened = fopen("/dev/null", "r"); assert(reopened);
    check(freopen("/proc/self/maps", "r", reopened), filtered);
    assert(!unlink(jvm)); /* deleted JDK mappings keep the same identity */
    check(fopen("/proc/self/maps", "r"), filtered);
    assert(!munmap(a, 4096) && !munmap(b, 4096)); assert(!unlink(java));
    snprintf(path, sizeof path, "%s/lib/server", root); assert(!rmdir(path));
    snprintf(path, sizeof path, "%s/lib", root); assert(!rmdir(path)); assert(!rmdir(root));
    puts("PASS maps: stdio, open, openat, syscall, thread aliases, seek, read-only and CLOEXEC");
}
