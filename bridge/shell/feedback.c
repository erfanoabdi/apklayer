/* What the compositor imports through zwp_linux_dmabuf_v1: from v4 its default feedback (main device: the GPU it renders on, and
 * tranches of format/modifier pairs from a shared table), before v4 the format/modifier events. Kept: the formats it takes with the
 * LINEAR modifier (dmabuf.c) and the main device (`apklayer-shell --compositor-gpu`, read by the CLI: cli/gpus.cpp). */
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/sysmacros.h>
#include "shell.h"

uint32_t dmabuf_linear[DMABUF_LINEAR_MAX];
int dmabuf_linear_n;
dev_t dmabuf_main_device;
int dmabuf_have_main_device;

static void add_linear(uint32_t fmt)
{
    for (int i = 0; i < dmabuf_linear_n; i++) if (dmabuf_linear[i] == fmt) return;
    if (dmabuf_linear_n < DMABUF_LINEAR_MAX) dmabuf_linear[dmabuf_linear_n++] = fmt;
}

/* before v4 */
static void dmabuf_format(void *d, struct zwp_linux_dmabuf_v1 *m, uint32_t fmt) { (void)d; (void)m; (void)fmt; }
static void dmabuf_modifier(void *d, struct zwp_linux_dmabuf_v1 *m, uint32_t fmt, uint32_t hi, uint32_t lo)
{
    (void)d; (void)m;
    if (hi == 0 && lo == 0) add_linear(fmt);  /* DRM_FORMAT_MOD_LINEAR is accepted for this format */
}
static const struct zwp_linux_dmabuf_v1_listener dmabuf_listener = { dmabuf_format, dmabuf_modifier };

/* v4 feedback */
struct table_entry { uint32_t format, pad; uint64_t modifier; };
static const struct table_entry *table;
static size_t table_n;

static void fb_done(void *d, struct zwp_linux_dmabuf_feedback_v1 *f) { (void)d; (void)f; }
static void fb_table(void *d, struct zwp_linux_dmabuf_feedback_v1 *f, int32_t fd, uint32_t size)
{
    (void)d; (void)f;
    if (table) munmap((void *)table, table_n * sizeof *table);
    table = mmap(NULL, size, PROT_READ, MAP_PRIVATE, fd, 0);
    table_n = table == MAP_FAILED ? 0 : size / sizeof *table;
    if (table == MAP_FAILED) table = NULL;
    close(fd);
}
static void fb_main_device(void *d, struct zwp_linux_dmabuf_feedback_v1 *f, struct wl_array *dev)
{
    (void)d; (void)f;
    if (dev->size != sizeof(dev_t)) return;
    memcpy(&dmabuf_main_device, dev->data, sizeof(dev_t));
    dmabuf_have_main_device = 1;
}
static void fb_tranche_done(void *d, struct zwp_linux_dmabuf_feedback_v1 *f) { (void)d; (void)f; }
static void fb_tranche_target(void *d, struct zwp_linux_dmabuf_feedback_v1 *f, struct wl_array *dev) { (void)d; (void)f; (void)dev; }
static void fb_tranche_formats(void *d, struct zwp_linux_dmabuf_feedback_v1 *f, struct wl_array *indices)
{
    (void)d; (void)f;
    const uint16_t *idx;
    wl_array_for_each(idx, indices)
        if (table && *idx < table_n && table[*idx].modifier == 0) add_linear(table[*idx].format);
}
static void fb_tranche_flags(void *d, struct zwp_linux_dmabuf_feedback_v1 *f, uint32_t flags) { (void)d; (void)f; (void)flags; }
static const struct zwp_linux_dmabuf_feedback_v1_listener feedback_listener = {
    fb_done, fb_table, fb_main_device, fb_tranche_done, fb_tranche_target, fb_tranche_formats, fb_tranche_flags,
};

void dmabuf_bind(struct wl_registry *r, uint32_t name, uint32_t ver)
{
    dmabuf_mgr = wl_registry_bind(r, name, &zwp_linux_dmabuf_v1_interface, ver < 4 ? ver : 4);
    if (ver >= 4)  /* v4 compositors no longer send format/modifier events */
        zwp_linux_dmabuf_feedback_v1_add_listener(zwp_linux_dmabuf_v1_get_default_feedback(dmabuf_mgr), &feedback_listener, NULL);
    else
        zwp_linux_dmabuf_v1_add_listener(dmabuf_mgr, &dmabuf_listener, NULL);
}

/* The compositor's GPU as its sysfs device directory (the same for its primary and render node); 1 when unknown. */
int print_compositor_gpu(void)
{
    if (!dmabuf_have_main_device) { fprintf(stderr, "[shell] the compositor gives no dmabuf feedback (linux-dmabuf v4)\n"); return 1; }
    char link[64], path[PATH_MAX];
    snprintf(link, sizeof link, "/sys/dev/char/%u:%u/device", major(dmabuf_main_device), minor(dmabuf_main_device));
    if (!realpath(link, path)) { fprintf(stderr, "[shell] %s: %s\n", link, strerror(errno)); return 1; }
    printf("%s\n", path);
    return 0;
}
