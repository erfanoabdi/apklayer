#include "stack.h"
#include "binder.h"
#include "sandbox.h"

#include "providers.h"
#include "gpus.h"

#include <fcntl.h>
#include <cerrno>
#include <poll.h>
#include <set>
#include <signal.h>
#include "linux_compat.h"
#include <sys/file.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/signalfd.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

namespace apklayer {

namespace {
struct Service {
    std::string name;
    int pid;
    int pidfd;
    Spawn spec;        // how it was started: an optional service that exits is started again the same way
    int restarts = 0;
};

// What the stack cannot run without: these ending ends the stack (and the test tool's end is the end of a test run). SurfaceFlinger holds its
// composer and allocator connections for its lifetime, so they are as essential. Every other service is restarted, as init restarts a HAL.
bool essential(const std::string& name) {
    for (const char* n : {"servicemanager", "surfaceflinger", "composer", "allocator", "tool", "test-display"})
        if (name == n) return true;
    return false;
}
const int kMaxRestarts = 5;  // like init's crash loop limit: a service that keeps dying stays down instead of restarting forever

bool staged(const Layout& l, const char* lib) { return exists(join(l.androidLibs, lib)); }

// A vintf manifest is declared only when this stack provides the service behind it: staged into the instance's own root (the path layer's
// APKLAYER_INSTANCE_ROOT, made fresh with each stack), never into the native tree other instances share. That root's manifest directory
// replaces the shared one, so it carries the shared fragments too.
void stageManifest(const Instance& inst, const Layout& l, bool provided, const std::string& name, const std::string& source, const char* partition = "vendor") {
    const std::string dir = std::string(partition) + "/etc/vintf/manifest";
    const std::string own = join(join(inst.runtimeDir, "android-root"), dir), shared = join(l.native, "android-root/" + dir);
    if (!exists(own)) {
        makeDirs(own);
        for (const std::string& f : listDir(shared))
            if (f.rfind("apklayer-", 0) != 0) if (auto text = readFile(join(shared, f))) writeFile(join(own, f), *text);  // the tree's own fragments, not other instances' staging
    }
    if (provided)
        if (auto text = readFile(join(l.root, source))) writeFile(join(own, name), *text);
}

// media = device: the media framework reads /vendor/etc/media_codecs*.xml (the codec list's names, limits, ranks), and the shared android-root's own
// media_codecs.xml names apklayer's components: the instance serves the device's files (the path layer takes the instance's root first).
void stageDeviceCodecXml(const Instance& inst) {
    const std::string own = join(inst.runtimeDir, "android-root/vendor/etc");
    makeDirs(own);
    for (const std::string& f : listDir("/vendor/etc"))
        if (f.rfind("media_codecs", 0) == 0 && f.size() > 4 && f.substr(f.size() - 4) == ".xml")
            if (auto text = readFile(join("/vendor/etc", f))) writeFile(join(own, f), *text);
}

// graphics = device (a Halium device, docs/HALIUM.md): the instance's VINTF declares neither apklayer's AIDL allocator nor its mapper
// (libui then takes the device's gralloc 4: allocator@4.0 through the host context, the passthrough mapper); own copies of the
// shared manifests without those HALs, which the path layer serves before the shared ones.
void undeclareOwnGraphics(const Instance& inst, const Layout& l) {
    const std::string own = join(inst.runtimeDir, "android-root/vendor/etc/vintf"), shared = join(l.native, "android-root/vendor/etc/vintf");
    for (const std::string& f : {std::string("manifest.xml"), std::string("manifest/apklayer.xml")}) {
        auto text = readFile(join(own, f));
        if (!text) text = readFile(join(shared, f));
        if (!text) continue;
        std::string out;
        size_t at = 0;
        for (size_t start; (start = text->find("<hal", at)) != std::string::npos;) {
            size_t end = text->find("</hal>", start);
            if (end == std::string::npos) break;
            end += 6;
            std::string hal = text->substr(start, end - start);
            out += text->substr(at, start - at);
            if (hal.find("android.hardware.graphics.allocator") == std::string::npos && hal.find("<name>mapper</name>") == std::string::npos) out += hal;
            at = end;
        }
        out += text->substr(at);
        makeDirs(dirName(join(own, f)));
        writeFile(join(own, f), out);
    }
}

// The pid of the shell listening on this session's socket (0: none): what stops it, never a match by name (other sessions' shells run too).
int shellPid(const Layout& l) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", l.shellSocket().c_str());
    ucred peer{};
    socklen_t len = sizeof peer;
    bool ok = connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof addr) == 0 && getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &peer, &len) == 0;
    close(fd);
    return ok ? peer.pid : 0;
}

// The session's window shell serves every instance of the session (their runtime directories under it): it ends with the last stack, so stopping
// or removing one instance leaves the others' windows alone.
void endShellUnlessShared(const Layout& l, const std::string& leaving) {
    for (const std::string& id : listDir(join(l.runtimeHome, "apklayer"))) {
        if (id == leaving || !isDir(join(l.runtimeHome, "apklayer/" + id))) continue;
        auto pid = readFile(join(l.stateHome, "apklayer/" + id + "/stack.pid"));
        if (pid && alive(atoi(pid->c_str()))) return;
    }
    terminate(shellPid(l));
}

