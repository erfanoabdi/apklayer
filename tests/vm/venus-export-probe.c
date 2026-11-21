// Venus exports a dma-buf (buffer or linear image memory, `b` or `i`), virgl's EGL imports and samples it: RESULT PASS on the UTM VM (Venus + virgl).
// Build: gcc -I/usr/include/libdrm -o probe venus-export-probe.c -lvulkan -lEGL -lGLESv2 -lgbm
// probe: Venus allocates + exports dma-buf; import into EGL (GBM platform) and read back.
#define _GNU_SOURCE
#include <vulkan/vulkan.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <gbm.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>
#include <drm_fourcc.h>
#define W 64
#define H 64
#define CK(x) do{VkResult r=(x);if(r){printf("FAIL %s = %d\n",#x,r);exit(2);}}while(0)
static int pick(VkPhysicalDeviceMemoryProperties*m,uint32_t b){for(int i=0;i<m->memoryTypeCount;i++)if((b>>i&1)&&(m->memoryTypes[i].propertyFlags&2))return i;return __builtin_ctz(b);}
int main(int argc,char**argv){
  int useImage = argc>1 && argv[1][0]=='i';
  VkApplicationInfo ai={VK_STRUCTURE_TYPE_APPLICATION_INFO,0,"p",1,"p",1,VK_API_VERSION_1_1};
  VkInstanceCreateInfo ic={VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,0,0,&ai};
  VkInstance inst; CK(vkCreateInstance(&ic,0,&inst));
  uint32_t n=8; VkPhysicalDevice pd[8]; vkEnumeratePhysicalDevices(inst,&n,pd);
  VkPhysicalDevice phys=0;
  for(uint32_t i=0;i<n;i++){VkPhysicalDeviceDriverProperties d={VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES};VkPhysicalDeviceProperties2 p={VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2,&d};vkGetPhysicalDeviceProperties2(pd[i],&p);printf("dev %s driver %s\n",p.properties.deviceName,d.driverName);if(d.driverID==VK_DRIVER_ID_MESA_VENUS)phys=pd[i];}
  if(!phys){printf("no venus\n");return 3;}
  float pr=1; VkDeviceQueueCreateInfo q={VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,0,0,0,1,&pr};
  const char*ex[]={"VK_KHR_external_memory_fd","VK_EXT_external_memory_dma_buf","VK_KHR_external_memory"};
  VkDeviceCreateInfo dc={VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,0,0,1,&q,0,0,3,ex};
  VkDevice dev; CK(vkCreateDevice(phys,&dc,0,&dev));
  PFN_vkGetMemoryFdKHR getfd=(void*)vkGetDeviceProcAddr(dev,"vkGetMemoryFdKHR");
  VkPhysicalDeviceMemoryProperties mp; vkGetPhysicalDeviceMemoryProperties(phys,&mp);
  VkExportMemoryAllocateInfo ei={VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO,0,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
  VkDeviceMemory mem; uint32_t pitch=W*4; VkDeviceSize size=0; uint32_t bits=0; void*ptr=0;
  if(useImage){
    VkExternalMemoryImageCreateInfo ex2={VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,0,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkImageCreateInfo ii={VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,&ex2,0,VK_IMAGE_TYPE_2D,VK_FORMAT_B8G8R8A8_UNORM,{W,H,1},1,1,VK_SAMPLE_COUNT_1_BIT,VK_IMAGE_TILING_LINEAR,VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,0,0,0,VK_IMAGE_LAYOUT_UNDEFINED};
    VkImage im; CK(vkCreateImage(dev,&ii,0,&im));
    VkMemoryRequirements mr; vkGetImageMemoryRequirements(dev,im,&mr); size=mr.size;bits=mr.memoryTypeBits;
    VkImageSubresource s={VK_IMAGE_ASPECT_COLOR_BIT,0,0}; VkSubresourceLayout sl; vkGetImageSubresourceLayout(dev,im,&s,&sl); pitch=sl.rowPitch; printf("image rowPitch %u size %lu\n",pitch,(unsigned long)size);
    VkMemoryDedicatedAllocateInfo da={VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,&ei,im,0};
    VkMemoryAllocateInfo ma={VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,&da,size,pick(&mp,bits)};
    CK(vkAllocateMemory(dev,&ma,0,&mem)); CK(vkBindImageMemory(dev,im,mem,0));
  } else {
    VkExternalMemoryBufferCreateInfo eb={VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_BUFFER_CREATE_INFO,0,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkBufferCreateInfo bi={VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,&eb,0,W*H*4,VK_BUFFER_USAGE_TRANSFER_DST_BIT|VK_BUFFER_USAGE_TRANSFER_SRC_BIT};
    VkBuffer b; CK(vkCreateBuffer(dev,&bi,0,&b));
    VkMemoryRequirements mr; vkGetBufferMemoryRequirements(dev,b,&mr); size=mr.size;bits=mr.memoryTypeBits;
    VkMemoryAllocateInfo ma={VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,&ei,size,pick(&mp,bits)};
    CK(vkAllocateMemory(dev,&ma,0,&mem)); CK(vkBindBufferMemory(dev,b,mem,0));
  }
  printf("memtype %d flags %x of %d types, bits %x\n",pick(&mp,bits),mp.memoryTypes[pick(&mp,bits)].propertyFlags,mp.memoryTypeCount,bits);VkResult mr2=vkMapMemory(dev,mem,0,size,0,&ptr);printf("map=%d\n",mr2);if(mr2)return 7;
  for(int y=0;y<H;y++)for(int x=0;x<W;x++)((uint32_t*)((char*)ptr+y*pitch))[x]=0xFF000000|(x*4)<<16|(y*4)<<8|0x55; // B8G8R8A8 mem: bytes B,G,R,A
  int fd=-1; VkMemoryGetFdInfoKHR gi={VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR,0,mem,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
  CK(getfd(dev,&gi,&fd)); printf("exported fd %d pitch %u size %lu\n",fd,pitch,(unsigned long)size);
  // EGL
  int drm=open("/dev/dri/renderD128",O_RDWR); struct gbm_device*g=gbm_create_device(drm);
  PFNEGLGETPLATFORMDISPLAYEXTPROC gpd=(void*)eglGetProcAddress("eglGetPlatformDisplayEXT");
  EGLDisplay d=gpd(EGL_PLATFORM_GBM_KHR,g,0); EGLint maj,min; if(!eglInitialize(d,&maj,&min)){printf("eglInit fail\n");return 4;}
  printf("EGL %s | %s\n",eglQueryString(d,EGL_VENDOR),eglQueryString(d,EGL_EXTENSIONS)); printf("GL %s\n","");
  eglBindAPI(EGL_OPENGL_ES_API);
  EGLint ca[]={EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_NONE}; EGLConfig cfg; EGLint nc; eglChooseConfig(d,ca,&cfg,1,&nc);
  EGLint cx[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE}; EGLContext c=eglCreateContext(d,cfg,EGL_NO_CONTEXT,cx);
  if(!eglMakeCurrent(d,EGL_NO_SURFACE,EGL_NO_SURFACE,c)){printf("makecurrent fail %x\n",eglGetError());return 5;}
  printf("GL_RENDERER %s\n",glGetString(GL_RENDERER));
  EGLint at[]={EGL_WIDTH,W,EGL_HEIGHT,H,EGL_LINUX_DRM_FOURCC_EXT,DRM_FORMAT_ARGB8888,EGL_DMA_BUF_PLANE0_FD_EXT,fd,EGL_DMA_BUF_PLANE0_OFFSET_EXT,0,EGL_DMA_BUF_PLANE0_PITCH_EXT,pitch,
    EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT,0,EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT,0,EGL_NONE};
  PFNEGLCREATEIMAGEKHRPROC ci=(void*)eglGetProcAddress("eglCreateImageKHR");
  EGLImageKHR img=ci(d,EGL_NO_CONTEXT,EGL_LINUX_DMA_BUF_EXT,0,at);
  printf("eglCreateImage(with modifier linear) = %p err %x\n",img,eglGetError());
  if(!img){ EGLint at2[]={EGL_WIDTH,W,EGL_HEIGHT,H,EGL_LINUX_DRM_FOURCC_EXT,DRM_FORMAT_ARGB8888,EGL_DMA_BUF_PLANE0_FD_EXT,fd,EGL_DMA_BUF_PLANE0_OFFSET_EXT,0,EGL_DMA_BUF_PLANE0_PITCH_EXT,pitch,EGL_NONE};
    img=ci(d,EGL_NO_CONTEXT,EGL_LINUX_DMA_BUF_EXT,0,at2); printf("eglCreateImage(no modifier) = %p err %x\n",img,eglGetError()); }
  if(!img)return 6;
  GLuint tex; glGenTextures(1,&tex); glBindTexture(GL_TEXTURE_2D,tex);
  PFNGLEGLIMAGETARGETTEXTURE2DOESPROC tg=(void*)eglGetProcAddress("glEGLImageTargetTexture2DOES"); tg(GL_TEXTURE_2D,img);
  printf("target tex glerr %x\n",glGetError());
  GLuint fb; glGenFramebuffers(1,&fb); glBindFramebuffer(GL_FRAMEBUFFER,fb); glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,tex,0);
  printf("fbo status %x\n",glCheckFramebufferStatus(GL_FRAMEBUFFER));
  uint8_t px[W*H*4]; memset(px,0,sizeof px); glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,px); printf("readpixels glerr %x\n",glGetError());
  // expected: pixel (x,y) R=x*4 G=y*4 B=0x55 in logical RGBA; GL read origin bottom? texture row 0 = first row; fbo readback row0 = texture row 0
  int bad=0; for(int y=0;y<H;y++)for(int x=0;x<W;x++){uint8_t*p=px+(y*W+x)*4; if(p[0]!=x*4||p[1]!=y*4||p[2]!=0x55)bad++;}
  printf("px(0,0)=%02x%02x%02x%02x px(10,20)=%02x%02x%02x%02x mismatches %d/%d\n",px[0],px[1],px[2],px[3],px[(20*W+10)*4],px[(20*W+10)*4+1],px[(20*W+10)*4+2],px[(20*W+10)*4+3],bad,W*H);
  printf(bad?"RESULT FAIL\n":"RESULT PASS\n"); return bad?1:0; }
