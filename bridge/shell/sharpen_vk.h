/* The render filter's Vulkan backend (sharpen.c dispatches to it: no EGL/GBM needed, or chosen with APKLAYER_SHARPEN_BACKEND=vulkan). libvulkan is
 * loaded at run time. App dmabufs are imported as sampled images (VK_EXT_image_drm_format_modifier, explicit plane layouts), the passes are the same
 * fragment shaders as the GLES path (SPIR-V made from them by tools/gen/sharpen-spv.py) drawn into a ring of exportable images the window shows. */
#ifndef APKLAYER_SHARPEN_VK_H
#define APKLAYER_SHARPEN_VK_H
#define VK_NO_PROTOTYPES
#include <stdint.h>
#include <vulkan/vulkan.h>
#include "sharpen.h"

#define SVK_BUFS 8
#define SVK_SLOTS 5
#define SVK_PIPES 12

#define SVK_INSTANCE_FNS(X) X(vkDestroyInstance) X(vkEnumeratePhysicalDevices) X(vkGetPhysicalDeviceProperties) X(vkGetPhysicalDeviceQueueFamilyProperties) \
    X(vkGetPhysicalDeviceMemoryProperties) X(vkEnumerateDeviceExtensionProperties) X(vkGetPhysicalDeviceFormatProperties2) X(vkCreateDevice) X(vkGetDeviceProcAddr)
#define SVK_DEVICE_FNS(X) X(vkDestroyDevice) X(vkGetDeviceQueue) X(vkCreateImage) X(vkDestroyImage) X(vkGetImageMemoryRequirements) X(vkAllocateMemory) X(vkFreeMemory) \
    X(vkBindImageMemory) X(vkCreateImageView) X(vkDestroyImageView) X(vkCreateFramebuffer) X(vkDestroyFramebuffer) X(vkCreateRenderPass) X(vkDestroyRenderPass) \
    X(vkCreateShaderModule) X(vkDestroyShaderModule) X(vkCreateDescriptorSetLayout) X(vkDestroyDescriptorSetLayout) X(vkCreatePipelineLayout) X(vkDestroyPipelineLayout) \
    X(vkCreateGraphicsPipelines) X(vkDestroyPipeline) X(vkCreateSampler) X(vkDestroySampler) X(vkCreateDescriptorPool) X(vkDestroyDescriptorPool) X(vkResetDescriptorPool) \
    X(vkAllocateDescriptorSets) X(vkUpdateDescriptorSets) X(vkCreateCommandPool) X(vkDestroyCommandPool) X(vkAllocateCommandBuffers) X(vkBeginCommandBuffer) \
    X(vkEndCommandBuffer) X(vkResetCommandBuffer) X(vkCmdBeginRenderPass) X(vkCmdEndRenderPass) X(vkCmdBindPipeline) X(vkCmdBindDescriptorSets) X(vkCmdPushConstants) \
    X(vkCmdSetViewport) X(vkCmdSetScissor) X(vkCmdDraw) X(vkCmdPipelineBarrier) X(vkCmdCopyImageToBuffer) X(vkQueueSubmit) X(vkQueueWaitIdle) X(vkCreateFence) \
    X(vkDestroyFence) X(vkCreateBuffer) X(vkDestroyBuffer) X(vkBindBufferMemory) X(vkGetBufferMemoryRequirements) X(vkMapMemory) X(vkUnmapMemory) \
    X(vkGetMemoryFdKHR) X(vkGetMemoryFdPropertiesKHR) X(vkGetImageDrmFormatModifierPropertiesEXT) X(vkGetImageSubresourceLayout) X(vkGetFenceFdKHR)

struct svk_img { VkImage img; VkDeviceMemory mem; VkImageView view; VkFramebuffer fb; int w, h; };
struct svk_in { struct svk_img i; int w, h, used, seen; };
struct svk_slot { struct svk_img i; int fd, busy; uint32_t stride, offset; uint64_t modifier; uint64_t used; };

struct svk {
    void *lib;
    PFN_vkGetInstanceProcAddr vkGetInstanceProcAddr;
#define X(n) PFN_##n n;
    SVK_INSTANCE_FNS(X) SVK_DEVICE_FNS(X)
#undef X
    VkInstance inst;
    VkPhysicalDevice pd;
    VkDevice dev;
    VkQueue q;
    uint32_t qf;
    VkPhysicalDeviceMemoryProperties mp;
    int has_foreign, has_fencefd;
    VkRenderPass rp_mid, rp_out, rp_f16;
    VkDescriptorSetLayout dsl;
    VkPipelineLayout pl;
    VkSampler lin, nea;
    VkDescriptorPool pool;
    VkShaderModule vs, fs[SVK_PIPES];
    VkPipeline pipe[SVK_PIPES];
    VkCommandPool cp;
    VkCommandBuffer cb;
    struct svk_in in[SVK_BUFS];
    struct svk_slot out[SVK_SLOTS];
    struct svk_img mid, f16[3];
    uint64_t accept[64];
    int accept_n, last;
    uint64_t tick;  /* the render count: the output slot used least recently is drawn into next */
};

int svk_prepare(struct svk **out, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod);
void svk_close(struct svk *s);
void svk_accept(struct svk *s, const uint64_t *mods, int n);
int svk_import(struct svk *s, int buf, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod);
void svk_forget(struct svk *s, int buf);
int svk_render(struct svk *s, int mode, int buf, int ow, int oh, float sharp, int *slot, int *fresh);
int svk_slot_info(struct svk *s, int slot, struct sharpen_out *out);
void svk_slot_release(struct svk *s, int slot);
int svk_readback(struct svk *s, int slot, void *rgba);

/* sharpen_vk_draw.c */
int svk_pipelines(struct svk *s);
void svk_pipelines_free(struct svk *s);
int svk_record(struct svk *s, int mode, int buf, int ow, int oh, float sharp, struct svk_slot *o);
void svk_img_free(struct svk *s, struct svk_img *i);
int svk_mem_type(struct svk *s, uint32_t bits, VkMemoryPropertyFlags want);
#endif
