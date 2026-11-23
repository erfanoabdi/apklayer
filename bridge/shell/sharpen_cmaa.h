/* CMAA2 (Intel, MIT) for the render filter (GLES 3.1 compute): four kernels, edge detection with local contrast adaptation and shape candidates, the indirect candidate
 * pass (simple shapes, Z shapes with long line search), the deferred blend resolve. sharpen_cmaa.c; the shader is sharpen_cmaa_cs.h (generated from tools/gen/cmaa/cmaa.comp).
 * Needs an OpenGL ES 3.1 context; sharpen.c falls back to SMAA (ultra) where it is missing. */
#ifndef APKLAYER_SHARPEN_CMAA_H
#define APKLAYER_SHARPEN_CMAA_H
#include "sharpen_smaa.h"

struct cmaa;

/* The context current. NULL when the context is below ES 3.1 or a kernel does not build (one line on stderr says which). */
struct cmaa *cmaa_create(GLuint (*prog)(const char *fs));
/* Same contract as smaa_run (the source is the app's external image when ext, else a GL_TEXTURE_2D; the result goes into dst, a framebuffer of the same size): the reference's ULTRA preset (edge
 * threshold 0.05) plus colour edges, the text gate and 1..4 runs by the upscale `ratio` (output / source width; 1.4 x ratio). */
int cmaa_run(struct cmaa *c, int ext, GLuint src, int w, int h, float ratio, GLuint dst, GLuint vbo);
void cmaa_free(struct cmaa *c);
#endif
