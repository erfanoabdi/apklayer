/* Android platform symbols that Android's own native libraries (libandroid_runtime and its closure, PLAN P7) use and
 * that bionic_translation's libc does not provide. Loaded into the process with glibc, where the translator's linker
 * finds symbols nothing else defines (dlsym on the global scope). Almost all are stubs: a desktop has no
 * property service, no linker namespaces, no malloc debugging.  Names and signatures follow bionic's headers. */
#define _GNU_SOURCE
#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
int __system_property_area_init(void); /* properties.c */
#include <signal.h>
#include <ucontext.h>
#include <unistd.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <netdb.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

/* fortify checks: the buffer-size argument is only for overflow detection */
ssize_t __pwrite_chk(int fd, const void *buf, size_t n, off_t off, size_t buflen) { (void)buflen; return pwrite(fd, buf, n, off); }
ssize_t __sendto_chk(int fd, const void *buf, size_t n, size_t buflen, int flags, const struct sockaddr *a, socklen_t l)
{ (void)buflen; return sendto(fd, buf, n, flags, a, l); }
/* the Ogg extractor's fortified memchr/memrchr (bionic only) */
void *__memchr_chk(const void *s, int c, size_t n, size_t buflen) { if (n > buflen) abort(); return memchr(s, c, n); }
void *__memrchr_chk(const void *s, int c, size_t n, size_t buflen) { if (n > buflen) abort(); return memrchr(s, c, n); }
void __cfi_slowpath(uint64_t type, void *ptr, void *diag) { (void)type; (void)ptr; (void)diag; } /* no CFI enforcement */
void __cfi_slowpath_diag(uint64_t type, void *ptr, void *diag) { __cfi_slowpath(type, ptr, diag); }

