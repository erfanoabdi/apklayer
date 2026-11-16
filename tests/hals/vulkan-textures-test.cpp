// ASTC emulation of the Vulkan HAL (aosp/hals/graphics/vulkan/TextureEmulation.cpp, gfxstream's decoder) on the host's real
// GPU, called as Android's loader calls the HAL: the feature and format queries report ASTC, an ASTC 4x4 image with two mip
// levels takes four void-extent blocks (one colour each) and one block, a barrier to a readable layout decodes, and the decoded
// RGBA8 texels and the blocks read back (the block copy through the emulation, the texels from the app's image as the host
// sees it); once with version-1 barriers and copies, once with synchronization2. On a device with ASTC the emulation is forced (APKLAYER_VULKAN_TEXTURE_EMULATION=force).
#include "../../aosp/hals/graphics/vulkan/TextureEmulation.h"
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

namespace tx = apklayer::vulkan::textures;
static int failures;
#define CHECK(cond, ...) do { if (!(cond)) { failures++; printf("FAIL %s:%d ", __FILE__, __LINE__); printf(__VA_ARGS__); printf("\n"); } } while (0)

static void* lib;
template<class T> static T sym(const char* name) { return reinterpret_cast<T>(dlsym(lib, name)); }
static VkDevice gDevice;
// what the loader would get: the HAL's entry point, or the host's
template<class T> static T dev(const char* name) {
    if (PFN_vkVoidFunction f = tx::deviceProc(gDevice, name)) return reinterpret_cast<T>(f);
    return reinterpret_cast<T>(sym<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr")(gDevice, name));
}
template<class T> static T inst(const char* name) {
    if (PFN_vkVoidFunction f = tx::instanceProc(name)) return reinterpret_cast<T>(f);
    return sym<T>(name);
}

// an ASTC void-extent block: one RGBA colour (UNORM16 each) for the whole block
static void voidExtent(uint8_t* block, uint16_t r, uint16_t g, uint16_t b, uint16_t a) {
    uint64_t low = 0xFFFFFFFFFFFFFDFCull, high = uint64_t(r) | uint64_t(g) << 16 | uint64_t(b) << 32 | uint64_t(a) << 48;
    memcpy(block, &low, 8);
    memcpy(block + 8, &high, 8);
}

int main() {
    lib = dlopen("libvulkan.so.1", RTLD_NOW);
    if (!lib) { printf("SKIP no host Vulkan loader\n"); return 77; }
    if (!getenv("APKLAYER_VULKAN_TEXTURE_EMULATION")) setenv("APKLAYER_VULKAN_TEXTURE_EMULATION", "force", 1);
    VkApplicationInfo app = {VK_STRUCTURE_TYPE_APPLICATION_INFO, nullptr, "vulkan-textures-test", 1, nullptr, 0, VK_API_VERSION_1_3};
    VkInstanceCreateInfo ici = {VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, nullptr, 0, &app};
    VkInstance instance;
    if (sym<PFN_vkCreateInstance>("vkCreateInstance")(&ici, nullptr, &instance) != VK_SUCCESS) { printf("SKIP no Vulkan instance\n"); return 77; }
    uint32_t n = 0;
    sym<PFN_vkEnumeratePhysicalDevices>("vkEnumeratePhysicalDevices")(instance, &n, nullptr);
    std::vector<VkPhysicalDevice> gpus(n);
    sym<PFN_vkEnumeratePhysicalDevices>("vkEnumeratePhysicalDevices")(instance, &n, gpus.data());
    VkPhysicalDevice gpu = VK_NULL_HANDLE;
    VkPhysicalDeviceProperties props;
    for (VkPhysicalDevice p : gpus) {  // a real GPU first (llvmpipe has ASTC and ETC2 itself)
        sym<PFN_vkGetPhysicalDeviceProperties>("vkGetPhysicalDeviceProperties")(p, &props);
        if (!gpu || props.deviceType != VK_PHYSICAL_DEVICE_TYPE_CPU) gpu = p;
        if (props.deviceType != VK_PHYSICAL_DEVICE_TYPE_CPU) break;
    }
    if (!gpu) { printf("SKIP no Vulkan device\n"); return 77; }
    sym<PFN_vkGetPhysicalDeviceProperties>("vkGetPhysicalDeviceProperties")(gpu, &props);
    VkPhysicalDeviceFeatures real = {}, reported = {};
    sym<PFN_vkGetPhysicalDeviceFeatures>("vkGetPhysicalDeviceFeatures")(gpu, &real);
    inst<PFN_vkGetPhysicalDeviceFeatures>("vkGetPhysicalDeviceFeatures")(gpu, &reported);
    printf("device %s: ASTC %d ETC2 %d (host), %d %d (reported)\n", props.deviceName, real.textureCompressionASTC_LDR, real.textureCompressionETC2,
           reported.textureCompressionASTC_LDR, reported.textureCompressionETC2);
    CHECK(reported.textureCompressionASTC_LDR && reported.textureCompressionETC2, "features not reported");
    VkFormatProperties fp;
    inst<PFN_vkGetPhysicalDeviceFormatProperties>("vkGetPhysicalDeviceFormatProperties")(gpu, VK_FORMAT_ASTC_4x4_UNORM_BLOCK, &fp);
    CHECK(fp.optimalTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT, "ASTC 4x4 not sampled: %x", fp.optimalTilingFeatures);
    VkImageFormatProperties ifp;
    VkResult r = inst<PFN_vkGetPhysicalDeviceImageFormatProperties>("vkGetPhysicalDeviceImageFormatProperties")(
        gpu, VK_FORMAT_ASTC_4x4_SRGB_BLOCK, VK_IMAGE_TYPE_2D, VK_IMAGE_TILING_OPTIMAL, VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT, 0, &ifp);
    CHECK(r == VK_SUCCESS && ifp.maxExtent.width >= 4096, "ASTC image properties %d", r);

    // a device asking for the ASTC feature, as an app that saw it would
    uint32_t qn = 0;
    sym<PFN_vkGetPhysicalDeviceQueueFamilyProperties>("vkGetPhysicalDeviceQueueFamilyProperties")(gpu, &qn, nullptr);
    std::vector<VkQueueFamilyProperties> qf(qn);
    sym<PFN_vkGetPhysicalDeviceQueueFamilyProperties>("vkGetPhysicalDeviceQueueFamilyProperties")(gpu, &qn, qf.data());
    uint32_t family = 0;
    while (family < qn && !(qf[family].queueFlags & VK_QUEUE_COMPUTE_BIT)) family++;
    float priority = 1;
    VkDeviceQueueCreateInfo qci = {VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, nullptr, 0, family, 1, &priority};
    VkPhysicalDeviceFeatures want = {};
    want.textureCompressionASTC_LDR = VK_TRUE;
    VkPhysicalDeviceVulkan13Features v13 = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES};
    v13.synchronization2 = props.apiVersion >= VK_API_VERSION_1_3;
    VkDeviceCreateInfo dci = {VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, &v13, 0, 1, &qci, 0, nullptr, 0, nullptr, &want};
    tx::DeviceRequest request;
    tx::prepare(gpu, &dci, &request);
    r = sym<PFN_vkCreateDevice>("vkCreateDevice")(gpu, &dci, nullptr, &gDevice);
    tx::created(gpu, r == VK_SUCCESS ? gDevice : VK_NULL_HANDLE, &request, r);
    CHECK(r == VK_SUCCESS, "vkCreateDevice %d", r);
    CHECK(want.textureCompressionASTC_LDR, "the app's feature struct not restored");
    if (r != VK_SUCCESS) return 1;
    VkQueue queue;
    dev<PFN_vkGetDeviceQueue>("vkGetDeviceQueue")(gDevice, family, 0, &queue);

    for (int sync2 = 0; sync2 <= int(v13.synchronization2); sync2++) {
    printf("round %s\n", sync2 ? "synchronization2 (vkCmdPipelineBarrier2, vkCmdCopyBufferToImage2)" : "version 1 barriers and copies");
    // the image: 8x8 (2x2 blocks) and its 4x4 mip
    VkImageCreateInfo ci = {VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, nullptr, 0, VK_IMAGE_TYPE_2D, VK_FORMAT_ASTC_4x4_UNORM_BLOCK, {8, 8, 1}, 2, 1,
                            VK_SAMPLE_COUNT_1_BIT, VK_IMAGE_TILING_OPTIMAL,
                            VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT};
    VkImage image;
    CHECK(tx::createImage(gDevice, &ci, &image, &r) && r == VK_SUCCESS, "createImage not emulated or failed (%d)", r);
    VkMemoryRequirements req;
    dev<PFN_vkGetImageMemoryRequirements>("vkGetImageMemoryRequirements")(gDevice, image, &req);
    VkPhysicalDeviceMemoryProperties mp;
    sym<PFN_vkGetPhysicalDeviceMemoryProperties>("vkGetPhysicalDeviceMemoryProperties")(gpu, &mp);
    auto type = [&](uint32_t bits, VkMemoryPropertyFlags flags) {
        for (uint32_t i = 0; i < mp.memoryTypeCount; i++)
            if ((bits & (1u << i)) && (mp.memoryTypes[i].propertyFlags & flags) == flags) return i;
        return 0u;
    };
    auto allocate = dev<PFN_vkAllocateMemory>("vkAllocateMemory");
    VkMemoryAllocateInfo mai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, req.size, type(req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)};
    VkDeviceMemory imageMemory;
    allocate(gDevice, &mai, nullptr, &imageMemory);
    CHECK(dev<PFN_vkBindImageMemory>("vkBindImageMemory")(gDevice, image, imageMemory, 0) == VK_SUCCESS, "bind");

    // one host buffer: the blocks to upload (0..79), the blocks read back (80..159), the decoded texels (160..)
    const VkDeviceSize texels = 160, size = texels + 8 * 8 * 4 + 4 * 4 * 4;
    VkBufferCreateInfo bci = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, nullptr, 0, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT};
    VkBuffer buffer;
    dev<PFN_vkCreateBuffer>("vkCreateBuffer")(gDevice, &bci, nullptr, &buffer);
    VkMemoryRequirements breq;
    dev<PFN_vkGetBufferMemoryRequirements>("vkGetBufferMemoryRequirements")(gDevice, buffer, &breq);
    mai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, breq.size,
           type(breq.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)};
    VkDeviceMemory bufferMemory;
    allocate(gDevice, &mai, nullptr, &bufferMemory);
    dev<PFN_vkBindBufferMemory>("vkBindBufferMemory")(gDevice, buffer, bufferMemory, 0);
    uint8_t* data;
    dev<PFN_vkMapMemory>("vkMapMemory")(gDevice, bufferMemory, 0, size, 0, reinterpret_cast<void**>(&data));
    memset(data, 0xAA, size);
    const uint8_t colors[5][4] = {{200, 0, 0, 255}, {0, 200, 0, 255}, {0, 0, 200, 255}, {100, 150, 50, 128}, {10, 20, 30, 40}};
    for (int b = 0; b < 5; b++) voidExtent(data + 16 * b, colors[b][0] << 8, colors[b][1] << 8, colors[b][2] << 8, colors[b][3] << 8);

    VkCommandPoolCreateInfo pci = {VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, nullptr, 0, family};
    VkCommandPool pool;
    dev<PFN_vkCreateCommandPool>("vkCreateCommandPool")(gDevice, &pci, nullptr, &pool);
    VkCommandBufferAllocateInfo cai = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, nullptr, pool, VK_COMMAND_BUFFER_LEVEL_PRIMARY, 1};
    VkCommandBuffer cb;
    dev<PFN_vkAllocateCommandBuffers>("vkAllocateCommandBuffers")(gDevice, &cai, &cb);
    VkCommandBufferBeginInfo begin = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    dev<PFN_vkBeginCommandBuffer>("vkBeginCommandBuffer")(cb, &begin);
    auto barrier = dev<PFN_vkCmdPipelineBarrier>("vkCmdPipelineBarrier");
    VkImageMemoryBarrier toDst = {VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, nullptr, 0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_UNDEFINED,
                                  VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED, image,
                                  {VK_IMAGE_ASPECT_COLOR_BIT, 0, VK_REMAINING_MIP_LEVELS, 0, 1}};
    VkBufferImageCopy up[2] = {{0, 0, 0, {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1}, {0, 0, 0}, {8, 8, 1}},
                               {64, 0, 0, {VK_IMAGE_ASPECT_COLOR_BIT, 1, 0, 1}, {0, 0, 0}, {4, 4, 1}}};
    VkImageMemoryBarrier toRead = toDst;  // readable: the decoder runs here
    toRead.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toRead.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    toRead.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toRead.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    if (!sync2) {
        barrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &toDst);
        dev<PFN_vkCmdCopyBufferToImage>("vkCmdCopyBufferToImage")(cb, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 2, up);
        barrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &toRead);
    } else {
        auto barrier2 = dev<PFN_vkCmdPipelineBarrier2>("vkCmdPipelineBarrier2");
        auto two = [](const VkImageMemoryBarrier& b, VkPipelineStageFlags2 src, VkPipelineStageFlags2 dst) {
            return VkImageMemoryBarrier2{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2, nullptr, src, b.srcAccessMask, dst, b.dstAccessMask, b.oldLayout,
                                         b.newLayout, b.srcQueueFamilyIndex, b.dstQueueFamilyIndex, b.image, b.subresourceRange};
        };
        VkImageMemoryBarrier2 b2 = two(toDst, VK_PIPELINE_STAGE_2_NONE, VK_PIPELINE_STAGE_2_COPY_BIT);
        VkDependencyInfo dep = {VK_STRUCTURE_TYPE_DEPENDENCY_INFO, nullptr, 0, 0, nullptr, 0, nullptr, 1, &b2};
        barrier2(cb, &dep);
        VkBufferImageCopy2 up2[2];
        for (int i = 0; i < 2; i++)
            up2[i] = {VK_STRUCTURE_TYPE_BUFFER_IMAGE_COPY_2, nullptr, up[i].bufferOffset, 0, 0, up[i].imageSubresource, up[i].imageOffset, up[i].imageExtent};
        VkCopyBufferToImageInfo2 copy = {VK_STRUCTURE_TYPE_COPY_BUFFER_TO_IMAGE_INFO_2, nullptr, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 2, up2};
        dev<PFN_vkCmdCopyBufferToImage2>("vkCmdCopyBufferToImage2")(cb, &copy);
        b2 = two(toRead, VK_PIPELINE_STAGE_2_COPY_BIT, VK_PIPELINE_STAGE_2_COPY_BIT);
        barrier2(cb, &dep);
    }
    VkBufferImageCopy blocks[2] = {{80, 0, 0, {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1}, {0, 0, 0}, {8, 8, 1}},
                                   {144, 0, 0, {VK_IMAGE_ASPECT_COLOR_BIT, 1, 0, 1}, {0, 0, 0}, {4, 4, 1}}};
    dev<PFN_vkCmdCopyImageToBuffer>("vkCmdCopyImageToBuffer")(cb, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, 2, blocks);
    // the decoded image under the app's handle, read with the host's own copy (an RGBA8 image to the host)
    VkBufferImageCopy decoded[2] = {{texels, 0, 0, {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1}, {0, 0, 0}, {8, 8, 1}},
                                    {texels + 256, 0, 0, {VK_IMAGE_ASPECT_COLOR_BIT, 1, 0, 1}, {0, 0, 0}, {4, 4, 1}}};
    sym<PFN_vkCmdCopyImageToBuffer>("vkCmdCopyImageToBuffer")(cb, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, 2, decoded);
    dev<PFN_vkEndCommandBuffer>("vkEndCommandBuffer")(cb);
    VkSubmitInfo submit = {VK_STRUCTURE_TYPE_SUBMIT_INFO, nullptr, 0, nullptr, nullptr, 1, &cb};
    CHECK(dev<PFN_vkQueueSubmit>("vkQueueSubmit")(queue, 1, &submit, VK_NULL_HANDLE) == VK_SUCCESS, "submit");
    CHECK(dev<PFN_vkQueueWaitIdle>("vkQueueWaitIdle")(queue) == VK_SUCCESS, "wait");

    CHECK(!memcmp(data, data + 80, 80), "blocks read back differ");
    for (int y = 0; y < 8; y++)
        for (int x = 0; x < 8; x++) {
            const uint8_t* px = data + texels + (y * 8 + x) * 4;
            const uint8_t* want = colors[(y / 4) * 2 + x / 4];
            CHECK(!memcmp(px, want, 4), "mip 0 texel %d,%d = %d %d %d %d, want %d %d %d %d", x, y, px[0], px[1], px[2], px[3], want[0], want[1], want[2], want[3]);
        }
    for (int i = 0; i < 16; i++) {
        const uint8_t* px = data + texels + 256 + i * 4;
        CHECK(!memcmp(px, colors[4], 4), "mip 1 texel %d = %d %d %d %d", i, px[0], px[1], px[2], px[3]);
    }
    // a view of the compressed format is made (on the decoded image)
    VkImageViewCreateInfo vci = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, nullptr, 0, image, VK_IMAGE_VIEW_TYPE_2D, VK_FORMAT_ASTC_4x4_UNORM_BLOCK,
                                 {}, {VK_IMAGE_ASPECT_COLOR_BIT, 0, 2, 0, 1}};
    VkImageView view;
    CHECK(dev<PFN_vkCreateImageView>("vkCreateImageView")(gDevice, &vci, nullptr, &view) == VK_SUCCESS, "image view");

    dev<PFN_vkDestroyImageView>("vkDestroyImageView")(gDevice, view, nullptr);
    CHECK(tx::destroyImage(gDevice, image), "destroyImage not emulated");
    dev<PFN_vkFreeCommandBuffers>("vkFreeCommandBuffers")(gDevice, pool, 1, &cb);
    dev<PFN_vkDestroyCommandPool>("vkDestroyCommandPool")(gDevice, pool, nullptr);
    dev<PFN_vkDestroyBuffer>("vkDestroyBuffer")(gDevice, buffer, nullptr);
    dev<PFN_vkFreeMemory>("vkFreeMemory")(gDevice, bufferMemory, nullptr);
    dev<PFN_vkFreeMemory>("vkFreeMemory")(gDevice, imageMemory, nullptr);
    }
    tx::destroyed(gDevice);
    sym<PFN_vkDestroyDevice>("vkDestroyDevice")(gDevice, nullptr);
    sym<PFN_vkDestroyInstance>("vkDestroyInstance")(instance, nullptr);
    printf("%s (%d failures)\n", failures ? "FAIL" : "PASS", failures);
    return failures ? 1 : 0;
}
