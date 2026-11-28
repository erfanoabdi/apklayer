// ASTC/ETC2 emulation over the host driver (TextureEmulation.h): device, image, memory and view entry points, and the format
// queries. The command-buffer side (copies, barriers, the compute state the decoder disturbs) is TextureCommands.cpp.
// Follows gfxstream's VkDecoderGlobalState (on_vkCreateImage, on_vkBindImageMemory, on_vkCreateImageView, the format queries).
#include "TextureState.h"

#include <dlfcn.h>
#include <cstdlib>
#include <cstring>

#ifdef __ANDROID__
#include "../../HostEnvironment.h"
#endif

namespace apklayer::vulkan::textures {

std::mutex gLock;
std::unordered_map<VkDevice, std::unique_ptr<Device>> gDevices;
std::unordered_map<VkImage, Image> gImages;
std::atomic<int> gEmulatedImages{0};

void* hostSymbol(const char* name) {
    static void* library = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    return library ? dlsym(library, name) : nullptr;
}

Device* findDevice(VkDevice device) {
    std::lock_guard<std::mutex> lock(gLock);
    auto it = gDevices.find(device);
    return it == gDevices.end() ? nullptr : it->second.get();
}

CompressedImageInfo* findImage(VkImage image, Device** device) {
    if (gEmulatedImages.load(std::memory_order_relaxed) == 0 || image == VK_NULL_HANDLE) return nullptr;
    std::lock_guard<std::mutex> lock(gLock);
    auto it = gImages.find(image);
    if (it == gImages.end()) return nullptr;
    if (device) *device = it->second.device;
    return it->second.info.get();
}

Unlink::Unlink(const void* chain, std::initializer_list<VkStructureType> drop) {
    VkBaseInStructure* last = nullptr;
    for (auto* link = static_cast<const VkBaseInStructure*>(chain); link; link = link->pNext) {
        auto* node = const_cast<VkBaseInStructure*>(link);
        original.emplace_back(node, node->pNext);
        if (std::find(drop.begin(), drop.end(), link->sType) != drop.end()) continue;
        if (last) last->pNext = node; else head = node;
        last = node;
    }
    if (last) last->pNext = nullptr;
}
Unlink::~Unlink() { for (auto& entry : original) entry.first->pNext = entry.second; }

namespace {

const char* environment(const char* name) {
#ifdef __ANDROID__
    return apklayer::hostGetenv(name);
#else
    return std::getenv(name);
#endif
}

struct Emulation { bool astc = false, etc2 = false; };

// APKLAYER_VULKAN_TEXTURE_TRACE=1: every emulated image's creation, memory requirements, bind and decode, and any image whose
// memory requirements come back with size 0 (NVIDIA's ICD reports 0 for an ASTC image it lacks: a leak of the host's answer)
}  // namespace
bool tracing() {
    static const bool on = [] { const char* v = environment("APKLAYER_VULKAN_TEXTURE_TRACE"); return v && *v && strcmp(v, "0"); }();
    return on;
}
// APKLAYER_VULKAN_TEXTURE_TRACE=nodecode (diagnostic): traced, and the decoder never runs (the app's barrier reaches the decoded image as is)
bool skipDecode() {
    static const bool on = [] { const char* v = environment("APKLAYER_VULKAN_TEXTURE_TRACE"); return v && !strcmp(v, "nodecode"); }();
    return on;
}
namespace {
void traceRequirements(const char* path, VkImage image, bool emulatedImage, const VkMemoryRequirements& r) {
    if (emulatedImage || r.size == 0)
        TRACE("%s image %p%s: size %llu alignment %llu types 0x%x", path, (void*)image, emulatedImage ? " (emulated)" : "",
              (unsigned long long)r.size, (unsigned long long)r.alignment, r.memoryTypeBits);
}

// what the physical device lacks (its own features, asked once)
Emulation emulation(VkPhysicalDevice physical) {
    static std::mutex lock;
    static std::unordered_map<VkPhysicalDevice, Emulation> known;
    std::lock_guard<std::mutex> guard(lock);
    auto it = known.find(physical);
    if (it != known.end()) return it->second;
    Emulation e;
    const char* off = environment("APKLAYER_VULKAN_TEXTURE_EMULATION");
    auto features = host<PFN_vkGetPhysicalDeviceFeatures>("vkGetPhysicalDeviceFeatures");
    if (features && !(off && !strcmp(off, "0"))) {
        VkPhysicalDeviceFeatures f = {};
        features(physical, &f);
        bool force = off && !strcmp(off, "force");  // tests: emulated even where the device has the formats
        e.astc = force || !f.textureCompressionASTC_LDR;
        e.etc2 = force || !f.textureCompressionETC2;
        if (e.astc || e.etc2) GFXSTREAM_INFO("the device lacks%s%s: emulated", e.astc ? " ASTC" : "", e.etc2 ? " ETC2" : "");
    }
    return known[physical] = e;
}

bool emulated(const Emulation& e, VkFormat format) { return (e.astc && isAstc(format)) || (e.etc2 && isEtc2(format)); }

// gfxstream's kEmulatedTexture*Mask: what a decoded compressed format offers
constexpr VkFormatFeatureFlags kBufferMask = VK_FORMAT_FEATURE_TRANSFER_SRC_BIT | VK_FORMAT_FEATURE_TRANSFER_DST_BIT |
                                             VK_FORMAT_FEATURE_BLIT_SRC_BIT | VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
constexpr VkFormatFeatureFlags kOptimalMask = kBufferMask | VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;

void mask(VkFormatProperties* p) {
    p->linearTilingFeatures &= kBufferMask;
    p->optimalTilingFeatures &= kOptimalMask;
    p->bufferFeatures &= kBufferMask;
}

// gfxstream's supportEmulatedCompressedImageFormatProperty, plus optimal tiling only (the blocks are not in a linear layout)
bool emulatedImageSupported(VkImageType type, VkImageTiling tiling, VkImageUsageFlags usage) {
    return !(usage & (VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)) && type != VK_IMAGE_TYPE_1D &&
           tiling == VK_IMAGE_TILING_OPTIMAL;
}

VKAPI_ATTR void VKAPI_CALL getFeatures(VkPhysicalDevice physical, VkPhysicalDeviceFeatures* features) {
    host<PFN_vkGetPhysicalDeviceFeatures>("vkGetPhysicalDeviceFeatures")(physical, features);
    Emulation e = emulation(physical);
    if (e.astc) features->textureCompressionASTC_LDR = VK_TRUE;
    if (e.etc2) features->textureCompressionETC2 = VK_TRUE;
}

VKAPI_ATTR void VKAPI_CALL getFeatures2(VkPhysicalDevice physical, VkPhysicalDeviceFeatures2* features) {
    host<PFN_vkGetPhysicalDeviceFeatures2>("vkGetPhysicalDeviceFeatures2")(physical, features);
    Emulation e = emulation(physical);
    if (e.astc) features->features.textureCompressionASTC_LDR = VK_TRUE;
    if (e.etc2) features->features.textureCompressionETC2 = VK_TRUE;
}

VKAPI_ATTR void VKAPI_CALL getFormatProperties(VkPhysicalDevice physical, VkFormat format, VkFormatProperties* properties) {
    auto fn = host<PFN_vkGetPhysicalDeviceFormatProperties>("vkGetPhysicalDeviceFormatProperties");
    if (!emulated(emulation(physical), format)) return fn(physical, format, properties);
    fn(physical, CompressedImageInfo::getOutputFormat(format), properties);
    mask(properties);
}

VKAPI_ATTR void VKAPI_CALL getFormatProperties2(VkPhysicalDevice physical, VkFormat format, VkFormatProperties2* properties) {
    auto fn = host<PFN_vkGetPhysicalDeviceFormatProperties2>("vkGetPhysicalDeviceFormatProperties2");
    if (!emulated(emulation(physical), format)) return fn(physical, format, properties);
    fn(physical, CompressedImageInfo::getOutputFormat(format), properties);
    mask(&properties->formatProperties);
    for (auto* link = static_cast<VkBaseOutStructure*>(properties->pNext); link; link = link->pNext) {
        if (link->sType == VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_3) {
            auto* p3 = reinterpret_cast<VkFormatProperties3*>(link);
            p3->linearTilingFeatures &= kBufferMask;
            p3->optimalTilingFeatures &= kOptimalMask;
            p3->bufferFeatures &= kBufferMask;
        } else if (link->sType == VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_EXT) {
            reinterpret_cast<VkDrmFormatModifierPropertiesListEXT*>(link)->drmFormatModifierCount = 0;  // no shared layout of the blocks
        } else if (link->sType == VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_2_EXT) {
            reinterpret_cast<VkDrmFormatModifierPropertiesList2EXT*>(link)->drmFormatModifierCount = 0;
        }
    }
}

VKAPI_ATTR VkResult VKAPI_CALL getImageFormatProperties(VkPhysicalDevice physical, VkFormat format, VkImageType type, VkImageTiling tiling,
                                                        VkImageUsageFlags usage, VkImageCreateFlags flags, VkImageFormatProperties* out) {
    auto fn = host<PFN_vkGetPhysicalDeviceImageFormatProperties>("vkGetPhysicalDeviceImageFormatProperties");
    if (!emulated(emulation(physical), format)) return fn(physical, format, type, tiling, usage, flags, out);
    if (!emulatedImageSupported(type, tiling, usage)) { *out = {}; return VK_ERROR_FORMAT_NOT_SUPPORTED; }
    flags = (flags & ~VK_IMAGE_CREATE_BLOCK_TEXEL_VIEW_COMPATIBLE_BIT) | VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT;
    VkResult result = fn(physical, CompressedImageInfo::getCompressedMipmapsFormat(format), type, tiling, usage | VK_IMAGE_USAGE_STORAGE_BIT,
                         flags, out);
    if (result == VK_SUCCESS) out->sampleCounts &= VK_SAMPLE_COUNT_1_BIT;
    return result;
}

VKAPI_ATTR VkResult VKAPI_CALL getImageFormatProperties2(VkPhysicalDevice physical, const VkPhysicalDeviceImageFormatInfo2* info,
                                                         VkImageFormatProperties2* out) {
    auto fn = host<PFN_vkGetPhysicalDeviceImageFormatProperties2>("vkGetPhysicalDeviceImageFormatProperties2");
    if (!emulated(emulation(physical), info->format)) return fn(physical, info, out);
    if (!emulatedImageSupported(info->type, info->tiling, info->usage)) {
        out->imageFormatProperties = {};
        return VK_ERROR_FORMAT_NOT_SUPPORTED;
    }
    VkPhysicalDeviceImageFormatInfo2 blocks = *info;
    blocks.format = CompressedImageInfo::getCompressedMipmapsFormat(info->format);
    blocks.usage |= VK_IMAGE_USAGE_STORAGE_BIT;
    blocks.flags = (blocks.flags & ~VK_IMAGE_CREATE_BLOCK_TEXEL_VIEW_COMPATIBLE_BIT) | VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT;
    Unlink chain(info->pNext, {VK_STRUCTURE_TYPE_IMAGE_FORMAT_LIST_CREATE_INFO});  // names the compressed format
    blocks.pNext = chain.head;
    VkResult result = fn(physical, &blocks, out);
    if (result == VK_SUCCESS) out->imageFormatProperties.sampleCounts &= VK_SAMPLE_COUNT_1_BIT;
    return result;
}

// ---- device entry points ----

VKAPI_ATTR void VKAPI_CALL getImageMemoryRequirements(VkDevice device, VkImage image, VkMemoryRequirements* requirements) {
    host<PFN_vkGetImageMemoryRequirements>("vkGetImageMemoryRequirements")(device, image, requirements);
    CompressedImageInfo* info = findImage(image, nullptr);
    if (info) *requirements = info->getMemoryRequirements();
    traceRequirements("vkGetImageMemoryRequirements", image, info, *requirements);
}

// the output image and its block images share one allocation: never a dedicated one (it would hold one image only)
void notDedicated(VkMemoryRequirements2* requirements) {
    for (auto* link = static_cast<VkBaseOutStructure*>(requirements->pNext); link; link = link->pNext)
        if (link->sType == VK_STRUCTURE_TYPE_MEMORY_DEDICATED_REQUIREMENTS) {
            auto* dedicated = reinterpret_cast<VkMemoryDedicatedRequirements*>(link);
            dedicated->prefersDedicatedAllocation = dedicated->requiresDedicatedAllocation = VK_FALSE;
        }
}

VKAPI_ATTR void VKAPI_CALL getImageMemoryRequirements2(VkDevice device, const VkImageMemoryRequirementsInfo2* query,
                                                       VkMemoryRequirements2* requirements) {
    Device* d = findDevice(device);
    if (!d || !d->getImageMemoryRequirements2) return;
    d->getImageMemoryRequirements2(device, query, requirements);
    CompressedImageInfo* info = findImage(query->image, nullptr);
    if (info) {
        requirements->memoryRequirements = info->getMemoryRequirements();
        notDedicated(requirements);
    }
    traceRequirements("vkGetImageMemoryRequirements2", query->image, info, requirements->memoryRequirements);
}

// gfxstream's on_vkGetDeviceImageMemoryRequirements: the images made and measured, then destroyed
VKAPI_ATTR void VKAPI_CALL getDeviceImageMemoryRequirements(VkDevice device, const VkDeviceImageMemoryRequirements* query,
                                                            VkMemoryRequirements2* requirements) {
    Device* d = findDevice(device);
    if (!d || !d->getDeviceImageMemoryRequirements) return;
    d->getDeviceImageMemoryRequirements(device, query, requirements);
    if (!emulated({d->astc, d->etc2}, query->pCreateInfo->format)) {
        traceRequirements("vkGetDeviceImageMemoryRequirements", VK_NULL_HANDLE, false, requirements->memoryRequirements);
        return;
    }
    CompressedImageInfo info(device, *query->pCreateInfo, d->pipelines.get());
    VkImageCreateInfo output = info.getOutputCreateInfo(*query->pCreateInfo);
    Unlink chain(output.pNext, {VK_STRUCTURE_TYPE_IMAGE_FORMAT_LIST_CREATE_INFO});
    output.pNext = chain.head;
    VkImage image;
    if (d->vk.vkCreateImage(device, &output, nullptr, &image) != VK_SUCCESS) {
        TRACE("vkGetDeviceImageMemoryRequirements format %d: the output image was not created, the host's size %llu stays",
              query->pCreateInfo->format, (unsigned long long)requirements->memoryRequirements.size);
        return;
    }
    info.setOutputImage(image);
    info.createCompressedMipmapImages(&d->vk, output);
    requirements->memoryRequirements = info.getMemoryRequirements();
    notDedicated(requirements);
    TRACE("vkGetDeviceImageMemoryRequirements format %d %ux%u mips %u layers %u: size %llu alignment %llu types 0x%x",
          query->pCreateInfo->format, query->pCreateInfo->extent.width, query->pCreateInfo->extent.height, query->pCreateInfo->mipLevels,
          query->pCreateInfo->arrayLayers, (unsigned long long)requirements->memoryRequirements.size,
          (unsigned long long)requirements->memoryRequirements.alignment, requirements->memoryRequirements.memoryTypeBits);
    info.destroy(&d->vk);
}

VKAPI_ATTR VkResult VKAPI_CALL bindImageMemory(VkDevice device, VkImage image, VkDeviceMemory memory, VkDeviceSize offset) {
    VkResult result = host<PFN_vkBindImageMemory>("vkBindImageMemory")(device, image, memory, offset);
    Device* d = nullptr;
    CompressedImageInfo* info = findImage(image, &d);
    if (result != VK_SUCCESS || !info) return result;
    result = info->bindCompressedMipmapsMemory(&d->vk, memory, offset);
    TRACE("vkBindImageMemory image %p memory %p offset %llu: %d", (void*)image, (void*)memory, (unsigned long long)offset, result);
    return result;
}

VKAPI_ATTR VkResult VKAPI_CALL bindImageMemory2(VkDevice device, uint32_t count, const VkBindImageMemoryInfo* binds) {
    Device* d = findDevice(device);
    if (!d || !d->bindImageMemory2) return VK_ERROR_INITIALIZATION_FAILED;
    bool any = false;
    for (uint32_t i = 0; i < count && !any; i++) any = findImage(binds[i].image, nullptr) != nullptr;
    if (!any) return d->bindImageMemory2(device, count, binds);
    for (uint32_t i = 0; i < count; i++) {  // one at a time, as gfxstream's performBindImageMemory
        VkResult result = d->bindImageMemory2(device, 1, &binds[i]);
        CompressedImageInfo* info = findImage(binds[i].image, nullptr);
        if (result == VK_SUCCESS && info) {
            result = info->bindCompressedMipmapsMemory(&d->vk, binds[i].memory, binds[i].memoryOffset);
            TRACE("vkBindImageMemory2 image %p memory %p offset %llu pNext %p: %d", (void*)binds[i].image, (void*)binds[i].memory,
                  (unsigned long long)binds[i].memoryOffset, binds[i].pNext, result);
        }
        if (result != VK_SUCCESS) return result;
    }
    return VK_SUCCESS;
}

// a view of the compressed format reads the decoded image; a view of an uncompressed format (block-texel compatible) the blocks
VKAPI_ATTR VkResult VKAPI_CALL createImageView(VkDevice device, const VkImageViewCreateInfo* create, const VkAllocationCallbacks* allocator,
                                               VkImageView* view) {
    auto fn = host<PFN_vkCreateImageView>("vkCreateImageView");
    Device* d = nullptr;
    CompressedImageInfo* info = findImage(create->image, &d);
    if (!info) return fn(device, create, allocator, view);
    VkImageViewCreateInfo ours = *create;
    Unlink chain(create->pNext, {VK_STRUCTURE_TYPE_IMAGE_VIEW_ASTC_DECODE_MODE_EXT});
    ours.pNext = chain.head;
    TRACE("vkCreateImageView image %p format %d type %d levels %u+%u layers %u+%u pNext %p", (void*)create->image, create->format,
          create->viewType, create->subresourceRange.baseMipLevel, create->subresourceRange.levelCount, create->subresourceRange.baseArrayLayer,
          create->subresourceRange.layerCount, create->pNext);
    if (emulated({d->astc, d->etc2}, create->format)) {
        ours.format = CompressedImageInfo::getOutputFormat(create->format);
    } else {
        ours.image = info->compressedMipmap(create->subresourceRange.baseMipLevel);
        ours.subresourceRange.baseMipLevel = 0;
        ours.subresourceRange.levelCount = 1;
    }
    return fn(device, &ours, allocator, view);
}

}  // namespace

PFN_vkVoidFunction instanceProc(const char* name) {
    static const struct { const char* name; PFN_vkVoidFunction function; } ours[] = {
        {"vkGetPhysicalDeviceFeatures", reinterpret_cast<PFN_vkVoidFunction>(getFeatures)},
        {"vkGetPhysicalDeviceFeatures2", reinterpret_cast<PFN_vkVoidFunction>(getFeatures2)},
        {"vkGetPhysicalDeviceFeatures2KHR", reinterpret_cast<PFN_vkVoidFunction>(getFeatures2)},
        {"vkGetPhysicalDeviceFormatProperties", reinterpret_cast<PFN_vkVoidFunction>(getFormatProperties)},
        {"vkGetPhysicalDeviceFormatProperties2", reinterpret_cast<PFN_vkVoidFunction>(getFormatProperties2)},
        {"vkGetPhysicalDeviceFormatProperties2KHR", reinterpret_cast<PFN_vkVoidFunction>(getFormatProperties2)},
        {"vkGetPhysicalDeviceImageFormatProperties", reinterpret_cast<PFN_vkVoidFunction>(getImageFormatProperties)},
        {"vkGetPhysicalDeviceImageFormatProperties2", reinterpret_cast<PFN_vkVoidFunction>(getImageFormatProperties2)},
        {"vkGetPhysicalDeviceImageFormatProperties2KHR", reinterpret_cast<PFN_vkVoidFunction>(getImageFormatProperties2)},
    };
    for (const auto& o : ours) {
        if (strcmp(name, o.name)) continue;
        std::string core(name);  // the wrappers call the core entry point (the instance is made at Vulkan 1.1 or later)
        if (core.size() > 3 && !core.compare(core.size() - 3, 3, "KHR")) core.resize(core.size() - 3);
        return hostSymbol(core.c_str()) ? o.function : nullptr;
    }
    return nullptr;
}

void prepare(VkPhysicalDevice physical, const VkDeviceCreateInfo* info, DeviceRequest* request) {
    Emulation e = emulation(physical);
    if (!e.astc && !e.etc2) return;
    auto clear = [&](const VkPhysicalDeviceFeatures* features) {
        auto* f = const_cast<VkPhysicalDeviceFeatures*>(features);
        if (e.astc && f->textureCompressionASTC_LDR) { f->textureCompressionASTC_LDR = VK_FALSE; request->cleared[request->count++] = &f->textureCompressionASTC_LDR; }
        if (e.etc2 && f->textureCompressionETC2) { f->textureCompressionETC2 = VK_FALSE; request->cleared[request->count++] = &f->textureCompressionETC2; }
    };
    if (info->pEnabledFeatures) clear(info->pEnabledFeatures);
    for (auto* link = static_cast<const VkBaseInStructure*>(info->pNext); link && request->count < 6; link = link->pNext)
        if (link->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2) clear(&reinterpret_cast<const VkPhysicalDeviceFeatures2*>(link)->features);
}

void created(VkPhysicalDevice physical, VkDevice device, DeviceRequest* request, VkResult result) {
    for (int i = 0; i < request->count; i++) *request->cleared[i] = VK_TRUE;
    Emulation e = emulation(physical);
    if (result != VK_SUCCESS || (!e.astc && !e.etc2)) return;
    auto proc = host<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr");
    auto d = std::make_unique<Device>();
    d->device = device;
    d->physical = physical;
    d->astc = e.astc;
    d->etc2 = e.etc2;
    auto get = [&](const char* name, const char* alias = nullptr) {
        PFN_vkVoidFunction fn = proc(device, name);
        return fn || !alias ? fn : proc(device, alias);
    };
#define APKLAYER_VK_FILL(name) d->vk.name = reinterpret_cast<PFN_##name>(get(#name));
    APKLAYER_VK_DISPATCH(APKLAYER_VK_FILL)
#undef APKLAYER_VK_FILL
    d->getImageMemoryRequirements2 = reinterpret_cast<PFN_vkGetImageMemoryRequirements2>(get("vkGetImageMemoryRequirements2", "vkGetImageMemoryRequirements2KHR"));
    d->getDeviceImageMemoryRequirements = reinterpret_cast<PFN_vkGetDeviceImageMemoryRequirements>(
        get("vkGetDeviceImageMemoryRequirements", "vkGetDeviceImageMemoryRequirementsKHR"));
    d->bindImageMemory2 = reinterpret_cast<PFN_vkBindImageMemory2>(get("vkBindImageMemory2", "vkBindImageMemory2KHR"));
    d->copyBufferToImage2 = reinterpret_cast<PFN_vkCmdCopyBufferToImage2>(get("vkCmdCopyBufferToImage2", "vkCmdCopyBufferToImage2KHR"));
    d->copyImage2 = reinterpret_cast<PFN_vkCmdCopyImage2>(get("vkCmdCopyImage2", "vkCmdCopyImage2KHR"));
    d->copyImageToBuffer2 = reinterpret_cast<PFN_vkCmdCopyImageToBuffer2>(get("vkCmdCopyImageToBuffer2", "vkCmdCopyImageToBuffer2KHR"));
    d->pipelineBarrier2 = reinterpret_cast<PFN_vkCmdPipelineBarrier2>(get("vkCmdPipelineBarrier2", "vkCmdPipelineBarrier2KHR"));
    d->pipelines = std::make_unique<GpuDecompressionPipelineManager>(&d->vk, device);
    std::lock_guard<std::mutex> lock(gLock);
    gDevices[device] = std::move(d);
}

void destroyed(VkDevice device) {
    std::unique_ptr<Device> d;
    std::vector<std::unique_ptr<CompressedImageInfo>> leftovers;
    {
        std::lock_guard<std::mutex> lock(gLock);
        auto it = gDevices.find(device);
        if (it == gDevices.end()) return;
        d = std::move(it->second);
        gDevices.erase(it);
        for (auto image = gImages.begin(); image != gImages.end();) {
            if (image->second.device == d.get()) {
                leftovers.push_back(std::move(image->second.info));
                image = gImages.erase(image);
                gEmulatedImages--;
            } else ++image;
        }
        forgetCommands(d.get());
    }
    for (auto& info : leftovers) info->destroy(&d->vk);  // images the app did not destroy (a device may only be destroyed without any)
    d->pipelines->clear();
}

bool createImage(VkDevice device, const VkImageCreateInfo* create, VkImage* image, VkResult* result) {
    Device* d = findDevice(device);
    if (!d || !emulated({d->astc, d->etc2}, create->format)) return false;
    auto info = std::make_unique<CompressedImageInfo>(device, *create, d->pipelines.get());
    VkImageCreateInfo output = info->getOutputCreateInfo(*create);
    // a format list naming the compressed format does not fit the decoded image: dropped (MUTABLE_FORMAT allows the views made here)
    Unlink chain(create->pNext, {VK_STRUCTURE_TYPE_IMAGE_FORMAT_LIST_CREATE_INFO});
    output.pNext = chain.head;
    // no app allocator: the block images and gfxstream's destroy() use none, and the output image goes with them
    *result = d->vk.vkCreateImage(device, &output, nullptr, image);
    TRACE("vkCreateImage format %d -> %d, %ux%ux%u mips %u layers %u usage 0x%x flags 0x%x type %d tiling %d: %d image %p", create->format,
          output.format, create->extent.width, create->extent.height, create->extent.depth, create->mipLevels, create->arrayLayers, create->usage,
          create->flags, create->imageType, create->tiling, *result, *result == VK_SUCCESS ? (void*)*image : nullptr);
    if (*result != VK_SUCCESS) return true;
    info->setOutputImage(*image);
    info->createCompressedMipmapImages(&d->vk, output);
    std::lock_guard<std::mutex> lock(gLock);
    gImages[*image] = {d, std::move(info)};
    gEmulatedImages++;
    return true;
}

bool destroyImage(VkDevice, VkImage image) {
    std::unique_ptr<CompressedImageInfo> info;
    Device* d;
    {
        std::lock_guard<std::mutex> lock(gLock);
        auto it = gImages.find(image);
        if (it == gImages.end()) return false;
        TRACE("vkDestroyImage image %p", (void*)image);
        d = it->second.device;
        info = std::move(it->second.info);
        gImages.erase(it);
        gEmulatedImages--;
    }
    info->destroy(&d->vk);  // the block images, the decoder's views and descriptors, and the output image
    return true;
}

PFN_vkVoidFunction deviceProc(VkDevice device, const char* name) {
    Device* d = findDevice(device);
    if (!d) return nullptr;
    static const struct { const char* name; PFN_vkVoidFunction function; } ours[] = {
        {"vkGetImageMemoryRequirements", reinterpret_cast<PFN_vkVoidFunction>(getImageMemoryRequirements)},
        {"vkGetImageMemoryRequirements2", reinterpret_cast<PFN_vkVoidFunction>(getImageMemoryRequirements2)},
        {"vkGetImageMemoryRequirements2KHR", reinterpret_cast<PFN_vkVoidFunction>(getImageMemoryRequirements2)},
        {"vkGetDeviceImageMemoryRequirements", reinterpret_cast<PFN_vkVoidFunction>(getDeviceImageMemoryRequirements)},
        {"vkGetDeviceImageMemoryRequirementsKHR", reinterpret_cast<PFN_vkVoidFunction>(getDeviceImageMemoryRequirements)},
        {"vkBindImageMemory", reinterpret_cast<PFN_vkVoidFunction>(bindImageMemory)},
        {"vkBindImageMemory2", reinterpret_cast<PFN_vkVoidFunction>(bindImageMemory2)},
        {"vkBindImageMemory2KHR", reinterpret_cast<PFN_vkVoidFunction>(bindImageMemory2)},
        {"vkCreateImageView", reinterpret_cast<PFN_vkVoidFunction>(createImageView)},
    };
    auto proc = host<PFN_vkGetDeviceProcAddr>("vkGetDeviceProcAddr");
    for (const auto& o : ours)
        if (!strcmp(name, o.name)) return proc(device, name) ? o.function : nullptr;  // only what the host device has
    return commandProc(device, name);
}

}  // namespace apklayer::vulkan::textures
