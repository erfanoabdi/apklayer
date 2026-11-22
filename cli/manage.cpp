#include "manage.h"
#include "sandbox.h"

#include "binder.h"
#include "desktop.h"
#include "fetch.h"
#include "gapps.h"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <cstring>
#include <ftw.h>
#include <map>
#include <regex>
#include <set>
#include <unistd.h>

namespace apklayer {

std::map<std::string, std::vector<std::string>> launches(const Layout& l, const Instance& inst) {
    std::map<std::string, std::vector<std::string>> byPackage;
    for (const std::string& f : listDir(inst.stateDir)) {
        if (f.rfind("run.", 0) != 0) continue;
        auto record = readFile(join(inst.stateDir, f));
        std::vector<std::string> pids = record ? split(*record, '\n') : std::vector<std::string>();
        if (pids.empty() || !alive(atoi(pids[0].c_str()))) continue;
        auto cmd = readFile("/proc/" + pids[0] + "/cmdline");
        if (!cmd) continue;
        std::string pkg;
        const std::string key = "-Dapklayer.package=";
        if (pids.size() >= 3 && !pids[2].empty()) pkg = pids[2].substr(0, pids[2].find(':'));  // run.cpp's record: the process the launch runs (a spare's JVM is unnamed at first)
        else if (size_t at = cmd->find(key); at != std::string::npos) pkg = cmd->substr(at + key.size(), cmd->find('\0', at) - at - key.size());
        else pkg = cmd->substr(0, cmd->find_first_of(std::string(":\0", 2)));
        if (!pkg.empty() && isDir(appDir(l, pkg))) byPackage[pkg].push_back(f);
    }
    return byPackage;
}

namespace {
long long treeSize;
int addSize(const char*, const struct stat* st, int type, FTW*) { if (type == FTW_F) treeSize += st->st_size; return 0; }
long long dirSize(const std::string& dir) {
    treeSize = 0;
    if (isDir(dir)) nftw(dir.c_str(), addSize, 32, FTW_PHYS);
    return treeSize;
}

bool stackRunning(const Instance& inst) {
    auto pid = readFile(inst.pidFile());
    return pid && alive(atoi(pid->c_str()));
}



// what may name an instance: a binder device suffix and a directory name, never one of the data home's own directories
bool validInstanceId(const std::string& id) {
    if (id.empty() || id.size() > 32 || id == "apps" || id == "runtime" || id == "google-apps" || id == "0") return false;
    for (char c : id) if (!(isalnum(static_cast<unsigned char>(c)) || c == '-' || c == '_')) return false;
    return true;
}

// key=value lines in file order (build.prop format: comments and blank lines skipped)
std::vector<std::pair<std::string, std::string>> propLines(const std::string& file) {
    std::vector<std::pair<std::string, std::string>> out;
    for (const std::string& line : split(readFile(file).value_or(""), '\n')) {
        size_t eq = line.find('=');
        if (line.empty() || line[0] == '#' || eq == std::string::npos) continue;
        out.emplace_back(trim(line.substr(0, eq)), line.substr(eq + 1));
    }
    return out;
}

// the live store of a running stack (aprop dump: "[name]: [value]")
std::map<std::string, std::string> liveProperties(const Layout& l, const Instance& inst) {
    std::map<std::string, std::string> live;
    if (!stackRunning(inst)) return live;
    Spawn dump;
    dump.argv = {join(l.native, "bin/aprop"), "dump"};
    dump.env = nativeEnv(l, inst).env;
    for (const std::string& line : split(runCapture(dump), '\n')) {
        size_t sep = line.find("]: [");
        if (line.size() > 4 && line[0] == '[' && sep != std::string::npos && line.back() == ']') live[line.substr(1, sep - 1)] = line.substr(sep + 4, line.size() - sep - 5);
    }
    return live;
}

std::string clean(std::string s) {  // a field of a row
    for (char& c : s) if (c == '\t' || c == '\n' || c == '\r') c = ' ';
    return s;
}
}  // namespace

void listAppsTsv(const Layout& l, const Instance& inst) {
    auto running = launches(l, inst);
    for (const std::string& app : listDir(l.appsDir)) {
        std::string f = join(appDir(l, app), "info.properties");
        if (!exists(f)) continue;
        printf("%s\t%s\t%s\t%s\t%s\t%d\t%d\t%d\t%d\n", clean(property(f, "package")).c_str(), clean(property(f, "label")).c_str(), clean(property(f, "versionName")).c_str(),
               appDir(l, app).c_str(), property(f, "uid").c_str(), running.count(app) ? 1 : 0, isSystemApp(f) ? 1 : 0, inDrawer(f) ? 1 : 0, isStale(f) ? 1 : 0);
    }
}

int appInfo(const Layout& l, const Instance& inst, const std::string& package) {
    std::string dir = appDir(l, package), f = join(dir, "info.properties");
    if (!exists(f)) die("not installed: " + package);
    for (const char* key : {"package", "label", "versionName", "versionCode", "targetSdk", "launcher", "uid", "nativeRoute"}) printf("%s\t%s\n", key, clean(property(f, key)).c_str());
    printf("system\t%d\ninDrawer\t%d\n", isSystemApp(f) ? 1 : 0, inDrawer(f) ? 1 : 0);
    printf("dir\t%s\ncodeSize\t%lld\n", dir.c_str(), dirSize(dir) - dirSize(join(dir, "data")));
    printf("dataDir\t%s\ndataSize\t%lld\n", inst.appData(package).c_str(), dirSize(inst.appData(package)));
    printf("running\t%d\n", launches(l, inst).count(package) ? 1 : 0);
    return 0;
}

int stopApp(const Layout& l, const Instance& inst, const std::string& package) {
    auto running = launches(l, inst);
    for (const std::string& record : running[package]) {
        if (auto pids = readFile(join(inst.stateDir, record))) for (const std::string& pid : split(*pids, '\n')) terminate(atoi(pid.c_str()));
        unlink(join(inst.stateDir, record).c_str());
    }
    printf("apklayer: stopped %zu launch(es) of %s\n", running[package].size(), package.c_str());
    return 0;
}

int clearData(const Layout& l, const Instance& inst, const std::string& package) {
    if (!isDir(appDir(l, package))) die("not installed: " + package);
    stopApp(l, inst, package);
    reclaim(inst.appData(package));  // files the sandbox's app uid owns
    removeTree(inst.appData(package));
    unlink(join(inst.root, "notification-channels/" + package).c_str());  // as NotificationManagerService.clearData: the app's channels go with its data
    if (inst.id == "default") removeTree(join(appDir(l, package), "data"));  // the shared place before data was per instance (appDataDir would move it back in)
    printf("apklayer: cleared the data of %s in instance %s\n", package.c_str(), inst.id.c_str());
    return 0;
}

void listInstances(const Layout& l) {
    for (const std::string& name : listDir(join(l.dataHome, "apklayer"))) {
        std::string root = join(l.dataHome, "apklayer/" + name);
        if (!isDir(join(root, "storage")) && !isDir(join(root, "data"))) continue;  // apps/ and anything else that is not an instance
        std::string id = name == "0" ? "default" : name;
        auto pid = readFile(join(l.stateHome, "apklayer/" + id + "/stack.pid"));
        printf("%s\t%d\t%d\t%s\t%d\n", id.c_str(), pid && alive(atoi(pid->c_str())) ? 1 : 0, binderReady(root, id) ? 1 : 0, root.c_str(), exists(join(l.stateHome, "apklayer/" + id + "/autostart")) ? 1 : 0);
    }
}

int instanceCommand(const Layout& l, const std::string& action, const std::string& id) {
    if (!validInstanceId(id) || (action == "remove" && id == "default")) die("not an instance name one may " + action + ": " + id + " (letters, digits, - and _)");
    if (action == "create") {
        Instance inst = Instance::open(l, id);
        printf("created\t%s\n", inst.root.c_str());
        if (binderReady(inst.root, id)) puts("binder\tok");
        else printf("binder\tmissing\t%s\n", binderSetupCommand(id).c_str());  // only with the kernel binder (apklayer binder kernel)
        // a new instance has the WebView provider as any other (setup installs it for the instance it ran with): when the runtime ships it and none is installed yet
        const std::string webview = manager::runtimeRoot() + "/current/system-apps/webview.apk";
        if (exists(webview) && !exists(join(l.appsDir, "com.android.webview/classes.jar"))) {
            Spawn s;
            s.argv = {l.cliBinary(), "--instance", id, "install", "--system", webview};
            s.env = currentEnv();
            if (runAndWait(s) != 0) fprintf(stderr, "apklayer: the WebView provider is not installed in %s (apklayer --instance %s install --system %s)\n", id.c_str(), id.c_str(), webview.c_str());
        }
        return 0;
    }
    if (action != "remove") die("instance create|remove <id>");
    if (!isDir(join(l.dataHome, "apklayer/" + id))) die("no such instance: " + id);
    Instance inst = Instance::open(l, id);
    bool kernel = !userspaceBinder(inst.root);
    if (stackRunning(inst)) stopStack(l, inst);
    for (const std::string& app : listDir(join(inst.root, "app-data"))) reclaim(join(inst.root, "app-data/" + app));  // the sandbox's app uids own these
    for (const std::string& tmp : listDir(inst.tmpDir())) reclaim(join(inst.tmpDir(), tmp));
    for (const std::string& dir : {inst.root, join(l.stateHome, "apklayer/" + id), inst.runtimeDir}) removeTree(dir);  // not $APKLAYER_LOGS: a test's own directory
    printf("apklayer: removed instance %s%s\n", id.c_str(), kernel ? " (its kernel binder devices stay until the next reboot)" : "");
    return 0;
}

int propCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    const std::string file = inst.propOverrides();
    auto own = propLines(file);
    if (args.empty() || (args[0] == "list" && args.size() == 1)) {
        auto live = liveProperties(l, inst);
        auto row = [&](const std::string& k, const std::string& v, const char* source) {
            printf("%s\t%s\t%s\t%s\n", clean(k).c_str(), clean(v).c_str(), source, live.count(k) ? clean(live[k]).c_str() : "");
        };
        std::set<std::string> seen;
        for (const auto& [k, v] : own) { row(k, v, "instance"); seen.insert(k); }
        for (const auto& [k, v] : propLines(join(l.root, "bridge/native/apklayer.prop"))) if (seen.insert(k).second) row(k, v, "apklayer");
        return 0;
    }
    const std::string& action = args[0];
    if (!((action == "set" && args.size() == 3) || (action == "unset" && args.size() == 2))) die("prop [list] | set KEY VALUE | unset KEY");
    const std::string& key = args[1];
    // the property store's limits (bridge/native/properties.c): names up to 63 bytes, values up to 91, one line each
    bool ok = !key.empty() && key.size() < 64;
    for (char c : key) ok = ok && (isalnum(static_cast<unsigned char>(c)) || strchr("._-:@", c));
    if (!ok) die("not a property name: " + key);
    if (action == "set" && (args[2].size() > 91 || args[2].find_first_of("\n\r") != std::string::npos)) die("a property value is one line of at most 91 bytes");
    std::string text;
    for (const auto& [k, v] : own) if (k != key) text += k + "=" + v + "\n";
    if (action == "set") text += key + "=" + args[2] + "\n";
    if (!writeFile(file, text)) die("cannot write " + file);
    printf("apklayer: %s %s for instance %s (from its next start)\n", key.c_str(), action == "set" ? "set" : "unset", inst.id.c_str());
    return 0;
}

