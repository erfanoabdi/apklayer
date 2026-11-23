/* The render filter's Vulkan passes: render passes, pipelines (one per shader of sharpen_vk_spv.h), and one command buffer per frame (sharpen_vk.h). */
#include <errno.h>
#include <math.h>
#include <poll.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "sharpen_vk.h"
#include "sharpen_vk_spv.h"

/* the shaders' push-constant block (tools/gen/sharpen-spv.py): vec2 src, scl, px, d; float sharp, down */
struct pc { float src[2], scl[2], px[2], d[2], sharp, down; };
enum { P_UP, P_CAS, P_DOWN };

static VkRenderPass make_rp(struct svk *s, VkFormat fmt, VkImageLayout final)
{
    VkAttachmentDescription at = { .format = fmt, .samples = VK_SAMPLE_COUNT_1_BIT, .loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE, .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
        .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE, .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE, .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED, .finalLayout = final };
    VkAttachmentReference ref = { 0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL };
    VkSubpassDescription sp = { .pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS, .colorAttachmentCount = 1, .pColorAttachments = &ref };
    VkSubpassDependency dep[2] = {  /* earlier passes' reads and writes finish before this one writes; its writes are visible to the next one's sampling */
        { VK_SUBPASS_EXTERNAL, 0, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
          VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, 0 },
        { 0, VK_SUBPASS_EXTERNAL, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
          VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_TRANSFER_READ_BIT, 0 } };
    VkRenderPassCreateInfo ci = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO, .attachmentCount = 1, .pAttachments = &at, .subpassCount = 1, .pSubpasses = &sp, .dependencyCount = 2, .pDependencies = dep };
    VkRenderPass rp = VK_NULL_HANDLE;
    if (s->vkCreateRenderPass(s->dev, &ci, NULL, &rp) != VK_SUCCESS) return VK_NULL_HANDLE;
    return rp;
}

void svk_pipelines_free(struct svk *s)
{
    for (int i = 0; i < SVK_PIPES; i++) {
        if (s->pipe[i]) s->vkDestroyPipeline(s->dev, s->pipe[i], NULL);
        if (s->fs[i]) s->vkDestroyShaderModule(s->dev, s->fs[i], NULL);
        s->pipe[i] = VK_NULL_HANDLE; s->fs[i] = VK_NULL_HANDLE;
    }
    if (s->fence) s->vkDestroyFence(s->dev, s->fence, NULL);
    s->fence = VK_NULL_HANDLE;
    if (s->vs) s->vkDestroyShaderModule(s->dev, s->vs, NULL);
    if (s->pool) s->vkDestroyDescriptorPool(s->dev, s->pool, NULL);
    if (s->pl) s->vkDestroyPipelineLayout(s->dev, s->pl, NULL);
    if (s->dsl) s->vkDestroyDescriptorSetLayout(s->dev, s->dsl, NULL);
    if (s->lin) s->vkDestroySampler(s->dev, s->lin, NULL);
    if (s->rp_mid) s->vkDestroyRenderPass(s->dev, s->rp_mid, NULL);
    if (s->rp_out) s->vkDestroyRenderPass(s->dev, s->rp_out, NULL);
    s->vs = 0; s->pool = 0; s->pl = 0; s->dsl = 0; s->lin = 0; s->rp_mid = s->rp_out = 0;
}

static VkShaderModule module(struct svk *s, const uint32_t *code, size_t bytes)
{
    VkShaderModuleCreateInfo ci = { .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO, .codeSize = bytes, .pCode = code };
    VkShaderModule m = VK_NULL_HANDLE;
    return s->vkCreateShaderModule(s->dev, &ci, NULL, &m) == VK_SUCCESS ? m : VK_NULL_HANDLE;
}

