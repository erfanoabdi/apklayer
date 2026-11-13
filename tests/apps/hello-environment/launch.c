/* Native process launches from app code (arm64: bionic_translation 0069 on an arm64 host, the translated guest libc on x86_64):
 * system, popen/pclose/fclose and posix_spawn[p] run Android's /system/bin/sh (mksh: KSH_VERSION) with the app's environment. */
#include <jni.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

static char failures[1024];
static void fail(const char *what) {
    size_t used = strlen(failures);
    snprintf(failures + used, sizeof failures - used, "%s%s", used ? "; " : "", what);
}
#define EXPECT(c, what) do { if (!(c)) fail(what); } while (0)
static int exited(int s, int n) { return WIFEXITED(s) && WEXITSTATUS(s) == n; }  /* evaluates the call once */
#define EXITED(s, n) exited((s), (n))
#define ANDROID_CHILD "test \"$APKLY_LAUNCH|${APKLAYER_INSTANCE-unset}|${KSH_VERSION:+mksh}\" = \"guest-launch|unset|mksh\""

/* posix_spawn is API 28, this library is built for 26: from libc at run time, as such apps do */
typedef posix_spawn_file_actions_t actions_t;
typedef posix_spawnattr_t attr_t;
static int (*spawn_fn)(pid_t *, const char *, const actions_t *, const attr_t *, char *const[], char *const[]);
static int (*spawnp_fn)(pid_t *, const char *, const actions_t *, const attr_t *, char *const[], char *const[]);
static int (*actions_init)(actions_t *), (*actions_destroy)(actions_t *), (*add_dup2)(actions_t *, int, int), (*add_close)(actions_t *, int);
static int (*add_open)(actions_t *, int, const char *, int, mode_t);
static int (*attr_init)(attr_t *), (*attr_destroy)(attr_t *), (*set_mask)(attr_t *, const sigset_t *), (*set_flags)(attr_t *, short);
static int resolve(void) {
    void *libc = dlopen("libc.so", RTLD_NOW);
    if (!libc) return 0;
    spawn_fn = dlsym(libc, "posix_spawn"); spawnp_fn = dlsym(libc, "posix_spawnp");
    actions_init = dlsym(libc, "posix_spawn_file_actions_init"); actions_destroy = dlsym(libc, "posix_spawn_file_actions_destroy");
    add_dup2 = dlsym(libc, "posix_spawn_file_actions_adddup2"); add_close = dlsym(libc, "posix_spawn_file_actions_addclose");
    add_open = dlsym(libc, "posix_spawn_file_actions_addopen");
    attr_init = dlsym(libc, "posix_spawnattr_init"); attr_destroy = dlsym(libc, "posix_spawnattr_destroy");
    set_mask = dlsym(libc, "posix_spawnattr_setsigmask"); set_flags = dlsym(libc, "posix_spawnattr_setflags");
    return spawn_fn && spawnp_fn && actions_init && actions_destroy && add_dup2 && add_close && add_open && attr_init && attr_destroy
        && set_mask && set_flags;
}

static int waited(pid_t pid) { int status = -1; while (waitpid(pid, &status, 0) == -1 && errno == EINTR) {} return status; }

static void spawn_checks(const char *dir) {
    if (!resolve()) { fail("posix_spawn functions missing from libc.so"); return; }
    char out[256], path[512];
    snprintf(path, sizeof path, "%s/launch-out", dir);
    int fds[2];
    if (pipe(fds)) { fail("pipe"); return; }
    actions_t actions;
    attr_t attr;
    sigset_t mask;
    sigemptyset(&mask);
    sigaddset(&mask, SIGUSR1);
    EXPECT(!actions_init(&actions) && !attr_init(&attr), "spawn object init");
    EXPECT(!add_dup2(&actions, fds[1], 1) && !add_close(&actions, fds[0])
           && !add_open(&actions, 2, path, O_WRONLY | O_CREAT | O_TRUNC, 0600), "file actions");
    char *argv[] = {"sh", "-c", ANDROID_CHILD " && echo \"$APKLY_LAUNCH\" && echo err >&2 && exit 6", NULL};
    pid_t pid = -1;
    EXPECT(!spawn_fn(&pid, "/system/bin/sh", &actions, NULL, argv, NULL), "posix_spawn");
    close(fds[1]);
    ssize_t n = read(fds[0], out, sizeof out - 1);
    out[n > 0 ? n : 0] = 0;
    close(fds[0]);
    EXPECT(!strcmp(out, "guest-launch\n"), "posix_spawn child output");
    EXPECT(pid > 0 && EXITED(waited(pid), 6), "posix_spawn status");
    FILE *err = fopen(path, "r");
    EXPECT(err && fgets(out, sizeof out, err) && !strcmp(out, "err\n"), "posix_spawn addopen in app data");
    if (err) fclose(err);
    unlink(path);
    actions_destroy(&actions);
    /* POSIX_SPAWN_SETSIGMASK: a SIGUSR1 sent at once stays pending in the program (toybox cat on a pipe) until it exits normally */
    EXPECT(!set_mask(&attr, &mask) && !set_flags(&attr, POSIX_SPAWN_SETSIGMASK) && !pipe(fds) && !actions_init(&actions)
           && !add_dup2(&actions, fds[0], 0) && !add_close(&actions, fds[1]), "attributes");
    char *cat[] = {"toybox", "cat", NULL};
    EXPECT(!spawn_fn(&pid, "/system/bin/toybox", &actions, &attr, cat, NULL), "posix_spawn toybox");
    close(fds[0]);
    if (pid > 0) kill(pid, SIGUSR1);
    close(fds[1]);
    int toybox = pid > 0 ? waited(pid) : -1;
    if (!EXITED(toybox, 0)) { snprintf(out, sizeof out, "posix_spawn SETSIGMASK: toybox with SIGUSR1 pending ended %#x", toybox); fail(out); }
    actions_destroy(&actions);
    attr_destroy(&attr);
    char *search[] = {"sh", "-c", ANDROID_CHILD, NULL};
    EXPECT(!spawnp_fn(&pid, "sh", NULL, NULL, search, NULL) && EXITED(waited(pid), 0), "posix_spawnp guest PATH");
    char *missing[] = {"missing-launch-tool", NULL};
    EXPECT(!spawnp_fn(&pid, "missing-launch-tool", NULL, NULL, missing, NULL) && EXITED(waited(pid), 127), "posix_spawnp missing: 127");
    EXPECT(!spawn_fn(&pid, "/system/bin/missing-launch-tool", NULL, NULL, missing, NULL) && EXITED(waited(pid), 127),
           "posix_spawn missing: 127");
}

