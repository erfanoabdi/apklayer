// Real Android loader -> host Vulkan: enumeration, command dispatch, image
// clear/copy/readback and repeated teardown. No synthetic GPU or WSI fallback.
#define VK_USE_PLATFORM_ANDROID_KHR
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <android/hardware_buffer.h>
#include <cutils/native_handle.h>
#include <vulkan/vk_android_native_buffer.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vector>
#include <set>
#include <string>

#define CHECK(x) do { VkResult r = (x); if (r != VK_SUCCESS) { fprintf(stderr, "vktest: %s = %d line %d\n", #x, r, __LINE__); return 1; } } while (0)
#define REQUIRE(x) do { if (!(x)) { fprintf(stderr, "vktest: %s failed line %d\n", #x, __LINE__); return 1; } } while (0)
#define INSTANCE(name) auto name = reinterpret_cast<PFN_##name>(get(instance, #name)); REQUIRE(name)
#define DEVICE(name) auto name = reinterpret_cast<PFN_##name>(deviceProc(device, #name)); REQUIRE(name)

static uint32_t memoryType(const VkPhysicalDeviceMemoryProperties& properties, uint32_t bits, VkMemoryPropertyFlags flags) {
    for (uint32_t i = 0; i < properties.memoryTypeCount; i++)
        if ((bits & (1u << i)) && (properties.memoryTypes[i].propertyFlags & flags) == flags) return i;
    return UINT32_MAX;
}

