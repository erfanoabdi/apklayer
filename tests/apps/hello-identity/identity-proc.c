/* /proc/self as the app's native code reads it (handoff item 6): maps markers, smaps, map_files, status, cmdline, comm, environ, auxv,
 * exe/fd/ns links, mountinfo, cgroup, SELinux attr, thread names, limits. Values are summarized to what differs between runtimes. */
#include "identity.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <unistd.h>

static const char *MARKERS[] = {"/apex/com.android.art/lib64/libart.so", "/apex/com.android.runtime/lib64/bionic/libc.so",
    "/apex/com.android.runtime/bin/linker64", "/system/bin/linker64", "/system/bin/app_process64", "libjavacore.so", "libopenjdk.so",
    "libandroid_runtime.so", "boot.art", "boot.oat", "boot-framework", ".vdex", ".odex", "base.apk", "/system/framework/framework.jar",
    "/apex/com.android.art/javalib/core-oj.jar", "[anon:dalvik-main space", "[anon:dalvik-", "dalvik-jit-code-cache", "[anon:libc_malloc]",
    "[anon:scudo:", "[anon:.bss]", "[anon:stack_and_tls:", "[stack]", "[vdso]", "[vvar]", "memfd:", "(deleted)", "/dev/ashmem",
    "/dev/__properties__", "/dev/binderfs", "/data/dalvik-cache", "libjvm.so", "libjava.so", "/lib/modules", ".jsa", "/usr/lib", "/home/",
    "/tmp/", "/opt/", "/snap/", "ld-linux", "libberberis", "libndk_translation", "libhoudini", "libapklayer", "libglib", "libwayland",
    "jdk", "java-", "/proc/", "/dev/dri", "/dev/kgsl", "/dev/mali", "gralloc", "/vendor/lib64/"};

static void maps(struct buf *b)
{
    size_t size;
    char *text = slurp("/proc/self/maps", &size);
    if (!text) { put(b, "maps", "ERR %d", errno); return; }
    /* the full view, to read what the app sees: only when its data directory holds a file of this name (tests/identity) */
    FILE *dump = fopen("/data/user/0/hello.identity/files/maps-dump.txt", "r+");
    if (dump) { fwrite(text, 1, size, dump); fclose(dump); }
    int lines = 0, rwx = 0, named = 0, anon = 0;
    char rwx_names[2048] = "";
    unsigned long rwx_kb = 0;
    char *paths = calloc(1, size + 1), *exe_first = NULL;
    size_t plen = 0;
    for (char *line = strtok(text, "\n"); line; line = strtok(NULL, "\n")) {
        lines++;
        char perms[8] = "";
        int path_at = 0;
        sscanf(line, "%*s %7s %*s %*s %*s %n", perms, &path_at);
        const char *path = path_at ? line + path_at : "";
        while (*path == ' ') path++;
        if (!strcmp(perms, "rwxp") || !strcmp(perms, "rwxs")) {
            unsigned long start = 0, end = 0;
            sscanf(line, "%lx-%lx", &start, &end);
            rwx++; rwx_kb += (end - start) >> 10;
            char item[96];  /* which mappings are W+X: their names (anonymous as "anon") */
            snprintf(item, sizeof item, "%.80s;", *path ? path : "anon");
            if (!strstr(rwx_names, item)) strncat(rwx_names, item, sizeof rwx_names - strlen(rwx_names) - 1);
        }
        if (!*path) { anon++; continue; }
        named++;
        if (!exe_first && strchr(perms, 'x')) exe_first = (char *)path;
        if (!strstr(paths, path)) plen += (size_t)sprintf(paths + plen, "%s;", path);
    }
    put(b, "maps.lines", "%d", lines);
    put(b, "maps.anonymous", "%d", anon);
    put(b, "maps.rwx", "%d", rwx);
    put(b, "maps.rwx.kb", "%lu", rwx_kb);
    put(b, "maps.rwx.names", "%s", rwx_names);
    put(b, "maps.firstExec", "%s", exe_first ? exe_first : "none");
    char present[4096] = "", absent[4096] = "";
    for (size_t i = 0; i < sizeof MARKERS / sizeof *MARKERS; i++) {
        char *dst = strstr(paths, MARKERS[i]) ? present : absent;
        strncat(dst, MARKERS[i], sizeof present - strlen(dst) - 2);
        strcat(dst, " ");
    }
    put(b, "maps.markers.present", "%s", present);
    put(b, "maps.markers.absent", "%s", absent);
    put(b, "maps.paths", "%s", paths);
    free(paths);
    free(text);
}

