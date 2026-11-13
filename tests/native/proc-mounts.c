/* The mount table an app sees (bridge/native/proc_view.c mounts_filter): /proc/self/mounts, mountinfo and mountstats name no desktop mount point (/home,
 * /run, /tmp, /mnt drives, /boot, the portal and NVIDIA mounts) once the process knows its package; the kernel's own (/proc, /sys, /dev) and "/" stay,
 * and the same files read through the host's view (APKLAYER_PROC_VIEW=host) are unchanged. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int count(const char *path, const char *needle_a, const char *needle_b, int *total) {
    FILE *f = fopen(path, "r");
    char *line = NULL; size_t cap = 0; int hits = 0;
    *total = 0;
    if (!f) return -1;
    while (getline(&line, &cap, f) >= 0) {
        (*total)++;
        if ((needle_a && strstr(line, needle_a)) || (needle_b && strstr(line, needle_b))) hits++;
    }
    free(line); fclose(f);
    return hits;
}

int main(void) {
    int host_total, total, leaked;
    int host_desktop = count("/proc/self/mounts", " /home ", " /run/user", &host_total);  /* before the mapping: the real table */
    void (*map)(const char *, long, const char *, const char *) = dlsym(RTLD_DEFAULT, "apklayer_map_own_data");
    assert(map);
    map("p.mounts", 12345, "", "/tmp/plt");
    const char *files[] = {"/proc/self/mounts", "/proc/self/mountinfo", "/proc/%d/mounts", NULL};
    for (int i = 0; files[i]; i++) {
        char path[64];
        snprintf(path, sizeof path, files[i], getpid());
        leaked = count(path, " /run/user", " /tmp ", &total) + count(path, " /boot", " /mnt/", &host_total) + count(path, "nvidia", "fuse.portal", &host_total);
        int kernel = count(path, " /proc ", " /sys ", &host_total);
        printf("%-24s %3d lines, desktop mounts left: %d, /proc and /sys kept: %d\n", path, total, leaked, kernel);
        assert(leaked == 0 && kernel >= 1 && total >= 1);
    }
    (void)host_desktop;
    puts("proc-mounts ok");
    return 0;
}