// `app <package> translation-mode [default|lite|interpret]`: berberis.mode.<package> (binary_translation 0008), the same override as `prop set`
int appCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    static const std::map<std::string, std::string> modes = {{"lite", "lite-translate-or-interpret"}, {"interpret", "interpret-only"}};
    if (args.size() == 2 && args[1] == "resolution") {  // `app <package> resolution`: "window\tWxH" the window's pixels at the app's last configure (the shell's log), nothing before it ran
        static const std::regex configure(R"(configure content \d+x\d+ @\d+% \((\d+)x(\d+) px\), title bar)");
        std::string found;
        for (const std::string& line : split(readFile(join(inst.logs(), "shell.log")).value_or(""), '\n')) {
            std::smatch m;
            if (line.find(" " + args[0] + ": configure content") == std::string::npos || !std::regex_search(line, m, configure)) continue;
            found = m[1].str() + "x" + m[2].str();
        }
        if (!found.empty()) printf("window\t%s\n", found.c_str());
        return 0;
    }
    if (args.size() < 2 || args.size() > 3 || args[1] != "translation-mode") die("app <package> translation-mode [default|lite|interpret] | app <package> resolution");
    const std::string& pkg = args[0];
    if (pkg.size() > 49) die("a package name of at most 49 characters (the translator's property name limit)");  // "berberis.mode." + 49 = 63 bytes
    std::string info = join(appDir(l, pkg), "info.properties");
    if (!exists(info)) die("not installed: " + pkg);
    const std::string key = "berberis.mode." + pkg;
    if (args.size() == 3) {
        if (args[2] != "default" && !modes.count(args[2])) die("not a translation mode: " + args[2] + " (default, lite, interpret)");
        int r = propCommand(l, inst, args[2] == "default" ? std::vector<std::string>{"unset", key} : std::vector<std::string>{"set", key, modes.at(args[2])});
        if (r != 0) return r;
    }
    std::string mode = "default";
    for (const auto& [k, v] : propLines(inst.propOverrides()))
        if (k == key) for (const auto& [name, value] : modes) if (value == v) mode = name;
    printf("translation-mode\t%s\n", mode.c_str());
    if (property(info, "nativeRoute").rfind("bridge-", 0) != 0) puts("note: this app's code is not translated here; the mode has no effect");
    puts("note: takes effect at the app's next start");
    return 0;
}

int settingsCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    const std::string file = join(inst.root, "settings");  // read by the hub (apklayer.host.HubHostSettings), which lays it over the desktop's
    static const std::map<std::string, std::regex> keys = {  // what each value may be (the hub reads them as such)
        {"night", std::regex("yes|no")}, {"accent", std::regex("#?[0-9a-fA-F]{6}")}, {"contrast", std::regex("high|normal")},
        {"animations", std::regex("on|off")}, {"font-scale", std::regex("[0-3](\\.[0-9]+)?")}, {"locale", std::regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")},
        {"timezone", std::regex("[A-Za-z0-9_+/-]+")}, {"time-format", std::regex("12|24")},
        {"window-mode", std::regex("auto|mobile|desktop")}, {"navbar", std::regex("auto|on|off")},
        {"render-scale", std::regex("(200|1[0-9][0-9]|[3-9][0-9]|2[5-9])")}, {"render-filter", std::regex("sharp|off")}};  // percent, 25..200 (the shell, MSG_RENDER_SCALE); above 100 renders larger than the window and shrinks it
    auto own = propLines(file);
    if (args.empty() || (args[0] == "list" && args.size() == 1)) {
        for (const auto& [k, v] : own) printf("%s\t%s\n", clean(k).c_str(), clean(v).c_str());
        return 0;
    }
    const std::string& action = args[0];
    if (!((action == "set" && args.size() == 3) || (action == "unset" && args.size() == 2)) || !(keys.count(args[1]) || args[1].rfind("window-mode.", 0) == 0 || args[1].rfind("render-scale.", 0) == 0 || args[1].rfind("render-filter.", 0) == 0))
        die("settings [list] | set KEY VALUE | unset KEY; keys: night yes|no, accent #rrggbb, contrast high|normal, animations on|off, "
            "font-scale <float>, locale <BCP-47>, timezone <tz id>, time-format 12|24, window-mode[.<package>] auto|mobile|desktop, navbar auto|on|off, render-scale[.<package>] 25..200, render-filter[.<package>] sharp|off");
    const std::string keyName = args[1].rfind("window-mode.", 0) == 0 ? "window-mode" : args[1].rfind("render-scale.", 0) == 0 ? "render-scale" : args[1].rfind("render-filter.", 0) == 0 ? "render-filter" : args[1];
    if (action == "set" && !std::regex_match(args[2], keys.at(keyName))) die("not a value for " + args[1] + ": " + args[2]);
    std::string text;
    for (const auto& [k, v] : own) if (k != args[1]) text += k + "=" + v + "\n";
    if (action == "set") text += args[1] + "=" + args[2] + "\n";
    if (!writeFile(file, text)) die("cannot write " + file);
    bool applied = keyName != "render-scale" && keyName != "render-filter" && settingsChanged(l, inst);  // the render scale and filter are read when an app starts
    printf("apklayer: %s %s for instance %s (%s)\n", args[1].c_str(), action == "set" ? "set" : "unset", inst.id.c_str(), applied ? "applied now" : "from the app's next start");
    return 0;
}

