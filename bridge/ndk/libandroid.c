/* NDK libandroid shim (glibc): assets from the APK, ALooper, AConfiguration, ANativeWindow stubs.
 * The APK path comes from $APKLAYER_APK (set by bin/apklayer); the JNI AssetManager object is ignored. */
#define _GNU_SOURCE
#include <errno.h>
#include <poll.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <zlib.h>

/* ---- assets: a minimal zip reader over the APK ---- */

struct entry { char *name; uint32_t off, csize, usize; uint16_t method; };
static struct entry *entries; static int nentries;
static unsigned char *apk; static size_t apklen;
static pthread_once_t once = PTHREAD_ONCE_INIT;

static uint32_t u32(const unsigned char *p) { return p[0] | p[1] << 8 | p[2] << 16 | (uint32_t)p[3] << 24; }
static uint16_t u16(const unsigned char *p) { return p[0] | p[1] << 8; }

static void load_apk(void)
{
    const char *path = getenv("APKLAYER_APK");
    int fd = path ? open(path, 0) : -1;
    struct stat st;
    if (fd < 0 || fstat(fd, &st) < 0) return;
    apklen = st.st_size;
    apk = mmap(NULL, apklen, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (apk == MAP_FAILED) { apk = NULL; return; }
    size_t e = apklen - 22;
    while (e > 0 && u32(apk + e) != 0x06054b50) e--;
    if (u32(apk + e) != 0x06054b50) return;
    nentries = u16(apk + e + 10);
    entries = calloc(nentries, sizeof *entries);
    const unsigned char *p = apk + u32(apk + e + 16);
    for (int i = 0; i < nentries; i++) {
        uint16_t nl = u16(p + 28), xl = u16(p + 30), cl = u16(p + 32);
        entries[i].method = u16(p + 10);
        entries[i].csize = u32(p + 20);
        entries[i].usize = u32(p + 24);
        entries[i].off = u32(p + 42);
        entries[i].name = strndup((const char *)p + 46, nl);
        p += 46 + nl + xl + cl;
    }
}

typedef struct AAssetManager { int unused; } AAssetManager;
typedef struct AAsset { unsigned char *data; off_t len, pos; } AAsset;
typedef struct AAssetDir { char *prefix; int next; } AAssetDir;
static AAssetManager the_manager;

AAssetManager *AAssetManager_fromJava(void *env, void *assetManager)
{ (void)env; (void)assetManager; pthread_once(&once, load_apk); return &the_manager; }

AAsset *AAssetManager_open(AAssetManager *m, const char *name, int mode)
{
    (void)m; (void)mode;
    pthread_once(&once, load_apk);
    char full[1024];
    snprintf(full, sizeof full, "assets/%s", name);
    for (int i = 0; i < nentries; i++) {
        if (strcmp(entries[i].name, full)) continue;
        const unsigned char *lh = apk + entries[i].off;
        const unsigned char *src = lh + 30 + u16(lh + 26) + u16(lh + 28);
        AAsset *a = calloc(1, sizeof *a);
        a->len = entries[i].usize;
        a->data = malloc(a->len ? a->len : 1);
        if (entries[i].method == 0) memcpy(a->data, src, a->len);
        else {
            z_stream z = {0};
            inflateInit2(&z, -15);
            z.next_in = (Bytef *)src; z.avail_in = entries[i].csize;
            z.next_out = a->data; z.avail_out = a->len;
            inflate(&z, Z_FINISH);
            inflateEnd(&z);
        }
        return a;
    }
    return NULL;
}

int AAsset_read(AAsset *a, void *buf, size_t n)
{
    size_t r = (size_t)(a->len - a->pos) < n ? (size_t)(a->len - a->pos) : n;
    memcpy(buf, a->data + a->pos, r);
    a->pos += r;
    return (int)r;
}
off_t AAsset_seek(AAsset *a, off_t off, int whence)
{
    off_t p = whence == SEEK_SET ? off : whence == SEEK_CUR ? a->pos + off : a->len + off;
    if (p < 0 || p > a->len) return -1;
    return a->pos = p;
}
off_t AAsset_getLength(AAsset *a) { return a->len; }
off_t AAsset_getRemainingLength(AAsset *a) { return a->len - a->pos; }
const void *AAsset_getBuffer(AAsset *a) { return a->data; }
int AAsset_isAllocated(AAsset *a) { (void)a; return 1; }
void AAsset_close(AAsset *a) { if (a) { free(a->data); free(a); } }
int AAsset_openFileDescriptor(AAsset *a, off_t *s, off_t *l) { (void)a; (void)s; (void)l; return -1; }

AAssetDir *AAssetManager_openDir(AAssetManager *m, const char *dir)
{
    (void)m;
    pthread_once(&once, load_apk);
    AAssetDir *d = calloc(1, sizeof *d);
    asprintf(&d->prefix, "assets/%s%s", dir, *dir ? "/" : "");
    return d;
}
const char *AAssetDir_getNextFileName(AAssetDir *d)
{
    size_t pl = strlen(d->prefix);
    while (d->next < nentries) {
        const char *n = entries[d->next++].name;
        if (!strncmp(n, d->prefix, pl) && n[pl] && !strchr(n + pl, '/')) return n + pl;
    }
    return NULL;
}
void AAssetDir_rewind(AAssetDir *d) { d->next = 0; }
void AAssetDir_close(AAssetDir *d) { free(d->prefix); free(d); }

/* ---- ALooper: poll() over registered fds plus a wake eventfd ---- */

#define ALOOPER_POLL_WAKE (-1)
#define ALOOPER_POLL_CALLBACK (-2)
#define ALOOPER_POLL_TIMEOUT (-3)
#define ALOOPER_POLL_ERROR (-4)
typedef int (*ALooper_callbackFunc)(int fd, int events, void *data);
struct watch { int fd, ident, events; ALooper_callbackFunc cb; void *data; };
typedef struct ALooper { int wake; pthread_mutex_t mu; struct watch w[64]; int n; } ALooper;
static __thread ALooper *tl_looper;

ALooper *ALooper_prepare(int opts)
{
    (void)opts;
    if (!tl_looper) {
        tl_looper = calloc(1, sizeof *tl_looper);
        tl_looper->wake = eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC);
        pthread_mutex_init(&tl_looper->mu, NULL);
    }
    return tl_looper;
}
ALooper *ALooper_forThread(void) { return tl_looper; }
void ALooper_acquire(ALooper *l) { (void)l; }
void ALooper_release(ALooper *l) { (void)l; }
void ALooper_wake(ALooper *l) { uint64_t one = 1; if (write(l->wake, &one, 8) < 0) {} }

