// apklayer patch: original Android Vulkan loader -> host Vulkan loader.
// Keep Android WSI in AOSP. Host dispatchable handles retain their host vtables;
// patches/aosp/frameworks/native/0003-vulkan-host-dispatch.patch gives Android's loader separate private dispatch data.
#define LOG_TAG "apklayer.vulkan"
#include "../../HostEnvironment.h"
#include <hardware/hwvulkan.h>
#include <vulkan/vk_android_native_buffer.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <log/log.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <algorithm>
#include <array>
#include <cstdlib>
#include <cstring>
#include <functional>
#include <map>
#include <mutex>
#include <string>
#include <vector>

#include "DeviceSelect.h"
#include "HardwareBuffer.h"
#include "TextureEmulation.h"
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
    int handleFd = -1;      // the gralloc handle's own fd number (Venus: replaced by the Venus-allocated dma-buf)
};
std::map<VkImage, NativeImage> gImages;  // native-buffer images
std::map<VkQueue, VkDevice> gQueueDevice;
std::map<VkQueue, VkSemaphore> gReleaseSemaphore;                 // one exportable semaphore per queue
struct NativeBufferDispatch {
    PFN_vkQueueSubmit submit = nullptr;
    PFN_vkImportSemaphoreFdKHR importSemaphore = nullptr;
    PFN_vkImportFenceFdKHR importFence = nullptr;
    PFN_vkCreateSemaphore createSemaphore = nullptr;
    PFN_vkGetSemaphoreFdKHR exportSemaphore = nullptr;
    VkQueue acquireQueue = VK_NULL_HANDLE;  // first queue in gQueueDevice's order, for the acquire fallback
    bool importSignaledSyncFd = false;
    uint32_t hostVisibleTypes = 0;  // Venus: virgl's EGL reads only memory made from a host-visible type (a device-local-only one fails with GL_OUT_OF_MEMORY)
    bool deferImport = false;  // Venus: the window buffer's dma-buf is imported at the first acquire (createNativeBufferImage)
};
std::map<VkDevice, NativeBufferDispatch> gNativeBuffer;  // devices created with VK_ANDROID_native_buffer

template<class T> T hostDevice(VkDevice device, const char* name) {
    // the host loader's own entry point when it exports one (it dispatches on the handle), the driver's pointer otherwise
    if (T exported = host<T>(name)) return exported;
    auto proc = host<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr");
    return proc ? reinterpret_cast<T>(proc(device, name)) : nullptr;
}

// apklayer patch: the sync-fd extension functions from the driver itself. libhybris's libvulkan.so.1 (a Halium phone) exports a stub for them that
// jumps to null; the loader's vkGetDeviceProcAddr answers the vendor driver's function (null while the extension is not enabled on the device).
template<class T> T hostDeviceFd(VkDevice device, const char* name) {
    auto proc = host<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr");
    if (auto fn = proc ? reinterpret_cast<T>(proc(device, name)) : nullptr) return fn;
    return hostDevice<T>(device, name);
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

// apklayer: Mesa's Venus (the UTM VM's virtual GPU): its own driver ID
bool isVenus(VkPhysicalDevice physical) {
    auto properties = host<PFN_vkGetPhysicalDeviceProperties2>("vkGetPhysicalDeviceProperties2");
    if (!properties) return false;
    VkPhysicalDeviceDriverProperties driver = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES};
    VkPhysicalDeviceProperties2 all = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2, &driver};
    properties(physical, &all);
    return driver.driverID == VK_DRIVER_ID_MESA_VENUS;
}