/* resolv's base64 encoder */
int __b64_ntop(const unsigned char *src, size_t len, char *dst, size_t size)
{
    static const char t[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    size_t o = 0;
    if (size < (len + 2) / 3 * 4 + 1) return -1;
    for (size_t i = 0; i < len; i += 3) {
        uint32_t v = src[i] << 16 | (i + 1 < len ? src[i + 1] << 8 : 0) | (i + 2 < len ? src[i + 2] : 0);
        dst[o++] = t[v >> 18 & 63]; dst[o++] = t[v >> 12 & 63];
        dst[o++] = i + 1 < len ? t[v >> 6 & 63] : '='; dst[o++] = i + 2 < len ? t[v & 63] : '=';
    }
    dst[o] = 0;
    return (int)o;
}

/* system properties: the real store is properties.c */
int __system_properties_init(void) { return __system_property_area_init(); }
int __system_properties_zygote_reload(void) { return 0; }

/* linker namespaces (libdl_android) and bionic's android_* helpers */
void *android_create_namespace(const char *a, const char *b, const char *c, uint64_t d, const char *e, void *f)
{ (void)a; (void)b; (void)c; (void)d; (void)e; (void)f; return NULL; }
void *android_get_exported_namespace(const char *name) { (void)name; return NULL; }
bool android_link_namespaces(void *a, void *b, const char *c) { (void)a; (void)b; (void)c; return true; }
bool android_dlwarning(void *obj, void (*cb)(void *, const char *)) { (void)obj; (void)cb; return false; }
/* fdsan: bionic's file-descriptor ownership checks. The functions are weak imports that libcutils (native_handle_close) calls
 * unconditionally, so unresolved they were a null call. There is no tracking here: owner tags are computed as bionic does and
 * ignored, closing is a plain close. */
uint64_t android_fdsan_get_owner_tag(int fd) { (void)fd; return 0; }
uint64_t android_fdsan_create_owner_tag(int type, uint64_t tag) { return type == 0 ? 0 : ((uint64_t)type << 56) | (tag & 0x00ffffffffffffffULL); }
void android_fdsan_exchange_owner_tag(int fd, uint64_t expected, uint64_t desired) { (void)fd; (void)expected; (void)desired; }
int android_fdsan_close_with_tag(int fd, uint64_t tag) { (void)tag; return close(fd); }
const char *android_fdsan_get_tag_type(uint64_t tag) { (void)tag; return "unknown"; }
uint64_t android_fdsan_get_tag_value(uint64_t tag) { return tag & 0x00ffffffffffffffULL; }
int android_fdsan_get_error_level(void) { return 0; }
int android_fdsan_set_error_level(int level) { (void)level; return 0; }
int android_get_application_target_sdk_version(void) { return 36; }
int android_get_device_api_level(void) { return 36; }
bool android_mallopt(int opcode, void *arg, size_t size) { (void)opcode; (void)arg; (void)size; return true; }
void android_reset_stack_guards(void) {}
void android_set_16kb_appcompat_mode(bool enable) { (void)enable; }

/* process name, malloc debugging, misc libc */
static const char *progname = "apklayer";
const char *getprogname(void) { return progname; }
void setprogname(const char *name) { progname = name; }
void malloc_disable(void) {}
void malloc_enable(void) {}
int malloc_iterate(uintptr_t base, size_t size, void (*cb)(uintptr_t, size_t, void *), void *arg)
{ (void)base; (void)size; (void)cb; (void)arg; return 0; }
size_t malloc_backtrace(void *pointer, uintptr_t *frames, size_t max) { (void)pointer; (void)frames; (void)max; return 0; }
void *memset_explicit(void *p, int c, size_t n) { volatile unsigned char *v = p; while (n--) *v++ = (unsigned char)c; return p; }
pid_t pthread_gettid_np(pthread_t t) { (void)t; return (pid_t)syscall(SYS_gettid); } /* only the calling thread is known */

/* network-scoped DNS (netd): the desktop has one network, so the network id and mark are ignored */
int android_getaddrinfofornet(const char *hostname, const char *servname, const struct addrinfo *hints, unsigned netid, unsigned mark,
                              struct addrinfo **res)
{ (void)netid; (void)mark; return getaddrinfo(hostname, servname, hints, res); }

/* bionic_translation's linker and wrapper log through __android_log_vprint, looked up in the glibc namespace; without
 * one they print everything (thousands of lines per library). This one keeps warnings and worse, the rest with
 * APKLAYER_LINKER_DEBUG=1. (The bionic side of the process has the real liblog; see the logger hook in runtime_shim.c.) */
int __android_log_vprint(int prio, const char *tag, const char *fmt, va_list ap)
{
    static int debug = -1;
    if (debug < 0) debug = getenv("APKLAYER_LINKER_DEBUG") != NULL;
    if (!debug && (prio < 5 || (prio < 6 && tag && strstr(tag, "linker")))) return 0; /* the linker's "not prelinked" warning is every library */
    char buf[1024];
    int n = vsnprintf(buf, sizeof buf, fmt, ap);
    size_t len = strlen(buf);
    while (len && buf[len - 1] == '\n') buf[--len] = 0;
    if (len) fprintf(stderr, "[%s] %s\n", tag ? tag : "linker", buf);
    return n;
}

/* Android's liblog writes to logd, which does not exist here; its own logger hook sends warnings and worse to stderr, all
 * of it with APKLAYER_RUNTIME_DEBUG, so a LOG_ALWAYS_FATAL message is not lost. Call after the process can load liblog. */
extern void *bionic_dlopen(const char *filename, int flag);
extern void *bionic_dlsym(void *handle, const char *symbol);
struct android_log_message { size_t size; int32_t buffer_id; int32_t priority; const char *tag, *file; uint32_t line; const char *message; };
static void stderr_logger(const struct android_log_message *m)
{
    if (m->priority >= 5 || getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[log %d] %s: %s\n", m->priority, m->tag, m->message);
    const char *dump = getenv("APKLAYER_DUMP_ON"); /* debug: the first log line containing this text prints the JVM's thread dump and exits */
    if (dump && strstr(m->message, dump)) {
        if (getenv("APKLAYER_DUMP_ABORT")) abort(); /* with -Dapklayer.runtimetrace=true: the native call chain instead */
        kill(getpid(), SIGQUIT);
        nanosleep(&(struct timespec){ 2, 0 }, NULL);
        _exit(3);
    }
}

void apklayer_log_to_stderr(void)
{
    void *liblog = bionic_dlopen("liblog.so", 2);
    void (*set_logger)(void (*)(const struct android_log_message *)) = liblog ? bionic_dlsym(liblog, "__android_log_set_logger") : NULL;
    if (set_logger) set_logger(stderr_logger);
}

/* Debug aid (-Dapklayer.runtimetrace=true, or APKLAYER_CRASH_TRACE=1 in btload): on SIGABRT/SIGSEGV print the frame-pointer chain of the faulting thread and the
 * memory map lines of the Android libraries, so addresses can be resolved with readelf/nm (gdb cannot unwind libraries
 * that the bionic linker loaded). Android's arm64 code keeps frame pointers. */
/* Async-signal-safe: a crash can hold the allocator's lock, and fopen/fprintf in the handler then hangs after the first line (the old handler
 * printed only "signal 11 pc=" for the SurfaceFlinger crash). Raw open/read/write and snprintf into stack buffers only. */
static char trace_maps[1 << 19];
static size_t trace_maps_len;
static void trace_write(const char *s, size_t n) { while (n) { ssize_t w = write(2, s, n); if (w <= 0) return; s += w; n -= w; } }
static void trace_line(const char *fmt, unsigned long long a, unsigned long long b, const char *path, size_t pathlen)
{
    char out[512];
    int n = snprintf(out, sizeof out, fmt, a, b);
    if (n > 0) trace_write(out, (size_t)n < sizeof out ? (size_t)n : sizeof out - 1);
    if (path) trace_write(path, pathlen);
    trace_write("\n", 1);
}
/* name the library of an address: "[runtime-trace] where <addr> <path>+<file offset>" (anonymous memory: "anonymous") */
static void where(uint64_t addr)
{
    for (size_t i = 0; i < trace_maps_len;) {
        size_t eol = i;
        while (eol < trace_maps_len && trace_maps[eol] != '\n') eol++;
        unsigned long long lo = 0, hi = 0, off = 0;
        size_t j = i;
        for (; j < eol && trace_maps[j] != '-'; j++) lo = lo * 16 + (trace_maps[j] <= '9' ? trace_maps[j] - '0' : trace_maps[j] - 'a' + 10);
        for (j++; j < eol && trace_maps[j] != ' '; j++) hi = hi * 16 + (trace_maps[j] <= '9' ? trace_maps[j] - '0' : trace_maps[j] - 'a' + 10);
        if (addr >= lo && addr < hi) {
            int spaces = 0;
            for (; j < eol && spaces < 3; j++) if (trace_maps[j] == ' ') spaces++;
            for (; j < eol; j++) if (trace_maps[j] != ' ') break;
            for (size_t k = j - 1; k > i && spaces == 3 && trace_maps[k] != ' ' ; k--) {}
            size_t o = i; /* the offset is the third field */
            int field = 0;
            for (; o < eol && field < 2; o++) if (trace_maps[o] == ' ') field++;
            for (; o < eol && trace_maps[o] != ' '; o++) off = off * 16 + (trace_maps[o] <= '9' ? trace_maps[o] - '0' : trace_maps[o] - 'a' + 10);
            const char *path = memchr(trace_maps + i, '/', eol - i);
            if (path) { trace_line("[runtime-trace] where 0x%llx +0x%llx ", (unsigned long long)addr, addr - lo + off, path, (size_t)(trace_maps + eol - path)); }
            else trace_line("[runtime-trace] where 0x%llx anonymous (%llx)", (unsigned long long)addr, lo, NULL, 0);
            return;
        }
        i = eol + 1;
    }
}

static void crash_trace_handler(int sig, siginfo_t *info, void *ctx)
{
    ucontext_t *uc = ctx;
    uint64_t pc = uc->uc_mcontext.pc, *fp = (uint64_t *)uc->uc_mcontext.regs[29];
    int fd = open("/proc/self/maps", O_RDONLY);
    if (fd >= 0) { ssize_t n; while (trace_maps_len < sizeof trace_maps && (n = read(fd, trace_maps + trace_maps_len, sizeof trace_maps - trace_maps_len)) > 0) trace_maps_len += n; close(fd); }
    trace_line("[runtime-trace] signal %llu pc=0x%llx", (unsigned long long)sig, (unsigned long long)pc, NULL, 0);
    trace_line("[runtime-trace] fault address 0x%llx x0=0x%llx", (unsigned long long)(uintptr_t)info->si_addr, (unsigned long long)uc->uc_mcontext.regs[0], NULL, 0);
    where(pc);
    trace_line("[runtime-trace] lr=0x%llx sp=0x%llx", (unsigned long long)uc->uc_mcontext.regs[30], (unsigned long long)uc->uc_mcontext.sp, NULL, 0);
    where(uc->uc_mcontext.regs[30]);
    for (int i = 0; i < 24 && fp && ((uintptr_t)fp & 7) == 0; i++) {
        trace_line("[runtime-trace] #%llu lr=0x%llx", (unsigned long long)i, (unsigned long long)fp[1], NULL, 0);
        where(fp[1]);
        uint64_t *next = (uint64_t *)fp[0];
        if (next <= fp) break;
        fp = next;
    }
    signal(sig, SIG_DFL);
    raise(sig);
}


/* For executables (btload): also segfaults and the like. Not for the JVM, which uses SIGSEGV internally. */
void apklayer_crash_trace_signals(void)
{
    struct sigaction sa = { .sa_sigaction = crash_trace_handler, .sa_flags = SA_SIGINFO | SA_RESETHAND };
    sigaction(SIGABRT, &sa, NULL);
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);
    sigaction(SIGILL, &sa, NULL);
}

void apklayer_crash_trace(void)
{
    struct sigaction sa = { .sa_sigaction = crash_trace_handler, .sa_flags = SA_SIGINFO | SA_RESETHAND };
    sigaction(SIGABRT, &sa, NULL);
}

/* android::Layer::logFrameStats() is declared and called from SurfaceFlinger::logFrameStats but never defined in the tree (the
 * surfaceflinger binary's linker drops the unused caller; as a library it stays, and bionic_translation resolves everything at load). */
void _ZN7android5Layer13logFrameStatsEv(void) {}
