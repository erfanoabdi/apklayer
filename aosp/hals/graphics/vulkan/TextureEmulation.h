// ASTC and ETC2/EAC images on a host device without them (NVIDIA and AMD desktop drivers): gfxstream's emulation
// (gfxstream/vulkan/emulated_textures, unmodified). The app's image is an uncompressed image of the decoded format; the
// compressed blocks live in one block-per-texel image per mip level beside it, in the same memory; copies go to those, and a
// barrier that makes the image readable runs gfxstream's compute decoder into the app's image (as gfxstream's own host does).
// Only a device whose physical device lacks the feature is touched; APKLAYER_VULKAN_TEXTURE_EMULATION=0 turns it off, =force emulates on every device (tests).
#pragma once
#include <vulkan/vulkan.h>

namespace apklayer::vulkan::textures {

// vkGetInstanceProcAddr: the physical-device queries that report the emulated formats (features, format and image-format
// properties); null for every other name
PFN_vkVoidFunction instanceProc(const char* name);

// vkCreateDevice: prepare() before the host call drops the emulated features from the request (the host would refuse them),
// created() after it restores the app's structs and, on success, sets the device up
struct DeviceRequest {
    VkBool32* cleared[8] = {};
    int count = 0;
};
void prepare(VkPhysicalDevice physical, const VkDeviceCreateInfo* info, DeviceRequest* request);
void created(VkPhysicalDevice physical, VkDevice device, DeviceRequest* request, VkResult result);
void destroyed(VkDevice device);

// vkGetDeviceProcAddr of a device that emulates: our entry point for the names we handle, null otherwise
PFN_vkVoidFunction deviceProc(VkDevice device, const char* name);

// vkCreateImage/vkDestroyImage (the driver's own wrappers call these first): true when the image is emulated and handled here
bool createImage(VkDevice device, const VkImageCreateInfo* info, VkImage* image, VkResult* result);
bool destroyImage(VkDevice device, VkImage image);

}  // namespace apklayer::vulkan::textures
