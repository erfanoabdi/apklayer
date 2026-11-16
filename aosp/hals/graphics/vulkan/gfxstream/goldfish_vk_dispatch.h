// apklayer shim for gfxstream's generated dispatch table: only the entries emulated_textures/ calls, filled by TextureEmulation.cpp
// from the host driver's vkGetDeviceProcAddr.
#pragma once
#include <vulkan/vulkan.h>

namespace gfxstream {
namespace host {
namespace vk {
struct VulkanDispatch {
#define APKLAYER_VK_DISPATCH(X)                                                                                                       \
    X(vkAllocateDescriptorSets) X(vkBindImageMemory) X(vkCmdBindDescriptorSets) X(vkCmdBindPipeline) X(vkCmdDispatch)              \
    X(vkCmdPipelineBarrier) X(vkCmdPushConstants) X(vkCreateDescriptorPool) X(vkCreateImage) X(vkCreateImageView)                    \
    X(vkDestroyDescriptorPool) X(vkDestroyImage) X(vkDestroyImageView) X(vkGetImageMemoryRequirements) X(vkUpdateDescriptorSets)  \
    X(vkCreateShaderModule) X(vkDestroyShaderModule) X(vkCreateComputePipelines) X(vkDestroyPipeline)                              \
    X(vkCreateDescriptorSetLayout) X(vkDestroyDescriptorSetLayout) X(vkCreatePipelineLayout) X(vkDestroyPipelineLayout)
#define APKLAYER_VK_MEMBER(name) PFN_##name name = nullptr;
    APKLAYER_VK_DISPATCH(APKLAYER_VK_MEMBER)
#undef APKLAYER_VK_MEMBER
};
}  // namespace vk
}  // namespace host
}  // namespace gfxstream
