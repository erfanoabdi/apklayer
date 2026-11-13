/* bionic_translation 0069: guest system, popen/pclose/fclose and posix_spawn[p] (bionic's attribute and file-action objects) launch
 * /system/bin/sh and guest programs with the guest environment and PATH through the preload's path layer. The Android root here has
 * a /system/bin/sh that marks its children (ANDROID_SH=1) and then runs the host shell. argv[1]: libc_bio. */
#define _GNU_SOURCE
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <ftw.h>
#include <limits.h>
#include <pthread.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#define CHECK(c) do { if (!(c)) { fprintf(stderr, "guest-spawn: line %d: %s\n", __LINE__, #c); abort(); } } while (0)
static int exited(int s, int n) { return WIFEXITED(s) && WEXITSTATUS(s) == n; }  /* evaluates the call once */
#define EXITED(s, n) exited((s), (n))

typedef void *handle;  /* bionic's posix_spawnattr_t and posix_spawn_file_actions_t: one pointer */
enum { B_SETPGROUP = 2, B_SETSIGDEF = 4, B_SETSIGMASK = 8, B_CLOEXEC_DEFAULT = 256 };  /* bionic spawn.h */
static int (*b_system)(const char *);
static FILE *(*b_popen)(const char *, const char *);
static int (*b_pclose)(FILE *), (*b_fclose)(FILE *);
static int (*b_spawn)(pid_t *, const char *, const handle *, const handle *, char *const[], char *const[]);
static int (*b_spawnp)(pid_t *, const char *, const handle *, const handle *, char *const[], char *const[]);
static int (*a_init)(handle *), (*a_destroy)(handle *), (*a_setflags)(handle *, short), (*a_getflags)(const handle *, short *);
static int (*a_setpgroup)(handle *, pid_t), (*a_setmask64)(handle *, const uint64_t *), (*a_getmask64)(const handle *, uint64_t *);
static int (*a_setdef64)(handle *, const uint64_t *);
static int (*f_init)(handle *), (*f_destroy)(handle *), (*f_open)(handle *, int, const char *, int, mode_t), (*f_close)(handle *, int);
static int (*f_dup2)(handle *, int, int), (*f_chdir)(handle *, const char *);
static char root[PATH_MAX];

static void *need(void *lib, const char *name) { void *f = dlsym(lib, name); if (!f) fprintf(stderr, "missing %s\n", name); CHECK(f); return f; }
static void write_file(const char *rel, const char *text, mode_t mode) {
    char path[PATH_MAX]; snprintf(path, sizeof path, "%s/%s", root, rel);
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, mode); CHECK(fd >= 0);
    CHECK(write(fd, text, strlen(text)) == (ssize_t)strlen(text)); CHECK(!close(fd));
}
static void read_file(const char *rel, char *out, size_t size) {
    char path[PATH_MAX]; snprintf(path, sizeof path, "%s/%s", root, rel);
    int fd = open(path, O_RDONLY | O_CLOEXEC); CHECK(fd >= 0);
    ssize_t n = read(fd, out, size - 1); CHECK(n >= 0); out[n] = 0; close(fd);
}
static int remove_entry(const char *path, const struct stat *st, int type, struct FTW *ftw) { (void)st; (void)type; (void)ftw; return remove(path); }
static int wait_child(pid_t pid) { int status; CHECK(waitpid(pid, &status, 0) == pid); return status; }
static int spawn_sh(handle *actions, handle *attr, const char *cmd, char *const env[]) {
    char *argv[] = {"sh", "-c", (char *)cmd, NULL};
    pid_t pid = -1;
    CHECK(b_spawn(&pid, "/system/bin/sh", actions, attr, argv, env) == 0 && pid > 0);
    return wait_child(pid);
}

static volatile sig_atomic_t interrupted;
static void on_int(int sig) { (void)sig; interrupted = 1; }
static void *system_thread(void *arg) {
    for (int i = 0; i < 20; i++) CHECK(EXITED(b_system("exit 2"), 2));
    return arg;
}
static void *popen_thread(void *arg) {
    for (int i = 0; i < 10; i++) {
        char cmd[64], line[64], want[32];
        snprintf(cmd, sizeof cmd, "echo t%ld-%d", (long)(intptr_t)arg, i);
        snprintf(want, sizeof want, "t%ld-%d\n", (long)(intptr_t)arg, i);
        FILE *p = b_popen(cmd, "r"); CHECK(p);
        CHECK(fgets(line, sizeof line, p) && !strcmp(line, want));
        CHECK(EXITED((i & 1 ? b_fclose : b_pclose)(p), 0));
    }
    return NULL;
}