int svk_pipelines(struct svk *s)
{
    VkSamplerCreateInfo sc = { .sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO, .magFilter = VK_FILTER_LINEAR, .minFilter = VK_FILTER_LINEAR, .addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
        .addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, .addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE };
    if (s->vkCreateSampler(s->dev, &sc, NULL, &s->lin) != VK_SUCCESS) goto fail;
    VkDescriptorSetLayoutBinding bd = { 0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_FRAGMENT_BIT, &s->lin };  /* tex: the app's buffer or EASU's picture, bilinear */
    VkDescriptorSetLayoutCreateInfo dl = { .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO, .bindingCount = 1, .pBindings = &bd };
    if (s->vkCreateDescriptorSetLayout(s->dev, &dl, NULL, &s->dsl) != VK_SUCCESS) goto fail;
    VkPushConstantRange pr = { VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(struct pc) };
    VkPipelineLayoutCreateInfo pli = { .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO, .setLayoutCount = 1, .pSetLayouts = &s->dsl, .pushConstantRangeCount = 1, .pPushConstantRanges = &pr };
    if (s->vkCreatePipelineLayout(s->dev, &pli, NULL, &s->pl) != VK_SUCCESS) goto fail;
    VkDescriptorPoolSize ps = { VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 8 };
    VkDescriptorPoolCreateInfo dp = { .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO, .maxSets = 8, .poolSizeCount = 1, .pPoolSizes = &ps };
    if (s->vkCreateDescriptorPool(s->dev, &dp, NULL, &s->pool) != VK_SUCCESS) goto fail;
    s->rp_mid = make_rp(s, VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    s->rp_out = make_rp(s, VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_LAYOUT_GENERAL);
    s->vs = module(s, svk_spv_vs, sizeof svk_spv_vs);
    if (!s->rp_mid || !s->rp_out || !s->vs) goto fail;
    VkPipelineVertexInputStateCreateInfo vi = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO };
    VkPipelineInputAssemblyStateCreateInfo ia = { .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO, .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST };
    VkPipelineViewportStateCreateInfo vp = { .sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO, .viewportCount = 1, .scissorCount = 1 };
    VkPipelineRasterizationStateCreateInfo rs = { .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO, .polygonMode = VK_POLYGON_MODE_FILL, .cullMode = VK_CULL_MODE_NONE, .lineWidth = 1.0f };
    VkPipelineMultisampleStateCreateInfo ms = { .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO, .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT };
    VkPipelineColorBlendAttachmentState ba = { .colorWriteMask = 0xf };
    VkPipelineColorBlendStateCreateInfo cb = { .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO, .attachmentCount = 1, .pAttachments = &ba };
    VkDynamicState dyn[2] = { VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR };
    VkPipelineDynamicStateCreateInfo ds = { .sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO, .dynamicStateCount = 2, .pDynamicStates = dyn };
    for (int i = 0; i < SVK_PIPES; i++) {
        s->fs[i] = module(s, svk_spv[i].code, svk_spv[i].size);
        if (!s->fs[i]) goto fail;
        VkPipelineShaderStageCreateInfo st[2] = {
            { .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, .stage = VK_SHADER_STAGE_VERTEX_BIT, .module = s->vs, .pName = "main" },
            { .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, .stage = VK_SHADER_STAGE_FRAGMENT_BIT, .module = s->fs[i], .pName = "main" } };
        VkGraphicsPipelineCreateInfo gi = { .sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO, .stageCount = 2, .pStages = st, .pVertexInputState = &vi, .pInputAssemblyState = &ia,
            .pViewportState = &vp, .pRasterizationState = &rs, .pMultisampleState = &ms, .pColorBlendState = &cb, .pDynamicState = &ds, .layout = s->pl,
            .renderPass = s->rp_mid };  /* rp_mid and rp_out are compatible: the same pipelines draw into either */
        if (s->vkCreateGraphicsPipelines(s->dev, VK_NULL_HANDLE, 1, &gi, NULL, &s->pipe[i]) != VK_SUCCESS) { s->pipe[i] = VK_NULL_HANDLE; goto fail; }
    }
    return 0;
fail:
    svk_pipelines_free(s);
    return -1;
}

