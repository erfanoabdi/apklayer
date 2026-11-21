/* Probe of the Linux ANGLE host libraries (runtime/angle/README.md "ANGLE host libraries on the X23"): ANGLE (Vulkan, GBM display of /dev/dri/card0 or surfaceless) clear + readback,
 * the dma-buf formats/modifiers it advertises, and an import of a GBM buffer (argv[1]: gbm usage flags, 4 = GBM_BO_USE_RENDERING) with and without a linear modifier.
 * Build: gcc a.c -L<angle host dir> -l:libEGL_angle.so -l:libGLESv2_angle.so -Wl,-rpath,'$ORIGIN' -ldl; run with ANGLE_DEFAULT_PLATFORM=vulkan. */
#define GL_GLEXT_PROTOTYPES
#define EGL_EGLEXT_PROTOTYPES
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <dlfcn.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <linux/dma-heap.h>
#define CK(x) do{ if(!(x)){printf("FAIL line %d: %s err=0x%x\n",__LINE__,#x,eglGetError());return 1;} }while(0)
int main(int c,char**v){ setvbuf(stdout,0,_IONBF,0);
 EGLint at[]={0x3203,0x3450,0x348F,0x31DD,EGL_NONE};
 PFNEGLGETPLATFORMDISPLAYEXTPROC gpd=(void*)eglGetProcAddress("eglGetPlatformDisplayEXT");
 void*g0=dlopen("libgbm.so.1",RTLD_NOW);void*(*cd0)(int)=dlsym(g0,"gbm_create_device");void*gdev=cd0(open("/dev/dri/card0",O_RDWR));
 PFNEGLGETPLATFORMDISPLAYPROC gp=(void*)eglGetProcAddress("eglGetPlatformDisplay"); EGLDisplay d=gp(0x31D7,gdev,0); printf("gbm display %p\n",d); CK(d); EGLint a,b; CK(eglInitialize(d,&a,&b));
 printf("EGL %d.%d vendor %s\nexts %s\n",a,b,eglQueryString(d,EGL_VENDOR),eglQueryString(d,EGL_EXTENSIONS));
 EGLint ca[]={EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE}; EGLConfig cfg; EGLint n; CK(eglChooseConfig(d,ca,&cfg,1,&n)&&n);
 EGLint xa[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE}; EGLContext cx=eglCreateContext(d,cfg,0,xa); CK(cx);
 EGLint pa[]={EGL_WIDTH,64,EGL_HEIGHT,64,EGL_NONE}; EGLSurface s=eglCreatePbufferSurface(d,cfg,pa); CK(s); CK(eglMakeCurrent(d,s,s,cx));
 printf("GL_RENDERER %s\nGL_VERSION %s\n",glGetString(GL_RENDERER),glGetString(GL_VERSION));
 glClearColor(1,0,1,1); glClear(GL_COLOR_BUFFER_BIT); unsigned char px[4]; glReadPixels(0,0,1,1,GL_RGBA,GL_UNSIGNED_BYTE,px); printf("clear px %02x%02x%02x%02x\n",px[0],px[1],px[2],px[3]);
 if(c<2) return 0;
 /* dma-buf import: allocate from dma-heap, fill with a pattern, import with/without modifier */
 PFNEGLQUERYDMABUFFORMATSEXTPROC qf=(void*)eglGetProcAddress("eglQueryDmaBufFormatsEXT");
 PFNEGLQUERYDMABUFMODIFIERSEXTPROC qm=(void*)eglGetProcAddress("eglQueryDmaBufModifiersEXT");
 if(qf){EGLint nf=0; qf(d,0,0,&nf); EGLint f[64]; if(nf>64)nf=64; qf(d,nf,f,&nf); printf("dmabuf formats %d:",nf); for(int i=0;i<nf;i++)printf(" %.4s",(char*)&f[i]); puts("");
  EGLint nm=0; EGLuint64KHR m[32]; EGLBoolean ext[32]; qm(d,0x34324241,32,m,ext,&nm); printf("AB24 modifiers %d:",nm); for(int i=0;i<nm;i++)printf(" %llx",(unsigned long long)m[i]); puts("");}
 struct{unsigned len;int fd;} al; {void*g=dlopen("libgbm.so.1",RTLD_NOW);void*(*cd)(int)=dlsym(g,"gbm_create_device");void*(*bc)(void*,unsigned,unsigned,unsigned,unsigned)=dlsym(g,"gbm_bo_create");int(*gf)(void*)=dlsym(g,"gbm_bo_get_fd");unsigned(*gs)(void*)=dlsym(g,"gbm_bo_get_stride");unsigned long long(*gm)(void*)=dlsym(g,"gbm_bo_get_modifier");
  void*dev=cd(open("/dev/dri/card0",O_RDWR)); void*bo=bc(dev,64,64,0x34324241,atoi(v[1])); if(!bo){puts("gbm bo fail");return 1;} al.fd=gf(bo); al.len=gs(bo)*64; printf("gbm bo fd %d stride %u modifier %llx\n",al.fd,gs(bo),gm(bo));}
 unsigned*p=mmap(0,al.len,PROT_READ|PROT_WRITE,MAP_SHARED,al.fd,0); if(p==MAP_FAILED){perror("mmap");p=calloc(1,al.len);}else for(int i=0;i<64*64;i++)p[i]=0xff00ff00; /* ABGR8888 mem: R=0 G=ff B=0 A=ff -> wait little endian 0xAABBGGRR */
 for(int mode=0;mode<2;mode++){
  EGLint ia[40]; int k=0; ia[k++]=EGL_WIDTH;ia[k++]=64;ia[k++]=EGL_HEIGHT;ia[k++]=64;ia[k++]=EGL_LINUX_DRM_FOURCC_EXT;ia[k++]=0x34324241;
  ia[k++]=EGL_DMA_BUF_PLANE0_FD_EXT;ia[k++]=al.fd;ia[k++]=EGL_DMA_BUF_PLANE0_OFFSET_EXT;ia[k++]=0;ia[k++]=EGL_DMA_BUF_PLANE0_PITCH_EXT;ia[k++]=al.len/64;
  if(mode){ia[k++]=EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT;ia[k++]=0;ia[k++]=EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT;ia[k++]=0;} ia[k++]=EGL_NONE;
  EGLImage im=eglCreateImageKHR(d,EGL_NO_CONTEXT,EGL_LINUX_DMA_BUF_EXT,0,ia); printf("import mode%d(%s modifier linear) image=%p err=0x%x\n",mode,mode?"with":"no",im,eglGetError()); if(!im)continue;
  GLuint t,f; glGenTextures(1,&t); glBindTexture(GL_TEXTURE_2D,t); glEGLImageTargetTexture2DOES(GL_TEXTURE_2D,im); printf("target tex glerr 0x%x\n",glGetError());
  glGenFramebuffers(1,&f); glBindFramebuffer(GL_FRAMEBUFFER,f); glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,t,0);
  printf("fb status 0x%x\n",glCheckFramebufferStatus(GL_FRAMEBUFFER)); glReadPixels(5,5,1,1,GL_RGBA,GL_UNSIGNED_BYTE,px); printf("sampled px %02x%02x%02x%02x (want 00ff00ff)\n",px[0],px[1],px[2],px[3]);
  glClearColor(0,0,1,1); glClear(GL_COLOR_BUFFER_BIT); glFinish(); printf("after clear-to-blue cpu px %08x (want ffff0000)\n",p[100]);
  glBindFramebuffer(GL_FRAMEBUFFER,0);
 }
 return 0;}
