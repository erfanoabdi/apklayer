/* The desktop's own /etc files in an app's view (bridge/native/paths.c desktop_etc_path): once the process knows its package, its distribution, host and shell
 * identity files are ENOENT (os-release, lsb-release, hostname, machine-id, fstab, sudoers, systemd, apt, ...); what the host's libraries need stays (localtime,
 * resolv.conf, ld.so.cache, ssl, fonts, nsswitch.conf, hosts and the Android passwd/group of the root); APKLAYER_ETC_VIEW=host turns the filter off. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

static int has(const char *path) { struct stat st; return stat(path, &st) == 0; }

int main(int argc, char **argv) {
    (void)argc;
    static const char *const hidden[] = {"/etc/os-release", "/etc/lsb-release", "/etc/hostname", "/etc/machine-id", "/etc/fstab", "/etc/shells", "/etc/apt", "/etc/systemd", NULL};
    static const char *const kept[] = {"/etc/localtime", "/etc/resolv.conf", "/etc/ld.so.cache", "/etc/ssl", "/etc/nsswitch.conf", NULL};
    if (!getenv("APKLAYER_TEST_ETC")) {
        int on_host = 0;
        for (int i = 0; hidden[i]; i++) on_host += has(hidden[i]);
        if (on_host < 2) { puts("skipped: this host has fewer than two of the files to hide"); return 77; }
        setenv("APKLAYER_TEST_ETC", "1", 1);
        execv("/proc/self/exe", argv);
        return 1;
    }
    int kept_before[8] = {0};
    for (int i = 0; kept[i]; i++) kept_before[i] = has(kept[i]);
    int hidden_before[16] = {0};
    for (int i = 0; hidden[i]; i++) hidden_before[i] = has(hidden[i]);
    void (*map)(const char *, long, const char *, const char *) = dlsym(RTLD_DEFAULT, "apklayer_map_own_data");
    assert(map);
    map("p.etc", 12345, "", "/tmp/plt");
    if (getenv("APKLAYER_ETC_VIEW")) {
        for (int i = 0; hidden[i]; i++) assert(has(hidden[i]) == hidden_before[i]);
        puts("etc-hidden host view ok");
        return 0;
    }
    for (int i = 0; hidden[i]; i++) assert(!has(hidden[i]) && (errno == ENOENT || !hidden_before[i]));
    for (int i = 0; kept[i]; i++) assert(has(kept[i]) == kept_before[i]);
    if (fork() == 0) { setenv("APKLAYER_ETC_VIEW", "host", 1); execv("/proc/self/exe", argv); _exit(1); }
    int status; wait(&status);
    assert(WIFEXITED(status) && WEXITSTATUS(status) == 0);
    puts("etc-hidden ok");
    return 0;
}
