/* Android system properties for the desktop (PLAN P7.1 step 0.4): a shared-memory store with bionic's API and semantics, so
 * libcutils, libbase (GetProperty/WaitForProperty), SurfaceFlinger and the framework natives read real values, and every
 * process of an instance sees the same ones. No property service: any process of the instance may set (security is the
 * host's), but ro.* properties are write-once as on Android. The file is per instance:
 * $APKLAYER_PROPERTIES, else $XDG_RUNTIME_DIR/apklayer/<$APKLAYER_INSTANCE or "default">/properties.
 * Layout: header + fixed-size records found by a linear scan (a build has a few hundred properties). A record's serial is
 * (value length << 24 | counter), the low bit set while a writer is updating (the same protocol as bionic's prop_info).
 * Exported under bionic's names; bionic_translation's stubs for the three it defines are removed by
 * scripts/patch-bionic-translation.sh so these are what bionic libraries resolve. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <linux/futex.h>
#include <sched.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

#define PROP_NAME_MAX 64   /* stored; bionic's own limit is longer for ro.* only in the property_contexts sense */
#define PROP_VALUE_MAX 92
#define AREA_SIZE (256 * 1024)
#define MAGIC 0x504b4c41u /* "ALKP" */

struct prop_info { /* the pointer handed to callers */
    _Atomic uint32_t serial;
    char value[PROP_VALUE_MAX];
    char name[PROP_NAME_MAX];
};

struct prop_area {
    uint32_t magic, version;
    _Atomic uint32_t count;
    _Atomic uint32_t area_serial;
    _Atomic int lock;
    struct prop_info records[];
};

#define MAX_RECORDS ((AREA_SIZE - sizeof(struct prop_area)) / sizeof(struct prop_info))

static struct prop_area *area;

static int mkdirs(char *path)
{
    for (char *p = path + 1; *p; p++) {
        if (*p != '/') continue;
        *p = 0;
        mkdir(path, 0700);
        *p = '/';
    }
    return 0;
}

static struct prop_area *open_area(void)
{
    if (area) return area;
    char path[PATH_MAX];
    const char *file = getenv("APKLAYER_PROPERTIES");
    if (file) {
        snprintf(path, sizeof path, "%s", file);
    } else {
        const char *rt = getenv("XDG_RUNTIME_DIR"), *inst = getenv("APKLAYER_INSTANCE");
        snprintf(path, sizeof path, "%s/apklayer/%s/properties", rt ? rt : "/tmp", inst ? inst : "default");
    }
    mkdirs(path);
    int fd = open(path, O_RDWR | O_CREAT | O_CLOEXEC, 0600);
    if (fd < 0) return NULL;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size < AREA_SIZE) {
        if (ftruncate(fd, AREA_SIZE) < 0) { close(fd); return NULL; }
    }
    void *p = mmap(NULL, AREA_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    close(fd);
    if (p == MAP_FAILED) return NULL;
    struct prop_area *a = p;
    uint32_t zero = 0;
    /* the first process to see the zeroed file stamps it */
    if (atomic_compare_exchange_strong((_Atomic uint32_t *)&a->magic, &zero, MAGIC)) a->version = 1;
    area = a;
    return a;
}

static void lock_area(struct prop_area *a) { while (atomic_exchange(&a->lock, 1)) sched_yield(); }
static void unlock_area(struct prop_area *a) { atomic_store(&a->lock, 0); }

static long futex(_Atomic uint32_t *addr, int op, uint32_t val, const struct timespec *ts)
{
    return syscall(SYS_futex, addr, op, val, ts, NULL, 0);
}

const struct prop_info *__system_property_find(const char *name)
{
    struct prop_area *a = open_area();
    if (!a || !name) return NULL;
    uint32_t n = atomic_load(&a->count);
    for (uint32_t i = 0; i < n; i++) if (!strcmp(a->records[i].name, name)) return &a->records[i];
    return NULL;
}

void __system_property_read_callback(const struct prop_info *pi, void (*cb)(void *, const char *, const char *, uint32_t), void *cookie)
{
    for (;;) {
        uint32_t s = atomic_load_explicit(&pi->serial, memory_order_acquire);
        if (s & 1) { sched_yield(); continue; } /* a writer is updating */
        char value[PROP_VALUE_MAX];
        memcpy(value, pi->value, sizeof value);
        value[PROP_VALUE_MAX - 1] = 0;
        if (atomic_load_explicit(&pi->serial, memory_order_acquire) != s) continue;
        cb(cookie, pi->name, value, s);
        return;
    }
}

