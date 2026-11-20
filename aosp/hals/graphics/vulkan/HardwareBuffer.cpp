// apklayer: VK_ANDROID_external_memory_android_hardware_buffer over the host driver (HardwareBuffer.h). The flow of Mesa's Android runtime
// (vk_android.c: properties from the gralloc handle, the image created with the buffer's explicit DRM layout, the memory a dedicated dma-buf
// import), on a host driver that only knows dma-bufs.
#define LOG_TAG "apklayer.vulkan"
#define VK_USE_PLATFORM_ANDROID_KHR 1
#include "HardwareBuffer.h"

#include <android/hardware_buffer.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <log/log.h>
#include <unistd.h>
#include <vndk/hardware_buffer.h>
#include <vulkan/vulkan_android.h>

#include <algorithm>
#include <cstring>
#include <initializer_list>
#include <map>
#include <mutex>
#include <utility>
#include <vector>

#include "gralloc_handle.h"

namespace apklayer::vulkan::hardware_buffer {
namespace {

constexpr uint64_t kModInvalid = 0x00ffffffffffffffull;  // DRM_FORMAT_MOD_INVALID: the allocator did not know (imported as LINEAR, as Driver.cpp)

template<class T> T host(const char* name) {
    static void* library = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    return reinterpret_cast<T>(library ? dlsym(library, name) : nullptr);
}

struct Device {
    VkPhysicalDevice physical;
    PFN_vkCreateImage createImage;
    PFN_vkAllocateMemory allocateMemory;
    PFN_vkGetMemoryFdPropertiesKHR memoryFdProperties;
};
std::mutex gLock;
std::map<VkDevice, Device> gDevices;
PFN_vkGetPhysicalDeviceImageFormatProperties2 gNextImageFormat = nullptr;

bool find(VkDevice device, Device* out) {
    std::lock_guard<std::mutex> lock(gLock);
    auto it = gDevices.find(device);
    if (it == gDevices.end()) return false;
    *out = it->second;
    return true;
}

// shortcut: the host image needs the buffer's DRM layout at vkCreateImage, which names no buffer (the buffer comes with the memory). Every
// importer we know (ANGLE's HardwareBufferImageSiblingVkAndroid, Skia's GrAHardwareBufferUtils) asks vkGetAndroidHardwareBufferPropertiesANDROID
// for the buffer right before it creates the image on the same thread: that query's layout is kept here and used by the next matching image.
struct Pending {
    VkDevice device = VK_NULL_HANDLE;
    gralloc::HandleData data{};
};
thread_local Pending tPending;

const VkBaseInStructure* findStruct(const void* chain, VkStructureType type) {
    for (auto* link = static_cast<const VkBaseInStructure*>(chain); link; link = link->pNext)
        if (link->sType == type) return link;
    return nullptr;
}

// a pNext chain without the given structs for the length of a host call (the app's links are restored after)
struct Unlink {
    Unlink(const void* chain, std::initializer_list<VkStructureType> drop) {
        VkBaseInStructure* last = nullptr;
        for (auto* link = static_cast<const VkBaseInStructure*>(chain); link;) {
            auto* node = const_cast<VkBaseInStructure*>(link);
            original.emplace_back(node, node->pNext);
            link = node->pNext;
            if (std::find(drop.begin(), drop.end(), node->sType) != drop.end()) continue;
            if (last) last->pNext = node; else head = node;
            last = node;
        }
        if (last) last->pNext = nullptr;
    }
    ~Unlink() { for (auto& entry : original) entry.first->pNext = entry.second; }
    const void* head = nullptr;
    std::vector<std::pair<VkBaseInStructure*, const VkBaseInStructure*>> original;
};

// AHardwareBuffer formats (the ones the Vulkan spec's AHardwareBuffer table names, and BGRA as Mesa adds it); YUV and BLOB have none
VkFormat vkFormat(uint32_t format) {
    switch (format) {
        case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM:
        case AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM: return VK_FORMAT_R8G8B8A8_UNORM;
        case AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM: return VK_FORMAT_R8G8B8_UNORM;
        case AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM: return VK_FORMAT_R5G6B5_UNORM_PACK16;
        case AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT: return VK_FORMAT_R16G16B16A16_SFLOAT;
        case AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM: return VK_FORMAT_A2B10G10R10_UNORM_PACK32;
        case AHARDWAREBUFFER_FORMAT_D16_UNORM: return VK_FORMAT_D16_UNORM;
        case AHARDWAREBUFFER_FORMAT_D24_UNORM: return VK_FORMAT_X8_D24_UNORM_PACK32;
        case AHARDWAREBUFFER_FORMAT_D24_UNORM_S8_UINT: return VK_FORMAT_D24_UNORM_S8_UINT;
        case AHARDWAREBUFFER_FORMAT_D32_FLOAT: return VK_FORMAT_D32_SFLOAT;
        case AHARDWAREBUFFER_FORMAT_D32_FLOAT_S8_UINT: return VK_FORMAT_D32_SFLOAT_S8_UINT;
        case AHARDWAREBUFFER_FORMAT_S8_UINT: return VK_FORMAT_S8_UINT;
        case AHARDWAREBUFFER_FORMAT_R8_UNORM: return VK_FORMAT_R8_UNORM;
        case 5 /* HAL_PIXEL_FORMAT_BGRA_8888 */: return VK_FORMAT_B8G8R8A8_UNORM;
        default: return VK_FORMAT_UNDEFINED;
    }
}

// what the host can do with this format at the buffer's modifier (the image is created with it)
VkFormatFeatureFlags modifierFeatures(VkPhysicalDevice physical, VkFormat format, uint64_t modifier) {
    auto properties = host<PFN_vkGetPhysicalDeviceFormatProperties2>("vkGetPhysicalDeviceFormatProperties2");
    if (!properties || format == VK_FORMAT_UNDEFINED) return 0;
    if (modifier == kModInvalid) modifier = 0;
    VkDrmFormatModifierPropertiesListEXT list = {VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_EXT};
    VkFormatProperties2 all = {VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2, &list};
    properties(physical, format, &all);
    std::vector<VkDrmFormatModifierPropertiesEXT> modifiers(list.drmFormatModifierCount);
    list.pDrmFormatModifierProperties = modifiers.data();
    properties(physical, format, &all);
    for (uint32_t i = 0; i < list.drmFormatModifierCount; i++)
        if (modifiers[i].drmFormatModifier == modifier) return modifiers[i].drmFormatModifierTilingFeatures;
    return 0;
}

bool decodeBuffer(const AHardwareBuffer* buffer, const native_handle_t** handle, gralloc::HandleData* data) {
    *handle = buffer ? AHardwareBuffer_getNativeHandle(buffer) : nullptr;
    // packed buffers (R8/R16 planes sharing one allocation) have no single-image layout
    return *handle && gralloc::decode(*handle, data) && data->numPlanes >= 1 && !data->packed;
}

VKAPI_ATTR VkResult VKAPI_CALL getAndroidHardwareBufferProperties(VkDevice device, const AHardwareBuffer* buffer,
                                                                  VkAndroidHardwareBufferPropertiesANDROID* properties) {
    Device d;
    const native_handle_t* handle;
    gralloc::HandleData data;
    if (!find(device, &d)) { ALOGE("vkGetAndroidHardwareBufferPropertiesANDROID: device %p was not created with %s", device, kName); return VK_ERROR_INVALID_EXTERNAL_HANDLE; }
    if (!decodeBuffer(buffer, &handle, &data)) {
        ALOGE("vkGetAndroidHardwareBufferPropertiesANDROID: buffer %p (handle %p, fds %d, ints %d) is not an unpacked apklayer gralloc buffer", buffer,
              handle, handle ? handle->numFds : -1, handle ? handle->numInts : -1);
        return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    }
    int fd = handle->data[0];
    off_t size = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);
    if (size <= 0) { ALOGE("vkGetAndroidHardwareBufferPropertiesANDROID: dma-buf size %lld", static_cast<long long>(size)); return VK_ERROR_INVALID_EXTERNAL_HANDLE; }
    uint32_t typeBits = 0;
    VkMemoryFdPropertiesKHR fdProperties = {VK_STRUCTURE_TYPE_MEMORY_FD_PROPERTIES_KHR};
    if (d.memoryFdProperties && d.memoryFdProperties(device, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, fd, &fdProperties) == VK_SUCCESS)
        typeBits = fdProperties.memoryTypeBits;
    if (!typeBits) {  // the driver does not say: any of its types (the import itself checks)
        VkPhysicalDeviceMemoryProperties memory = {};
        if (auto fn = host<PFN_vkGetPhysicalDeviceMemoryProperties>("vkGetPhysicalDeviceMemoryProperties")) fn(d.physical, &memory);
        typeBits = memory.memoryTypeCount >= 32 ? ~0u : (1u << memory.memoryTypeCount) - 1;
    }
    properties->allocationSize = static_cast<VkDeviceSize>(size);
    properties->memoryTypeBits = typeBits;

