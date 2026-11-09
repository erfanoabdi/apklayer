// `apklayer setup`: the first run and the runtime updates, in one command (the manager is a front-end that runs it and reads its lines).
// Idempotent: it does what is missing or stale. Output, one per line, tab separated:
//   step <id> <state> <detail>      id: binder java runtime providers system-apps webview microg fdroid; state: todo running done failed skipped
//   progress <0..1 or -1>           the running step's download
//   latest <version> <runtime version> <date>   (--check, when releases.json was read)
#include "setup.h"
#include "sandbox.h"

#include <algorithm>
#include <cstdio>
#include <map>
#include <set>
#include <unistd.h>

#include "apps.h"
#include "autostart.h"
#include "binder.h"
#include "datamove.h"
#include "env.h"
#include "fetch.h"
#include "halium.h"
#include "providers.h"
#include "releases.h"
#include "util.h"

namespace apklayer {

namespace {
bool anyFailed = false;
void step(const std::string& id, const std::string& state, const std::string& detail) {
    if (state == "failed") anyFailed = true;
    printf("step\t%s\t%s\t%s\n", id.c_str(), state.c_str(), detail.c_str());
    fflush(stdout);
}
void progress(double p) {
    static int last = -2;
    int pct = p < 0 ? -1 : static_cast<int>(p * 100);
    if (pct == last) return;
    last = pct;
    printf("progress\t%s\n", p < 0 ? "-1" : std::to_string(p).c_str());
    fflush(stdout);
}
std::string readText(const std::string& path) { return readFile(path).value_or(""); }
std::string envOr(const char* name, const char* fallback) { return getenvOr(name, fallback); }

// the providers step's line: every domain is automatic unless chosen (cli/providers.cpp); on a Halium device, which are the device's own
std::string providersDetail(const Layout& layout, const Instance& instance) {
    std::string device;
    for (const char* d : {"graphics", "camera", "vibrator", "media"})
        if (deviceDomain(layout, instance, d)) device += std::string(device.empty() ? "" : ", ") + d;
    return device.empty() ? "Automatic: the best this host has" : "Automatic: the device's " + device;
}

// the bundle's system APKs (file, sha256) and which of them differ from what setup installed last: the record beside setup-done holds
// "<sha256> <file>" per APK, so a runtime update (another build, the x86_64 APKs replacing arm64 ones) installs only the changed ones
struct SystemApps { std::vector<std::pair<std::string, std::string>> all, changed; };
SystemApps systemApps(const std::string& dir, const std::string& record) {
    std::map<std::string, std::string> installed;
    for (const std::string& line : split(readText(record), '\n'))
        if (size_t sp = line.find(' '); sp != std::string::npos) installed[line.substr(sp + 1)] = line.substr(0, sp);
    std::vector<std::string> files;
    for (const std::string& f : listDir(dir)) if (f.size() > 4 && f.substr(f.size() - 4) == ".apk") files.push_back(f);
    std::sort(files.begin(), files.end());
    SystemApps r;
    for (const std::string& f : files) {
        r.all.emplace_back(f, sha256File(join(dir, f)));
        if (installed[f] != r.all.back().second) r.changed.push_back(r.all.back());
    }
    return r;
}

// "" when the JVM apps run on is Java 21 or newer, else what is wrong (the deb depends on openjdk-21-jre-headless; an install that skipped it,
// as on a phone's small root file system, names another one as APKLAYER_NATIVE_JAVA in ~/.config/apklayer/env)
std::string javaProblem(const Layout& layout) {
    std::string java = findJava(layout);
    Spawn s;
    s.argv = {"sh", "-c", "exec \"$0\" -version 2>&1", java};
    s.env = currentEnv();
    int status = 0;
    std::string out = runCapture(s, &status);
    size_t at = out.find("version \"");
    int major = status == 0 && at != std::string::npos ? atoi(out.c_str() + at + 9) : 0;
    if (major >= 21) return "";
    std::string hint = " (sudo apt install openjdk-21-jre-headless, or APKLAYER_NATIVE_JAVA=<java 21> in ~/.config/apklayer/env)";
    return major > 0 ? "Java " + std::to_string(major) + " is too old: apklayer needs 21" + hint : "No Java 21 found" + hint;
}

struct Options {
    bool check = false, update = false, microg = false, fdroid = false;
    std::string bundle;    // a file, file:// or URL of the runtime bundle: no releases.json lookup
    std::string moveData;  // --move-data <dir>: only that
};

// a runtime step's failure; one for want of space names the way out (cli/datamove.cpp)
int runtimeFailed(const Layout& layout, const std::string& e) {
    std::string big = bigDataDir(layout);
    step("runtime", "failed", e.rfind("Not enough space", 0) != 0 ? e
        : e + ". Free space there, or move apklayer's data to a bigger file system: apklayer setup --move-data " + (big.empty() ? "<directory>" : big));
    return 1;
}

// packages of an F-Droid repository: its index, each APK checked against the index's SHA256, then the CLI's own install
std::string installFromIndex(const Layout& layout, const Instance& inst, const std::string& id, const std::string& indexUrl, const std::vector<std::string>& packages) {
    std::string index = manager::downloadsDir() + "/index-" + std::to_string(std::hash<std::string>{}(indexUrl)) + ".json";
    if (std::string e = manager::download(indexUrl, index, nullptr); !e.empty()) return e;
    std::string text = readText(index);
    unlink(index.c_str());
    for (const std::string& pkg : packages) {
        std::string error;
        manager::IndexApk apk = manager::pickApk(text, pkg, manager::androidAbi(), error);
        if (!error.empty()) return error;
        // installed at this version or newer: nothing to do (the manager's Update of an add-on that is current)
        std::string have = property(join(appDir(layout, pkg), "info.properties"), "versionCode");
        if (!have.empty() && atoll(have.c_str()) >= apk.versionCode) { step(id, "running", pkg + " is up to date"); continue; }
        std::string file = manager::downloadsDir() + "/" + baseName(apk.url);
        step(id, "running", "Downloading " + pkg);
        if (std::string e = manager::fetch(apk.url, apk.sha256, file, progress); !e.empty()) return e;
        step(id, "running", "Installing " + pkg);
        installApp(layout, inst, {file});
        unlink(file.c_str());
    }
    return "";
}
}  // namespace

int setupCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args) {
    Options o;
    o.bundle = getenvOr("APKLAYER_RUNTIME_BUNDLE", "");
    for (size_t i = 0; i < args.size(); i++) {
        if (args[i] == "--check") o.check = true;
        else if (args[i] == "--update") o.update = true;
        else if (args[i] == "--microg") o.microg = true;
        else if (args[i] == "--fdroid") o.fdroid = true;
        else if (args[i] == "--bundle" && i + 1 < args.size()) o.bundle = args[++i];
        else if (args[i] == "--move-data" && i + 1 < args.size()) o.moveData = args[++i];
        else die("setup [--check] [--update] [--bundle <file|file://|URL>] [--microg] [--fdroid] | setup --move-data <dir>");
    }
    if (!o.moveData.empty()) {
        std::string e = moveData(layout, o.moveData);
        step("data", e.empty() ? "done" : "failed", e.empty() ? "apklayer's data is in " + o.moveData + " (" + join(layout.dataHome, "apklayer") + " links to it)" : e);
        return e.empty() ? 0 : 1;
    }
    if (layout.runtimeVersion.empty()) die("setup is for the installed package (a source tree has its own runtime)");
    const std::string root = manager::runtimeRoot(), downloads = root + "/.downloads";  // a bundle downloads beside its runtime: one file system to check
    std::string have = manager::installedRuntime(root), note;

    // 1. binder: the userspace one needs nothing; the kernel's (apklayer binder kernel) its devices (root's: tools/host-setup/binder.sh)
    bool binder = binderReady(instance.root, instance.id);
    step("binder", binder ? "done" : "failed", userspaceBinder(instance.root) ? "Userspace binder for instance " + instance.id + " (no kernel devices needed)"
                                               : binder ? "Kernel binder ready for instance " + instance.id : binderDeviceWhy(instance.id));
    std::string java = javaProblem(layout);
    step("java", java.empty() ? "done" : "failed", java.empty() ? "Java 21" : java);
    std::string noSandbox = sandboxUnavailable();
    step("sandbox", sandboxOn() ? "done" : "skipped", sandboxOn() ? "Each app runs as its own user" : noSandbox.empty() ? "Off (APKLAYER_SANDBOX=1 turns it on)" : "Off: " + noSandbox);

    // 2. the runtime: --bundle, else the current release's (releases.json), else what the package names
    manager::Release release;
    std::string error;
    if (o.bundle.empty() && (have.empty() || o.update || o.check)) {
        std::string file = manager::downloadsDir() + "/releases.json";
        error = manager::download(manager::releasesUrl(), file, nullptr);
        if (error.empty()) release = manager::parseLatest(readText(file), manager::osCodename(), manager::cpuNames(), error);
        if (error.empty()) printf("latest\t%s\t%s\t%s\n", release.version.c_str(), release.runtimeVersion.c_str(), release.date.c_str());
    }
    const bool newer = release.runtime.found() && !release.runtimeVersion.empty() && release.runtimeVersion != have;
    if (o.check) {
        step("runtime", have.empty() ? "todo" : newer ? "todo" : "done", have.empty() ? "Not installed" : newer ? "Version " + release.runtimeVersion + " is available (have " + have + ")" : "Version " + have);
        step("providers", "done", providersDetail(layout, instance));
        bool current = systemApps(join(root, "current/system-apps"), join(layout.dataHome, "apklayer/system-apps.sha256")).changed.empty();
        step("system-apps", current ? "done" : "todo", current ? "Providers, documents and the WebView" : "The runtime has new providers, documents or WebView");
        return 0;
    }
    // the first setup of a phone whose home is on its small system partition: the data on /userdata from the start (nothing installed yet to move)
    const std::string marker = join(layout.dataHome, "apklayer/setup-done");
    if (have.empty() && !exists(marker) && listDir(join(layout.dataHome, "apklayer/apps")).empty() && smallDataHome(layout)) {
        std::string big = bigDataDir(layout), e = moveData(layout, big);
        step("runtime", "running", e.empty() ? "apklayer's data goes to " + big + " (the home's file system is small)" : "apklayer's data stays in the home: " + e);
    }
    if (!o.bundle.empty() && (have.empty() || o.update)) {
        step("runtime", "running", "Unpacking " + baseName(o.bundle));
        std::string src = o.bundle.rfind("file://", 0) == 0 ? manager::fileUriPath(o.bundle) : o.bundle, file = src;
        std::string sum;
        if (src.rfind("http", 0) == 0) {
            file = downloads + "/" + baseName(src);
            std::string sumFile = file + ".sha256";
            if (std::string e = manager::download(src + ".sha256", sumFile, nullptr); !e.empty()) { step("runtime", "failed", e); return 1; }
            sum = trim(readText(sumFile)).substr(0, 64);
            unlink(sumFile.c_str());
            if (std::string e = manager::fetch(src, sum, file, progress); !e.empty()) return runtimeFailed(layout, e);
        } else if (auto text = readFile(src + ".sha256")) {
            if (trim(*text).substr(0, 64) != manager::sha256File(src)) { step("runtime", "failed", "sha256 mismatch for " + baseName(src)); return 1; }
        }
        std::string e = manager::unpackRuntime(file, root, layout.runtimeVersion, &note);
        if (file != src) unlink(file.c_str());
        if (!e.empty()) return runtimeFailed(layout, e);
        have = layout.runtimeVersion;
    } else if (o.bundle.empty() && (have.empty() || (o.update && newer))) {
        if (!release.runtime.found()) { step("runtime", "failed", error.empty() ? "The release has no runtime for " + manager::cpuNames()[0] : error); return 1; }
        // the bundle and its contents, before a byte is downloaded (releases.json before 0.4.10 lists no sizes: unpackRuntime checks the contents)
        const long long unpacked = release.runtime.unpacked;
        if (std::string e = manager::runtimeRoom(root, release.runtime.size + unpacked + unpacked / 10, &note); !e.empty()) return runtimeFailed(layout, e);
        step("runtime", "running", "Downloading runtime " + release.runtimeVersion);
        std::string file = downloads + "/" + baseName(release.runtime.url);
        std::string e = manager::fetch(release.runtime.url, release.runtime.sha256, file, progress);
        if (e.empty()) e = manager::unpackRuntime(file, root, release.runtimeVersion, &note);
        unlink(file.c_str());
        if (!e.empty()) return runtimeFailed(layout, e);
        have = release.runtimeVersion;
    }
    if (!note.empty()) step("runtime", "running", note);  // the runtimes removed to make room
    if (have.empty()) { step("runtime", "failed", "No complete Android runtime: run setup again (a download or --bundle <file>)"); return 1; }
    Layout fresh = Layout::detect(false);  // the unpacked bundle is the native dir now: link the package's half in, instrument the framework (a few minutes)
    step("runtime", "running", "Setting up version " + have + " (a few minutes after an update)");
    fresh.requireRuntime();
    if (!fresh.setUpBundle(false)) { step("runtime", "failed", "Setting up the runtime in " + fresh.native + " failed: apklayer setup in a terminal shows why"); return 1; }
    step("runtime", "done", "Version " + have);

    // 3. the instance's providers: automatic (a Halium device's graphics, camera and vibrator are the device's), nothing to write
    step("providers", "done", providersDetail(fresh, instance));

    // 4. the system apps of the bundle (providers, documents, the WebView): the ones that changed since the last setup (a setup from before the
    // record installs all once), replaced as a system update replaces them (`install --system` of an installed package keeps data, uid, mark)
    const bool first = !exists(marker);
    const std::string record = join(layout.dataHome, "apklayer/system-apps.sha256");
    SystemApps apps = systemApps(join(root, "current/system-apps"), record);
    std::set<std::string> failed;
    for (size_t i = 0; i < apps.changed.size(); i++) {
        const std::string& f = apps.changed[i].first;
        step("system-apps", "running", "Installing " + f + " (" + std::to_string(i + 1) + " of " + std::to_string(apps.changed.size()) + ")");
        progress(static_cast<double>(i) / apps.changed.size());
        Spawn s;
        s.argv = {layout.cliBinary(), "--instance", instance.id, "install", "--system", join(root, "current/system-apps/" + f)};
        s.env = currentEnv();
        int rc = runAndWait(s);
        if (rc != 0 && rc != kInstallDowngrade) failed.insert(f);  // a downgrade: the user installed a newer one, theirs stays
    }
    std::string text, names;  // a failed APK is left out: tried again at the next setup
    for (const auto& [f, sum] : apps.all) {
        if (!failed.count(f)) text += sum + " " + f + "\n";
        else names += (names.empty() ? "" : "; ") + f;
    }
    makeDirs(dirName(record));
    writeFile(record, text);
    // the WebView provider's switch (Chromium's sandbox off) is the instance's own file, written at launch (cli/run.cpp): nothing for root
    step("system-apps", failed.empty() ? "done" : "failed", !failed.empty() ? names : apps.changed.empty() ? "Installed" : std::to_string(apps.changed.size()) + " installed");
    if (failed.empty() && first) {
        writeFile(marker, "1\n");
        // a phone: the instance always on (its stack and hub up from login, the spare ready), so no launch waits for them (~10 s of a cold start
        // on the Volla X23); once, at the first setup: the user's own choice stays (`apklayer instance autostart <id> off`)
        if (isHalium() && !autostartOn(instance)) autostartCommand(fresh, {instance.id, "on"});
    }

    // 5. the optional ones
    auto extra = [&](const char* id, const std::string& index, const std::vector<std::string>& packages) {
        std::string e = installFromIndex(fresh, instance, id, index, packages);
        step(id, e.empty() ? "done" : "failed", e.empty() ? "Installed" : e);
    };
    if (o.microg) extra("microg", envOr("APKLAYER_MICROG_INDEX", "https://microg.org/fdroid/repo/index-v1.json"), {"com.google.android.gms", "com.android.vending"});
    if (o.fdroid) extra("fdroid", envOr("APKLAYER_FDROID_INDEX", "https://f-droid.org/repo/index-v1.json"), {"org.fdroid.fdroid"});
    return anyFailed ? 1 : 0;
}

}  // namespace apklayer
