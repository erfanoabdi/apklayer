#include "layout.h"

#include <unistd.h>

namespace apklayer {

namespace {
bool looksLikeRoot(const std::string& dir) { return isDir(join(dir, "bridge")) && isDir(join(dir, "scripts")); }

// ~/.config/apklayer/env: lines of KEY=VALUE or `export KEY=VALUE`, applied to the environment unless already set (menu launches get them too).
void applyConfig() {
    std::string file = join(getenvOr("XDG_CONFIG_HOME", join(homeDir(), ".config")), "apklayer/env");
    auto text = readFile(file);
    if (!text) return;
    for (const std::string& raw : split(*text, '\n')) {
        std::string line = trim(raw);
        if (line.empty() || line[0] == '#') continue;
        if (line.rfind("export ", 0) == 0) line = trim(line.substr(7));
        size_t eq = line.find('=');
        if (eq == std::string::npos) continue;
        std::string value = trim(line.substr(eq + 1));
        if (value.size() >= 2 && (value.front() == '"' || value.front() == '\'') && value.back() == value.front()) value = value.substr(1, value.size() - 2);
        setenv(trim(line.substr(0, eq)).c_str(), value.c_str(), 0);
    }
}
}  // namespace

Layout Layout::detect() {
    applyConfig();
    Layout l;
    std::string exe = realPath("/proc/self/exe");
    l.root = getenvOr("APKLAYER_ROOT", "");
    for (std::string dir = dirName(exe); l.root.empty() && dir != "/"; dir = dirName(dir)) {
        if (looksLikeRoot(dir)) l.root = dir;
        else if (looksLikeRoot(join(dir, "share/apklayer"))) l.root = join(dir, "share/apklayer");
    }
    if (l.root.empty()) die("cannot find the apklayer root (set APKLAYER_ROOT)");
    l.build = getenvOr("APKLAYER_BUILD", join(l.root, "b"));
    l.native = getenvOr("APKLAYER_NATIVE", join(homeDir(), "arm64-native"));
    l.androidLibs = getenvOr("APKLAYER_ANDROID_LIBS", join(homeDir(), "android-arm64/lib64"));
    l.bionic = getenvOr("APKLAYER_BT", join(homeDir(), "bt-arm/bionic_translation/b"));
    l.deps = getenvOr("APKLAYER_DEPS", join(homeDir(), ".cache/apklayer/deps"));
    l.dataHome = getenvOr("XDG_DATA_HOME", join(homeDir(), ".local/share"));
    l.appsDir = join(l.dataHome, "apklayer/apps");
    l.stateHome = getenvOr("XDG_STATE_HOME", join(homeDir(), ".local/state"));
    l.runtimeHome = getenvOr("XDG_RUNTIME_DIR", "/run/user/" + std::to_string(getuid()));
    return l;
}

std::string Layout::cliBinary() const { return realPath("/proc/self/exe"); }

Instance Instance::open(const Layout& layout, const std::string& id) {
    Instance i;
    i.id = id;
    i.stateDir = getenvOr("APKLAYER_LOGS", join(layout.stateHome, "apklayer/" + id));  // tests may point the logs elsewhere
    i.runtimeDir = join(layout.runtimeHome, "apklayer/" + id);
    i.storageDir = join(layout.dataHome, "apklayer/" + (id == "default" ? std::string("0") : id) + "/storage");
    makeDirs(join(i.storageDir, "emulated/0"));
    makeDirs(i.stateDir);
    makeDirs(i.runtimeDir);
    return i;
}

}  // namespace apklayer
