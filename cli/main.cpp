// apklayer: run Android apps on Linux. install | run | list | uninstall | screenshot | stop | hub | component-host | env (see README.md).
#include <algorithm>
#include <cstdio>
#include <cstring>
#include <set>
#include <unistd.h>

#include "autostart.h"
#include "daemon.h"
#include "desktop.h"
#include "gpus.h"
#include "halium.h"
#include "setup.h"
#include "logs.h"
#include "portal.h"
#include "sandbox.h"
#include "manage.h"
#include "migrate.h"
#include "providers.h"
#include "run.h"
#include "spare.h"
#include "unit.h"
#include "version.h"

using namespace apklayer;

namespace {
int usage() {
    fputs("usage: apklayer [--instance ID] <command>\n"
          "  install [-d] [--system] <app.apk> [split.apk...]  convert + describe + install (menu entry, icon, .apk handler); also .apks/.xapk/.apkm;\n"
          "                         an update keeps the data, needs the same signer and a version no lower (-d: allow a downgrade);\n"
          "                         --system: a system app (FLAG_SYSTEM, not in the app drawer unless shown)\n"
          "  setup [--check] [--update] [--bundle <file|file://|URL>] [--microg] [--fdroid]  first run and runtime updates in one command (the manager runs it):\n"
          "                         binder check, the Android runtime (releases.json; --bundle or APKLAYER_RUNTIME_BUNDLE offline), the device providers of a Halium\n"
          "                         device (Ubuntu Touch: default instance \"ut\"), the system apps, optional microG and F-Droid; prints `step <id> <state> <detail>`\n"
          "                         lines; --check changes nothing\n"
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
          "  instance autostart <id> [on [--no-start]|off]  always-on mode: a systemd user unit starts the instance (stack and hub) at login and keeps it up, its hub never\n"
          "                         exits when idle (background push, alarms, jobs); without on|off: prints the state\n"
          "  prop [list] | set KEY VALUE | unset KEY  the instance's Android property overrides (from its next start)\n"
          "  providers [set DOMAIN BACKEND [KEY=VALUE...] | unset DOMAIN]  the hardware providers (sensors, gnss, vibrator, camera) and their backends\n"
          "                         (rows: domain provider backend option...; backend DOMAIN NAME probe; option DOMAIN BACKEND KEY DEFAULT text|file)\n"
          "  settings [list] | set KEY VALUE | unset KEY  the instance's own values over the desktop's settings (night yes|no, accent #rrggbb,\n"
          "                         contrast high|normal, animations on|off, font-scale, locale, timezone, time-format 12|24,\n"
          "                         window-mode[.<package>] auto|mobile|desktop); live in a running instance\n"
          "  permissions <package> | grant|revoke <package> <permission>  what the app requests (permission, level, granted); a runtime one granted or revoked\n"
          "  logs [<package> [--previous]]  the app's log of its last launch (its output and android.util.Log; --previous: the launch before);\n"
          "                         without a package: the instance's log files\n"
          "  report <package> [out.tar.gz]  one archive for a bug report: versions, host, the app's info, its logs and the instance's service logs\n"
          "  status                 the instance's stack, running apps, GPU, audio, battery, location\n"
          "  gpus                   the host's GPUs (id, node, driver, vendor:device, compositor's, this instance's, name)\n"
          "  gpu set <id|node> | unset  the instance's GPU (default: the compositor's; from its next start)\n"
          "  env                    print the environment of Android's processes\n"
          "  daemon                 apklayerd: every command (and every app process) then runs under it (tools/host-setup/apklayerd.service);\n"
          "                         the per-app sandbox (each app its own uid) is on where the host is set up (docs/INSTALL.md); APKLAYER_SANDBOX=0|1 forces it\n"
          "  home-access <package> on|off  with the sandbox: the app sees the user's whole home (else only what the picker hands it)\n"
          "  portal pick open|save|directory [--multiple] [--name N] | open <uri>  the desktop's file dialog or link opener (a sandboxed app's)\n"
          "  portal helper geoclue.py|bluez.py|settings.py [args]  a D-Bus helper run as the user (the sandbox's location, Bluetooth, settings)\n"
          "  --version              version, converterAbi, dataFormat (key<TAB>value)\n",
          stderr);
    return 2;
}
}  // namespace

