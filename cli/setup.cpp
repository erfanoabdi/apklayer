// `apklayer setup`: the first run and the runtime updates, in one command (the manager is a front-end that runs it and reads its lines).
// Idempotent: it does what is missing or stale. Output, one per line, tab separated:
//   step <id> <state> <detail>      id: binder runtime providers system-apps webview microg fdroid; state: todo running done failed skipped
//   progress <0..1 or -1>           the running step's download
//   latest <version> <runtime version> <date>   (--check, when releases.json was read)
#include "setup.h"

#include <algorithm>
#include <cstdio>
#include <unistd.h>

#include "apps.h"
#include "binder.h"
#include "fetch.h"
#include "halium.h"
#include "providers.h"
#include "releases.h"

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

struct Options {
    bool check = false, update = false, microg = false, fdroid = false;
    std::string bundle;  // a file, file:// or URL of the runtime bundle: no releases.json lookup
};

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
        else die("setup [--check] [--update] [--bundle <file|file://|URL>] [--microg] [--fdroid]");
    }
    if (layout.runtimeVersion.empty()) die("setup is for the installed package (a source tree has its own runtime)");
    const std::string root = manager::runtimeRoot();
    std::string have = manager::installedRuntime(root);

    // 1. binder: the instance's devices (root's: the package's service makes them at boot, the manager asks pkexec)
    bool binder = !binderDevice(instance.id).empty() && exists(binderDevice(instance.id));
    step("binder", binder ? "done" : "failed", binder ? "Ready for instance " + instance.id : binderDeviceWhy(instance.id));

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
        step("providers", exists(providersFile(instance)) || !isHalium() ? "done" : "todo", isHalium() ? "Halium device: graphics and camera = device" : "Defaults");
        step("system-apps", exists(join(layout.dataHome, "apklayer/setup-done")) ? "done" : "todo", "Providers, documents and the WebView");
        return 0;
    }
    if (!o.bundle.empty() && (have.empty() || o.update)) {
        step("runtime", "running", "Unpacking " + baseName(o.bundle));
        std::string src = o.bundle.rfind("file://", 0) == 0 ? manager::fileUriPath(o.bundle) : o.bundle, file = src;
        std::string sum;
        if (src.rfind("http", 0) == 0) {
            file = manager::downloadsDir() + "/" + baseName(src);
            std::string sumFile = file + ".sha256";
            if (std::string e = manager::download(src + ".sha256", sumFile, nullptr); !e.empty()) { step("runtime", "failed", e); return 1; }
            sum = trim(readText(sumFile)).substr(0, 64);
            if (std::string e = manager::fetch(src, sum, file, progress); !e.empty()) { step("runtime", "failed", e); return 1; }
        } else if (auto text = readFile(src + ".sha256")) {
            if (trim(*text).substr(0, 64) != manager::sha256File(src)) { step("runtime", "failed", "sha256 mismatch for " + baseName(src)); return 1; }
        }
        if (std::string e = manager::unpackRuntime(file, root, layout.runtimeVersion); !e.empty()) { step("runtime", "failed", e); return 1; }
        if (file != src) unlink(file.c_str());
        have = layout.runtimeVersion;
    } else if (o.bundle.empty() && (have.empty() || (o.update && newer))) {
        if (!release.runtime.found()) { step("runtime", "failed", error.empty() ? "The release has no runtime for " + manager::cpuNames()[0] : error); return 1; }
        step("runtime", "running", "Downloading runtime " + release.runtimeVersion);
        std::string file = manager::downloadsDir() + "/" + baseName(release.runtime.url);
        std::string e = manager::fetch(release.runtime.url, release.runtime.sha256, file, progress);
        if (e.empty()) e = manager::unpackRuntime(file, root, release.runtimeVersion);
        unlink(file.c_str());
        if (!e.empty()) { step("runtime", "failed", e); return 1; }
        have = release.runtimeVersion;
    }
    Layout fresh = Layout::detect();  // the unpacked bundle is the native dir now: link the package's half in, instrument the framework (a few minutes)
    step("runtime", "running", "Setting up version " + have + " (a few minutes after an update)");
    fresh.requireRuntime();
    fresh.setUpBundle();
    step("runtime", "done", "Version " + have);

    // 3. the instance's providers: a Halium device's graphics and camera are the device's
    seedHaliumProviders(instance);
    step("providers", "done", isHalium() ? "Halium device: " + trim(readText(providersFile(instance)).empty() ? "defaults" : "device providers") : "Defaults");

    // 4. the system apps of the bundle (providers, documents, the WebView), once
    const std::string marker = join(layout.dataHome, "apklayer/setup-done");
    if (!exists(marker)) {
        const std::string dir = join(root, "current/system-apps");
        std::vector<std::string> list;
        for (const std::string& f : listDir(dir)) if (f.size() > 4 && f.substr(f.size() - 4) == ".apk") list.push_back(f);
        std::sort(list.begin(), list.end());
        std::string failed;
        for (size_t i = 0; i < list.size(); i++) {
            step("system-apps", "running", "Installing " + list[i] + " (" + std::to_string(i + 1) + " of " + std::to_string(list.size()) + ")");
            progress(static_cast<double>(i) / list.size());
            Spawn s;
            s.argv = {layout.cliBinary(), "--instance", instance.id, "install", "--system", join(dir, list[i])};
            s.env = currentEnv();
            if (runAndWait(s) != 0) failed += (failed.empty() ? "" : "; ") + list[i];
        }
        // the WebView provider's renderer runs without Chromium's sandbox, a switch only root can write (tools/host-setup/webview.sh)
        if (std::find(list.begin(), list.end(), "webview.apk") != list.end() && !exists("/data/local/tmp/webview-command-line")) {
            std::string script = join(layout.root, "tools/host-setup/webview.sh");
            step("webview", "running", "The WebView's switch (asks for your password)");
            Spawn s;
            s.argv = {"pkexec", "sh", script};
            s.env = currentEnv();
            step("webview", runAndWait(s) == 0 ? "done" : "skipped", "sudo sh " + script + " enables the WebView");
        }
        step("system-apps", failed.empty() ? "done" : "failed", failed.empty() ? std::to_string(list.size()) + " installed" : failed);
        if (failed.empty()) { makeDirs(dirName(marker)); writeFile(marker, "1\n"); }
    } else step("system-apps", "done", "Installed");

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