bool supportsSyncFdImport(VkPhysicalDevice physical) {
    auto semaphoreProperties = host<PFN_vkGetPhysicalDeviceExternalSemaphoreProperties>("vkGetPhysicalDeviceExternalSemaphoreProperties");
    auto fenceProperties = host<PFN_vkGetPhysicalDeviceExternalFenceProperties>("vkGetPhysicalDeviceExternalFenceProperties");
    if (!semaphoreProperties || !fenceProperties) return false;
    VkPhysicalDeviceExternalSemaphoreInfo semaphoreInfo = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_SEMAPHORE_INFO, nullptr,
                                                           VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
    VkExternalSemaphoreProperties semaphore = {VK_STRUCTURE_TYPE_EXTERNAL_SEMAPHORE_PROPERTIES};
    semaphoreProperties(physical, &semaphoreInfo, &semaphore);
    VkPhysicalDeviceExternalFenceInfo fenceInfo = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_FENCE_INFO, nullptr,
                                                  VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT};
    VkExternalFenceProperties fence = {VK_STRUCTURE_TYPE_EXTERNAL_FENCE_PROPERTIES};
    fenceProperties(physical, &fenceInfo, &fence);
    return (semaphore.externalSemaphoreFeatures & VK_EXTERNAL_SEMAPHORE_FEATURE_IMPORTABLE_BIT) &&
           (fence.externalFenceFeatures & VK_EXTERNAL_FENCE_FEATURE_IMPORTABLE_BIT);
}

// APKLAYER_VULKAN_PRESENT=1 (the CLI sets it where the host has a Vulkan GPU). Venus (the UTM VM) aborts on the first command that uses a dma-buf made by
// another driver (virgl: tests/vm/venus-export-probe.c); its swapchain images are allocated by Venus itself (allocateExported). Others import the gralloc's.
bool nativeBufferSupported(VkPhysicalDevice physical) {
    if (!apklayer::hostGetenv("APKLAYER_VULKAN_PRESENT")) return false;
    for (const char* name : kNativeBufferNeeds) if (!hasExtension(physical, name)) return false;
    return true;
}

