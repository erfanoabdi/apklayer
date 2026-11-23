/* The render filter's Vulkan backend: device, imported app buffers, the output ring (sharpen_vk.h). The passes are in sharpen_vk_draw.c. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "sharpen_vk.h"

#define DRM_FORMAT_XRGB8888 0x34325258u
#define MOD_INVALID 0x00ffffffffffffffull

static VkFormat vkfmt(uint32_t fourcc)
{
    switch (fourcc) {
    case 0x34325258u: case 0x34325241u: return VK_FORMAT_B8G8R8A8_UNORM;  /* XRGB8888, ARGB8888 */
    case 0x34324258u: case 0x34324241u: return VK_FORMAT_R8G8B8A8_UNORM;  /* XBGR8888, ABGR8888 */
    }
    return VK_FORMAT_UNDEFINED;
}

int svk_mem_type(struct svk *s, uint32_t bits, VkMemoryPropertyFlags want)
{
    for (uint32_t i = 0; i < s->mp.memoryTypeCount; i++)
        if ((bits & (1u << i)) && (s->mp.memoryTypes[i].propertyFlags & want) == want) return (int)i;
    return -1;
}

void svk_img_free(struct svk *s, struct svk_img *i)
{
    if (i->fb) s->vkDestroyFramebuffer(s->dev, i->fb, NULL);
    if (i->view) s->vkDestroyImageView(s->dev, i->view, NULL);
    if (i->img) s->vkDestroyImage(s->dev, i->img, NULL);
    if (i->mem) s->vkFreeMemory(s->dev, i->mem, NULL);
    memset(i, 0, sizeof *i);
}

static int view_of(struct svk *s, struct svk_img *i, VkFormat fmt)
{
    VkImageViewCreateInfo vi = { .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, .image = i->img, .viewType = VK_IMAGE_VIEW_TYPE_2D, .format = fmt,
        .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 } };
    return s->vkCreateImageView(s->dev, &vi, NULL, &i->view) == VK_SUCCESS ? 0 : -1;
}

/* an app buffer as a sampled image: the dmabuf's planes with their layout and modifier (a single fd: RGBA formats have one plane) */
static int import_image(struct svk *s, struct svk_img *img, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod)
{
    VkFormat fmt = vkfmt(fourcc);
    if (fmt == VK_FORMAT_UNDEFINED || mod == MOD_INVALID || n < 1 || n > 4) return -1;
    for (int i = 1; i < n; i++) if (fds[i] != fds[0]) return -1;
    VkSubresourceLayout pl[4] = {{ 0 }};
    for (int i = 0; i < n; i++) { pl[i].offset = offsets[i]; pl[i].rowPitch = strides[i]; }
    VkImageDrmFormatModifierExplicitCreateInfoEXT mi = { .sType = VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT, .drmFormatModifier = mod, .drmFormatModifierPlaneCount = (uint32_t)n, .pPlaneLayouts = pl };
    VkExternalMemoryImageCreateInfo ex = { .sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO, .pNext = &mi, .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT };
    VkImageCreateInfo ci = { .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .pNext = &ex, .imageType = VK_IMAGE_TYPE_2D, .format = fmt, .extent = { (uint32_t)w, (uint32_t)h, 1 },
        .mipLevels = 1, .arrayLayers = 1, .samples = VK_SAMPLE_COUNT_1_BIT, .tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT, .usage = VK_IMAGE_USAGE_SAMPLED_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE, .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED };
    memset(img, 0, sizeof *img);
    if (s->vkCreateImage(s->dev, &ci, NULL, &img->img) != VK_SUCCESS) { img->img = 0; return -1; }
    int fd = dup(fds[0]);
    VkMemoryFdPropertiesKHR fp = { .sType = VK_STRUCTURE_TYPE_MEMORY_FD_PROPERTIES_KHR };
    VkMemoryRequirements rq;
    s->vkGetImageMemoryRequirements(s->dev, img->img, &rq);
    if (fd < 0 || s->vkGetMemoryFdPropertiesKHR(s->dev, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, fd, &fp) != VK_SUCCESS) goto fail;
    int mt = svk_mem_type(s, rq.memoryTypeBits & fp.memoryTypeBits, 0);
    if (mt < 0) goto fail;
    VkMemoryDedicatedAllocateInfo ded = { .sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO, .image = img->img };
    VkImportMemoryFdInfoKHR imp = { .sType = VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR, .pNext = &ded, .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, .fd = fd };
    VkMemoryAllocateInfo ai = { .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .pNext = &imp, .allocationSize = rq.size, .memoryTypeIndex = (uint32_t)mt };
    if (s->vkAllocateMemory(s->dev, &ai, NULL, &img->mem) != VK_SUCCESS) goto fail;  /* the fd is consumed only on success */
    fd = -1;
    if (s->vkBindImageMemory(s->dev, img->img, img->mem, 0) != VK_SUCCESS || view_of(s, img, fmt) < 0) goto fail;
    img->w = w; img->h = h;
    return 0;
fail:
    if (fd >= 0) close(fd);
    svk_img_free(s, img);
    return -1;
}

