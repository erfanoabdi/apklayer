#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <dirent.h>
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
        if (sscanf(line, "%lx-%lx %4s %lx", &start, &end, perms, &offset) != 4) continue;
        assert(start < end);
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
static void check_map_files(const char *directory, void *other, int filtered)
{
    char names[3][64], path[1024], target[1024];
    void *mappings[] = {a, b, other};
    DIR *dir = opendir(directory); assert(dir);
    for (int i = 0; i < 3; i++) {
        snprintf(names[i], sizeof names[i], "%lx-%lx", (unsigned long)mappings[i], (unsigned long)mappings[i] + 4096);
        snprintf(path, sizeof path, "%s/%s", directory, names[i]);
        int hidden = filtered && i < 2;
        errno = 0;
        ssize_t n = readlink(path, target, sizeof target);
        assert(hidden ? n == -1 && errno == ENOENT : n > 0);
        errno = 0;
        n = readlinkat(dirfd(dir), names[i], target, sizeof target);
        assert(hidden ? n == -1 && errno == ENOENT : n > 0);
        errno = 0;
        n = syscall(SYS_readlinkat, dirfd(dir), names[i], target, sizeof target);
        assert(hidden ? n == -1 && errno == ENOENT : n > 0);
        struct stat st;
        errno = 0;
        int r = lstat(path, &st);
        assert(hidden ? r == -1 && errno == ENOENT : r == 0 && S_ISLNK(st.st_mode));
    }
    int found[3] = {0}; struct dirent *entry;
    while ((entry = readdir(dir)))
        for (int i = 0; i < 3; i++) found[i] += !strcmp(entry->d_name, names[i]);
    for (int i = 0; i < 3; i++) assert(found[i] == !(filtered && i < 2));
    closedir(dir);
    int fd = open(directory, O_RDONLY | O_DIRECTORY); assert(fd >= 0);
    memset(found, 0, sizeof found);
    char entries[4096]; long n;
    while ((n = syscall(SYS_getdents64, fd, entries, sizeof entries)) > 0) {
        for (size_t at = 0; at < (size_t)n;) {
            unsigned short len; memcpy(&len, entries + at + 16, 2); assert(len >= 20 && at + len <= (size_t)n);
            for (int i = 0; i < 3; i++) found[i] += !strcmp(entries + at + 19, names[i]);
            at += len;
        }
    }
    assert(n == 0); close(fd);
    for (int i = 0; i < 3; i++) assert(found[i] == !(filtered && i < 2));
}

static void strip_numa_file(char *line)
{
    char *file = strstr(line, " file=");
    if (file) memmove(file, file + 6 + strcspn(file + 6, " \t\n"), strlen(file + 6 + strcspn(file + 6, " \t\n")) + 1);
}
static void numa_rows(FILE *f, void *other, char rows[3][2048])
{
    void *mappings[] = {a, b, other};
    char *line = NULL; size_t cap = 0; int found = 0;
    assert(f);
    while (getline(&line, &cap, f) >= 0) {
        unsigned long start;
        if (sscanf(line, "%lx", &start) != 1) continue;
        for (int i = 0; i < 3; i++) if (start == (unsigned long)mappings[i]) {
            assert(strlen(line) < 2048); strcpy(rows[i], line); found++;
        }
    }
    assert(!ferror(f) && found == 3); free(line); fclose(f);
}
static void check_numa(void *other, int filtered)
{
    char raw[3][2048], actual[3][2048];
    const char *original = getenv("APKLAYER_PROC_VIEW");
    char *saved = original ? strdup(original) : NULL;
    assert(!setenv("APKLAYER_PROC_VIEW", "host", 1));
    FILE *host = fopen("/proc/self/numa_maps", "r");
    int error = errno;
    if (saved) { assert(!setenv("APKLAYER_PROC_VIEW", saved, 1)); free(saved); }
    else assert(!unsetenv("APKLAYER_PROC_VIEW"));
    if (!host) { assert(error == ENOENT); return; } /* kernels without CONFIG_NUMA have no file */
    numa_rows(host, other, raw);
    for (int i = 0; i < 3; i++) assert(strstr(raw[i], " N") && strstr(raw[i], "kernelpagesize_kB="));
    char numeric[128]; snprintf(numeric, sizeof numeric, "/proc/%d/task/%ld/numa_maps", getpid(), syscall(SYS_gettid));
    const char *paths[] = {"/proc/self/numa_maps", "/proc/thread-self/numa_maps", numeric};
    for (int alias = 0; alias < 3; alias++) {
        for (int api = 0; api < 3; api++) {
            FILE *f = api == 0 ? fopen(paths[alias], "r") : fdopen(api == 1 ? open(paths[alias], O_RDONLY) : syscall(SYS_openat, AT_FDCWD, paths[alias], O_RDONLY, 0), "r");
            numa_rows(f, other, actual);
            for (int i = 0; i < 3; i++) {
                if (filtered && i == 0) assert(strstr(actual[i], "/libart.so") && !strstr(actual[i], "/libjvm.so"));
                else if (filtered && i == 1) assert(!strstr(actual[i], " file="));
                else assert(!strcmp(raw[i], actual[i]));
                char expected[2048]; strcpy(expected, raw[i]); strip_numa_file(expected); strip_numa_file(actual[i]);
                assert(!strcmp(expected, actual[i])); /* address, policy and all page/node counters preserved */
            }
        }
    }
    int dir = open("/proc/self", O_RDONLY | O_DIRECTORY); assert(dir >= 0);
    numa_rows(fdopen(openat(dir, "numa_maps", O_RDONLY), "r"), other, actual); close(dir);
    assert(!filtered || (strstr(actual[0], "/libart.so") && !strstr(actual[1], " file=")));
}

