/* LD_PRELOAD part of the runtime: an Android program an app runs (an ELF whose interpreter is /system/bin/linker64, e.g. a game
 * generator shipped as lib*.so and started with ProcessBuilder) runs through btload, as Android's linker would run it:
 *   btload <search path> <program> --exec argv...
 * The program's own LD_LIBRARY_PATH (Android's search path for it, set by the app) goes first in btload's search path, then the
 * device libraries; the runtime's variables (host LD_LIBRARY_PATH, LD_PRELOAD...) are the ones this process started with (saved at
 * load: the JDK's process helper replaces environ with the child's environment before execvp), so btload itself starts. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <spawn.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <limits.h>

extern char **environ;
#define REAL(name) ({ static void *f_; if (!f_) f_ = dlsym(RTLD_NEXT, #name); f_; })

/* The runtime's variables, and the path layer's (paths.c read_env): an app that builds its own environment for its programs (Termux) drops them, and its programs' /data, /system and
 * binder devices were then the shared root's or the host's (Termux's mkdir -p wrote into the native android-root). LD_PRELOAD stays second (save_runtime_env). */
static const char *const runtime_vars[] = {"LD_LIBRARY_PATH", "LD_PRELOAD", "XDG_DATA_DIRS", "APKLAYER_NATIVE", "BIONIC_LD_LIBRARY_PATH", "APKLAYER_HOST_LIBS",
    "APKLAYER_ANDROID_ROOT", "APKLAYER_INSTANCE", "APKLAYER_INSTANCE_ROOT", "APKLAYER_STORAGE", "APKLAYER_DATA", "APKLAYER_HOST_BINDER", "APKLAYER_HOST_HWBINDER", NULL};

#define NVARS (sizeof runtime_vars / sizeof *runtime_vars - 1)
static char *started[NVARS];  /* "NAME=value" of each runtime variable at load, or NULL */

struct command { char *btload, *libpath, **argv, **envp, *environment_fd; int fd; };
extern int apklayer_pack_guest_environment(char *const envp[]);

__attribute__((constructor)) static void save_runtime_env(void)
{
    for (size_t i = 0; i < NVARS; i++)
        for (char **e = environ; e && *e; e++)
            if (!strncmp(*e, runtime_vars[i], strlen(runtime_vars[i])) && (*e)[strlen(runtime_vars[i])] == '=') { started[i] = strdup(*e); break; }
    /* LD_PRELOAD without HotSpot's libjsig (cli/env.cpp): it is the JVM's, and in a program it warns at each signal() ("signal chaining ... deprecated") */
    char *preload = started[1], *kept = preload ? strdup(preload) : NULL, *save;
    if (!kept) return;
    char *out = strchr(kept, '=') + 1;
    *out = 0;
    for (char *t = strtok_r(strchr(preload, '=') + 1, ":", &save); t; t = strtok_r(NULL, ":", &save)) {
        size_t n = strlen(t);
        if (n >= 10 && !strcmp(t + n - 10, "libjsig.so")) continue;
        if (out[-1] != '=') *out++ = ':';
        out = stpcpy(out, t);
    }
    free(preload);
    started[1] = kept;
}

static const char *runtime_value(const char *name)
{
    for (size_t i = 0; i < NVARS; i++)
        if (started[i] && !strcmp(runtime_vars[i], name)) return started[i] + strlen(name) + 1;
    return NULL;
}

static bool android_program(const char *path)
{
    static const char linker[] = "/system/bin/linker";
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    bool found = false;
    Elf64_Ehdr eh;
    if (pread(fd, &eh, sizeof eh, 0) == sizeof eh && !memcmp(eh.e_ident, ELFMAG, SELFMAG) && eh.e_ident[EI_CLASS] == ELFCLASS64)
        for (int i = 0; i < eh.e_phnum && !found; i++) {
            Elf64_Phdr ph;
            char interp[sizeof linker - 1];
            if (pread(fd, &ph, sizeof ph, eh.e_phoff + (off_t)i * eh.e_phentsize) != sizeof ph) break;
            found = ph.p_type == PT_INTERP && pread(fd, interp, sizeof interp, ph.p_offset) == sizeof interp && !memcmp(interp, linker, sizeof interp);
        }
    close(fd);
    return found;
}