int svk_import(struct svk *s, int buf, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod)
{
    if (buf < 0 || buf >= SVK_BUFS) return -1;
    svk_forget(s, buf);
    if (import_image(s, &s->in[buf].i, fourcc, w, h, n, fds, strides, offsets, mod) < 0) return -1;
    s->in[buf].w = w; s->in[buf].h = h; s->in[buf].used = 1; s->in[buf].seen = 0;
    return 0;
}

void svk_forget(struct svk *s, int buf)
{
    if (buf < 0 || buf >= SVK_BUFS || !s->in[buf].used) return;
    svk_img_free(s, &s->in[buf].i);
    memset(&s->in[buf], 0, sizeof s->in[buf]);
}

void svk_accept(struct svk *s, const uint64_t *mods, int n)
{
    s->accept_n = n > 64 ? 64 : n < 0 ? 0 : n;
    for (int i = 0; i < s->accept_n; i++) s->accept[i] = mods[i];
}

static void slot_free(struct svk *s, struct svk_slot *o)
{
    svk_img_free(s, &o->i);
    if (o->fd >= 0) close(o->fd);
    memset(o, 0, sizeof *o);
    o->fd = -1;
}

/* an exportable XRGB image of a modifier the compositor listed (the driver picks one), else LINEAR: the shell knows it takes those */
static int slot_make(struct svk *s, struct svk_slot *o, int w, int h)
{
    slot_free(s, o);
    VkDrmFormatModifierPropertiesEXT props[64];
    VkDrmFormatModifierPropertiesListEXT ml = { .sType = VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_EXT, .drmFormatModifierCount = 64, .pDrmFormatModifierProperties = props };
    VkFormatProperties2 fp = { .sType = VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2, .pNext = &ml };
    s->vkGetPhysicalDeviceFormatProperties2(s->pd, VK_FORMAT_B8G8R8A8_UNORM, &fp);
    uint64_t cand[64];
    int nc = 0;
    for (uint32_t i = 0; i < ml.drmFormatModifierCount && nc < 64; i++) {
        if (props[i].drmFormatModifierPlaneCount != 1 || !(props[i].drmFormatModifierTilingFeatures & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT)) continue;
        int ok = props[i].drmFormatModifier == 0 && s->accept_n == 0;
        for (int a = 0; a < s->accept_n && !ok; a++) ok = s->accept[a] == props[i].drmFormatModifier;
        if (ok) cand[nc++] = props[i].drmFormatModifier;
    }
    if (!nc && s->accept_n > 0)
        for (uint32_t i = 0; i < ml.drmFormatModifierCount; i++)
            if (props[i].drmFormatModifier == 0 && props[i].drmFormatModifierPlaneCount == 1 && (props[i].drmFormatModifierTilingFeatures & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT)) cand[nc++] = 0;
    if (!nc) return -1;
    VkImageDrmFormatModifierListCreateInfoEXT lc = { .sType = VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_LIST_CREATE_INFO_EXT, .drmFormatModifierCount = (uint32_t)nc, .pDrmFormatModifiers = cand };
    VkExternalMemoryImageCreateInfo ex = { .sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO, .pNext = &lc, .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT };
    VkImageCreateInfo ci = { .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .pNext = &ex, .imageType = VK_IMAGE_TYPE_2D, .format = VK_FORMAT_B8G8R8A8_UNORM, .extent = { (uint32_t)w, (uint32_t)h, 1 },
        .mipLevels = 1, .arrayLayers = 1, .samples = VK_SAMPLE_COUNT_1_BIT, .tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT,
        .usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT, .sharingMode = VK_SHARING_MODE_EXCLUSIVE, .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED };
    if (s->vkCreateImage(s->dev, &ci, NULL, &o->i.img) != VK_SUCCESS) { o->i.img = 0; return -1; }
    VkMemoryRequirements rq;
    s->vkGetImageMemoryRequirements(s->dev, o->i.img, &rq);
    int mt = svk_mem_type(s, rq.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (mt < 0) mt = svk_mem_type(s, rq.memoryTypeBits, 0);
    if (mt < 0) goto fail;
    VkMemoryDedicatedAllocateInfo ded = { .sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO, .image = o->i.img };
    VkExportMemoryAllocateInfo exp = { .sType = VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO, .pNext = &ded, .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT };
    VkMemoryAllocateInfo ai = { .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .pNext = &exp, .allocationSize = rq.size, .memoryTypeIndex = (uint32_t)mt };
    if (s->vkAllocateMemory(s->dev, &ai, NULL, &o->i.mem) != VK_SUCCESS || s->vkBindImageMemory(s->dev, o->i.img, o->i.mem, 0) != VK_SUCCESS) goto fail;
    VkImageDrmFormatModifierPropertiesEXT mp = { .sType = VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_PROPERTIES_EXT };
    if (s->vkGetImageDrmFormatModifierPropertiesEXT(s->dev, o->i.img, &mp) != VK_SUCCESS) goto fail;
    VkImageSubresource sr = { VK_IMAGE_ASPECT_MEMORY_PLANE_0_BIT_EXT, 0, 0 };
    VkSubresourceLayout sl;
    s->vkGetImageSubresourceLayout(s->dev, o->i.img, &sr, &sl);
    VkMemoryGetFdInfoKHR gi = { .sType = VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR, .memory = o->i.mem, .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT };
    if (s->vkGetMemoryFdKHR(s->dev, &gi, &o->fd) != VK_SUCCESS) { o->fd = -1; goto fail; }
    o->stride = (uint32_t)sl.rowPitch; o->offset = (uint32_t)sl.offset; o->modifier = mp.drmFormatModifier;
    VkFramebufferCreateInfo fb = { .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO, .renderPass = s->rp_out, .attachmentCount = 1, .pAttachments = &o->i.view, .width = (uint32_t)w, .height = (uint32_t)h, .layers = 1 };
    if (view_of(s, &o->i, VK_FORMAT_B8G8R8A8_UNORM) < 0 || (fb.pAttachments = &o->i.view, s->vkCreateFramebuffer(s->dev, &fb, NULL, &o->i.fb) != VK_SUCCESS)) goto fail;
    o->i.w = w; o->i.h = h;
    return 0;
fail:
    slot_free(s, o);
    return -1;
}

int svk_render(struct svk *s, int buf, int ow, int oh, float sharp, int text, int *slot, int *fresh)
{
    if (buf < 0 || buf >= SVK_BUFS || !s->in[buf].used) return -1;
    int k = -1;
    /* the free slot that was used longest ago, not the one just released: the compositor's GPU may still be reading a buffer it has just released (the dmabuf carries no
     * implicit fence of its reads for a Vulkan write on every driver), so it is given as much time as the ring allows */
    for (int i = 0; i < SVK_SLOTS; i++) if (!s->out[i].busy && (k < 0 || s->out[i].used < s->out[k].used)) k = i;
    if (k < 0) return -2;
    struct svk_slot *o = &s->out[k];
    *fresh = 0;
    if (!o->i.img || o->i.w != ow || o->i.h != oh) {
        if (slot_make(s, o, ow, oh) < 0) { fprintf(stderr, "[sharpen] Vulkan: no output buffer %dx%d (%d modifiers offered)\n", ow, oh, s->accept_n); return -1; }
        *fresh = 1;
    }
    if (svk_record(s, buf, ow, oh, sharp, text, o) < 0) return -1;
    o->busy = 1;
    o->used = ++s->tick;
    s->last = k;
    *slot = k;
    return 0;
}

int svk_slot_info(struct svk *s, int slot, struct sharpen_out *out)
{
    struct svk_slot *o = slot >= 0 && slot < SVK_SLOTS ? &s->out[slot] : NULL;
    if (!o || o->fd < 0) return -1;
    int fd = dup(o->fd);
    if (fd < 0) return -1;
    out->fd = fd; out->w = o->i.w; out->h = o->i.h; out->fourcc = DRM_FORMAT_XRGB8888; out->stride = o->stride; out->offset = o->offset; out->modifier = o->modifier;
    return 0;
}

void svk_slot_release(struct svk *s, int slot) { if (slot >= 0 && slot < SVK_SLOTS) s->out[slot].busy = 0; }

/* test aid: the last slot's pixels as RGBA bytes */
int svk_readback(struct svk *s, int slot, void *rgba)
{
    struct svk_slot *o = &s->out[slot < 0 ? s->last : slot];
    if (!o->i.img) return -1;
    size_t sz = (size_t)o->i.w * o->i.h * 4;
    VkBuffer b; VkDeviceMemory m; void *p;
    VkBufferCreateInfo bi = { .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, .size = sz, .usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT, .sharingMode = VK_SHARING_MODE_EXCLUSIVE };
    if (s->vkCreateBuffer(s->dev, &bi, NULL, &b) != VK_SUCCESS) return -1;
    VkMemoryRequirements rq;
    s->vkGetBufferMemoryRequirements(s->dev, b, &rq);
    int mt = svk_mem_type(s, rq.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    VkMemoryAllocateInfo ai = { .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .allocationSize = rq.size, .memoryTypeIndex = (uint32_t)(mt < 0 ? 0 : mt) };
    int rc = -1;
    if (mt >= 0 && s->vkAllocateMemory(s->dev, &ai, NULL, &m) == VK_SUCCESS) {
        s->vkBindBufferMemory(s->dev, b, m, 0);
        VkCommandBufferBeginInfo bg = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO, .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT };
        s->vkResetCommandBuffer(s->cb, 0);
        s->vkBeginCommandBuffer(s->cb, &bg);
        VkImageMemoryBarrier br = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT, .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_GENERAL, .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = o->i.img, .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 } };
        s->vkCmdPipelineBarrier(s->cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1, &br);
        VkBufferImageCopy cp = { .imageSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 }, .imageExtent = { (uint32_t)o->i.w, (uint32_t)o->i.h, 1 } };
        s->vkCmdCopyImageToBuffer(s->cb, o->i.img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, b, 1, &cp);
        s->vkEndCommandBuffer(s->cb);
        VkSubmitInfo si = { .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &s->cb };
        if (s->vkQueueSubmit(s->q, 1, &si, VK_NULL_HANDLE) == VK_SUCCESS) {
            s->vkQueueWaitIdle(s->q);
            if (s->vkMapMemory(s->dev, m, 0, sz, 0, &p) == VK_SUCCESS) {
                const uint8_t *in = p;
                uint8_t *out = rgba;
                for (size_t i = 0; i < sz; i += 4) { out[i] = in[i + 2]; out[i + 1] = in[i + 1]; out[i + 2] = in[i]; out[i + 3] = in[i + 3]; }
                s->vkUnmapMemory(s->dev, m);
                rc = 0;
            }
        }
        s->vkFreeMemory(s->dev, m, NULL);
    }
    s->vkDestroyBuffer(s->dev, b, NULL);
    return rc;
}

