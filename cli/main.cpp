// apklayer: run Android apps on Linux. install | run | list | uninstall | screenshot | stop | hub | component-host | env (see README.md).
#include <cstdio>
#include <cstring>
#include <unistd.h>

#include "desktop.h"
#include "manage.h"
#include "migrate.h"
#include "run.h"
#include "spare.h"
#include "version.h"

using namespace apklayer;

namespace {
int usage() {
    fputs("usage: apklayer [--instance ID] <command>\n"
          "  install [-d] [--system] <app.apk> [split.apk...]  convert + describe + install (menu entry, icon, .apk handler); also .apks/.xapk/.apkm;\n"
          "                         an update keeps the data, needs the same signer and a version no lower (-d: allow a downgrade);\n"
          "                         --system: a system app (FLAG_SYSTEM, not in the app drawer unless shown)\n"
          "  reconvert <package> | --all-stale  convert an installed app again from its kept APKs (its data stays); --all-stale: every app converted\n"
          "                         with an older converter ABI (the stale column of list --tsv)\n"
          "  run <package|app.apk>  run an installed app (an .apk path is installed first)\n"
          "  list [--tsv]           installed apps (--tsv: package, label, version, dir, uid, running in this instance, system app, in the app drawer, stale)\n"
          "  launcher show|hide <package>  the app's launcher in the desktop's app drawer (system apps are hidden by default)\n"
          "  info <package>         the app's description, sizes and directories (key<TAB>value)\n"
          "  clear-data <package>   remove the app's files in this instance\n"
          "  uninstall <package>    remove an app and its data\n"
          "  ime enable|disable <package>  let the app's input method be used (from the instance's next start)\n"
          "  screenshot <package|stack> <out.png>  PNG of the app's window as SurfaceFlinger composes it\n"
          "  stop [package]         stop the instance's Android services (apps end with them), or one app\n"
          "  instances              id, running, binder devices, root of every instance\n"
          "  instance create|remove <id>  make an instance (prints the binder setup it still needs) or remove it with its data\n"
          "  prop [list] | set KEY VALUE | unset KEY  the instance's Android property overrides (from its next start)\n"
          "  settings [list] | set KEY VALUE | unset KEY  the instance's own values over the desktop's settings (night yes|no, accent #rrggbb,\n"
          "                         contrast high|normal, animations on|off, font-scale, locale, timezone, time-format 12|24); live in a running instance\n"
          "  permissions <package> | grant|revoke <package> <permission>  what the app requests (permission, level, granted); a runtime one granted or revoked\n"
          "  status                 the instance's stack, running apps, GPU, audio, battery, location\n"
          "  env                    print the environment of Android's processes\n"
          "  --version              version, converterAbi, dataFormat (key<TAB>value)\n",
          stderr);
    return 2;
}
}  // namespace

int main(int argc, char** argv) {
    std::vector<std::string> args(argv + 1, argv + argc);
    std::string id = getenvOr("APKLAYER_INSTANCE", "default");
    if (args.size() >= 2 && args[0] == "--instance") { id = args[1]; args.erase(args.begin(), args.begin() + 2); }
    if (args.empty()) return usage();
    if (args[0] == "--version") { printf("version\t%s\nconverterAbi\t%d\ndataFormat\t%d\n", kVersion, kConverterAbi, kDataFormat); return 0; }
    Layout layout = Layout::detect();
    migrateData(layout);
    Instance inst = Instance::open(layout, id);
    const std::string& cmd = args[0];
    size_t n = args.size();
    if (cmd == "install" && n >= 2) {
        bool downgrade = false, system = false;
        size_t first = 1;
        for (; first < n && (args[first] == "-d" || args[first] == "--system"); first++) (args[first] == "-d" ? downgrade : system) = true;
        if (first == n) return usage();
        installApp(layout, inst, std::vector<std::string>(args.begin() + first, args.end()), downgrade, system);
        return 0;
    }
    if (cmd == "reconvert" && n == 2) return reconvert(layout, inst, args[1]);
    if (cmd == "run" && n == 2) {
        std::string target = args[1];
        if (exists(target)) target = installApp(layout, inst, {target});
        return runApp(layout, inst, target, RunMode::App);
    }
    if (cmd == "component-host" && n == 2) return runApp(layout, inst, args[1], RunMode::Component);
    if (cmd == "app-spare" && n == 3 && args[1] == "--after") return runAppSpare(layout, inst, atoi(args[2].c_str()));  // started by a launch (cli/spare.cpp)
    if (cmd == "app-spare" && n == 1) { wakeAppSpare(layout, inst); return 0; }  // the hub's, at its start
    if (cmd == "hub") return runHub(layout, inst);
    if (cmd == "wait" && n == 2 && args[1] == "hub") return waitHub(layout, inst);
    if (cmd == "list") markSystemApps(layout);  // installs staged before the system mark
    if (cmd == "list" && n == 2 && args[1] == "--tsv") { listAppsTsv(layout, inst); return 0; }
    if (cmd == "list") { listApps(layout); return 0; }
    if (cmd == "info" && n == 2) return appInfo(layout, inst, args[1]);
    if (cmd == "clear-data" && n == 2) return clearData(layout, inst, args[1]);
    if (cmd == "stop" && n == 2) return stopApp(layout, inst, args[1]);
    if (cmd == "instances") { listInstances(layout); return 0; }
    if (cmd == "instance" && n == 3) return instanceCommand(layout, args[1], args[2]);
    if (cmd == "settings") return settingsCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "prop") return propCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "permissions" && n >= 2) return permissionsCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "status") return status(layout, inst);
    if (cmd == "uninstall" && n == 2) { uninstallApp(layout, args[1]); return 0; }
    if (cmd == "launcher" && n == 3) return launcherCommand(layout, args[1], args[2]);
    if (cmd == "ime" && n == 3) return inputMethod(layout, inst, args[1], args[2]);
    if (cmd == "screenshot" && n == 3) return screenshot(layout, inst, args[1], args[2]);
    if (cmd == "stop") { stopStack(layout, inst); return 0; }
    if (cmd == "stack-daemon") return runStackDaemon(layout, inst);
    // test harness: the services plus one tool in the foreground (ends with the tool); exec: one library under the environment
    if (cmd == "stack" && n >= 2) return runStackDaemon(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "exec" && n >= 2) return execTool(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "env") {
        NativeEnv ne = nativeEnv(layout, inst);
        hostJvmArgs(layout, inst, ne);
        for (const auto& [k, v] : ne.env) printf("%s=%s\n", k.c_str(), v.c_str());
        return 0;
    }
    return usage();
}