static void smaps(struct buf *b)
{
    size_t size;
    char *text = slurp("/proc/self/smaps", &size);
    if (!text) { put(b, "smaps", "ERR %d", errno); return; }
    /* the field names of the first entry */
    char fields[1024] = "";
    char *line = strtok(text, "\n");
    for (line = strtok(NULL, "\n"); line && !strchr(line, '-'); line = strtok(NULL, "\n")) {
        char name[64];
        if (sscanf(line, "%63[^:]:", name) == 1) { strncat(fields, name, sizeof fields - strlen(fields) - 2); strcat(fields, " "); }
    }
    put(b, "smaps.fields", "%s", fields);
    free(text);
    text = slurp("/proc/self/smaps_rollup", &size);
    put(b, "smaps_rollup", "%s", text ? "readable" : strerror(errno));
    free(text);
}

static void map_files(struct buf *b)
{
    DIR *d = opendir("/proc/self/map_files");
    if (!d) { put(b, "map_files", "opendir %s", strerror(errno)); return; }
    int n = 0, links = 0, jdk = 0;
    char first[PATH_MAX] = "", err[64] = "";
    struct dirent *e;
    while ((e = readdir(d))) {
        if (e->d_name[0] == '.') continue;
        n++;
        char path[PATH_MAX], target[PATH_MAX];
        snprintf(path, sizeof path, "/proc/self/map_files/%s", e->d_name);
        ssize_t r = readlink(path, target, sizeof target - 1);
        if (r < 0) { if (!err[0]) snprintf(err, sizeof err, "%s", strerror(errno)); continue; }
        target[r] = 0;
        links++;
        if (strstr(target, "libjvm") || strstr(target, "/lib/modules")) jdk++;
        if (!first[0]) snprintf(first, sizeof first, "%s", target);
    }
    closedir(d);
    put(b, "map_files", "entries=%d readlink_ok=%d readlink_err=%s jdk=%d first=%s", n, links, err[0] ? err : "none", jdk, first);
}

static void status(struct buf *b)
{
    char *text = slurp("/proc/self/status", NULL);
    if (!text) { put(b, "status", "ERR %d", errno); return; }
    static const char *keep[] = {"Name", "Umask", "State", "TracerPid", "Uid", "Gid", "Groups", "NStgid", "Kthread", "Threads", "Seccomp",
        "Seccomp_filters", "NoNewPrivs", "CapInh", "CapPrm", "CapEff", "CapBnd", "CapAmb", "Speculation_Store_Bypass", "Cpus_allowed_list",
        "untag_mask", "x86_Thread_features", "Mems_allowed_list"};
    char names[2048] = "";
    for (char *line = strtok(text, "\n"); line; line = strtok(NULL, "\n")) {
        char *colon = strchr(line, ':');
        if (!colon) continue;
        *colon = 0;
        strncat(names, line, sizeof names - strlen(names) - 2); strcat(names, " ");
        for (size_t i = 0; i < sizeof keep / sizeof *keep; i++)
            if (!strcmp(line, keep[i])) {
                char key[64], *v = colon + 1;
                while (*v == ' ' || *v == '\t') v++;
                snprintf(key, sizeof key, "status.%s", line);
                put(b, key, "%s", v);
            }
    }
    put(b, "status.fieldNames", "%s", names);
    free(text);
}

