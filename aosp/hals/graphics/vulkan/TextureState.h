// Internal state of the texture emulation (TextureEmulation.cpp, TextureCommands.cpp).
#pragma once
#include "TextureEmulation.h"

#include <algorithm>
#include <atomic>
#include <initializer_list>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "gfxstream/common/logging.h"
#include "vulkan/emulated_textures/compressed_image_info.h"
#include "vulkan/vk_format_utils.h"

namespace apklayer::vulkan::textures {

using gfxstream::host::vk::CompressedImageInfo;
using gfxstream::host::vk::GpuDecompressionPipelineManager;
using gfxstream::host::vk::isAstc;
using gfxstream::host::vk::isEtc2;
using gfxstream::host::vk::VulkanDispatch;

struct Device {
    VkDevice device = VK_NULL_HANDLE;
    bool astc = false, etc2 = false;
    VulkanDispatch vk;  // what gfxstream's code calls
    PFN_vkGetImageMemoryRequirements2 getImageMemoryRequirements2 = nullptr;
    PFN_vkGetDeviceImageMemoryRequirements getDeviceImageMemoryRequirements = nullptr;
    PFN_vkBindImageMemory2 bindImageMemory2 = nullptr;
    PFN_vkCmdCopyBufferToImage2 copyBufferToImage2 = nullptr;
    PFN_vkCmdCopyImage2 copyImage2 = nullptr;
    PFN_vkCmdCopyImageToBuffer2 copyImageToBuffer2 = nullptr;
    PFN_vkCmdPipelineBarrier2 pipelineBarrier2 = nullptr;
    std::unique_ptr<GpuDecompressionPipelineManager> pipelines;
    std::mutex decoding;  // gfxstream's pipeline manager is not thread-safe (its callers hold the global lock)
};

struct Image {
    Device* device = nullptr;
    std::unique_ptr<CompressedImageInfo> info;
};

extern std::mutex gLock;  // gDevices, gImages and the command buffers' map
extern std::unordered_map<VkDevice, std::unique_ptr<Device>> gDevices;
extern std::unordered_map<VkImage, Image> gImages;
extern std::atomic<int> gEmulatedImages;  // 0: nothing to look up

void* hostSymbol(const char* name);  // the host loader's export (it dispatches on the handle)
template<class T> T host(const char* name) { return reinterpret_cast<T>(hostSymbol(name)); }
Device* findDevice(VkDevice device);
CompressedImageInfo* findImage(VkImage image, Device** device);  // null: not emulated

// a pNext chain without the given structs for the length of a host call (the app's structs are relinked back after)
struct Unlink {
    Unlink(const void* chain, std::initializer_list<VkStructureType> drop);
    ~Unlink();
    const void* head = nullptr;
    std::vector<std::pair<VkBaseInStructure*, const VkBaseInStructure*>> original;
};

PFN_vkVoidFunction commandProc(VkDevice device, const char* name);  // TextureCommands.cpp
void forgetCommands(Device* device);                                // with gLock held

}  // namespace apklayer::vulkan::textures
