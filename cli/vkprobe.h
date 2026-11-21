#pragma once

namespace apklayer {
// The internal `vulkan-probe` command (cli/vkprobe.cpp): prints "<major>.<minor>[ 1]" of the best non-CPU Vulkan device, exit 1 without one.
int vulkanProbe();
}  // namespace apklayer