    VkFormat format = vkFormat(data.androidFormat);
    VkFormatFeatureFlags features = modifierFeatures(d.physical, format, data.modifier);
    if (!features) {
        ALOGW("AHardwareBuffer %ux%u android format %u fourcc 0x%x modifier 0x%llx: the host has no %s features at that modifier", data.width,
              data.height, data.androidFormat, data.fourcc, static_cast<unsigned long long>(data.modifier), format ? "such" : "VkFormat");
        format = VK_FORMAT_UNDEFINED;
    }
    for (auto* link = reinterpret_cast<VkBaseOutStructure*>(properties->pNext); link; link = link->pNext) {
        if (link->sType == VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID) {
            auto* out = reinterpret_cast<VkAndroidHardwareBufferFormatPropertiesANDROID*>(link);
            out->format = format;
            out->externalFormat = data.androidFormat;  // must not be 0; offered only together with format (no external-format images)
            out->formatFeatures = features;
            out->samplerYcbcrConversionComponents = {};  // identity
            out->suggestedYcbcrModel = VK_SAMPLER_YCBCR_MODEL_CONVERSION_RGB_IDENTITY;
            out->suggestedYcbcrRange = VK_SAMPLER_YCBCR_RANGE_ITU_FULL;
            out->suggestedXChromaOffset = out->suggestedYChromaOffset = VK_CHROMA_LOCATION_COSITED_EVEN;
        } else if (link->sType == VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_2_ANDROID) {
            auto* out = reinterpret_cast<VkAndroidHardwareBufferFormatProperties2ANDROID*>(link);
            out->format = format;
            out->externalFormat = data.androidFormat;
            out->formatFeatures = features;
            out->samplerYcbcrConversionComponents = {};
            out->suggestedYcbcrModel = VK_SAMPLER_YCBCR_MODEL_CONVERSION_RGB_IDENTITY;
            out->suggestedYcbcrRange = VK_SAMPLER_YCBCR_RANGE_ITU_FULL;
            out->suggestedXChromaOffset = out->suggestedYChromaOffset = VK_CHROMA_LOCATION_COSITED_EVEN;
        }
    }
    tPending = {device, data};
    return VK_SUCCESS;
}

VKAPI_ATTR VkResult VKAPI_CALL getMemoryAndroidHardwareBuffer(VkDevice, const VkMemoryGetAndroidHardwareBufferInfoANDROID*, AHardwareBuffer**) {
    ALOGE("vkGetMemoryAndroidHardwareBufferANDROID: exporting memory as an AHardwareBuffer is not supported");
    return VK_ERROR_OUT_OF_HOST_MEMORY;
}

// the import: the buffer's dma-buf (our dup; a successful import owns it) in place of the AHardwareBuffer, the dedicated image kept
VKAPI_ATTR VkResult VKAPI_CALL allocateMemory(VkDevice device, const VkMemoryAllocateInfo* info, const VkAllocationCallbacks* allocator,
                                              VkDeviceMemory* memory) {
    Device d;
    if (!find(device, &d) || !d.allocateMemory) return VK_ERROR_INITIALIZATION_FAILED;
    auto* import = reinterpret_cast<const VkImportAndroidHardwareBufferInfoANDROID*>(
        findStruct(info->pNext, VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID));
    if (!import) return d.allocateMemory(device, info, allocator, memory);
    const native_handle_t* handle;
    gralloc::HandleData data;
    if (!decodeBuffer(import->buffer, &handle, &data)) return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    int fd = fcntl(handle->data[0], F_DUPFD_CLOEXEC, 3);
    if (fd < 0) return VK_ERROR_TOO_MANY_OBJECTS;
    Unlink chain(info->pNext, {VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID});
    VkImportMemoryFdInfoKHR fdImport = {VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR, chain.head, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, fd};
    VkMemoryAllocateInfo hostInfo = *info;
    hostInfo.pNext = &fdImport;
    VkResult result = d.allocateMemory(device, &hostInfo, allocator, memory);
    if (result != VK_SUCCESS) {
        close(fd);
        ALOGE("AHardwareBuffer import %ux%u fourcc 0x%x modifier 0x%llx: vkAllocateMemory %d", data.width, data.height, data.fourcc,
              static_cast<unsigned long long>(data.modifier), result);
    }
    return result;
}

VKAPI_ATTR VkResult VKAPI_CALL getImageFormatProperties2(VkPhysicalDevice physical, const VkPhysicalDeviceImageFormatInfo2* info,
                                                         VkImageFormatProperties2* out) {
    auto next = gNextImageFormat;
    auto* external = reinterpret_cast<const VkPhysicalDeviceExternalImageFormatInfo*>(
        findStruct(info->pNext, VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_IMAGE_FORMAT_INFO));
    if (!external || external->handleType != VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID) return next(physical, info, out);
    // the host is asked about the plain image (it knows no AHardwareBuffer handle type); the external answer is ours: an imported buffer is
    // always a dedicated allocation of the image it was made for
    void* appNext = out->pNext;
    VkResult result;
    {
        Unlink in(info->pNext, {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_IMAGE_FORMAT_INFO});
        Unlink outChain(appNext, {VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_USAGE_ANDROID});
        VkPhysicalDeviceImageFormatInfo2 query = *info;
        query.pNext = in.head;
        out->pNext = const_cast<void*>(outChain.head);
        result = next(physical, &query, out);
        out->pNext = appNext;
    }  // the app's chains linked again
    if (result != VK_SUCCESS) return result;
    for (auto* link = reinterpret_cast<VkBaseOutStructure*>(appNext); link; link = link->pNext) {
        if (link->sType == VK_STRUCTURE_TYPE_EXTERNAL_IMAGE_FORMAT_PROPERTIES) {
            reinterpret_cast<VkExternalImageFormatProperties*>(link)->externalMemoryProperties = {
                VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT | VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT,
                VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID, VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID};
        } else if (link->sType == VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_USAGE_ANDROID) {
            uint64_t usage = 0;
            if (info->usage & (VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT)) usage |= AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
            if (info->usage & (VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT))
                usage |= AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER;
            if (info->flags & VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT) usage |= AHARDWAREBUFFER_USAGE_GPU_CUBE_MAP;
            reinterpret_cast<VkAndroidHardwareBufferUsageANDROID*>(link)->androidHardwareBufferUsage = usage;
        }
    }
    return VK_SUCCESS;
}

}  // namespace