static int run(void* library, unsigned cycle) {
    auto get = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(library, "vkGetInstanceProcAddr")); REQUIRE(get);
    auto create = reinterpret_cast<PFN_vkCreateInstance>(get(VK_NULL_HANDLE, "vkCreateInstance")); REQUIRE(create);
    auto enumerate = reinterpret_cast<PFN_vkEnumerateInstanceExtensionProperties>(get(VK_NULL_HANDLE, "vkEnumerateInstanceExtensionProperties")); REQUIRE(enumerate);
    uint32_t count = 0;
    CHECK(enumerate(nullptr, &count, nullptr));
    std::vector<VkExtensionProperties> instanceExtensions(count);
    CHECK(enumerate(nullptr, &count, instanceExtensions.data()));
    std::set<std::string> names;
    for (const auto& p : instanceExtensions) REQUIRE(names.insert(p.extensionName).second);
    bool androidSurface = false;
    for (const auto& p : instanceExtensions) androidSurface |= !strcmp(p.extensionName, "VK_KHR_android_surface");
    REQUIRE(androidSurface);
    const char* extensions[] = {"VK_KHR_surface", "VK_KHR_android_surface"};
    VkApplicationInfo application{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    application.pApplicationName = "apklayer-vktest"; application.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    info.pApplicationInfo = &application; info.enabledExtensionCount = 2; info.ppEnabledExtensionNames = extensions;
    VkInstance instance;
    CHECK(create(&info, nullptr, &instance));
    INSTANCE(vkDestroyInstance); INSTANCE(vkEnumeratePhysicalDevices); INSTANCE(vkGetPhysicalDeviceProperties);
    INSTANCE(vkGetPhysicalDeviceMemoryProperties); INSTANCE(vkGetPhysicalDeviceQueueFamilyProperties);
    INSTANCE(vkEnumerateDeviceExtensionProperties); INSTANCE(vkCreateDevice);
    INSTANCE(vkGetPhysicalDeviceExternalSemaphoreProperties);
    auto deviceProc = reinterpret_cast<PFN_vkGetDeviceProcAddr>(get(instance, "vkGetDeviceProcAddr")); REQUIRE(deviceProc);
    count = 0; CHECK(vkEnumeratePhysicalDevices(instance, &count, nullptr)); REQUIRE(count);
    std::vector<VkPhysicalDevice> physical(count);
    CHECK(vkEnumeratePhysicalDevices(instance, &count, physical.data()));
    VkPhysicalDevice gpu = physical[0];
    VkPhysicalDeviceProperties properties; vkGetPhysicalDeviceProperties(gpu, &properties);
    printf("vktest: cycle %u device=%s type=%u api=%u.%u.%u\n", cycle, properties.deviceName,
        properties.deviceType, VK_API_VERSION_MAJOR(properties.apiVersion), VK_API_VERSION_MINOR(properties.apiVersion), VK_API_VERSION_PATCH(properties.apiVersion)); fflush(stdout);
    count = 0; CHECK(vkEnumerateDeviceExtensionProperties(gpu, nullptr, &count, nullptr)); REQUIRE(count > 1);
    VkExtensionProperties one; uint32_t shortCount = 1;
    REQUIRE(vkEnumerateDeviceExtensionProperties(gpu, nullptr, &shortCount, &one) == VK_INCOMPLETE && shortCount == 1);
    std::vector<VkExtensionProperties> deviceExtensions(count);
    CHECK(vkEnumerateDeviceExtensionProperties(gpu, nullptr, &count, deviceExtensions.data()));
    names.clear();
    for (const auto& p : deviceExtensions) REQUIRE(names.insert(p.extensionName).second);
    bool swapchain = false, dma = false, modifier = false, semaphoreFd = false;
    for (const auto& p : deviceExtensions) {
        swapchain |= !strcmp(p.extensionName, "VK_KHR_swapchain");
        dma |= !strcmp(p.extensionName, "VK_EXT_external_memory_dma_buf");
        modifier |= !strcmp(p.extensionName, "VK_EXT_image_drm_format_modifier");
        semaphoreFd |= !strcmp(p.extensionName, "VK_KHR_external_semaphore_fd");
    }
    VkPhysicalDeviceExternalSemaphoreInfo semaphoreInfo{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_SEMAPHORE_INFO};
    semaphoreInfo.handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT;
    VkExternalSemaphoreProperties semaphoreProperties{VK_STRUCTURE_TYPE_EXTERNAL_SEMAPHORE_PROPERTIES};
    vkGetPhysicalDeviceExternalSemaphoreProperties(gpu, &semaphoreInfo, &semaphoreProperties);
    printf("vktest: dma_buf=%d drm_modifier=%d semaphore_fd=%d sync_fd_features=0x%x android_swapchain=%d\n",
        dma, modifier, semaphoreFd, semaphoreProperties.externalSemaphoreFeatures, swapchain); fflush(stdout);
    // Android's swapchain exists when asked for (APKLAYER_VULKAN_PRESENT) and the host device can import dma-bufs and share sync fds (Driver.cpp)
    REQUIRE(swapchain == (getenv("APKLAYER_VULKAN_PRESENT") && dma && modifier && semaphoreFd));
    count = 0; vkGetPhysicalDeviceQueueFamilyProperties(gpu, &count, nullptr);
    std::vector<VkQueueFamilyProperties> families(count); vkGetPhysicalDeviceQueueFamilyProperties(gpu, &count, families.data());
    uint32_t family = UINT32_MAX;
    for (uint32_t i = 0; i < count; i++) if (families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) { family = i; break; }
    REQUIRE(family != UINT32_MAX);
    float priority = 1;
    VkDeviceQueueCreateInfo queueInfo{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    queueInfo.queueFamilyIndex = family; queueInfo.queueCount = 1; queueInfo.pQueuePriorities = &priority;
    VkDeviceCreateInfo deviceInfo{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    deviceInfo.queueCreateInfoCount = 1; deviceInfo.pQueueCreateInfos = &queueInfo;
    const char* requested = "VK_KHR_swapchain";
    deviceInfo.enabledExtensionCount = 1; deviceInfo.ppEnabledExtensionNames = &requested;
    VkDevice device;
    if (!swapchain) REQUIRE(vkCreateDevice(gpu, &deviceInfo, nullptr, &device) == VK_ERROR_EXTENSION_NOT_PRESENT);
    deviceInfo.enabledExtensionCount = 0; deviceInfo.ppEnabledExtensionNames = nullptr;
    CHECK(vkCreateDevice(gpu, &deviceInfo, nullptr, &device));
    DEVICE(vkDestroyDevice); DEVICE(vkGetDeviceQueue); DEVICE(vkCreateBuffer); DEVICE(vkDestroyBuffer);
    DEVICE(vkGetBufferMemoryRequirements); DEVICE(vkAllocateMemory); DEVICE(vkFreeMemory); DEVICE(vkBindBufferMemory);
    DEVICE(vkCreateImage); DEVICE(vkDestroyImage); DEVICE(vkGetImageMemoryRequirements); DEVICE(vkBindImageMemory);
    DEVICE(vkMapMemory); DEVICE(vkUnmapMemory); DEVICE(vkCreateCommandPool); DEVICE(vkDestroyCommandPool);
    DEVICE(vkAllocateCommandBuffers); DEVICE(vkFreeCommandBuffers); DEVICE(vkBeginCommandBuffer); DEVICE(vkEndCommandBuffer);
    DEVICE(vkCmdPipelineBarrier); DEVICE(vkCmdClearColorImage); DEVICE(vkCmdCopyImageToBuffer);
    DEVICE(vkQueueSubmit); DEVICE(vkCreateFence); DEVICE(vkWaitForFences); DEVICE(vkDestroyFence);
    VkQueue queue; vkGetDeviceQueue(device, family, 0, &queue);
    VkPhysicalDeviceMemoryProperties memory; vkGetPhysicalDeviceMemoryProperties(gpu, &memory);
    VkBufferCreateInfo bufferInfo{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bufferInfo.size = 16*16*4; bufferInfo.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    VkBuffer buffer; CHECK(vkCreateBuffer(device, &bufferInfo, nullptr, &buffer));
    VkMemoryRequirements requirements; vkGetBufferMemoryRequirements(device, buffer, &requirements);
    VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocation.allocationSize = requirements.size;
    allocation.memoryTypeIndex = memoryType(memory, requirements.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    REQUIRE(allocation.memoryTypeIndex != UINT32_MAX);
    VkDeviceMemory bufferMemory; CHECK(vkAllocateMemory(device, &allocation, nullptr, &bufferMemory));
    CHECK(vkBindBufferMemory(device, buffer, bufferMemory, 0));
    VkImageCreateInfo imageInfo{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    imageInfo.imageType = VK_IMAGE_TYPE_2D; imageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    imageInfo.extent = {16,16,1}; imageInfo.mipLevels = 1; imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT; imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    VkImage image; CHECK(vkCreateImage(device, &imageInfo, nullptr, &image));
    vkGetImageMemoryRequirements(device, image, &requirements);
    allocation.allocationSize = requirements.size; allocation.memoryTypeIndex = memoryType(memory, requirements.memoryTypeBits, 0);
    REQUIRE(allocation.memoryTypeIndex != UINT32_MAX);
    VkDeviceMemory imageMemory; CHECK(vkAllocateMemory(device, &allocation, nullptr, &imageMemory));
    CHECK(vkBindImageMemory(device, image, imageMemory, 0));
    VkCommandPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO}; poolInfo.queueFamilyIndex = family;
    VkCommandPool pool; CHECK(vkCreateCommandPool(device, &poolInfo, nullptr, &pool));
    VkCommandBufferAllocateInfo commandInfo{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    commandInfo.commandPool = pool; commandInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY; commandInfo.commandBufferCount = 1;
    VkCommandBuffer command; CHECK(vkAllocateCommandBuffers(device, &commandInfo, &command));
    VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO}; CHECK(vkBeginCommandBuffer(command, &begin));
    // Resolve this command directly from Android libvulkan, exercising its own
    // dispatch side table as well as host PFNs from vkGetDeviceProcAddr.
    auto fill = reinterpret_cast<PFN_vkCmdFillBuffer>(dlsym(library, "vkCmdFillBuffer")); REQUIRE(fill);
    fill(command, buffer, 0, VK_WHOLE_SIZE, 0xdeadbeef);
    VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    barrier.srcQueueFamilyIndex = barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image; barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED; barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,0,nullptr,0,nullptr,1,&barrier);
    VkClearColorValue color{{1,0,0.5f,1}};
    vkCmdClearColorImage(command, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &color, 1, &barrier.subresourceRange);
    barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT; barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,nullptr,0,nullptr,1,&barrier);
    VkBufferMemoryBarrier bufferBarrier{VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER};
    bufferBarrier.srcAccessMask = bufferBarrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    bufferBarrier.srcQueueFamilyIndex = bufferBarrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    bufferBarrier.buffer = buffer; bufferBarrier.size = VK_WHOLE_SIZE;
    vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,nullptr,1,&bufferBarrier,0,nullptr);
    VkBufferImageCopy copy{}; copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT,0,0,1}; copy.imageExtent = {16,16,1};
    vkCmdCopyImageToBuffer(command,image,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,buffer,1,&copy);
    bufferBarrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT; bufferBarrier.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
    vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_HOST_BIT,0,0,nullptr,1,&bufferBarrier,0,nullptr);
    CHECK(vkEndCommandBuffer(command));
    VkFenceCreateInfo fenceInfo{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO}; VkFence fence;
    CHECK(vkCreateFence(device,&fenceInfo,nullptr,&fence));
    VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO}; submit.commandBufferCount = 1; submit.pCommandBuffers = &command;
    CHECK(vkQueueSubmit(queue,1,&submit,fence));
    CHECK(vkWaitForFences(device,1,&fence,VK_TRUE,2000000000));
    void* mapped; CHECK(vkMapMemory(device,bufferMemory,0,VK_WHOLE_SIZE,0,&mapped));
    auto* pixels = static_cast<unsigned char*>(mapped);
    for (unsigned i = 0; i < 16*16; i++) REQUIRE(pixels[i*4] == 255 && pixels[i*4+1] == 0 && pixels[i*4+2] >= 127 && pixels[i*4+2] <= 128 && pixels[i*4+3] == 255);
    vkUnmapMemory(device,bufferMemory);
    vkDestroyFence(device,fence,nullptr);
    if (cycle % 2) vkFreeCommandBuffers(device,pool,1,&command);
    vkDestroyCommandPool(device,pool,nullptr);
    vkDestroyImage(device,image,nullptr); vkFreeMemory(device,imageMemory,nullptr);
    vkDestroyBuffer(device,buffer,nullptr); vkFreeMemory(device,bufferMemory,nullptr);
    vkDestroyDevice(device,nullptr); vkDestroyInstance(instance,nullptr);
    printf("vktest: PASS cycle %u: 256 image pixels, Android/host dispatch and teardown\n", cycle); fflush(stdout);
    return 0;
}

