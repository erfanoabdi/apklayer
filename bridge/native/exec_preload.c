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

extern char **environ;
#define REAL(name) ({ static void *f_; if (!f_) f_ = dlsym(RTLD_NEXT, #name); f_; })

static const char *const runtime_vars[] = {"LD_LIBRARY_PATH", "LD_PRELOAD", "XDG_DATA_DIRS", "APKLAYER_NATIVE", "BIONIC_LD_LIBRARY_PATH", "APKLAYER_HOST_LIBS", NULL};

#define NVARS (sizeof runtime_vars / sizeof *runtime_vars - 1)
static char *started[NVARS];  /* "NAME=value" of each runtime variable at load, or NULL */

struct command { char *btload, *libpath, **argv, **envp; };

__attribute__((constructor)) static void save_runtime_env(void)
{
    for (size_t i = 0; i < NVARS; i++)
        for (char **e = environ; e && *e; e++)
            if (!strncmp(*e, runtime_vars[i], strlen(runtime_vars[i])) && (*e)[strlen(runtime_vars[i])] == '=') { started[i] = strdup(*e); break; }
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
    for (const char *const *v = runtime_vars; *v; v++)
        if (!strncmp(entry, *v, strlen(*v)) && entry[strlen(*v)] == '=') return true;
    return false;
}

static void release(struct command *c) { free(c->btload); free(c->libpath); free(c->argv); free(c->envp); }

static bool rewrite(const char *path, char *const argv[], char *const envp[], struct command *c)
{
    const char *native = runtime_value("APKLAYER_NATIVE"), *libs = runtime_value("BIONIC_LD_LIBRARY_PATH");
    if (!path || !native || !libs || !android_program(path)) return false;
    const char *own = runtime_value("LD_LIBRARY_PATH"), *asked = value(envp, "LD_LIBRARY_PATH");
    bool app_path = asked && *asked && (!own || strcmp(asked, own));
    size_t argc = 0, envc = 0, o = 0;
    while (argv && argv[argc]) argc++;
    while (envp && envp[envc]) envc++;
    *c = (struct command){0};
    if (asprintf(&c->btload, "%s/bin/btload", native) < 0) c->btload = NULL;
    if (asprintf(&c->libpath, "%s%s%s", app_path ? asked : "", app_path ? ":" : "", libs) < 0) c->libpath = NULL;
    c->argv = calloc(argc + 6, sizeof(char *));
    c->envp = calloc(envc + NVARS + 1, sizeof(char *));
    if (!c->btload || !c->libpath || !c->argv || !c->envp) { release(c); return false; }
    char **a = c->argv;
    *a++ = c->btload, *a++ = c->libpath, *a++ = (char *)path, *a++ = "--exec", *a++ = argc ? argv[0] : (char *)path;
    for (size_t i = 1; i < argc; i++) *a++ = argv[i];
    for (size_t i = 0; i < envc; i++)
        if (!runtime_var(envp[i])) c->envp[o++] = envp[i];
    for (size_t i = 0; i < NVARS; i++)
        if (started[i]) c->envp[o++] = started[i];
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

int execve(const char *path, char *const argv[], char *const envp[])
{
    struct command c;
    struct target t;
    target(&t, path, envp);
    return target_done(&t, rewrite(t.path, argv, t.envp, &c) ? exec_command(&c) : ((int (*)(const char *, char *const[], char *const[]))REAL(execve))(t.path, argv, t.envp));
}

int execv(const char *path, char *const argv[]) { return execve(path, argv, environ); }

/* a name without a slash is searched in PATH: host programs, left to glibc */
int execvpe(const char *file, char *const argv[], char *const envp[])
{
    struct command c;
    struct target t;
    target(&t, strchr(file, '/') ? file : NULL, envp);
    const char *use = t.path ? t.path : file;
    return target_done(&t, t.path && rewrite(use, argv, t.envp, &c) ? exec_command(&c) : ((int (*)(const char *, char *const[], char *const[]))REAL(execvpe))(use, argv, t.envp));
}

int execvp(const char *file, char *const argv[]) { return execvpe(file, argv, environ); }

static int spawn(int (*real)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *, char *const[], char *const[]),
                 pid_t *pid, const char *path, const posix_spawn_file_actions_t *actions, const posix_spawnattr_t *attr, char *const argv[], char *const envp[])
{
    struct command c;
    struct target t;
    target(&t, strchr(path, '/') ? path : NULL, envp);
    const char *use = t.path ? t.path : path;
    if (!t.path || !rewrite(use, argv, t.envp, &c)) return target_done(&t, real(pid, use, actions, attr, argv, t.envp));
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