/* an intermediate image of the output's size, drawn into by one pass and sampled by the next */
static int target(struct svk *s, struct svk_img *t, VkFormat fmt, VkRenderPass rp, int w, int h)
{
    if (t->img && t->w == w && t->h == h) return 0;
    svk_img_free(s, t);
    VkImageCreateInfo ci = { .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .imageType = VK_IMAGE_TYPE_2D, .format = fmt, .extent = { (uint32_t)w, (uint32_t)h, 1 }, .mipLevels = 1, .arrayLayers = 1,
        .samples = VK_SAMPLE_COUNT_1_BIT, .tiling = VK_IMAGE_TILING_OPTIMAL, .usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT, .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED };
    if (s->vkCreateImage(s->dev, &ci, NULL, &t->img) != VK_SUCCESS) { t->img = 0; return -1; }
    VkMemoryRequirements rq;
    s->vkGetImageMemoryRequirements(s->dev, t->img, &rq);
    int mt = svk_mem_type(s, rq.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    VkMemoryAllocateInfo ai = { .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .allocationSize = rq.size, .memoryTypeIndex = (uint32_t)(mt < 0 ? 0 : mt) };
    VkImageViewCreateInfo vi = { .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, .image = t->img, .viewType = VK_IMAGE_VIEW_TYPE_2D, .format = fmt, .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 } };
    VkFramebufferCreateInfo fb = { .sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO, .renderPass = rp, .attachmentCount = 1, .pAttachments = &t->view, .width = (uint32_t)w, .height = (uint32_t)h, .layers = 1 };
    if (mt < 0 || s->vkAllocateMemory(s->dev, &ai, NULL, &t->mem) != VK_SUCCESS || s->vkBindImageMemory(s->dev, t->img, t->mem, 0) != VK_SUCCESS
        || s->vkCreateImageView(s->dev, &vi, NULL, &t->view) != VK_SUCCESS || s->vkCreateFramebuffer(s->dev, &fb, NULL, &t->fb) != VK_SUCCESS) {
        svk_img_free(s, t);
        return -1;
    }
    t->w = w; t->h = h;
    return 0;
}

static void pass(struct svk *s, int pipe, VkRenderPass rp, VkFramebuffer fb, int w, int h, VkImageView v, const struct pc *pc)
{
    VkDescriptorSet set;
    VkDescriptorSetAllocateInfo ai = { .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO, .descriptorPool = s->pool, .descriptorSetCount = 1, .pSetLayouts = &s->dsl };
    if (s->vkAllocateDescriptorSets(s->dev, &ai, &set) != VK_SUCCESS) return;
    VkDescriptorImageInfo ii = { VK_NULL_HANDLE, v, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL };  /* the sampler is immutable */
    VkWriteDescriptorSet wr = { .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET, .dstSet = set, .dstBinding = 0, .descriptorCount = 1,
        .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, .pImageInfo = &ii };
    s->vkUpdateDescriptorSets(s->dev, 1, &wr, 0, NULL);
    VkRenderPassBeginInfo bi = { .sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO, .renderPass = rp, .framebuffer = fb, .renderArea = { { 0, 0 }, { (uint32_t)w, (uint32_t)h } } };
    s->vkCmdBeginRenderPass(s->cb, &bi, VK_SUBPASS_CONTENTS_INLINE);
    s->vkCmdBindPipeline(s->cb, VK_PIPELINE_BIND_POINT_GRAPHICS, s->pipe[pipe]);
    s->vkCmdBindDescriptorSets(s->cb, VK_PIPELINE_BIND_POINT_GRAPHICS, s->pl, 0, 1, &set, 0, NULL);
    s->vkCmdPushConstants(s->cb, s->pl, VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof *pc, pc);
    VkViewport vp = { 0, 0, (float)w, (float)h, 0, 1 };
    VkRect2D sc = { { 0, 0 }, { (uint32_t)w, (uint32_t)h } };
    s->vkCmdSetViewport(s->cb, 0, 1, &vp);
    s->vkCmdSetScissor(s->cb, 0, 1, &sc);
    s->vkCmdDraw(s->cb, 3, 1, 0, 0);
    s->vkCmdEndRenderPass(s->cb);
}

/* The GPU work is done when this returns; the thread sleeps meanwhile (the fence's sync fd in poll) where the driver exports one. */
static void wait_done(struct svk *s, VkFence f)
{
    if (s->vkGetFenceFdKHR) {
        VkFenceGetFdInfoKHR gi = { .sType = VK_STRUCTURE_TYPE_FENCE_GET_FD_INFO_KHR, .fence = f, .handleType = VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT };
        int fd = -1;
        if (s->vkGetFenceFdKHR(s->dev, &gi, &fd) == VK_SUCCESS) {
            if (fd < 0) return;  /* already signalled */
            struct pollfd pf = { fd, POLLIN, 0 };
            int r = 0;
            for (int i = 0; i < 5 && r <= 0; i++) do r = poll(&pf, 1, 1000); while (r < 0 && errno == EINTR);
            close(fd);
            if (r > 0) return;
        }
    }
    s->vkQueueWaitIdle(s->q);
}