extern "C" const native_handle_t* AHardwareBuffer_getNativeHandle(const AHardwareBuffer* buffer);  // VNDK, libnativewindow

// Import test without a JVM: gralloc buffers through the Android loader and the HAL's native-buffer image import, several in a row.
static int importBuffers() {
    void* library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL); REQUIRE(library);
    auto get = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(library, "vkGetInstanceProcAddr")); REQUIRE(get);
    auto create = reinterpret_cast<PFN_vkCreateInstance>(get(VK_NULL_HANDLE, "vkCreateInstance")); REQUIRE(create);
    const char* extensions[] = {"VK_KHR_surface", "VK_KHR_android_surface"};
    VkApplicationInfo application{VK_STRUCTURE_TYPE_APPLICATION_INFO}; application.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    info.pApplicationInfo = &application; info.enabledExtensionCount = 2; info.ppEnabledExtensionNames = extensions;
    VkInstance instance; CHECK(create(&info, nullptr, &instance));
    INSTANCE(vkEnumeratePhysicalDevices); INSTANCE(vkCreateDevice); INSTANCE(vkGetPhysicalDeviceQueueFamilyProperties);
    auto deviceProc = reinterpret_cast<PFN_vkGetDeviceProcAddr>(get(instance, "vkGetDeviceProcAddr")); REQUIRE(deviceProc);
    uint32_t count = 0; CHECK(vkEnumeratePhysicalDevices(instance, &count, nullptr)); REQUIRE(count);
    std::vector<VkPhysicalDevice> physical(count); CHECK(vkEnumeratePhysicalDevices(instance, &count, physical.data()));
    float priority = 1;
    VkDeviceQueueCreateInfo queueInfo{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO}; queueInfo.queueCount = 1; queueInfo.pQueuePriorities = &priority;
    const char* deviceExtensions[] = {"VK_KHR_swapchain"};
    VkDeviceCreateInfo deviceInfo{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    deviceInfo.queueCreateInfoCount = 1; deviceInfo.pQueueCreateInfos = &queueInfo; deviceInfo.enabledExtensionCount = 1; deviceInfo.ppEnabledExtensionNames = deviceExtensions;
    VkDevice device; CHECK(vkCreateDevice(physical[0], &deviceInfo, nullptr, &device));
    DEVICE(vkCreateImage);
    AHardwareBuffer* buffers[4] = {}; VkImage images[4] = {};
    const bool early = getenv("VKIMPORT_ALLOC_FIRST") != nullptr;  // allocate every buffer before the first import
    auto allocate = [&](int i) {
        AHardwareBuffer_Desc desc{720, 1224, 1, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM, AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE, 0, 0, 0};
        return AHardwareBuffer_allocate(&desc, &buffers[i]) == 0;
    };
    if (early) for (int i = 0; i < 4; i++) REQUIRE(allocate(i));
    for (int i = 0; i < 4; i++) {
        if (!early) REQUIRE(allocate(i));
        AHardwareBuffer* buffer = buffers[i];
        AHardwareBuffer_Desc desc;
        AHardwareBuffer_describe(buffer, &desc);
        VkNativeBufferANDROID native{VK_STRUCTURE_TYPE_NATIVE_BUFFER_ANDROID};
        native.handle = AHardwareBuffer_getNativeHandle(buffer); native.stride = desc.stride; native.format = 1;
        VkImageCreateInfo image{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO}; image.pNext = &native;
        image.imageType = VK_IMAGE_TYPE_2D; image.format = VK_FORMAT_R8G8B8A8_UNORM; image.extent = {720, 1224, 1}; image.mipLevels = 1; image.arrayLayers = 1;
        image.samples = VK_SAMPLE_COUNT_1_BIT; image.tiling = VK_IMAGE_TILING_OPTIMAL; image.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        VkImage handle; CHECK(vkCreateImage(device, &image, nullptr, &handle));
        images[i] = handle;
        printf("vkimport: image %d created (stride %u)\n", i, desc.stride); fflush(stdout);
    }
    // the memory import happens at acquire, once every buffer exists
    auto acquire = reinterpret_cast<PFN_vkAcquireImageANDROID>(deviceProc(device, "vkAcquireImageANDROID")); REQUIRE(acquire);
    for (int i = 0; i < 4; i++) {
        CHECK(acquire(device, images[i], -1, VK_NULL_HANDLE, VK_NULL_HANDLE));
        printf("vkimport: image %d acquired and imported\n", i); fflush(stdout);
    }
    puts("vkimport: PASS"); fflush(stdout);
    return 0;
}
extern "C" int main(int argc, char** argv) {
    if (argc > 1 && !strcmp(argv[1], "import")) return importBuffers();
    setvbuf(stdout, nullptr, _IONBF, 0);
    void* library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) { fprintf(stderr, "vktest: dlopen failed: %s\n", dlerror()); return 1; }
    for (unsigned cycle = 0; cycle < 6; cycle++) if (run(library,cycle)) return 1;
    dlclose(library);
    puts("vktest: PASS original Android Vulkan loader and host execution");
    return 0;
}