static void system_checks(void) {
    CHECK(b_system(NULL) == 1);
    CHECK(EXITED(b_system("exit 3"), 3));
    CHECK(EXITED(b_system("test \"$HOME|$CHILD_VALUE|$ANDROID_SH|${ENV_HOST_ONLY-unset}\" = \"/guest-home|guest|1|unset\""), 0));
    /* the caller ignores SIGINT and SIGQUIT while it waits, the shell has them at their default; the caller's handler stays */
    struct sigaction sa = {0}, now;
    sa.sa_handler = on_int; sigemptyset(&sa.sa_mask); CHECK(!sigaction(SIGINT, &sa, NULL));
    CHECK(EXITED(b_system("kill -INT $PPID; exit 4"), 4) && !interrupted);
    int status = b_system("kill -INT $$; exit 5");
    CHECK(WIFSIGNALED(status) && WTERMSIG(status) == SIGINT);
    CHECK(!sigaction(SIGINT, NULL, &now) && now.sa_handler == on_int);
    sa.sa_handler = SIG_IGN; CHECK(!sigaction(SIGINT, &sa, NULL));
    CHECK(EXITED(b_system("kill -INT $$; exit 7"), 7));  /* ignored before: ignored in the shell */
    sa.sa_handler = on_int; CHECK(!sigaction(SIGINT, &sa, NULL));
    /* the shell gets the caller's mask (SIGUSR1 blocked), the caller's mask comes back (SIGCHLD unblocked) */
    sigset_t usr1, before, after;
    sigemptyset(&usr1); sigaddset(&usr1, SIGUSR1); CHECK(!pthread_sigmask(SIG_BLOCK, &usr1, &before));
    CHECK(EXITED(b_system("kill -USR1 $$; exit 6"), 6));
    CHECK(!pthread_sigmask(SIG_SETMASK, &before, &after) && sigismember(&after, SIGUSR1) && !sigismember(&after, SIGCHLD));
    /* concurrent calls: the last one restores the caller's SIGINT handler, not SIG_IGN */
    pthread_t threads[4];
    for (int i = 0; i < 4; i++) CHECK(!pthread_create(&threads[i], NULL, system_thread, NULL));
    for (int i = 0; i < 4; i++) CHECK(!pthread_join(threads[i], NULL));
    CHECK(!sigaction(SIGINT, NULL, &now) && now.sa_handler == on_int);
}

static void popen_checks(void) {
    char line[256];
    FILE *p = b_popen("echo \"$HOME|$CHILD_VALUE|$ANDROID_SH|${ENV_HOST_ONLY-unset}\"", "r"); CHECK(p);
    CHECK(fcntl(fileno(p), F_GETFD) & FD_CLOEXEC);
    CHECK(fgets(line, sizeof line, p) && !strcmp(line, "/guest-home|guest|1|unset\n"));
    CHECK(EXITED(b_pclose(p), 0));
    p = b_popen("read -r line; test \"$line\" = hello", "w"); CHECK(p);
    CHECK(fputs("hello\n", p) >= 0 && EXITED(b_pclose(p), 0));
    p = b_popen("exit 4", "re"); CHECK(p && (fcntl(fileno(p), F_GETFD) & FD_CLOEXEC));
    CHECK(EXITED(b_fclose(p), 4));  /* fclose waits as pclose (bionic's alias) */
    p = b_popen("read -r line; echo \"got:$line\"", "r+"); CHECK(p);
    CHECK(fputs("both\n", p) >= 0 && !fflush(p) && fgets(line, sizeof line, p) && !strcmp(line, "got:both\n"));
    CHECK(EXITED(b_pclose(p), 0));
    p = b_popen("exit 0", "we"); CHECK(p && EXITED(b_pclose(p), 0));
    /* a second popen's child does not hold the first's pipe: the first sees its end */
    FILE *first = b_popen("while read -r line; do :; done", "w"), *second = b_popen("read -r line", "w"); CHECK(first && second);
    CHECK(EXITED(b_pclose(first), 0));
    CHECK(fputs("x\n", second) >= 0 && EXITED(b_pclose(second), 0));
    FILE *plain = fopen("/dev/null", "r"); CHECK(plain && b_pclose(plain) == 0);  /* not a popen stream: a plain close */
    pthread_t threads[4];
    for (intptr_t i = 0; i < 4; i++) CHECK(!pthread_create(&threads[i], NULL, popen_thread, (void *)i));
    for (int i = 0; i < 4; i++) CHECK(!pthread_join(threads[i], NULL));
}

