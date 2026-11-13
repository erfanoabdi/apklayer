/* App-facing process files: coherent runtime mappings and Android app IDs.
 * shortcut: ART mapping names do not provide ART symbols or object layouts; extend alongside the runtime compatibility work. */
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
long apklayer_app_uid(void);
int apklayer_proc_is_dir(int fd);
int apklayer_proc_pid_hidden(const char *name);

/* Resolve the opened file, so relative openat, symlinks and per-thread spellings share one check. */
static int own_proc_file(int fd)
{
    char link[64], path[PATH_MAX], task[80];
    snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
    ssize_t n = readlink(link, path, sizeof path - 1);
    if (n < 0) return 0;
    path[n] = 0;
    unsigned long pid, tid;
    char leaf[16];
    int end = 0;
    if (sscanf(path, "/proc/%lu/%15s%n", &pid, leaf, &end) == 2 && end && !path[end] && !strchr(leaf, '/')) {
        snprintf(task, sizeof task, "/proc/self/task/%lu", pid);
        return access(task, F_OK) == 0 ? (!strcmp(leaf, "maps") ? 1 : !strcmp(leaf, "status") ? 2 : 0) : 0;
    }
    end = 0;
    return sscanf(path, "/proc/%lu/task/%lu/%15s%n", &pid, &tid, leaf, &end) == 3 && end && !path[end] && pid == (unsigned long)getpid()
        ? (!strcmp(leaf, "maps") ? 1 : !strcmp(leaf, "status") ? 2 : 0) : 0;
}

/* fd remains owned by the caller, including on failure. */
static int proc_filter(int fd)
{
    const char *view = getenv("APKLAYER_PROC_VIEW");
    if (fd < 0 || !apklayer_own_data_env() || (view && !strcmp(view, "host"))) return fd;
    int flags = fcntl(fd, F_GETFL), cloexec = fcntl(fd, F_GETFD);
    if (flags < 0 || (flags & (O_PATH | O_ACCMODE)) != O_RDONLY) return fd;
    int kind = own_proc_file(fd);
    if (!kind) return fd;
    long app_uid = apklayer_app_uid();
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
    while (kind == 1 && getline(&line, &cap, in) >= 0) {
        char *path = strchr(line, '/'), *jvm = path ? strstr(path, "/lib/server/libjvm.so") : NULL;
        if (jvm) { size_t len = jvm - path; if (len < sizeof jdk) { memcpy(jdk, path, len); jdk[len] = 0; } break; }
    }
    if (ferror(in) || fseek(in, 0, SEEK_SET)) goto buffered;
    while (getline(&line, &cap, in) >= 0) {
        if (kind == 2) {
            unsigned ids[4];
            int uid = !strncmp(line, "Uid:", 4), gid = !strncmp(line, "Gid:", 4);
            if (app_uid >= 0 && (uid || gid) && sscanf(line + 4, "%u %u %u %u", &ids[0], &ids[1], &ids[2], &ids[3]) == 4) {
                unsigned real = uid ? getuid() : getgid();
                for (int i = 0; i < 4; i++) if (ids[i] == real) ids[i] = (unsigned)app_uid;
                fprintf(buffer, "%.4s\t%u\t%u\t%u\t%u\n", line, ids[0], ids[1], ids[2], ids[3]);
            } else fputs(line, buffer);
            continue;
        }
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

int apklayer_proc_fd(int fd)
{
    if (proc_filter(fd) >= 0) return fd;
    int saved = errno; if (fd >= 0) close(fd); errno = saved; return -1;
}
FILE *apklayer_proc_file(FILE *file)
{
    if (!file || proc_filter(fileno(file)) >= 0) return file;
    int saved = errno; fclose(file); errno = saved; return NULL;
}

/* Same visibility predicate as readdir and the translator: a raw libc syscall must not expose extra PIDs. */
long apklayer_proc_getdents64(int fd, void *buf, size_t count)
{
    long (*raw)(long, ...) = dlsym(RTLD_NEXT, "syscall");
    int filter = apklayer_own_data_env() && apklayer_proc_is_dir(fd);
    for (;;) {
        long n = raw(SYS_getdents64, fd, buf, count);
        if (n <= 0 || !filter) return n;
        size_t used = 0;
        for (size_t at = 0; at < (size_t)n;) {
            /* linux_dirent64: ino64, off64, reclen16, type8, name[]; memcpy avoids unaligned reads. */
            unsigned short len;
            if ((size_t)n - at < 20) { errno = EIO; return -1; }
            char *entry = (char *)buf + at;
            memcpy(&len, entry + 16, sizeof len);
            if (len < 20 || len > (size_t)n - at || !memchr(entry + 19, 0, len - 19)) { errno = EIO; return -1; }
            if (!apklayer_proc_pid_hidden(entry + 19)) {
                memmove((char *)buf + used, entry, len);
                used += len;
            }
            at += len;
        }
        if (used) return (long)used;
    }
}