bool settingsChanged(const Layout& l, const Instance& inst) {
    if (!stackRunning(inst)) return false;
    // HubProtocol.SETTINGS_CHANGED (43): the hub rereads the settings and input method files; the running apps get the new configuration
    Spawn call;
    call.argv = {l.btload(), l.androidLibs, "libapklayer_servicetool.so", "--main", "call", "apklayer.instance", "43", "apklayer.IInstanceHub"};
    call.env = nativeEnv(l, inst).env;
    call.logFile = join(inst.logs(), "settings.log");
    return runAndWait(call) == 0;
}

int permissionsCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    if (!((args.size() == 1) || (args.size() == 3 && (args[0] == "grant" || args[0] == "revoke")))) die("permissions <package> | grant|revoke <package> <permission>");
    const std::string& pkg = args.size() == 1 ? args[0] : args[1];
    std::string info = join(appDir(l, pkg), "info.properties");
    if (!exists(info)) die("not installed: " + pkg);
    if (readFile(info).value_or("").find("\npermissions=") == std::string::npos) die("`apklayer reconvert " + pkg + "` to list its permissions (installed before apklayer recorded them; its data stays)");
    std::string etc = join(l.native, "android-root/system/etc");
    std::map<std::string, std::string> levels;  // the framework's protection levels (tools/gen/permission-levels.py), as HostSystemConfig reads them
    for (const std::string& line : split(readFile(join(etc, "apklayer-permission-levels.tsv")).value_or(""), '\n'))
        if (size_t tab = line.find('\t'); tab != std::string::npos) levels[line.substr(0, tab)] = line.substr(tab + 1);
    // the person's answers, also the permission prompt's (apklayer.host.HostRuntimePermissions): "package\tpermission" granted, revoked ("\tonce": asked again)
    const std::string grantedFile = join(inst.root, "permissions-granted"), file = join(inst.root, "permissions-revoked");
    std::set<std::string> granted, revoked;
    for (const std::string& line : split(readFile(grantedFile).value_or(""), '\n')) if (!line.empty()) granted.insert(line);
    for (const std::string& line : split(readFile(file).value_or(""), '\n')) if (!line.empty()) revoked.insert(line);
    const bool legacy = std::atoi(property(info, "targetSdk").c_str()) < 23;  // granted at install unless revoked, as Android does
    std::vector<std::string> requested = split(property(info, "permissions"), ';');
    if (args.size() == 1) {
        // signature permissions: a privileged system app (in the build's allowlist) holds them, as HostSystemConfig grants them
        // Google's own apps keep their allowlist in the data home (cli/gapps.cpp), which HostSystemConfig reads beside this one: both, or the
        // CLI disagrees with the JVM about what is privileged
        bool privileged = false;
        std::vector<std::string> allowlists = {join(etc, "permissions")};
        if (std::string gapps = gappsEtcDir(l); !gapps.empty()) allowlists.push_back(join(gapps, "permissions"));
        for (const std::string& dir : allowlists)
            for (const std::string& f : listDir(dir)) {
                const std::string xml = readFile(join(dir, f)).value_or("");  // either quote: a vendor's allowlist is not always written as AOSP's are
                privileged = privileged || xml.find("<privapp-permissions package=\"" + pkg + "\"") != std::string::npos
                                        || xml.find("<privapp-permissions package='" + pkg + "'") != std::string::npos;
            }
        for (const std::string& p : requested) {
            if (p.empty()) continue;
            const std::string level = levels.count(p) ? levels[p] : "";
            // blank: a permission another app defines, which that app's protection level decides
            const std::string key = pkg + "\t" + p;
            const bool held = granted.count(key) || (legacy && !revoked.count(key) && !revoked.count(key + "\tonce"));
            const char* shown = level == "dangerous" ? (held ? "1" : "0") : level == "normal" || level == "appop" ? "1"
                                : level == "signature" ? (privileged ? "1" : "0") : "";
            printf("%s\t%s\t%s\n", clean(p).c_str(), level.c_str(), shown);
        }
        return 0;
    }
    const std::string& perm = args[2];
    // POST_NOTIFICATIONS is also the per-app notification switch for apps that do not request it (below 33: NotificationManagerService blocks the package)
    if (std::find(requested.begin(), requested.end(), perm) == requested.end() && perm != "android.permission.POST_NOTIFICATIONS") die(pkg + " does not request " + perm);
    if (levels[perm] != "dangerous") die(perm + " is not a runtime permission (only dangerous ones are granted or revoked)");
    const std::string key = pkg + "\t" + perm;
    std::string text, grants;
    for (const std::string& line : revoked) if (line != key && line != key + "\tonce") text += line + "\n";
    for (const std::string& line : granted) if (line != key) grants += line + "\n";
    if (args[0] == "revoke") text += key + "\n"; else grants += key + "\n";
    if (!writeFile(file, text) || !writeFile(grantedFile, grants)) die("cannot write " + file);
    if (args[0] == "revoke") stopApp(l, inst, pkg);  // as Android does: a revoked permission ends the app's processes
    printf("apklayer: %s %s %s %s in instance %s\n", args[0] == "grant" ? "granted" : "revoked", perm.c_str(), args[0] == "grant" ? "to" : "from", pkg.c_str(),
           inst.id.c_str());
    return 0;
}

