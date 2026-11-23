// Diagnostic (TextureState.h dumpRecord): APKLAYER_VULKAN_BLOCK_DUMP=<dir>[:<count>] writes the block data of the first uploads to an emulated
// compressed image, as the app sent them, to files, so a reference decoder can tell bad blocks from a bad decode.
#include "TextureState.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace apklayer::vulkan::textures {
namespace {

const char* environment(const char* name) {
#ifdef __ANDROID__
    return apklayer::hostGetenv(name);
#else
    return std::getenv(name);
#endif
}

struct Setting { std::string dir; int count = 0; };
const Setting& setting() {
    static const Setting s = [] {
        Setting r;
        const char* v = environment("APKLAYER_VULKAN_BLOCK_DUMP");
        if (!v || !*v) return r;
        r.dir = v;
        r.count = 4;
        size_t colon = r.dir.rfind(':');
        if (colon != std::string::npos) { r.count = atoi(r.dir.c_str() + colon + 1); r.dir.resize(colon); }
        return r;
    }();
    return s;
}

struct Pending {
    VkCommandBuffer cmd;
    VkDevice device;
    VkBuffer buffer;
    VkDeviceMemory memory;
    VkDeviceSize size;
    int index;
    uint32_t w, h;
};
std::mutex gDumpLock;
std::vector<Pending> gPending;
int gMade = 0;

}  // namespace

bool dumping() { return setting().count > 0; }

void dumpRecord(Device* d, VkCommandBuffer cmd, VkBuffer src, VkDeviceSize offset, VkDeviceSize size, VkFormat, uint32_t w, uint32_t h) {
    if (!d || !size) return;
    int index;
    {
        std::lock_guard<std::mutex> lock(gDumpLock);
        if (gMade >= setting().count) return;
        index = gMade++;
    }
    VkBufferCreateInfo bi = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bi.size = size;
    bi.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bi.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    VkBuffer buffer = VK_NULL_HANDLE;
    if (host<PFN_vkCreateBuffer>("vkCreateBuffer")(d->device, &bi, nullptr, &buffer) != VK_SUCCESS) return;
    VkMemoryRequirements req;
    host<PFN_vkGetBufferMemoryRequirements>("vkGetBufferMemoryRequirements")(d->device, buffer, &req);
    VkPhysicalDeviceMemoryProperties props;
    host<PFN_vkGetPhysicalDeviceMemoryProperties>("vkGetPhysicalDeviceMemoryProperties")(d->physical, &props);
    uint32_t type = ~0u;
    const VkMemoryPropertyFlags want = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    for (uint32_t i = 0; i < props.memoryTypeCount; i++)
        if ((req.memoryTypeBits & (1u << i)) && (props.memoryTypes[i].propertyFlags & want) == want) { type = i; break; }
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, req.size, type};
    if (type == ~0u || host<PFN_vkAllocateMemory>("vkAllocateMemory")(d->device, &ai, nullptr, &memory) != VK_SUCCESS ||
        host<PFN_vkBindBufferMemory>("vkBindBufferMemory")(d->device, buffer, memory, 0) != VK_SUCCESS) {
        host<PFN_vkDestroyBuffer>("vkDestroyBuffer")(d->device, buffer, nullptr);
        return;
    }
    VkBufferCopy copy = {offset, 0, size};
    host<PFN_vkCmdCopyBuffer>("vkCmdCopyBuffer")(cmd, src, buffer, 1, &copy);
    VkMemoryBarrier barrier = {VK_STRUCTURE_TYPE_MEMORY_BARRIER, nullptr, VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_HOST_READ_BIT};
    host<PFN_vkCmdPipelineBarrier>("vkCmdPipelineBarrier")(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &barrier, 0, nullptr, 0, nullptr);
    std::lock_guard<std::mutex> lock(gDumpLock);
    gPending.push_back({cmd, d->device, buffer, memory, size, index, w, h});
    GFXSTREAM_INFO("block dump %d: %ux%u image, %llu bytes of blocks from buffer offset %llu", index, w, h, (unsigned long long)size, (unsigned long long)offset);
}

void dumpSubmitted(VkQueue queue, uint32_t count, const VkCommandBuffer* cmds) {
    std::vector<Pending> mine;
    {
        std::lock_guard<std::mutex> lock(gDumpLock);
        for (auto it = gPending.begin(); it != gPending.end();) {
            bool in = false;
            for (uint32_t i = 0; i < count; i++) in |= cmds[i] == it->cmd;
            if (in) { mine.push_back(*it); it = gPending.erase(it); } else ++it;
        }
    }
    if (mine.empty()) return;
    host<PFN_vkQueueWaitIdle>("vkQueueWaitIdle")(queue);
    for (const Pending& p : mine) {
        void* map = nullptr;
        if (host<PFN_vkMapMemory>("vkMapMemory")(p.device, p.memory, 0, p.size, 0, &map) == VK_SUCCESS) {
            std::string path = setting().dir + "/blocks-" + std::to_string(p.index) + "-" + std::to_string(p.w) + "x" + std::to_string(p.h) + ".bin";
            if (FILE* f = fopen(path.c_str(), "wb")) {
                fwrite(map, 1, p.size, f);
                fclose(f);
                GFXSTREAM_INFO("block dump %d written to %s", p.index, path.c_str());
            } else GFXSTREAM_INFO("block dump %d: cannot write %s", p.index, path.c_str());
            host<PFN_vkUnmapMemory>("vkUnmapMemory")(p.device, p.memory);
        }
        host<PFN_vkDestroyBuffer>("vkDestroyBuffer")(p.device, p.buffer, nullptr);
        host<PFN_vkFreeMemory>("vkFreeMemory")(p.device, p.memory, nullptr);
    }
}

}  // namespace apklayer::vulkan::textures