// VK_ANDROID_external_memory_android_hardware_buffer (HardwareBuffer.cpp): the same dma-buf import, without presentation. Not on Venus
// (it aborts on a dma-buf another process allocated, see createNativeBufferImage).
const char* const kHardwareBufferNeeds[] = {
    VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME, VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME, VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME,
    VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME,  // the app's ownership transfers to and from the buffer's other users
};
bool hardwareBufferSupported(VkPhysicalDevice physical) {
    for (const char* name : kHardwareBufferNeeds) if (!hasExtension(physical, name)) return false;
    return !isVenus(physical);
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
VkResult importPending(VkDevice device, VkImage image, NativeBufferDispatch* dispatch);

VKAPI_ATTR VkResult VKAPI_CALL acquireImageANDROID(VkDevice device, VkImage image, int nativeFenceFd, VkSemaphore semaphore, VkFence fence) {
    NativeBufferDispatch dispatch;
    VkResult imported = importPending(device, image, &dispatch);
    if (imported != VK_SUCCESS) { if (nativeFenceFd >= 0) close(nativeFenceFd); return imported; }
    if (nativeFenceFd < 0 && !dispatch.importSignaledSyncFd && (semaphore != VK_NULL_HANDLE || fence != VK_NULL_HANDLE)) {
        // A sync-fd import of -1 signals acquisition without submitting to an app-owned queue.
        // Keep an empty submission for drivers such as Venus, where that import leaves the semaphore unsignaled.
        if (!dispatch.submit || !dispatch.acquireQueue) return VK_ERROR_INITIALIZATION_FAILED;
        VkSubmitInfo signal = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
        signal.signalSemaphoreCount = semaphore != VK_NULL_HANDLE ? 1 : 0;
        signal.pSignalSemaphores = &semaphore;
        return dispatch.submit(dispatch.acquireQueue, 1, &signal, fence);
    }
    if (!dispatch.importSemaphore) { if (nativeFenceFd >= 0) close(nativeFenceFd); return VK_ERROR_INITIALIZATION_FAILED; }
    if (fence != VK_NULL_HANDLE && !dispatch.importFence) {
        // no fence fds on this host driver: wait for the window's fence here, then signal the app's fence with an empty submit
        if (!dispatch.submit || !dispatch.acquireQueue) { if (nativeFenceFd >= 0) close(nativeFenceFd); return VK_ERROR_INITIALIZATION_FAILED; }
        if (nativeFenceFd >= 0) {
            struct pollfd wait = {nativeFenceFd, POLLIN, 0};
            while (poll(&wait, 1, -1) < 0 && errno == EINTR) {}
        }
        VkSubmitInfo signal = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
        VkResult result = dispatch.submit(dispatch.acquireQueue, 1, &signal, fence);
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
        result = dispatch.importSemaphore(device, &info);
        if (result == VK_SUCCESS) semaphoreFd = -1;
    }
    if (result == VK_SUCCESS && fence != VK_NULL_HANDLE) {
        VkImportFenceFdInfoKHR info = {VK_STRUCTURE_TYPE_IMPORT_FENCE_FD_INFO_KHR, nullptr, fence, VK_FENCE_IMPORT_TEMPORARY_BIT,
                                       VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT, fenceFd};
        result = dispatch.importFence(device, &info);
        if (result == VK_SUCCESS) fenceFd = -1;
    }
    if (semaphoreFd >= 0) close(semaphoreFd);
    if (fenceFd >= 0) close(fenceFd);
    return result;
}

VKAPI_ATTR VkResult VKAPI_CALL queueSignalReleaseImageANDROID(VkQueue queue, uint32_t waitCount, const VkSemaphore* waits, VkImage, int* fenceFd) {
    if (waitCount == 0) { *fenceFd = -1; return VK_SUCCESS; }
    VkDevice device;
    NativeBufferDispatch dispatch;
    VkSemaphore signal = VK_NULL_HANDLE;
    {
        std::lock_guard<std::mutex> lock(gLock);
        auto q = gQueueDevice.find(queue);
        if (q == gQueueDevice.end()) return VK_ERROR_INITIALIZATION_FAILED;
        device = q->second;
        auto native = gNativeBuffer.find(device);
        if (native == gNativeBuffer.end()) return VK_ERROR_INITIALIZATION_FAILED;
        dispatch = native->second;
        auto s = gReleaseSemaphore.find(queue);
        if (s != gReleaseSemaphore.end()) signal = s->second;
    }
    if (!dispatch.createSemaphore || !dispatch.submit || !dispatch.exportSemaphore) return VK_ERROR_INITIALIZATION_FAILED;
    if (signal == VK_NULL_HANDLE) {
        VkExportSemaphoreCreateInfo exportInfo = {VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO, nullptr, VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
        VkSemaphoreCreateInfo info = {VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO, &exportInfo, 0};
        VkResult result = dispatch.createSemaphore(device, &info, nullptr, &signal);
        if (result != VK_SUCCESS) return result;
        std::lock_guard<std::mutex> lock(gLock);
        gReleaseSemaphore[queue] = signal;
    }
    constexpr VkPipelineStageFlags kStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    static constexpr std::array<VkPipelineStageFlags, 4> kWaitStages = {kStage, kStage, kStage, kStage};
    const VkPipelineStageFlags* stages = kWaitStages.data();
    std::vector<VkPipelineStageFlags> extraStages;
    if (waitCount > kWaitStages.size()) {
        extraStages.assign(waitCount, kStage);
        stages = extraStages.data();
    }
    VkSubmitInfo batch = {VK_STRUCTURE_TYPE_SUBMIT_INFO, nullptr, waitCount, waits, stages, 0, nullptr, 1, &signal};
    VkResult result = dispatch.submit(queue, 1, &batch, VK_NULL_HANDLE);
    if (result != VK_SUCCESS) return result;
    VkSemaphoreGetFdInfoKHR get = {VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR, nullptr, signal, VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
    return dispatch.exportSemaphore(device, &get, fenceFd);
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

    // On Venus the memory waits for vkAcquireImageANDROID (importPending): Mesa's Venus driver aborts when a gralloc allocation (another buffer
    // of the swapchain, made by the allocator service) happens after an import. The loader allocates and creates all of a swapchain's images first. Every other driver gets the memory bound here, as Vulkan requires before the app's first view of the image: NVIDIA bakes a view's
    // address at creation, and a view made before the bind (Unity: its swapchain views and framebuffers right after vkCreateSwapchainKHR) renders
    // to address 0 (Arknights: Endfield, Xid 31 MMU fault at 0x4000 in the raster output, device lost; the validation layer's VUID-VkImageViewCreateInfo-image-01020).
    int fd = fcntl(buffer->handle->data[0], F_DUPFD_CLOEXEC, 3);
    if (fd < 0) { destroyImage(device, *image, allocator); return VK_ERROR_TOO_MANY_OBJECTS; }
    bool defer;
    {
        std::lock_guard<std::mutex> lock(gLock);
        gImages[*image] = {device, VK_NULL_HANDLE, fd, buffer->handle->data[0]};
        auto native = gNativeBuffer.find(device);
        defer = native != gNativeBuffer.end() && native->second.deferImport;
    }
    if (defer) return VK_SUCCESS;
    NativeBufferDispatch dispatch;
    result = importPending(device, *image, &dispatch);
    if (result == VK_SUCCESS) return VK_SUCCESS;
    {
        std::lock_guard<std::mutex> lock(gLock);
        auto it = gImages.find(*image);
        if (it != gImages.end()) { if (it->second.fd >= 0) close(it->second.fd); gImages.erase(it); }
    }
    destroyImage(device, *image, allocator);
    return result;
}

// Venus cannot use a dma-buf another driver (virgl, the allocator's GBM) made, but virgl's EGL imports one Venus exported (tests/vm/venus-export-probe.c).
// apklayer: the image's memory is allocated by Venus, exported as a dma-buf and put over the gralloc handle's fd (same number, so the buffer
// the compositor attaches at the first queueBuffer is this one); the image keeps the gralloc buffer's explicit DRM layout.
VkResult allocateExported(VkDevice device, VkImage image, int fd, int handleFd, uint32_t hostVisibleTypes) {
    auto requirements = hostDevice<PFN_vkGetImageMemoryRequirements>(device, "vkGetImageMemoryRequirements");
    auto allocate = hostDevice<PFN_vkAllocateMemory>(device, "vkAllocateMemory");
    auto free = hostDevice<PFN_vkFreeMemory>(device, "vkFreeMemory");
    auto bind = hostDevice<PFN_vkBindImageMemory>(device, "vkBindImageMemory");
    auto getFd = hostDeviceFd<PFN_vkGetMemoryFdKHR>(device, "vkGetMemoryFdKHR");
    if (!requirements || !allocate || !free || !bind || !getFd) return VK_ERROR_INITIALIZATION_FAILED;
    struct stat ours, theirs;  // the handle's fd must still be the buffer this image was made for
    if (fstat(fd, &ours) || fstat(handleFd, &theirs) || ours.st_ino != theirs.st_ino || ours.st_dev != theirs.st_dev) return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    VkMemoryRequirements need;
    requirements(device, image, &need);
    uint32_t types = need.memoryTypeBits & hostVisibleTypes ? need.memoryTypeBits & hostVisibleTypes : need.memoryTypeBits;
    if (!types) return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    VkExportMemoryAllocateInfo exportInfo = {VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO, nullptr, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkMemoryDedicatedAllocateInfo dedicated = {VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO, &exportInfo, image, VK_NULL_HANDLE};
    VkMemoryAllocateInfo alloc = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, &dedicated, need.size, static_cast<uint32_t>(__builtin_ctz(types))};
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkResult result = allocate(device, &alloc, nullptr, &memory);
    if (result != VK_SUCCESS) return result;
    VkMemoryGetFdInfoKHR get = {VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR, nullptr, memory, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    int exported = -1;
    result = getFd(device, &get, &exported);
    if (result == VK_SUCCESS) result = bind(device, image, memory, 0);
    if (result == VK_SUCCESS && dup3(exported, handleFd, O_CLOEXEC) < 0) result = VK_ERROR_TOO_MANY_OBJECTS;
    if (exported >= 0) close(exported);
    if (result != VK_SUCCESS) { free(device, memory, nullptr); return result; }
    std::lock_guard<std::mutex> lock(gLock);
    auto it = gImages.find(image);
    if (it != gImages.end()) { close(it->second.fd); it->second.fd = -1; it->second.memory = memory; }
    return VK_SUCCESS;
}

// The memory of a native-buffer image: the dma-buf imported as dedicated memory and bound.
VkResult importPending(VkDevice device, VkImage image, NativeBufferDispatch* dispatch) {
    int fd, handleFd;
    {
        std::lock_guard<std::mutex> lock(gLock);
        auto native = gNativeBuffer.find(device);
        if (native == gNativeBuffer.end()) return VK_ERROR_INITIALIZATION_FAILED;
        *dispatch = native->second;
        auto it = gImages.find(image);
        if (it == gImages.end() || it->second.fd < 0) return VK_SUCCESS;  // not ours, or already imported
        fd = it->second.fd;
        handleFd = it->second.handleFd;
    }
    if (dispatch->deferImport) return allocateExported(device, image, fd, handleFd, dispatch->hostVisibleTypes);
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
    if (hardwareBufferSupported(physical)) {
        VkExtensionProperties ahb = {};
        strcpy(ahb.extensionName, apklayer::vulkan::hardware_buffer::kName);
        ahb.specVersion = 5;
        properties.push_back(ahb);
    }
    return copyExtensions(properties, count, out);
}


VKAPI_ATTR VkResult VKAPI_CALL createImage(VkDevice device, const VkImageCreateInfo* info, const VkAllocationCallbacks* allocator, VkImage* image) {
    for (auto* link = reinterpret_cast<const VkBaseInStructure*>(info->pNext); link; link = link->pNext)
        if (link->sType == VK_STRUCTURE_TYPE_NATIVE_BUFFER_ANDROID)
            return createNativeBufferImage(device, info, allocator, reinterpret_cast<const VkNativeBufferANDROID*>(link), image);
    VkResult imported;
    if (apklayer::vulkan::hardware_buffer::createImage(device, info, allocator, image, &imported)) return imported;  // AHardwareBuffer memory
    VkResult emulated;
    if (apklayer::vulkan::textures::createImage(device, info, image, &emulated)) return emulated;  // ASTC/ETC2 the host lacks
    auto fn = hostDevice<PFN_vkCreateImage>(device, "vkCreateImage");
    return fn ? fn(device, info, allocator, image) : VK_ERROR_INITIALIZATION_FAILED;
}

VKAPI_ATTR void VKAPI_CALL destroyImage(VkDevice device, VkImage image, const VkAllocationCallbacks* allocator) {
    if (apklayer::vulkan::textures::destroyImage(device, image)) return;
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

void registerQueue(VkDevice device, VkQueue queue) {
    if (!queue) return;
    std::lock_guard<std::mutex> lock(gLock);
    gQueueDevice[queue] = device;
    auto native = gNativeBuffer.find(device);
    if (native == gNativeBuffer.end()) return;
    auto& acquireQueue = native->second.acquireQueue;
    if (!acquireQueue || std::less<VkQueue>{}(queue, acquireQueue)) acquireQueue = queue;
}

VKAPI_ATTR void VKAPI_CALL getDeviceQueue(VkDevice device, uint32_t family, uint32_t index, VkQueue* queue) {
    if (auto fn = hostDevice<PFN_vkGetDeviceQueue>(device, "vkGetDeviceQueue")) fn(device, family, index, queue);
    registerQueue(device, *queue);
}

VKAPI_ATTR void VKAPI_CALL getDeviceQueue2(VkDevice device, const VkDeviceQueueInfo2* info, VkQueue* queue) {
    if (auto fn = hostDevice<PFN_vkGetDeviceQueue2>(device, "vkGetDeviceQueue2")) fn(device, info, queue);
    registerQueue(device, *queue);
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
    apklayer::vulkan::textures::destroyed(device);
    apklayer::vulkan::hardware_buffer::destroyed(device);
    if (destroy) destroy(device, allocator);
}

PFN_vkVoidFunction getDeviceProcAddr(VkDevice device, const char* name) {
    bool native;
    {
        std::lock_guard<std::mutex> lock(gLock);
        native = gNativeBuffer.count(device) != 0;
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
    if (PFN_vkVoidFunction imported = apklayer::vulkan::hardware_buffer::deviceProc(device, name)) return imported;
    if (PFN_vkVoidFunction emulated = apklayer::vulkan::textures::deviceProc(device, name)) return emulated;
    auto fn = host<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr");
    return fn ? fn(device, name) : nullptr;
}

// The loader asks for VK_ANDROID_native_buffer by name; the host driver gets the extensions that carry it instead.
VkResult createDevice(VkPhysicalDevice physical, const VkDeviceCreateInfo* info, const VkAllocationCallbacks* allocator, VkDevice* device) {
    auto fn = host<PFN_vkCreateDevice>("vkCreateDevice");
    if (!fn) return VK_ERROR_INCOMPATIBLE_DRIVER;
    std::vector<const char*> names;
    bool native = false, hardwareBuffer = false;
    for (uint32_t i = 0; i < info->enabledExtensionCount; i++) {
        if (!strcmp(info->ppEnabledExtensionNames[i], VK_ANDROID_NATIVE_BUFFER_EXTENSION_NAME)) native = true;
        else if (!strcmp(info->ppEnabledExtensionNames[i], apklayer::vulkan::hardware_buffer::kName)) hardwareBuffer = true;
        else names.push_back(info->ppEnabledExtensionNames[i]);
    }
    auto add = [&](const char* name) {
        if (std::none_of(names.begin(), names.end(), [&](const char* n) { return !strcmp(n, name); })) names.push_back(name);
    };
    if (native) for (const char* name : kNativeBufferNeeds) add(name);
    if (hardwareBuffer) for (const char* name : kHardwareBufferNeeds) add(name);
    if (native || hardwareBuffer)
        for (const char* name : kNativeBufferOptional) if (hasExtension(physical, name)) add(name);
    VkDeviceCreateInfo host = *info;
    host.enabledExtensionCount = names.size();
    host.ppEnabledExtensionNames = names.data();
    apklayer::vulkan::textures::DeviceRequest textures;
    apklayer::vulkan::textures::prepare(physical, &host, &textures);  // emulated ASTC/ETC2 are not asked of the host
    VkResult result = fn(physical, &host, allocator, device);
    apklayer::vulkan::textures::created(physical, result == VK_SUCCESS ? *device : VK_NULL_HANDLE, &textures, result);
    if (result == VK_SUCCESS && hardwareBuffer) apklayer::vulkan::hardware_buffer::created(physical, *device);
    if (result == VK_SUCCESS && native) {
        // Resolve once while the device is being created. Extension functions may be driver-specific.
        NativeBufferDispatch dispatch = {
            hostDevice<PFN_vkQueueSubmit>(*device, "vkQueueSubmit"),
            hostDeviceFd<PFN_vkImportSemaphoreFdKHR>(*device, "vkImportSemaphoreFdKHR"),
            hostDeviceFd<PFN_vkImportFenceFdKHR>(*device, "vkImportFenceFdKHR"),
            hostDevice<PFN_vkCreateSemaphore>(*device, "vkCreateSemaphore"),
            hostDeviceFd<PFN_vkGetSemaphoreFdKHR>(*device, "vkGetSemaphoreFdKHR"),
        };
        // the host loader exports vkImportFenceFdKHR for every device, but its trampoline jumps to null on one without VK_KHR_external_fence_fd (Venus)
        if (!hasExtension(physical, VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME)) dispatch.importFence = nullptr;
        dispatch.importSignaledSyncFd = dispatch.importSemaphore && dispatch.importFence && supportsSyncFdImport(physical);
        dispatch.deferImport = isVenus(physical);
        if (auto memoryProperties = ::host<PFN_vkGetPhysicalDeviceMemoryProperties>("vkGetPhysicalDeviceMemoryProperties")) {
            VkPhysicalDeviceMemoryProperties types;
            memoryProperties(physical, &types);
            for (uint32_t i = 0; i < types.memoryTypeCount; i++)
                if (types.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) dispatch.hostVisibleTypes |= 1u << i;
        }
        std::lock_guard<std::mutex> lock(gLock);
        gNativeBuffer[*device] = dispatch;
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

VKAPI_ATTR VkResult VKAPI_CALL enumeratePhysicalDevices(VkInstance instance, uint32_t* count, VkPhysicalDevice* out) {
    return apklayer::vulkan::enumeratePhysicalDevices(host<PFN_vkGetInstanceProcAddr>("vkGetInstanceProcAddr"), instance, count, out);
}
VKAPI_ATTR VkResult VKAPI_CALL enumeratePhysicalDeviceGroups(VkInstance instance, uint32_t* count, VkPhysicalDeviceGroupProperties* out) {
    return apklayer::vulkan::enumeratePhysicalDeviceGroups(host<PFN_vkGetInstanceProcAddr>("vkGetInstanceProcAddr"), instance, count, out);
}

// The Android loader offers VK_EXT_swapchain_maintenance1 (present fences: ANGLE) when sync fds import into fences, and then calls vkImportFenceFdKHR, which it
// loads only for a device that lists VK_KHR_external_fence_fd. Venus reports the import but lacks the extension (a null call): report it as absent.
VKAPI_ATTR void VKAPI_CALL externalFenceProperties(VkPhysicalDevice physical, const VkPhysicalDeviceExternalFenceInfo* info, VkExternalFenceProperties* out) {
    if (auto fn = ::host<PFN_vkGetPhysicalDeviceExternalFenceProperties>("vkGetPhysicalDeviceExternalFenceProperties")) fn(physical, info, out);
    if (info->handleType == VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT && !hasExtension(physical, VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME))
        out->externalFenceFeatures = 0;
}

PFN_vkVoidFunction getInstanceProcAddr(VkInstance instance, const char* name) {
    // the instance's GPU only (DeviceSelect.cpp)
    if (!strcmp(name, "vkEnumeratePhysicalDevices")) return reinterpret_cast<PFN_vkVoidFunction>(enumeratePhysicalDevices);
    if (!strcmp(name, "vkEnumeratePhysicalDeviceGroups") || !strcmp(name, "vkEnumeratePhysicalDeviceGroupsKHR"))
        return reinterpret_cast<PFN_vkVoidFunction>(enumeratePhysicalDeviceGroups);
    if (!strcmp(name, "vkEnumerateDeviceExtensionProperties"))
        return reinterpret_cast<PFN_vkVoidFunction>(enumerateDeviceExtensions);
    if (!strcmp(name, "vkGetPhysicalDeviceExternalFenceProperties") || !strcmp(name, "vkGetPhysicalDeviceExternalFencePropertiesKHR"))
        return reinterpret_cast<PFN_vkVoidFunction>(externalFenceProperties);
    if (!strcmp(name, "vkCreateDevice")) return reinterpret_cast<PFN_vkVoidFunction>(createDevice);
    if (!strcmp(name, "vkGetDeviceProcAddr")) return reinterpret_cast<PFN_vkVoidFunction>(getDeviceProcAddr);
    auto fn = host<PFN_vkGetInstanceProcAddr>("vkGetInstanceProcAddr");
    PFN_vkVoidFunction emulated = apklayer::vulkan::textures::instanceProc(name);  // ASTC/ETC2 reported
    // the AHardwareBuffer handle type in image-format queries, in front of the emulation's (or the host's) entry point
    if (PFN_vkVoidFunction imported = apklayer::vulkan::hardware_buffer::instanceProc(name, emulated ? emulated : fn ? fn(instance, name) : nullptr))
        return imported;
    if (emulated) return emulated;
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
