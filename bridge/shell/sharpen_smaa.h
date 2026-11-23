/* SMAA 1x for the render filter (GLES2 backend): edge detection, blending weights (area and search tables) and neighbourhood blending, three passes after
 * iryoku/smaa (MIT). sharpen_smaa.c; the shaders are sharpen_smaa_fs.h (generated), the tables sharpen_smaa_area.h / sharpen_smaa_search.h (verbatim). */
#ifndef APKLAYER_SHARPEN_SMAA_H
#define APKLAYER_SHARPEN_SMAA_H
#include <GLES2/gl2.h>

struct rt { GLuint tex, fbo; int w, h; };  /* an RGBA8 render target (LINEAR) */

struct smaa {
    GLuint (*prog)(const char *fs);              /* sharpen.c's shader builder (the shared vertex shader) */
    GLuint area, search;                         /* the lookup tables */
    GLuint edge[2], wts, blend[2];               /* programs, [source: 0 a 2D texture, 1 the app's external image]; built on first use */
    struct rt p[2];                              /* the pictures between two runs (iterations) */
    struct rt e, b;                              /* the edges and the blending weights */
};

/* Needs the context current. NULL when the tables cannot be made. */
struct smaa *smaa_create(GLuint (*prog)(const char *fs));
/* Anti-aliases src (the app's external image when ext, else a GL_TEXTURE_2D; w x h) into dst (a framebuffer of the same size) with the ULTRA preset of the reference SMAA.h (the fallback
 * where CMAA2 is unavailable; runs twice at an upscale `ratio` of 1.4 and more, with the text gate in the second run). `rt_ensure` makes the targets. 0: done, -1: a shader or target failed (dst untouched). */
int smaa_run(struct smaa *a, GLuint (*rt_ensure)(struct rt *, int, int), int ext, GLuint src, int w, int h, float ratio, GLuint dst, GLuint vbo);
#endif
