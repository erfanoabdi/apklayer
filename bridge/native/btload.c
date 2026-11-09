/* btload: loads a bionic library with bionic_translation the way apklayer.host.NativeLoader does, and prints the result.
 * Usage: btload LIBDIR NAME [--main ARGS...]      (--main: also calls the library's main(argc, argv) with NAME ARGS..., how
 * a Soong executable rebuilt as a shared library, e.g. libapklayer_servicemanager, is run)
 *        btload LIBDIR PROGRAM --exec ARGV0 ARGS...  (--exec: an Android executable, entered at its ELF entry like the kernel would:
 * the path layer's execve wrapper, exec_preload.c, runs an app's bionic programs this way; needs LD_LIBRARY_PATH to bionic_translation's build dir, XDG_DATA_DIRS to its cfg.d,
 * LD_PRELOAD=libgcc_s.so.1 plus the platform/stub libraries). Used for PLAN P7.0. */
#define _GNU_SOURCE
#include <link.h>
#include <stdbool.h>
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
extern void dl_parse_library_path(const char *path, char *delim);
extern void *bionic_dlsym(void *handle, const char *symbol);
extern void *bionic_dlopen(const char *filename, int flag);
extern char *bionic_dlerror(void);
extern struct r_debug *_r_debug_ptr;
extern int bionic_dl_iterate_phdr(int (*cb)(struct dl_phdr_info *, size_t, void *), void *data);
extern char **environ;

struct program { const char *path; uintptr_t base; bool found; };
static int find_program(struct dl_phdr_info *info, size_t size, void *data)
{
    (void)size;
    struct program *p = data;
    if (info->dlpi_name && !strcmp(info->dlpi_name, p->path)) p->base = info->dlpi_addr, p->found = true;
    return p->found;
}

/* bionic's _start hands its stack pointer to __libc_init as raw_args; bionic_translation's __libc_init reads {argc, argv}
 * there and the environment after argv's NULL */
__attribute__((noreturn)) static void enter(uintptr_t entry, int argc, char **args)
{
    size_t envc = 0;
    while (environ[envc]) envc++;
    char **argv = calloc(argc + envc + 2, sizeof(char *));
    memcpy(argv, args, argc * sizeof(char *));
    memcpy(argv + argc + 1, environ, envc * sizeof(char *));
    uintptr_t raw[2] __attribute__((aligned(16))) = {(uintptr_t)argc, (uintptr_t)argv};
#if defined(__aarch64__)
    __asm__ volatile("mov sp, %1\n br %0" ::"r"(entry), "r"(raw) : "memory");
#elif defined(__x86_64__)
    __asm__ volatile("mov %1, %%rsp\n jmp *%0" ::"r"(entry), "r"(raw) : "memory");
#elif defined(__arm__)
    __asm__ volatile("mov sp, %1\n bx %0" ::"r"(entry), "r"(raw) : "memory");
#else
#error "btload --exec: no entry sequence for this architecture"
#endif
    __builtin_unreachable();
}

static int exec_program(const char *path, int argc, char **argv)
{
    extern bool apklayer_apply_path_overrides(char **path);
    char *named = (char *)path;
    apklayer_apply_path_overrides(&named);  /* the linker names a program on a mapped path (an app's /data/data/<pkg>/...) by its host path; leaked, the process becomes it */
    struct program p = {named, 0, false};
    Elf64_Ehdr eh;
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    bool header = fd >= 0 && pread(fd, &eh, sizeof eh, 0) == sizeof eh;
    if (fd >= 0) close(fd);
    bionic_dl_iterate_phdr(find_program, &p);
    if (!header || !p.found) { fprintf(stderr, "btload: no entry for %s\n", path); return 127; }
    /* the program's own name, as bionic's libc init sets __progname from argv[0] (getprogname: android_platform.c), and glibc's for its err/error */
    extern void setprogname(const char *);
    setprogname(argv[0]);
    program_invocation_name = argv[0];
    program_invocation_short_name = strrchr(argv[0], '/') ? strrchr(argv[0], '/') + 1 : argv[0];
    enter(p.base + eh.e_entry, argc, argv);
}
int main(int argc, char **argv)
{
    if (argc < 3) return 2;
    _r_debug_ptr = &_r_debug;
    extern void apklayer_log_to_stderr(void);
    bool exec = argc >= 5 && !strcmp(argv[3], "--exec");  /* the program's stdout is its own */
    /* an exec'd program is opened by its absolute path (FCL runs ./libunpack200.so from its lib dir), and the shim linker opens a path only
     * inside its search path: the program's own directory goes last, as Android's linker runs any path */
    char *full = exec ? realpath(argv[2], NULL) : NULL, *search = NULL;
    if (full) argv[2] = full;
    if (full && asprintf(&search, "%s:%.*s", argv[1], (int)(strrchr(full, '/') - full), full) < 0) search = NULL;
    dl_parse_library_path(search ? search : argv[1], ":");
    /* host libraries the Android code calls into (APKLAYER_HOST_LIBS, colon separated), global so the bionic linker finds their symbols */
    if (getenv("APKLAYER_HOST_LIBS")) {
        char *libs = strdup(getenv("APKLAYER_HOST_LIBS"));
        for (char *save, *l = strtok_r(libs, ":", &save); l; l = strtok_r(NULL, ":", &save))
            if (!dlopen(l, RTLD_NOW | RTLD_GLOBAL)) fprintf(stderr, "btload: %s\n", dlerror());
    }
    apklayer_log_to_stderr();
    if (getenv("APKLAYER_CRASH_TRACE")) { extern void apklayer_crash_trace_signals(void); apklayer_crash_trace_signals(); }
    void *h = bionic_dlopen(argv[2], 2 /* RTLD_NOW */);
    if (!exec) { if (h) printf("LOADED %s\n", argv[2]); else printf("FAILED %s: %s\n", argv[2], bionic_dlerror()); }
    else if (!h) { fprintf(stderr, "btload: %s: %s\n", argv[2], bionic_dlerror()); return 127; }
    if (h) { extern void apklayer_install_path_layer(void); apklayer_install_path_layer(); } /* libc_bio is loaded now */
    if (exec) return exec_program(argv[2], argc - 4, argv + 4);
    if (!h || argc < 4 || strcmp(argv[3], "--main")) return h ? 0 : 1;
    int (*lib_main)(int, char **) = bionic_dlsym(h, "main");
    if (!lib_main) { printf("no main in %s\n", argv[2]); return 1; }
    argv[3] = argv[2]; /* argv[0] of main */
    return lib_main(argc - 3, argv + 3);
}