static void spawn_checks(const char *self) {
    handle attr, actions;
    short flags;
    uint64_t mask = 0, got = 0;
    char out[256], fdtext[16];
    CHECK(!a_init(&attr) && attr);
    CHECK(a_setflags(&attr, 512) == EINVAL);
    CHECK(!a_setflags(&attr, B_SETSIGMASK) && !a_getflags(&attr, &flags) && flags == B_SETSIGMASK);
    mask = 1ull << (SIGUSR1 - 1);
    CHECK(!a_setmask64(&attr, &mask) && !a_getmask64(&attr, &got) && got == mask);
    CHECK(EXITED(spawn_sh(NULL, &attr, "kill -USR1 $$; exit 6", NULL), 6));  /* SETSIGMASK: SIGUSR1 blocked */
    struct sigaction ign = {0}; ign.sa_handler = SIG_IGN; sigemptyset(&ign.sa_mask); CHECK(!sigaction(SIGUSR2, &ign, NULL));
    mask = 1ull << (SIGUSR2 - 1);
    CHECK(!a_setflags(&attr, 0));
    CHECK(EXITED(spawn_sh(NULL, &attr, "kill -USR2 $$; exit 8", NULL), 8));  /* inherited SIG_IGN */
    CHECK(!a_setflags(&attr, B_SETSIGDEF | B_SETPGROUP) && !a_setdef64(&attr, &mask) && !a_setpgroup(&attr, 0));
    int status = spawn_sh(NULL, &attr, "kill -USR2 $$; exit 8", NULL);
    CHECK(WIFSIGNALED(status) && WTERMSIG(status) == SIGUSR2);  /* SETSIGDEF */
    CHECK(EXITED(spawn_sh(NULL, &attr, "read -r a b c d pgrp rest </proc/$$/stat; test \"$pgrp\" = \"$$\"", NULL), 0));  /* SETPGROUP 0 */
    /* file actions: mapped open and chdir (Android paths in the instance root), dup2 and close */
    int pipefd[2]; CHECK(!pipe(pipefd));
    CHECK(!f_init(&actions) && actions);
    CHECK(f_open(&actions, -1, "/x", O_RDONLY, 0) == EBADF && f_close(&actions, -1) == EBADF);
    CHECK(!f_dup2(&actions, pipefd[1], 1) && !f_close(&actions, pipefd[0]) && !f_close(&actions, pipefd[1]));
    CHECK(!f_open(&actions, 2, "/data/spawn-out", O_WRONLY | O_TRUNC, 0) && !f_chdir(&actions, "/data"));
    CHECK(EXITED(spawn_sh(&actions, NULL, "echo \"$HOME|$ANDROID_SH\"; echo err >&2; echo here >chdir-out", NULL), 0));
    close(pipefd[1]);
    ssize_t n = read(pipefd[0], out, sizeof out - 1); CHECK(n > 0); out[n] = 0; close(pipefd[0]);
    CHECK(!strcmp(out, "/guest-home|1\n"));
    read_file("data/spawn-out", out, sizeof out); CHECK(!strcmp(out, "err\n"));
    read_file("data/chdir-out", out, sizeof out); CHECK(!strcmp(out, "here\n"));
    CHECK(!f_destroy(&actions) && !actions);
    /* POSIX_SPAWN_CLOEXEC_DEFAULT: inherited fds close, what the actions make stays */
    int keep = open("/dev/null", O_RDONLY); CHECK(keep > 2);
    snprintf(fdtext, sizeof fdtext, "%d", keep);
    char cmd[160]; snprintf(cmd, sizeof cmd, "test -e /proc/$$/fd/%s && exit 1; test -e /proc/$$/fd/9", fdtext);
    CHECK(!a_setflags(&attr, B_CLOEXEC_DEFAULT) && !a_getflags(&attr, &flags) && flags == B_CLOEXEC_DEFAULT);
    CHECK(!f_init(&actions) && !f_dup2(&actions, keep, 9));
    CHECK(EXITED(spawn_sh(&actions, &attr, cmd, NULL), 0));
    CHECK(!a_setflags(&attr, 0));
    snprintf(cmd, sizeof cmd, "test -e /proc/$$/fd/%s", fdtext);
    CHECK(EXITED(spawn_sh(NULL, &attr, cmd, NULL), 0));  /* without it the fd is inherited */
    close(keep); f_destroy(&actions); CHECK(!a_destroy(&attr) && !attr);
    /* explicit environment; /bin is /system/bin; failures after the fork are status 127 */
    char *env[] = {"HOME=/explicit-home", "CHILD_VALUE=explicit", NULL};
    CHECK(EXITED(spawn_sh(NULL, NULL, "test \"$HOME|$CHILD_VALUE|$ANDROID_SH\" = \"/explicit-home|explicit|1\"", env), 0));
    char *bin_sh[] = {"sh", "-c", "test \"$ANDROID_SH\" = 1", NULL};
    pid_t pid = -1;
    CHECK(!b_spawn(&pid, "/bin/sh", NULL, NULL, bin_sh, NULL) && EXITED(wait_child(pid), 0));
    CHECK(!b_spawn(&pid, "/system/bin/missing", NULL, NULL, bin_sh, NULL) && EXITED(wait_child(pid), 127));
    CHECK(!b_spawn(&pid, "/system/bin/spawn-script", NULL, NULL, bin_sh, NULL) && EXITED(wait_child(pid), 127));  /* no #!: execve */
    /* posix_spawnp: the parent's guest PATH (not the child's, not the host's), mapped; a file without #! runs with the shell */
    char *child[] = {"spawn-tool", "--child", "/guest-home", "/no-such-dir:/system/bin", "guest", NULL};
    CHECK(!b_spawnp(&pid, "spawn-tool", NULL, NULL, child, NULL) && EXITED(wait_child(pid), 0));
    char *explicit_child[] = {"spawn-tool", "--child", "/explicit-home", "/unused-child-path", "explicit", NULL};
    char *explicit_env[] = {"HOME=/explicit-home", "PATH=/unused-child-path", "CHILD_VALUE=explicit", NULL};
    CHECK(!b_spawnp(&pid, "spawn-tool", NULL, NULL, explicit_child, explicit_env) && EXITED(wait_child(pid), 0));
    char *script[] = {"spawn-script", NULL};
    CHECK(!b_spawnp(&pid, "spawn-script", NULL, NULL, script, NULL) && EXITED(wait_child(pid), 9));
    CHECK(!b_spawnp(&pid, "missing-tool", NULL, NULL, script, NULL) && EXITED(wait_child(pid), 127));
    CHECK(!b_spawnp(&pid, "", NULL, NULL, script, NULL) && EXITED(wait_child(pid), 127));
    (void)self;
}

