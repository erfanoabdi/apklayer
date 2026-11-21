#include "../../aosp/hals/graphics/vulkan/TextureEmulation.h"
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <algorithm>

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


// vulkan-astc-probe [GPU-name-substring=] FORMAT W H BX BY BLOCKBYTES OUTPREFIX LAYERS LEVELS FILE...  (APKLAYER_VULKAN_TEXTURE_EMULATION=force|0 as in the HAL)
// FILEs are layer-major, level-minor block data (a .astc file's 16-byte header is skipped). The blocks go in with vkCmdCopyBufferToImage, a barrier
// to TRANSFER_SRC decodes, then every layer/level is read back twice: the blocks through the emulation (must equal the upload) and the decoded
// RGBA8 texels through the host's copy into OUTPREFIX.L<layer>M<level>.rgba (the caller compares them with astcenc's own decode or another GPU's).
static std::vector<uint8_t> slurp(const char* path) {
    std::vector<uint8_t> v;
    FILE* f = fopen(path, "rb");
    if (!f) { printf("FAIL cannot read %s\n", path); exit(1); }
    uint8_t buf[65536];
    size_t n;
    while ((n = fread(buf, 1, sizeof buf, f)) > 0) v.insert(v.end(), buf, buf + n);
    fclose(f);
    if (strstr(path, ".astc") && v.size() > 16) v.erase(v.begin(), v.begin() + 16);
    return v;
}

