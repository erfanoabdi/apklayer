/* App-facing maps: retain real ranges, expose the runtime as ART, and anonymize the rest of the JDK.
 * shortcut: this is maps identity only; ART symbols, object layouts and map_files are not emulated. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

const char *apklayer_own_data_env(void);

/* Resolve the opened file, so relative openat, symlinks and per-thread spellings share one check. */
static int own_maps(int fd)
{
    char link[64], path[PATH_MAX], task[80];
    snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
    ssize_t n = readlink(link, path, sizeof path - 1);
    if (n < 0) return 0;
    path[n] = 0;
    unsigned long pid, tid;
    int end = 0;
    if (sscanf(path, "/proc/%lu/maps%n", &pid, &end) == 1 && end && !path[end]) {
        snprintf(task, sizeof task, "/proc/self/task/%lu", pid);
        return access(task, F_OK) == 0;
    }
    end = 0;
    return sscanf(path, "/proc/%lu/task/%lu/maps%n", &pid, &tid, &end) == 2 && end && !path[end] && pid == (unsigned long)getpid();
}

/* fd remains owned by the caller, including on failure. */
int apklayer_maps_filter(int fd)
{
    const char *view = getenv("APKLAYER_PROC_VIEW");
    if (fd < 0 || !apklayer_own_data_env() || (view && !strcmp(view, "host"))) return fd;
    int flags = fcntl(fd, F_GETFL), cloexec = fcntl(fd, F_GETFD);
    if (flags < 0 || (flags & (O_PATH | O_ACCMODE)) != O_RDONLY || !own_maps(fd)) return fd;
    /* Use the real syscall entry: the preload's syscall wrapper calls us too. */
    long (*raw)(long, ...) = dlsym(RTLD_NEXT, "syscall");
    int copy = fcntl(fd, F_DUPFD_CLOEXEC, 0), out = -1, result = -1;
    FILE *in = copy < 0 ? NULL : fdopen(copy, "r");
    if (!in) { if (copy >= 0) close(copy); return -1; }
    char *line = NULL, *text = NULL;
    size_t cap = 0, size = 0;
    FILE *buffer = open_memstream(&text, &size);
    if (!buffer) goto done;
    char jdk[PATH_MAX] = "";
    /* Discover the JDK root from libjvm, including bundled JDKs outside /usr. */
    while (getline(&line, &cap, in) >= 0) {
        char *path = strchr(line, '/'), *jvm = path ? strstr(path, "/lib/server/libjvm.so") : NULL;
        if (jvm) { size_t len = jvm - path; if (len < sizeof jdk) { memcpy(jdk, path, len); jdk[len] = 0; } break; }
    }
    if (ferror(in) || fseek(in, 0, SEEK_SET)) goto buffered;
    while (getline(&line, &cap, in) >= 0) {
        unsigned long start, end, offset, inode;
        unsigned major, minor;
        char perms[5]; int name = 0;
        if (sscanf(line, "%lx-%lx %4s %lx %x:%x %lu %n", &start, &end, perms, &offset, &major, &minor, &inode, &name) == 7 && name) {
            char *path = line + name;
            size_t len = strlen(jdk);
            if (len && !strncmp(path, jdk, len) && path[len] == '/') {
                if (strstr(path + len, "/lib/server/libjvm.so"))
                    fprintf(buffer, "%lx-%lx %s %08lx %02x:%02x %lu /apex/com.android.art/%s/libart.so\n", start, end, perms, offset, major, minor, inode, sizeof(void *) == 8 ? "lib64" : "lib");
                else fprintf(buffer, "%lx-%lx %s 00000000 00:00 0\n", start, end, perms);
                continue;
            }
        }
        fputs(line, buffer);
    }
    if (ferror(in) || ferror(buffer)) goto buffered;
    if (fclose(buffer)) { buffer = NULL; goto done; }
    buffer = NULL;
    out = raw(SYS_memfd_create, "maps", 1 /* MFD_CLOEXEC */);
    if (out < 0) goto done;
    for (size_t at = 0; at < size;) {
        ssize_t n = write(out, text + at, size - at);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) goto done;
        at += n;
    }
    char path[64]; snprintf(path, sizeof path, "/proc/self/fd/%d", out);
    int ro = raw(SYS_openat, AT_FDCWD, path, O_RDONLY | O_CLOEXEC, 0);
    if (ro < 0) goto done;
    if (dup3(ro, fd, (cloexec & FD_CLOEXEC) ? O_CLOEXEC : 0) >= 0) result = fd;
    close(ro);
    goto done;
buffered:
    fclose(buffer);
done:
    { int saved = errno; free(line); free(text); fclose(in); if (out >= 0) close(out); errno = saved; }
    return result;
}

int apklayer_maps_fd(int fd)
{
    if (apklayer_maps_filter(fd) >= 0) return fd;
    int saved = errno; if (fd >= 0) close(fd); errno = saved; return -1;
}
FILE *apklayer_maps_file(FILE *file)
{
    if (!file || apklayer_maps_filter(fileno(file)) >= 0) return file;
    int saved = errno; fclose(file); errno = saved; return NULL;
}
