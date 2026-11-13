// header only: vulkan.apklayer is replayed from its existing build commands (tools/lib/orb-build-vulkan.py); a new source file needs a graph regeneration
// The instance's GPU for Vulkan: apps see only the host's physical devices on the DRM device of $APKLAYER_RENDER_NODE (set per apklayer
// instance by the CLI, cli/gpus.cpp), the one the allocator and EGL use. Matched by VK_EXT_physical_device_drm (vendor:device ids do not
// name a node: Venus reports the host's GPU, not the virtio PCI ids). A node no Vulkan device drives (no GPU driver: EGL renders there with
// llvmpipe) keeps the CPU devices. When a GPU device lacks the extension, nothing is filtered (it might be the one).
#pragma once

#include "../../HostEnvironment.h"
#include <log/log.h>
#include <vulkan/vulkan.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <vector>

namespace apklayer::vulkan {
namespace detail {

inline bool hasDrmExtension(PFN_vkGetInstanceProcAddr gipa, VkInstance instance, VkPhysicalDevice d) {
    auto fn = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(gipa(instance, "vkEnumerateDeviceExtensionProperties"));
    uint32_t count = 0;
    if (!fn || fn(d, nullptr, &count, nullptr) != VK_SUCCESS) return false;
    std::vector<VkExtensionProperties> ext(count);
    if (fn(d, nullptr, &count, ext.data()) != VK_SUCCESS) return false;
    for (const auto& e : ext) if (!strcmp(e.extensionName, VK_EXT_PHYSICAL_DEVICE_DRM_EXTENSION_NAME)) return true;
    return false;
}

template<class T> inline VkResult fill(const std::vector<T>& items, uint32_t* count, T* out) {
    if (!out) { *count = static_cast<uint32_t>(items.size()); return VK_SUCCESS; }
    uint32_t n = std::min<uint32_t>(*count, static_cast<uint32_t>(items.size()));
    std::copy(items.begin(), items.begin() + n, out);
    *count = n;
    return n < items.size() ? VK_INCOMPLETE : VK_SUCCESS;
}

}  // namespace detail

inline VkResult visibleDevices(PFN_vkGetInstanceProcAddr gipa, VkInstance instance, std::vector<VkPhysicalDevice>* out) {
    auto enumerate = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(gipa(instance, "vkEnumeratePhysicalDevices"));
    auto properties2 = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties2>(gipa(instance, "vkGetPhysicalDeviceProperties2"));
    if (!enumerate) return VK_ERROR_INITIALIZATION_FAILED;
    uint32_t count = 0;
    VkResult r = enumerate(instance, &count, nullptr);
    if (r != VK_SUCCESS) return r;
    std::vector<VkPhysicalDevice> all(count);
    if ((r = enumerate(instance, &count, all.data())) < 0) return r;
    all.resize(count);
    *out = all;
    const char* node = apklayer::hostGetenv("APKLAYER_RENDER_NODE");
    struct stat st;
    if (!node || !properties2 || stat(node, &st) != 0) return VK_SUCCESS;
    std::vector<VkPhysicalDevice> match, cpu;
    for (VkPhysicalDevice d : all) {
        VkPhysicalDeviceDrmPropertiesEXT drm = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRM_PROPERTIES_EXT};
        VkPhysicalDeviceProperties2 p = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
        bool known = detail::hasDrmExtension(gipa, instance, d);
        if (known) p.pNext = &drm;
        properties2(d, &p);
        if (p.properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_CPU) { cpu.push_back(d); continue; }
        if (!known) { ALOGW("%s has no VK_EXT_physical_device_drm: every device stays visible", p.properties.deviceName); return VK_SUCCESS; }
        if ((drm.hasRender && makedev(drm.renderMajor, drm.renderMinor) == st.st_rdev) ||
            (drm.hasPrimary && makedev(drm.primaryMajor, drm.primaryMinor) == st.st_rdev))
            match.push_back(d);
    }
    *out = !match.empty() ? match : !cpu.empty() ? cpu : all;
    ALOGI("%s: %zu of %zu Vulkan devices", node, out->size(), all.size());
    return VK_SUCCESS;
}

inline VkResult enumeratePhysicalDevices(PFN_vkGetInstanceProcAddr gipa, VkInstance instance, uint32_t* count, VkPhysicalDevice* out) {
    std::vector<VkPhysicalDevice> visible;
    VkResult r = visibleDevices(gipa, instance, &visible);
    return r == VK_SUCCESS ? detail::fill(visible, count, out) : r;
}

inline VkResult enumeratePhysicalDeviceGroups(PFN_vkGetInstanceProcAddr gipa, VkInstance instance, uint32_t* count, VkPhysicalDeviceGroupProperties* out) {
    auto enumerate = reinterpret_cast<PFN_vkEnumeratePhysicalDeviceGroups>(gipa(instance, "vkEnumeratePhysicalDeviceGroups"));
    std::vector<VkPhysicalDevice> visible;
    VkResult r = visibleDevices(gipa, instance, &visible);
    if (r != VK_SUCCESS || !enumerate) return r != VK_SUCCESS ? r : VK_ERROR_INITIALIZATION_FAILED;
    uint32_t n = 0;
    if ((r = enumerate(instance, &n, nullptr)) != VK_SUCCESS) return r;
    std::vector<VkPhysicalDeviceGroupProperties> groups(n, {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GROUP_PROPERTIES});
    if ((r = enumerate(instance, &n, groups.data())) < 0) return r;
    groups.resize(n);
    std::vector<VkPhysicalDeviceGroupProperties> kept;
    for (const auto& g : groups)
        if (g.physicalDeviceCount && std::find(visible.begin(), visible.end(), g.physicalDevices[0]) != visible.end()) kept.push_back(g);
    if (!out) { *count = static_cast<uint32_t>(kept.size()); return VK_SUCCESS; }
    uint32_t m = std::min<uint32_t>(*count, static_cast<uint32_t>(kept.size()));
    for (uint32_t i = 0; i < m; i++) {
        void* next = out[i].pNext;  // the caller's sType/pNext stay
        out[i] = kept[i];
        out[i].pNext = next;
    }
    *count = m;
    return m < kept.size() ? VK_INCOMPLETE : VK_SUCCESS;
}

}  // namespace apklayer::vulkan