static void raw(struct buf *b, const char *key, const char *path, int nul_to_space)
{
    size_t size;
    char *text = slurp(path, &size);
    if (!text) { put(b, key, "ERR %s", strerror(errno)); return; }
    if (nul_to_space) for (size_t i = 0; i + 1 < size; i++) if (!text[i]) text[i] = ' ';
    while (size && (text[size - 1] == '\n' || text[size - 1] == 0)) text[--size] = 0;
    put(b, key, "%s", size > 3000 ? "(long)" : text);
    free(text);
}

static int cmp(const void *a, const void *b) { return strcmp(*(char *const *)a, *(char *const *)b); }

static void environ_keys(struct buf *b)
{
    size_t size;
    char *text = slurp("/proc/self/environ", &size);
    if (!text) { put(b, "environ", "ERR %s", strerror(errno)); return; }
    char *keys[512]; int n = 0;
    for (size_t i = 0; i < size && n < 512; i += strlen(text + i) + 1) {
        char *eq = strchr(text + i, '=');
        if (eq) *eq = 0;
        keys[n++] = text + i;
        if (eq) *eq = '=';
    }
    for (int i = 0; i < n; i++) { char *eq = strchr(keys[i], '='); if (eq) *eq = 0; }
    qsort(keys, (size_t)n, sizeof *keys, cmp);
    char out[8192] = "";
    for (int i = 0; i < n; i++) { strncat(out, keys[i], sizeof out - strlen(out) - 2); strcat(out, " "); }
    put(b, "environ.count", "%d", n);
    put(b, "environ.keys", "%s", out);
    free(text);
}

static void auxv(struct buf *b)
{
    size_t size;
    unsigned long *v = (unsigned long *)slurp("/proc/self/auxv", &size);
    if (!v) { put(b, "auxv", "ERR %s", strerror(errno)); return; }
    char types[1024] = "";
    int mismatch = 0;
    for (size_t i = 0; i + 1 < size / sizeof *v && v[i]; i += 2) {
        char t[24];
        snprintf(t, sizeof t, "%lu ", v[i]);
        strncat(types, t, sizeof types - strlen(types) - 1);
        if (v[i] == AT_HWCAP || v[i] == AT_HWCAP2 || v[i] == AT_PAGESZ || v[i] == AT_UID || v[i] == AT_EUID || v[i] == AT_SECURE || v[i] == AT_PHNUM)
            if (getauxval(v[i]) != v[i + 1]) mismatch++;
        if (v[i] == AT_HWCAP) put(b, "auxv.file.hwcap", "0x%lx", v[i + 1]);
        if (v[i] == AT_HWCAP2) put(b, "auxv.file.hwcap2", "0x%lx", v[i + 1]);
        if (v[i] == AT_PLATFORM) { char s[32] = ""; peek(v[i + 1], s, 31); put(b, "auxv.file.platform", "%.31s", s); }
        if (v[i] == AT_EXECFN) { char s[256] = ""; peek(v[i + 1], s, 255); put(b, "auxv.file.execfn", "%.255s", s); }
    }
    put(b, "auxv.file.types", "%s", types);
    put(b, "auxv.file.getauxvalMismatch", "%d", mismatch);
    free(v);
}

