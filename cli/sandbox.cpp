#include "sandbox.h"
#include "apps.h"
#include "daemon.h"
#include "linux_compat.h"
#include "flatpak.h"
#include "halium.h"

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <ftw.h>
#include <grp.h>
#include <pwd.h>
#include <sched.h>
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <sys/xattr.h>
#include <unistd.h>

namespace apklayer {


namespace {
const long kFirstAppUid = 10000;  // Android's FIRST_APPLICATION_UID: namespace uids from here are the user's subordinate ids

struct Range { long start = 0, count = 0; };
// The user's line of /etc/subuid or /etc/subgid (by name or uid), the range newuidmap/newgidmap allow
Range subordinate(const char* file) {
    passwd* pw = getpwuid(getuid());
    std::string name = pw ? pw->pw_name : "", uid = std::to_string(getuid());
    for (const std::string& line : split(readFile(file).value_or(""), '\n')) {
        std::vector<std::string> f = split(line, ':');
        if (f.size() == 3 && (f[0] == name || f[0] == uid)) return {atol(f[1].c_str()), atol(f[2].c_str())};
    }
    return {};
}

// Android's groups an app process gets besides its own gid (ProcessList: shared and cache app gids, the user's everybody gid, permission gids)
const gid_t kInetGid = 3003, kEverybodyGid = 9997;
const long kExtraGids = 2;  // kInetGid and kEverybodyGid: mapped from the top of the subordinate range

// Namespace uid (and gid) 1000, Android's system uid (the hub, the services), is the user; 10000 and up the subordinate range; gids 3003 and 9997
// the last two subordinate gids
bool writeMaps(int pid) {
    Range u = subordinate("/etc/subuid"), g = subordinate("/etc/subgid");
    if (u.count < kFirstAppUid || g.count < kFirstAppUid) {
        fprintf(stderr, "apklayer: the sandbox needs 10000 or more subordinate ids for this user in /etc/subuid and /etc/subgid (usermod --add-subuids/--add-subgids)\n");
        return false;
    }
    Spawn map;
    map.env = currentEnv();
    map.argv = {"newuidmap", std::to_string(pid), "1000", std::to_string(getuid()), "1", std::to_string(kFirstAppUid), std::to_string(u.start), std::to_string(std::min(u.count, 65536L))};
    if (runAndWait(map) != 0) return false;
    long gids = std::min(g.count, 65536L) - kExtraGids;
    map.argv = {"newgidmap", std::to_string(pid), "1000", std::to_string(getgid()), "1", std::to_string(kFirstAppUid), std::to_string(g.start), std::to_string(gids),
                std::to_string(kInetGid), std::to_string(g.start + gids), "1", std::to_string(kEverybodyGid), std::to_string(g.start + gids + 1), "1"};
    return runAndWait(map) == 0;
}

// The groups Android's zygote gives an app process (ProcessList.startProcessLocked): its shared and cache app gids, the user's gid, inet with INTERNET
std::vector<gid_t> appGroups(const Layout& l, const std::string& package, uid_t uid) {
    long appId = (long)uid % 100000;
    std::vector<gid_t> groups = {uid, (gid_t)(50000 + appId - kFirstAppUid), (gid_t)(20000 + appId - kFirstAppUid), kEverybodyGid};
    for (const std::string& p : split(property(join(appDir(l, package), "info.properties"), "permissions"), ';'))
        if (p == "android.permission.INTERNET") groups.push_back(kInetGid);
    return groups;
}

// A namespace made by a process AppArmor did not allow it to (Ubuntu 23.10+: kernel.apparmor_restrict_unprivileged_userns) has no capabilities
bool capable() {
    pid_t probe = fork();
    if (probe == 0) _exit(unshare(CLONE_NEWNS) == 0 ? 0 : 1);
    int st = 0;
    waitpid(probe, &st, 0);
    return WIFEXITED(st) && WEXITSTATUS(st) == 0;
}
const char* kAppArmorHint = "apklayer: no capabilities in the sandbox's user namespace: this system restricts them (AppArmor); sudo tools/host-setup/sandbox.sh once\n";

int stackPid(const Instance& inst) {
    auto pid = readFile(inst.pidFile());
    if (!pid || atoi(pid->c_str()) <= 0) die("the sandbox needs the instance's stack running (its user namespace): " + inst.id);
    return atoi(pid->c_str());
}

// Joins the user namespace of the instance's stack: false when this process is in it already (it has no capabilities left there)
bool joinInstance(const Instance& inst) {
    int fd = open(("/proc/" + std::to_string(stackPid(inst)) + "/ns/user").c_str(), O_RDONLY | O_CLOEXEC);
    struct stat target{}, own{};
    if (fd < 0 || fstat(fd, &target) != 0 || ::stat("/proc/self/ns/user", &own) != 0) die("cannot open the user namespace of " + inst.id + "'s stack");
    if (target.st_ino == own.st_ino) { close(fd); return false; }
    if (setns(fd, CLONE_NEWUSER) != 0) die(std::string("cannot join the sandbox's user namespace: ") + strerror(errno));
    close(fd);
    return true;
}

uid_t chownTo;
int chownOne(const char* path, const struct stat*, int, FTW*) { return lchown(path, chownTo, chownTo) == 0 ? 0 : -1; }
void chownTree(const std::string& dir, uid_t id) {
    chownTo = id;
    if (nftw(dir.c_str(), chownOne, 16, FTW_PHYS) != 0) fprintf(stderr, "apklayer: cannot give %s to uid %u: %s\n", dir.c_str(), id, strerror(errno));
}

// The instance's shared storage (/storage/emulated/0) is every app's, as Android's sdcard emulation makes it: each directory open to all and
// with a default ACL that makes what anyone creates there read-write for all (an app's umask does not apply), the user's own files included.
// shortcut: no per-app media rules (scoped storage); MediaProvider and the pickers are the Android-side gates.
int openStorageDir(const char* path, const struct stat* st, int type, FTW*) {
    if (type != FTW_D) return 0;
    struct __attribute__((packed)) { uint32_t version; struct __attribute__((packed)) { uint16_t tag, perm; uint32_t id; } entry[3]; } acl =
        {2, {{0x01, 7, ~0u}, {0x04, 7, ~0u}, {0x20, 7, ~0u}}};  // POSIX ACL xattr: user::rwx group::rwx other::rwx
    if ((st->st_mode & 07777) != 0777) chmod(path, 0777);
    char current[64];
    if (getxattr(path, "system.posix_acl_default", current, sizeof current) != (ssize_t)sizeof acl) setxattr(path, "system.posix_acl_default", &acl, sizeof acl, 0);
    return 0;
}

bool under(const std::string& path, const std::string& dir) { return path == dir || path.rfind(dir + "/", 0) == 0; }

// What the host's GL, Vulkan, VA and OpenCL loaders read besides /usr and /etc, driver-agnostic: the files and directories their variables name,
// the user's and XDG_DATA_DIRS' ICD and vendor directories, and each manifest's library_path (a driver installed into the home, NVIDIA's
// egl-wayland or a Vulkan layer in ~/.local). The caller keeps what lies in a hidden directory.
std::vector<std::string> graphicsFiles(const Layout& l) {
    std::vector<std::string> out, dirs;
    for (const char* var : {"__EGL_VENDOR_LIBRARY_FILENAMES", "__EGL_VENDOR_LIBRARY_DIRS", "__EGL_EXTERNAL_PLATFORM_CONFIG_FILENAMES", "__EGL_EXTERNAL_PLATFORM_CONFIG_DIRS",
                            "VK_ICD_FILENAMES", "VK_DRIVER_FILES", "VK_ADD_DRIVER_FILES", "VK_LAYER_PATH", "VK_ADD_LAYER_PATH", "LIBGL_DRIVERS_PATH",
                            "GBM_BACKENDS_PATH", "LIBVA_DRIVERS_PATH", "OCL_ICD_VENDORS", "OCL_ICD_FILENAMES"})
        for (const std::string& entry : split(getenvOr(var, ""), ':'))
            if (!entry.empty() && entry[0] == '/') (isDir(entry) ? dirs : out).push_back(entry);
    std::vector<std::string> roots = {l.dataHome, getenvOr("XDG_CONFIG_HOME", join(homeDir(), ".config"))};
    for (const std::string& d : split(getenvOr("XDG_DATA_DIRS", "/usr/local/share:/usr/share"), ':')) if (!d.empty()) roots.push_back(d);
    for (const std::string& r : roots)
        for (const char* sub : {"vulkan/icd.d", "vulkan/implicit_layer.d", "vulkan/explicit_layer.d", "glvnd/egl_vendor.d", "egl/egl_external_platform.d"})
            if (isDir(join(r, sub))) dirs.push_back(join(r, sub));
    std::vector<std::string> manifests;
    for (const std::string& d : dirs) {
        out.push_back(d);
        for (const std::string& name : listDir(d)) manifests.push_back(join(d, name));
    }
    for (const std::string& f : out) if (f.size() > 5 && f.substr(f.size() - 5) == ".json") manifests.push_back(f);
    for (const std::string& m : manifests) {
        std::string text = readFile(m).value_or("");
        size_t key = text.find("\"library_path\""), colon = key == std::string::npos ? key : text.find(':', key);
        size_t open = colon == std::string::npos ? colon : text.find('"', colon), close = open == std::string::npos ? open : text.find('"', open + 1);
        if (close == std::string::npos) continue;
        std::string lib = text.substr(open + 1, close - open - 1);
        if (lib.find('/') == std::string::npos) continue;  // a bare name: found through the library path, which is bound already
        out.push_back(lib[0] == '/' ? lib : join(dirName(realPath(m)), lib));
    }
    return out;
}

// One step of an app's mount view, in order: a tmpfs, a clone of the host's tree (taken before anything is covered) moved to the same path, or a
// directory of the app's own made inside a tmpfs, or a symbolic link of the host's again in a tmpfs
struct Step {
    enum Kind { Tmpfs, Bind, OwnDir, Link } kind;
    std::string path;
    int tree = -1;
    bool file = false;
    uid_t owner = (uid_t)-1;
    std::string target;
};

int cloneTree(const std::string& path, bool writable) {
    int fd = open_tree(AT_FDCWD, path.c_str(), OPEN_TREE_CLONE | OPEN_TREE_CLOEXEC | AT_RECURSIVE);
    if (fd < 0) die("cannot clone " + path + " for the sandbox: " + strerror(errno));
    mount_attr attr{};
    attr.attr_set = MOUNT_ATTR_NOSUID | MOUNT_ATTR_NODEV | (writable ? 0 : MOUNT_ATTR_RDONLY);
    if (mount_setattr(fd, "", AT_EMPTY_PATH | AT_RECURSIVE, &attr, sizeof attr) != 0) die("cannot restrict " + path + ": " + strerror(errno));
    return fd;
}

void makeMountPoint(const std::string& path, bool file) {
    makeDirs(file ? dirName(path) : path);
    if (file) close(open(path.c_str(), O_CREAT | O_WRONLY | O_CLOEXEC, 0600));
}

void apply(const std::vector<Step>& steps) {
    for (const Step& s : steps) {
        if (s.kind == Step::Tmpfs) {
            makeDirs(s.path);
            if (mount("tmpfs", s.path.c_str(), "tmpfs", MS_NOSUID | MS_NODEV, "mode=0755") != 0) die("cannot hide " + s.path + ": " + strerror(errno));
        } else if (s.kind == Step::Bind) {
            makeMountPoint(s.path, s.file);
            if (move_mount(s.tree, "", AT_FDCWD, s.path.c_str(), MOVE_MOUNT_F_EMPTY_PATH) != 0) die("cannot mount " + s.path + " in the sandbox: " + strerror(errno));
            close(s.tree);
        } else if (s.kind == Step::Link) {
            struct stat st{};
            makeDirs(dirName(s.path));
            if (lstat(s.path.c_str(), &st) != 0 && symlink(s.target.c_str(), s.path.c_str()) != 0) die("cannot link " + s.path + " in the sandbox: " + strerror(errno));
        } else {
            makeDirs(s.path);
            if (chown(s.path.c_str(), s.owner, s.owner) != 0) die("cannot give " + s.path + " to the app");
        }
    }
}

// What an app process sees: its own data, the instance's shared storage and /data, the runtime read-only; the user's home, /tmp and runtime directory
// are empty tmpfs, the other apps' data is not there. Everything at the host's paths, so no path changes.

std::vector<Step> appView(const Layout& l, const Instance& inst, const std::string& package, uid_t uid, const std::string& java) {
    std::string home = realPath(homeDir()), runtime = realPath(l.runtimeHome);
    std::vector<std::string> hidden = {home, "/tmp"};
    if (!under(runtime, home)) hidden.push_back(runtime);
    auto isHidden = [&](const std::string& p) { return std::any_of(hidden.begin(), hidden.end(), [&](const std::string& h) { return under(p, h); }); };
    // the runtime's code and libraries (an entry that is the home itself or above it is left out: it would bring the whole home back)
    std::vector<std::string> shared = {l.root, l.build, l.native, l.androidLibs, l.bionic, l.deps, dirName(dirName(realPath(java)))};
    for (const char* var : {"LD_LIBRARY_PATH", "BIONIC_LD_LIBRARY_PATH", "LD_PRELOAD", "APKLAYER_HOST_LIBS"})
        for (const std::string& entry : split(getenvOr(var, ""), ':'))
            if (!entry.empty() && entry[0] == '/') shared.push_back(isDir(entry) ? entry : dirName(entry));
    for (const std::string& entry : graphicsFiles(l)) shared.push_back(isDir(entry) ? entry : dirName(entry));
    // what the runtime links to elsewhere (a Meson install links the prebuilts' jars and Android libraries into its native dir)
    for (const std::string& dir : {l.native, join(l.native, "aosp")})
        for (const std::string& name : listDir(dir)) {
            std::string target = realPath(join(dir, name));
            if (!target.empty() && !under(target, realPath(dir))) shared.push_back(isDir(target) ? target : dirName(target));
        }
    std::vector<std::string> ro;
    for (const std::string& p : shared) {
        std::string r = realPath(p);
        if (!r.empty() && exists(r) && isHidden(r) && !under(home, r)) ro.push_back(r);
    }
    std::sort(ro.begin(), ro.end());
    std::vector<Step> steps;
    auto bind = [&](const std::string& path, const std::string& source, bool writable, bool file = false) {
        steps.push_back({Step::Bind, path, cloneTree(source, writable), file});
    };
    for (const std::string& h : hidden) steps.push_back({Step::Tmpfs, h});
    if (homeAccess(l, inst, package)) {  // the user's opt-in (`apklayer home-access`), as Flatpak's --filesystem=home
        // a private directory on the way to the runtime (~/.cache, 0700) would stop the app's uid: an empty one there, the runtime's part bound into it
        auto closed = [](const std::string& dir) {
            struct stat st{};
            return ::stat(dir.c_str(), &st) == 0 && !(st.st_mode & S_IXOTH);
        };
        // a home only its owner may pass (Ubuntu's 0750): its entries one by one in the open tmpfs. shortcut: the app's uid is not the user's, so it
        // reads and writes what the files' "other" bits allow; full access needs idmapped mounts (root only for a disk's filesystem)
        if (closed(home)) {
            for (const std::string& name : listDir(home)) {
                struct stat st{};
                std::string p = join(home, name);
                if (lstat(p.c_str(), &st) == 0 && (S_ISDIR(st.st_mode) || S_ISREG(st.st_mode))) bind(p, p, true, S_ISREG(st.st_mode));
            }
        } else {
            bind(home, home, true);
        }
        std::vector<std::string> shut, inside = ro;  // and on the way to the instance's parts bound below (~/.local/state)
        for (const std::string& own : {l.appsDir, inst.stateDir, inst.root, inst.runtimeDir}) inside.push_back(realPath(own));
        for (const std::string& r : inside)
            for (size_t at = home.size() + 1; (at = r.find('/', at)) != std::string::npos; at++)
                if (closed(r.substr(0, at))) {
                    if (std::find(shut.begin(), shut.end(), r.substr(0, at)) == shut.end()) shut.push_back(r.substr(0, at));
                    break;
                }
        for (const std::string& s : shut) steps.push_back({Step::Tmpfs, s});
        ro.push_back(home);
    }
    std::string last;
    for (const std::string& r : ro)
        if (r != home && (last.empty() || !under(r, last))) { bind(r, r, false); last = r; }
    // the runtime is bound at its real paths: a link on the way to one in a hidden directory (~/apklayer -> ~/apklayer-m1, through which the JDK
    // image's java and the class path are named) again, as on the host; before a mount point made through one would make it a directory
    std::vector<std::string> named = shared;
    for (const std::string& own : {l.appsDir, inst.stateDir, inst.root, inst.runtimeDir, inst.storageDir, inst.tmpDir()}) named.push_back(own);
    for (const std::string& p : named)
        for (size_t at = 1; at <= p.size() && p[0] == '/'; at++) {
            if (at < p.size() && p[at] != '/') continue;
            std::string prefix = p.substr(0, at);
            struct stat st{};
            char target[4096];
            ssize_t n;
            if (isHidden(prefix) && lstat(prefix.c_str(), &st) == 0 && S_ISLNK(st.st_mode) && (n = readlink(prefix.c_str(), target, sizeof target - 1)) > 0)
                steps.push_back({Step::Link, prefix, -1, false, (uid_t)-1, std::string(target, n)});
        }
    // what holds every instance's and app's data, where such a tree has it inside (a source tree with its tests' data): empty again,
    // then only this instance's parts
    for (const std::string& all : {join(realPath(l.dataHome), "apklayer"), join(realPath(l.stateHome), "apklayer"), runtime})
        if (isDir(all)) steps.push_back({Step::Tmpfs, all});  // also where no hidden directory holds them (a data home on another disk)
    for (const std::string& own : {l.appsDir, inst.stateDir, inst.root}) bind(realPath(own), own, false);
    std::string data = realPath(appDataDir(l, inst, package)), tmp = join(inst.tmpDir(), "sandbox-" + std::to_string(uid));
    makeDirs(tmp);
    if (chown(tmp.c_str(), uid, uid) != 0) die("cannot give " + tmp + " to the app");
    steps.push_back({Step::Tmpfs, join(realPath(inst.root), "app-data")});  // the other apps' data is not in the view at all
    bind(data, data, true);
    bind(realPath(inst.storageDir), inst.storageDir, true);
    // Android's /data as an app sees it: nothing but its own (above) and what the system exposes (WebView's command line, the HAL list); a system
    // app (SettingsProvider: the system uid on Android) also /data/system
    std::string androidData = realPath(inst.dataDir);
    steps.push_back({Step::Tmpfs, androidData});
    for (const char* shown : {"local/tmp", "apklayer"})
        if (isDir(join(androidData, shown))) bind(join(androidData, shown), join(androidData, shown), false);
    if (property(join(appDir(l, package), "info.properties"), "system") == "true") bind(join(androidData, "system"), join(androidData, "system"), true);
    bind(realPath(inst.tmpDir()), tmp, true);  // java.io.tmpdir: the app's own
    std::string assets = join(inst.tmpDir(), "robolectric-nativeruntime-assets");  // fonts and ICU data the instance's JVMs share (stack.cpp)
    if (isDir(assets)) bind(realPath(assets), assets, false);
    steps.push_back({Step::OwnDir, join(runtime, "apklayer"), -1, false, uid});  // in the hidden runtime directory: the app's vsync socket
    bind(realPath(inst.runtimeDir), inst.runtimeDir, false);  // the instance's properties
    for (const std::string& socket : {l.shellSocket(), daemonSocket(l)})
        if (exists(socket)) bind(socket, socket, true, true);
    return steps;
}
}  // namespace

static std::string homeAccessFile(const std::string& root) { return join(root, "home-access"); }  // an instance's packages granted the user's home, one per line

// The user's grant, or a system app that manages all storage (ExternalStorageProvider, whose Home root DocumentsUI shows: the picker hands one file on)
bool homeAccess(const Layout& l, const Instance& inst, const std::string& package) {
    for (const std::string& p : split(readFile(homeAccessFile(inst.root)).value_or(""), '\n')) if (p == package) return true;
    std::string info = join(appDir(l, package), "info.properties");
    if (property(info, "system") != "true") return false;
    for (const std::string& p : split(property(info, "permissions"), ';')) if (p == "android.permission.MANAGE_EXTERNAL_STORAGE") return true;
    return false;
}

uid_t appUidOf(uid_t hostUid) {
    Range u = subordinate("/etc/subuid");
    long n = std::min(u.count, 65536L);
    return u.count && (long)hostUid >= u.start && (long)hostUid < u.start + n ? (uid_t)(kFirstAppUid + hostUid - u.start) : (uid_t)-1;
}

// Why this host cannot run the sandbox ("" when it can): subordinate ids, newuidmap/newgidmap, and where Ubuntu restricts unprivileged user
// namespaces the AppArmor profile for this binary (tools/host-setup/sandbox.sh, the deb's postinst)
std::string sandboxUnavailable() {
    if (inFlatpak()) return "inside Flatpak (its seccomp filter refuses nested user namespaces)";
    if (isHalium()) return "not set up on Halium devices yet";
    Range u = subordinate("/etc/subuid"), g = subordinate("/etc/subgid");
    if (u.count < kFirstAppUid || g.count < kFirstAppUid) return "no subordinate ids for this user (sudo usermod --add-subuids 100000-165535 --add-subgids 100000-165535 $USER)";
    for (const char* tool : {"/usr/bin/newuidmap", "/usr/bin/newgidmap"})
        if (!isExecutable(tool)) return std::string(tool) + " is missing (the uidmap package)";
    if (trim(readFile("/proc/sys/kernel/apparmor_restrict_unprivileged_userns").value_or("0")) == "1") {
        std::string exe = realPath("/proc/self/exe");
        bool profile = false;
        for (const std::string& name : listDir("/etc/apparmor.d"))
            if (name.rfind("apklayer", 0) == 0 && readFile(join("/etc/apparmor.d", name)).value_or("").find(exe) != std::string::npos) profile = true;
        if (!profile) return "no AppArmor profile allows " + exe + " user namespaces (sudo tools/host-setup/sandbox.sh, or reinstall the deb)";
    }
    return "";
}

// Opt-in: APKLAYER_SANDBOX=1 (on by default in 0.4.5 broke NVIDIA's device nodes and deb installs; back to opt-in in 0.4.6 until it's proven there)
bool sandboxOn() { return getenvOr("APKLAYER_SANDBOX", "") == "1"; }


void setHomeAccess(const std::string& root, const std::string& package, bool on) {
    if (!exists(homeAccessFile(root)) && !on) return;
    std::string kept;
    for (const std::string& p : split(readFile(homeAccessFile(root)).value_or(""), '\n')) if (!p.empty() && p != package) kept += p + "\n";
    if (on) kept += package + "\n";
    writeFile(homeAccessFile(root), kept);
}

int homeAccessCommand(const Layout& l, const Instance& inst, const std::string& package, const std::string& state) {
    if (!exists(join(appDir(l, package), "info.properties"))) die("not installed: " + package);
    if (state != "on" && state != "off") die("home-access <package> on|off");
    setHomeAccess(inst.root, package, state == "on");
    printf("apklayer: %s %s the user's home in instance %s (with the sandbox, from its next start)\n", package.c_str(), state == "on" ? "sees" : "no longer sees", inst.id.c_str());
    return 0;
}

void enterInstanceUserNs() {
    int go[2];
    if (pipe2(go, O_CLOEXEC) != 0) die("pipe failed");
    pid_t self = getpid();
    pid_t mapper = fork();
    if (mapper == 0) {  // the maps are written from outside: newuidmap checks the writer's own ids
        char byte;
        _exit(read(go[0], &byte, 1) == 1 && writeMaps(self) ? 0 : 1);
    }
    if (unshare(CLONE_NEWUSER) != 0) die(std::string("cannot make the sandbox's user namespace: ") + strerror(errno));
    (void)!write(go[1], "x", 1);
    int st = 0;
    waitpid(mapper, &st, 0);
    close(go[0]);
    close(go[1]);
    if (!WIFEXITED(st) || WEXITSTATUS(st) != 0) die("the sandbox's uid maps could not be written (newuidmap/newgidmap: the uidmap package)");
    if (!capable()) { fputs(kAppArmorHint, stderr); exit(1); }
}

void sandboxSpawn(const Layout& l, const Instance& inst, Spawn& spec, const std::string& package) {
    std::vector<std::string> argv = {l.cliBinary(), "--instance", inst.id, "sandbox-exec", package, "--"};
    // an app reads the instance's class data sharing archive but never writes it (code the other apps' JVMs map): the hub dumps it (HubHost)
    for (const std::string& a : spec.argv)
        if (package == "-" || (a != "-XX:+RecordDynamicDumpInfo" && a.rfind("-Dapklayer.cdsdump=", 0) != 0)) argv.push_back(a);
    spec.argv = argv;
    // not in sandbox-exec itself: the path layer would answer its own path lookups with Android's (/storage...); sandbox-exec hands it on
    if (spec.env.count("LD_PRELOAD")) { spec.env["APKLAYER_SANDBOX_PRELOAD"] = spec.env["LD_PRELOAD"]; spec.env.erase("LD_PRELOAD"); }
}

int sandboxExec(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    if (args.size() < 3 || args[1] != "--") die("sandbox-exec <package|-> -- command...");
    const std::string& package = args[0];
    std::vector<std::string> argv(args.begin() + 2, args.end());
    char cwd[4096];
    std::string dir = getcwd(cwd, sizeof cwd) ? cwd : "/";
    if (!joinInstance(inst)) die("no sandbox to enter: the stack of " + inst.id + " runs without it (apklayer stop, then start with APKLAYER_SANDBOX=1), or this process is in it already (launches from inside go through apklayerd)");
    if (package != "-") {
        uid_t uid = (uid_t)atol(property(join(appDir(l, package), "info.properties"), "uid").c_str());
        if (uid < (uid_t)kFirstAppUid) die("no app uid for " + package);
        if (unshare(CLONE_NEWNS) != 0) { fputs(kAppArmorHint, stderr); return 1; }
        if (mount(nullptr, "/", nullptr, MS_REC | MS_SLAVE, nullptr) != 0) die("cannot make the sandbox's mounts private");
        std::string data = appDataDir(l, inst, package);
        struct stat st{};
        if (::stat(data.c_str(), &st) == 0 && st.st_uid != uid) chownTree(data, uid);  // the app's files are its own uid's (data from before the sandbox too)
        chmod(data.c_str(), 0700);
        nftw(realPath(inst.storageDir).c_str(), openStorageDir, 16, FTW_PHYS);
        for (const std::string& f : listDir(appDir(l, package)))  // its APKs readable by its uid (installs before cli/apps.cpp made them 0644)
            if (f.size() > 4 && f.substr(f.size() - 4) == ".apk") chmod(join(appDir(l, package), f).c_str(), 0644);
        // a launch's intent file (APKLAYER_INTENT_FILE, wherever its writer put it) goes with it into the app's own tmp, consumed as the app would
        const char* intentFile = getenv("APKLAYER_INTENT_FILE");
        std::optional<std::string> intent = intentFile ? readFile(intentFile) : std::nullopt;
        if (intent) unlink(intentFile);
        apply(appView(l, inst, package, uid, argv[0]));
        if (intent) {
            std::string moved = join(inst.tmpDir(), "intent-" + std::to_string(getpid()) + ".properties");
            writeFile(moved, *intent);
            if (chown(moved.c_str(), uid, uid) != 0) die("cannot give the intent file to the app");
            setenv("APKLAYER_INTENT_FILE", moved.c_str(), 1);
        }
        // what the app's uid may not open but the user may hand it (GPU nodes, the granted home): apklayerd's broker (cli/broker.cpp, bridge/native/broker.c)
        if (exists(daemonSocket(l))) setenv("APKLAYER_BROKER", daemonSocket(l).c_str(), 1);
        if (homeAccess(l, inst, package)) setenv("APKLAYER_BROKER_HOME", realPath(homeDir()).c_str(), 1);
        if (chdir(dir.c_str()) != 0 && chdir("/") != 0) die("no working directory in the sandbox");  // by path again: the old one is a handle into the hidden tree
        std::vector<gid_t> groups = appGroups(l, package, uid);  // never the user's own groups (1000 and the unmapped ones)
        if (setgroups(groups.size(), groups.data()) != 0 || setresgid(uid, uid, uid) != 0 || setresuid(uid, uid, uid) != 0) die("cannot become uid " + std::to_string(uid));
    }
    prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0);  // and no capabilities after exec: the namespace's uids are not 0
    if (const char* preload = getenv("APKLAYER_SANDBOX_PRELOAD")) { setenv("LD_PRELOAD", preload, 1); unsetenv("APKLAYER_SANDBOX_PRELOAD"); }
    std::vector<char*> pointers;
    for (std::string& a : argv) pointers.push_back(a.data());
    pointers.push_back(nullptr);
    execvp(pointers[0], pointers.data());
    die("cannot run " + argv[0] + ": " + strerror(errno));
}