int ALooper_addFd(ALooper *l, int fd, int ident, int events, ALooper_callbackFunc cb, void *data)
{
    pthread_mutex_lock(&l->mu);
    int ok = l->n < 64;
    if (ok) l->w[l->n++] = (struct watch){ fd, ident, events, cb, data };
    pthread_mutex_unlock(&l->mu);
    ALooper_wake(l);
    return ok ? 1 : -1;
}
int ALooper_removeFd(ALooper *l, int fd)
{
    int found = 0;
    pthread_mutex_lock(&l->mu);
    for (int i = 0; i < l->n; i++)
        if (l->w[i].fd == fd) { l->w[i] = l->w[--l->n]; found = 1; break; }
    pthread_mutex_unlock(&l->mu);
    return found;
}

int ALooper_pollOnce(int timeoutMillis, int *outFd, int *outEvents, void **outData)
{
    ALooper *l = ALooper_prepare(0);
    struct pollfd p[65];
    struct watch w[64];
    pthread_mutex_lock(&l->mu);
    int n = l->n;
    memcpy(w, l->w, n * sizeof *w);
    pthread_mutex_unlock(&l->mu);
    p[0] = (struct pollfd){ l->wake, POLLIN, 0 };
    for (int i = 0; i < n; i++) p[i + 1] = (struct pollfd){ w[i].fd, (short)((w[i].events & 1 ? POLLIN : 0) | (w[i].events & 2 ? POLLOUT : 0)), 0 };
    int r = poll(p, n + 1, timeoutMillis);
    if (r < 0) return errno == EINTR ? ALOOPER_POLL_WAKE : ALOOPER_POLL_ERROR;
    if (r == 0) return ALOOPER_POLL_TIMEOUT;
    if (p[0].revents) { uint64_t v; if (read(l->wake, &v, 8) < 0) {} return ALOOPER_POLL_WAKE; }
    for (int i = 0; i < n; i++) {
        if (!p[i + 1].revents) continue;
        int ev = (p[i + 1].revents & POLLIN ? 1 : 0) | (p[i + 1].revents & POLLOUT ? 2 : 0) | (p[i + 1].revents & POLLERR ? 4 : 0) | (p[i + 1].revents & POLLHUP ? 8 : 0);
        if (w[i].cb) { if (!w[i].cb(w[i].fd, ev, w[i].data)) ALooper_removeFd(l, w[i].fd); return ALOOPER_POLL_CALLBACK; }
        if (outFd) *outFd = w[i].fd;
        if (outEvents) *outEvents = ev;
        if (outData) *outData = w[i].data;
        return w[i].ident;
    }
    return ALOOPER_POLL_TIMEOUT;
}
int ALooper_pollAll(int timeoutMillis, int *outFd, int *outEvents, void **outData)
{ return ALooper_pollOnce(timeoutMillis, outFd, outEvents, outData); }