static const char *value(char *const *env, const char *name)
{
    size_t n = strlen(name);
    for (; env && *env; env++)
        if (!strncmp(*env, name, n) && (*env)[n] == '=') return *env + n + 1;
    return NULL;
}

static bool runtime_var(const char *entry)
{
    if (!strncmp(entry, "APKLAYER_GUEST_ENV_FD=", 22)) return true;
    for (const char *const *v = runtime_vars; *v; v++)
        if (!strncmp(entry, *v, strlen(*v)) && entry[strlen(*v)] == '=') return true;
    return false;
}

static void release(struct command *c) {
    if (c->fd >= 0) close(c->fd);
    free(c->environment_fd); free(c->btload); free(c->libpath); free(c->argv); free(c->envp);
}

static int rewrite(const char *path, char *const argv[], char *const envp[], char *const guest_envp[], struct command *c)
{
    const char *native = runtime_value("APKLAYER_NATIVE"), *libs = runtime_value("BIONIC_LD_LIBRARY_PATH");
    if (!path || !native || !libs || !android_program(path)) return false;
    const char *own = runtime_value("LD_LIBRARY_PATH"), *asked = value(envp, "LD_LIBRARY_PATH");
    bool app_path = asked && *asked && (!own || strcmp(asked, own));
    size_t argc = 0, envc = 0, o = 0;
    while (argv && argv[argc]) argc++;
    while (envp && envp[envc]) envc++;
    *c = (struct command){.fd = -1};
    if (asprintf(&c->btload, "%s/bin/btload", native) < 0) c->btload = NULL;
    if (asprintf(&c->libpath, "%s%s%s", app_path ? asked : "", app_path ? ":" : "", libs) < 0) c->libpath = NULL;
    c->argv = calloc(argc + 6, sizeof(char *));
    c->envp = calloc(envc + NVARS + 2, sizeof(char *));
    if (!c->btload || !c->libpath || !c->argv || !c->envp) { release(c); errno = ENOMEM; return -1; }
    c->fd = apklayer_pack_guest_environment(guest_envp);
    if (c->fd < 0 || asprintf(&c->environment_fd, "APKLAYER_GUEST_ENV_FD=%d", c->fd) < 0) {
        int error = errno; release(c); errno = error; return -1;
    }
    char **a = c->argv;
    *a++ = c->btload, *a++ = c->libpath, *a++ = (char *)path, *a++ = "--exec", *a++ = argc ? argv[0] : (char *)path;
    for (size_t i = 1; i < argc; i++) *a++ = argv[i];
    for (size_t i = 0; i < envc; i++)
        if (!runtime_var(envp[i])) c->envp[o++] = envp[i];
    for (size_t i = 0; i < NVARS; i++)
        if (started[i]) c->envp[o++] = started[i];
    c->envp[o++] = c->environment_fd;
    return true;
}

static int exec_command(struct command *c)
{
    int r = ((int (*)(const char *, char *const[], char *const[]))REAL(execve))(c->btload, c->argv, c->envp), e = errno;
    release(c);
    errno = e;
    return r;
}

/* Every exec: its path through the path layer (an app's /data/data/<pkg>/..., /storage/...: paths.c), and the app's own-data mapping
 * (APKLAYER_OWN_DATA, paths.c) in the program's environment in place of any it carries, so the program maps the app's paths as this process does. */
bool apklayer_apply_path_overrides(char **path);
const char *apklayer_own_data_env(void);

struct target { char *path; bool mapped; char **envp, **made; };

static void target(struct target *t, const char *path, char *const envp[])
{
    t->path = (char *)path;
    t->mapped = path && path[0] == '/' && apklayer_apply_path_overrides(&t->path);
    t->envp = (char **)envp, t->made = NULL;
    const char *own = apklayer_own_data_env();
    size_t n = 0, o = 0;
    while (own && envp && envp[n]) n++;
    if (!own || !(t->made = calloc(n + 2, sizeof(char *)))) return;
    for (size_t i = 0; i < n; i++)
        if (strncmp(envp[i], "APKLAYER_OWN_DATA=", 18)) t->made[o++] = envp[i];
    t->made[o] = (char *)own;
    t->envp = t->made;
}