bool grantPath(const Instance& inst, int pid, const std::string& path) {
    pid_t worker = fork();
    if (worker == 0) {
        int target = open(("/proc/" + std::to_string(pid) + "/ns/mnt").c_str(), O_RDONLY | O_CLOEXEC);
        if (target < 0 || !joinInstance(inst) || unshare(CLONE_NEWNS) != 0) { fprintf(stderr, "apklayer: cannot reach the app's view\n"); _exit(1); }
        // a mount namespace of the sandbox's own that still shows the host: the picked tree is cloned here, then moved into the app's view
        int tree = open_tree(AT_FDCWD, path.c_str(), OPEN_TREE_CLONE | OPEN_TREE_CLOEXEC);
        if (tree < 0 || setns(target, CLONE_NEWNS) != 0) { fprintf(stderr, "apklayer: cannot hand %s over: %s\n", path.c_str(), strerror(errno)); _exit(1); }
        struct stat st{};
        makeMountPoint(path, fstat(tree, &st) == 0 && !S_ISDIR(st.st_mode));
        if (move_mount(tree, "", AT_FDCWD, path.c_str(), MOVE_MOUNT_F_EMPTY_PATH) != 0) { fprintf(stderr, "apklayer: cannot mount %s for the app: %s\n", path.c_str(), strerror(errno)); _exit(1); }
        _exit(0);
    }
    int st = 0;
    waitpid(worker, &st, 0);
    return WIFEXITED(st) && WEXITSTATUS(st) == 0;
}

