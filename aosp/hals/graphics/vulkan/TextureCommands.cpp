// ASTC/ETC2 emulation, the command-buffer side (TextureEmulation.h): copies to and from an emulated image go to its block images,
// and a barrier that makes it readable decodes (gfxstream's on_vkCmdCopy* and on_vkCmdPipelineBarrier). The decoder binds its own
// compute pipeline, descriptor sets and push constants, so the app's compute state of the command buffer is kept and bound again
// after it (gfxstream rebinds the pipeline and the sets).
#include "TextureState.h"

#include <cstring>

namespace apklayer::vulkan::textures {
namespace {

constexpr size_t kKeep = 16;  // the most recent compute binds kept to replay (later ones replace earlier ones' effect)

struct SetBind {
    VkPipelineLayout layout;
    uint32_t first;
    std::vector<VkDescriptorSet> sets;
    std::vector<uint32_t> offsets;
};
struct Push {
    VkPipelineLayout layout;
    VkShaderStageFlags stages;
    uint32_t offset;
    std::vector<uint8_t> bytes;
};
struct Command {
    Device* device = nullptr;
    VkPipeline compute = VK_NULL_HANDLE;
    std::vector<SetBind> sets;
    std::vector<Push> pushes;
    void reset() { compute = VK_NULL_HANDLE; sets.clear(); pushes.clear(); }
};
std::unordered_map<VkCommandBuffer, std::unique_ptr<Command>> gCommands;

// a command buffer is used by one thread at a time (externally synchronized): its Command needs the lock only to be found
Command* findCommand(VkCommandBuffer buffer) {
    std::lock_guard<std::mutex> lock(gLock);
    auto it = gCommands.find(buffer);
    return it == gCommands.end() ? nullptr : it->second.get();
}

template<class T> void keep(std::vector<T>& list, T&& item) {
    if (list.size() == kKeep) list.erase(list.begin());
    list.push_back(std::move(item));
}

void restore(VkCommandBuffer buffer, const Command* c) {
    if (!c) return;
    const VulkanDispatch& vk = c->device->vk;
    if (c->compute) vk.vkCmdBindPipeline(buffer, VK_PIPELINE_BIND_POINT_COMPUTE, c->compute);
    for (const auto& s : c->sets)
        vk.vkCmdBindDescriptorSets(buffer, VK_PIPELINE_BIND_POINT_COMPUTE, s.layout, s.first, s.sets.size(), s.sets.data(), s.offsets.size(),
                                   s.offsets.data());
    for (const auto& p : c->pushes) vk.vkCmdPushConstants(buffer, p.layout, p.stages, p.offset, p.bytes.size(), p.bytes.data());
}

VKAPI_ATTR VkResult VKAPI_CALL allocateCommandBuffers(VkDevice device, const VkCommandBufferAllocateInfo* info, VkCommandBuffer* buffers) {
    VkResult result = host<PFN_vkAllocateCommandBuffers>("vkAllocateCommandBuffers")(device, info, buffers);
    Device* d = findDevice(device);
    if (result != VK_SUCCESS || !d) return result;
    std::lock_guard<std::mutex> lock(gLock);
    for (uint32_t i = 0; i < info->commandBufferCount; i++) {
        auto c = std::make_unique<Command>();
        c->device = d;
        gCommands[buffers[i]] = std::move(c);
    }
    return result;
}

VKAPI_ATTR void VKAPI_CALL freeCommandBuffers(VkDevice device, VkCommandPool pool, uint32_t count, const VkCommandBuffer* buffers) {
    {
        std::lock_guard<std::mutex> lock(gLock);
        for (uint32_t i = 0; i < count; i++) gCommands.erase(buffers[i]);
    }
    host<PFN_vkFreeCommandBuffers>("vkFreeCommandBuffers")(device, pool, count, buffers);
}

VKAPI_ATTR VkResult VKAPI_CALL beginCommandBuffer(VkCommandBuffer buffer, const VkCommandBufferBeginInfo* info) {
    if (Command* c = findCommand(buffer)) c->reset();
    return host<PFN_vkBeginCommandBuffer>("vkBeginCommandBuffer")(buffer, info);
}

VKAPI_ATTR void VKAPI_CALL cmdBindPipeline(VkCommandBuffer buffer, VkPipelineBindPoint point, VkPipeline pipeline) {
    if (point == VK_PIPELINE_BIND_POINT_COMPUTE)
        if (Command* c = findCommand(buffer)) c->compute = pipeline;
    host<PFN_vkCmdBindPipeline>("vkCmdBindPipeline")(buffer, point, pipeline);
}

VKAPI_ATTR void VKAPI_CALL cmdBindDescriptorSets(VkCommandBuffer buffer, VkPipelineBindPoint point, VkPipelineLayout layout, uint32_t first,
                                                 uint32_t count, const VkDescriptorSet* sets, uint32_t offsetCount, const uint32_t* offsets) {
    if (point == VK_PIPELINE_BIND_POINT_COMPUTE)
        if (Command* c = findCommand(buffer))
            keep(c->sets, SetBind{layout, first, {sets, sets + count}, {offsets, offsets + offsetCount}});
    host<PFN_vkCmdBindDescriptorSets>("vkCmdBindDescriptorSets")(buffer, point, layout, first, count, sets, offsetCount, offsets);
}

VKAPI_ATTR void VKAPI_CALL cmdPushConstants(VkCommandBuffer buffer, VkPipelineLayout layout, VkShaderStageFlags stages, uint32_t offset,
                                            uint32_t size, const void* values) {
    if (stages & VK_SHADER_STAGE_COMPUTE_BIT)
        if (Command* c = findCommand(buffer)) {
            auto* bytes = static_cast<const uint8_t*>(values);
            keep(c->pushes, Push{layout, stages, offset, {bytes, bytes + size}});
        }
    host<PFN_vkCmdPushConstants>("vkCmdPushConstants")(buffer, layout, stages, offset, size, values);
}

VKAPI_ATTR void VKAPI_CALL cmdCopyBufferToImage(VkCommandBuffer buffer, VkBuffer src, VkImage dst, VkImageLayout layout, uint32_t count,
                                                const VkBufferImageCopy* regions) {
    auto fn = host<PFN_vkCmdCopyBufferToImage>("vkCmdCopyBufferToImage");
    CompressedImageInfo* info = findImage(dst, nullptr);
    if (!info) return fn(buffer, src, dst, layout, count, regions);
    TRACE("vkCmdCopyBufferToImage image %p layout %d regions %u (first: offset %llu mip %u %ux%u)", (void*)dst, layout, count,
          (unsigned long long)regions[0].bufferOffset, regions[0].imageSubresource.mipLevel, regions[0].imageExtent.width, regions[0].imageExtent.height);
    for (uint32_t r = 0; r < count; r++) {
        VkBufferImageCopy region = info->getBufferImageCopy(regions[r]);
        fn(buffer, src, info->compressedMipmap(regions[r].imageSubresource.mipLevel), layout, 1, &region);
    }
}

VKAPI_ATTR void VKAPI_CALL cmdCopyBufferToImage2(VkCommandBuffer buffer, const VkCopyBufferToImageInfo2* copy) {
    Command* c = findCommand(buffer);
    if (!c || !c->device->copyBufferToImage2) return;
    CompressedImageInfo* info = findImage(copy->dstImage, nullptr);
    if (!info) return c->device->copyBufferToImage2(buffer, copy);
    TRACE("vkCmdCopyBufferToImage2 image %p regions %u", (void*)copy->dstImage, copy->regionCount);
    for (uint32_t r = 0; r < copy->regionCount; r++) {
        VkBufferImageCopy2 region = info->getBufferImageCopy(copy->pRegions[r]);
        VkCopyBufferToImageInfo2 one = *copy;
        one.dstImage = info->compressedMipmap(copy->pRegions[r].imageSubresource.mipLevel);
        one.regionCount = 1;
        one.pRegions = &region;
        c->device->copyBufferToImage2(buffer, &one);
    }
}

VKAPI_ATTR void VKAPI_CALL cmdCopyImageToBuffer(VkCommandBuffer buffer, VkImage src, VkImageLayout layout, VkBuffer dst, uint32_t count,
                                                const VkBufferImageCopy* regions) {
    auto fn = host<PFN_vkCmdCopyImageToBuffer>("vkCmdCopyImageToBuffer");
    CompressedImageInfo* info = findImage(src, nullptr);
    if (!info) return fn(buffer, src, layout, dst, count, regions);
    TRACE("vkCmdCopyImageToBuffer image %p regions %u", (void*)src, count);
    for (uint32_t r = 0; r < count; r++) {
        VkBufferImageCopy region = info->getBufferImageCopy(regions[r]);
        fn(buffer, info->compressedMipmap(regions[r].imageSubresource.mipLevel), layout, dst, 1, &region);
    }
}

VKAPI_ATTR void VKAPI_CALL cmdCopyImageToBuffer2(VkCommandBuffer buffer, const VkCopyImageToBufferInfo2* copy) {
    Command* c = findCommand(buffer);
    if (!c || !c->device->copyImageToBuffer2) return;
    CompressedImageInfo* info = findImage(copy->srcImage, nullptr);
    if (!info) return c->device->copyImageToBuffer2(buffer, copy);
    TRACE("vkCmdCopyImageToBuffer2 image %p regions %u", (void*)copy->srcImage, copy->regionCount);
    for (uint32_t r = 0; r < copy->regionCount; r++) {
        VkBufferImageCopy2 region = info->getBufferImageCopy(copy->pRegions[r]);
        VkCopyImageToBufferInfo2 one = *copy;
        one.srcImage = info->compressedMipmap(copy->pRegions[r].imageSubresource.mipLevel);
        one.regionCount = 1;
        one.pRegions = &region;
        c->device->copyImageToBuffer2(buffer, &one);
    }
}

VKAPI_ATTR void VKAPI_CALL cmdCopyImage(VkCommandBuffer buffer, VkImage src, VkImageLayout srcLayout, VkImage dst, VkImageLayout dstLayout,
                                        uint32_t count, const VkImageCopy* regions) {
    auto fn = host<PFN_vkCmdCopyImage>("vkCmdCopyImage");
    CompressedImageInfo* from = findImage(src, nullptr);
    CompressedImageInfo* to = findImage(dst, nullptr);
    if (!from && !to) return fn(buffer, src, srcLayout, dst, dstLayout, count, regions);
    TRACE("vkCmdCopyImage %p%s -> %p%s regions %u (first: mip %u -> %u, %ux%u)", (void*)src, from ? " (emulated)" : "", (void*)dst,
          to ? " (emulated)" : "", count, regions[0].srcSubresource.mipLevel, regions[0].dstSubresource.mipLevel, regions[0].extent.width,
          regions[0].extent.height);
    for (uint32_t r = 0; r < count; r++) {
        VkImageCopy region = CompressedImageInfo::getCompressedMipmapsImageCopy(regions[r], from, to);
        fn(buffer, from ? from->compressedMipmap(regions[r].srcSubresource.mipLevel) : src, srcLayout,
           to ? to->compressedMipmap(regions[r].dstSubresource.mipLevel) : dst, dstLayout, 1, &region);
    }
}

VKAPI_ATTR void VKAPI_CALL cmdCopyImage2(VkCommandBuffer buffer, const VkCopyImageInfo2* copy) {
    Command* c = findCommand(buffer);
    if (!c || !c->device->copyImage2) return;
    CompressedImageInfo* from = findImage(copy->srcImage, nullptr);
    CompressedImageInfo* to = findImage(copy->dstImage, nullptr);
    if (!from && !to) return c->device->copyImage2(buffer, copy);
    TRACE("vkCmdCopyImage2 %p%s -> %p%s regions %u", (void*)copy->srcImage, from ? " (emulated)" : "", (void*)copy->dstImage, to ? " (emulated)" : "",
          copy->regionCount);
    for (uint32_t r = 0; r < copy->regionCount; r++) {
        VkImageCopy2 region = CompressedImageInfo::getCompressedMipmapsImageCopy(copy->pRegions[r], from, to);
        VkCopyImageInfo2 one = *copy;
        if (from) one.srcImage = from->compressedMipmap(copy->pRegions[r].srcSubresource.mipLevel);
        if (to) one.dstImage = to->compressedMipmap(copy->pRegions[r].dstSubresource.mipLevel);
        one.regionCount = 1;
        one.pRegions = &region;
        c->device->copyImage2(buffer, &one);
    }
}

// the emulated images' barriers: decoded when they become readable (gfxstream's decompressIfNeeded), the others collected in `out`;
// true when the decoder ran (the app's compute state is then bound again)
bool decode(VkCommandBuffer buffer, VkPipelineStageFlags src, VkPipelineStageFlags dst, const VkImageMemoryBarrier& barrier,
            CompressedImageInfo* info, Device* d, std::vector<VkImageMemoryBarrier>& out) {
    std::lock_guard<std::mutex> lock(d->decoding);
    bool ran = false;
    if (skipDecode()) out.push_back(barrier);
    else ran = info->decompressIfNeeded(&d->vk, buffer, src, dst, barrier, out);
    TRACE("barrier image %p layout %d -> %d stages 0x%x -> 0x%x levels %u+%u layers %u+%u command buffer %p: %s", (void*)barrier.image,
          barrier.oldLayout, barrier.newLayout, src, dst, barrier.subresourceRange.baseMipLevel, barrier.subresourceRange.levelCount,
          barrier.subresourceRange.baseArrayLayer, barrier.subresourceRange.layerCount, (void*)buffer, ran ? "decoded" : "not decoded");
    return ran;
}

VKAPI_ATTR void VKAPI_CALL cmdPipelineBarrier(VkCommandBuffer buffer, VkPipelineStageFlags src, VkPipelineStageFlags dst,
                                              VkDependencyFlags flags, uint32_t memoryCount, const VkMemoryBarrier* memory,
                                              uint32_t bufferCount, const VkBufferMemoryBarrier* buffers, uint32_t imageCount,
                                              const VkImageMemoryBarrier* images) {
    auto fn = host<PFN_vkCmdPipelineBarrier>("vkCmdPipelineBarrier");
    bool any = false;
    for (uint32_t i = 0; i < imageCount && !any; i++) any = findImage(images[i].image, nullptr) != nullptr;
    if (!any) return fn(buffer, src, dst, flags, memoryCount, memory, bufferCount, buffers, imageCount, images);
    std::vector<VkImageMemoryBarrier> out;
    bool decoded = false;
    for (uint32_t i = 0; i < imageCount; i++) {
        Device* d = nullptr;
        CompressedImageInfo* info = findImage(images[i].image, &d);
        if (!info) out.push_back(images[i]);
        else decoded |= decode(buffer, src, dst, images[i], info, d, out);
    }
    if (decoded) restore(buffer, findCommand(buffer));
    if (memoryCount || bufferCount || !out.empty())
        fn(buffer, src, dst, flags, memoryCount, memory, bufferCount, buffers, out.size(), out.data());
}

// synchronization2 flags the version-1 barrier of the decoder can take: the low 32 bits are the same bits, the rest widened
VkPipelineStageFlags stages1(VkPipelineStageFlags2 stages, VkPipelineStageFlags none) {
    if (!stages) return none;
    return stages >> 32 ? static_cast<VkPipelineStageFlags>(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT) : static_cast<VkPipelineStageFlags>(stages);
}
VkAccessFlags access1(VkAccessFlags2 access) {
    return access >> 32 ? static_cast<VkAccessFlags>(access) | VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT : static_cast<VkAccessFlags>(access);
}

// gfxstream decodes on version-1 barriers only; a synchronization2 barrier of an emulated image takes the same path as one
VKAPI_ATTR void VKAPI_CALL cmdPipelineBarrier2(VkCommandBuffer buffer, const VkDependencyInfo* dependency) {
    Command* c = findCommand(buffer);
    if (!c || !c->device->pipelineBarrier2) return;
    bool any = false;
    for (uint32_t i = 0; i < dependency->imageMemoryBarrierCount && !any; i++)
        any = findImage(dependency->pImageMemoryBarriers[i].image, nullptr) != nullptr;
    if (!any) return c->device->pipelineBarrier2(buffer, dependency);
    std::vector<VkImageMemoryBarrier2> rest;
    bool decoded = false;
    for (uint32_t i = 0; i < dependency->imageMemoryBarrierCount; i++) {
        const VkImageMemoryBarrier2& b = dependency->pImageMemoryBarriers[i];
        Device* d = nullptr;
        CompressedImageInfo* info = findImage(b.image, &d);
        if (!info) { rest.push_back(b); continue; }
        VkImageMemoryBarrier one = {VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, nullptr, access1(b.srcAccessMask), access1(b.dstAccessMask),
                                    b.oldLayout, b.newLayout, b.srcQueueFamilyIndex, b.dstQueueFamilyIndex, b.image, b.subresourceRange};
        VkPipelineStageFlags src = stages1(b.srcStageMask, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);
        VkPipelineStageFlags dst = stages1(b.dstStageMask, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);
        std::vector<VkImageMemoryBarrier> out;
        decoded |= decode(buffer, src, dst, one, info, d, out);
        if (!out.empty()) c->device->vk.vkCmdPipelineBarrier(buffer, src, dst, 0, 0, nullptr, 0, nullptr, out.size(), out.data());
    }
    if (decoded) restore(buffer, c);
    if (dependency->memoryBarrierCount || dependency->bufferMemoryBarrierCount || !rest.empty()) {
        VkDependencyInfo others = *dependency;
        others.imageMemoryBarrierCount = rest.size();
        others.pImageMemoryBarriers = rest.data();
        c->device->pipelineBarrier2(buffer, &others);
    }
}

}  // namespace

void forgetCommands(Device* device) {
    for (auto it = gCommands.begin(); it != gCommands.end();)
        it = it->second->device == device ? gCommands.erase(it) : std::next(it);
}

PFN_vkVoidFunction commandProc(VkDevice device, const char* name) {
    static const struct { const char* name; PFN_vkVoidFunction function; } ours[] = {
        {"vkAllocateCommandBuffers", reinterpret_cast<PFN_vkVoidFunction>(allocateCommandBuffers)},
        {"vkFreeCommandBuffers", reinterpret_cast<PFN_vkVoidFunction>(freeCommandBuffers)},
        {"vkBeginCommandBuffer", reinterpret_cast<PFN_vkVoidFunction>(beginCommandBuffer)},
        {"vkCmdBindPipeline", reinterpret_cast<PFN_vkVoidFunction>(cmdBindPipeline)},
        {"vkCmdBindDescriptorSets", reinterpret_cast<PFN_vkVoidFunction>(cmdBindDescriptorSets)},
        {"vkCmdPushConstants", reinterpret_cast<PFN_vkVoidFunction>(cmdPushConstants)},
        {"vkCmdCopyBufferToImage", reinterpret_cast<PFN_vkVoidFunction>(cmdCopyBufferToImage)},
        {"vkCmdCopyBufferToImage2", reinterpret_cast<PFN_vkVoidFunction>(cmdCopyBufferToImage2)},
        {"vkCmdCopyBufferToImage2KHR", reinterpret_cast<PFN_vkVoidFunction>(cmdCopyBufferToImage2)},
        {"vkCmdCopyImageToBuffer", reinterpret_cast<PFN_vkVoidFunction>(cmdCopyImageToBuffer)},
        {"vkCmdCopyImageToBuffer2", reinterpret_cast<PFN_vkVoidFunction>(cmdCopyImageToBuffer2)},
        {"vkCmdCopyImageToBuffer2KHR", reinterpret_cast<PFN_vkVoidFunction>(cmdCopyImageToBuffer2)},
        {"vkCmdCopyImage", reinterpret_cast<PFN_vkVoidFunction>(cmdCopyImage)},
        {"vkCmdCopyImage2", reinterpret_cast<PFN_vkVoidFunction>(cmdCopyImage2)},
        {"vkCmdCopyImage2KHR", reinterpret_cast<PFN_vkVoidFunction>(cmdCopyImage2)},
        {"vkCmdPipelineBarrier", reinterpret_cast<PFN_vkVoidFunction>(cmdPipelineBarrier)},
        {"vkCmdPipelineBarrier2", reinterpret_cast<PFN_vkVoidFunction>(cmdPipelineBarrier2)},
        {"vkCmdPipelineBarrier2KHR", reinterpret_cast<PFN_vkVoidFunction>(cmdPipelineBarrier2)},
    };
    auto proc = host<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr");
    for (const auto& o : ours)
        if (!strcmp(name, o.name)) return proc(device, name) ? o.function : nullptr;
    return nullptr;
}

}  // namespace apklayer::vulkan::textures