static int target_done(struct target *t, int r)
{
    int e = errno;
    if (t->mapped) free(t->path);
    free(t->made);
    errno = e;
    return r;
}

/* Real candidate execs share mapping, translator, permission checks and script handling. */
int apklayer_exec_search_guest(const char *file, char *const argv[], char *const envp[], const char *search, bool inherited)
{
    if (!file || !search || !argv || !argv[0] || strchr(file, '/')) { errno = EINVAL; return -1; }
    if (!*file) { errno = ENOENT; return -1; }
    size_t filelen = strlen(file);
    int denied = 0;
    const char *dir = search;
    do {
        const char *end = strchr(dir, ':');
        size_t length = end ? (size_t)(end - dir) : strlen(dir);
        char candidate[PATH_MAX];
        if (length + filelen + 2 > sizeof candidate) { errno = ENAMETOOLONG; goto next; }
        size_t used = length;
        memcpy(candidate, dir, length);
        if (length && dir[length - 1] != '/') candidate[used++] = '/';
        memcpy(candidate + used, file, filelen + 1);
        execve(candidate, argv, envp);
        if (errno == ENOEXEC) {
            size_t argc = 0;
            while (argv[argc]) argc++;
            char **shell = calloc(argc + 2, sizeof(char *));
            if (!shell) { errno = ENOMEM; return -1; }
            shell[0] = "/system/bin/sh"; shell[1] = candidate;
            for (size_t i = 1; i < argc; i++) shell[i + 1] = argv[i];
            execve(shell[0], shell, envp);
            int error = errno; free(shell); errno = error;
            return -1;
        }
        switch (errno) {
            case EACCES: denied = EACCES; /* fall through */
            case ENOENT: case ENOTDIR: case ELOOP: case ESTALE: case ENODEV: case ETIMEDOUT: break;
            case EISDIR: case ENAMETOOLONG: if (inherited) break; /* bionic execvp continues these too */
                return -1;
            default: return -1;
        }
next:
        if (!end) break;
        dir = end + 1;
    } while (true);
    if (denied) errno = denied;
    return -1;
}

/* JDK's helper has already changed cwd; bypass its cached host PATH without changing envp. */
static int search_request(const char *path, char *const args[], char *const envp[])
{
    static const char marker[] = "/proc/self/fd/apklayer-exec-search/";
    if (!args || !args[0] || strcmp(path, args[0]) || !args[1] || (strcmp(args[1], "1i") && strcmp(args[1], "1e"))
            || !args[2] || !args[3] || strchr(args[3], '/')
            || strcmp(path + sizeof marker - 1, args[3])) { errno = EINVAL; return -1; }
    return apklayer_exec_search_guest(args[3], args + 3, envp, args[2], !strcmp(args[1], "1i"));
}

/* A script whose interpreter is on a mapped path (Termux: #!/data/data/com.termux/files/usr/bin/sh) would have the kernel look the interpreter up
 * unmapped: it runs as the kernel would run it, "<interpreter> [<arg>] <script> <argv[1]...>", through these wrappers. NULL: run the file as it is. */
static char **script_argv(const char *file, const char *path, char *const argv[])
{
    char line[256];
    int fd = open(file, O_RDONLY | O_CLOEXEC);
    ssize_t n = fd < 0 ? -1 : read(fd, line, sizeof line - 1);
    if (fd >= 0) close(fd);
    if (n < 3 || line[0] != '#' || line[1] != '!') return NULL;
    line[n] = 0;
    char *p = line + 2, *end = strchr(p, '\n'), *arg = NULL;
    if (!end) return NULL;
    while (end > p && (end[-1] == ' ' || end[-1] == '\t' || end[-1] == '\r')) end--;
    *end = 0;
    p += strspn(p, " \t");
    char *name = p;
    p += strcspn(p, " \t");
    if (*p) { *p++ = 0; p += strspn(p, " \t"); if (*p) arg = p; }  /* the rest of the line is one argument, as the kernel passes it */
    char *mapped = name;
    if (!*name || !apklayer_apply_path_overrides(&mapped)) return NULL;  /* an unmapped interpreter: the kernel finds it */
    free(mapped);
    size_t argc = 0;
    while (argv && argv[argc]) argc++;
    char **a = calloc(1, (argc + 4) * sizeof(char *) + sizeof line);  /* the argv array, then copies of the interpreter's name and argument */
    if (!a) return NULL;
    char *strings = (char *)(a + argc + 4), **o = a;
    *o++ = strcpy(strings, name);
    if (arg) *o++ = strcpy(strings + strlen(name) + 1, arg);
    *o++ = (char *)path;
    for (size_t i = 1; i < argc; i++) *o++ = argv[i];
    return a;
}