/* ---- AConfiguration: defaults of a phone-sized en-US device ---- */

typedef struct AConfiguration { int32_t density, orientation, sdk; } AConfiguration;
AConfiguration *AConfiguration_new(void) { AConfiguration *c = calloc(1, sizeof *c); c->density = 160; c->orientation = 1; c->sdk = 36; return c; }
void AConfiguration_delete(AConfiguration *c) { free(c); }
void AConfiguration_fromAssetManager(AConfiguration *c, AAssetManager *m) { (void)c; (void)m; }
void AConfiguration_getLanguage(AConfiguration *c, char *o) { (void)c; o[0] = 'e'; o[1] = 'n'; }
void AConfiguration_getCountry(AConfiguration *c, char *o) { (void)c; o[0] = 'U'; o[1] = 'S'; }
int32_t AConfiguration_getDensity(AConfiguration *c) { return c->density; }
int32_t AConfiguration_getOrientation(AConfiguration *c) { return c->orientation; }
int32_t AConfiguration_getSdkVersion(AConfiguration *c) { return c->sdk; }
int32_t AConfiguration_getMcc(AConfiguration *c) { (void)c; return 0; }
int32_t AConfiguration_getMnc(AConfiguration *c) { (void)c; return 0; }
int32_t AConfiguration_getScreenSize(AConfiguration *c) { (void)c; return 2; }
int32_t AConfiguration_getScreenLong(AConfiguration *c) { (void)c; return 0; }
int32_t AConfiguration_getUiModeType(AConfiguration *c) { (void)c; return 1; }
int32_t AConfiguration_getUiModeNight(AConfiguration *c) { (void)c; return 0; }
int32_t AConfiguration_getTouchscreen(AConfiguration *c) { (void)c; return 3; }
int32_t AConfiguration_getKeyboard(AConfiguration *c) { (void)c; return 2; }
int32_t AConfiguration_getNavigation(AConfiguration *c) { (void)c; return 0; }
int32_t AConfiguration_getKeysHidden(AConfiguration *c) { (void)c; return 0; }
int32_t AConfiguration_getNavHidden(AConfiguration *c) { (void)c; return 0; }

/* ---- ANativeWindow: the surface path is not wired yet (P4.3 GL); these report "no window" ---- */

typedef struct ANativeWindow ANativeWindow;
int32_t ANativeWindow_getWidth(ANativeWindow *w) { (void)w; return 0; }
int32_t ANativeWindow_getHeight(ANativeWindow *w) { (void)w; return 0; }
int32_t ANativeWindow_getFormat(ANativeWindow *w) { (void)w; return 1; }
void ANativeWindow_acquire(ANativeWindow *w) { (void)w; }
void ANativeWindow_release(ANativeWindow *w) { (void)w; }
ANativeWindow *ANativeWindow_fromSurface(void *env, void *surface) { (void)env; (void)surface; return NULL; }
