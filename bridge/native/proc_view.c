/* App-facing process files: coherent runtime mappings and Android app IDs.
 * shortcut: ART mapping names do not provide ART symbols or object layouts; extend alongside the runtime compatibility work. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <elf.h>
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
int apklayer_app_groups(gid_t *out, size_t capacity);
int apklayer_proc_is_dir(int fd);
int apklayer_proc_pid_hidden(const char *name);
/* proc_task.c */
const char *apklayer_proc_thread_name(long tid, const char *real, char *out);
int apklayer_proc_cgroup(char *out, size_t size, long pid);
const char *apklayer_proc_status_line(const char *line);
int apklayer_proc_parent_is_host_tool(long pid);
int apklayer_proc_foreign_environment(long pid);
const char *apklayer_proc_map_name(const char *host, char *out, size_t size);

static int proc_file_kind(const char *leaf)
{
    if (!strcmp(leaf, "maps") || !strcmp(leaf, "smaps")) return 1;
    if (!strcmp(leaf, "status")) return 2;
    if (!strcmp(leaf, "map_files")) return 3;
    if (!strcmp(leaf, "numa_maps")) return 4;
    if (!strcmp(leaf, "environ")) return 5;
    if (!strcmp(leaf, "mounts") || !strcmp(leaf, "mountinfo") || !strcmp(leaf, "mountstats")) return 6;
    if (!strcmp(leaf, "comm")) return 7;
    if (!strcmp(leaf, "stat")) return 8;
    if (!strcmp(leaf, "cgroup")) return 9;
    if (!strcmp(leaf, "cmdline")) return 10;
    return 0;
}

/* Resolve the opened file, so relative openat, symlinks and per-thread spellings share one check. *tid: the thread the file describes
 * (/proc/<tid>/... too). A file of another process sets *foreign to its pid and returns its kind (0 for an own file). */
static const char *jdk_root(void);
static int proc_file_of(int fd, long *tid, long *foreign)
{
    char link[64], path[PATH_MAX], task[80];
    snprintf(link, sizeof link, "/proc/self/fd/%d", fd);
    ssize_t n = readlink(link, path, sizeof path - 1);
    *foreign = 0;
    if (n < 0) return 0;
    path[n] = 0;
    if (n > 9 && !strcmp(path + n - 9, "/bin/java")) {  /* the JDK's java: what /proc/self/exe opens (its link reads app_process64, proc_task.c) */
        const char *jdk = jdk_root();
        if (jdk[0] && strlen(jdk) == (size_t)n - 9 && !strncmp(path, jdk, n - 9)) return 11;
    }
    unsigned long pid, id;
    char leaf[16];
    int end = 0;
    if (sscanf(path, "/proc/%lu/%15s%n", &pid, leaf, &end) == 2 && end && !path[end] && !strchr(leaf, '/')) {
        snprintf(task, sizeof task, "/proc/self/task/%lu", pid);
        *tid = (long)pid;
        if (access(task, F_OK) == 0) return proc_file_kind(leaf);
        *foreign = (long)pid;
        return proc_file_kind(leaf);
    }
    end = 0;
    if (sscanf(path, "/proc/%lu/task/%lu/%15s%n", &pid, &id, leaf, &end) == 3 && end && !path[end]) {
        *tid = (long)id;
        if (pid == (unsigned long)getpid()) return proc_file_kind(leaf);
    }
    return 0;
}

static int own_proc_file(int fd)
{
    long tid, foreign;
    int kind = proc_file_of(fd, &tid, &foreign);
    return foreign ? 0 : kind;
}

static int app_proc_view(void)
{
    const char *view = getenv("APKLAYER_PROC_VIEW");
    return apklayer_own_data_env() && (!view || strcmp(view, "host"));
}