int main(void)
{
    char root[] = "/tmp/apklayer maps-XXXXXX", path[512], jvm[512], java[512]; assert(mkdtemp(root));
    snprintf(path, sizeof path, "%s/lib", root); assert(!mkdir(path, 0700));
    snprintf(path, sizeof path, "%s/lib/server", root); assert(!mkdir(path, 0700));
    snprintf(jvm, sizeof jvm, "%s/lib/server/libjvm.so", root);
    snprintf(java, sizeof java, "%s/lib/libjava.so", root);
    a = mapping(jvm); b = mapping(java);
    char guestroot[] = "/tmp/apklayer-guest-XXXXXX", guest[512]; assert(mkdtemp(guestroot));
    snprintf(guest, sizeof guest, "%s/guest.so", guestroot);
    void *other = mapping(guest);
    assert(*(volatile unsigned char *)a == 0 && *(volatile unsigned char *)b == 0 && *(volatile unsigned char *)other == 0);
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
    check(fopen("/proc/self/smaps", "re"), filtered);
    check(fopen("/proc/thread-self/smaps", "re"), filtered);
    int dir = open("/proc/self", O_DIRECTORY | O_RDONLY); assert(dir >= 0);
    check(fdopen(openat(dir, "maps", O_RDONLY), "r"), filtered); close(dir);
    FILE *reopened = fopen("/dev/null", "r"); assert(reopened);
    check(freopen("/proc/self/maps", "r", reopened), filtered);
    check_numa(other, filtered);
    check_map_files("/proc/self/map_files", other, filtered);
    /* map_files is process-only: Linux task directories expose maps/smaps but no map_files. */
    snprintf(path, sizeof path, "/proc/%d/map_files", getpid());
    check_map_files(path, other, filtered);
    snprintf(path, sizeof path, "%s/map-alias", root);
    assert(!symlink("/proc/self/map_files", path));
    check_map_files(path, other, filtered);
    assert(!unlink(path));
    assert(!unlink(jvm)); /* deleted JDK mappings keep the same identity */
    check(fopen("/proc/self/maps", "r"), filtered);
    check_numa(other, filtered);
    check_map_files("/proc/self/map_files", other, filtered);
    assert(!munmap(other, 4096) && !unlink(guest) && !rmdir(guestroot));
    assert(!munmap(a, 4096) && !munmap(b, 4096)); assert(!unlink(java));
    snprintf(path, sizeof path, "%s/lib/server", root); assert(!rmdir(path));
    snprintf(path, sizeof path, "%s/lib", root); assert(!rmdir(path)); assert(!rmdir(root));
    puts("PASS maps: stdio, open, openat, syscall, thread aliases, seek, read-only and CLOEXEC");
}
