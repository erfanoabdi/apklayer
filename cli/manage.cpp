#include "manage.h"

#include "desktop.h"

#include <algorithm>
#include <cctype>
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
        if (size_t at = cmd->find(key); at != std::string::npos) pkg = cmd->substr(at + key.size(), cmd->find('\0', at) - at - key.size());
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

std::string binderDevice(const std::string& id) { return "/dev/binderfs/" + (id == "default" ? std::string("binder") : "binder-" + id); }

// what may name an instance: a binder device suffix and a directory name, never one of the data home's own directories
bool validInstanceId(const std::string& id) {
    if (id.empty() || id.size() > 32 || id == "apps" || id == "0") return false;
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
    for (const char* key : {"package", "label", "versionName", "versionCode", "targetSdk", "launcher", "uid"}) printf("%s\t%s\n", key, clean(property(f, key)).c_str());
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
    removeTree(inst.appData(package));
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
        printf("%s\t%d\t%d\t%s\n", id.c_str(), pid && alive(atoi(pid->c_str())) ? 1 : 0, exists(binderDevice(id)) ? 1 : 0, root.c_str());
    }
}

int instanceCommand(const Layout& l, const std::string& action, const std::string& id) {
    if (!validInstanceId(id) || (action == "remove" && id == "default")) die("not an instance name one may " + action + ": " + id + " (letters, digits, - and _)");
    if (action == "create") {
        Instance inst = Instance::open(l, id);
        printf("created\t%s\n", inst.root.c_str());
        if (exists(binderDevice(id))) puts("binder\tok");
        else printf("binder\tmissing\tsudo sh %s %s\n", join(l.root, "tools/host-setup/binder.sh").c_str(), id.c_str());  // binderfs devices need root
        return 0;
    }
    if (action != "remove") die("instance create|remove <id>");
    if (!isDir(join(l.dataHome, "apklayer/" + id))) die("no such instance: " + id);
    Instance inst = Instance::open(l, id);
    if (stackRunning(inst)) stopStack(l, inst);
    for (const std::string& dir : {inst.root, join(l.stateHome, "apklayer/" + id), inst.runtimeDir}) removeTree(dir);  // not $APKLAYER_LOGS: a test's own directory
    printf("apklayer: removed instance %s (its binder devices stay until the next reboot)\n", id.c_str());
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

int settingsCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    const std::string file = join(inst.root, "settings");  // read by the hub (apklayer.host.HubHostSettings), which lays it over the desktop's
    static const std::map<std::string, std::regex> keys = {  // what each value may be (the hub reads them as such)
        {"night", std::regex("yes|no")}, {"accent", std::regex("#?[0-9a-fA-F]{6}")}, {"contrast", std::regex("high|normal")},
        {"animations", std::regex("on|off")}, {"font-scale", std::regex("[0-3](\\.[0-9]+)?")}, {"locale", std::regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")},
        {"timezone", std::regex("[A-Za-z0-9_+/-]+")}, {"time-format", std::regex("12|24")}};
    auto own = propLines(file);
    if (args.empty() || (args[0] == "list" && args.size() == 1)) {
        for (const auto& [k, v] : own) printf("%s\t%s\n", clean(k).c_str(), clean(v).c_str());
        return 0;
    }
    const std::string& action = args[0];
    if (!((action == "set" && args.size() == 3) || (action == "unset" && args.size() == 2)) || !keys.count(args[1]))
        die("settings [list] | set KEY VALUE | unset KEY; keys: night yes|no, accent #rrggbb, contrast high|normal, animations on|off, "
            "font-scale <float>, locale <BCP-47>, timezone <tz id>, time-format 12|24");
    if (action == "set" && !std::regex_match(args[2], keys.at(args[1]))) die("not a value for " + args[1] + ": " + args[2]);
    std::string text;
    for (const auto& [k, v] : own) if (k != args[1]) text += k + "=" + v + "\n";
    if (action == "set") text += args[1] + "=" + args[2] + "\n";
    if (!writeFile(file, text)) die("cannot write " + file);
    bool live = false;
    if (stackRunning(inst)) {  // HubProtocol.SETTINGS_CHANGED (43): the hub rereads the file and the running apps get the new configuration
        Spawn call;
        call.argv = {l.btload(), l.androidLibs, "libapklayer_servicetool.so", "--main", "call", "apklayer.instance", "43", "apklayer.IInstanceHub"};
        call.env = nativeEnv(l, inst).env;
        call.logFile = join(inst.logs(), "settings.log");
        live = runAndWait(call) == 0;
    }
    printf("apklayer: %s %s for instance %s (%s)\n", args[1].c_str(), action == "set" ? "set" : "unset", inst.id.c_str(), live ? "applied now" : "from its next start");
    return 0;
}

int permissionsCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    if (!((args.size() == 1) || (args.size() == 3 && (args[0] == "grant" || args[0] == "revoke")))) die("permissions <package> | grant|revoke <package> <permission>");
    const std::string& pkg = args.size() == 1 ? args[0] : args[1];
    std::string info = join(appDir(l, pkg), "info.properties");
    if (!exists(info)) die("not installed: " + pkg);
    if (readFile(info).value_or("").find("\npermissions=") == std::string::npos) die("reinstall " + pkg + " to list its permissions (installed before apklayer recorded them)");
    std::string etc = join(l.native, "android-root/system/etc");
    std::map<std::string, std::string> levels;  // the framework's protection levels (tools/gen/permission-levels.py), as HostSystemConfig reads them
    for (const std::string& line : split(readFile(join(etc, "apklayer-permission-levels.tsv")).value_or(""), '\n'))
        if (size_t tab = line.find('\t'); tab != std::string::npos) levels[line.substr(0, tab)] = line.substr(tab + 1);
    const std::string file = join(inst.root, "permissions-revoked");  // read by the runtime (apklayer.host.HostRevokedPermissions)
    std::set<std::string> revoked;
    for (const std::string& line : split(readFile(file).value_or(""), '\n')) if (!line.empty()) revoked.insert(line);
    std::vector<std::string> requested = split(property(info, "permissions"), ';');
    if (args.size() == 1) {
        // signature permissions: a privileged system app (in the build's allowlist) holds them, as HostSystemConfig grants them
        bool privileged = false;
        for (const std::string& f : listDir(join(etc, "permissions")))
            privileged = privileged || readFile(join(etc, "permissions/" + f)).value_or("").find("<privapp-permissions package=\"" + pkg + "\"") != std::string::npos;
        for (const std::string& p : requested) {
            if (p.empty()) continue;
            const std::string level = levels.count(p) ? levels[p] : "";
            // blank: a permission another app defines, which that app's protection level decides
            const char* granted = level == "dangerous" ? (revoked.count(pkg + "\t" + p) ? "0" : "1") : level == "normal" || level == "appop" ? "1"
                                : level == "signature" ? (privileged ? "1" : "0") : "";
            printf("%s\t%s\t%s\n", clean(p).c_str(), level.c_str(), granted);
        }
        return 0;
    }
    const std::string& perm = args[2];
    // POST_NOTIFICATIONS is also the per-app notification switch for apps that do not request it (below 33: NotificationManagerService blocks the package)
    if (std::find(requested.begin(), requested.end(), perm) == requested.end() && perm != "android.permission.POST_NOTIFICATIONS") die(pkg + " does not request " + perm);
    if (levels[perm] != "dangerous") die(perm + " is not a runtime permission (only dangerous ones are granted or revoked)");
    std::string text;
    for (const std::string& line : revoked) if (line != pkg + "\t" + perm) text += line + "\n";
    if (args[0] == "revoke") text += pkg + "\t" + perm + "\n";
    if (!writeFile(file, text)) die("cannot write " + file);
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
           exists(binderDevice(inst.id)) ? "ok" : "missing", pkgs.c_str(), kAbi);
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
    printf("location\t%s\n", exists("/usr/share/dbus-1/system-services/org.freedesktop.GeoClue2.service") ? "GeoClue" : "off (no GeoClue)");
    return 0;
}

}  // namespace apklayer
