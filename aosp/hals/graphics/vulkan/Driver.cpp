// apklayer patch: original Android Vulkan loader -> host Vulkan loader.
// Keep Android WSI in AOSP. Host dispatchable handles retain their host vtables;
// scripts/patch-vulkan.sh gives Android's loader separate private dispatch data.
#define LOG_TAG "apklayer.vulkan"
#include <hardware/hwvulkan.h>
#include <dlfcn.h>
#include <errno.h>
#include <log/log.h>
#include <algorithm>
#include <cstring>
#include <vector>

namespace {
void* hostLibrary() {
    static void* library = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    return library;
}
template<class T> T host(const char* name) {
    return reinterpret_cast<T>(hostLibrary() ? dlsym(hostLibrary(), name) : nullptr);
}

VkResult copyExtensions(const std::vector<VkExtensionProperties>& properties,
                        uint32_t* count, VkExtensionProperties* out) {
    if (!out) { *count = properties.size(); return VK_SUCCESS; }
    uint32_t copied = std::min(*count, static_cast<uint32_t>(properties.size()));
    std::copy_n(properties.data(), copied, out);
    *count = copied;
    return copied == properties.size() ? VK_SUCCESS : VK_INCOMPLETE;
}

VkResult enumerateInstanceExtensions(const char* layer, uint32_t* count, VkExtensionProperties* out) {
    auto fn = host<PFN_vkEnumerateInstanceExtensionProperties>("vkEnumerateInstanceExtensionProperties");
    if (!fn) return VK_ERROR_INCOMPATIBLE_DRIVER;
    if (layer) return fn(layer, count, out);
    uint32_t size = 0;
    VkResult result = fn(nullptr, &size, nullptr);
    if (result != VK_SUCCESS) return result;
    std::vector<VkExtensionProperties> properties(size);
    result = fn(nullptr, &size, properties.data());
    if (result != VK_SUCCESS) return result;
    properties.resize(size);
    // Android owns its surface handles. Host Linux WSI entry points cannot use
    // those handles, and repeating the loader's own extension names is invalid.
    properties.erase(std::remove_if(properties.begin(), properties.end(), [](const auto& p) {
        return strstr(p.extensionName, "surface") || strstr(p.extensionName, "display") ||
               strstr(p.extensionName, "swapchain");
    }), properties.end());
    return copyExtensions(properties, count, out);
}

VkResult enumerateDeviceExtensions(VkPhysicalDevice physical, const char* layer,
                                   uint32_t* count, VkExtensionProperties* out) {
    auto fn = host<PFN_vkEnumerateDeviceExtensionProperties>("vkEnumerateDeviceExtensionProperties");
    if (!fn) return VK_ERROR_INCOMPATIBLE_DRIVER;
    if (layer) return fn(physical, layer, count, out);
    uint32_t size = 0;
    VkResult result = fn(physical, nullptr, &size, nullptr);
    if (result != VK_SUCCESS) return result;
    std::vector<VkExtensionProperties> properties(size);
    result = fn(physical, nullptr, &size, properties.data());
    if (result != VK_SUCCESS) return result;
    properties.resize(size);
    // Host swapchains require host WSI surfaces. Only VK_ANDROID_native_buffer
    // may become VK_KHR_swapchain in Android's loader; never pass a host WSI
    // extension off as support for ANativeWindow buffers.
    properties.erase(std::remove_if(properties.begin(), properties.end(), [](const auto& p) {
        return strstr(p.extensionName, "swapchain") ||
               !strcmp(p.extensionName, "VK_KHR_incremental_present") ||
               !strcmp(p.extensionName, "VK_KHR_present_id") ||
               !strcmp(p.extensionName, "VK_KHR_present_wait") ||
               !strcmp(p.extensionName, "VK_GOOGLE_display_timing") ||
               !strcmp(p.extensionName, "VK_EXT_hdr_metadata");
    }), properties.end());
    return copyExtensions(properties, count, out);
}

VkResult createInstance(const VkInstanceCreateInfo* info, const VkAllocationCallbacks* allocator,
                        VkInstance* instance) {
    auto fn = host<PFN_vkCreateInstance>("vkCreateInstance");
    return fn ? fn(info, allocator, instance) : VK_ERROR_INCOMPATIBLE_DRIVER;
}

PFN_vkVoidFunction getInstanceProcAddr(VkInstance instance, const char* name) {
    if (!strcmp(name, "vkEnumerateDeviceExtensionProperties"))
        return reinterpret_cast<PFN_vkVoidFunction>(enumerateDeviceExtensions);
    auto fn = host<PFN_vkGetInstanceProcAddr>("vkGetInstanceProcAddr");
    return fn ? fn(instance, name) : nullptr;
}

int closeDevice(hw_device_t* device) {
    delete reinterpret_cast<hwvulkan_device_t*>(device);
    return 0;
}
int openDevice(const hw_module_t* module, const char* id, hw_device_t** out) {
    if (strcmp(id, HWVULKAN_DEVICE_0)) return -EINVAL;
    if (!hostLibrary()) { ALOGE("host Vulkan loader unavailable: %s", dlerror()); return -ENOENT; }
    auto* device = new hwvulkan_device_t{};
    device->common.tag = HARDWARE_DEVICE_TAG;
    device->common.version = HWVULKAN_DEVICE_API_VERSION_0_1;
    device->common.module = const_cast<hw_module_t*>(module);
    device->common.close = closeDevice;
    device->EnumerateInstanceExtensionProperties = enumerateInstanceExtensions;
    device->CreateInstance = createInstance;
    device->GetInstanceProcAddr = getInstanceProcAddr;
    *out = &device->common;
    ALOGI("using the host Vulkan loader; Android presentation not enabled");
    return 0;
}
hw_module_methods_t methods = {.open = openDevice};
} // namespace

extern "C" {
__attribute__((visibility("default"))) hwvulkan_module_t HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = HWVULKAN_MODULE_API_VERSION_0_1,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = HWVULKAN_HARDWARE_MODULE_ID,
        .name = "apklayer host Vulkan",
        .author = "apklayer",
        .methods = &methods,
    },
};
}