int main(int argc, char** argv) {
    // started by an Android process (the hub: the spare, component hosts), the path layer is preloaded here too and spells a realpath below
    // $APKLAYER_STORAGE as /storage: this tool works with host paths (nativeEnv sets the variable again for what it starts)
    unsetenv("APKLAYER_STORAGE");
    std::vector<std::string> args(argv + 1, argv + argc);
    bool inDaemon = !args.empty() && args[0] == "--no-daemon";  // apklayerd's own child: runs here
    if (inDaemon) args.erase(args.begin());
    std::vector<std::string> full = args;
    std::string id = getenvOr("APKLAYER_INSTANCE", defaultInstanceId());
    if (args.size() >= 2 && args[0] == "--instance") { id = args[1]; args.erase(args.begin(), args.begin() + 2); }
    if (args.empty()) return usage();
    if (args[0] == "--version") {  // defaultInstance: the instance commands without --instance use (a Halium device's is "ut", cli/halium.cpp)
        printf("version\t%s\nconverterAbi\t%d\ndataFormat\t%d\ndefaultInstance\t%s\n", kVersion, kConverterAbi, kDataFormat, id.c_str());
        return 0;
    }
    Layout layout = Layout::detect(args[0] != "setup");  // setup sets the runtime up itself, after replacing a broken one
    // a command runs in apklayerd when it runs (its processes are then the daemon's); the internal ones and the daemon itself stay here
    bool autostart = args.size() >= 3 && args[0] == "instance" && args[1] == "autostart";  // systemctl --user is the user's, not apklayerd's
    static const std::set<std::string> kLocal = {"daemon", "stack-daemon", "stack", "exec", "sandbox-exec", "app-spare", "env", "setup"};
    if (int status = 0; !inDaemon && !autostart && !kLocal.count(args[0]) && forwardToDaemon(layout, full, &status)) return status;
    if (args[0] == "daemon") return runDaemon(layout, args.size() == 2 && args[1] == "--on-demand");
    // what lives on leaves the caller's systemd unit, which its session may freeze (Lomiri): each launch, the spare, the hub, the stack, setup (cli/unit.h)
    const std::string& what = args[0];
    if ((what == "run" || what == "component-host") && args.size() == 2) enterOwnUnit("apklayer-" + id + "-app-" + args[1]);
    else if (what == "app-spare" && args.size() == 3) enterOwnUnit("apklayer-" + id + "-spare");
    else if (what == "hub" || what == "stack-daemon") enterOwnUnit("apklayer-" + id + (what == "hub" ? "-hub" : "-stack"), true);
    else if (what == "setup" && std::find(args.begin(), args.end(), "--check") == args.end()) enterOwnUnit("apklayer-setup");
    migrateData(layout);
    Instance inst = Instance::open(layout, id);
    const std::string& cmd = args[0];
    size_t n = args.size();
    // the sandbox's answer for this command and every process it starts (the Java side reads the variable)
    bool sandbox = sandboxOn();
    setenv("APKLAYER_SANDBOX", sandbox ? "1" : "0", 1);
    if (!sandbox && cmd == "run" && getenvOr("APKLAYER_SANDBOX_QUIET", "").empty())
        if (std::string why = sandboxUnavailable(); !why.empty()) fprintf(stderr, "apklayer: apps run without the per-app sandbox: %s\n", why.c_str());
    if (cmd == "run" || cmd == "list") refreshDesktops(layout);  // a launch from a working entry or the manager mends the others
    if (cmd == "install" && n >= 2) {
        bool downgrade = false, system = false;
        size_t first = 1;
        for (; first < n && (args[first] == "-d" || args[first] == "--system"); first++) (args[first] == "-d" ? downgrade : system) = true;
        if (first == n) return usage();
        installApp(layout, inst, std::vector<std::string>(args.begin() + first, args.end()), downgrade, system);
        return 0;
    }
    if (cmd == "setup") return setupCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "reconvert" && n == 2) return reconvert(layout, inst, args[1]);
    if (cmd == "run" && n == 2) {
        std::string target = args[1];
        if (exists(target)) target = installApp(layout, inst, {target});
        return runApp(layout, inst, target, RunMode::App);
    }
    if (cmd == "component-host" && n == 2) return runApp(layout, inst, args[1], RunMode::Component);
    // under the sandbox a spare is one app's (cli/spare.cpp): only a launch names it, the hub's wake has none
    if (cmd == "app-spare" && n == 5 && args[1] == "--after" && args[3] == "--package" && sandboxOn()) return runAppSpare(layout, inst, atoi(args[2].c_str()), args[4]);
    if (cmd == "app-spare" && sandboxOn()) return 0;
    if (cmd == "app-spare" && n == 3 && args[1] == "--after") return runAppSpare(layout, inst, atoi(args[2].c_str()));  // started by a launch (cli/spare.cpp)
    if (cmd == "app-spare" && n == 1) { wakeAppSpare(layout, inst); return 0; }  // the hub's, at its start
    if (cmd == "sandbox-exec") return sandboxExec(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "home-access" && n == 3) return homeAccessCommand(layout, inst, args[1], args[2]);
    if (cmd == "portal") return portalCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "hub") return runHub(layout, inst);
    if (cmd == "wait" && n == 2 && args[1] == "hub") return waitHub(layout, inst);
    if (cmd == "list") { writeMenu(layout); markSystemApps(layout); }  // installs staged before the menu or the system mark
    if (cmd == "list" && n == 2 && args[1] == "--tsv") { listAppsTsv(layout, inst); return 0; }
    if (cmd == "list") { listApps(layout); return 0; }
    if (cmd == "info" && n == 2) return appInfo(layout, inst, args[1]);
    if (cmd == "logs") return logsCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "report") return reportCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "clear-data" && n == 2) return clearData(layout, inst, args[1]);
    if (cmd == "stop" && n == 2) return stopApp(layout, inst, args[1]);
    if (cmd == "instances") { listInstances(layout); return 0; }
    if (cmd == "instance" && n >= 3 && args[1] == "autostart") return autostartCommand(layout, std::vector<std::string>(args.begin() + 2, args.end()));
    if (cmd == "instance" && n == 3) return instanceCommand(layout, args[1], args[2]);
    if (cmd == "providers") return providersCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "settings") return settingsCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "prop") return propCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "permissions" && n >= 2) return permissionsCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
    if (cmd == "status") return status(layout, inst);
    if (cmd == "gpus" && n == 1) return gpusCommand(layout, inst);
    if (cmd == "gpu" && n >= 2) return gpuCommand(layout, inst, std::vector<std::string>(args.begin() + 1, args.end()));
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