void reclaim(const std::string& dir) {
    struct stat st{};
    if (lstat(dir.c_str(), &st) != 0 || st.st_uid == getuid()) return;
    int go[2], done[2];
    if (pipe2(go, O_CLOEXEC) != 0 || pipe2(done, O_CLOEXEC) != 0) die("pipe failed");
    pid_t worker = fork();
    if (worker == 0) {  // a user namespace of its own (the instance's may be down): there the app uids are mapped and the user may chown them
        char byte;
        if (unshare(CLONE_NEWUSER) != 0) _exit(1);
        (void)!write(go[1], "x", 1);
        if (read(done[0], &byte, 1) != 1 || byte != 'y') _exit(1);
        chownTree(dir, 1000);
        _exit(0);
    }
    char byte = 0;
    bool mapped = read(go[0], &byte, 1) == 1 && writeMaps(worker);
    (void)!write(done[1], mapped ? "y" : "n", 1);
    int status = 0;
    waitpid(worker, &status, 0);
    for (int fd : {go[0], go[1], done[0], done[1]}) close(fd);
    if (!mapped || !WIFEXITED(status) || WEXITSTATUS(status) != 0) fprintf(stderr, "apklayer: cannot take %s back from the sandbox's uids\n", dir.c_str());
}

}  // namespace apklayer
