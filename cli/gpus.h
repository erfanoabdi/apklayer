// The host's GPUs (DRM devices with a render node) and the one an instance runs on (docs/history/PLAN-2026-11-10.md "GPU per instance").
#pragma once

#include <string>
#include <vector>

#include "layout.h"

namespace apklayer {

struct Gpu {
    std::string id;            // stable identity: the /dev/dri/by-path name (pci-0000:03:00.0), else the sysfs device path (no by-path: vgem, some SoCs)
    std::string node;          // /dev/dri/renderD<n> (the number can change between boots: never stored)
    std::string device;        // sysfs device directory (realpath)
    std::string driver;        // kernel driver (i915, amdgpu, virtio-pci...)
    std::string vendorDevice;  // PCI vendor:device ("" off PCI)
    bool compositor = false;   // the desktop compositor renders on it (its linux-dmabuf main device)
};

std::vector<Gpu> listGpus(const Layout& layout);
// The instance's GPU: APKLAYER_GPU, else <instance root>/gpu, else the compositor's, else the first. nullptr: no render node at all.
const Gpu* instanceGpu(const std::vector<Gpu>& gpus, const Instance& instance);
std::string gpuChoiceFile(const Instance& instance);
// gpus: every GPU (id, node, driver, vendor:device, compositor, chosen for this instance, name); gpu set <id|node> | unset
// The density (dpi) apps get on the session's display, from the shell's `--display-info` (bridge/shell/mobile.c): a handheld's physical bucket, else
// 160 x the output's scale. 0: no shell or no Wayland. The stack's ro.sf.lcd_density.
int displayDensity(const Layout& layout);
int gpusCommand(const Layout& layout, const Instance& instance);
int gpuCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);

}  // namespace apklayer
