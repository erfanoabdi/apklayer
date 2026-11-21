/* A window path for compositors without zwp_linux_dmabuf_v1 (Lomiri/Mir on a Halium phone: android_wlegl gralloc handles only, and the host allocator's
 * buffers are not gralloc buffers): the dmabuf of a DMABUF_FRAME is mapped (the host allocator's linear dumb buffers map; a tiled or compressed one does not)
 * and each frame is copied into a wl_shm buffer. Slower than the zero-copy path, which stays the one wherever the compositor has linux-dmabuf. */
#define _GNU_SOURCE
#include <linux/dma-buf.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>
#include "shell.h"

#define FOURCC_ARGB8888 0x34325241u
#define FOURCC_XRGB8888 0x34325258u
#define FOURCC_ABGR8888 0x34324241u
#define FOURCC_XBGR8888 0x34324258u
#define OUT_N 3

struct dshm {
    struct { void *map; size_t len; int fd, stride, w, h; size_t off; uint32_t fourcc; } in[DBUF_MAX];
    struct { struct wl_buffer *b; void *map; size_t len; int busy; struct dshm *owner; } out[OUT_N];
    int w, h, next;
    struct window *win;
};

static void out_release(void *data, struct wl_buffer *b) { (void)b; *(int *)data = 0; }
static const struct wl_buffer_listener out_listener = { out_release };

static void out_free(struct dshm *s)
{
    for (int i = 0; i < OUT_N; i++) {
        if (s->out[i].b) wl_buffer_destroy(s->out[i].b);
        if (s->out[i].map) munmap(s->out[i].map, s->out[i].len);
        s->out[i].b = NULL; s->out[i].map = NULL;
    }
}