static void device_destroy(struct svk *s)
{
    if (s->dev) {
        if (s->q) s->vkQueueWaitIdle(s->q);
        for (int i = 0; i < SVK_BUFS; i++) svk_forget(s, i);
        for (int i = 0; i < SVK_SLOTS; i++) slot_free(s, &s->out[i]);
        svk_img_free(s, &s->mid);
        svk_pipelines_free(s);
        if (s->cp) s->vkDestroyCommandPool(s->dev, s->cp, NULL);
        s->vkDestroyDevice(s->dev, NULL);
    }
    s->dev = NULL; s->cp = 0; s->q = NULL;
}

void svk_close(struct svk *s)
{
    if (!s) return;
    device_destroy(s);
    if (s->inst) s->vkDestroyInstance(s->inst, NULL);
    if (s->lib) dlclose(s->lib);
    free(s);
}

static int has_ext(const VkExtensionProperties *e, uint32_t n, const char *name)
{
    for (uint32_t i = 0; i < n; i++) if (!strcmp(e[i].extensionName, name)) return 1;
    return 0;
}

/* a device of the instance that takes dmabufs with modifiers: the render filter's objects are made on it and the sample buffer is imported once as a probe */
static int device_make(struct svk *s, VkPhysicalDevice pd, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod)
{
    uint32_t ne = 0;
    s->vkEnumerateDeviceExtensionProperties(pd, NULL, &ne, NULL);
    VkExtensionProperties *ext = calloc(ne ? ne : 1, sizeof *ext);
    if (!ext) return -1;
    s->vkEnumerateDeviceExtensionProperties(pd, NULL, &ne, ext);
    const char *want[8];
    int nw = 0;
    int ok = has_ext(ext, ne, "VK_KHR_external_memory_fd") && has_ext(ext, ne, "VK_EXT_external_memory_dma_buf") && has_ext(ext, ne, "VK_EXT_image_drm_format_modifier");
    want[nw++] = "VK_KHR_external_memory_fd"; want[nw++] = "VK_EXT_external_memory_dma_buf"; want[nw++] = "VK_EXT_image_drm_format_modifier";
    if (has_ext(ext, ne, "VK_KHR_image_format_list")) want[nw++] = "VK_KHR_image_format_list";
    s->has_foreign = has_ext(ext, ne, "VK_EXT_queue_family_foreign");
    if (s->has_foreign) want[nw++] = "VK_EXT_queue_family_foreign";
    s->has_fencefd = has_ext(ext, ne, "VK_KHR_external_fence_fd");
    if (s->has_fencefd) want[nw++] = "VK_KHR_external_fence_fd";
    free(ext);
    if (!ok) return -1;
    uint32_t nq = 0;
    s->vkGetPhysicalDeviceQueueFamilyProperties(pd, &nq, NULL);
    VkQueueFamilyProperties qp[16];
    if (nq > 16) nq = 16;
    s->vkGetPhysicalDeviceQueueFamilyProperties(pd, &nq, qp);
    uint32_t qf = nq;
    for (uint32_t i = 0; i < nq && qf == nq; i++) if (qp[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) qf = i;
    if (qf == nq) return -1;
    float prio = 1.0f;
    VkDeviceQueueCreateInfo qi = { .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, .queueFamilyIndex = qf, .queueCount = 1, .pQueuePriorities = &prio };
    VkDeviceCreateInfo di = { .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, .queueCreateInfoCount = 1, .pQueueCreateInfos = &qi, .enabledExtensionCount = (uint32_t)nw, .ppEnabledExtensionNames = want };
    if (s->vkCreateDevice(pd, &di, NULL, &s->dev) != VK_SUCCESS) { s->dev = NULL; return -1; }
    s->pd = pd; s->qf = qf;
#define X(f) s->f = (PFN_##f)s->vkGetDeviceProcAddr(s->dev, #f);
    SVK_DEVICE_FNS(X)
#undef X
    if (!s->has_fencefd) s->vkGetFenceFdKHR = NULL;
    s->vkGetDeviceQueue(s->dev, qf, 0, &s->q);
    s->vkGetPhysicalDeviceMemoryProperties(pd, &s->mp);
    VkCommandPoolCreateInfo cpi = { .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT, .queueFamilyIndex = qf };
    VkCommandBufferAllocateInfo cai = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1 };
    struct svk_img probe;
    if (!s->vkGetMemoryFdKHR || !s->vkGetMemoryFdPropertiesKHR || !s->vkGetImageDrmFormatModifierPropertiesEXT) goto fail;
    if (s->vkCreateCommandPool(s->dev, &cpi, NULL, &s->cp) != VK_SUCCESS) goto fail;
    cai.commandPool = s->cp;
    if (s->vkAllocateCommandBuffers(s->dev, &cai, &s->cb) != VK_SUCCESS || svk_pipelines(s) < 0) goto fail;
    if (import_image(s, &probe, fourcc, w, h, n, fds, strides, offsets, mod) < 0) goto fail;
    svk_img_free(s, &probe);
    return 0;
fail:
    device_destroy(s);
    return -1;
}

