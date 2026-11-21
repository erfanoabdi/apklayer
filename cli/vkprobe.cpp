// `apklayer vulkan-probe`: the best GPU's Vulkan level through the host's libvulkan.so.1, for hosts without vulkaninfo (a Halium phone: libhybris's
// Vulkan front end over the vendor driver, no ICD files; any Khronos loader works the same). Prints "<major>.<minor> [1]" like hostVulkan's vulkaninfo path.
// The Vulkan ABI is declared here (the few structs used) so the CLI needs no Vulkan headers.
// Venus (Mesa's virtio-gpu Vulkan, deviceName "Virtio-GPU Venus (...)") is appended as " venus", which hostVulkan turns into its venus flag.
#include "vkprobe.h"

#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

namespace apklayer {
namespace {
struct AppInfo { int sType; const void* next; const char *app; uint32_t appVersion; const char* engine; uint32_t engineVersion, api; };
struct InstanceInfo { int sType; const void* next; uint32_t flags; const AppInfo* app; uint32_t layers; const char* const* layerNames; uint32_t exts; const char* const* extNames; };
using CreateInstance = int (*)(const InstanceInfo*, const void*, void**);
using Enumerate = int (*)(void*, uint32_t*, void**);
using GetProperties = void (*)(void*, void*);  // VkPhysicalDeviceProperties: apiVersion at 0, deviceType at 16 (the rest is skipped in a larger buffer)
using GetFeatures = void (*)(void*, uint32_t*);  // VkPhysicalDeviceFeatures: 55 VkBool32
}  // namespace

int vulkanProbe() {
    void* lib = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!lib) return 1;
    auto create = reinterpret_cast<CreateInstance>(dlsym(lib, "vkCreateInstance"));
    auto enumerate = reinterpret_cast<Enumerate>(dlsym(lib, "vkEnumeratePhysicalDevices"));
    auto props = reinterpret_cast<GetProperties>(dlsym(lib, "vkGetPhysicalDeviceProperties"));
    auto feats = reinterpret_cast<GetFeatures>(dlsym(lib, "vkGetPhysicalDeviceFeatures"));
    if (!create || !enumerate || !props || !feats) return 1;
    AppInfo app{0, nullptr, "apklayer", 1, "apklayer", 1, 1u << 22 | 1u << 12};  // VK_API_VERSION_1_1
    InstanceInfo info{1, nullptr, 0, &app, 0, nullptr, 0, nullptr};
    void* instance = nullptr;
    if (create(&info, nullptr, &instance) != 0) return 1;
    void* devices[16];
    uint32_t count = 16;
    if (enumerate(instance, &count, devices) < 0) return 1;  // VK_INCOMPLETE (> 0) still fills 16
    uint32_t best = 0;
    bool level1 = false, venus = false;
    for (uint32_t i = 0; i < count; i++) {
        alignas(8) uint32_t p[512] = {};
        props(devices[i], p);
        if (p[4] == 4) continue;  // VK_PHYSICAL_DEVICE_TYPE_CPU: no game target
        uint32_t api = ((p[0] >> 12) & 0x3ff) | ((p[0] >> 22) << 16);  // major<<16 | minor, as hostVulkan orders them
        if (api <= best) continue;
        best = api;
        venus = strstr(reinterpret_cast<const char*>(p + 5), "Venus") != nullptr;  // VkPhysicalDeviceProperties::deviceName, at offset 20
        uint32_t f[64] = {};
        feats(devices[i], f);
        // the features of CTS's VulkanFeaturesTest.determineHardwareLevel (the vulkaninfo path's list, by VkPhysicalDeviceFeatures index)
        level1 = true;
        static const int kLevel1[] = {20, 1, 2, 3, 4, 5, 6, 21, 26, 28, 33, 34};
        for (int index : kLevel1) level1 = level1 && f[index];
    }
    if (!best) return 1;
    printf("%u.%u%s%s\n", best >> 16, best & 0xffff, level1 ? " 1" : "", venus ? " venus" : "");
    return 0;
}
}  // namespace apklayer
