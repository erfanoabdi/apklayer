/* Anime4K's CNN passes (shaders: sharpen_more.h, sharpen_a4k_cnn.h) into three half-float textures of the source's size (T0..T2) and one RGBA8 texture of twice the
 * source's size (T3); the last pass (Lanczos) draws into the output dmabuf. Included by sharpen.c after its helpers. */
struct a4k { GLuint prog[6], tex[4], fbo[4]; int w, h, tried; };

#ifndef GL_HALF_FLOAT_OES
#define GL_HALF_FLOAT_OES 0x8D61
#endif

static int a4k_target(int i, struct a4k *a, int w, int h)
{
    glGenTextures(1, &a->tex[i]);
    glBindTexture(GL_TEXTURE_2D, a->tex[i]);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, i < 3 ? GL_HALF_FLOAT_OES : GL_UNSIGNED_BYTE, NULL);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glGenFramebuffers(1, &a->fbo[i]);
    glBindFramebuffer(GL_FRAMEBUFFER, a->fbo[i]);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, a->tex[i], 0);
    return glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
}

/* 1: the passes are ready for this source size (0: not on this GPU; the caller draws with FSR) */
static int a4k_ready(struct a4k **pa, int w, int h)
{
    struct a4k *a = *pa;
    if (!a) a = *pa = calloc(1, sizeof *a);
    if (!a) return 0;
    if (!a->tried) {
        a->tried = 1;
        const char *fs[6] = { FS_A4K_C1, FS_A4K_C2, FS_A4K_C3, FS_A4K_C4, FS_A4K_D2S, FS_A4K_FIN };
        for (int i = 0; i < 6; i++) if (!(a->prog[i] = program(fs[i]))) { a->tried = 2; fprintf(stderr, "[sharpen] Anime4K shader %d unavailable on this GPU: FSR 1 is used\n", i); }
    }
    if (a->tried == 2) return 0;
    if (a->w != w || a->h != h) {
        for (int i = 0; i < 4; i++) { if (a->fbo[i]) glDeleteFramebuffers(1, &a->fbo[i]); if (a->tex[i]) glDeleteTextures(1, &a->tex[i]); a->fbo[i] = a->tex[i] = 0; }
        for (int i = 0; i < 4; i++) if (!a4k_target(i, a, i < 3 ? w : 2 * w, i < 3 ? h : 2 * h)) { a->tried = 2; fprintf(stderr, "[sharpen] Anime4K: no half-float render target on this GPU: FSR 1 is used\n"); return 0; }
        a->w = w; a->h = h;
    }
    return 1;
}

/* one pass into `fbo` (w x h); `src` is the size the shader's texel maths uses; ext: the app's buffer on unit 0, t[i] 2D textures under the names[i] samplers */
static void a4k_pass(struct a4k *a, int p, GLuint fbo, int w, int h, int sw, int sh, GLuint ext, const GLuint *t, const char **names, int nt, GLuint vbo)
{
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glViewport(0, 0, w, h);
    glUseProgram(a->prog[p]);
    glUniform2f(glGetUniformLocation(a->prog[p], "src"), (float)sw, (float)sh);
    if (ext) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, ext);
        glUniform1i(glGetUniformLocation(a->prog[p], "tex"), 0);
    }
    for (int i = 0; i < nt; i++) {
        glActiveTexture(GL_TEXTURE1 + i);
        glBindTexture(GL_TEXTURE_2D, t[i]);
        glUniform1i(glGetUniformLocation(a->prog[p], names[i]), 1 + i);
    }
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    draw();
    glActiveTexture(GL_TEXTURE0);
}

/* the six passes; the last into `out` (the output dmabuf's framebuffer) */
static void a4k_run(struct a4k *a, GLuint ext, int sw, int sh, int w, int h, GLuint out, GLuint vbo)
{
    glDisable(GL_BLEND);
    a4k_pass(a, 0, a->fbo[0], sw, sh, sw, sh, ext, NULL, NULL, 0, vbo);                                                        /* conv 1: app buffer -> T0 */
    a4k_pass(a, 1, a->fbo[1], sw, sh, sw, sh, 0, (GLuint[]){ a->tex[0] }, (const char *[]){ "g" }, 1, vbo);                    /* conv 2: T0 -> T1 */
    a4k_pass(a, 2, a->fbo[2], sw, sh, sw, sh, 0, (GLuint[]){ a->tex[1] }, (const char *[]){ "n" }, 1, vbo);                    /* conv 3: T1 -> T2 */
    a4k_pass(a, 3, a->fbo[0], sw, sh, sw, sh, 0, (GLuint[]){ a->tex[2] }, (const char *[]){ "dir" }, 1, vbo);                  /* conv 4: T2 -> T0 */
    a4k_pass(a, 4, a->fbo[3], 2 * sw, 2 * sh, sw, sh, ext, (GLuint[]){ a->tex[0] }, (const char *[]){ "g" }, 1, vbo);         /* depth-to-space + residual: T3 (2x) */
    a4k_pass(a, 5, out, w, h, 2 * sw, 2 * sh, 0, (GLuint[]){ a->tex[3] }, (const char *[]){ "g" }, 1, vbo);                    /* Lanczos 3 to the output */
}