// Run exactly the same native regression through a real APK/HotSpot JNI call.
extern "C" JNIEXPORT jint JNICALL Java_hello_vulkan_Main_probe(JNIEnv*, jclass) {
    return main(0, nullptr);
}

// Presentation: an Android swapchain on a SurfaceView's window, solid frames through acquire/clear/present (VK_ANDROID_native_buffer).
struct ANativeWindow;
extern "C" JNIEXPORT jint JNICALL Java_hello_vulkan_Present_present(JNIEnv* env, jclass, jobject surface, jint frames) {
    void* library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL); REQUIRE(library);
    void* android = dlopen("libandroid.so", RTLD_NOW | RTLD_GLOBAL); REQUIRE(android);
    auto fromSurface = reinterpret_cast<ANativeWindow* (*)(JNIEnv*, jobject)>(dlsym(android, "ANativeWindow_fromSurface")); REQUIRE(fromSurface);
    auto release = reinterpret_cast<void (*)(ANativeWindow*)>(dlsym(android, "ANativeWindow_release")); REQUIRE(release);
    ANativeWindow* window = fromSurface(env, surface); REQUIRE(window);
    auto get = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(library, "vkGetInstanceProcAddr")); REQUIRE(get);
    auto create = reinterpret_cast<PFN_vkCreateInstance>(get(VK_NULL_HANDLE, "vkCreateInstance")); REQUIRE(create);
    const char* extensions[] = {"VK_KHR_surface", "VK_KHR_android_surface"};
    VkApplicationInfo application{VK_STRUCTURE_TYPE_APPLICATION_INFO}; application.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    info.pApplicationInfo = &application; info.enabledExtensionCount = 2; info.ppEnabledExtensionNames = extensions;
    VkInstance instance; CHECK(create(&info, nullptr, &instance));
    INSTANCE(vkEnumeratePhysicalDevices); INSTANCE(vkGetPhysicalDeviceProperties); INSTANCE(vkGetPhysicalDeviceQueueFamilyProperties);
    INSTANCE(vkCreateDevice); INSTANCE(vkCreateAndroidSurfaceKHR); INSTANCE(vkDestroySurfaceKHR); INSTANCE(vkDestroyInstance);
    INSTANCE(vkGetPhysicalDeviceSurfaceSupportKHR); INSTANCE(vkGetPhysicalDeviceSurfaceCapabilitiesKHR); INSTANCE(vkGetPhysicalDeviceSurfaceFormatsKHR);
    auto deviceProc = reinterpret_cast<PFN_vkGetDeviceProcAddr>(get(instance, "vkGetDeviceProcAddr")); REQUIRE(deviceProc);
    uint32_t count = 0; CHECK(vkEnumeratePhysicalDevices(instance, &count, nullptr)); REQUIRE(count);
    std::vector<VkPhysicalDevice> physical(count); CHECK(vkEnumeratePhysicalDevices(instance, &count, physical.data()));
    VkPhysicalDevice gpu = physical[0];
    VkPhysicalDeviceProperties properties; vkGetPhysicalDeviceProperties(gpu, &properties);
    VkAndroidSurfaceCreateInfoKHR surfaceInfo{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR}; surfaceInfo.window = window;
    VkSurfaceKHR vkSurface; CHECK(vkCreateAndroidSurfaceKHR(instance, &surfaceInfo, nullptr, &vkSurface));
    count = 0; vkGetPhysicalDeviceQueueFamilyProperties(gpu, &count, nullptr);
    std::vector<VkQueueFamilyProperties> families(count); vkGetPhysicalDeviceQueueFamilyProperties(gpu, &count, families.data());
    uint32_t family = UINT32_MAX;
    for (uint32_t i = 0; i < count; i++) {
        VkBool32 present = VK_FALSE; vkGetPhysicalDeviceSurfaceSupportKHR(gpu, i, vkSurface, &present);
        if ((families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) { family = i; break; }
    }
    REQUIRE(family != UINT32_MAX);
    float priority = 1;
    VkDeviceQueueCreateInfo queueInfo{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    queueInfo.queueFamilyIndex = family; queueInfo.queueCount = 1; queueInfo.pQueuePriorities = &priority;
    const char* deviceExtensions[] = {"VK_KHR_swapchain"};
    VkDeviceCreateInfo deviceInfo{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    deviceInfo.queueCreateInfoCount = 1; deviceInfo.pQueueCreateInfos = &queueInfo;
    deviceInfo.enabledExtensionCount = 1; deviceInfo.ppEnabledExtensionNames = deviceExtensions;
    VkDevice device; CHECK(vkCreateDevice(gpu, &deviceInfo, nullptr, &device));
    DEVICE(vkDestroyDevice); DEVICE(vkGetDeviceQueue); DEVICE(vkCreateSwapchainKHR); DEVICE(vkDestroySwapchainKHR); DEVICE(vkGetSwapchainImagesKHR);
    DEVICE(vkAcquireNextImageKHR); DEVICE(vkQueuePresentKHR); DEVICE(vkCreateSemaphore); DEVICE(vkDestroySemaphore); DEVICE(vkCreateCommandPool);
    DEVICE(vkDestroyCommandPool); DEVICE(vkAllocateCommandBuffers); DEVICE(vkBeginCommandBuffer); DEVICE(vkEndCommandBuffer);
    DEVICE(vkCmdPipelineBarrier); DEVICE(vkCmdClearColorImage); DEVICE(vkQueueSubmit); DEVICE(vkQueueWaitIdle); DEVICE(vkResetCommandBuffer); DEVICE(vkDeviceWaitIdle);
    VkQueue queue; vkGetDeviceQueue(device, family, 0, &queue);
    VkSurfaceCapabilitiesKHR caps; CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(gpu, vkSurface, &caps));
    count = 0; CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, vkSurface, &count, nullptr)); REQUIRE(count);
    std::vector<VkSurfaceFormatKHR> formats(count); CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, vkSurface, &count, formats.data()));
    VkSurfaceFormatKHR format = formats[0];
    for (const auto& f : formats) if (f.format == VK_FORMAT_R8G8B8A8_UNORM) format = f;
    VkExtent2D extent = caps.currentExtent;
    if (extent.width == 0xFFFFFFFF) extent = {720, 1280};
    VkSwapchainCreateInfoKHR swap{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
    swap.surface = vkSurface; swap.minImageCount = std::max(caps.minImageCount, 3u);
    if (caps.maxImageCount) swap.minImageCount = std::min(swap.minImageCount, caps.maxImageCount);
    swap.imageFormat = format.format; swap.imageColorSpace = format.colorSpace; swap.imageExtent = extent; swap.imageArrayLayers = 1;
    swap.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    swap.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE; swap.preTransform = caps.currentTransform;
    swap.compositeAlpha = (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) ? VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR : VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    swap.presentMode = VK_PRESENT_MODE_FIFO_KHR; swap.clipped = VK_TRUE;
    VkSwapchainKHR swapchain; CHECK(vkCreateSwapchainKHR(device, &swap, nullptr, &swapchain));
    uint32_t imageCount = 0; CHECK(vkGetSwapchainImagesKHR(device, swapchain, &imageCount, nullptr));
    std::vector<VkImage> images(imageCount); CHECK(vkGetSwapchainImagesKHR(device, swapchain, &imageCount, images.data()));
    printf("vkswap: %s swapchain %ux%u format %d, %u images\n", properties.deviceName, extent.width, extent.height, format.format, imageCount); fflush(stdout);
    VkSemaphoreCreateInfo semaphoreInfo{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    VkSemaphore acquired, rendered; CHECK(vkCreateSemaphore(device, &semaphoreInfo, nullptr, &acquired)); CHECK(vkCreateSemaphore(device, &semaphoreInfo, nullptr, &rendered));
    VkCommandPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO}; poolInfo.queueFamilyIndex = family;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    VkCommandPool pool; CHECK(vkCreateCommandPool(device, &poolInfo, nullptr, &pool));
    VkCommandBufferAllocateInfo commandInfo{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    commandInfo.commandPool = pool; commandInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY; commandInfo.commandBufferCount = 1;
    VkCommandBuffer command; CHECK(vkAllocateCommandBuffers(device, &commandInfo, &command));
    VkImageSubresourceRange range = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    int presented = 0;
    for (int frame = 0; frame < frames; frame++) {
        uint32_t index;
        VkResult r = vkAcquireNextImageKHR(device, swapchain, 2000000000ull, acquired, VK_NULL_HANDLE, &index);
        if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) { fprintf(stderr, "vkswap: acquire %d on frame %d\n", r, frame); break; }
        CHECK(vkResetCommandBuffer(command, 0));
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO}; CHECK(vkBeginCommandBuffer(command, &begin));
        VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        barrier.srcQueueFamilyIndex = barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED; barrier.image = images[index]; barrier.subresourceRange = range;
        barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED; barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);
        VkClearColorValue color{{1, 0, 0.5f, 1}};  // solid magenta: the screenshot check looks for it
        vkCmdClearColorImage(command, images[index], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &color, 1, &range);
        barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; barrier.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT; barrier.dstAccessMask = 0;
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);
        CHECK(vkEndCommandBuffer(command));
        VkPipelineStageFlags wait = VK_PIPELINE_STAGE_TRANSFER_BIT;
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO}; submit.waitSemaphoreCount = 1; submit.pWaitSemaphores = &acquired; submit.pWaitDstStageMask = &wait;
        submit.commandBufferCount = 1; submit.pCommandBuffers = &command; submit.signalSemaphoreCount = 1; submit.pSignalSemaphores = &rendered;
        CHECK(vkQueueSubmit(queue, 1, &submit, VK_NULL_HANDLE));
        VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR}; present.waitSemaphoreCount = 1; present.pWaitSemaphores = &rendered;
        present.swapchainCount = 1; present.pSwapchains = &swapchain; present.pImageIndices = &index;
        r = vkQueuePresentKHR(queue, &present);
        if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) { fprintf(stderr, "vkswap: present %d on frame %d\n", r, frame); break; }
        CHECK(vkQueueWaitIdle(queue));
        presented++;
        if (presented % 30 == 0) { printf("vkswap: presented %d frames\n", presented); fflush(stdout); }
    }
    vkDeviceWaitIdle(device);
    vkDestroyCommandPool(device, pool, nullptr); vkDestroySemaphore(device, acquired, nullptr); vkDestroySemaphore(device, rendered, nullptr);
    vkDestroySwapchainKHR(device, swapchain, nullptr); vkDestroyDevice(device, nullptr);
    vkDestroySurfaceKHR(instance, vkSurface, nullptr); vkDestroyInstance(instance, nullptr);
    release(window);
    printf("vkswap: PASS %d frames presented\n", presented); fflush(stdout);
    return presented == frames ? 0 : 2;
}
