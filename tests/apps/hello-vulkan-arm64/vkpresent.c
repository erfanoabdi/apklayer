/* An arm64-only Vulkan swapchain (hello-vulkan-arm64): on an x86_64 host it runs only through the native bridge, its libvulkan the guest
 * proxy of the host's Android loader. A C copy of aosp/tools/vktest.cpp's present(): solid magenta frames on a SurfaceView's window, the
 * same "vkswap:" lines (tests/vm/vulkan-present.py). */
#define VK_USE_PLATFORM_ANDROID_KHR
#define VK_NO_PROTOTYPES
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdio.h>
#include <vulkan/vulkan.h>

#define CHECK(x) do { VkResult r_ = (x); if (r_ != VK_SUCCESS) { fprintf(stderr, "vkswap: %s = %d line %d\n", #x, r_, __LINE__); return 1; } } while (0)
#define REQUIRE(x) do { if (!(x)) { fprintf(stderr, "vkswap: %s failed line %d\n", #x, __LINE__); return 1; } } while (0)
#define INSTANCE(name) PFN_##name name = (PFN_##name)get(instance, #name); REQUIRE(name)
#define DEVICE(name) PFN_##name name = (PFN_##name)deviceProc(device, #name); REQUIRE(name)

JNIEXPORT jint JNICALL Java_hello_vulkan_arm64_Main_present(JNIEnv *env, jclass c, jobject surface, jint frames)
{
    (void)c;
    void *library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL); REQUIRE(library);
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface); REQUIRE(window);
    PFN_vkGetInstanceProcAddr get = (PFN_vkGetInstanceProcAddr)dlsym(library, "vkGetInstanceProcAddr"); REQUIRE(get);
    PFN_vkCreateInstance create = (PFN_vkCreateInstance)get(VK_NULL_HANDLE, "vkCreateInstance"); REQUIRE(create);
    const char *extensions[] = {"VK_KHR_surface", "VK_KHR_android_surface"};
    VkApplicationInfo application = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .apiVersion = VK_API_VERSION_1_1};
    VkInstanceCreateInfo info = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &application,
                                 .enabledExtensionCount = 2, .ppEnabledExtensionNames = extensions};
    VkInstance instance; CHECK(create(&info, NULL, &instance));
    INSTANCE(vkEnumeratePhysicalDevices); INSTANCE(vkGetPhysicalDeviceProperties); INSTANCE(vkGetPhysicalDeviceQueueFamilyProperties);
    INSTANCE(vkCreateDevice); INSTANCE(vkCreateAndroidSurfaceKHR); INSTANCE(vkDestroySurfaceKHR); INSTANCE(vkDestroyInstance);
    INSTANCE(vkGetPhysicalDeviceSurfaceSupportKHR); INSTANCE(vkGetPhysicalDeviceSurfaceCapabilitiesKHR); INSTANCE(vkGetPhysicalDeviceSurfaceFormatsKHR);
    PFN_vkGetDeviceProcAddr deviceProc = (PFN_vkGetDeviceProcAddr)get(instance, "vkGetDeviceProcAddr"); REQUIRE(deviceProc);
    uint32_t count = 1;
    VkPhysicalDevice gpu;
    VkResult listed = vkEnumeratePhysicalDevices(instance, &count, &gpu);
    REQUIRE((listed == VK_SUCCESS || listed == VK_INCOMPLETE) && count);
    VkPhysicalDeviceProperties properties; vkGetPhysicalDeviceProperties(gpu, &properties);
    VkAndroidSurfaceCreateInfoKHR surfaceInfo = {.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR, .window = window};
    VkSurfaceKHR vkSurface; CHECK(vkCreateAndroidSurfaceKHR(instance, &surfaceInfo, NULL, &vkSurface));
    VkQueueFamilyProperties families[16];
    count = 16; vkGetPhysicalDeviceQueueFamilyProperties(gpu, &count, families);
    uint32_t family = UINT32_MAX;
    for (uint32_t i = 0; i < count && family == UINT32_MAX; i++) {
        VkBool32 present = VK_FALSE; vkGetPhysicalDeviceSurfaceSupportKHR(gpu, i, vkSurface, &present);
        if ((families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) family = i;
    }
    REQUIRE(family != UINT32_MAX);
    float priority = 1;
    VkDeviceQueueCreateInfo queueInfo = {.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, .queueFamilyIndex = family, .queueCount = 1,
                                         .pQueuePriorities = &priority};
    const char *deviceExtensions[] = {"VK_KHR_swapchain"};
    VkDeviceCreateInfo deviceInfo = {.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, .queueCreateInfoCount = 1, .pQueueCreateInfos = &queueInfo,
                                     .enabledExtensionCount = 1, .ppEnabledExtensionNames = deviceExtensions};
    VkDevice device; CHECK(vkCreateDevice(gpu, &deviceInfo, NULL, &device));
    DEVICE(vkDestroyDevice); DEVICE(vkGetDeviceQueue); DEVICE(vkCreateSwapchainKHR); DEVICE(vkDestroySwapchainKHR); DEVICE(vkGetSwapchainImagesKHR);
    DEVICE(vkAcquireNextImageKHR); DEVICE(vkQueuePresentKHR); DEVICE(vkCreateSemaphore); DEVICE(vkDestroySemaphore); DEVICE(vkCreateCommandPool);
    DEVICE(vkDestroyCommandPool); DEVICE(vkAllocateCommandBuffers); DEVICE(vkBeginCommandBuffer); DEVICE(vkEndCommandBuffer);
    DEVICE(vkCmdPipelineBarrier); DEVICE(vkCmdClearColorImage); DEVICE(vkQueueSubmit); DEVICE(vkQueueWaitIdle); DEVICE(vkResetCommandBuffer); DEVICE(vkDeviceWaitIdle);
    VkQueue queue; vkGetDeviceQueue(device, family, 0, &queue);
    VkSurfaceCapabilitiesKHR caps; CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(gpu, vkSurface, &caps));
    VkSurfaceFormatKHR formats[32];
    count = 32;
    VkResult got = vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, vkSurface, &count, formats);
    REQUIRE((got == VK_SUCCESS || got == VK_INCOMPLETE) && count);
    VkSurfaceFormatKHR format = formats[0];
    for (uint32_t i = 0; i < count; i++) if (formats[i].format == VK_FORMAT_R8G8B8A8_UNORM) format = formats[i];
    VkExtent2D extent = caps.currentExtent;
    if (extent.width == 0xFFFFFFFF) extent = (VkExtent2D){720, 1280};
    uint32_t images_wanted = caps.minImageCount > 3 ? caps.minImageCount : 3;
    if (caps.maxImageCount && images_wanted > caps.maxImageCount) images_wanted = caps.maxImageCount;
    VkSwapchainCreateInfoKHR swap = {
        .sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR, .surface = vkSurface, .minImageCount = images_wanted, .imageFormat = format.format,
        .imageColorSpace = format.colorSpace, .imageExtent = extent, .imageArrayLayers = 1,
        .imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT, .imageSharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .preTransform = caps.currentTransform,
        .compositeAlpha = (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) ? VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR : VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
        .presentMode = VK_PRESENT_MODE_FIFO_KHR, .clipped = VK_TRUE};
    VkSwapchainKHR swapchain; CHECK(vkCreateSwapchainKHR(device, &swap, NULL, &swapchain));
    VkImage images[16];
    uint32_t imageCount = 16; CHECK(vkGetSwapchainImagesKHR(device, swapchain, &imageCount, images));
    printf("vkswap: %s swapchain %ux%u format %d, %u images\n", properties.deviceName, extent.width, extent.height, format.format, imageCount); fflush(stdout);
    VkSemaphoreCreateInfo semaphoreInfo = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    VkSemaphore acquired, rendered; CHECK(vkCreateSemaphore(device, &semaphoreInfo, NULL, &acquired)); CHECK(vkCreateSemaphore(device, &semaphoreInfo, NULL, &rendered));
    VkCommandPoolCreateInfo poolInfo = {.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
                                        .queueFamilyIndex = family};
    VkCommandPool pool; CHECK(vkCreateCommandPool(device, &poolInfo, NULL, &pool));
    VkCommandBufferAllocateInfo commandInfo = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, .commandPool = pool,
                                               .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1};
    VkCommandBuffer command; CHECK(vkAllocateCommandBuffers(device, &commandInfo, &command));
    VkImageSubresourceRange range = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    int presented = 0;
    for (int frame = 0; frame < frames; frame++) {
        uint32_t index;
        VkResult r = vkAcquireNextImageKHR(device, swapchain, 2000000000ull, acquired, VK_NULL_HANDLE, &index);
        if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) { fprintf(stderr, "vkswap: acquire %d on frame %d\n", r, frame); break; }
        CHECK(vkResetCommandBuffer(command, 0));
        VkCommandBufferBeginInfo begin = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO}; CHECK(vkBeginCommandBuffer(command, &begin));
        VkImageMemoryBarrier barrier = {.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
                                        .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED, .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                        .image = images[index], .subresourceRange = range};
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, NULL, 0, NULL, 1, &barrier);
        VkClearColorValue color = {{1, 0, 0.5f, 1}};  /* solid magenta: the screenshot check looks for it */
        vkCmdClearColorImage(command, images[index], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &color, 1, &range);
        barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; barrier.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT; barrier.dstAccessMask = 0;
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, NULL, 0, NULL, 1, &barrier);
        CHECK(vkEndCommandBuffer(command));
        VkPipelineStageFlags wait = VK_PIPELINE_STAGE_TRANSFER_BIT;
        VkSubmitInfo submit = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .waitSemaphoreCount = 1, .pWaitSemaphores = &acquired, .pWaitDstStageMask = &wait,
                               .commandBufferCount = 1, .pCommandBuffers = &command, .signalSemaphoreCount = 1, .pSignalSemaphores = &rendered};
        CHECK(vkQueueSubmit(queue, 1, &submit, VK_NULL_HANDLE));
        VkPresentInfoKHR present = {.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR, .waitSemaphoreCount = 1, .pWaitSemaphores = &rendered,
                                    .swapchainCount = 1, .pSwapchains = &swapchain, .pImageIndices = &index};
        r = vkQueuePresentKHR(queue, &present);
        if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) { fprintf(stderr, "vkswap: present %d on frame %d\n", r, frame); break; }
        CHECK(vkQueueWaitIdle(queue));
        presented++;
        if (presented % 30 == 0) { printf("vkswap: presented %d frames\n", presented); fflush(stdout); }
    }
    vkDeviceWaitIdle(device);
    vkDestroyCommandPool(device, pool, NULL); vkDestroySemaphore(device, acquired, NULL); vkDestroySemaphore(device, rendered, NULL);
    vkDestroySwapchainKHR(device, swapchain, NULL); vkDestroyDevice(device, NULL);
    vkDestroySurfaceKHR(instance, vkSurface, NULL); vkDestroyInstance(instance, NULL);
    ANativeWindow_release(window);
    printf("vkswap: PASS %d frames presented\n", presented); fflush(stdout);
    return presented == frames ? 0 : 2;
}
