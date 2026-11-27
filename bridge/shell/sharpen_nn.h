/* The neural upscale pass (apklayer patch; docs/PLAN.md "Neural upscaler"): a small 2x network distilled from waifu2x upconv_7 (nagadomi/waifu2x, MIT) as OpenGL ES 3.1 compute, whose
 * result is resampled (Catmull-Rom) to the output size, so any render scale from 50 to 100 % works. Weights: tools/nnup/export_student.py. sharpen_nn.c. */
#ifndef APKLAYER_SHARPEN_NN_H
#define APKLAYER_SHARPEN_NN_H
#include "sharpen_smaa.h"

struct nn;

/* The context current; `prog` is sharpen.c's shader builder. NULL when the context is below ES 3.1, a shader does not build or the weights file cannot be read
 * (path: $APKLAYER_NN_WEIGHTS, else `dflt`). */
struct nn *nn_create(GLuint (*prog)(const char *fs), const char *dflt);
/* The app's external image src (w x h) copied into snapshot `snap` (0 or 1): once the GPU has run the copy the app's buffer is free. 0 or -1. */
int nn_capture(struct nn *n, GLuint src, int w, int h, int snap, GLuint vbo);
int nn_snap_size(struct nn *n, int snap, int *w, int *h);
/* One frame from snapshot `snap`: the network, then the 2x picture drawn at ow x oh into the framebuffer dst. 0 or -1 (nothing drawn). */
int nn_run(struct nn *n, int snap, GLuint dst, int ow, int oh, GLuint vbo);
void nn_free(struct nn *n);
#endif
