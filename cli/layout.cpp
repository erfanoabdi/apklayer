#include "layout.h"

#include "config.h"
#include "env.h"

#include <sys/stat.h>
#include <unistd.h>

namespace apklayer {

namespace {
bool looksLikeRoot(const std::string& dir) { return isDir(join(dir, "bridge")) && isDir(join(dir, "tools")); }

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

// This program's path. A click (Ubuntu Touch) lives in /opt/click.ubuntu.com/<app>/<version>/, which an update removes: what outlives this
// process (launchers, the always-on unit, the runtime's links, -Dapklayer.cli) names it through the click's own <app>/current link instead
std::string selfPath() {
    std::string exe = realPath("/proc/self/exe");
    const std::string clicks = "/opt/click.ubuntu.com/";
    size_t app = exe.rfind(clicks, 0) == 0 ? exe.find('/', clicks.size()) : std::string::npos;
    size_t version = app == std::string::npos ? app : exe.find('/', app + 1);
    if (version == std::string::npos) return exe;
    std::string current = exe.substr(0, app) + "/current";
    return realPath(current) == exe.substr(0, version) ? current + exe.substr(version) : exe;
}
}  // namespace

Layout Layout::detect(bool setUp) {
    applyConfig();
    applySettings();  // ~/.config/apklayer/config (cli/config.cpp)
    Layout l;
    std::string exe = selfPath();
    l.root = getenvOr("APKLAYER_ROOT", "");
    for (std::string dir = dirName(exe); l.root.empty() && dir != "/"; dir = dirName(dir)) {
        if (looksLikeRoot(dir)) l.root = dir;
        else if (looksLikeRoot(join(dir, "share/apklayer"))) l.root = join(dir, "share/apklayer");
    }
    if (l.root.empty()) die("cannot find the apklayer root (set APKLAYER_ROOT)");
    // the build the CLI lives in (<build>/cli/apklayer: a meson build dir, or the installed /usr/lib/apklayer)
    std::string own = dirName(dirName(exe));
    l.build = getenvOr("APKLAYER_BUILD", exists(join(own, "bridge/shell/apklayer-shell")) ? own : join(l.root, "b"));
    l.dataHome = getenvOr("XDG_DATA_HOME", join(homeDir(), ".local/share"));
    // installed (debian/): the Android runtime bundle unpacked per user, runtime/current as the manager switches it, else the version the
    // package names (docs/RELEASES.md)
    l.runtimeVersion = property(join(l.root, "versions"), "runtime");
    bool installed = !l.runtimeVersion.empty();
    std::string runtimes = join(l.dataHome, "apklayer/runtime");
    std::string bundle = join(runtimes, exists(join(runtimes, "current/runtime.json")) ? "current" : l.runtimeVersion);
    // `meson install` (meson.build): <lib>/native is the runtime's native dir, complete (with the Android half) unless the build left that to the
    // bundle (the deb); a source tree's CLI uses its build's install
    std::string lib = installed ? l.build : APKLAYER_LIB;
    bool complete = isDir(join(lib, "native/aosp/android-all"));
    bool ours = installed || isDir(join(lib, "native"));
    l.native = getenvOr("APKLAYER_NATIVE", complete || !installed ? (ours ? join(lib, "native") : join(homeDir(), "arm64-native")) : bundle);
    l.androidLibs = getenvOr("APKLAYER_ANDROID_LIBS", ours ? join(l.native, "android-libs") : join(homeDir(), "android-arm64/lib64"));
    l.bionic = getenvOr("APKLAYER_BT", ours ? join(lib, "bionic_translation/b") : join(homeDir(), "bt-arm/bionic_translation/b"));
    l.deps = getenvOr("APKLAYER_DEPS", ours ? l.native : join(homeDir(), ".cache/apklayer/deps"));
    l.arm32 = getenvOr("APKLAYER_ARM32", join(l.native, "arm32"));
    if (setUp && installed && l.native == bundle) l.setUpBundle();
    l.appsDir = join(l.dataHome, "apklayer/apps");
    l.stateHome = getenvOr("XDG_STATE_HOME", join(homeDir(), ".local/state"));
    l.runtimeHome = getenvOr("XDG_RUNTIME_DIR", "/run/user/" + std::to_string(getuid()));
    return l;
}

// The package's half of the runtime (<build>/native) linked into the unpacked bundle, the JNI shim put into its framework jars and the instrumented
// framework made (tools/setup-runtime.sh, under a lock): again whenever the package's native-runtime.jar changes.
bool Layout::setUpBundle(bool fatal) const {
    if (!exists(join(native, "runtime.json"))) return true;  // not downloaded yet: nativeEnv says so when something needs it
    struct stat made{}, jar{};
    if (stat(join(build, "native/native-runtime.jar").c_str(), &jar) == 0 && stat(join(native, ".setup").c_str(), &made) == 0 && made.st_mtime == jar.st_mtime) return true;
    Spawn setup;
    setup.argv = {"sh", join(root, "tools/setup-runtime.sh"), native, join(build, "native")};
    setup.env = currentEnv();
    setup.env["APKLAYER_BT"] = bionic;
    setup.env["APKLAYER_ANDROID_LIBS"] = androidLibs;
    setup.env["APKLAYER_JAVA"] = findJava(*this);  // the JVM apps run on (APKLAYER_NATIVE_JAVA...), not just PATH's java
    if (runAndWait(setup) == 0) return true;
    if (fatal) die("could not set up the Android runtime in " + native);
    return false;
}

void Layout::requireRuntime() const {
    if (!runtimeVersion.empty() && !exists(join(native, "runtime.json")))
        die("no Android runtime " + runtimeVersion + " in " + native + ": open apklayer-manager, or unpack apklayer-runtime-<cpu>-" + runtimeVersion +
            ".tar.zst there (docs/RELEASES.md)");
}

// The 32-bit runtime is a meson install for armhf (<arm32>/lib/apklayer) with its JVM and Android libraries beside it; the architecture-independent
// half (framework jars, host classes, android-root) is this layout's, linked into its native dir on first use.
Layout Layout::arm32Layout() const {
    Layout a = *this;
    a.native = join(arm32, "lib/apklayer/native");
    a.bionic = join(arm32, "lib/apklayer/bionic_translation/b");
    a.androidLibs = join(arm32, "android-libs");
    a.deps = a.native;
    a.java = join(arm32, "bin/java");
    for (const char* shared : {"aosp", "android-root", "jdk-patch", "native-runtime.jar", "android-stub.jar", "libs", "runtime.json"}) {
        std::string from = join(std::string(shared) == "android-stub.jar" ? deps : native, shared), to = join(a.native, shared);
        if (exists(from) && !exists(to) && symlink(from.c_str(), to.c_str()) != 0) die("cannot link " + from + " into " + a.native);
    }
    return a;
}

std::string Layout::cliBinary() const { return selfPath(); }

Instance Instance::open(const Layout& layout, const std::string& id) {
    Instance i;
    i.id = id;
    i.stateDir = getenvOr("APKLAYER_LOGS", join(layout.stateHome, "apklayer/" + id));  // tests may point the logs elsewhere
    i.runtimeDir = join(layout.runtimeHome, "apklayer/" + id);
    i.root = join(layout.dataHome, "apklayer/" + (id == "default" ? std::string("0") : id));
    i.storageDir = join(i.root, "storage");
    makeDirs(join(i.storageDir, "emulated/0"));  // /storage/emulated/0, /sdcard and /data/media/0 of the instance (APKLAYER_STORAGE, the path layer)
    // /data of the instance (APKLAYER_DATA, the path layer), so the shared native directory stays read-only. What init makes at boot: the system
    // user's directory (SettingsProvider's files); keystore2's and SurfaceFlinger's come with the stack (cli/stack.cpp).
    i.dataDir = join(i.root, "data");
    makeDirs(join(i.dataDir, "system/users/0"));
    makeDirs(i.stateDir);
    makeDirs(i.runtimeDir);
    return i;
}

}  // namespace apklayer
