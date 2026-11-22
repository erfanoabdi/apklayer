/* The shell's optional upscale pass (`apklayer settings` render-filter sharp): a game that renders at a render scale other than the window's
 * pixels is shown through a Catmull-Rom bicubic resample and a contrast adaptive sharpening step (the technique of AMD's CAS) instead of the
 * compositor's bilinear stretch. The app's dmabufs are imported into EGL (the first render node that takes them), the result is drawn into a ring
 * of dmabufs the window shows. No Wayland here (the window glue is sharpen_win.c; tests/hals/sharpen-test.c runs this alone). Every function
 * can fail: the caller keeps the zero-copy path then. Single threaded (the shell's main thread). */
#ifndef APKLAYER_SHARPEN_H
#define APKLAYER_SHARPEN_H
#include <stdint.h>

struct sharpen;

/* what the compositor needs to import one output buffer (fd: a dup, the caller closes it) */
struct sharpen_out {
    int fd, w, h;
    uint32_t fourcc, stride, offset;
    uint64_t modifier;
};

struct sharpen *sharpen_open(void);
void sharpen_close(struct sharpen *s);

/* The modifiers the compositor takes for XRGB8888 (n may be 0: then only LINEAR output buffers are made, as the caller knows the compositor takes those
 * through its own table); the output ring is allocated with one of them. Before the first sharpen_render. */
void sharpen_accept(struct sharpen *s, const uint64_t *mods, int n);

/* The slow setup alone (the device that takes the sample buffer, EGL, shaders): safe from a helper thread while nothing else uses `s`; sharpen_import
 * calls it itself when it has not run. */
int sharpen_prepare(struct sharpen *s, uint32_t fourcc, int w, int h, int nplanes, const int *fds, const uint32_t *strides, const uint32_t *offsets,
                    uint64_t modifier);

/* one app buffer by index 0..7: its planes as the DMABUF_FRAME message carries them (the caller keeps its fds). */
int sharpen_import(struct sharpen *s, int buf, uint32_t fourcc, int w, int h, int nplanes, const int *fds, const uint32_t *strides,
                   const uint32_t *offsets, uint64_t modifier);
void sharpen_forget(struct sharpen *s, int buf);

/* 0: FSR 1 (EASU + RCAS), 1: NVIDIA Image Scaling (one pass; the sharpness is NIS's 0..1 slider), 2: Snapdragon GSR 1 (one pass), 3: Lanczos 3, 4: Anime4K (five passes). Takes effect with the next sharpen_render. */
void sharpen_set_mode(struct sharpen *s, int mode);
/* Phases of the NIS / SGSR 1 / Lanczos upscale: 1..4, 0 the default (APKLAYER_SHARPEN_STAGES, else 1). Takes effect with the next sharpen_render. */
void sharpen_set_stages(struct sharpen *s, int n);

/* Draws buffer `buf` into output slot `*slot` (0..2, the first one not in use) of out_w x out_h; sharpness 0..1 (0.5: CAS default). Returns 0 and the
 * slot, with *fresh set when the slot's dmabuf is new (the caller makes its wl_buffer from sharpen_slot_info), -1 when nothing could be drawn, -2 when all output buffers are held (draw it again after a sharpen_slot_release). */
int sharpen_render(struct sharpen *s, int buf, int out_w, int out_h, float sharpness, int *slot, int *fresh);
int sharpen_slot_info(struct sharpen *s, int slot, struct sharpen_out *out);
void sharpen_slot_release(struct sharpen *s, int slot);   /* the compositor is done with it */
/* test aid: the pixels of the last rendered slot (out_w * out_h * 4, XRGB little endian) */
int sharpen_readback(struct sharpen *s, int slot, void *rgba);
#endif