// Android's way: readiness is a system property of the instance (apklayer.stack.ready=1), waited on with the property store's own wait.
const char* kReadyProperty = "apklayer.stack.ready";

bool propertyIs(const Layout& l, const NativeEnv& ne, const char* name, const char* value) {
    Spawn get;
    get.argv = {join(l.native, "bin/aprop"), "get", name};
    get.env = ne.env;
    return trim(runCapture(get)) == value;
}

bool waitReady(const Layout& l, const NativeEnv& ne, int deathPidfd) { return waitForProperty(l, ne, kReadyProperty, "1", deathPidfd); }

}  // namespace

bool alive(int pid) { return pid > 0 && kill(pid, 0) == 0; }

// A process in a user namespace this user made (the sandbox's app processes: their environment is not readable, their command line an Android
// process name). shortcut: any such namespace counts (a container's too) when a recorded pid is checked; upgrade by comparing with the stack's
static bool inOwnUserNs(int pid) {
    int fd = open(("/proc/" + std::to_string(pid) + "/ns/user").c_str(), O_RDONLY | O_CLOEXEC);
    struct stat target{}, own{};
    uid_t owner = (uid_t)-1;
    bool ours = fd >= 0 && fstat(fd, &target) == 0 && ::stat("/proc/self/ns/user", &own) == 0 && target.st_ino != own.st_ino
                && ioctl(fd, NS_GET_OWNER_UID, &owner) == 0 && owner == getuid();
    if (fd >= 0) close(fd);
    return ours;
}

// SIGTERM, then wait for the exit on a pidfd (SIGKILL after 5 s): `stop` returns with the processes gone.
void terminate(int pid) {
    if (!alive(pid)) return;
    // a recycled pid is not ours: every process apklayer starts carries APKLAYER_INSTANCE (its command line may be an Android process name)
    if (auto env = readFile("/proc/" + std::to_string(pid) + "/environ"); !env || env->find("APKLAYER_INSTANCE=") == std::string::npos) {
        auto cmd = readFile("/proc/" + std::to_string(pid) + "/cmdline");
        if ((!cmd || cmd->find("apklayer") == std::string::npos) && !inOwnUserNs(pid)) return;
    }
    int fd = pidfdOpen(pid);
    kill(pid, SIGTERM);
    pollfd p{fd, POLLIN, 0};
    if (fd >= 0 && poll(&p, 1, 5000) == 0) {
        kill(pid, SIGKILL);
        poll(&p, 1, 2000);
    }
    if (fd >= 0) close(fd);
}

void setProperty(const Layout& l, const NativeEnv& ne, const char* name, const char* value) {
    Spawn set;
    set.argv = {join(l.native, "bin/aprop"), "set", name, value};
    set.env = ne.env;
    runAndWait(set);
}

bool waitForProperty(const Layout& l, const NativeEnv& ne, const char* name, const char* value, int deathPidfd) {
    Spawn wait;
    wait.argv = {join(l.native, "bin/aprop"), "wait", name, value};
    wait.env = ne.env;
    wait.dieWithParent = true;  // a caller's timeout that kills this process (`timeout apklayer wait hub`) ends the wait too
    int pid = spawn(wait);
    int fd = pidfdOpen(pid);
    pollfd fds[2] = {{fd, POLLIN, 0}, {deathPidfd, POLLIN, 0}};
    while (poll(fds, deathPidfd >= 0 ? 2 : 1, -1) < 0 && errno == EINTR) {}
    bool ok = fds[0].revents & POLLIN;
    if (!ok) kill(pid, SIGTERM);
    waitpid(pid, nullptr, 0);
    close(fd);
    return ok;
}