int svk_prepare(struct svk **out, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod)
{
    struct svk *s = calloc(1, sizeof *s);
    if (!s) return -1;
    for (int i = 0; i < SVK_SLOTS; i++) s->out[i].fd = -1;
    s->lib = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!s->lib) goto fail;
    s->vkGetInstanceProcAddr = (PFN_vkGetInstanceProcAddr)dlsym(s->lib, "vkGetInstanceProcAddr");
    PFN_vkCreateInstance create = s->vkGetInstanceProcAddr ? (PFN_vkCreateInstance)s->vkGetInstanceProcAddr(NULL, "vkCreateInstance") : NULL;
    if (!create) goto fail;
    VkApplicationInfo app = { .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .pApplicationName = "apklayer-shell", .apiVersion = VK_API_VERSION_1_1 };
    VkInstanceCreateInfo ic = { .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app };
    if (create(&ic, NULL, &s->inst) != VK_SUCCESS) { s->inst = NULL; goto fail; }
#define X(f) s->f = (PFN_##f)s->vkGetInstanceProcAddr(s->inst, #f);
    SVK_INSTANCE_FNS(X)
#undef X
    uint32_t np = 0;
    VkPhysicalDevice pds[8];
    s->vkEnumeratePhysicalDevices(s->inst, &np, NULL);
    if (np > 8) np = 8;
    if (s->vkEnumeratePhysicalDevices(s->inst, &np, pds) < 0) goto fail;
    for (uint32_t i = 0; i < np; i++) {
        if (device_make(s, pds[i], fourcc, w, h, n, fds, strides, offsets, mod) < 0) continue;
        VkPhysicalDeviceProperties pr;
        s->vkGetPhysicalDeviceProperties(pds[i], &pr);
        fprintf(stderr, "[sharpen] using Vulkan on %s\n", pr.deviceName);
        *out = s;
        return 0;
    }
fail:
    svk_close(s);
    return -1;
}
