#include "halium.h"

#include <cstdio>
#include <unistd.h>

#include "binder.h"

namespace apklayer {

bool isHalium() { return isDir("/android/system") && exists("/dev/binderfs/binder-control") && isDir("/vendor/lib64"); }

std::string defaultInstanceId() { return isHalium() ? "ut" : "default"; }

void haliumEnvDefaults(Env& env, const Layout& layout) {
    if (!isHalium() || env.count("PULSE_SERVER")) return;
    std::string socket = join(layout.runtimeHome, "pulse/native");
    if (exists(socket)) env["PULSE_SERVER"] = "unix:" + socket;
}

}  // namespace apklayer
