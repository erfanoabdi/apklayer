// apklayer shim: the enum names emulated_textures/ logs, as numbers.
#pragma once
#include <cstdio>
#include <vulkan/vulkan.h>

namespace apklayer_gfxstream {
inline const char* number(int value) {
    static thread_local char text[4][16];
    static thread_local int next;
    char* out = text[next++ & 3];
    snprintf(out, sizeof(text[0]), "%d", value);
    return out;
}
}  // namespace apklayer_gfxstream
inline const char* string_VkFormat(VkFormat value) { return apklayer_gfxstream::number(value); }
inline const char* string_VkImageType(VkImageType value) { return apklayer_gfxstream::number(value); }
inline const char* string_VkImageLayout(VkImageLayout value) { return apklayer_gfxstream::number(value); }