/* JVM libraries live for the process lifetime; cache only a discovered root, never a failed lookup. */
static const char *jdk_root(void)
{
    static __thread char root[PATH_MAX];
    if (root[0]) return root;
    long (*raw)(long, ...) = dlsym(RTLD_NEXT, "syscall");
    int fd = raw(SYS_openat, AT_FDCWD, "/proc/self/maps", O_RDONLY | O_CLOEXEC, 0);
    FILE *maps = fd < 0 ? NULL : fdopen(fd, "r");
    if (!maps) { if (fd >= 0) close(fd); return root; }
    char *line = NULL; size_t cap = 0;
    while (getline(&line, &cap, maps) >= 0) {
        char *path = strchr(line, '/'), *jvm = path ? strstr(path, "/lib/server/libjvm.so") : NULL;
        if (jvm && (size_t)(jvm - path) < sizeof root) {
            memcpy(root, path, jvm - path); root[jvm - path] = 0;
            break;
        }
    }
    free(line); fclose(maps);
    return root;
}

const char *apklayer_proc_jdk_root(void) { return jdk_root(); }

/* Only bionic's dladdr/phdr callbacks call this; host loader and unwinder APIs stay untouched. */
const char *apklayer_proc_module_name(const char *name)
{
    if (!name || !app_proc_view()) return name;
    int saved = errno;
    const char *root = jdk_root(); size_t len = strlen(root);
    const char *result = name;
    if (len && !strncmp(name, root, len) && name[len] == '/')
        result = !strcmp(name + len, "/lib/server/libjvm.so")
            ? (sizeof(void *) == 8 ? "/apex/com.android.art/lib64/libart.so" : "/apex/com.android.art/lib/libart.so") : "";
    errno = saved;
    return result;
}

int apklayer_proc_map_dir(int fd)
{
    if (!app_proc_view()) return 0;
    int saved = errno, result = own_proc_file(fd) == 3;
    errno = saved;
    return result;
}

/* Used by the shared path layer as well as readdir/getdents, including relative and symlink aliases. */
int apklayer_proc_map_hidden(int dirfd, const char *path)
{
    if (!path || !app_proc_view()) return 0;
    const char *base = strrchr(path, '/');
    base = base ? base + 1 : path;
    int saved = errno;
    unsigned long start, end; int consumed = 0;
    int parsed = sscanf(base, "%lx-%lx%n", &start, &end, &consumed);
    errno = saved;
    if (parsed != 2 || !consumed || base[consumed] || start >= end) return 0;
    int parent = dirfd, owned = -1, hidden = 0;
    long (*raw)(long, ...) = dlsym(RTLD_NEXT, "syscall");
    if (base != path) {
        size_t len = base - path;
        if (len >= PATH_MAX) return 0;
        char directory[PATH_MAX]; memcpy(directory, path, len); directory[len] = 0;
        parent = owned = raw(SYS_openat, dirfd, directory, O_PATH | O_DIRECTORY | O_CLOEXEC, 0);
    } else if (parent == AT_FDCWD) {
        parent = owned = raw(SYS_openat, AT_FDCWD, ".", O_PATH | O_DIRECTORY | O_CLOEXEC, 0);
    }
    if (parent >= 0 && apklayer_proc_map_dir(parent)) {
        char target[PATH_MAX];
        ssize_t n = raw(SYS_readlinkat, parent, base, target, sizeof target - 1);
        if (n > 0) {
            target[n] = 0;
            const char *root = jdk_root(); size_t len = strlen(root);
            hidden = len && !strncmp(target, root, len) && target[len] == '/';
        }
    }
    if (owned >= 0) close(owned);
    errno = saved;
    return hidden;
}

/* numa_maps escapes whitespace in file= tokens; replace only that token, retaining NUMA accounting. */
static void numa_line(FILE *out, const char *line)
{
    const char *token = strstr(line, " file=");
    if (!token) { fputs(line, out); return; }
    const char *name = token + 6, *end = name + strcspn(name, " \t\n");
    char decoded[PATH_MAX]; size_t used = 0;
    for (const char *p = name; p < end && used + 1 < sizeof decoded; p++) {
        if (*p == '\\' && end - p >= 4 && p[1] >= '0' && p[1] <= '7' && p[2] >= '0' && p[2] <= '7' && p[3] >= '0' && p[3] <= '7') {
            decoded[used++] = (char)((p[1] - '0') * 64 + (p[2] - '0') * 8 + p[3] - '0'); p += 3;
        } else decoded[used++] = *p;
    }
    decoded[used] = 0;
    if (used >= 10 && !strcmp(decoded + used - 10, " (deleted)")) decoded[used - 10] = 0;
    const char *projected = apklayer_proc_module_name(decoded);
    if (projected == decoded) { fputs(line, out); return; }
    fwrite(line, 1, (*projected ? name : token) - line, out);
    fputs(projected, out); fputs(end, out);
}