int main(int argc, char** argv) {
    if (argc < 11) { printf("usage: vulkan-astc-probe FORMAT W H BX BY BLOCKBYTES OUTPREFIX LAYERS LEVELS FILE...\n"); return 2; }
    const VkFormat format = VkFormat(atoi(argv[1]));
    const uint32_t W = atoi(argv[2]), H = atoi(argv[3]), BX = atoi(argv[4]), BY = atoi(argv[5]), BB = atoi(argv[6]), layers = atoi(argv[8]), levels = atoi(argv[9]);
    const char* prefix = argv[7];
    if (int(argc) - 10 != int(layers * levels)) { printf("FAIL need %u files\n", layers * levels); return 2; }
    lib = dlopen("libvulkan.so.1", RTLD_NOW);
    if (!lib) { printf("SKIP no host Vulkan loader\n"); return 77; }
    VkApplicationInfo app = {VK_STRUCTURE_TYPE_APPLICATION_INFO, nullptr, "vulkan-astc-probe", 1, nullptr, 0, VK_API_VERSION_1_3};
    VkInstanceCreateInfo ici = {VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, nullptr, 0, &app};
    VkInstance instance;
    if (sym<PFN_vkCreateInstance>("vkCreateInstance")(&ici, nullptr, &instance) != VK_SUCCESS) { printf("SKIP no Vulkan instance\n"); return 77; }
    uint32_t n = 0;
    sym<PFN_vkEnumeratePhysicalDevices>("vkEnumeratePhysicalDevices")(instance, &n, nullptr);
    std::vector<VkPhysicalDevice> gpus(n);
    sym<PFN_vkEnumeratePhysicalDevices>("vkEnumeratePhysicalDevices")(instance, &n, gpus.data());
    const char* want_gpu = getenv("PROBE_GPU");  // a substring of the device name
    VkPhysicalDevice gpu = VK_NULL_HANDLE;
    VkPhysicalDeviceProperties props;
    for (VkPhysicalDevice p : gpus) {
        sym<PFN_vkGetPhysicalDeviceProperties>("vkGetPhysicalDeviceProperties")(p, &props);
        if (props.deviceType != VK_PHYSICAL_DEVICE_TYPE_CPU && (!want_gpu || strstr(props.deviceName, want_gpu))) { gpu = p; break; }
    }
    if (!gpu) { printf("SKIP no matching Vulkan device\n"); return 77; }
    sym<PFN_vkGetPhysicalDeviceProperties>("vkGetPhysicalDeviceProperties")(gpu, &props);
    VkPhysicalDeviceFeatures real = {}, reported = {};
    sym<PFN_vkGetPhysicalDeviceFeatures>("vkGetPhysicalDeviceFeatures")(gpu, &real);
    inst<PFN_vkGetPhysicalDeviceFeatures>("vkGetPhysicalDeviceFeatures")(gpu, &reported);
    printf("device %s: ASTC %d ETC2 %d (host), %d %d (reported)\n", props.deviceName, real.textureCompressionASTC_LDR, real.textureCompressionETC2,
           reported.textureCompressionASTC_LDR, reported.textureCompressionETC2);
    VkFormatProperties fp;
    inst<PFN_vkGetPhysicalDeviceFormatProperties>("vkGetPhysicalDeviceFormatProperties")(gpu, format, &fp);
    CHECK(fp.optimalTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT, "format %d not sampled: %x", format, fp.optimalTilingFeatures);
    printf("format %d features reported: optimal %x\n", format, fp.optimalTilingFeatures);

    uint32_t qn = 0;
    sym<PFN_vkGetPhysicalDeviceQueueFamilyProperties>("vkGetPhysicalDeviceQueueFamilyProperties")(gpu, &qn, nullptr);
    std::vector<VkQueueFamilyProperties> qf(qn);
    sym<PFN_vkGetPhysicalDeviceQueueFamilyProperties>("vkGetPhysicalDeviceQueueFamilyProperties")(gpu, &qn, qf.data());
    uint32_t family = 0;
    while (family < qn && !(qf[family].queueFlags & VK_QUEUE_COMPUTE_BIT)) family++;
    float priority = 1;
    VkDeviceQueueCreateInfo qci = {VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, nullptr, 0, family, 1, &priority};
    VkPhysicalDeviceFeatures want = {};
    want.textureCompressionASTC_LDR = want.textureCompressionETC2 = VK_TRUE;
    VkDeviceCreateInfo dci = {VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, nullptr, 0, 1, &qci, 0, nullptr, 0, nullptr, &want};
    tx::DeviceRequest request;
    tx::prepare(gpu, &dci, &request);
    VkResult r = sym<PFN_vkCreateDevice>("vkCreateDevice")(gpu, &dci, nullptr, &gDevice);
    tx::created(gpu, r == VK_SUCCESS ? gDevice : VK_NULL_HANDLE, &request, r);
    CHECK(r == VK_SUCCESS, "vkCreateDevice %d", r);
    if (r != VK_SUCCESS) return 1;
    VkQueue queue;
    dev<PFN_vkGetDeviceQueue>("vkGetDeviceQueue")(gDevice, family, 0, &queue);

    VkImageCreateInfo ci = {VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, nullptr, VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT, VK_IMAGE_TYPE_2D, format, {W, H, 1}, levels, layers,
                            VK_SAMPLE_COUNT_1_BIT, VK_IMAGE_TILING_OPTIMAL,
                            VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT};
    VkImage image;
    const bool emulated = tx::createImage(gDevice, &ci, &image, &r);
    if (!emulated) r = dev<PFN_vkCreateImage>("vkCreateImage")(gDevice, &ci, nullptr, &image);
    CHECK(r == VK_SUCCESS, "createImage %d", r);
    printf("image emulated: %d\n", emulated);
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

    // buffer layout per subresource: blocks up | blocks back | decoded RGBA8 (each 256-aligned)
    struct Sub { uint32_t w, h, layer, level; VkDeviceSize up, back, dec, blocks; std::vector<uint8_t> data; };
    std::vector<Sub> subs;
    VkDeviceSize size = 0;
    auto align = [](VkDeviceSize v) { return (v + 255) & ~VkDeviceSize(255); };
    for (uint32_t l = 0; l < layers; l++)
        for (uint32_t m = 0; m < levels; m++) {
            Sub s = {std::max(1u, W >> m), std::max(1u, H >> m), l, m};
            s.blocks = VkDeviceSize((s.w + BX - 1) / BX) * ((s.h + BY - 1) / BY) * BB;
            s.data = slurp(argv[10 + l * levels + m]);
            if (s.data.size() != s.blocks) { printf("FAIL %s has %zu bytes, level needs %llu\n", argv[10 + l * levels + m], s.data.size(), (unsigned long long)s.blocks); return 1; }
            s.up = size; size = align(size + s.blocks);
            s.back = size; size = align(size + s.blocks);
            s.dec = size; size = align(size + VkDeviceSize(s.w) * s.h * 4);
            subs.push_back(std::move(s));
        }
    VkBufferCreateInfo bci = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, nullptr, 0, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT};
    VkBuffer buffer;
    dev<PFN_vkCreateBuffer>("vkCreateBuffer")(gDevice, &bci, nullptr, &buffer);
    VkMemoryRequirements breq;
    dev<PFN_vkGetBufferMemoryRequirements>("vkGetBufferMemoryRequirements")(gDevice, buffer, &breq);
    mai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, breq.size, type(breq.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)};
    VkDeviceMemory bufferMemory;
    allocate(gDevice, &mai, nullptr, &bufferMemory);
    dev<PFN_vkBindBufferMemory>("vkBindBufferMemory")(gDevice, buffer, bufferMemory, 0);
    uint8_t* data;
    dev<PFN_vkMapMemory>("vkMapMemory")(gDevice, bufferMemory, 0, size, 0, reinterpret_cast<void**>(&data));
    memset(data, 0xAA, size);
    std::vector<VkBufferImageCopy> up, back, dec;
    // PROBE_ROW_LENGTH=1: the copies name the rows in texels (bufferRowLength = width, bufferImageHeight = height), as Unity's uploads do; the
    // buffer layout is the same as tightly packed (a row holds ceil(width / block width) blocks either way)
    const bool rowLength = getenv("PROBE_ROW_LENGTH") != nullptr;
    for (Sub& s : subs) {
        memcpy(data + s.up, s.data.data(), s.blocks);
        VkImageSubresourceLayers sl = {VK_IMAGE_ASPECT_COLOR_BIT, s.level, s.layer, 1};
        uint32_t rl = rowLength ? s.w : 0, ih = rowLength ? s.h : 0;
        up.push_back({s.up, rl, ih, sl, {0, 0, 0}, {s.w, s.h, 1}});
        back.push_back({s.back, rl, ih, sl, {0, 0, 0}, {s.w, s.h, 1}});
        dec.push_back({s.dec, 0, 0, sl, {0, 0, 0}, {s.w, s.h, 1}});
    }
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
                                  {VK_IMAGE_ASPECT_COLOR_BIT, 0, VK_REMAINING_MIP_LEVELS, 0, VK_REMAINING_ARRAY_LAYERS}};
    VkImageMemoryBarrier toRead = toDst;
    toRead.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toRead.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    toRead.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toRead.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    barrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &toDst);
    dev<PFN_vkCmdCopyBufferToImage>("vkCmdCopyBufferToImage")(cb, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, up.size(), up.data());
    barrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &toRead);
    dev<PFN_vkCmdCopyImageToBuffer>("vkCmdCopyImageToBuffer")(cb, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, back.size(), back.data());
    sym<PFN_vkCmdCopyImageToBuffer>("vkCmdCopyImageToBuffer")(cb, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, dec.size(), dec.data());
    dev<PFN_vkEndCommandBuffer>("vkEndCommandBuffer")(cb);
    VkSubmitInfo submit = {VK_STRUCTURE_TYPE_SUBMIT_INFO, nullptr, 0, nullptr, nullptr, 1, &cb};
    CHECK(dev<PFN_vkQueueSubmit>("vkQueueSubmit")(queue, 1, &submit, VK_NULL_HANDLE) == VK_SUCCESS, "submit");
    CHECK(dev<PFN_vkQueueWaitIdle>("vkQueueWaitIdle")(queue) == VK_SUCCESS, "wait");

    for (Sub& s : subs) {
        CHECK(!memcmp(data + s.up, data + s.back, s.blocks), "L%u M%u blocks read back differ", s.layer, s.level);
        char name[512];
        snprintf(name, sizeof name, "%s.L%uM%u.rgba", prefix, s.layer, s.level);
        FILE* f = fopen(name, "wb");
        fwrite(data + s.dec, 1, size_t(s.w) * s.h * 4, f);
        fclose(f);
    }
    // a view of the compressed format in each of its two colour spaces' mutable twin: only creation (the decoded image stands behind it)
    VkImageViewCreateInfo vci = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, nullptr, 0, image, layers > 1 ? VK_IMAGE_VIEW_TYPE_2D_ARRAY : VK_IMAGE_VIEW_TYPE_2D, format,
                                 {}, {VK_IMAGE_ASPECT_COLOR_BIT, 0, levels, 0, layers}};
    VkImageView view;
    CHECK(dev<PFN_vkCreateImageView>("vkCreateImageView")(gDevice, &vci, nullptr, &view) == VK_SUCCESS, "image view");
    tx::destroyImage(gDevice, image);
    tx::destroyed(gDevice);
    printf("%s (%d failures)\n", failures ? "FAIL" : "PASS", failures);
    return failures ? 1 : 0;
}
