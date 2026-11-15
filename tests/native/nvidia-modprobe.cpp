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
    printf("%s\n", ok ? "PASS" : "FAIL");
    return ok ? 0 : 1;
}