void created(VkPhysicalDevice physical, VkDevice device) {
    auto proc = host<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr");
    if (!proc) return;
    Device d = {physical, reinterpret_cast<PFN_vkCreateImage>(proc(device, "vkCreateImage")),
                reinterpret_cast<PFN_vkAllocateMemory>(proc(device, "vkAllocateMemory")),
                reinterpret_cast<PFN_vkGetMemoryFdPropertiesKHR>(proc(device, "vkGetMemoryFdPropertiesKHR"))};
    ALOGI("%s on device %p (AHardwareBuffer imports as dma-bufs)", kName, device);
    std::lock_guard<std::mutex> lock(gLock);
    gDevices[device] = d;
}

void destroyed(VkDevice device) {
    std::lock_guard<std::mutex> lock(gLock);
    gDevices.erase(device);
}

PFN_vkVoidFunction deviceProc(VkDevice device, const char* name) {
    {
        std::lock_guard<std::mutex> lock(gLock);
        if (!gDevices.count(device)) return nullptr;
    }
    if (!strcmp(name, "vkGetAndroidHardwareBufferPropertiesANDROID")) return reinterpret_cast<PFN_vkVoidFunction>(getAndroidHardwareBufferProperties);
    if (!strcmp(name, "vkGetMemoryAndroidHardwareBufferANDROID")) return reinterpret_cast<PFN_vkVoidFunction>(getMemoryAndroidHardwareBuffer);
    if (!strcmp(name, "vkAllocateMemory")) return reinterpret_cast<PFN_vkVoidFunction>(allocateMemory);
    return nullptr;
}

