/* The native bridge's own libraries in an app's /proc/self/maps (bridge/native/proc_task.c apklayer_proc_map_name): the staged Android libraries show at
 * /system/lib64/<name>, but libberberis_*.so (the translator and its proxies) exist on no Android device: those mappings keep their ranges and lose their name.
 * A fixture library of each kind is mapped from a scratch android-libs directory. Through the host's view (APKLAYER_PROC_VIEW=host) both names stay. */
#define _GNU_SOURCE
#include <assert.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

static void *map_file(const char *path) {
    int fd = open(path, O_RDONLY);
    assert(fd >= 0);
    void *m = mmap(NULL, 4096, PROT_READ, MAP_PRIVATE, fd, 0);
    assert(m != MAP_FAILED);
    close(fd);
    return m;
}

int main(int argc, char **argv) {
    char dir[] = "/tmp/apklayer-bridge-libs-XXXXXX", libs[200], a[256], b[256], cmd[600];
    if (!getenv("APKLAYER_TEST_DIR")) {
        assert(mkdtemp(dir));
        setenv("APKLAYER_TEST_DIR", dir, 1);
        snprintf(libs, sizeof libs, "%s/android-libs", dir);
        assert(mkdir(libs, 0700) == 0);
        snprintf(a, sizeof a, "%s/libberberis_fixture.so", libs);
        snprintf(b, sizeof b, "%s/libfixture_plain.so", libs);
        const char *fx[] = {a, b};
        for (int i = 0; i < 2; i++) { const char *p = fx[i]; int fd = open(p, O_CREAT | O_WRONLY, 0600); assert(fd >= 0); assert(write(fd, "x", 1) == 1); close(fd); }
        execv("/proc/self/exe", argv);
        return 1;
    }
    (void)argc;
    const char *tmp = getenv("APKLAYER_TEST_DIR");
    snprintf(a, sizeof a, "%s/android-libs/libberberis_fixture.so", tmp);
    snprintf(b, sizeof b, "%s/android-libs/libfixture_plain.so", tmp);
    char *ma = map_file(a), *mb = map_file(b);
    int host = getenv("APKLAYER_PROC_VIEW") != NULL, seen_bridge = 0, seen_plain = 0, bridge_nameless = 0;
    FILE *f = fopen("/proc/self/maps", "r");
    char line[1024];
    while (f && fgets(line, sizeof line, f)) {
        unsigned long s, e;
        if (sscanf(line, "%lx-%lx", &s, &e) != 2) continue;
        if (s == (unsigned long)ma) { seen_bridge = 1; bridge_nameless = !strchr(line, '/') && !strstr(line, "berberis"); }
        if (s == (unsigned long)mb) { seen_plain = 1; assert(host ? strstr(line, "libfixture_plain.so") != NULL : strstr(line, "/system/lib64/libfixture_plain.so") != NULL); }
        if (host && s == (unsigned long)ma) assert(strstr(line, "libberberis_fixture.so"));
    }
    if (f) fclose(f);
    assert(seen_bridge && seen_plain);
    if (!host) assert(bridge_nameless);
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", tmp);
    if (!strncmp(tmp, "/tmp/apklayer-bridge-libs-", 26)) assert(system(cmd) == 0);
    puts(host ? "proc-bridge-libs host view ok" : "proc-bridge-libs ok");
    return 0;
}