int main(int argc, char **argv)
{
    if (argc == 5 && !strcmp(argv[1], "--child")) {  /* a guest program: the environment it was given */
        CHECK(getenv("HOME") && !strcmp(getenv("HOME"), argv[2]));
        CHECK(getenv("PATH") && !strcmp(getenv("PATH"), argv[3]));
        CHECK(getenv("CHILD_VALUE") && !strcmp(getenv("CHILD_VALUE"), argv[4]));
        CHECK(!getenv("ENV_HOST_ONLY"));
        return 0;
    }
    CHECK(argc == 2);
    if (!getenv("APKLAYER_ANDROID_ROOT")) {  /* the path layer reads its root once, when it loads: run again with it set */
        char tmpl[] = "/tmp/apklayer-spawn-XXXXXX", exe[PATH_MAX];
        CHECK(mkdtemp(tmpl));
        snprintf(root, sizeof root, "%s", tmpl);
        CHECK(!mkdir(strcat(strcpy(exe, root), "/system"), 0755) && !mkdir(strcat(exe, "/bin"), 0755));
        CHECK(!mkdir(strcat(strcpy(exe, root), "/data"), 0755));
        char shell[PATH_MAX + 160];  /* Android's shell sees the instance's /system: a script it runs by name, there */
        snprintf(shell, sizeof shell, "#!/bin/sh\nANDROID_SH=1; export ANDROID_SH\n"
                 "case \"$1\" in /system/*) f=\"$1\"; shift; exec /bin/sh \"%s$f\" \"$@\";; esac\nexec /bin/sh \"$@\"\n", root);
        write_file("system/bin/sh", shell, 0755);
        write_file("system/bin/spawn-script", "exit 9\n", 0755);
        write_file("data/spawn-out", "", 0644);
        ssize_t n = readlink("/proc/self/exe", exe, sizeof exe - 1); CHECK(n > 0); exe[n] = 0;
        char tool[PATH_MAX]; snprintf(tool, sizeof tool, "%s/system/bin/spawn-tool", root); CHECK(!symlink(exe, tool));
        setenv("APKLAYER_ANDROID_ROOT", root, 1);
        execv("/proc/self/exe", argv);
        return 1;
    }
    snprintf(root, sizeof root, "%s", getenv("APKLAYER_ANDROID_ROOT"));
    void *libc = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL); CHECK(libc);
    void (*set_overrides)(bool (*)(char **)) = need(libc, "libc_bio_set_apply_path_overrides_func");
    set_overrides((bool (*)(char **))need(RTLD_DEFAULT, "apklayer_apply_path_overrides"));
    b_system = need(libc, "bionic_system"); b_popen = need(libc, "bionic_popen");
    b_pclose = need(libc, "bionic_pclose"); b_fclose = need(libc, "bionic_fclose");
    b_spawn = need(libc, "bionic_posix_spawn"); b_spawnp = need(libc, "bionic_posix_spawnp");
    a_init = need(libc, "bionic_posix_spawnattr_init"); a_destroy = need(libc, "bionic_posix_spawnattr_destroy");
    a_setflags = need(libc, "bionic_posix_spawnattr_setflags"); a_getflags = need(libc, "bionic_posix_spawnattr_getflags");
    a_setpgroup = need(libc, "bionic_posix_spawnattr_setpgroup");
    a_setmask64 = need(libc, "bionic_posix_spawnattr_setsigmask64"); a_getmask64 = need(libc, "bionic_posix_spawnattr_getsigmask64");
    a_setdef64 = need(libc, "bionic_posix_spawnattr_setsigdefault64");
    f_init = need(libc, "bionic_posix_spawn_file_actions_init"); f_destroy = need(libc, "bionic_posix_spawn_file_actions_destroy");
    f_open = need(libc, "bionic_posix_spawn_file_actions_addopen"); f_close = need(libc, "bionic_posix_spawn_file_actions_addclose");
    f_dup2 = need(libc, "bionic_posix_spawn_file_actions_adddup2"); f_chdir = need(libc, "bionic_posix_spawn_file_actions_addchdir_np");
    int (*clear)(void) = need(libc, "bionic_clearenv"), (*put)(char *) = need(libc, "bionic_putenv");
    int (*set)(const char *, const char *, int) = need(libc, "bionic_setenv");
    char home[] = "HOME=/guest-home", value[] = "CHILD_VALUE=guest";
    CHECK(!clear() && !put(home) && !put(value) && !set("PATH", "/no-such-dir:/system/bin", 1));  /* Termux JNI sequence */
    /* 0070: a query of signal 32 (glibc's own) succeeds, as bionic's posix_spawn child makes it for every signal */
    int (*b_sigaction)(int, const void *, void *) = need(libc, "bionic_sigaction");
    long old[16] = {1};
    CHECK(b_sigaction(32, NULL, old) == 0 && old[0] == 0);
    system_checks();
    popen_checks();
    spawn_checks(argv[0]);
    CHECK(!nftw(root, remove_entry, 16, FTW_DEPTH | FTW_PHYS));
    puts("PASS native guest spawn: system signals/status, popen r/w/r+/e with pclose and fclose, concurrent popen/system, "
         "posix_spawn attributes/file actions/CLOEXEC_DEFAULT, guest environment and PATH, /system/bin/sh, status 127");
}