struct read_args { char *name, *value; };
static void read_cb(void *cookie, const char *name, const char *value, uint32_t serial)
{
    (void)serial;
    struct read_args *a = cookie;
    if (a->name) snprintf(a->name, PROP_NAME_MAX, "%s", name);
    snprintf(a->value, PROP_VALUE_MAX, "%s", value);
}

int __system_property_read(const struct prop_info *pi, char *name, char *value)
{
    struct read_args a = { name, value };
    char scratch[PROP_VALUE_MAX];
    if (!value) a.value = scratch;
    __system_property_read_callback(pi, read_cb, &a);
    return (int)strlen(a.value);
}

int __system_property_get(const char *name, char *value)
{
    const struct prop_info *pi = __system_property_find(name);
    if (!pi) { value[0] = 0; return 0; }
    return __system_property_read(pi, NULL, value);
}

uint32_t __system_property_serial(const struct prop_info *pi) { return atomic_load(&pi->serial); }
uint32_t __system_property_area_serial(void) { struct prop_area *a = open_area(); return a ? atomic_load(&a->area_serial) : 0; }
int __system_property_area_init(void) { return open_area() ? 0 : -1; }
int __system_property_set_filename(const char *f) { setenv("APKLAYER_PROPERTIES", f, 1); area = NULL; return 0; }

const struct prop_info *__system_property_find_nth(unsigned n)
{
    struct prop_area *a = open_area();
    return a && n < atomic_load(&a->count) ? &a->records[n] : NULL;
}

int __system_property_foreach(void (*fn)(const struct prop_info *, void *), void *cookie)
{
    struct prop_area *a = open_area();
    if (!a) return -1;
    for (uint32_t i = 0, n = atomic_load(&a->count); i < n; i++) fn(&a->records[i], cookie);
    return 0;
}

int __system_property_set(const char *name, const char *value)
{
    struct prop_area *a = open_area();
    size_t nlen = name ? strlen(name) : 0, vlen = value ? strlen(value) : 0;
    if (!a || nlen == 0 || nlen >= PROP_NAME_MAX || vlen >= PROP_VALUE_MAX) return -1;
    lock_area(a);
    struct prop_info *pi = (struct prop_info *)__system_property_find(name);
    int r = 0;
    if (pi) {
        if (!strncmp(name, "ro.", 3)) { r = -1; } /* write-once */
        else {
            uint32_t s = atomic_load(&pi->serial);
            atomic_store(&pi->serial, s | 1); /* writer in progress */
            memset(pi->value, 0, sizeof pi->value);
            memcpy(pi->value, value, vlen);
            atomic_store(&pi->serial, (((uint32_t)vlen << 24) | (((s & 0xffffff) + 2) & 0xffffff)) & ~1u);
            futex(&pi->serial, FUTEX_WAKE, INT_MAX, NULL);
        }
    } else if (atomic_load(&a->count) >= MAX_RECORDS) {
        r = -1;
    } else {
        pi = &a->records[atomic_load(&a->count)];
        memcpy(pi->name, name, nlen + 1);
        memcpy(pi->value, value, vlen + 1);
        atomic_store(&pi->serial, (uint32_t)vlen << 24);
        atomic_fetch_add(&a->count, 1);
    }
    atomic_fetch_add(&a->area_serial, 1);
    futex(&a->area_serial, FUTEX_WAKE, INT_MAX, NULL);
    unlock_area(a);
    return r;
}

/* Blocks until the property (or, with pi NULL, any property) changes from old_serial. */
int __system_property_wait(const struct prop_info *pi, uint32_t old_serial, uint32_t *new_serial, const struct timespec *relative_timeout)
{
    struct prop_area *a = open_area();
    if (!a) return 0;
    _Atomic uint32_t *word = pi ? (_Atomic uint32_t *)&pi->serial : &a->area_serial;
    for (;;) {
        uint32_t now = atomic_load(word);
        if (now != old_serial && !(pi && (now & 1))) { if (new_serial) *new_serial = now; return 1; }
        if (futex(word, FUTEX_WAIT, old_serial, relative_timeout) < 0 && errno == ETIMEDOUT) return 0;
    }
}
