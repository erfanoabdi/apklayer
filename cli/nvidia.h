#pragma once

#include <string>
#include <vector>

namespace apklayer {

// The arguments of the setuid nvidia-modprobe that makes the NVIDIA device node `path` (empty: not a node it makes). The kernel module makes only
// nvidiactl and nvidia0 when it loads; -uvm, -uvm-tools and -modeset appear when a client asks, so the first GL/Vulkan/CUDA user makes them. An app in
// the sandbox cannot run a setuid helper (no new privileges), apklayerd can: its broker (cli/broker.cpp) runs it and retries the open.
inline std::vector<std::string> nvidiaModprobeArgs(const std::string& path) {
    if (path.rfind("/dev/nvidia", 0) != 0) return {};
    const std::string name = path.substr(5);
    if (name == "nvidia-uvm" || name == "nvidia-uvm-tools") return {"-u", "-c=0"};
    if (name == "nvidia-modeset") return {"-m"};
    if (name == "nvidiactl") return {"-c=255"};
    const std::string minor = name.substr(6);
    if (!minor.empty() && minor.size() < 4 && minor.find_first_not_of("0123456789") == std::string::npos) return {"-c=" + minor};
    return {};
}

}  // namespace apklayer
