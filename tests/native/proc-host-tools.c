/* apklayer's own tooling in the app's cgroup scope (bridge/native/paths.c host_tool_process): `apklayer run` and the CLI it starts live next to the app's JVMs in its
 * systemd scope, so the cgroup test alone lets /proc/<pid> of them through. An executable named apklayer* is the host's tool and is ENOENT to the app; a process of
 * another name in the scope (the app's own JVMs) stays visible. Run in its own cgroup scope (systemd-run --user --scope); skipped (77) where others share it. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

static pid_t start(const char *exe) {
    pid_t pid = fork();
    if (pid == 0) { execl(exe, exe, "30", (char *)NULL); _exit(127); }
    return pid;
}

static int visible(pid_t pid) { char p[40]; struct stat st; snprintf(p, sizeof p, "/proc/%d", pid); return stat(p, &st) == 0; }

int main(void) {
    char c1[256] = "", path[600], line[64]; int others = 0; FILE *f = fopen("/proc/self/cgroup", "r");
    if (f && fgets(c1, sizeof c1, f)) { c1[strcspn(c1, "\n")] = 0; snprintf(path, sizeof path, "/sys/fs/cgroup%s/cgroup.procs", c1 + 3); } else path[0] = 0;
    if (f) fclose(f);
    if (path[0] && (f = fopen(path, "r"))) { while (fgets(line, sizeof line, f)) others += atoi(line) != getpid(); fclose(f); }
    if (!path[0] || others) { puts("skipped: other processes share this cgroup (run it in systemd-run --user --scope)"); return 77; }

    char dir[] = "/tmp/apklayer-host-tools-XXXXXX", fake[64], plain[64], cmd[400];
    assert(mkdtemp(dir));
    snprintf(fake, sizeof fake, "%s/apklayer-fake", dir);
    snprintf(plain, sizeof plain, "%s/sleeper", dir);
    snprintf(cmd, sizeof cmd, "cp /bin/sleep '%s' && cp /bin/sleep '%s'", fake, plain);
    assert(system(cmd) == 0);
    pid_t tool = start(fake), other = start(plain);
    assert(tool > 0 && other > 0);
    usleep(200000);
    assert(visible(tool) && visible(other));  /* before the mapping: the host's own view */
    void (*map)(const char *, long, const char *, const char *) = dlsym(RTLD_DEFAULT, "apklayer_map_own_data");
    assert(map);
    map("p.tools", 12345, "", "/tmp/plt");
    int tool_seen = visible(tool), other_seen = visible(other);
    printf("host tool (apklayer-fake) visible: %d, another process of the scope visible: %d\n", tool_seen, other_seen);
    kill(tool, SIGKILL); kill(other, SIGKILL); waitpid(tool, NULL, 0); waitpid(other, NULL, 0);
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", dir);
    (void)!system(cmd);
    assert(!tool_seen && other_seen);
    puts("proc-host-tools ok");
    return 0;
}
