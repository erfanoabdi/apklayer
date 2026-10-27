#!/bin/sh
# apklayer patch: retain host Vulkan loader vtables when reusing Android's loader.
set -eu
python3 - "$1" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1]) / 'frameworks/native/vulkan/libvulkan'
p = root / 'driver.h'
s = p.read_text()
marker = '// apklayer patch: side table preserves the host loader dispatch pointer.'
if marker not in s:
    s = s.replace('#include <inttypes.h>', '#include <inttypes.h>\n#include <stdlib.h>')
    at = s.index('template <typename DispatchableType>\nbool SetDataInternal')
    s = s[:at] + '''// apklayer patch: side table preserves the host loader dispatch pointer.
// Host libvulkan and Android libvulkan must never overwrite each other's vtables.
bool HostDispatchEnabled();
bool SetHostData(const void* object, const void* data);
void* GetHostData(const void* object);
void ForgetHostData(const void* data);

''' + s[at:]
    s = s.replace('    StaticAssertDispatchable(dispatchable);', '''    StaticAssertDispatchable(dispatchable);
    if (HostDispatchEnabled()) return SetHostData(dispatchable, data);''', 1)
    at = s.index('void* GetDataInternal(')
    s = s[:at] + s[at:].replace('    StaticAssertDispatchable(dispatchable);', '''    StaticAssertDispatchable(dispatchable);
    if (HostDispatchEnabled()) return GetHostData(dispatchable);''', 1)
    p.write_text(s)
p = root / 'driver.cpp'
s = p.read_text()
if marker not in s:
    at = s.index('namespace driver {') + len('namespace driver {')
    s = s[:at] + '''
// apklayer patch: side table preserves the host loader dispatch pointer.
namespace {
std::mutex host_dispatch_mutex;
struct HostDispatchEntry { const void* data; VkCommandPool pool; };
std::unordered_map<const void*, HostDispatchEntry> host_dispatch_data;
}
bool HostDispatchEnabled() {
    static const bool enabled = getenv("APKLAYER_VULKAN_HOST") != nullptr;
    return enabled;
}
bool SetHostData(const void* object, const void* data) {
    std::lock_guard<std::mutex> lock(host_dispatch_mutex);
    host_dispatch_data[object] = {data, VK_NULL_HANDLE};
    return true;
}
void* GetHostData(const void* object) {
    std::lock_guard<std::mutex> lock(host_dispatch_mutex);
    auto it = host_dispatch_data.find(object);
    return it == host_dispatch_data.end() ? nullptr : const_cast<void*>(it->second.data);
}
void ForgetHostData(const void* data) {
    if (!HostDispatchEnabled()) return;
    std::lock_guard<std::mutex> lock(host_dispatch_mutex);
    for (auto it = host_dispatch_data.begin(); it != host_dispatch_data.end();) {
        if (it->second.data == data) it = host_dispatch_data.erase(it);
        else ++it;
    }
}
''' + s[at:]
    s = s.replace('#include <algorithm>', '#include <algorithm>\n#include <mutex>\n#include <unordered_map>')
    s = s.replace('    data->~InstanceData();', '    ForgetHostData(data);\n    data->~InstanceData();')
    s = s.replace('    data->~DeviceData();', '    ForgetHostData(data);\n    data->~DeviceData();')
    p.write_text(s)
