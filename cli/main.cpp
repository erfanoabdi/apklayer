// apklayer: run Android apps on Linux. install | run | list | uninstall | stop | hub | component-host | env (see README.md).
#include <cstdio>
#include <cstring>
#include <unistd.h>

#include "run.h"

using namespace apklayer;

namespace {
int usage() {
    fputs("usage: apklayer [--instance ID] <command>\n"
          "  install <app.apk>      convert + describe + install (menu entry, icon, .apk handler)\n"
          "  run <package|app.apk>  run an installed app (an .apk path is installed first)\n"
          "  list                   installed apps\n"
          "  uninstall <package>    remove an app and its data\n"
          "  stop                   stop the instance's Android services (apps end with them)\n"
          "  env                    print the environment of Android's processes\n",
          stderr);
    return 2;
}
}  // namespace

int main(int argc, char** argv) {
    std::vector<std::string> args(argv + 1, argv + argc);
    std::string id = getenvOr("APKLAYER_INSTANCE", "default");
    if (args.size() >= 2 && args[0] == "--instance") { id = args[1]; args.erase(args.begin(), args.begin() + 2); }
    if (args.empty()) return usage();
    Layout layout = Layout::detect();
    Instance inst = Instance::open(layout, id);
    const std::string& cmd = args[0];
    size_t n = args.size();
    if (cmd == "install" && n == 2) { installApp(layout, inst, args[1]); return 0; }
    if (cmd == "run" && n == 2) {
        std::string target = args[1];
        if (exists(target)) target = installApp(layout, inst, target);
        return runApp(layout, inst, target, RunMode::App);
    }
    if (cmd == "component-host" && n == 2) return runApp(layout, inst, args[1], RunMode::Component);
    if (cmd == "hub") return runHub(layout, inst);
    if (cmd == "list") { listApps(layout); return 0; }
    if (cmd == "uninstall" && n == 2) { uninstallApp(layout, args[1]); return 0; }
    if (cmd == "stop") { stopStack(layout, inst); return 0; }
    if (cmd == "stack-daemon") return runStackDaemon(layout, inst);
    // test harness: the services plus one tool in the foreground (ends with the tool); exec: one library under the environment
    if (cmd == "stack" && n >= 2) return runStackDaemon(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "exec" && n >= 2) return execTool(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "env") {
        NativeEnv ne = nativeEnv(layout, inst);
        hostJvmArgs(layout, ne);
        for (const auto& [k, v] : ne.env) printf("%s=%s\n", k.c_str(), v.c_str());
        return 0;
    }
    return usage();
}
