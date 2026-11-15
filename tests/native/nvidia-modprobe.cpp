#include <cstdio>
#include "../../cli/nvidia.h"
// cli/nvidia.h nvidiaModprobeArgs: which nvidia-modprobe call makes which NVIDIA node (the broker runs it for a node that does not exist yet).
// Build: g++ -std=c++20 tests/native/nvidia-modprobe.cpp
int main() {
    using apklayer::nvidiaModprobeArgs;
    using V = std::vector<std::string>;
    bool ok = nvidiaModprobeArgs("/dev/nvidia-uvm") == V{"-u", "-c=0"} && nvidiaModprobeArgs("/dev/nvidia-uvm-tools") == V{"-u", "-c=0"} &&
              nvidiaModprobeArgs("/dev/nvidia-modeset") == V{"-m"} && nvidiaModprobeArgs("/dev/nvidiactl") == V{"-c=255"} &&
              nvidiaModprobeArgs("/dev/nvidia1") == V{"-c=1"} && nvidiaModprobeArgs("/dev/nvidia").empty() && nvidiaModprobeArgs("/dev/nvidia-caps/x").empty() &&
              nvidiaModprobeArgs("/dev/dri/card0").empty() && nvidiaModprobeArgs("/dev/nvidia1x").empty();
    // a Debian-style box (nodes 0660 root:video, the driver makes only nvidiactl and nvidia0, a second GPU, the -caps folder): each missing node maps to
    // the call that makes exactly it (-c=<minor>, also two digits), the rest of /dev/nvidia* to none
    const struct { const char* node; V args; } debian[] = {{"/dev/nvidia0", {"-c=0"}}, {"/dev/nvidia3", {"-c=3"}}, {"/dev/nvidia10", {"-c=10"}},
        {"/dev/nvidia-uvm", {"-u", "-c=0"}}, {"/dev/nvidia-modeset", {"-m"}}, {"/dev/nvidia-caps", {}}, {"/dev/nvidia-caps/nvidia-cap1", {}}, {"/dev/nvidia1234", {}}, {"/dev/nvidiaX", {}}, {"/dev/kfd", {}}};
    for (const auto& d : debian)
        if (nvidiaModprobeArgs(d.node) != d.args) { printf("FAIL %s\n", d.node); ok = false; }
    printf("%s\n", ok ? "PASS" : "FAIL");
    return ok ? 0 : 1;
}