/* Replace only a successfully opened readable proc file; keep its descriptor and close-on-exec flag. */
static int proc_replace(int fd, int cloexec, const char *name, const char *text, size_t size)
{
    long (*raw)(long, ...) = dlsym(RTLD_NEXT, "syscall");
    int out = raw(SYS_memfd_create, name, 1 /* MFD_CLOEXEC */), result = -1;
    if (out < 0) return -1;
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
done:
    { int saved = errno; close(out); errno = saved; }
    return result;
}

/* What an app sees of the mount table (/proc/<pid>/mounts, mountinfo, mountstats): Android's mounts are its partitions, /data and the app's storage; the desktop's
 * (the user's /home, /run/user, /mnt drives, the NVIDIA and portal mounts, /boot, /tmp, systemd's autofs) name the host and are left out. The mounts that
 * exist on both are kept as they are. A mount is kept when its mount point is below one of Android's roots, or is one of the kernel's virtual file systems. */
static int mount_kept(const char *point)
{
    static const char *const roots[] = {"/", "/proc", "/sys", "/dev", "/system", "/vendor", "/product", "/system_ext", "/odm", "/apex", "/data", "/storage", "/sdcard", "/mnt/runtime", "/cache", "/metadata", "/linkerconfig", NULL};
    if (!strcmp(point, "/")) return 1;
    /* the desktop's own kernel file systems: no such mount in an Android app's table */
    static const char *const desktop[] = {"/dev/shm", "/dev/mqueue", "/dev/hugepages", "/dev/binderfs", "/sys/kernel/security", "/sys/firmware", "/sys/fs/fuse", "/proc/sys/fs/binfmt_misc", "/sys/fs/cgroup/user.slice", NULL};
    for (int i = 0; desktop[i]; i++) {
        size_t n = strlen(desktop[i]);
        if (!strncmp(point, desktop[i], n) && (!point[n] || point[n] == '/')) return 0;
    }
    for (int i = 1; roots[i]; i++) {
        size_t n = strlen(roots[i]);
        if (!strncmp(point, roots[i], n) && (!point[n] || point[n] == '/')) return 1;
    }
    return 0;
}

/* the mount point is field 2 of mounts and field 5 of mountinfo; mountstats says "mounted on <point> with fstype" */
static int mounts_line_kept(const char *line)
{
    char point[PATH_MAX]; size_t used = 0;
    const char *p = line;
    int skip = 1;                                           /* mounts: device, point */
    if (!strncmp(line, "device ", 7)) {                      /* mountstats */
        p = strstr(line, " mounted on ");
        if (!p) return 1;
        p += 12; skip = 0;
    } else if (line[0] >= '0' && line[0] <= '9') {          /* mountinfo: id parent major:minor root point */
        for (int field = 0; field < 4 && *p; p++) if (*p == ' ') field++;
        skip = 0;
    }
    for (int field = 0; field < skip && *p; p++) if (*p == ' ') field++;
    for (; *p && *p != ' ' && used + 1 < sizeof point; p++) {
        if (*p == '\\' && p[1] >= '0' && p[1] <= '7' && p[2] >= '0' && p[2] <= '7' && p[3] >= '0' && p[3] <= '7') {
            point[used++] = (char)((p[1] - '0') * 64 + (p[2] - '0') * 8 + p[3] - '0'); p += 3;
        } else point[used++] = *p;
    }
    point[used] = 0;
    return mount_kept(point);
}

