// Real Android loader -> host Vulkan: enumeration, command dispatch, image
// clear/copy/readback and repeated teardown. No synthetic GPU or WSI fallback.
#define VK_USE_PLATFORM_ANDROID_KHR
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
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
    REQUIRE(!swapchain); // This checkpoint must not expose the host's incompatible WSI.
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
    REQUIRE(vkCreateDevice(gpu, &deviceInfo, nullptr, &device) == VK_ERROR_EXTENSION_NOT_PRESENT);
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
extern "C" int main(int, char**) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    void* library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) { fprintf(stderr, "vktest: dlopen failed: %s\n", dlerror()); return 1; }
    for (unsigned cycle = 0; cycle < 6; cycle++) if (run(library,cycle)) return 1;
    dlclose(library);
    puts("vktest: PASS original Android Vulkan loader and host execution; Android presentation pending");
    return 0;
}

// Run exactly the same native regression through a real APK/HotSpot JNI call.
extern "C" JNIEXPORT jint JNICALL Java_hello_vulkan_Main_probe(JNIEnv*, jclass) {
    return main(0, nullptr);
}