int status(const Layout& l, const Instance& inst) {
    NativeEnv ne = nativeEnv(l, inst);  // probes the host GLES once per session
    std::string pkgs;
    for (const auto& [pkg, records] : launches(l, inst)) pkgs += (pkgs.empty() ? "" : ",") + pkg;
    printf("instance\t%s\nstack\t%s\nbinder\t%s\napps\t%s\nabi\t%s\n", inst.id.c_str(), stackRunning(inst) ? "running" : "stopped",
           binderReady(inst.root, inst.id) ? "ok" : "missing", pkgs.c_str(), kAbi);
    printf("gpu node\t%s\n", ne.env.count("APKLAYER_RENDER_NODE") ? ne.env["APKLAYER_RENDER_NODE"].c_str() : "none");
    Spawn egl;
    egl.argv = {"eglinfo", "-B", "-p", "gbm"};
    egl.env = currentEnv();  // the host driver as it is, without the version claim nativeEnv adds
    for (const std::string& line : split(runCapture(egl), '\n')) {
        if (line.rfind("OpenGL ES profile renderer: ", 0) == 0) printf("gpu\t%s\n", clean(line.substr(28)).c_str());
        if (line.rfind("OpenGL ES profile version: ", 0) == 0) printf("gles\t%s%s\n", clean(line.substr(27)).c_str(), ne.env.count("MESA_GLES_VERSION_OVERRIDE") ? " (apps see 3.2)" : "");
    }
    printf("vulkan\t%s\n", ne.env.count("APKLAYER_VULKAN_HOST") ? "host driver" : "not staged");
    std::string pw = join(getenvOr("PIPEWIRE_RUNTIME_DIR", l.runtimeHome), "pipewire-0");
    printf("audio\t%s\n", exists(pw) ? ("PipeWire (" + pw + ")").c_str() : "no PipeWire socket");
    // what apklayer.host.HostBattery reads: a present Battery supply of /sys/class/power_supply, else a desktop on mains
    std::string battery = "none (mains power)";
    for (const std::string& s : listDir("/sys/class/power_supply")) {
        std::string d = join("/sys/class/power_supply", s);
        if (trim(readFile(join(d, "type")).value_or("")) == "Battery" && trim(readFile(join(d, "present")).value_or("1")) != "0")
            battery = trim(readFile(join(d, "capacity")).value_or("?")) + "% " + trim(readFile(join(d, "status")).value_or(""));
    }
    printf("battery\t%s\n", clean(battery).c_str());
    // apps get location from GeoClue (bridge/portal/geoclue.py) when the system has it
    std::string location = exists("/usr/share/dbus-1/system-services/org.freedesktop.GeoClue2.service") ? "GeoClue" : "off (no GeoClue)";
    if (exists("/usr/share/dbus-1/services/org.freedesktop.Geoclue.Master.service")) {  // Sailfish: GeoClue 1, switched by Settings > Location (location.conf)
        std::string conf = readFile("/var/lib/location/location.conf").value_or("");
        location = conf.find("\nenabled=true") != std::string::npos ? "GeoClue 1" : "GeoClue 1, but Location is off in Settings";
    }
    printf("location\t%s\n", location.c_str());
    // the hub's inotify watches for MediaStore (apklayer.host.HubMediaWatch): what is over its budget is scanned at the next hub start
    if (auto watch = readFile(join(inst.stateDir, "media-watch")); watch && stackRunning(inst)) printf("media watch\t%s\n", trim(*watch).c_str());
    return 0;
}

}  // namespace apklayer
