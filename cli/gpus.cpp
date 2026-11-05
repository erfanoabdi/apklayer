#include "gpus.h"

#include <algorithm>
#include <cstdio>

namespace apklayer {

namespace {
// The compositor's GPU as a sysfs device path: the shell asks linux-dmabuf v4 for its main device (bridge/shell/feedback.c). Once per session.
std::string compositorDevice(const Layout& l) {
    std::string cache = join(l.runtimeHome, "apklayer-compositor-gpu");
    if (auto cached = readFile(cache)) return trim(*cached) == "-" ? "" : trim(*cached);
    std::string device;
    if (isExecutable(l.shellBinary())) {
        Spawn q;
        q.argv = {l.shellBinary(), "--compositor-gpu"};
        q.env = currentEnv();
        int status = 0;
        device = trim(runCapture(q, &status));
        if (status != 0) device.clear();
    }
    writeFile(cache, (device.empty() ? "-" : device) + "\n");  // "-": no Wayland or no dmabuf feedback (v3 compositor)
    return device;
}

std::string pciName(const std::string& vendorDevice) {
    if (vendorDevice.size() != 9) return "";
    std::string vendor = vendorDevice.substr(0, 4), device = vendorDevice.substr(5);
    for (const char* db : {"/usr/share/hwdata/pci.ids", "/usr/share/misc/pci.ids"}) {
        auto text = readFile(db);
        if (!text) continue;
        std::string vendorName;
        for (const std::string& line : split(*text, '\n')) {
            if (line.empty() || line[0] == '#') continue;
            if (line[0] != '\t') {
                if (!vendorName.empty()) return vendorName;  // past the vendor's devices: the device is not listed
                if (line.rfind(vendor + "  ", 0) == 0) vendorName = line.substr(6);
            } else if (!vendorName.empty() && line.size() > 7 && line[1] != '\t' && line.compare(1, 4, device) == 0) {
                return vendorName + " " + line.substr(7);
            }
        }
        if (!vendorName.empty()) return vendorName;
    }
    return "";
}

std::string hexId(const std::string& sysfsFile) {  // "0x8086\n" -> "8086"
    std::string v = trim(readFile(sysfsFile).value_or(""));
    return v.rfind("0x", 0) == 0 ? v.substr(2) : v;
}
}  // namespace

std::vector<Gpu> listGpus(const Layout& l) {
    std::vector<Gpu> gpus;
    std::string compositor = compositorDevice(l);
    std::vector<std::string> byPath = listDir("/dev/dri/by-path");
    for (const std::string& n : listDir("/sys/class/drm")) {
        if (n.rfind("renderD", 0) != 0) continue;
        Gpu g;
        g.node = "/dev/dri/" + n;
        g.device = realPath("/sys/class/drm/" + n + "/device");
        g.driver = baseName(realPath(join(g.device, "driver")));
        std::string vendor = hexId(join(g.device, "vendor")), device = hexId(join(g.device, "device"));
        if (exists(join(g.device, "config")) && !vendor.empty()) g.vendorDevice = vendor + ":" + device;  // PCI (config space): the ids name the chip
        for (const std::string& link : byPath)
            if (link.size() > 7 && link.substr(link.size() - 7) == "-render" && realPath("/dev/dri/by-path/" + link) == g.node)
                g.id = link.substr(0, link.size() - 7);
        if (g.id.empty()) g.id = g.device;
        g.compositor = !compositor.empty() && compositor == g.device;
        gpus.push_back(g);
    }
    std::sort(gpus.begin(), gpus.end(), [](const Gpu& a, const Gpu& b) { return a.node < b.node; });
    return gpus;
}

std::string gpuChoiceFile(const Instance& inst) { return join(inst.root, "gpu"); }

const Gpu* instanceGpu(const std::vector<Gpu>& gpus, const Instance& inst) {
    std::string want = getenvOr("APKLAYER_GPU", trim(readFile(gpuChoiceFile(inst)).value_or("")));
    if (!want.empty()) {
        for (const Gpu& g : gpus)
            if (want == g.id || want == g.node) return &g;
        fprintf(stderr, "apklayer: GPU %s of instance %s is not present: using the default\n", want.c_str(), inst.id.c_str());
    }
    for (const Gpu& g : gpus)
        if (g.compositor) return &g;
    return gpus.empty() ? nullptr : &gpus[0];
}

int gpusCommand(const Layout& l, const Instance& inst) {
    std::vector<Gpu> gpus = listGpus(l);
    const Gpu* chosen = instanceGpu(gpus, inst);
    printf("id\tnode\tdriver\tvendor:device\tcompositor\tinstance\tname\n");
    for (const Gpu& g : gpus) {
        std::string name = pciName(g.vendorDevice);
        printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\n", g.id.c_str(), g.node.c_str(), g.driver.c_str(), g.vendorDevice.empty() ? "-" : g.vendorDevice.c_str(),
               g.compositor ? "yes" : "no", &g == chosen ? "yes" : "no", (!name.empty() ? name : g.vendorDevice.empty() ? baseName(g.device) : g.driver).c_str());
    }
    return 0;
}

int displayDensity(const Layout& l) {
    if (!isExecutable(l.shellBinary())) return 0;
    Spawn q;
    q.argv = {l.shellBinary(), "--display-info"};
    q.env = currentEnv();
    int status = 0;
    std::string out = runCapture(q, &status);
    int density = 0;
    if (status != 0 || sscanf(out.c_str(), "density %d", &density) != 1) return 0;
    return density;
}

int gpuCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    if (args.size() == 1 && args[0] == "unset") {
        removeTree(gpuChoiceFile(inst));
        printf("apklayer: instance %s runs on the compositor's GPU (from its next start)\n", inst.id.c_str());
        return 0;
    }
    if (args.size() != 2 || args[0] != "set") die("gpu set <id|node> | gpu unset (ids: apklayer gpus)");
    for (const Gpu& g : listGpus(l))
        if (args[1] == g.id || args[1] == g.node) {
            writeFile(gpuChoiceFile(inst), g.id + "\n");  // the stable id, never renderD<n>
            printf("apklayer: instance %s runs on %s (%s) from its next start\n", inst.id.c_str(), g.id.c_str(), g.node.c_str());
            return 0;
        }
    die("no such GPU: " + args[1] + " (apklayer gpus)");
}

}  // namespace apklayer
