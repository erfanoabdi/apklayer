/* Can the host GL stack share a dmabuf between two contexts the way the TextureView path does? One context fills a gbm buffer (through an EGLImage bound to a
 * texture used as a framebuffer attachment), another imports the same dmabuf as an EGLImage and reads it back as a 2D texture or as an external texture.
 * Build: cc -o dmabuf-share tests/vm/dmabuf-share.c -lEGL -lGLESv2 -lgbm.  Prints PASS/FAIL per variant. */
#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <fcntl.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define DRM_FORMAT_MOD_INVALID 0x00ffffffffffffffULL
#define W 256
#define H 256
static EGLDisplay dpy;
static PFNEGLCREATEIMAGEKHRPROC createImage;
static PFNGLEGLIMAGETARGETTEXTURE2DOESPROC targetTexture;

static EGLContext makeContext(void) {
    const EGLint attrs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext c = eglCreateContext(dpy, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, attrs);
    if (c == EGL_NO_CONTEXT) { fprintf(stderr, "eglCreateContext 0x%x\n", eglGetError()); exit(2); }
    return c;
}

static EGLImage importBo(struct gbm_bo* bo, int* fdOut) {
    int fd = gbm_bo_get_fd(bo);
    uint64_t mod = gbm_bo_get_modifier(bo);
    EGLint a[] = {EGL_WIDTH, W, EGL_HEIGHT, H, EGL_LINUX_DRM_FOURCC_EXT, (EGLint)gbm_bo_get_format(bo), EGL_DMA_BUF_PLANE0_FD_EXT, fd,
                  EGL_DMA_BUF_PLANE0_OFFSET_EXT, 0, EGL_DMA_BUF_PLANE0_PITCH_EXT, (EGLint)gbm_bo_get_stride(bo),
                  EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT, (EGLint)(mod & 0xffffffff), EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT, (EGLint)(mod >> 32), EGL_NONE};
    if (mod == DRM_FORMAT_MOD_INVALID) a[14] = EGL_NONE;
    *fdOut = fd;
    return createImage(dpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, NULL, a);
}

static int readCenter(unsigned fbo, unsigned char* px) {
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glReadPixels(W / 2, H / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
    return glGetError();
}

static void variant(struct gbm_device* gbm, unsigned usage, const char* name, int external, int explicitFence) {
    struct gbm_bo* bo = gbm_bo_create(gbm, W, H, GBM_FORMAT_ABGR8888, usage);
    if (!bo) { printf("SKIP %s: gbm_bo_create failed\n", name); return; }
    EGLContext producer = makeContext(), consumer = makeContext();
    int fd1, fd2;
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, producer);
    EGLImage img = importBo(bo, &fd1);
    if (img == EGL_NO_IMAGE) { printf("FAIL %s: producer import 0x%x\\n", name, eglGetError()); return; }
    GLuint tex, fbo;
    glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_2D, tex); targetTexture(GL_TEXTURE_2D, img);
    glGenFramebuffers(1, &fbo); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { printf("FAIL %s: producer fbo incomplete\n", name); return; }
    glClearColor(0.f, 1.f, 0.f, 1.f); glClear(GL_COLOR_BUFFER_BIT);
    if (explicitFence) glFinish(); else glFlush();
    unsigned char back[4] = {0};
    readCenter(fbo, back);
    int producerSees = back[1] > 200;
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, consumer);
    EGLImage img2 = importBo(bo, &fd2);
    if (img2 == EGL_NO_IMAGE) { printf("FAIL %s: consumer import 0x%x\n", name, eglGetError()); return; }
    GLenum target = external ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D;
    GLuint ctex; glGenTextures(1, &ctex); glBindTexture(target, ctex); targetTexture(target, img2);
    glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);  /* a 2D texture without mipmaps is incomplete with the default filter */
    GLuint outTex, outFbo;
    glGenTextures(1, &outTex); glBindTexture(GL_TEXTURE_2D, outTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glGenFramebuffers(1, &outFbo); glBindFramebuffer(GL_FRAMEBUFFER, outFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, outTex, 0);
    const char* vs = "#version 300 es\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0,1);}";
    const char* fsExt = "#version 300 es\n#extension GL_OES_EGL_image_external_essl3 : require\nprecision mediump float;uniform samplerExternalOES s;out vec4 c;void main(){c=texture(s,gl_FragCoord.xy/256.);}";
    const char* fs2d = "#version 300 es\nprecision mediump float;uniform sampler2D s;out vec4 c;void main(){c=texture(s,gl_FragCoord.xy/256.);}";
    GLuint v = glCreateShader(GL_VERTEX_SHADER), f = glCreateShader(GL_FRAGMENT_SHADER), prog = glCreateProgram();
    glShaderSource(v, 1, &vs, NULL); glCompileShader(v);
    const char* fsrc = external ? fsExt : fs2d;
    glShaderSource(f, 1, &fsrc, NULL); glCompileShader(f);
    GLint ok = 0; glGetShaderiv(f, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[512]; glGetShaderInfoLog(f, 512, NULL, log); printf("FAIL %s: shader: %s\n", name, log); return; }
    glAttachShader(prog, v); glAttachShader(prog, f); glLinkProgram(prog); glUseProgram(prog);
    glViewport(0, 0, W, H); glActiveTexture(GL_TEXTURE0); glBindTexture(target, ctex);
    glDrawArrays(GL_TRIANGLES, 0, 3); glFinish();
    unsigned char got[4] = {0};
    readCenter(outFbo, got);
    printf("%s %s: producer reads %d,%d,%d,%d; consumer samples %d,%d,%d,%d\n", (got[1] > 200 ? "PASS" : "FAIL"), name,
           back[0], back[1], back[2], back[3], got[0], got[1], got[2], got[3]);
    (void)producerSees; close(fd1); close(fd2);
    gbm_bo_destroy(bo);
}

int main(void) {
    int drm = open("/dev/dri/renderD128", O_RDWR);
    struct gbm_device* gbm = gbm_create_device(drm);
    PFNEGLGETPLATFORMDISPLAYEXTPROC getDisplay = (PFNEGLGETPLATFORMDISPLAYEXTPROC)eglGetProcAddress("eglGetPlatformDisplayEXT");
    dpy = getDisplay(EGL_PLATFORM_GBM_KHR, gbm, NULL);
    EGLint major, minor;
    if (!eglInitialize(dpy, &major, &minor)) { fprintf(stderr, "eglInitialize 0x%x\n", eglGetError()); return 2; }
    createImage = (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
    targetTexture = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
    eglBindAPI(EGL_OPENGL_ES_API);
    printf("EGL %d.%d, %s\n", major, minor, eglQueryString(dpy, EGL_VENDOR));
    variant(gbm, GBM_BO_USE_RENDERING, "render buffer, 2D texture", 0, 1);
    variant(gbm, GBM_BO_USE_RENDERING, "render buffer, external texture", 1, 1);
    variant(gbm, GBM_BO_USE_RENDERING | GBM_BO_USE_LINEAR, "linear render buffer, 2D texture", 0, 1);
    variant(gbm, GBM_BO_USE_RENDERING | GBM_BO_USE_LINEAR, "linear render buffer, external texture", 1, 1);
    variant(gbm, GBM_BO_USE_RENDERING | GBM_BO_USE_LINEAR, "linear render buffer, external texture, glFlush only", 1, 0);
    return 0;
}
