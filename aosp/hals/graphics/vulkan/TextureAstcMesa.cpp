// ASTC decode with Mesa's compute shader (APKLAYER_VULKAN_ASTC_DECODER=mesa). mesa_astc/astc_decoder.glsl is Mesa's src/compiler/glsl/astc_decoder.glsl (MIT,
// Hans-Kristian Arntzen's Granite decoder) compiled with glslangValidator exactly as Mesa's src/vulkan/runtime/meson.build does (-V -S comp -x; see
// mesa_astc/README); the lookup tables are Granite's (mesa_astc/texcompress_astc_luts.*). What it does is vk_texcompress_astc.c's: one pipeline per block
// size (specialization: block width, height, unorm8), the five LUTs and one partition table as uniform texel buffers, the block image of a mip as the sampled
// payload and the output image's mip (an R8G8B8A8_UINT view) as the storage image; push constants {texel start in blocks, texel end, is_3D}; one workgroup
// decodes 2x2 blocks. gfxstream's CompressedImageInfo does the barriers around it (the block images and the output are in GENERAL while this runs).
#include "TextureState.h"

#include <cstdint>
#include <cstring>
#include <map>
#include <tuple>
#include <vector>

#include "mesa_astc/texcompress_astc_luts.h"

namespace apklayer::vulkan::textures {
namespace {

using gfxstream::host::vk::AstcDecoder;
using gfxstream::host::vk::MesaAstcHooks;

const uint32_t kSpirv[] = {
#include "mesa_astc/astc_spv.h"
};

constexpr int kLuts = 5;
constexpr int kTables = 14;

struct Vk {
    PFN_vkCreateBuffer createBuffer = host<PFN_vkCreateBuffer>("vkCreateBuffer");
    PFN_vkDestroyBuffer destroyBuffer = host<PFN_vkDestroyBuffer>("vkDestroyBuffer");
    PFN_vkGetBufferMemoryRequirements bufferRequirements = host<PFN_vkGetBufferMemoryRequirements>("vkGetBufferMemoryRequirements");
    PFN_vkAllocateMemory allocate = host<PFN_vkAllocateMemory>("vkAllocateMemory");
    PFN_vkFreeMemory free = host<PFN_vkFreeMemory>("vkFreeMemory");
    PFN_vkBindBufferMemory bindBuffer = host<PFN_vkBindBufferMemory>("vkBindBufferMemory");
    PFN_vkMapMemory map = host<PFN_vkMapMemory>("vkMapMemory");
    PFN_vkUnmapMemory unmap = host<PFN_vkUnmapMemory>("vkUnmapMemory");
    PFN_vkCreateBufferView createView = host<PFN_vkCreateBufferView>("vkCreateBufferView");
    PFN_vkDestroyBufferView destroyView = host<PFN_vkDestroyBufferView>("vkDestroyBufferView");
    PFN_vkGetPhysicalDeviceMemoryProperties memoryProperties = host<PFN_vkGetPhysicalDeviceMemoryProperties>("vkGetPhysicalDeviceMemoryProperties");
    PFN_vkGetPhysicalDeviceProperties physicalProperties = host<PFN_vkGetPhysicalDeviceProperties>("vkGetPhysicalDeviceProperties");
    PFN_vkCreateImageView createImageView = host<PFN_vkCreateImageView>("vkCreateImageView");
    PFN_vkDestroyImageView destroyImageView = host<PFN_vkDestroyImageView>("vkDestroyImageView");
    PFN_vkCreateDescriptorSetLayout createSetLayout = host<PFN_vkCreateDescriptorSetLayout>("vkCreateDescriptorSetLayout");
    PFN_vkCreatePipelineLayout createPipelineLayout = host<PFN_vkCreatePipelineLayout>("vkCreatePipelineLayout");
    PFN_vkCreateShaderModule createShader = host<PFN_vkCreateShaderModule>("vkCreateShaderModule");
    PFN_vkCreateComputePipelines createPipelines = host<PFN_vkCreateComputePipelines>("vkCreateComputePipelines");
    PFN_vkCreateDescriptorPool createPool = host<PFN_vkCreateDescriptorPool>("vkCreateDescriptorPool");
    PFN_vkDestroyDescriptorPool destroyPool = host<PFN_vkDestroyDescriptorPool>("vkDestroyDescriptorPool");
    PFN_vkAllocateDescriptorSets allocateSets = host<PFN_vkAllocateDescriptorSets>("vkAllocateDescriptorSets");
    PFN_vkUpdateDescriptorSets updateSets = host<PFN_vkUpdateDescriptorSets>("vkUpdateDescriptorSets");
    PFN_vkCmdBindPipeline bindPipeline = host<PFN_vkCmdBindPipeline>("vkCmdBindPipeline");
    PFN_vkCmdBindDescriptorSets bindSets = host<PFN_vkCmdBindDescriptorSets>("vkCmdBindDescriptorSets");
    PFN_vkCmdPushConstants push = host<PFN_vkCmdPushConstants>("vkCmdPushConstants");
    PFN_vkCmdDispatch dispatch = host<PFN_vkCmdDispatch>("vkCmdDispatch");
};
const Vk& vk() { static const Vk v; return v; }

struct DeviceState {
    bool failed = false, ready = false;
    VkBuffer buffer = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkBufferView luts[kLuts] = {};
    VkBufferView tables[kTables] = {};
    VkDescriptorSetLayout setLayout = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkShaderModule shader = VK_NULL_HANDLE;
    VkPipeline pipelines[kTables] = {};
};

struct ImageState {
    VkDescriptorPool pool = VK_NULL_HANDLE;
    std::map<std::tuple<uint32_t, uint32_t, uint32_t>, VkDescriptorSet> sets;  // (mip, first layer, layers)
    std::vector<VkImageView> views;
};

std::mutex gMesaLock;
std::map<VkDevice, DeviceState> gDeviceStates;  // never erased: the device's resources die with the device
std::map<gfxstream::host::vk::CompressedImageInfo*, ImageState> gImageStates;

int tableIndex(uint32_t w, uint32_t h) {
    static const uint32_t sizes[kTables][2] = {{4, 4}, {5, 4}, {5, 5}, {6, 5}, {6, 6}, {8, 5}, {8, 6}, {8, 8}, {10, 5}, {10, 6}, {10, 8}, {10, 10}, {12, 10}, {12, 12}};
    for (int i = 0; i < kTables; i++) if (sizes[i][0] == w && sizes[i][1] == h) return i;
    return -1;
}

// the LUT buffer's memory: host visible and coherent, as vk_texcompress_astc.c makes it
bool makeLuts(Device* d, DeviceState& s) {
    const Vk& v = vk();
    Granite::ASTCLutHolder& holder = Granite::get_astc_luts();
    struct Lut { const void* data; size_t size; VkFormat format; } luts[kLuts] = {
        {holder.color_endpoint.lut, sizeof(holder.color_endpoint.lut), VK_FORMAT_R16G16B16A16_UINT},
        {holder.color_endpoint.unquant_lut, holder.color_endpoint.unquant_offset, VK_FORMAT_R8_UINT},
        {holder.weights.lut, sizeof(holder.weights.lut), VK_FORMAT_R8G8B8A8_UINT},
        {holder.weights.unquant_lut, holder.weights.unquant_offset, VK_FORMAT_R8_UINT},
        {holder.integer.trits_quints, sizeof(holder.integer.trits_quints), VK_FORMAT_R16_UINT},
    };
    VkPhysicalDeviceProperties props;
    v.physicalProperties(d->physical, &props);
    const VkDeviceSize align = std::max<VkDeviceSize>(props.limits.minTexelBufferOffsetAlignment, 4);
    auto up = [&](VkDeviceSize n) { return (n + align - 1) / align * align; };
    VkDeviceSize offsets[kLuts + kTables], sizes[kLuts + kTables], total = 0;
    const void* data[kLuts + kTables];
    for (int i = 0; i < kLuts; i++) { total = up(total); offsets[i] = total; sizes[i] = luts[i].size; data[i] = luts[i].data; total += luts[i].size; }
    static const uint32_t blocks[kTables][2] = {{4, 4}, {5, 4}, {5, 5}, {6, 5}, {6, 6}, {8, 5}, {8, 6}, {8, 8}, {10, 5}, {10, 6}, {10, 8}, {10, 10}, {12, 10}, {12, 12}};
    for (int i = 0; i < kTables; i++) {
        auto& table = holder.get_partition_table(blocks[i][0], blocks[i][1]);
        total = up(total);
        offsets[kLuts + i] = total;
        sizes[kLuts + i] = table.lut_width * table.lut_height;
        data[kLuts + i] = table.lut_buffer.data();
        total += sizes[kLuts + i];
    }
    VkBufferCreateInfo bi = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bi.size = total;
    bi.usage = VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT;
    bi.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (v.createBuffer(d->device, &bi, nullptr, &s.buffer) != VK_SUCCESS) return false;
    VkMemoryRequirements req;
    v.bufferRequirements(d->device, s.buffer, &req);
    VkPhysicalDeviceMemoryProperties mem;
    v.memoryProperties(d->physical, &mem);
    const VkMemoryPropertyFlags want = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    uint32_t type = ~0u;
    for (uint32_t i = 0; i < mem.memoryTypeCount; i++)
        if ((req.memoryTypeBits & (1u << i)) && (mem.memoryTypes[i].propertyFlags & want) == want) { type = i; break; }
    if (type == ~0u) return false;
    VkMemoryAllocateInfo ai = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, req.size, type};
    if (v.allocate(d->device, &ai, nullptr, &s.memory) != VK_SUCCESS || v.bindBuffer(d->device, s.buffer, s.memory, 0) != VK_SUCCESS) return false;
    uint8_t* ptr = nullptr;
    if (v.map(d->device, s.memory, 0, VK_WHOLE_SIZE, 0, reinterpret_cast<void**>(&ptr)) != VK_SUCCESS) return false;
    for (int i = 0; i < kLuts + kTables; i++) memcpy(ptr + offsets[i], data[i], sizes[i]);
    v.unmap(d->device, s.memory);
    for (int i = 0; i < kLuts + kTables; i++) {
        VkBufferViewCreateInfo vi = {VK_STRUCTURE_TYPE_BUFFER_VIEW_CREATE_INFO};
        vi.buffer = s.buffer;
        vi.format = i < kLuts ? luts[i].format : VK_FORMAT_R8_UINT;
        vi.offset = offsets[i];
        vi.range = sizes[i];
        if (v.createView(d->device, &vi, nullptr, i < kLuts ? &s.luts[i] : &s.tables[i - kLuts]) != VK_SUCCESS) return false;
    }
    return true;
}

bool makeLayouts(Device* d, DeviceState& s) {
    const Vk& v = vk();
    VkDescriptorSetLayoutBinding bindings[8] = {};
    for (uint32_t i = 0; i < 8; i++) {
        bindings[i].binding = i;
        bindings[i].descriptorType = i == 0 ? VK_DESCRIPTOR_TYPE_STORAGE_IMAGE : i == 1 ? VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE : VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
        bindings[i].descriptorCount = 1;
        bindings[i].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    }
    VkDescriptorSetLayoutCreateInfo li = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    li.bindingCount = 8;
    li.pBindings = bindings;
    if (v.createSetLayout(d->device, &li, nullptr, &s.setLayout) != VK_SUCCESS) return false;
    VkPushConstantRange range = {VK_SHADER_STAGE_COMPUTE_BIT, 0, 20};
    VkPipelineLayoutCreateInfo pl = {VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    pl.setLayoutCount = 1;
    pl.pSetLayouts = &s.setLayout;
    pl.pushConstantRangeCount = 1;
    pl.pPushConstantRanges = &range;
    if (v.createPipelineLayout(d->device, &pl, nullptr, &s.pipelineLayout) != VK_SUCCESS) return false;
    VkShaderModuleCreateInfo si = {VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
    si.codeSize = sizeof kSpirv;
    si.pCode = kSpirv;
    return v.createShader(d->device, &si, nullptr, &s.shader) == VK_SUCCESS;
}

VkPipeline pipelineFor(Device* d, DeviceState& s, uint32_t bw, uint32_t bh) {
    int t = tableIndex(bw, bh);
    if (t < 0) return VK_NULL_HANDLE;
    if (s.pipelines[t]) return s.pipelines[t];
    uint32_t data[3] = {bw, bh, 1};
    VkSpecializationMapEntry entries[3] = {{0, 0, 4}, {1, 4, 4}, {2, 8, 4}};
    VkSpecializationInfo spec = {3, entries, sizeof data, data};
    VkComputePipelineCreateInfo ci = {VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
    ci.stage = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, nullptr, 0, VK_SHADER_STAGE_COMPUTE_BIT, s.shader, "main", &spec};
    ci.layout = s.pipelineLayout;
    if (vk().createPipelines(d->device, VK_NULL_HANDLE, 1, &ci, nullptr, &s.pipelines[t]) != VK_SUCCESS) s.pipelines[t] = VK_NULL_HANDLE;
    return s.pipelines[t];
}

VkImageView makeView(VkDevice device, VkImage image, VkFormat format, uint32_t mip, uint32_t layer, uint32_t layers) {
    VkImageViewCreateInfo vi = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    vi.image = image;
    vi.viewType = VK_IMAGE_VIEW_TYPE_2D_ARRAY;
    vi.format = format;
    vi.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, mip, 1, layer, layers};
    VkImageView view = VK_NULL_HANDLE;
    return vk().createImageView(device, &vi, nullptr, &view) == VK_SUCCESS ? view : VK_NULL_HANDLE;
}

bool decode(gfxstream::host::vk::CompressedImageInfo* info, gfxstream::host::vk::VulkanDispatch*, VkCommandBuffer cmd, const VkImageSubresourceRange& requested) {
    using std::get;
    Device* d = findDevice(info->device());
    if (!d || info->imageType() != VK_IMAGE_TYPE_2D) return false;
    const VkExtent2D block = info->blockSize();
    if (tableIndex(block.width, block.height) < 0) return false;
    const uint32_t levels = requested.levelCount == VK_REMAINING_MIP_LEVELS ? info->mipLevels() - requested.baseMipLevel : requested.levelCount;
    const uint32_t layers = requested.layerCount == VK_REMAINING_ARRAY_LAYERS ? info->layerCount() - requested.baseArrayLayer : requested.layerCount;
    const Vk& v = vk();
    std::lock_guard<std::mutex> lock(gMesaLock);
    DeviceState& s = gDeviceStates[d->device];
    if (s.failed) return false;
    if (!s.ready) {
        if (!makeLuts(d, s) || !makeLayouts(d, s)) { s.failed = true; GFXSTREAM_WARNING("Mesa ASTC decoder: setup failed, using the gfxstream shader"); return false; }
        s.ready = true;
    }
    VkPipeline pipeline = pipelineFor(d, s, block.width, block.height);
    if (!pipeline) return false;
    ImageState& is = gImageStates[info];
    if (!is.pool) {
        const uint32_t maxSets = info->mipLevels() * 8;
        VkDescriptorPoolSize sizes[3] = {{VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, maxSets}, {VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, maxSets}, {VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER, maxSets * 6}};
        VkDescriptorPoolCreateInfo pi = {VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        pi.maxSets = maxSets;
        pi.poolSizeCount = 3;
        pi.pPoolSizes = sizes;
        if (v.createPool(d->device, &pi, nullptr, &is.pool) != VK_SUCCESS) return false;
    }
    // every set first, so a failure leaves the command buffer untouched for the fallback
    std::vector<VkDescriptorSet> chosen;
    for (uint32_t i = 0; i < levels; i++) {
        uint32_t mip = requested.baseMipLevel + i;
        auto key = std::make_tuple(mip, requested.baseArrayLayer, layers);
        auto it = is.sets.find(key);
        if (it == is.sets.end()) {
            VkDescriptorSetAllocateInfo ai = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO, nullptr, is.pool, 1, &s.setLayout};
            VkDescriptorSet set = VK_NULL_HANDLE;
            if (v.allocateSets(d->device, &ai, &set) != VK_SUCCESS) return false;
            VkImageView out = makeView(d->device, info->outputImage(), VK_FORMAT_R8G8B8A8_UINT, mip, requested.baseArrayLayer, layers);
            VkImageView in = makeView(d->device, info->compressedMipmap(mip), VK_FORMAT_R32G32B32A32_UINT, 0, requested.baseArrayLayer, layers);
            if (!out || !in) return false;
            is.views.push_back(out);
            is.views.push_back(in);
            VkDescriptorImageInfo outInfo = {VK_NULL_HANDLE, out, VK_IMAGE_LAYOUT_GENERAL}, inInfo = {VK_NULL_HANDLE, in, VK_IMAGE_LAYOUT_GENERAL};
            VkWriteDescriptorSet writes[8] = {};
            for (uint32_t b = 0; b < 8; b++) {
                writes[b].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
                writes[b].dstSet = set;
                writes[b].dstBinding = b;
                writes[b].descriptorCount = 1;
            }
            writes[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            writes[0].pImageInfo = &outInfo;
            writes[1].descriptorType = VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
            writes[1].pImageInfo = &inInfo;
            for (int l = 0; l < kLuts; l++) { writes[2 + l].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER; writes[2 + l].pTexelBufferView = &s.luts[l]; }
            writes[7].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
            writes[7].pTexelBufferView = &s.tables[tableIndex(block.width, block.height)];
            v.updateSets(d->device, 8, writes, 0, nullptr);
            it = is.sets.emplace(key, set).first;
        }
        chosen.push_back(it->second);
    }
    v.bindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
    for (uint32_t i = 0; i < levels; i++) {
        VkExtent3D e = info->mipExtent(requested.baseMipLevel + i);
        int32_t pc[5] = {0, 0, (int32_t)e.width, (int32_t)e.height, 0};
        v.push(cmd, s.pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof pc, pc);
        v.bindSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, s.pipelineLayout, 0, 1, &chosen[i], 0, nullptr);
        uint32_t bx = (e.width + block.width - 1) / block.width, by = (e.height + block.height - 1) / block.height;
        v.dispatch(cmd, (bx + 1) / 2, (by + 1) / 2, layers);
    }
    return true;
}

void forget(gfxstream::host::vk::CompressedImageInfo* info) {
    std::lock_guard<std::mutex> lock(gMesaLock);
    auto it = gImageStates.find(info);
    if (it == gImageStates.end()) return;
    for (VkImageView view : it->second.views) vk().destroyImageView(info->device(), view, nullptr);
    if (it->second.pool) vk().destroyPool(info->device(), it->second.pool, nullptr);
    gImageStates.erase(it);
}

}  // namespace

void installMesaAstc() {
    gfxstream::host::vk::gMesaAstcHooks.decode = decode;
    gfxstream::host::vk::gMesaAstcHooks.forget = forget;
}

}  // namespace apklayer::vulkan::textures
