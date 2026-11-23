/* The temporal stabiliser of the render filter (GLES backend, apklayer patch, not AMD's): see sharpen_temporal.c. */
#ifndef APKLAYER_SHARPEN_TEMPORAL_H
#define APKLAYER_SHARPEN_TEMPORAL_H
#include "sharpen_smaa.h"  /* struct rt */

struct tmpl;

/* The context current; `prog` is sharpen.c's shader builder. NULL when a shader does not build (then there is no temporal pass). */
struct tmpl *tmpl_create(GLuint (*prog)(const char *fs));
/* Blends the previous output into the finished one (out_tex / out_fbo: the output slot, ow x oh) where the app's picture (the external texture `src`, sw x sh) changed little since the
 * last call, and keeps this output and this source for the next call. The history restarts when a size or `sharp` differs from the last call. `rt_ensure` makes the targets. */
void tmpl_run(struct tmpl *t, GLuint (*rt_ensure)(struct rt *, int, int), GLuint vbo, GLuint out_tex, GLuint out_fbo, int ow, int oh, GLuint src, int sw, int sh, float sharp);
void tmpl_free(struct tmpl *t);
#endif
