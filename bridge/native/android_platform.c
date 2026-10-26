/* Android platform symbols that Android's own native libraries (libandroid_runtime and its closure, PLAN P7) use and
 * that bionic_translation's libc does not provide. Loaded into the process with glibc, where the translator's linker
 * finds symbols nothing else defines (dlsym on the global scope). Almost all are stubs: a desktop has no
 * property service, no linker namespaces, no malloc debugging.  Names and signatures follow bionic's headers. */
#define _GNU_SOURCE
#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
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
void __cfi_slowpath(uint64_t type, void *ptr, void *diag) { (void)type; (void)ptr; (void)diag; } /* no CFI enforcement */

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

/* system properties: an empty, read-only store */
int __system_properties_init(void) { return 0; }
int __system_properties_zygote_reload(void) { return 0; }
uint32_t __system_property_area_serial(void) { return 0; }
uint32_t __system_property_serial(const void *pi) { (void)pi; return 0; }
int __system_property_foreach(void (*cb)(const void *, void *), void *cookie) { (void)cb; (void)cookie; return 0; }
void __system_property_read_callback(const void *pi, void (*cb)(void *, const char *, const char *, uint32_t), void *cookie)
{ (void)pi; (void)cb; (void)cookie; }
int __system_property_set(const char *name, const char *value) { (void)name; (void)value; return 0; }
bool __system_property_wait(const void *pi, uint32_t old, uint32_t *new_serial, const struct timespec *timeout)
{ (void)pi; (void)old; (void)new_serial; (void)timeout; return false; }

/* linker namespaces (libdl_android) and bionic's android_* helpers */
void *android_create_namespace(const char *a, const char *b, const char *c, uint64_t d, const char *e, void *f)
{ (void)a; (void)b; (void)c; (void)d; (void)e; (void)f; return NULL; }
void *android_get_exported_namespace(const char *name) { (void)name; return NULL; }
bool android_link_namespaces(void *a, void *b, const char *c) { (void)a; (void)b; (void)c; return true; }
bool android_dlwarning(void *obj, void (*cb)(void *, const char *)) { (void)obj; (void)cb; return false; }
uint64_t android_fdsan_get_owner_tag(int fd) { (void)fd; return 0; }
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
    if (prio < 5 && !debug) return 0;
    char buf[1024];
    int n = vsnprintf(buf, sizeof buf, fmt, ap);
    size_t len = strlen(buf);
    while (len && buf[len - 1] == '\n') buf[--len] = 0;
    if (len) fprintf(stderr, "[%s] %s\n", tag ? tag : "linker", buf);
    return n;
}