static void links(struct buf *b)
{
    const char *names[] = {"exe", "cwd", "root", "ns/mnt", "ns/pid", "ns/net", "ns/user", "ns/uts", "ns/ipc", "ns/cgroup", "ns/time"};
    for (size_t i = 0; i < sizeof names / sizeof *names; i++) {
        char path[64], target[PATH_MAX], key[64];
        snprintf(path, sizeof path, "/proc/self/%s", names[i]);
        snprintf(key, sizeof key, "link.%s", names[i]);
        ssize_t r = readlink(path, target, sizeof target - 1);
        if (r < 0) { put(b, key, "ERR %s", strerror(errno)); continue; }
        target[r] = 0;
        if (!strncmp(names[i], "ns/", 3)) { char *br = strchr(target, '['); if (br) strcpy(br, "[N]"); }
        put(b, key, "%s", target);
    }
    {   /* the file behind the exe link: an ELF of the app's ABI (Android: app_process64), not the JDK's java */
        unsigned char head[20] = {0};
        int fd = open("/proc/self/exe", O_RDONLY | O_CLOEXEC);
        ssize_t got = fd < 0 ? -1 : read(fd, head, sizeof head);
        if (fd >= 0) close(fd);
        if (got < 20) put(b, "exe.file", "ERR %s", fd < 0 ? strerror(errno) : "short");
        else put(b, "exe.file", "%s machine=%u", memcmp(head, "\177ELF", 4) ? "not ELF" : "ELF", head[18] | head[19] << 8);
    }
    DIR *d = opendir("/proc/self/fd");
    if (!d) { put(b, "fd", "ERR %s", strerror(errno)); return; }
    char *targets[1024]; int n = 0;
    struct dirent *e;
    while ((e = readdir(d)) && n < 1024) {
        if (e->d_name[0] == '.') continue;
        char path[64], target[PATH_MAX];
        snprintf(path, sizeof path, "/proc/self/fd/%s", e->d_name);
        ssize_t r = readlink(path, target, sizeof target - 1);
        if (r < 0) continue;
        target[r] = 0;
        char *br = strchr(target, '[');
        if (br && (!strncmp(target, "socket:", 7) || !strncmp(target, "pipe:", 5))) *br = 0;
        targets[n++] = strdup(target);
    }
    closedir(d);
    qsort(targets, (size_t)n, sizeof *targets, cmp);
    char out[16384] = "";
    for (int i = 0; i < n; i++) {
        if (i && !strcmp(targets[i], targets[i - 1])) { free(targets[i]); continue; }
        strncat(out, targets[i], sizeof out - strlen(out) - 2); strcat(out, ";");
        free(targets[i]);
    }
    put(b, "fd.count", "%d", n);
    put(b, "fd.targets", "%s", out);
}

static void mountinfo(struct buf *b)
{
    char *text = slurp("/proc/self/mountinfo", NULL);
    if (!text) { put(b, "mountinfo", "ERR %s", strerror(errno)); return; }
    int n = 0;
    char out[16384] = "";
    for (char *line = strtok(text, "\n"); line; line = strtok(NULL, "\n")) {
        char point[512], type[64];
        n++;
        char *sep = strstr(line, " - ");
        if (sscanf(line, "%*s %*s %*s %*s %511s", point) == 1 && sep && sscanf(sep + 3, "%63s", type) == 1) {
            char item[600];
            snprintf(item, sizeof item, "%s:%s;", point, type);
            strncat(out, item, sizeof out - strlen(out) - 1);
        }
    }
    put(b, "mountinfo.count", "%d", n);
    put(b, "mountinfo.points", "%s", out);
    free(text);
}

static void tasks(struct buf *b)
{
    DIR *d = opendir("/proc/self/task");
    if (!d) { put(b, "task", "ERR %s", strerror(errno)); return; }
    char *names[512]; int n = 0;
    struct dirent *e;
    while ((e = readdir(d)) && n < 512) {
        if (e->d_name[0] == '.') continue;
        char path[64];
        snprintf(path, sizeof path, "/proc/self/task/%s/comm", e->d_name);
        char *comm = slurp(path, NULL);
        if (!comm) continue;
        comm[strcspn(comm, "\n")] = 0;
        for (char *c = comm; *c; c++) if (*c >= '0' && *c <= '9') *c = 'N';
        names[n++] = comm;
    }
    closedir(d);
    qsort(names, (size_t)n, sizeof *names, cmp);
    char out[16384] = "";
    for (int i = 0; i < n;) {
        int j = i;
        while (j < n && !strcmp(names[j], names[i])) j++;
        char item[96];
        if (j - i > 1) snprintf(item, sizeof item, "%s*%d;", names[i], j - i);
        else snprintf(item, sizeof item, "%s;", names[i]);
        strncat(out, item, sizeof out - strlen(out) - 1);
        i = j;
    }
    for (int i = 0; i < n; i++) free(names[i]);
    put(b, "task.count", "%d", n);
    put(b, "task.comms", "%s", out);
}

