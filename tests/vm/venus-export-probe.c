// Venus <-> virgl dma-buf probe (UTM VM: Venus + virgl on one renderD128). Build: gcc -I/usr/include/libdrm -o probe venus-export-probe.c -lvulkan -lEGL -lGLESv2 -lgbm
//   probe export: Venus allocates an explicit-LINEAR image (VkExportMemoryAllocateInfo DMA_BUF), clears it on the GPU (sync_fd semaphore, polled), exports the
//                 dma-buf; virgl's EGL imports and reads it. MT=<n> picks the memory type (default the lowest: device-local only -> GL_OUT_OF_MEMORY; MT=1,
//                 host-visible -> RESULT PASS). IDLE=1 also waits the queue.
//   probe import: a GBM (virgl) bo is imported into Venus (allocate and bind work) and the first command on the image aborts (exit 134).
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
#include <poll.h>
#include <sys/mman.h>
#include <drm_fourcc.h>
#define W 64
#define H 64
#define CK(x) do{VkResult r=(x);if(r){printf("FAIL %s = %d\n",#x,r);exit(2);}}while(0)
static int pick(VkPhysicalDeviceMemoryProperties*m,uint32_t b){for(int i=0;i<m->memoryTypeCount;i++)if((b>>i&1)&&(m->memoryTypes[i].propertyFlags&2))return i;return __builtin_ctz(b);}
static int export_main(int argc,char**argv){
  int useImage = argc>1 && argv[1][0]=='i';
  VkApplicationInfo ai={VK_STRUCTURE_TYPE_APPLICATION_INFO,0,"p",1,"p",1,VK_API_VERSION_1_1};
  VkInstanceCreateInfo ic={VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,0,0,&ai};
  VkInstance inst; CK(vkCreateInstance(&ic,0,&inst));
  uint32_t n=8; VkPhysicalDevice pd[8]; vkEnumeratePhysicalDevices(inst,&n,pd);
  VkPhysicalDevice phys=0;
  for(uint32_t i=0;i<n;i++){VkPhysicalDeviceDriverProperties d={VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES};VkPhysicalDeviceProperties2 p={VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2,&d};vkGetPhysicalDeviceProperties2(pd[i],&p);printf("dev %s driver %s\n",p.properties.deviceName,d.driverName);if(d.driverID==VK_DRIVER_ID_MESA_VENUS)phys=pd[i];}
  if(!phys){printf("no venus\n");return 3;}
  float pr=1; VkDeviceQueueCreateInfo q={VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,0,0,0,1,&pr};
  const char*ex[]={"VK_KHR_external_memory_fd","VK_EXT_external_memory_dma_buf","VK_KHR_external_memory","VK_EXT_image_drm_format_modifier","VK_KHR_external_semaphore","VK_KHR_external_semaphore_fd","VK_KHR_image_format_list","VK_KHR_bind_memory2","VK_KHR_maintenance1"};
  VkDeviceCreateInfo dc={VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,0,0,1,&q,0,0,9,ex};
  VkDevice dev; CK(vkCreateDevice(phys,&dc,0,&dev));
  PFN_vkGetMemoryFdKHR getfd=(void*)vkGetDeviceProcAddr(dev,"vkGetMemoryFdKHR");
  VkPhysicalDeviceMemoryProperties mp; vkGetPhysicalDeviceMemoryProperties(phys,&mp);
  VkExportMemoryAllocateInfo ei={VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO,0,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
  VkDeviceMemory mem; uint32_t pitch=W*4; VkDeviceSize size=0; uint32_t bits=0; void*ptr=0;
  VkExternalMemoryImageCreateInfo ex2={VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,0,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
  pitch=((W*4)+255)/256*256+256*useImage; // useImage arg: extra pitch padding to mimic odd stride
  VkSubresourceLayout lay={0,0,pitch,0,0};
  VkImageDrmFormatModifierExplicitCreateInfoEXT dm={VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT,&ex2,0,1,&lay};
  VkImageCreateInfo ii={VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,&dm,0,VK_IMAGE_TYPE_2D,VK_FORMAT_B8G8R8A8_UNORM,{W,H,1},1,1,VK_SAMPLE_COUNT_1_BIT,VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT,VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,0,0,0,VK_IMAGE_LAYOUT_UNDEFINED};
  VkImage im; CK(vkCreateImage(dev,&ii,0,&im));
  VkMemoryRequirements mr; vkGetImageMemoryRequirements(dev,im,&mr); size=mr.size;bits=mr.memoryTypeBits;
  printf("pitch %u size %lu bits %x\n",pitch,(unsigned long)size,bits);
  VkMemoryDedicatedAllocateInfo da={VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,&ei,im,0};
  int mt=getenv("MT")?atoi(getenv("MT")):__builtin_ctz(bits);
  VkMemoryAllocateInfo ma={VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,&da,size,mt};
  CK(vkAllocateMemory(dev,&ma,0,&mem)); CK(vkBindImageMemory(dev,im,mem,0)); printf("memtype %d flags %x\n",mt,mp.memoryTypes[mt].propertyFlags);
  // GPU clear
  VkQueue qu; vkGetDeviceQueue(dev,0,0,&qu);
  VkCommandPoolCreateInfo pci={VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,0,0,0}; VkCommandPool pool; CK(vkCreateCommandPool(dev,&pci,0,&pool));
  VkCommandBufferAllocateInfo cai={VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,0,pool,VK_COMMAND_BUFFER_LEVEL_PRIMARY,1}; VkCommandBuffer cb; CK(vkAllocateCommandBuffers(dev,&cai,&cb));
  VkCommandBufferBeginInfo bi={VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,0,VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT}; CK(vkBeginCommandBuffer(cb,&bi));
  VkImageMemoryBarrier br={VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,0,0,VK_ACCESS_TRANSFER_WRITE_BIT,VK_IMAGE_LAYOUT_UNDEFINED,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,0,0,im,{VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}};
  vkCmdPipelineBarrier(cb,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,0,0,0,1,&br);
  VkClearColorValue cc={{0.2f,0.4f,0.6f,1.0f}}; VkImageSubresourceRange sr={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}; vkCmdClearColorImage(cb,im,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,&cc,1,&sr);
  br.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;br.dstAccessMask=0;br.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;br.newLayout=VK_IMAGE_LAYOUT_GENERAL;
  vkCmdPipelineBarrier(cb,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,0,0,0,0,0,1,&br);
  CK(vkEndCommandBuffer(cb));
  VkExportSemaphoreCreateInfo xs={VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO,0,VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT}; VkSemaphoreCreateInfo sci={VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,&xs}; VkSemaphore sem; CK(vkCreateSemaphore(dev,&sci,0,&sem));
  VkSubmitInfo si={VK_STRUCTURE_TYPE_SUBMIT_INFO,0,0,0,0,1,&cb,1,&sem}; CK(vkQueueSubmit(qu,1,&si,0)); printf("submitted\n");
  PFN_vkGetSemaphoreFdKHR gsf=(void*)vkGetDeviceProcAddr(dev,"vkGetSemaphoreFdKHR"); int sfd=-2; VkSemaphoreGetFdInfoKHR sg={VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR,0,sem,VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
  CK(gsf(dev,&sg,&sfd)); printf("sync fd %d\n",sfd);
  if(sfd>=0){ struct pollfd p={sfd,POLLIN,0}; int r=poll(&p,1,2000); printf("poll sync_fd -> %d revents %x\n",r,p.revents); }
  if(getenv("IDLE")) {CK(vkQueueWaitIdle(qu)); printf("queue idle\n");}
  ptr=0;
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
  int bad=0; for(int i=0;i<W*H;i++){uint8_t*p=px+i*4; if(abs(p[0]-51)>1||abs(p[1]-102)>1||abs(p[2]-153)>1)bad++;}
  printf("px(0,0)=%02x%02x%02x%02x mismatches %d/%d\n",px[0],px[1],px[2],px[3],bad,W*H);
  printf(bad?"RESULT FAIL\n":"RESULT PASS\n"); return bad?1:0; }

static int import_main(int argc,char**argv){
  int useImage = argc>1 && argv[1][0]=='i';
  VkApplicationInfo ai={VK_STRUCTURE_TYPE_APPLICATION_INFO,0,"p",1,"p",1,VK_API_VERSION_1_1};
  VkInstanceCreateInfo ic={VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,0,0,&ai};
  VkInstance inst; CK(vkCreateInstance(&ic,0,&inst));
  uint32_t n=8; VkPhysicalDevice pd[8]; vkEnumeratePhysicalDevices(inst,&n,pd);
  VkPhysicalDevice phys=0;
  for(uint32_t i=0;i<n;i++){VkPhysicalDeviceDriverProperties d={VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES};VkPhysicalDeviceProperties2 p={VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2,&d};vkGetPhysicalDeviceProperties2(pd[i],&p);printf("dev %s driver %s\n",p.properties.deviceName,d.driverName);if(d.driverID==VK_DRIVER_ID_MESA_VENUS)phys=pd[i];}
  if(!phys){printf("no venus\n");return 3;}
  float pr=1; VkDeviceQueueCreateInfo q={VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,0,0,0,1,&pr};
  const char*ex[]={"VK_KHR_external_memory_fd","VK_EXT_external_memory_dma_buf","VK_KHR_external_memory","VK_EXT_image_drm_format_modifier","VK_KHR_external_semaphore","VK_KHR_external_semaphore_fd","VK_KHR_image_format_list","VK_KHR_bind_memory2","VK_KHR_maintenance1"};
  VkDeviceCreateInfo dc={VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,0,0,1,&q,0,0,9,ex};
  VkDevice dev; CK(vkCreateDevice(phys,&dc,0,&dev));
  PFN_vkGetMemoryFdKHR getfd=(void*)vkGetDeviceProcAddr(dev,"vkGetMemoryFdKHR");
  VkPhysicalDeviceMemoryProperties mp; vkGetPhysicalDeviceMemoryProperties(phys,&mp);
  int drm0=open("/dev/dri/renderD128",O_RDWR); struct gbm_device*g0=gbm_create_device(drm0);
  struct gbm_bo*bo=gbm_bo_create(g0,W,H,GBM_FORMAT_ARGB8888,GBM_BO_USE_RENDERING|GBM_BO_USE_LINEAR);
  if(!bo){printf("gbm_bo_create failed\n");return 8;}
  uint32_t pitch=gbm_bo_get_stride(bo); int fd=gbm_bo_get_fd(bo); off_t sz=lseek(fd,0,SEEK_END); printf("gbm bo pitch %u fd %d size %ld modifier %lx\n",pitch,fd,(long)sz,(unsigned long)gbm_bo_get_modifier(bo));
  VkExternalMemoryImageCreateInfo ex2={VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,0,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
  VkSubresourceLayout lay={0,0,pitch,0,0};
  VkImageDrmFormatModifierExplicitCreateInfoEXT dm={VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT,&ex2,0,1,&lay};
  VkImageCreateInfo ii={VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,&dm,0,VK_IMAGE_TYPE_2D,VK_FORMAT_B8G8R8A8_UNORM,{W,H,1},1,1,VK_SAMPLE_COUNT_1_BIT,VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT,VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,0,0,0,VK_IMAGE_LAYOUT_UNDEFINED};
  VkImage im; CK(vkCreateImage(dev,&ii,0,&im)); printf("image created\n");
  VkMemoryRequirements mr; vkGetImageMemoryRequirements(dev,im,&mr);
  VkImportMemoryFdInfoKHR imp={VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR,0,VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT,dup(fd)};
  VkMemoryDedicatedAllocateInfo da={VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,&imp,im,0};
  VkMemoryAllocateInfo ma={VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,&da,sz>0?sz:mr.size,__builtin_ctz(mr.memoryTypeBits)};
  VkDeviceMemory mem; fflush(stdout); CK(vkAllocateMemory(dev,&ma,0,&mem)); printf("import allocated\n"); fflush(stdout); CK(vkBindImageMemory(dev,im,mem,0)); printf("bound\n"); fflush(stdout);
  VkQueue qu; vkGetDeviceQueue(dev,0,0,&qu);
  VkCommandPoolCreateInfo pci={VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,0,0,0}; VkCommandPool pool; CK(vkCreateCommandPool(dev,&pci,0,&pool));
  VkCommandBufferAllocateInfo cai={VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,0,pool,VK_COMMAND_BUFFER_LEVEL_PRIMARY,1}; VkCommandBuffer cb; CK(vkAllocateCommandBuffers(dev,&cai,&cb));
  VkCommandBufferBeginInfo bi={VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,0,VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT}; CK(vkBeginCommandBuffer(cb,&bi));
  VkImageMemoryBarrier br={VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,0,0,VK_ACCESS_TRANSFER_WRITE_BIT,VK_IMAGE_LAYOUT_UNDEFINED,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,0,0,im,{VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}};
  vkCmdPipelineBarrier(cb,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,0,0,0,1,&br);
  VkClearColorValue cc={{0.2f,0.4f,0.6f,1.0f}}; VkImageSubresourceRange sr={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}; vkCmdClearColorImage(cb,im,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,&cc,1,&sr);
  CK(vkEndCommandBuffer(cb)); VkSubmitInfo si={VK_STRUCTURE_TYPE_SUBMIT_INFO,0,0,0,0,1,&cb}; printf("submitting\n"); fflush(stdout); CK(vkQueueSubmit(qu,1,&si,0)); CK(vkQueueWaitIdle(qu)); printf("virgl dma-buf used by Venus: RESULT PASS\n"); return 0;
}

int main(int argc,char**argv){ return argc>1&&!strcmp(argv[1],"import")?import_main(argc,argv):export_main(argc,argv); }