/* Records, submits and waits for one frame: buffer `buf` through FSR 1 (or, for a source larger than the output, the area downscale) into `o`. */
int svk_record(struct svk *s, int buf, int ow, int oh, float sharp, struct svk_slot *o)
{
    struct svk_in *in = &s->in[buf];
    int down = in->w > ow || in->h > oh;  /* render scale above 100: the area downscale (as the GLES path) */
    if (!down && target(s, &s->mid, VK_FORMAT_B8G8R8A8_UNORM, s->rp_mid, ow, oh) < 0) return -1;
    struct pc pc = { { (float)in->w, (float)in->h }, { (float)ow / (float)in->w, (float)oh / (float)in->h }, { 1.0f / (float)ow, 1.0f / (float)oh },
        { 1.0f / (float)in->w, 1.0f / (float)in->h }, sharp < 0 ? 0 : sharp > 1 ? 1 : sharp, in->w > ow ? (float)in->w / (float)ow : 0.0f };
    s->vkResetDescriptorPool(s->dev, s->pool, 0);
    VkCommandBufferBeginInfo bg = { .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO, .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT };
    s->vkResetCommandBuffer(s->cb, 0);
    if (s->vkBeginCommandBuffer(s->cb, &bg) != VK_SUCCESS) return -1;
    if (s->has_foreign || !in->seen) {  /* the app's buffer: acquired from outside (its writes visible), sampled as read-only */
        VkImageMemoryBarrier br = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .dstAccessMask = VK_ACCESS_SHADER_READ_BIT, .oldLayout = in->seen ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED,
            .newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, .srcQueueFamilyIndex = s->has_foreign ? VK_QUEUE_FAMILY_FOREIGN_EXT : VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = s->has_foreign ? s->qf : VK_QUEUE_FAMILY_IGNORED, .image = in->i.img, .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 } };
        s->vkCmdPipelineBarrier(s->cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, NULL, 0, NULL, 1, &br);
        in->seen = 1;
    }
    if (down) {
        pass(s, P_DOWN, s->rp_out, o->i.fb, ow, oh, in->i.view, &pc);
    } else {  /* FSR 1: EASU into the intermediate, RCAS into the output */
        pass(s, P_UP, s->rp_mid, s->mid.fb, ow, oh, in->i.view, &pc);
        pass(s, P_CAS, s->rp_out, o->i.fb, ow, oh, s->mid.view, &pc);
    }
    if (s->has_foreign) {  /* the output goes back to the outside (the compositor) */
        VkImageMemoryBarrier br = { .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, .oldLayout = VK_IMAGE_LAYOUT_GENERAL, .newLayout = VK_IMAGE_LAYOUT_GENERAL,
            .srcQueueFamilyIndex = s->qf, .dstQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT, .image = o->i.img, .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 } };
        s->vkCmdPipelineBarrier(s->cb, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, NULL, 0, NULL, 1, &br);
    }
    if (s->vkEndCommandBuffer(s->cb) != VK_SUCCESS) return -1;
    if (!s->fence) {
        VkExportFenceCreateInfo ex = { .sType = VK_STRUCTURE_TYPE_EXPORT_FENCE_CREATE_INFO, .handleTypes = VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT };
        VkFenceCreateInfo fi = { .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO, .pNext = s->vkGetFenceFdKHR ? &ex : NULL };
        if (s->vkCreateFence(s->dev, &fi, NULL, &s->fence) != VK_SUCCESS) { s->fence = VK_NULL_HANDLE; s->vkGetFenceFdKHR = NULL; fi.pNext = NULL; if (s->vkCreateFence(s->dev, &fi, NULL, &s->fence) != VK_SUCCESS) { s->fence = VK_NULL_HANDLE; return -1; } }
    }
    VkSubmitInfo si = { .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &s->cb };
    if (s->vkQueueSubmit(s->q, 1, &si, s->fence) != VK_SUCCESS) return -1;
    wait_done(s, s->fence);
    s->vkResetFences(s->dev, 1, &s->fence);  /* (exporting the sync fd already reset it; harmless then) */
    return 0;
}
