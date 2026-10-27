// apklayer patch: original Android Vulkan loader -> host Vulkan loader.
// Keep Android WSI in AOSP. Host dispatchable handles retain their host vtables;
// scripts/patch-vulkan.sh gives Android's loader separate private dispatch data.
#define LOG_TAG "apklayer.vulkan"
#include <hardware/hwvulkan.h>
#include <vulkan/vk_android_native_buffer.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <log/log.h>
#include <sys/types.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <vector>

#include "gralloc_handle.h"

namespace {
void* hostLibrary() {
    static void* library = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    return library;
}
template<class T> T host(const char* name) {
    return reinterpret_cast<T>(hostLibrary() ? dlsym(hostLibrary(), name) : nullptr);
}


// ---- VK_ANDROID_native_buffer over the host driver (the logic of Mesa's generic Android runtime, vk_android.c) ----
// The Android loader's swapchain images are gralloc buffers dequeued from an ANativeWindow. Each one is imported into the host driver as a
// dma-buf VkImage (explicit DRM modifier layout from our gralloc handle); acquire imports the window's fence into a semaphore/fence, release
// exports a sync fd that signals when the queue's work is done. Advertised only when the host device can import dma-bufs and share sync fds.
constexpr uint64_t kProducerGpuRenderTarget = 1ull << 9;  // GRALLOC1_PRODUCER_USAGE_GPU_RENDER_TARGET
constexpr uint64_t kConsumerGpuTexture = 1ull << 8;       // GRALLOC1_CONSUMER_USAGE_GPU_TEXTURE
constexpr int kGralloc0HwRender = 0x200, kGralloc0HwTexture = 0x100;

const char* const kNativeBufferNeeds[] = {
    VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME, VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME, VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME,
    VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME,
};
const char* const kNativeBufferOptional[] = {  // not core before Vulkan 1.2 on the host device: named when it has them
    VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME, VK_KHR_IMAGE_FORMAT_LIST_EXTENSION_NAME,
    VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME,  // Venus has semaphore fds but no fence fds: acquire then waits the fd itself (below)
};

std::mutex gLock;
struct NativeImage {
    VkDevice device;
    VkDeviceMemory memory;  // the imported dma-buf, VK_NULL_HANDLE until the import
    int fd;                 // our dup of the dma-buf while the import is pending, -1 after
};
std::map<VkImage, NativeImage> gImages;  // native-buffer images
std::map<VkQueue, VkDevice> gQueueDevice;
std::map<VkQueue, VkSemaphore> gReleaseSemaphore;                 // one exportable semaphore per queue
std::map<VkDevice, bool> gNativeBuffer;                           // devices created with VK_ANDROID_native_buffer

template<class T> T hostDevice(VkDevice device, const char* name) {
    // the host loader's own entry point when it exports one (it dispatches on the handle), the driver's pointer otherwise
    if (T exported = host<T>(name)) return exported;
    auto proc = host<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr");
    return proc ? reinterpret_cast<T>(proc(device, name)) : nullptr;
}

bool hasExtension(VkPhysicalDevice physical, const char* name) {
    auto fn = host<PFN_vkEnumerateDeviceExtensionProperties>("vkEnumerateDeviceExtensionProperties");
    uint32_t count = 0;
    if (!fn || fn(physical, nullptr, &count, nullptr) != VK_SUCCESS) return false;
    std::vector<VkExtensionProperties> properties(count);
    if (fn(physical, nullptr, &count, properties.data()) != VK_SUCCESS) return false;
    for (const auto& p : properties) if (!strcmp(p.extensionName, name)) return true;
    return false;
}

// OPT-IN (APKLAYER_VULKAN_PRESENT=1): on the UTM VM's Venus GPU (virglrenderer on macOS) a virgl dma-buf imports and binds but Venus aborts on the first
// command that uses the image, even in a standalone C program (docs/PLAN.md P7.5). Advertised by default, Vulkan games would try the swapchain and die
// instead of falling back to GL. Hosts whose driver can use imported dma-bufs (native Linux GPUs) can enable it.
bool nativeBufferSupported(VkPhysicalDevice physical) {
    if (!getenv("APKLAYER_VULKAN_PRESENT")) return false;
    for (const char* name : kNativeBufferNeeds) if (!hasExtension(physical, name)) return false;
    return true;
}

VkResult grallocUsage(VkImageUsageFlags imageUsage, int* usage0) {
    const VkImageUsageFlags render = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    const VkImageUsageFlags texture = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT |
                                      VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
    if (imageUsage & ~(render | texture)) return VK_ERROR_FORMAT_NOT_SUPPORTED;
    int usage = 0;
    if (imageUsage & render) usage |= kGralloc0HwRender;
    if (imageUsage & texture) usage |= kGralloc0HwTexture;
    if (!usage) return VK_ERROR_FORMAT_NOT_SUPPORTED;
    *usage0 = usage;
    return VK_SUCCESS;
}

VKAPI_ATTR VkResult VKAPI_CALL getSwapchainGrallocUsageANDROID(VkDevice, VkFormat, VkImageUsageFlags imageUsage, int* usage) {
    return grallocUsage(imageUsage, usage);
}

VKAPI_ATTR VkResult VKAPI_CALL getSwapchainGrallocUsage2ANDROID(VkDevice, VkFormat, VkImageUsageFlags imageUsage, VkSwapchainImageUsageFlagsANDROID,
                                                                uint64_t* consumer, uint64_t* producer) {
    int usage0 = 0;
    VkResult result = grallocUsage(imageUsage, &usage0);
    if (result != VK_SUCCESS) return result;
    *consumer = *producer = 0;
    if (usage0 & kGralloc0HwRender) *producer |= kProducerGpuRenderTarget;
    if (usage0 & kGralloc0HwTexture) *consumer |= kConsumerGpuTexture;
    return VK_SUCCESS;
}

// The fence fd is ours to close in every case (vkAcquireImageANDROID's contract); the Vulkan import functions take it only on success.
VkResult importPending(VkDevice device, VkImage image);

VKAPI_ATTR VkResult VKAPI_CALL acquireImageANDROID(VkDevice device, VkImage image, int nativeFenceFd, VkSemaphore semaphore, VkFence fence) {
    VkResult imported = importPending(device, image);
    if (nativeFenceFd < 0 && (semaphore != VK_NULL_HANDLE || fence != VK_NULL_HANDLE)) {
        // nothing to wait for: signal the app's semaphore and fence with a real (empty) submission rather than a sync-fd import of -1,
        // which leaves Venus's semaphore unsignaled
        auto submit = hostDevice<PFN_vkQueueSubmit>(device, "vkQueueSubmit");
        VkQueue queue = VK_NULL_HANDLE;
        {
            std::lock_guard<std::mutex> lock(gLock);
            for (const auto& q : gQueueDevice) if (q.second == device) { queue = q.first; break; }
        }
        if (!submit || !queue) return VK_ERROR_INITIALIZATION_FAILED;
        VkSubmitInfo signal = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
        signal.signalSemaphoreCount = semaphore != VK_NULL_HANDLE ? 1 : 0;
        signal.pSignalSemaphores = &semaphore;
        return submit(queue, 1, &signal, fence);
    }
    if (imported != VK_SUCCESS) { if (nativeFenceFd >= 0) close(nativeFenceFd); return imported; }
    auto importSemaphore = hostDevice<PFN_vkImportSemaphoreFdKHR>(device, "vkImportSemaphoreFdKHR");
    auto importFence = hostDevice<PFN_vkImportFenceFdKHR>(device, "vkImportFenceFdKHR");
    if (!importSemaphore) { if (nativeFenceFd >= 0) close(nativeFenceFd); return VK_ERROR_INITIALIZATION_FAILED; }
    if (fence != VK_NULL_HANDLE && !importFence) {
        // no fence fds on this host driver: wait for the window's fence here, then signal the app's fence with an empty submit
        auto submit = hostDevice<PFN_vkQueueSubmit>(device, "vkQueueSubmit");
        VkQueue queue = VK_NULL_HANDLE;
        {
            std::lock_guard<std::mutex> lock(gLock);
            for (const auto& q : gQueueDevice) if (q.second == device) { queue = q.first; break; }
        }
        if (!submit || !queue) { if (nativeFenceFd >= 0) close(nativeFenceFd); return VK_ERROR_INITIALIZATION_FAILED; }
        if (nativeFenceFd >= 0) {
            struct pollfd wait = {nativeFenceFd, POLLIN, 0};
            while (poll(&wait, 1, -1) < 0 && errno == EINTR) {}
        }
        VkSubmitInfo signal = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
        VkResult result = submit(queue, 1, &signal, fence);
        if (result != VK_SUCCESS) { if (nativeFenceFd >= 0) close(nativeFenceFd); return result; }
        fence = VK_NULL_HANDLE;  // done: only the semaphore (if any) takes the fd now
    }
    int semaphoreFd = -1, fenceFd = -1;
    if (nativeFenceFd >= 0) {
        if (semaphore != VK_NULL_HANDLE && fence != VK_NULL_HANDLE) {
            semaphoreFd = nativeFenceFd;
            fenceFd = dup(nativeFenceFd);
            if (fenceFd < 0) { close(nativeFenceFd); return errno == EMFILE ? VK_ERROR_TOO_MANY_OBJECTS : VK_ERROR_OUT_OF_HOST_MEMORY; }
        } else if (semaphore != VK_NULL_HANDLE) semaphoreFd = nativeFenceFd;
        else if (fence != VK_NULL_HANDLE) fenceFd = nativeFenceFd;
        else close(nativeFenceFd);
    }
    VkResult result = VK_SUCCESS;
    if (semaphore != VK_NULL_HANDLE) {
        VkImportSemaphoreFdInfoKHR info = {VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_FD_INFO_KHR, nullptr, semaphore, VK_SEMAPHORE_IMPORT_TEMPORARY_BIT,
                                           VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT, semaphoreFd};
        result = importSemaphore(device, &info);
        if (result == VK_SUCCESS) semaphoreFd = -1;
    }
    if (result == VK_SUCCESS && fence != VK_NULL_HANDLE) {
        VkImportFenceFdInfoKHR info = {VK_STRUCTURE_TYPE_IMPORT_FENCE_FD_INFO_KHR, nullptr, fence, VK_FENCE_IMPORT_TEMPORARY_BIT,
                                       VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT, fenceFd};
        result = importFence(device, &info);
        if (result == VK_SUCCESS) fenceFd = -1;
    }
    if (semaphoreFd >= 0) close(semaphoreFd);
    if (fenceFd >= 0) close(fenceFd);
    return result;
}

VKAPI_ATTR VkResult VKAPI_CALL queueSignalReleaseImageANDROID(VkQueue queue, uint32_t waitCount, const VkSemaphore* waits, VkImage, int* fenceFd) {
    if (waitCount == 0) { *fenceFd = -1; return VK_SUCCESS; }
    VkDevice device;
    VkSemaphore signal;
    {
        std::lock_guard<std::mutex> lock(gLock);
        auto q = gQueueDevice.find(queue);
        if (q == gQueueDevice.end()) return VK_ERROR_INITIALIZATION_FAILED;
        device = q->second;
        signal = gReleaseSemaphore.count(queue) ? gReleaseSemaphore[queue] : VK_NULL_HANDLE;
    }
    auto createSemaphore = hostDevice<PFN_vkCreateSemaphore>(device, "vkCreateSemaphore");
    auto submit = hostDevice<PFN_vkQueueSubmit>(device, "vkQueueSubmit");
    auto getFd = hostDevice<PFN_vkGetSemaphoreFdKHR>(device, "vkGetSemaphoreFdKHR");
    if (!createSemaphore || !submit || !getFd) return VK_ERROR_INITIALIZATION_FAILED;
    if (signal == VK_NULL_HANDLE) {
        VkExportSemaphoreCreateInfo exportInfo = {VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO, nullptr, VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
        VkSemaphoreCreateInfo info = {VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO, &exportInfo, 0};
        VkResult result = createSemaphore(device, &info, nullptr, &signal);
        if (result != VK_SUCCESS) return result;
        std::lock_guard<std::mutex> lock(gLock);
        gReleaseSemaphore[queue] = signal;
    }
    std::vector<VkPipelineStageFlags> stages(waitCount, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
    VkSubmitInfo batch = {VK_STRUCTURE_TYPE_SUBMIT_INFO, nullptr, waitCount, waits, stages.data(), 0, nullptr, 1, &signal};
    VkResult result = submit(queue, 1, &batch, VK_NULL_HANDLE);
    if (result != VK_SUCCESS) return result;
    VkSemaphoreGetFdInfoKHR get = {VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR, nullptr, signal, VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
    return getFd(device, &get, fenceFd);
}

// A native-buffer image: created on the host with the gralloc buffer's DRM layout, its dma-buf imported as dedicated memory.
VkResult createNativeBufferImage(VkDevice device, const VkImageCreateInfo* info, const VkAllocationCallbacks* allocator,
                                 const VkNativeBufferANDROID* buffer, VkImage* image) {
    using namespace apklayer::gralloc;
    HandleData data;
    if (!decode(buffer->handle, &data) || data.numPlanes < 1) return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    auto createImage = hostDevice<PFN_vkCreateImage>(device, "vkCreateImage");
    auto destroyImage = hostDevice<PFN_vkDestroyImage>(device, "vkDestroyImage");
    if (!createImage || !destroyImage) return VK_ERROR_INITIALIZATION_FAILED;

    VkSubresourceLayout layouts[kMaxPlanes] = {};
    for (uint32_t i = 0; i < data.numPlanes; i++) { layouts[i].offset = data.offset[i]; layouts[i].rowPitch = data.stride[i]; }
    VkImageDrmFormatModifierExplicitCreateInfoEXT drm = {VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT, nullptr,
                                                         data.modifier == 0x00ffffffffffffffull /* DRM_FORMAT_MOD_INVALID: the allocator did not know */ ? 0 : data.modifier,
                                                         data.numPlanes, layouts};
    VkExternalMemoryImageCreateInfo external = {VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO, &drm,
                                                VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    // the host driver does not know the loader's Android structs (VkNativeBufferANDROID, VkSwapchainImageCreateInfoANDROID: extension 11's
    // enum range): relink the chain without them for the call and restore every pNext after (Venus aborts on an unknown struct)
    VkImageCreateInfo host = *info;
    host.tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT;
    std::vector<std::pair<VkBaseInStructure*, const VkBaseInStructure*>> original;
    VkBaseInStructure* last = nullptr;
    const VkBaseInStructure* first = nullptr;
    for (auto* link = reinterpret_cast<const VkBaseInStructure*>(info->pNext); link; link = link->pNext) {
        auto* node = const_cast<VkBaseInStructure*>(link);
        original.emplace_back(node, node->pNext);
        if (link->sType >= 1000010000 && link->sType < 1000011000) continue;
        if (last) last->pNext = node; else first = node;
        last = node;
    }
    if (last) last->pNext = nullptr;
    drm.pNext = first;
    host.pNext = &external;
    VkResult result = createImage(device, &host, allocator, image);
    for (auto& entry : original) entry.first->pNext = entry.second;
    if (result != VK_SUCCESS) return result;

    // The dma-buf import waits for vkAcquireImageANDROID (importPending): Mesa's Venus driver aborts when a gralloc allocation (another buffer of the
    // swapchain, made by the allocator service) happens after an import. The loader allocates and creates all of a swapchain's images first.
    int fd = fcntl(buffer->handle->data[0], F_DUPFD_CLOEXEC, 3);
    if (fd < 0) { destroyImage(device, *image, allocator); return VK_ERROR_TOO_MANY_OBJECTS; }
    std::lock_guard<std::mutex> lock(gLock);
    gImages[*image] = {device, VK_NULL_HANDLE, fd};
    return VK_SUCCESS;
}

// The memory of a native-buffer image: the dma-buf imported as dedicated memory and bound.
VkResult importPending(VkDevice device, VkImage image) {
    int fd;
    {
        std::lock_guard<std::mutex> lock(gLock);
        auto it = gImages.find(image);
        if (it == gImages.end() || it->second.fd < 0) return VK_SUCCESS;  // not ours, or already imported
        fd = it->second.fd;
    }
    auto requirements = hostDevice<PFN_vkGetImageMemoryRequirements>(device, "vkGetImageMemoryRequirements");
    auto allocate = hostDevice<PFN_vkAllocateMemory>(device, "vkAllocateMemory");
    auto free = hostDevice<PFN_vkFreeMemory>(device, "vkFreeMemory");
    auto bind = hostDevice<PFN_vkBindImageMemory>(device, "vkBindImageMemory");
    if (!requirements || !allocate || !free || !bind) return VK_ERROR_INITIALIZATION_FAILED;
    VkMemoryRequirements need;
    requirements(device, image, &need);
    // vkGetMemoryFdPropertiesKHR is not asked: Venus aborts on a dma-buf it did not create, while importing it works. The memory type is the
    // image's lowest (the choice Mesa's own Android runtime makes).
    if (!need.memoryTypeBits) return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    VkMemoryDedicatedAllocateInfo dedicated = {VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO, nullptr, image, VK_NULL_HANDLE};
    int importFd = fcntl(fd, F_DUPFD_CLOEXEC, 3);  // a successful import takes the fd, and the image may be imported again after a failure
    if (importFd < 0) return VK_ERROR_TOO_MANY_OBJECTS;
    VkImportMemoryFdInfoKHR import = {VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR, &dedicated, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, importFd};
    off_t size = lseek(importFd, 0, SEEK_END);
    VkMemoryAllocateInfo alloc = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, &import, size > 0 ? static_cast<VkDeviceSize>(size) : need.size,
                                  static_cast<uint32_t>(__builtin_ctz(need.memoryTypeBits))};
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkResult result = allocate(device, &alloc, nullptr, &memory);
    if (result != VK_SUCCESS) { close(importFd); return result; }  // a failed import leaves the fd ours
    result = bind(device, image, memory, 0);
    if (result != VK_SUCCESS) { free(device, memory, nullptr); return result; }
    std::lock_guard<std::mutex> lock(gLock);
    auto it = gImages.find(image);
    if (it != gImages.end()) { close(it->second.fd); it->second.fd = -1; it->second.memory = memory; }
    return VK_SUCCESS;
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
    if (nativeBufferSupported(physical)) {
        VkExtensionProperties native = {};
        strcpy(native.extensionName, VK_ANDROID_NATIVE_BUFFER_EXTENSION_NAME);
        native.specVersion = 7;  // gralloc usage 1 and 2; vkAcquireImageANDROID and vkQueueSignalReleaseImageANDROID
        properties.push_back(native);
    }
    return copyExtensions(properties, count, out);
}


VKAPI_ATTR VkResult VKAPI_CALL createImage(VkDevice device, const VkImageCreateInfo* info, const VkAllocationCallbacks* allocator, VkImage* image) {
    for (auto* link = reinterpret_cast<const VkBaseInStructure*>(info->pNext); link; link = link->pNext)
        if (link->sType == VK_STRUCTURE_TYPE_NATIVE_BUFFER_ANDROID)
            return createNativeBufferImage(device, info, allocator, reinterpret_cast<const VkNativeBufferANDROID*>(link), image);
    auto fn = hostDevice<PFN_vkCreateImage>(device, "vkCreateImage");
    return fn ? fn(device, info, allocator, image) : VK_ERROR_INITIALIZATION_FAILED;
}

VKAPI_ATTR void VKAPI_CALL destroyImage(VkDevice device, VkImage image, const VkAllocationCallbacks* allocator) {
    VkDeviceMemory memory = VK_NULL_HANDLE;
    {
        std::lock_guard<std::mutex> lock(gLock);
        auto it = gImages.find(image);
        if (it != gImages.end()) {
            memory = it->second.memory;
            if (it->second.fd >= 0) close(it->second.fd);
            gImages.erase(it);
        }
    }
    if (auto fn = hostDevice<PFN_vkDestroyImage>(device, "vkDestroyImage")) fn(device, image, allocator);
    if (memory != VK_NULL_HANDLE)
        if (auto fn = hostDevice<PFN_vkFreeMemory>(device, "vkFreeMemory")) fn(device, memory, allocator);
}

VKAPI_ATTR void VKAPI_CALL getDeviceQueue(VkDevice device, uint32_t family, uint32_t index, VkQueue* queue) {
    if (auto fn = hostDevice<PFN_vkGetDeviceQueue>(device, "vkGetDeviceQueue")) fn(device, family, index, queue);
    std::lock_guard<std::mutex> lock(gLock);
    if (*queue) gQueueDevice[*queue] = device;
}

VKAPI_ATTR void VKAPI_CALL getDeviceQueue2(VkDevice device, const VkDeviceQueueInfo2* info, VkQueue* queue) {
    if (auto fn = hostDevice<PFN_vkGetDeviceQueue2>(device, "vkGetDeviceQueue2")) fn(device, info, queue);
    std::lock_guard<std::mutex> lock(gLock);
    if (*queue) gQueueDevice[*queue] = device;
}

VKAPI_ATTR void VKAPI_CALL destroyDevice(VkDevice device, const VkAllocationCallbacks* allocator) {
    auto destroySemaphore = hostDevice<PFN_vkDestroySemaphore>(device, "vkDestroySemaphore");
    auto destroy = hostDevice<PFN_vkDestroyDevice>(device, "vkDestroyDevice");
    std::vector<VkSemaphore> semaphores;
    {
        std::lock_guard<std::mutex> lock(gLock);
        for (auto it = gQueueDevice.begin(); it != gQueueDevice.end();) {
            if (it->second == device) {
                auto s = gReleaseSemaphore.find(it->first);
                if (s != gReleaseSemaphore.end()) { semaphores.push_back(s->second); gReleaseSemaphore.erase(s); }
                it = gQueueDevice.erase(it);
            } else ++it;
        }
        gNativeBuffer.erase(device);
    }
    if (destroySemaphore) for (VkSemaphore semaphore : semaphores) destroySemaphore(device, semaphore, allocator);
    if (destroy) destroy(device, allocator);
}

PFN_vkVoidFunction getDeviceProcAddr(VkDevice device, const char* name) {
    bool native;
    {
        std::lock_guard<std::mutex> lock(gLock);
        auto it = gNativeBuffer.find(device);
        native = it != gNativeBuffer.end() && it->second;
    }
    struct { const char* name; PFN_vkVoidFunction function; bool needsNative; } ours[] = {
        {"vkGetDeviceProcAddr", reinterpret_cast<PFN_vkVoidFunction>(getDeviceProcAddr), false},
        {"vkDestroyDevice", reinterpret_cast<PFN_vkVoidFunction>(destroyDevice), false},
        {"vkGetDeviceQueue", reinterpret_cast<PFN_vkVoidFunction>(getDeviceQueue), false},
        {"vkGetDeviceQueue2", reinterpret_cast<PFN_vkVoidFunction>(getDeviceQueue2), false},
        {"vkCreateImage", reinterpret_cast<PFN_vkVoidFunction>(createImage), false},
        {"vkDestroyImage", reinterpret_cast<PFN_vkVoidFunction>(destroyImage), false},
        {"vkGetSwapchainGrallocUsageANDROID", reinterpret_cast<PFN_vkVoidFunction>(getSwapchainGrallocUsageANDROID), true},
        {"vkGetSwapchainGrallocUsage2ANDROID", reinterpret_cast<PFN_vkVoidFunction>(getSwapchainGrallocUsage2ANDROID), true},
        {"vkAcquireImageANDROID", reinterpret_cast<PFN_vkVoidFunction>(acquireImageANDROID), true},
        {"vkQueueSignalReleaseImageANDROID", reinterpret_cast<PFN_vkVoidFunction>(queueSignalReleaseImageANDROID), true},
    };
    for (auto& o : ours)
        if (!strcmp(name, o.name)) return o.needsNative && !native ? nullptr : o.function;
    auto fn = host<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr");
    return fn ? fn(device, name) : nullptr;
}

// The loader asks for VK_ANDROID_native_buffer by name; the host driver gets the extensions that carry it instead.
VkResult createDevice(VkPhysicalDevice physical, const VkDeviceCreateInfo* info, const VkAllocationCallbacks* allocator, VkDevice* device) {
    auto fn = host<PFN_vkCreateDevice>("vkCreateDevice");
    if (!fn) return VK_ERROR_INCOMPATIBLE_DRIVER;
    std::vector<const char*> names;
    bool native = false;
    for (uint32_t i = 0; i < info->enabledExtensionCount; i++) {
        if (!strcmp(info->ppEnabledExtensionNames[i], VK_ANDROID_NATIVE_BUFFER_EXTENSION_NAME)) native = true;
        else names.push_back(info->ppEnabledExtensionNames[i]);
    }
    if (native) {
        auto add = [&](const char* name) {
            if (std::none_of(names.begin(), names.end(), [&](const char* n) { return !strcmp(n, name); })) names.push_back(name);
        };
        for (const char* name : kNativeBufferNeeds) add(name);
        for (const char* name : kNativeBufferOptional) if (hasExtension(physical, name)) add(name);
    }
    VkDeviceCreateInfo host = *info;
    host.enabledExtensionCount = names.size();
    host.ppEnabledExtensionNames = names.data();
    VkResult result = fn(physical, &host, allocator, device);
    if (result == VK_SUCCESS) {
        std::lock_guard<std::mutex> lock(gLock);
        gNativeBuffer[*device] = native;
    }
    return result;
}

VkResult createInstance(const VkInstanceCreateInfo* info, const VkAllocationCallbacks* allocator,
                        VkInstance* instance) {
    auto fn = host<PFN_vkCreateInstance>("vkCreateInstance");
    if (!fn) return VK_ERROR_INCOMPATIBLE_DRIVER;
    // the dma-buf and sync-fd queries of the native-buffer path are core only from Vulkan 1.1
    VkApplicationInfo app = info->pApplicationInfo ? *info->pApplicationInfo : VkApplicationInfo{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    if (app.apiVersion < VK_API_VERSION_1_1) app.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo host = *info;
    host.pApplicationInfo = &app;
    return fn(&host, allocator, instance);
}

PFN_vkVoidFunction getInstanceProcAddr(VkInstance instance, const char* name) {
    if (!strcmp(name, "vkEnumerateDeviceExtensionProperties"))
        return reinterpret_cast<PFN_vkVoidFunction>(enumerateDeviceExtensions);
    if (!strcmp(name, "vkCreateDevice")) return reinterpret_cast<PFN_vkVoidFunction>(createDevice);
    if (!strcmp(name, "vkGetDeviceProcAddr")) return reinterpret_cast<PFN_vkVoidFunction>(getDeviceProcAddr);
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
    ALOGI("using the host Vulkan loader; Android presentation through VK_ANDROID_native_buffer when the host device can import dma-bufs");
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