int execve(const char *path, char *const argv[], char *const envp[])
{
    static const char marker[] = "/proc/self/fd/apklayer-exec-search/";
    if (!strncmp(path, marker, sizeof marker - 1)) return search_request(path, argv, envp);
    static __thread int depth;  /* a script's interpreter may be a script: the kernel's limit */
    struct command c;
    struct target t;
    char **sa;
    target(&t, path, envp);
    // Script/Android-ELF adaptation must not bypass the executable permission check.
    if (faccessat(AT_FDCWD, t.path, X_OK, AT_EACCESS) < 0) return target_done(&t, -1);
    if (depth < 4 && (sa = script_argv(t.path, path, argv))) {
        depth++;
        int r = execve(sa[0], sa, envp);
        depth--;
        free(sa);
        return target_done(&t, r);
    }
    /* btload gets the caller's spelling: its linker maps an absolute program path itself (a mapped one is outside its search path) */
    int rewritten = rewrite(path, argv, t.envp, envp, &c);
    return target_done(&t, rewritten < 0 ? -1 : rewritten ? exec_command(&c) : ((int (*)(const char *, char *const[], char *const[]))REAL(execve))(t.path, argv, t.envp));
}

int execv(const char *path, char *const argv[]) { return execve(path, argv, environ); }

/* Berberis supplies the exact guest environment; rewrite keeps its private loader controls separate. */
int apklayer_execve_guest(const char *path, char *const argv[], char *const envp[])
{
    return execve(path, argv, envp);
}

/* a name without a slash is searched in PATH: host programs, left to glibc */
int execvpe(const char *file, char *const argv[], char *const envp[])
{
    if (strchr(file, '/') && (execve(file, argv, envp), errno != ENOEXEC)) return -1;  /* ENOEXEC (a script without #!): glibc runs it with /bin/sh */
    struct target t;
    target(&t, strchr(file, '/') ? file : NULL, envp);
    return target_done(&t, ((int (*)(const char *, char *const[], char *const[]))REAL(execvpe))(t.path ? t.path : file, argv, t.envp));
}

int execvp(const char *file, char *const argv[]) { return execvpe(file, argv, environ); }

static int spawn(int (*real)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *, char *const[], char *const[]),
                 pid_t *pid, const char *path, const posix_spawn_file_actions_t *actions, const posix_spawnattr_t *attr, char *const argv[], char *const envp[])
{
    struct command c;
    struct target t;
    target(&t, strchr(path, '/') ? path : NULL, envp);
    const char *use = t.path ? t.path : path;
    int rewritten = t.path ? rewrite(path, argv, t.envp, envp, &c) : 0;
    if (rewritten < 0) return target_done(&t, errno);
    if (!rewritten) return target_done(&t, real(pid, use, actions, attr, argv, t.envp));
    int r = real(pid, c.btload, actions, attr, c.argv, c.envp);
    release(&c);
    return target_done(&t, r);
}

int posix_spawn(pid_t *pid, const char *path, const posix_spawn_file_actions_t *actions, const posix_spawnattr_t *attr, char *const argv[], char *const envp[])
{
    return spawn(REAL(posix_spawn), pid, path, actions, attr, argv, envp);
}

int posix_spawnp(pid_t *pid, const char *file, const posix_spawn_file_actions_t *actions, const posix_spawnattr_t *attr, char *const argv[], char *const envp[])
{
    return spawn(REAL(posix_spawnp), pid, file, actions, attr, argv, envp);
}
