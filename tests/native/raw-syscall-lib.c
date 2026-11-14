/* The guest side of raw-syscall.c: a library with no libc, loaded by bionic_translation's linker (the app namespace), that makes raw `svc #0`
 * syscalls as anti-tamper code and static runtimes do, and inspects the process through the linker's dl* entries (resolved from libdl_bio). */
#if defined(__aarch64__)
typedef unsigned long size_t;
typedef struct { const char *fname; void *fbase; const char *sname; void *saddr; } dl_info_t;
struct phdr_info { unsigned long addr; const char *name; };

void *dlopen(const char *, int);
const char *dlerror(void);
int dladdr(const void *, dl_info_t *);
int dl_iterate_phdr(int (*)(struct phdr_info *, size_t, void *), void *);

long raw_sys(long n, long a, long b, long c, long d)
{
    register long x8 __asm__("x8") = n, x0 __asm__("x0") = a, x1 __asm__("x1") = b, x2 __asm__("x2") = c, x3 __asm__("x3") = d;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3) : "memory");
    return x0;
}

static int has(const char *s, const char *needle)
{
    for (; *s; s++) {
        const char *a = s, *b = needle;
        while (*a && *b && *a == *b) a++, b++;
        if (!*b) return 1;
    }
    return 0;
}

static int starts(const char *s, const char *p) { while (*p) if (*s++ != *p++) return 0; return 1; }

/* dlopen of a library by name: 1 when it opened, 0 when it failed (the message in err) */
int guest_dlopen(const char *name, char *err, size_t n)
{
    void *h = dlopen(name, 2);
    if (h) return 1;
    const char *e = dlerror();
    size_t i = 0;
    for (; e && e[i] && i + 1 < n; i++) err[i] = e[i];
    err[i] = 0;
    return 0;
}

/* dladdr of an address: the module name and the symbol name, as this code sees them */
int guest_dladdr(const void *addr, char *file, char *sym, size_t n)
{
    dl_info_t info = {0};
    int r = dladdr(addr, &info);
    size_t i = 0;
    for (; r && info.fname && info.fname[i] && i + 1 < n; i++) file[i] = info.fname[i];
    file[i] = 0;
    for (i = 0; r && info.sname && info.sname[i] && i + 1 < n; i++) sym[i] = info.sname[i];
    sym[i] = 0;
    return r;
}

struct census { int total, host, jvm, libc, program; };
static int census_cb(struct phdr_info *info, size_t size, void *data)
{
    struct census *c = data;
    const char *name = info->name ? info->name : "";
    (void)size;
    c->total++;
    if (has(name, "raw-syscall-lib")) return 0;  /* this library itself: an app's, at a host path in the test (/data/app/... on a device) */
    if (has(name, "libjvm") || has(name, "/jvm/") || has(name, "java-") || has(name, "libjava")) c->jvm++;
    if (starts(name, "/usr/") || starts(name, "/home/") || starts(name, "/tmp/") || starts(name, "/opt/") || starts(name, "/lib/")) c->host++;
    if (has(name, "/bionic/libc.so")) c->libc++;
    if (has(name, "app_process")) c->program++;
    return 0;
}

/* the modules this code is shown */
void guest_census(int *out)
{
    struct census c = {0};
    dl_iterate_phdr(census_cb, &c);
    out[0] = c.total, out[1] = c.host, out[2] = c.jvm, out[3] = c.libc, out[4] = c.program;
}
#endif
