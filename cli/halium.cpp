#include "halium.h"

#include <cstdio>
#include <unistd.h>

#include "binder.h"
#include "stack.h"
#include "unit.h"

namespace apklayer {

bool isHalium() { return isDir("/android/system") && exists("/dev/binderfs/binder-control") && isDir("/vendor/lib64"); }

std::string defaultInstanceId() { return isHalium() ? "ut" : "default"; }

void haliumEnvDefaults(Env& env, const Layout& layout) {
    if (!isHalium() || env.count("PULSE_SERVER")) return;
    std::string socket = join(layout.runtimeHome, "pulse/native");
    if (exists(socket)) env["PULSE_SERVER"] = "unix:" + socket;
}

void startLomiriAppShell(const Layout& l, const std::string& id) {
    if (getenvOr("APP_ID", "").empty() || !getenvOr("APKLAYER_APP_SHELL", "").empty() || currentUnit().rfind("lomiri-app-launch--", 0) != 0) return;
    Instance inst = Instance::open(l, id);
    std::string socket = join(l.runtimeHome, "apklayer/shell-" + std::to_string(getpid()) + ".sock");  // the pid stays through enterOwnUnit
    makeDirs(dirName(socket));
    Spawn shell;
    shell.env = nativeEnv(l, inst).env;  // with APP_ID: Lomiri's app (the launch's unit: this process's, not yet its own scope)
    shell.env["APKLAYER_SHELL"] = socket;
    shell.logFile = join(inst.logs(), "shell-" + std::to_string(getpid()) + ".log");
    shell.dieWithParent = true;  // the app's card goes when its launch ends (Back, swipe-close, crash)
    if (startShell(l, shell)) setenv("APKLAYER_APP_SHELL", socket.c_str(), 1);  // cli/run.cpp: the launch's window sink and JVM use it
}

}  // namespace apklayer