static int mounts_filter(int fd, int cloexec)
{
    int copy = fcntl(fd, F_DUPFD_CLOEXEC, 0);
    FILE *in = copy < 0 ? NULL : fdopen(copy, "r");
    if (!in) { if (copy >= 0) close(copy); return -1; }
    char *line = NULL, *text = NULL;
    size_t cap = 0, size = 0;
    int result = -1;
    FILE *out = open_memstream(&text, &size);
    if (!out) goto done;
    int shape = 0;  /* 1 mountinfo, 2 mounts */
    while (getline(&line, &cap, in) >= 0) {
        if (!shape) shape = line[0] >= '0' && line[0] <= '9' ? 1 : strncmp(line, "device ", 7) ? 2 : 3;
        if (mounts_line_kept(line)) fputs(line, out);
    }
    /* Android's partitions (an Android 13 app's table: dm-verity system partitions, the ART/runtime apexes, /data, the FUSE storage) */
    static const struct { const char *point, *source, *type, *opts; } android[] = {
        {"/system", "/dev/block/dm-3", "ext4", "ro,seclabel,relatime"}, {"/vendor", "/dev/block/dm-4", "ext4", "ro,seclabel,relatime"},
        {"/product", "/dev/block/dm-5", "ext4", "ro,seclabel,relatime"}, {"/system_ext", "/dev/block/dm-6", "ext4", "ro,seclabel,relatime"},
        {"/odm", "/dev/block/dm-7", "ext4", "ro,seclabel,relatime"}, {"/apex", "tmpfs", "tmpfs", "rw,seclabel,nosuid,nodev,noexec,relatime,mode=755"},
        {"/apex/com.android.art", "/dev/block/loop1", "ext4", "ro,dirsync,seclabel,nodev,noatime"},
        {"/apex/com.android.runtime", "/dev/block/loop2", "ext4", "ro,dirsync,seclabel,nodev,noatime"},
        {"/apex/com.android.i18n", "/dev/block/loop3", "ext4", "ro,dirsync,seclabel,nodev,noatime"},
        {"/data", "/dev/block/dm-8", "ext4", "rw,seclabel,nosuid,nodev,noatime"}, {"/linkerconfig", "tmpfs", "tmpfs", "rw,seclabel,nosuid,nodev,noexec,relatime,mode=755"},
        {"/storage/emulated", "/dev/fuse", "fuse", "rw,lazytime,nosuid,nodev,noexec,noatime,user_id=0,group_id=0,allow_other"},
    };
    for (size_t i = 0; shape == 1 || shape == 2; i++) {
        if (i == sizeof android / sizeof *android) break;
        if (shape == 1) fprintf(out, "%zu 1 0:%zu / %s %s - %s %s %s\n", 1000 + i, 100 + i, android[i].point, android[i].opts, android[i].type, android[i].source, android[i].opts);
        else fprintf(out, "%s %s %s %s 0 0\n", android[i].source, android[i].point, android[i].type, android[i].opts);
    }
    if (ferror(in) || ferror(out) || fclose(out)) { out = NULL; goto done; }
    out = NULL;
    result = proc_replace(fd, cloexec, "mounts", text, size);
done:
    { int saved = errno; if (out) fclose(out); free(line); free(text); if (in) fclose(in); errno = saved; }
    return result;
}

/* The whole (small) proc file at fd: comm, stat, cgroup, cmdline; malloc'd, *size its length */
static char *slurp_fd(int fd, size_t *size)
{
    size_t cap = 4096, used = 0;
    char *text = malloc(cap + 1);
    for (off_t at = 0; text;) {
        ssize_t n = pread(fd, text + used, cap - used, at);
        if (n < 0 && errno == EINTR) continue;
        if (n < 0) { free(text); return NULL; }
        if (!n) break;
        used += (size_t)n; at += n;
        if (used == cap) { char *more = realloc(text, cap * 2 + 1); if (!more) { free(text); return NULL; } text = more; cap *= 2; }
    }
    if (text) text[used] = 0;
    *size = used;
    return text;
}