// Returns with the session's shell listening. One start at a time (the stack daemon and `run` both ask; two shells would each own half the
// clients), and the started shell reports readiness itself: it writes to APKLAYER_SHELL_READY_FD after listen() (EOF: it died first). A shell
// that neither listens nor dies (a compositor that does not answer its roundtrips) is ended after kShellStartMs: callers fail instead of blocking.
static const int kShellStartMs = 10000;
bool ensureShell(const Layout& l, const Instance& inst, const NativeEnv& env) {
    makeDirs(join(l.runtimeHome, "apklayer"));
    int lock = open(join(l.runtimeHome, "apklayer/shell.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0600);
    flock(lock, LOCK_EX);
    bool ok = true;
    if (shellPid(l) == 0) {
        unlink(l.shellSocket().c_str());
        int ready[2];
        if (pipe2(ready, O_CLOEXEC) != 0) die("pipe failed");
        fcntl(ready[1], F_SETFD, 0);  // the shell inherits the write end
        Spawn shell;
        shell.argv = {l.shellBinary()};
        shell.env = env.env;
        shell.env["APKLAYER_SHELL_READY_FD"] = std::to_string(ready[1]);
        shell.logFile = join(inst.logs(), "shell.log");
        shell.newSession = true;  // outlives any instance; instances never stop it
        shell.unit = "apklayer-shell";  // every window's: never frozen with the app or manager that started it (cli/unit.h)
        shell.detach = true;
        int pid = spawn(shell);
        close(ready[1]);
        pollfd p{ready[0], POLLIN, 0};
        int n;
        while ((n = poll(&p, 1, kShellStartMs)) < 0 && errno == EINTR) {}
        char c;
        ok = n > 0 && read(ready[0], &c, 1) == 1;
        close(ready[0]);
        if (!ok) {
            fprintf(stderr, "apklayer: the window shell %s (Wayland display %s); log: %s\n",
                    n > 0 ? "exited before it listened" : ("did not listen within " + std::to_string(kShellStartMs / 1000) + " s").c_str(), getenvOr("WAYLAND_DISPLAY", "unset").c_str(), shell.logFile.c_str());
            if (n == 0) terminate(pid);
        }
    }
    close(lock);
    return ok;
}

// keystore2 keeps its database in /data/misc/keystore, which the path layer serves from the instance's /data
// The software Gatekeeper and gatekeeperd (auth-bound keys) come with the keystore when both are staged
static bool gatekeeper(const Layout& l) {
    return staged(l, "libapklayer_keystore2.so") && staged(l, "libapklayer_gatekeeper.so") && staged(l, "libapklayer_gatekeeperd.so");
}
static std::string keystoreDir(const Instance& inst) {
    std::string dir = join(inst.dataDir, "misc/keystore");
    makeDirs(dir);
    return dir;
}

// What Android's init derives at boot (property_service.cpp): the fingerprint (and the build's display id, flavor, description) of the product the instance presents (bridge/native/apklayer.prop over
// the tree's build.prop, whose own fingerprint names the GSI's "generic" product) and the ABI lists of this machine's CPU. Set before the files
// are loaded: ro.* is write-once, so these win and every reader (natives, apklayer.host.HostBuild) sees one device.
static void setDerivedProperties(const Layout& l, const NativeEnv& ne, const std::vector<std::string>& files) {
    auto get = [&](const char* key) {
        for (const std::string& f : files)
            if (std::string v = property(f, key); !v.empty()) return v;
        return std::string();
    };
    // a build without BUILD_NUMBER numbers itself after its builder (eng.<user>[.<date>]); a device's incremental is a build number: the build's time
    std::string incremental = get("ro.build.version.incremental");
    if (incremental.empty() || incremental.rfind("eng.", 0) == 0) {
        incremental = get("ro.build.date.utc");
        setProperty(l, ne, "ro.build.version.incremental", incremental.c_str());
        setProperty(l, ne, "ro.system.build.version.incremental", incremental.c_str());
    }
    std::string fingerprint = get("ro.product.brand") + "/" + get("ro.product.name") + "/" + get("ro.product.device") + ":" + get("ro.build.version.release_or_codename") +
                              "/" + get("ro.build.id") + "/" + incremental + ":" + get("ro.build.type") + "/" + get("ro.build.tags");
    // a staged native bridge adds its ABI, as on an x86_64 device with one (ro.dalvik.vm.native.bridge names it; apps pick their libraries by the list)
    bool bridge = *kBridgeAbi && exists(join(l.androidLibs, kBridgeLib));
    std::string abis64 = std::string(kAbi) + (bridge ? std::string(",") + kBridgeAbi : "");
    std::string abis = abis64 + (*kAbi32 ? std::string(",") + kAbi32 : "");
    if (bridge) {
        setProperty(l, ne, "ro.dalvik.vm.native.bridge", kBridgeLib);
        setProperty(l, ne, "ro.berberis.flags", "glibc-host-thread-id-handoff");  // bionic_translation's thread slots are glibc's (native_bridge.c)
    }
    setProperty(l, ne, "ro.build.fingerprint", fingerprint.c_str());
    setProperty(l, ne, "ro.system.build.fingerprint", fingerprint.c_str());
    // as the build system writes them for the build type and tags the instance presents (apklayer.prop), not the tree's "userdebug ... test-keys"
    std::string flavor = get("ro.product.name") + "-" + get("ro.build.type");
    setProperty(l, ne, "ro.build.display.id", (get("ro.build.id") + " " + get("ro.build.tags")).c_str());
    setProperty(l, ne, "ro.build.flavor", flavor.c_str());
    setProperty(l, ne, "ro.build.description", (flavor + " " + get("ro.build.version.release_or_codename") + " " + get("ro.build.id") + " " +
                                                incremental + " " + get("ro.build.tags")).c_str());
    // the GLES version apps read (ActivityManager.getDeviceConfigurationInfo, the unnamed FeatureInfo): major << 16 | minor, from the host's
    // driver as apps' contexts get it (cli/env.cpp); a device prop file's value wins (ro.* is write-once)
    if (auto it = ne.env.find("APKLAYER_GLES_VERSION"); it != ne.env.end()) {
        unsigned major = 0, minor = 0;
        if (sscanf(it->second.c_str(), "%u.%u", &major, &minor) == 2) setProperty(l, ne, "ro.opengles.version", std::to_string(major << 16 | minor).c_str());
    }
    setProperty(l, ne, "ro.product.cpu.abi", kAbi);
    setProperty(l, ne, "ro.product.cpu.abilist", abis.c_str());
    setProperty(l, ne, "ro.product.cpu.abilist64", abis64.c_str());
    setProperty(l, ne, "ro.product.cpu.abilist32", kAbi32);
}

// graphics = device: the device's properties its vendor libraries read, as a prop file loaded before apklayer.prop (ro.* is write-once, so the
// device's values win). Android's libEGL and Vulkan loader pick the driver by ro.hardware.egl/vulkan (libGLES_<egl>.so, vulkan.<vulkan>.so);
// MediaTek's gralloc derives a buffer's allocation format (AFBC or not) from ro.board.platform and debug.mediatek.disp_decompress, and the
// mapper in apklayer's processes refuses a buffer the device's allocator made otherwise. Android's vendor namespaces (ro.vendor., vendor.)
// come along; other debug.* do not (debug.hwui.*, debug.sf.* would change apklayer's own renderer and compositor). A Halium device sets
// them at boot, outside its prop files (and /vendor/build.prop is root's): libhybris's getprop lists its live property area.
static std::string deviceGraphicsProperties(const Instance& inst) {
    Spawn get;
    get.argv = {"/usr/bin/getprop"};
    get.env = hostToolEnv();
    int status = 0;
    std::string out = exists("/usr/bin/getprop") ? runCapture(get, &status) : "", props;
    std::set<std::string> found;
    for (size_t at = 0, end; at < out.size(); at = end + 1) {
        end = out.find('\n', at);
        if (end == std::string::npos) end = out.size();
        std::string line = out.substr(at, end - at);  // [key]: [value]
        size_t k = line.find("]: [");
        if (line.size() < 6 || line[0] != '[' || k == std::string::npos || line.back() != ']') continue;
        std::string key = line.substr(1, k - 1), value = line.substr(k + 4, line.size() - k - 5);
        bool wanted = key == "ro.hardware.egl" || key == "ro.hardware.vulkan" || key == "ro.board.platform" || key == "ro.opengles.version";
        for (const char* prefix : {"ro.vendor.", "vendor.", "debug.mediatek."}) wanted = wanted || key.rfind(prefix, 0) == 0;
        if (!wanted || value.empty()) continue;
        props += key + "=" + value + "\n";
        found.insert(key);
    }
    for (const char* key : {"ro.hardware.egl", "ro.hardware.vulkan", "ro.board.platform"})
        if (!found.count(key)) fprintf(stderr, "apklayer: graphics = device, but the device's %s is unknown\n", key);
    std::string file = join(inst.runtimeDir, "device.prop");
    writeFile(file, props);
    return file;
}

// The instance's property store as a device's init fills it at boot: the prop files (overrides, the device's graphics values, the display's density,
// apklayer.prop, the tree's build.prop) and what init derives from them (setDerivedProperties). The stack's start, and `exec` without a stack.
static void loadProperties(const Layout& l, const Instance& inst, const NativeEnv& ne) {
    const std::string& n = l.native;
    std::vector<std::string> propFiles = {join(l.root, "bridge/native/apklayer.prop"), join(n, "android-root/props/system_build.prop")};
    // the instance's own overrides (`apklayer prop set`): first, as ro.* is write-once (and the fingerprint is derived from them), and again last, as
    // every other property keeps the last value loaded
    if (exists(inst.propOverrides())) { propFiles.insert(propFiles.begin(), inst.propOverrides()); propFiles.push_back(inst.propOverrides()); }
    if (deviceGraphics(l, inst)) propFiles.insert(propFiles.begin() + (propFiles.size() > 2), deviceGraphicsProperties(inst));  // after the overrides' first copy
    if (int density = displayDensity(l); density > 0 && density != 160) {  // ro.sf.lcd_density: this display's, before apklayer.prop's 160 (write-once)
        std::string file = join(inst.root, "display.prop");
        writeFile(file, "ro.sf.lcd_density=" + std::to_string(density) + "\n");
        propFiles.insert(propFiles.begin() + (exists(inst.propOverrides()) ? 1 : 0), file);
    }
    if (deviceDomain(l, inst, "media")) {
        // the device's Codec2 is HIDL (a Halium 12 vendor): libcodec2's client takes the HAL media.c2.hal.selection names, which apklayer.prop
        // sets to aidl: loaded last (a plain property keeps the last value)
        std::string file = join(inst.runtimeDir, "media-device.prop");
        writeFile(file, "media.c2.hal.selection=hidl\n");
        propFiles.push_back(file);
    }
    setDerivedProperties(l, ne, propFiles);
    Spawn prop;
    prop.argv = {join(n, "bin/aprop"), "load"};
    prop.argv.insert(prop.argv.end(), propFiles.begin(), propFiles.end());
    prop.env = ne.env;
    prop.logFile = join(inst.logs(), "aprop.log");
    runAndWait(prop);
}

int runStackDaemon(const Layout& l, const Instance& inst, const std::vector<std::string>& tool) {
    if (sandboxOn()) enterInstanceUserNs();  // first: everything the stack starts is in the instance's user namespace (cli/sandbox.cpp)
    if (!userspaceBinder(inst.root) && binderDevice(inst.id).empty()) die("instance " + inst.id + ": " + binderDeviceWhy(inst.id));  // cli/binder.cpp: no second instance without binderfs
    NativeEnv ne = nativeEnv(l, inst);
    const std::string& n = l.native;
    for (const std::string& f : listDir(inst.logs()))
        if (f.size() > 4 && f.substr(f.size() - 4) == ".log" && f != "stack.log") unlink(join(inst.logs(), f).c_str());  // stack.log: this daemon's own stderr, already open


    // a fresh property store per stack (ro.* is write-once); ensureStack already made it when a client waits on the ready property
    if (getenvOr("APKLAYER_STACK_PREPARED", "").empty()) {
        removeTree(inst.runtimeDir);
        makeDirs(inst.runtimeDir);
    }
    stageManifest(inst, l, staged(l, "libapklayer_codec2.so") && !deviceDomain(l, inst, "media"), "apklayer-media.xml", "aosp/hals/media/codec2/manifest.xml");
    stageManifest(inst, l, staged(l, "libapklayer_audio.so"), "apklayer-audio.xml", "aosp/hals/audio/manifest.xml");
    stageManifest(inst, l, staged(l, "libapklayer_audioserver.so"), "apklayer-audio-effects.xml", "aosp/hals/audio/effects-manifest.xml");
    stageManifest(inst, l, staged(l, "libapklayer_keymint.so"), "apklayer-keymint.xml", "aosp/hals/security/manifest.xml");
    stageManifest(inst, l, staged(l, "libapklayer_keystore2.so"), "apklayer-keystore2.xml", "aosp/hals/security/keystore2-manifest.xml", "system");  // a framework HAL: keystore2 may register its service
    stageManifest(inst, l, gatekeeper(l), "apklayer-gatekeeper.xml", "aosp/hals/security/gatekeeper-manifest.xml");
    // the hardware providers of providers.toml: their HALs are declared for this stack's lifetime (servicemanager reads the manifests once)
    std::vector<ProviderService> providers = providerServices(l, inst);
    writeHostHals(l, inst);  // domains set to the device: their HALs come from the host binder contexts, no front starts
    for (const ProviderService& p : providers) stageManifest(inst, l, true, "apklayer-" + p.name + ".xml", p.manifest);
    if (deviceGraphics(l, inst)) undeclareOwnGraphics(inst, l);
    if (deviceDomain(l, inst, "media")) stageDeviceCodecXml(inst);
    loadProperties(l, inst, ne);
    Spawn prop;
    prop.env = ne.env;
    // Android init creates this directory (in the instance's /data) and announces it to RenderEngine.
    std::string cache = join(inst.dataDir, "misc/surfaceflinger");
    makeDirs(cache);
    for (const char* f : {"egl_shaders", "skia_shaders", "skia_graphite_pipelines"}) writeFile(join(cache, f), "");
    prop.argv = {join(n, "bin/aprop"), "set", "service.sf.cache_dir_available", "1"};
    runAndWait(prop);

    if (getenvOr("APKLAYER_START_SHELL", "1") == "1") ensureShell(l, inst, ne);  // `run` asks again and fails on its own when this did not work

    std::vector<Service> running;
    auto spec = [&](const std::string& name, const std::string& lib, const std::vector<std::string>& args, const std::string& backend = "") {
        Spawn s;
        s.argv = {l.btload(), l.androidLibs, lib, "--main"};
        s.argv.insert(s.argv.end(), args.begin(), args.end());
        s.env = ne.env;
        if (!backend.empty()) s.env["APKLAYER_HOST_LIBS"] += (s.env["APKLAYER_HOST_LIBS"].empty() ? "" : ":") + backend;  // a HAL front's backend (bridge/hals/<domain>)
        s.logFile = join(inst.logs(), name + ".log");
        return s;
    };
    auto start = [&](const std::string& name, const std::string& lib, const std::vector<std::string>& args, const std::string& backend = "") {
        Spawn s = spec(name, lib, args, backend);
        int pid = spawn(s);
        running.push_back({name, pid, pidfdOpen(pid), s});
    };
    const std::string hals = join(l.root, "aosp/hals/audio");
    if (userspaceBinder(inst.root)) ensureBinderDaemon(l.runtimeHome, n, inst.id, join(inst.logs(), "binderd.log"));  // docs/BINDER.md
    start("servicemanager", "libapklayer_servicemanager.so", {"/dev/binder"});
    start("permission", "libapklayer_permission.so", {});  // libbinder's checkPermission() asks this (no system_server PermissionController)
    if (!deviceGraphics(l, inst)) start("allocator", "libapklayer_allocator.so", {});  // a Halium device's own: the host context
    start("composer", "libapklayer_composer.so", {});
    if (staged(l, "libapklayer_codec2.so") && !deviceDomain(l, inst, "media")) start("codec2", "libapklayer_codec2.so", {});  // media = device: the host's Codec2 store
    if (staged(l, "libapklayer_mediaserver.so")) {
        start("media", "libapklayer_mediaserver.so", {});
        start("extractor", "libapklayer_mediaserver.so", {"--extractor"});
    }
    if (staged(l, "libapklayer_audio.so")) start("audio", "libapklayer_audio.so", {join(hals, "audio_policy_configuration.xml")});
    if (staged(l, "libapklayer_audioserver.so")) {
        start("audio-effects", "libapklayer_audio.so", {"--effects", join(hals, "audio_effects_config.xml")});
        start("audio-services", "libapklayer_audioserver.so", {});
    }
    if (staged(l, "libapklayer_keymint.so") && staged(l, "libapklayer_keystore2.so")) {  // the real Android keystore: software KeyMint, then keystore2 over it
        start("keymint", "libapklayer_keymint.so", {});
        if (gatekeeper(l)) start("gatekeeper", "libapklayer_gatekeeper.so", {});  // before keystore2: its shared secret takes part in keystore2's negotiation
        keystoreDir(inst);
        start("keystore2", "libapklayer_keystore2.so", {"/data/misc/keystore"});  // as keystore2.rc: the database directory, through the path layer
        if (gatekeeper(l)) {  // as gatekeeperd.rc; the hub enrolls and verifies the instance's credential (HubLockScreen)
            makeDirs(join(inst.dataDir, "misc/gatekeeper"));
            start("gatekeeperd", "libapklayer_gatekeeperd.so", {"/data/misc/gatekeeper"});
        }
    }
    for (const ProviderService& p : providers) {
        start(p.name, p.front, p.options, p.backend);
        // system_server's SensorService, over the sensors HAL: what SensorManager in apps talks to (HostSensorShadows)
        if (p.name == "sensors" && staged(l, "libapklayer_sensorservice.so")) start("sensorservice", "libapklayer_sensorservice.so", {});
        // cameraserver over the camera provider HAL: what the apps' CameraManager and android.hardware.Camera talk to (HostCameraShadows)
        if (p.name == "camera" && staged(l, "libapklayer_cameraserver.so")) start("cameraserver", "libapklayer_cameraserver.so", {});
    }
    // no sensors provider: SensorService still runs, with no sensors (no HAL declared), as a device always has it; else libsensor (NDK
    // ASensorManager: Unity) waits 300 s for "sensorservice" on every call
    bool sensors = false;
    for (const ProviderService& p : providers) sensors |= p.name == "sensors";
    if (!sensors && staged(l, "libapklayer_sensorservice.so")) start("sensorservice", "libapklayer_sensorservice.so", {});
    // camera = device: the device's HIDL provider through the host hwbinder context, cameraserver over it (libhidl 0003 lists it)
    if (deviceDomain(l, inst, "camera") && staged(l, "libapklayer_cameraserver.so")) start("cameraserver", "libapklayer_cameraserver.so", {});
    start("surfaceflinger", "libapklayer_surfaceflinger.so", {});
    if (!tool.empty()) {
        // smoke tools draw on stack 0: give them the same virtual-display sink an app uses
        if (staged(l, "libapklayer_vdisplay.so"))
            start("test-display", "libapklayer_vdisplay.so", {"apklayer:test", "0", getenvOr("APKLAYER_DISPLAY_WIDTH", "720"), getenvOr("APKLAYER_DISPLAY_HEIGHT", "1280")});
        start("tool", tool[0], std::vector<std::string>(tool.begin() + 1, tool.end()));
    }
    writeFile(inst.pidFile(), std::to_string(getpid()) + "\n");

    // Ready when SurfaceFlinger has registered, and the audio policy when audio services run (an AudioTrack created before it exists gets
    // "server died"). One waiter per service blocks in waitForService; the daemon sets the ready property after all of them returned.
    std::vector<std::string> waitFor = {"SurfaceFlingerAIDL"};
    if (staged(l, "libapklayer_audioserver.so")) waitFor.push_back("media.audio_policy");
    for (const ProviderService& p : providers)  // an app's first camera list (the provider joins cameraserver whenever it registers)
        if (p.name == "camera" && staged(l, "libapklayer_cameraserver.so")) waitFor.push_back("media.camera");
    if (deviceDomain(l, inst, "camera") && staged(l, "libapklayer_cameraserver.so")) waitFor.push_back("media.camera");
    std::vector<int> waiters;
    for (const std::string& svc : waitFor) {
        Spawn w;
        w.argv = {l.btload(), l.androidLibs, "libapklayer_servicetool.so", "--main", "wait", svc};
        w.env = ne.env;
        w.logFile = join(inst.logs(), "ready-" + svc + ".log");
        waiters.push_back(spawn(w));
    }
    std::vector<pollfd> fds;
    for (const Service& s : running) fds.push_back({s.pidfd, POLLIN, 0});
    size_t nServices = fds.size();
    std::vector<int> waiterFds;
    for (int pid : waiters) { waiterFds.push_back(pidfdOpen(pid)); fds.push_back({waiterFds.back(), POLLIN, 0}); }
    sigset_t sigs;
    sigemptyset(&sigs);
    sigaddset(&sigs, SIGTERM);
    sigaddset(&sigs, SIGINT);
    sigaddset(&sigs, SIGHUP);  // `apklayer providers set|unset` changed providers.toml
    sigprocmask(SIG_BLOCK, &sigs, nullptr);
    int sfd = signalfd(-1, &sigs, SFD_CLOEXEC);
    fds.push_back({sfd, POLLIN, 0});

    size_t pending = waiters.size();
    int status = 0;
    for (bool run = true; run;) {
        if (poll(fds.data(), fds.size(), -1) < 0) continue;
        for (size_t i = 0; i < fds.size() && run; i++) {
            if (!(fds[i].revents & POLLIN)) continue;
            if (i < nServices) {
                Service& s = running[i];
                int st = 0;
                waitpid(s.pid, &st, 0);
                close(s.pidfd);
                fprintf(stderr, "exited: %s (status %d)\n", s.name.c_str(), WIFEXITED(st) ? WEXITSTATUS(st) : 128 + WTERMSIG(st));
                if (!essential(s.name) && s.restarts < kMaxRestarts) {
                    s.restarts++;
                    s.pid = spawn(s.spec);
                    s.pidfd = pidfdOpen(s.pid);
                    fds[i].fd = s.pidfd;
                    fprintf(stderr, "restarted: %s (%d of %d)\n", s.name.c_str(), s.restarts, kMaxRestarts);
                    continue;
                }
                if (!essential(s.name)) { fds[i].fd = -1; s.pid = 0; fprintf(stderr, "%s keeps exiting: left stopped\n", s.name.c_str()); continue; }
                s.pid = 0;  // reaped: never signalled again (its pid may be reused)
                status = s.name == "tool" ? 0 : 1;  // the tool finishing is the normal end of a test run; its verdict is in its log
                run = false;
            } else if (i < nServices + waiters.size()) {
                int st = 0;
                waitpid(waiters[i - nServices], &st, 0);
                fds[i].fd = -1;  // done: poll ignores negative descriptors
                // btload's exit status is not the tool's: the printed "NAME: found" is the result (as the old script grepped it)
                auto out = readFile(join(inst.logs(), "ready-" + waitFor[i - nServices] + ".log"));
                if (out && out->find(": found") != std::string::npos && --pending == 0) {
                    setProperty(l, ne, kReadyProperty, "1");
                }
            } else {
                signalfd_siginfo info{};
                if (read(sfd, &info, sizeof info) != sizeof info || info.ssi_signo != SIGHUP) { run = false; continue; }  // asked to stop
                // providers.toml changed: a running domain whose backend or options differ restarts with them, a removed one stops, the
                // other services keep running. A domain this stack did not start with waits for the next start (its HAL is not declared).
                std::vector<ProviderService> wanted = providerServices(l, inst);
                writeHostHals(l, inst);  // read once per process: processes started from now on see the change
                for (const std::string& domain : providerDomains()) {
                    const ProviderService* want = nullptr;
                    for (const ProviderService& p : wanted) if (p.name == domain) want = &p;
                    size_t k = 0;
                    while (k < nServices && running[k].name != domain) k++;
                    if (k == nServices) { if (want) fprintf(stderr, "providers: %s starts with the instance's next start\n", domain.c_str()); continue; }
                    Service& s = running[k];
                    Spawn fresh = want ? spec(want->name, want->front, want->options, want->backend) : Spawn{};
                    if (want && s.pid > 0 && fresh.argv == s.spec.argv && fresh.env == s.spec.env) continue;  // unchanged
                    if (s.pid > 0) { kill(s.pid, SIGTERM); waitpid(s.pid, nullptr, 0); close(s.pidfd); s.pid = 0; fds[k].fd = -1; }
                    if (!want) { fprintf(stderr, "providers: %s stopped\n", domain.c_str()); continue; }
                    s.spec = fresh;
                    s.restarts = 0;
                    s.pid = spawn(fresh);
                    s.pidfd = pidfdOpen(s.pid);
                    fds[k].fd = s.pidfd;
                    fprintf(stderr, "providers: %s restarted with its new configuration\n", domain.c_str());
                    for (const Service& client : running)  // SensorService connects to the HAL once: it restarts (as a crashed service) with it
                        if (domain == "sensors" && client.name == "sensorservice" && client.pid > 0) kill(client.pid, SIGTERM);
                }
            }
        }
    }
    for (const Service& s : running) if (s.pid > 0) kill(s.pid, SIGTERM);
    for (int pid : waiters) kill(pid, SIGTERM);
    for (const Service& s : running) {
        int st = 0;
        if (s.pid > 0 && waitpid(s.pid, &st, 0) == s.pid && !(WIFSIGNALED(st) && WTERMSIG(st) == SIGTERM) && !(WIFEXITED(st) && WEXITSTATUS(st) == 0))
            fprintf(stderr, "stopped: %s (status %d)\n", s.name.c_str(), WIFEXITED(st) ? WEXITSTATUS(st) : 128 + WTERMSIG(st));  // a clean stop is SIGTERM's default action
    }
    // the store ends with its services: a later tool must not find servicemanager.ready=true with no servicemanager (it waits forever).
    // Before the pid file goes: a stack started after that makes its own store here.
    removeTree(inst.runtimeDir);
    unlink(inst.pidFile().c_str());
    // the JVMs of this stack are gone: their extracted native runtimes too (the directory stays: a JVM about to start has it as java.io.tmpdir), but not the
    // fonts and ICU data they share (robolectric patch 0001). A copy keyed by an older framework jar stays until the state dir is removed.
    for (const std::string& name : listDir(inst.tmpDir()))
        if (name != "robolectric-nativeruntime-assets") removeTree(join(inst.tmpDir(), name));
    endShellUnlessShared(l, inst.id);
    return status;
}

bool ensureStack(const Layout& l, const Instance& inst) {
    int lock = open(join(inst.stateDir, "start.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0644);
    struct Unlock { int fd; ~Unlock() { close(fd); } } unlock{lock};  // closing releases the flock: the hub JVM asks for the stack itself
    flock(lock, LOCK_EX);  // concurrent launches share one start
    auto pid = readFile(inst.pidFile());
    NativeEnv ne = nativeEnv(l, inst);
    if (pid && alive(atoi(pid->c_str())) && propertyIs(l, ne, kReadyProperty, "1")) return true;
    if (!(pid && alive(atoi(pid->c_str())))) {
        removeTree(inst.runtimeDir);  // the new stack's property store: made before the waiter opens it, so both see the same file
        makeDirs(inst.runtimeDir);
        ne.env["APKLAYER_STACK_PREPARED"] = "1";
        Spawn d;
        d.argv = {l.cliBinary(), "stack-daemon", inst.id};
        d.env = ne.env;
        d.logFile = join(inst.logs(), "stack.log");
        d.newSession = true;
        int child = spawn(d);
        int death = pidfdOpen(child);
        bool ok = waitReady(l, ne, death);
        if (!ok) fprintf(stderr, "apklayer: the Android services did not come up; logs in %s\n", inst.logs().c_str());
        return ok;
    }
    return waitReady(l, ne, -1);
}

int execTool(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    if (args.empty()) die("exec <library> [args...]");
    NativeEnv ne = nativeEnv(l, inst);
    Spawn s;
    s.argv = {l.btload(), l.androidLibs, args[0], "--main"};
    s.argv.insert(s.argv.end(), args.begin() + 1, args.end());
    s.env = ne.env;
    // without a stack the tool still sees a device's properties (the store stays for the next tool; a stack's start makes its own)
    if (auto pid = readFile(inst.pidFile()); !(pid && alive(atoi(pid->c_str())))) {
        makeDirs(inst.runtimeDir);
        loadProperties(l, inst, ne);
    }
    if (userspaceBinder(inst.root)) ensureBinderDaemon(l.runtimeHome, l.native, inst.id, join(inst.logs(), "binderd.log"));
    return runAndWait(s);
}

void stopStack(const Layout& l, const Instance& inst) {
    endShellUnlessShared(l, inst.id);
    for (const std::string& f : listDir(inst.stateDir)) {  // launches of this instance: their JVM and window sink
        if (f.rfind("run.", 0) != 0) continue;
        auto record = readFile(join(inst.stateDir, f));
        if (record) for (const std::string& pid : split(*record, '\n')) terminate(atoi(pid.c_str()));
        unlink(join(inst.stateDir, f).c_str());
    }
    // the hub lives on the stack's binder: it ends with it (a stale one would hold the lock for the next stack)
    if (auto hub = readFile(join(inst.stateDir, "hub.pid")))  // the hub JVM, then its launcher: the next hub can take hub.lock as soon as stop returns
        for (const std::string& pid : split(*hub, '\n')) terminate(atoi(pid.c_str()));
    auto pid = readFile(inst.pidFile());
    if (pid) terminate(atoi(pid->c_str()));
    // the userspace binder (docs/BINDER.md): it would end by itself once its clients are gone; stop means now
    if (auto binderd = readFile(join(binderSocketDir(l.runtimeHome), inst.id + ".pid"))) terminate(atoi(binderd->c_str()));
}

}  // namespace apklayer
