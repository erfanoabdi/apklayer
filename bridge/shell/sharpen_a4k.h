/* Anime4K's passes (shaders: sharpen_more.h) into three RGBA8 textures of the output's size; the last pass draws into the output dmabuf. Included by sharpen.c
 * after its helpers. T0 gradients, T1 (sobel norm, refinement), T2 the line direction. */
struct a4k { GLuint prog[5], tex[3], fbo[3]; int w, h, tried; };

static GLuint a4k_target(int i, struct a4k *a, int w, int h)
{
    glGenTextures(1, &a->tex[i]);
    glBindTexture(GL_TEXTURE_2D, a->tex[i]);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glGenFramebuffers(1, &a->fbo[i]);
    glBindFramebuffer(GL_FRAMEBUFFER, a->fbo[i]);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, a->tex[i], 0);
    return glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
}

/* 1: the passes are ready for this output size (0: not on this GPU; the caller draws with FSR) */
static int a4k_ready(struct a4k **pa, int w, int h)
{
    struct a4k *a = *pa;
    if (!a) a = *pa = calloc(1, sizeof *a);
    if (!a) return 0;
    if (!a->tried) {
        a->tried = 1;
        const char *fs[5] = { FS_A4K_PRE, FS_A4K_Y, FS_A4K_X, FS_A4K_DIR, FS_A4K_APPLY };
        for (int i = 0; i < 5; i++) if (!(a->prog[i] = program(fs[i]))) { a->tried = 2; fprintf(stderr, "[sharpen] Anime4K shader %d unavailable on this GPU: FSR 1 is used\n", i); }
    }
    if (a->tried == 2) return 0;
    if (a->w != w || a->h != h) {
        for (int i = 0; i < 3; i++) { if (a->fbo[i]) glDeleteFramebuffers(1, &a->fbo[i]); if (a->tex[i]) glDeleteTextures(1, &a->tex[i]); a->fbo[i] = a->tex[i] = 0; }
        for (int i = 0; i < 3; i++) if (!a4k_target(i, a, w, h)) { a->tried = 2; fprintf(stderr, "[sharpen] Anime4K: no RGBA8 render target on this GPU\n"); return 0; }
        a->w = w; a->h = h;
    }
    return 1;
}

static void a4k_pass(struct a4k *a, int p, GLuint fbo, int w, int h, float dx, float dy, GLuint ext, const GLuint *t, const char **names, int nt, GLuint vbo)
{
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glViewport(0, 0, w, h);
    glUseProgram(a->prog[p]);
    glUniform2f(glGetUniformLocation(a->prog[p], "d"), dx, dy);
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

/* the five passes; the last into `out` (the output dmabuf's framebuffer) */
static void a4k_run(struct a4k *a, GLuint ext, int sw, int sh, int w, int h, GLuint out, GLuint vbo)
{
    float dx = 1.0f / (float)sw, dy = 1.0f / (float)sh;
    glDisable(GL_BLEND);
    a4k_pass(a, 0, a->fbo[0], w, h, dx, dy, ext, NULL, NULL, 0, vbo);                                    /* T0: luma gradients */
    a4k_pass(a, 1, a->fbo[1], w, h, dx, dy, 0, (GLuint[]){ a->tex[0] }, (const char *[]){ "g" }, 1, vbo);  /* T1: sobel norm, refinement */
    a4k_pass(a, 2, a->fbo[0], w, h, dx, dy, 0, (GLuint[]){ a->tex[1] }, (const char *[]){ "n" }, 1, vbo);  /* T0: gradients of the norm */
    a4k_pass(a, 3, a->fbo[2], w, h, dx, dy, 0, (GLuint[]){ a->tex[0], a->tex[1] }, (const char *[]){ "g", "n" }, 2, vbo);  /* T2: direction */
    a4k_pass(a, 4, out, w, h, dx, dy, ext, (GLuint[]){ a->tex[1], a->tex[2] }, (const char *[]){ "n", "dir" }, 2, vbo);
}