# apklayer patch: command-buffer entries retire with their buffer or pool,
# rather than accumulating until the whole device is destroyed.
p = root / 'driver.cpp'
s = p.read_text()
if 'void HostFreeCommandBuffers(' not in s:
    s = s.replace('std::unordered_map<const void*, const void*> host_dispatch_data;',
        'struct HostDispatchEntry { const void* data; VkCommandPool pool; };\nstd::unordered_map<const void*, HostDispatchEntry> host_dispatch_data;')
    s = s.replace('host_dispatch_data[object] = data;', 'host_dispatch_data[object] = {data, VK_NULL_HANDLE};')
    s = s.replace('const_cast<void*>(it->second)', 'const_cast<void*>(it->second.data)')
    s = s.replace('if (it->second == data)', 'if (it->second.data == data)')
    at = s.index('PFN_vkVoidFunction GetDeviceProcAddr(VkDevice device,')
    s = s[:at] + '''// apklayer patch: retire side-table entries on command-buffer/pool destruction.
void HostFreeCommandBuffers(VkDevice device, VkCommandPool pool, uint32_t count,
                           const VkCommandBuffer* buffers) {
    auto fn = reinterpret_cast<PFN_vkFreeCommandBuffers>(
        GetData(device).driver.GetDeviceProcAddr(device, "vkFreeCommandBuffers"));
    fn(device, pool, count, buffers);
    std::lock_guard<std::mutex> lock(host_dispatch_mutex);
    for (uint32_t i = 0; i < count; ++i) host_dispatch_data.erase(buffers[i]);
}
void HostDestroyCommandPool(VkDevice device, VkCommandPool pool,
                           const VkAllocationCallbacks* allocator) {
    const void* data = &GetData(device);
    auto fn = reinterpret_cast<PFN_vkDestroyCommandPool>(
        GetData(device).driver.GetDeviceProcAddr(device, "vkDestroyCommandPool"));
    fn(device, pool, allocator);
    std::lock_guard<std::mutex> lock(host_dispatch_mutex);
    for (auto it = host_dispatch_data.begin(); it != host_dispatch_data.end();) {
        if (it->second.data == data && it->second.pool == pool && pool != VK_NULL_HANDLE)
            it = host_dispatch_data.erase(it);
        else ++it;
    }
}
void HostTrackCommandBuffers(const VkCommandBufferAllocateInfo* info,
                            VkCommandBuffer* buffers) {
    if (!HostDispatchEnabled()) return;
    std::lock_guard<std::mutex> lock(host_dispatch_mutex);
    for (uint32_t i = 0; i < info->commandBufferCount; ++i)
        host_dispatch_data.at(buffers[i]).pool = info->commandPool;
}

''' + s[at:]
    s = s.replace('PFN_vkVoidFunction GetDeviceProcAddr(VkDevice device, const char* pName) {', '''PFN_vkVoidFunction GetDeviceProcAddr(VkDevice device, const char* pName) {
    if (HostDispatchEnabled()) {
        if (!strcmp(pName, "vkFreeCommandBuffers"))
            return reinterpret_cast<PFN_vkVoidFunction>(HostFreeCommandBuffers);
        if (!strcmp(pName, "vkDestroyCommandPool"))
            return reinterpret_cast<PFN_vkVoidFunction>(HostDestroyCommandPool);
    }''')
    s = s.replace('            SetData(pCommandBuffers[i], data);', '            SetData(pCommandBuffers[i], data);\n        HostTrackCommandBuffers(pAllocateInfo, pCommandBuffers);')
    p.write_text(s)
# AOSP assumes every installed driver can present Android buffers. A host HAL
# without native-buffer import must not inherit those unconditional WSI claims.
p = root / 'driver.h'
s = p.read_text()
if 'bool HostPresentationSupported(VkPhysicalDevice' not in s:
    s = s.replace('bool HostDispatchEnabled();', 'bool HostDispatchEnabled();\nbool HostPresentationSupported(VkPhysicalDevice physical);')
    p.write_text(s)
p = root / 'driver.cpp'
s = p.read_text()
# Keep reruns idempotent while replacing this small host-only helper.
start = s.find('// apklayer patch: require Android native-buffer support for WSI.')
if start >= 0:
    end = s.index('bool OpenHAL() {', start)
    s = s[:start] + s[end:]
at = s.index('bool OpenHAL() {')
s = s[:at] + '''// apklayer patch: require Android native-buffer support for WSI.
bool HostPresentationSupported(VkPhysicalDevice physical) {
    if (!HostDispatchEnabled()) return true;
    const auto enumerate = GetData(physical).driver.EnumerateDeviceExtensionProperties;
    uint32_t count = 0;
    if (enumerate(physical, nullptr, &count, nullptr) != VK_SUCCESS) return false;
    std::vector<VkExtensionProperties> extensions(count);
    if (enumerate(physical, nullptr, &count, extensions.data()) != VK_SUCCESS) return false;
    for (const auto& extension : extensions)
        if (!strcmp(extension.extensionName, VK_ANDROID_NATIVE_BUFFER_EXTENSION_NAME)) return true;
    return false;
}

''' + s[at:]
addition = '''    // apklayer patch: no presentation-dependent extensions without a buffer importer.
    if (!HostPresentationSupported(physicalDevice)) loader_extensions.clear();

'''
s = s.replace(addition, '')
at = s.index('VkResult EnumerateDeviceExtensionProperties(')
s = s[:at] + s[at:].replace('    // enumerate our extensions first', addition + '    // enumerate our extensions first', 1)
p.write_text(s)
p = root / 'swapchain.cpp'
s = p.read_text()
if 'HostPresentationSupported(pdev)' not in s:
    s = s.replace('GetPhysicalDeviceSurfaceSupportKHR(VkPhysicalDevice /*pdev*/,', 'GetPhysicalDeviceSurfaceSupportKHR(VkPhysicalDevice pdev,')
    s = s.replace('    *supported = VK_TRUE;\n    return VK_SUCCESS;', '    *supported = driver::HostPresentationSupported(pdev) ? VK_TRUE : VK_FALSE;\n    return VK_SUCCESS;', 1)
    p.write_text(s)
PY