/* What a program the app starts sees (an Android shell and its toybox children, as Termux's): its own status/comm/cmdline/exe, and its parent
 * shell's environ (the exec environment on Android). Output lines are joined with '|'. */
static void child(struct buf *b, const char *key, const char *command)
{
    FILE *p = popen(command, "r");
    if (!p) { put(b, key, "ERR popen %s", strerror(errno)); return; }
    char out[8192] = "", line[1024];
    while (fgets(line, sizeof line, p)) strncat(out, line, sizeof out - strlen(out) - 1);
    int status = pclose(p);
    put(b, key, "exit=%d %s", status, out);
}

static void children(struct buf *b)
{
    /* toybox by its full path: the Android root has /system/bin/toybox but no applet links */
    child(b, "child.status", "/system/bin/sh -c 't=/system/bin/toybox; $t cat /proc/self/status | $t grep -E \"^(Name|Uid|TracerPid|NoNewPrivs|Seccomp):\"; true' 2>&1");
    child(b, "child.comm", "/system/bin/sh -c '/system/bin/toybox cat /proc/self/comm; true' 2>&1");
    child(b, "child.cmdline", "/system/bin/sh -c 't=/system/bin/toybox; $t cat /proc/self/cmdline | $t tr \"\\0\" \" \"; echo' 2>&1");
    child(b, "child.exe", "/system/bin/sh -c 't=/system/bin/toybox; $t readlink /proc/self/exe; $t readlink /proc/$$/exe' 2>&1");
    child(b, "child.parentEnviron", "PROBE_PARENT=1 /system/bin/sh -c 't=/system/bin/toybox; $t cat /proc/$$/environ | $t tr \"\\0\" \"\\n\" | $t cut -d= -f1 | $t sort | $t tr \"\\n\" \" \"; echo' 2>&1");
}

JNIEXPORT jstring JNICALL Java_hello_identity_Main_proc(JNIEnv *env, jclass cls)
{
    (void)cls;
    struct buf b = {0};
    maps(&b);
    smaps(&b);
    map_files(&b);
    status(&b);
    raw(&b, "cmdline", "/proc/self/cmdline", 1);
    raw(&b, "comm", "/proc/self/comm", 0);
    raw(&b, "stat.head", "/proc/self/stat", 0);
    raw(&b, "cgroup", "/proc/self/cgroup", 0);
    raw(&b, "attr.current", "/proc/self/attr/current", 1);
    raw(&b, "attr.prev", "/proc/self/attr/prev", 1);
    raw(&b, "oom_score_adj", "/proc/self/oom_score_adj", 0);
    raw(&b, "loginuid", "/proc/self/loginuid", 0);
    raw(&b, "limits", "/proc/self/limits", 0);
    raw(&b, "wchan", "/proc/self/wchan", 0);
    raw(&b, "proc1.cmdline", "/proc/1/cmdline", 1);
    raw(&b, "version", "/proc/version", 0);
    raw(&b, "net.unix.head", "/proc/net/unix", 0);
    environ_keys(&b);
    auxv(&b);
    links(&b);
    mountinfo(&b);
    tasks(&b);
    children(&b);
    char *ppid_line = slurp("/proc/self/stat", NULL);
    if (ppid_line) {
        int ppid = 0;
        char *close_paren = strrchr(ppid_line, ')');
        if (close_paren) sscanf(close_paren + 2, "%*c %d", &ppid);
        char path[64];
        snprintf(path, sizeof path, "/proc/%d/cmdline", ppid);
        raw(&b, "parent.cmdline", path, 1);
        free(ppid_line);
    }
    return finish(env, &b);
}