PFN_vkVoidFunction instanceProc(const char* name, PFN_vkVoidFunction next) {
    if (strcmp(name, "vkGetPhysicalDeviceImageFormatProperties2") && strcmp(name, "vkGetPhysicalDeviceImageFormatProperties2KHR")) return nullptr;
    if (!next) return nullptr;
    gNextImageFormat = reinterpret_cast<PFN_vkGetPhysicalDeviceImageFormatProperties2>(next);  // the same function under either name
    return reinterpret_cast<PFN_vkVoidFunction>(getImageFormatProperties2);
}

bool createImage(VkDevice device, const VkImageCreateInfo* info, const VkAllocationCallbacks* allocator, VkImage* image, VkResult* result) {
    auto* external = reinterpret_cast<const VkExternalMemoryImageCreateInfo*>(
        findStruct(info->pNext, VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO));
    if (!external || !(external->handleTypes & VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID)) return false;
    Device d;
    Pending pending = tPending;
    tPending = {};
    if (!find(device, &d) || !d.createImage) { *result = VK_ERROR_INITIALIZATION_FAILED; return true; }
    if (pending.device != device || pending.data.width != info->extent.width || pending.data.height != info->extent.height) {
        ALOGE("vkCreateImage %ux%u for an AHardwareBuffer: no vkGetAndroidHardwareBufferPropertiesANDROID of a %ux%u buffer before it on this thread",
              info->extent.width, info->extent.height, pending.data.width, pending.data.height);
        *result = VK_ERROR_INVALID_EXTERNAL_HANDLE;
        return true;
    }
    const gralloc::HandleData& data = pending.data;
    VkSubresourceLayout layouts[gralloc::kMaxPlanes] = {};
    for (uint32_t i = 0; i < data.numPlanes; i++) { layouts[i].offset = data.offset[i]; layouts[i].rowPitch = data.stride[i]; }
    Unlink chain(info->pNext, {VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO, VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID});
    VkImageDrmFormatModifierExplicitCreateInfoEXT drm = {VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT, chain.head,
                                                         data.modifier == kModInvalid ? 0 : data.modifier, data.numPlanes, layouts};
    VkExternalMemoryImageCreateInfo dmaBuf = {VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO, &drm, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkImageCreateInfo hostInfo = *info;
    hostInfo.pNext = &dmaBuf;
    hostInfo.tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT;
    *result = d.createImage(device, &hostInfo, allocator, image);
    if (*result != VK_SUCCESS)
        ALOGE("vkCreateImage %ux%u format %d for an AHardwareBuffer (fourcc 0x%x modifier 0x%llx usage 0x%x): %d", info->extent.width,
              info->extent.height, info->format, data.fourcc, static_cast<unsigned long long>(data.modifier), info->usage, *result);
    return true;
}

}  // namespace apklayer::vulkan::hardware_buffer
