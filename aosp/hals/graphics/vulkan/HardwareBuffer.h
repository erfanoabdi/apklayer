// VK_ANDROID_external_memory_android_hardware_buffer over the host driver: an AHardwareBuffer (one of our gralloc buffers) is imported as
// its dma-buf with its explicit DRM layout, as Driver.cpp does for swapchain buffers. ANGLE needs it for EGL_NATIVE_BUFFER_ANDROID images
// (HWUI's hardware bitmaps), Skia's Vulkan backend for AHardwareBuffer textures. Only RGB/depth formats with a VkFormat; YUV external
// formats (VkExternalFormatANDROID != 0, sampler Y'CbCr conversion) are not offered.
#pragma once
#include <vulkan/vulkan.h>

namespace apklayer::vulkan::hardware_buffer {

constexpr const char* kName = "VK_ANDROID_external_memory_android_hardware_buffer";

// vkCreateDevice succeeded with the extension: the device's entry points below become active
void created(VkPhysicalDevice physical, VkDevice device);
void destroyed(VkDevice device);

// vkGetDeviceProcAddr: vkGetAndroidHardwareBufferPropertiesANDROID, vkGetMemoryAndroidHardwareBufferANDROID and vkAllocateMemory
// (the import) of a device created with the extension; null otherwise
PFN_vkVoidFunction deviceProc(VkDevice device, const char* name);

// vkGetInstanceProcAddr: vkGetPhysicalDeviceImageFormatProperties2[KHR], answering the AHardwareBuffer handle type and calling next
// (the texture emulation's entry point or the host's) for everything else; null for other names
PFN_vkVoidFunction instanceProc(const char* name, PFN_vkVoidFunction next);

// vkCreateImage: true when the image is created for AHardwareBuffer memory (handled here, *result set)
bool createImage(VkDevice device, const VkImageCreateInfo* info, const VkAllocationCallbacks* allocator, VkImage* image, VkResult* result);

}  // namespace apklayer::vulkan::hardware_buffer