/* A #!/bin/sh script is run by Android's shell (/bin is /system/bin on Android), through each launch form: the PATH search of the exec
 * family, an exec of its path and posix_spawn. Its line goes to the pipe, read here. */
static void script_run(const char *label, const char *dir, const char *script, int how) {
    int fds[2];
    char out[64] = "", path[512];
    snprintf(path, sizeof path, "%s/%s", dir, script);
    if (pipe(fds)) { fail("pipe"); return; }
    char *argv[] = {(char *)script, NULL};
    pid_t pid = -1;
    if (how == 2) {
        actions_t actions;
        if (!actions_init(&actions) && !add_dup2(&actions, fds[1], 1) && !add_close(&actions, fds[0])
                && spawn_fn(&pid, path, &actions, NULL, argv, NULL)) pid = -1;
        actions_destroy(&actions);
    } else if ((pid = fork()) == 0) {
        dup2(fds[1], 1);
        close(fds[0]);
        if (how == 0) { setenv("PATH", dir, 1); execvp(script, argv); }
        else execv(path, argv);
        _exit(127);
    }
    close(fds[1]);
    ssize_t n = pid > 0 ? read(fds[0], out, sizeof out - 1) : -1;
    out[n > 0 ? n : 0] = 0;
    close(fds[0]);
    int status = pid > 0 ? waited(pid) : -1;
    if (strcmp(out, "Smksh\n") || !EXITED(status, 0)) {
        char what[160];
        snprintf(what, sizeof what, "%s: #!/bin/sh script printed '%.20s' status %#x (want Smksh: Android's shell)", label, out, status);
        fail(what);
    }
}

static void script_checks(const char *dir) {
    char path[512];
    snprintf(path, sizeof path, "%s/binsh-script", dir);
    FILE *f = fopen(path, "w");
    if (!f) { fail("script create"); return; }
    fputs("#!/bin/sh\necho \"S${KSH_VERSION:+mksh}\"\n", f);
    fclose(f);
    chmod(path, 0700);
    script_run("execvp PATH search", dir, "binsh-script", 0);
    script_run("execv path", dir, "binsh-script", 1);
    if (spawn_fn) script_run("posix_spawn path", dir, "binsh-script", 2);
    unlink(path);
}

JNIEXPORT jstring JNICALL Java_hello_environment_Main_launchCheck(JNIEnv *env, jclass cls, jstring jdir)
{
    (void)cls;
    failures[0] = 0;
    const char *dir = (*env)->GetStringUTFChars(env, jdir, NULL);
    if (!dir) return NULL;
    setenv("APKLY_LAUNCH", "guest-launch", 1);
    const char *volatile none = NULL;
    EXPECT(system(none) == 1, "system(NULL)");
    EXPECT(EXITED(system(ANDROID_CHILD), 0), "system: Android shell and environment");
    EXPECT(EXITED(system("exit 3"), 3), "system status");
    char line[128];
    FILE *p = popen("echo \"$APKLY_LAUNCH|${KSH_VERSION:+mksh}\"", "r");
    EXPECT(p && fgets(line, sizeof line, p) && !strcmp(line, "guest-launch|mksh\n"), "popen r: Android shell and environment");
    EXPECT(p && EXITED(pclose(p), 0), "pclose status");
    p = popen("read -r line; test \"$line\" = hello && exit 4", "w");
    EXPECT(p && fputs("hello\n", p) >= 0 && EXITED(fclose(p), 4), "popen w: fclose waits");
    p = popen("read -r line; echo \"got:$line\"", "r+");
    EXPECT(p && fputs("both\n", p) >= 0 && !fflush(p) && fgets(line, sizeof line, p) && !strcmp(line, "got:both\n"), "popen r+");
    EXPECT(p && EXITED(pclose(p), 0), "popen r+ status");
    p = popen("exit 0", "re");
    EXPECT(p && (fcntl(fileno(p), F_GETFD) & FD_CLOEXEC) && EXITED(pclose(p), 0), "popen e: close-on-exec");
    spawn_checks(dir);
    script_checks(dir);
    unsetenv("APKLY_LAUNCH");
    (*env)->ReleaseStringUTFChars(env, jdir, dir);
    return failures[0] ? (*env)->NewStringUTF(env, failures) : NULL;
}