/* comm, stat, cgroup and cmdline of a thread of this process (tid), and the zygote and environ views of another process (foreign) */
extern __thread volatile int apklayer_proc_raw;
static int task_filter(int fd, int cloexec, int kind, long tid, long foreign)
{
    if (apklayer_proc_raw) return fd;
    if (foreign) {
        if (kind == 5) {  /* another Android program of the app: its exec environment (proc_task.c) */
            int env = apklayer_proc_foreign_environment(foreign);
            if (env < 0) return fd;  /* a process that publishes none (the JVM, a forked helper): the kernel's bytes, as before (tests/native/proc-environment.c) */
            size_t size = 0;
            char *text = slurp_fd(env, &size);
            close(env);
            int result = text ? proc_replace(fd, cloexec, "environ", text, size) : fd;
            free(text);
            return result;
        }
        if ((kind == 7 || kind == 10) && apklayer_proc_parent_is_host_tool(foreign))
            return kind == 7 ? proc_replace(fd, cloexec, "comm", "zygote64\n", 9) : proc_replace(fd, cloexec, "cmdline", "zygote64", 9);
        return fd;
    }
    size_t size;
    char *text = slurp_fd(fd, &size), out[512], name[16];
    if (!text) return -1;
    int result = fd;
    if (kind == 7) {
        text[strcspn(text, "\n")] = 0;
        int n = snprintf(out, sizeof out, "%s\n", apklayer_proc_thread_name(tid, text, name));
        result = proc_replace(fd, cloexec, "comm", out, (size_t)n);
    } else if (kind == 8) {  /* pid (comm) state ...: comm may hold ')' itself, so up to the last one */
        char *open_paren = strchr(text, '('), *close_paren = strrchr(text, ')');
        if (open_paren && close_paren && close_paren > open_paren) {
            char real[64], *joined = NULL;
            snprintf(real, sizeof real, "%.*s", (int)(close_paren - open_paren - 1), open_paren + 1);
            int n = asprintf(&joined, "%.*s(%s%s", (int)(open_paren - text), text, apklayer_proc_thread_name(tid, real, name), close_paren);
            if (n >= 0) result = proc_replace(fd, cloexec, "stat", joined, (size_t)n);
            free(joined);
        }
    } else if (kind == 9) {
        int n = apklayer_proc_cgroup(out, sizeof out, getpid());
        result = proc_replace(fd, cloexec, "cgroup", out, (size_t)n);
    } else if (kind == 10 && dlsym(RTLD_DEFAULT, "apklayer_btload_program")) {  /* btload's moved arguments: no NUL padding after them */
        while (size > 1 && !text[size - 1] && !text[size - 2]) size--;
        result = proc_replace(fd, cloexec, "cmdline", text, size);
    }
    free(text);
    return result;
}

/* The ELF behind /proc/self/exe: Android's app_process64 stand-in, a static executable of this machine's architecture that only exits (a reader
 * finds an ELF64 of the right class and machine, as in the real binary; it has no symbols). Returns its size. */
static size_t app_process_standin(unsigned char *out)
{
#if defined(__aarch64__)
    static const unsigned char code[] = {0xa8, 0x0b, 0x80, 0xd2, 0x00, 0x00, 0x80, 0xd2, 0x01, 0x00, 0x00, 0xd4};  /* mov x8,#93; mov x0,#0; svc #0 */
    const int machine = EM_AARCH64;
#else
    static const unsigned char code[] = {0xb8, 0x3c, 0, 0, 0, 0x31, 0xff, 0x0f, 0x05};  /* mov eax,60; xor edi,edi; syscall */
    const int machine = EM_X86_64;
#endif
    Elf64_Ehdr eh = {0};
    Elf64_Phdr ph = {0};
    size_t head = sizeof eh + sizeof ph, size = head + sizeof code;
    memcpy(eh.e_ident, ELFMAG, SELFMAG);
    eh.e_ident[EI_CLASS] = ELFCLASS64; eh.e_ident[EI_DATA] = ELFDATA2LSB; eh.e_ident[EI_VERSION] = EV_CURRENT;
    eh.e_type = ET_EXEC; eh.e_machine = machine; eh.e_version = EV_CURRENT; eh.e_entry = 0x400000 + head;
    eh.e_phoff = sizeof eh; eh.e_ehsize = sizeof eh; eh.e_phentsize = sizeof ph; eh.e_phnum = 1;
    ph.p_type = PT_LOAD; ph.p_flags = PF_R | PF_X; ph.p_vaddr = ph.p_paddr = 0x400000; ph.p_filesz = ph.p_memsz = size; ph.p_align = 0x1000;
    memcpy(out, &eh, sizeof eh); memcpy(out + sizeof eh, &ph, sizeof ph); memcpy(out + head, code, sizeof code);
    return size;
}

