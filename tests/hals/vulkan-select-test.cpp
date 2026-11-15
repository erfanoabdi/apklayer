// The instance-GPU Vulkan filter (aosp/hals/graphics/vulkan/DeviceSelect.h): a fake device list (always), then the host's real Vulkan loader
// (`real`: Intel + llvmpipe on the PC). The "node" is a character device whose rdev the fake DRM properties name.
#include "../../aosp/hals/graphics/vulkan/DeviceSelect.h"
#include <dlfcn.h>
#include <cassert>

using namespace apklayer::vulkan;
struct Fake { VkPhysicalDeviceType type; bool drm; uint32_t major, minor; };
static std::vector<Fake> devs;
static VkPhysicalDevice h(size_t i) { return reinterpret_cast<VkPhysicalDevice>(i + 1); }
static VkResult enumDevs(VkInstance, uint32_t* n, VkPhysicalDevice* o) {
    if (o) for (size_t i = 0; i < *n && i < devs.size(); i++) o[i] = h(i);
    *n = devs.size(); return VK_SUCCESS;
}
static VkResult enumExt(VkPhysicalDevice d, const char*, uint32_t* n, VkExtensionProperties* o) {
    if (!devs[reinterpret_cast<size_t>(d) - 1].drm) { *n = 0; return VK_SUCCESS; }
    if (o) strcpy(o[0].extensionName, VK_EXT_PHYSICAL_DEVICE_DRM_EXTENSION_NAME);
    *n = 1; return VK_SUCCESS;
}
static void props2(VkPhysicalDevice d, VkPhysicalDeviceProperties2* p) {
    const Fake& f = devs[reinterpret_cast<size_t>(d) - 1];
    p->properties.deviceType = f.type;
    if (p->pNext) { auto* drm = static_cast<VkPhysicalDeviceDrmPropertiesEXT*>(p->pNext); drm->hasRender = VK_TRUE; drm->renderMajor = f.major; drm->renderMinor = f.minor; }
}
static PFN_vkVoidFunction fakeGipa(VkInstance, const char* n) {
    if (!strcmp(n, "vkEnumeratePhysicalDevices")) return reinterpret_cast<PFN_vkVoidFunction>(enumDevs);
    if (!strcmp(n, "vkEnumerateDeviceExtensionProperties")) return reinterpret_cast<PFN_vkVoidFunction>(enumExt);
    if (!strcmp(n, "vkGetPhysicalDeviceProperties2")) return reinterpret_cast<PFN_vkVoidFunction>(props2);
    return nullptr;
}
static std::vector<VkPhysicalDevice> pick(PFN_vkGetInstanceProcAddr g, VkInstance i, const char* node) {
    setenv("APKLAYER_RENDER_NODE", node, 1);
    std::vector<VkPhysicalDevice> v;
    VkResult r = visibleDevices(g, i, &v);
    assert(r == VK_SUCCESS);
    return v;
}

int main(int argc, char** argv) {
    // /dev/null is 1:3 and /dev/zero 1:5: stand-ins for two render nodes
    const VkPhysicalDeviceType GPU = VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU, CPU = VK_PHYSICAL_DEVICE_TYPE_CPU;
    devs = {{GPU, true, 1, 3}, {GPU, true, 1, 5}, {CPU, false, 0, 0}};
    auto v = pick(fakeGipa, nullptr, "/dev/null"); assert(v.size() == 1 && v[0] == h(0));
    v = pick(fakeGipa, nullptr, "/dev/zero"); assert(v.size() == 1 && v[0] == h(1));
    v = pick(fakeGipa, nullptr, "/dev/full"); assert(v.size() == 1 && v[0] == h(2));       // no Vulkan driver on that node: the CPU device
    v = pick(fakeGipa, nullptr, "/dev/nonexistent"); assert(v.size() == 3);               // unusable node: nothing filtered
    unsetenv("APKLAYER_RENDER_NODE"); v.clear();
    assert(visibleDevices(fakeGipa, nullptr, &v) == VK_SUCCESS && v.size() == 3);
    devs = {{GPU, false, 0, 0}, {GPU, true, 1, 5}};
    v = pick(fakeGipa, nullptr, "/dev/zero"); assert(v.size() == 2);                      // a GPU without the extension: everything stays
    uint32_t n = 0;
    assert(enumeratePhysicalDevices(fakeGipa, nullptr, &n, nullptr) == VK_SUCCESS && n == 2);
    puts("fake device list: PASS");
    if (argc < 2 || strcmp(argv[1], "real")) return 0;

    void* lib = dlopen("libvulkan.so.1", RTLD_NOW);
    assert(lib);
    auto gipa = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(lib, "vkGetInstanceProcAddr"));
    auto create = reinterpret_cast<PFN_vkCreateInstance>(gipa(nullptr, "vkCreateInstance"));
    VkApplicationInfo app = {VK_STRUCTURE_TYPE_APPLICATION_INFO}; app.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo ci = {VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO}; ci.pApplicationInfo = &app;
    VkInstance inst;
    assert(create(&ci, nullptr, &inst) == VK_SUCCESS);
    auto p2 = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties2>(gipa(inst, "vkGetPhysicalDeviceProperties2"));
    auto type = [&](VkPhysicalDevice d) { VkPhysicalDeviceProperties2 p = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2}; p2(d, &p); return p.properties.deviceType; };
    auto all = pick(gipa, inst, "/dev/nonexistent");
    printf("host Vulkan devices: %zu\n", all.size());
    assert(all.size() >= 2);
    const char* gpuNode = argc > 2 ? argv[2] : "/dev/dri/renderD128";
    v = pick(gipa, inst, gpuNode);
    assert(v.size() == 1 && type(v[0]) != CPU);
    uint32_t cnt = 0;
    assert(enumeratePhysicalDevices(gipa, inst, &cnt, nullptr) == VK_SUCCESS && cnt == 1);
    v = pick(gipa, inst, "/dev/null");                                                     // a node no driver serves: llvmpipe only
    assert(v.size() == 1 && type(v[0]) == CPU);
    puts("real loader: PASS (render node -> its GPU only, other node -> CPU device)");
}
