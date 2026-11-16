// apklayer shim: the three format predicates emulated_textures/ uses, as in gfxstream's host/vulkan/vk_format_utils.h.
#pragma once
#include <vulkan/vulkan.h>

namespace gfxstream {
namespace host {
namespace vk {
constexpr bool isEtc2(VkFormat format) { return format >= VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK && format <= VK_FORMAT_EAC_R11G11_SNORM_BLOCK; }
// ASTC LDR only (VK_FORMAT_ASTC_4x4_UNORM_BLOCK .. VK_FORMAT_ASTC_12x12_SRGB_BLOCK), not HDR
constexpr bool isAstc(VkFormat format) { return format >= VK_FORMAT_ASTC_4x4_UNORM_BLOCK && format <= VK_FORMAT_ASTC_12x12_SRGB_BLOCK; }
constexpr bool isBc(VkFormat format) { return format >= VK_FORMAT_BC1_RGB_UNORM_BLOCK && format <= VK_FORMAT_BC7_SRGB_BLOCK; }
}  // namespace vk
}  // namespace host
}  // namespace gfxstream
