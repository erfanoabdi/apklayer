// apklayer stub for gfxstream's CPU ASTC path (astc_texture.cpp, astc-codec): not vendored, the GPU decoder is always used.
// Shaped like gfxstream's own astc_cpu_decompressor_noop.cpp: never created (initAstcCpuDecompression is not called).
#pragma once
#include <cstddef>
#include <cstdint>
#include "goldfish_vk_dispatch.h"

namespace gfxstream {
namespace host {
namespace vk {
struct VkDecoderContext {};
struct AstcCpuDecompressor {
    static AstcCpuDecompressor& get() { static AstcCpuDecompressor none; return none; }
};
class AstcTexture {
   public:
    AstcTexture(VulkanDispatch*, VkDevice, VkPhysicalDevice, VkExtent3D, uint32_t, uint32_t, AstcCpuDecompressor*) {}
    bool successfullyDecompressed() const { return false; }
    void on_vkCmdCopyBufferToImage(VkCommandBuffer, uint8_t*, size_t, VkImage, VkImageLayout, uint32_t, const VkBufferImageCopy*,
                                   const VkDecoderContext&) {}
    void on_vkCmdCopyBufferToImage2(VkCommandBuffer, uint8_t*, size_t, const VkCopyBufferToImageInfo2*, const VkDecoderContext&) {}
};
}  // namespace vk
}  // namespace host
}  // namespace gfxstream