static int out_make(struct dshm *s, int w, int h, uint32_t fourcc)
{
    out_free(s);
    size_t len = (size_t)w * h * 4;
    for (int i = 0; i < OUT_N; i++) {
        int fd = memfd_create("apklayer-dmabuf-shm", MFD_CLOEXEC);
        if (fd < 0 || ftruncate(fd, (off_t)len) < 0) { if (fd >= 0) close(fd); return -1; }
        void *map = mmap(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        if (map == MAP_FAILED) { close(fd); return -1; }
        struct wl_shm_pool *pool = wl_shm_create_pool(shm, fd, (int32_t)len);
        s->out[i].b = wl_shm_pool_create_buffer(pool, 0, w, h, w * 4, fourcc == FOURCC_ARGB8888 || fourcc == FOURCC_ABGR8888 ? WL_SHM_FORMAT_ARGB8888 : WL_SHM_FORMAT_XRGB8888);
        wl_shm_pool_destroy(pool);
        close(fd);
        s->out[i].map = map; s->out[i].len = len; s->out[i].busy = 0;
        wl_buffer_add_listener(s->out[i].b, &out_listener, &s->out[i].busy);
    }
    s->w = w; s->h = h;
    return 0;
}

static struct dshm *state(struct window *w)
{
    if (!w->dshm) { w->dshm = calloc(1, sizeof(struct dshm)); if (w->dshm) w->dshm->win = w; }
    return w->dshm;
}

static int refuse(struct window *w, int buf)
{
    send_msg(w->client, MSG_DMABUF_RELEASE, (int32_t[]){ w->id, buf }, 2);  /* the producer's slot must not starve */
    return -1;
}

/* m: the DMABUF_FRAME payload; a first frame of the slot (fd >= 0) maps its dmabuf. 0 when the slot can be shown. */
int dshm_import(struct window *w, const int32_t *m, int fd)
{
    struct dshm *s = state(w);
    int buf = m[1];
    if (!s) return refuse(w, buf);
    if (fd >= 0) {
        uint64_t modifier = ((uint64_t)(uint32_t)m[7] << 32) | (uint32_t)m[6];
        if (modifier != 0 && modifier != 0x00ffffffffffffffull) {  /* tiled or compressed: mapped, it would read as garbage */
            fprintf(stderr, "[shell] no zwp_linux_dmabuf_v1 and the dmabuf's modifier 0x%016llx is not linear\n", (unsigned long long)modifier);
            return refuse(w, buf);
        }
        uint32_t fourcc = (uint32_t)m[2];
        if (fourcc != FOURCC_ARGB8888 && fourcc != FOURCC_XRGB8888 && fourcc != FOURCC_ABGR8888 && fourcc != FOURCC_XBGR8888) {
            fprintf(stderr, "[shell] no zwp_linux_dmabuf_v1 and format 0x%08x is not copied\n", fourcc);
            return refuse(w, buf);
        }
        if (s->in[buf].map) { munmap(s->in[buf].map, s->in[buf].len); close(s->in[buf].fd); s->in[buf].map = NULL; }
        size_t len = (size_t)(uint32_t)m[12] + (size_t)(uint32_t)m[8] * (uint32_t)m[4];
        void *map = mmap(NULL, len, PROT_READ, MAP_SHARED, fd, 0);
        if (map == MAP_FAILED) { fprintf(stderr, "[shell] no zwp_linux_dmabuf_v1 and the dmabuf does not map (modifier 0x%08x%08x)\n", (uint32_t)m[7], (uint32_t)m[6]); return refuse(w, buf); }
        s->in[buf].map = map; s->in[buf].len = len; s->in[buf].fd = dup(fd);
        s->in[buf].stride = m[8]; s->in[buf].fourcc = fourcc; s->in[buf].w = m[3]; s->in[buf].h = m[4]; s->in[buf].off = (uint32_t)m[12];
        fprintf(stderr, "[shell] no zwp_linux_dmabuf_v1: window frames are copied into wl_shm buffers (buffer %d, %dx%d)\n", buf, m[3], m[4]);
    }
    return s->in[buf].map ? 0 : refuse(w, buf);
}

void dshm_show(struct window *w, int buf, const int32_t *damage)
{
    struct dshm *s = w->dshm;
    if (!s || buf < 0 || buf >= DBUF_MAX || !s->in[buf].map) { if (buf >= 0 && buf < DBUF_MAX) refuse(w, buf); return; }
    int width = s->in[buf].w, height = s->in[buf].h;
    if ((s->w != width || s->h != height || !s->out[0].b) && out_make(s, width, height, s->in[buf].fourcc) < 0) { refuse(w, buf); return; }
    int o = -1;
    for (int k = 0; k < OUT_N && o < 0; k++) if (!s->out[(s->next + k) % OUT_N].busy) o = (s->next + k) % OUT_N;
    if (o < 0) o = s->next;  /* all held by the compositor: reuse the oldest (a torn frame beats none) */
    s->next = (o + 1) % OUT_N;
    struct dma_buf_sync sync = { DMA_BUF_SYNC_START | DMA_BUF_SYNC_READ };
    ioctl(s->in[buf].fd, DMA_BUF_IOCTL_SYNC, &sync);
    int swap = s->in[buf].fourcc == FOURCC_ABGR8888 || s->in[buf].fourcc == FOURCC_XBGR8888;
    for (int y = 0; y < height; y++) {
        const uint32_t *src = (const uint32_t *)((const char *)s->in[buf].map + s->in[buf].off + (size_t)y * s->in[buf].stride);
        uint32_t *dst = (uint32_t *)((char *)s->out[o].map + (size_t)y * width * 4);
        if (!swap) memcpy(dst, src, (size_t)width * 4);
        else for (int x = 0; x < width; x++) { uint32_t p = src[x]; dst[x] = (p & 0xff00ff00u) | (p & 0xff) << 16 | (p >> 16 & 0xff); }
    }
    sync.flags = DMA_BUF_SYNC_END | DMA_BUF_SYNC_READ;
    ioctl(s->in[buf].fd, DMA_BUF_IOCTL_SYNC, &sync);
    s->out[o].busy = 1;
    w->framed = 1;
    wl_surface_attach(w->surface, s->out[o].b, 0, 0);
    surface_damage(w->surface, damage[0], damage[1], damage[2], damage[3]);
    wl_surface_commit(w->surface);
    send_msg(w->client, MSG_DMABUF_RELEASE, (int32_t[]){ w->id, buf }, 2);  /* copied: the producer may draw again */
}

void dshm_destroy(struct window *w)
{
    struct dshm *s = w->dshm;
    if (!s) return;
    out_free(s);
    for (int i = 0; i < DBUF_MAX; i++) if (s->in[i].map) { munmap(s->in[i].map, s->in[i].len); close(s->in[i].fd); }
    free(s);
    w->dshm = NULL;
}