/* fd remains owned by the caller, including on failure. */
static int proc_filter(int fd)
{
    const char *view = getenv("APKLAYER_PROC_VIEW");
    if (fd < 0 || !apklayer_own_data_env() || (view && !strcmp(view, "host"))) return fd;
    int flags = fcntl(fd, F_GETFL), cloexec = fcntl(fd, F_GETFD);
    if (flags < 0 || (flags & (O_PATH | O_ACCMODE)) != O_RDONLY) return fd;
    long tid = 0, foreign = 0;
    int kind = proc_file_of(fd, &tid, &foreign);
    if (!kind || kind == 3) return fd;
    if (kind == 11) { unsigned char elf[128]; size_t size = app_process_standin(elf); return proc_replace(fd, cloexec, "app_process64", (const char *)elf, size); }
    if (foreign || kind >= 7) return task_filter(fd, cloexec, kind, tid, foreign);
    if (kind == 5) {
        const char *(*initial)(size_t *) = dlsym(RTLD_DEFAULT, "libc_bio_initial_environment");
        if (!initial) { errno = ENOSYS; return -1; }
        size_t size = 0;
        errno = 0;
        const char *text = initial(&size);
        if (!text) return errno ? -1 : fd;
        return proc_replace(fd, cloexec, "environ", text, size);
    }
    if (kind == 6) return mounts_filter(fd, cloexec);
    long app_uid = apklayer_app_uid();
    int copy = fcntl(fd, F_DUPFD_CLOEXEC, 0), result = -1;
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
        if (kind == 4) { numa_line(buffer, line); continue; }
        if (kind == 2) {
            const char *fixed = apklayer_proc_status_line(line);
            if (fixed) { fputs(fixed, buffer); continue; }
            if (!strncmp(line, "Name:", 5)) {
                char real[64], name[16];
                snprintf(real, sizeof real, "%s", line + 5 + strspn(line + 5, " \t"));
                real[strcspn(real, "\n")] = 0;
                fprintf(buffer, "Name:\t%s\n", apklayer_proc_thread_name(tid, real, name));
                continue;
            }
            gid_t groups[16];
            int ngroups;
            if (!strncmp(line, "Groups:", 7) && (ngroups = apklayer_app_groups(groups, 16)) >= 0) {
                fputs("Groups:\t", buffer);
                for (int i = 0; i < ngroups; i++) fprintf(buffer, "%u ", (unsigned)groups[i]);
                fputc('\n', buffer);
                continue;
            }
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
            if (!*path && perms[0] == 'r' && perms[1] == 'w' && perms[2] == 'x') {  /* the JVM's code cache: ART's JIT cache is never writable and executable at once */
                fprintf(buffer, "%lx-%lx r-x%c %08lx %02x:%02x %lu [anon:dalvik-jit-code-cache]\n", start, end, perms[3], offset, major, minor, inode);
                continue;
            }
            char mapped[PATH_MAX], plain[PATH_MAX];
            snprintf(plain, sizeof plain, "%.*s", (int)strcspn(path, "\n"), path);
            const char *android = *plain ? apklayer_proc_map_name(plain, mapped, sizeof mapped) : NULL;
            if (android && !*android) {
                fprintf(buffer, "%lx-%lx %s 00000000 00:00 0\n", start, end, perms);
                continue;
            }
            if (android) {
                fprintf(buffer, "%lx-%lx %s %08lx %02x:%02x %lu %s\n", start, end, perms, offset, major, minor, inode, android);
                continue;
            }
        }
        fputs(line, buffer);
    }
    if (ferror(in) || ferror(buffer)) goto buffered;
    if (fclose(buffer)) { buffer = NULL; goto done; }
    buffer = NULL;
    result = proc_replace(fd, cloexec, "maps", text, size);
    goto done;
buffered:
    fclose(buffer);
done:
    { int saved = errno; free(line); free(text); fclose(in); errno = saved; }
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
    int proc = apklayer_own_data_env() && apklayer_proc_is_dir(fd);
    int maps = apklayer_proc_map_dir(fd);
    int filter = proc || maps;
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
            if (!(proc && apklayer_proc_pid_hidden(entry + 19)) && !(maps && apklayer_proc_map_hidden(fd, entry + 19))) {
                memmove((char *)buf + used, entry, len);
                used += len;
            }
            at += len;
        }
        if (used) return (long)used;
    }
}
