// Google's own apps (LiteGapps' superlite set from apklayer's gapps branch, the framework's SDK 37) as the instance's system apps, in place of microG: the zip is downloaded and checked,
// tools/gapps.sh takes its APKs and XMLs out, the XMLs stay in <data>/apklayer/google-apps/etc -- the privileged allowlist that makes GmsCore a
// privileged system app holding its signature permissions, sysconfig and default grants, read from there (gappsEtcDir) rather than copied into the
// runtime's android-root, which a package build serves read-only -- and each APK is installed `--system` as the bundle's system apps are.
// No Google code is in apklayer's main tree: the gapps branch's release zips (LiteGapps' files, unchanged; workflow gapps), pinned by sha256 in downloads.json (APKLAYER_GAPPS_URL and APKLAYER_GAPPS_SHA256 override).
#include "gapps.h"

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <fcntl.h>
#include <iterator>
#include <strings.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>

#include "apps.h"
#include "releases.h"
#include "util.h"

namespace apklayer {

namespace {
struct Zip { std::string url, sha256; };
constexpr char kFramework[] = "com.google.android.gsf";
constexpr char kServices[] = "com.google.android.gms";
constexpr char kStore[] = "com.android.vending";

// The NOGAPPS key used by Services and Companion (also pinned in ShadowHostPackageManager).
constexpr char kMicrogSigner[] = "9BD06727E62796C0130EB6DAB39B73157451582CBD138E86C468ACC395D14165";
constexpr char kGoogleSigner[] = "7CE83C1B71F3D572FED04C8D40C5CB10FF75E6D87D9DF6FBD53F0468C2905053";
constexpr char kGoogleLegacySigner[] = "F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB83";
constexpr char kGoogleServicesSigner[] = "5F2391277B1DBD489000467E4C2FA6AF802430080457DCE2F618992E9DFB5402";

struct BundleApk { const char* file; const char* package; bool required; };
constexpr BundleApk kApks[] = {
    {"GoogleServicesFramework.apk", kFramework, true},
    {"GmsCore.apk", kServices, true},
    {"Phonesky.apk", kStore, true},
    {"GoogleContactsSyncAdapter.apk", "com.google.android.syncadapters.contacts", false},
    {"GoogleCalendarSyncAdapter.apk", "com.google.android.syncadapters.calendar", false},
};

enum class Provider { Missing, Google, Microg, Unknown };

Provider installedProvider(const Layout& layout, const std::string& pkg) {
    const std::string dir = appDir(layout, pkg);
    if (!exists(dir)) return Provider::Missing;
    const std::string info = join(dir, "info.properties");
    if (property(info, "package") != pkg) return Provider::Unknown;
    const std::string signer = property(info, "signer");
    if ((pkg == kServices || pkg == kStore) && strcasecmp(signer.c_str(), kMicrogSigner) == 0) return Provider::Microg;
    if (strcasecmp(signer.c_str(), kGoogleSigner) == 0 || strcasecmp(signer.c_str(), kGoogleLegacySigner) == 0
        || (pkg == kServices && strcasecmp(signer.c_str(), kGoogleServicesSigner) == 0)) return Provider::Google;
    return Provider::Unknown;
}

// Packages and configuration are shared by instances using the same data directory.
struct SwitchLock {
    int fd;
    explicit SwitchLock(const Layout& layout) {
        const std::string dir = join(layout.dataHome, "apklayer");
        makeDirs(dir);
        fd = open(join(dir, ".google-provider.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0600);
    }
    ~SwitchLock() { if (fd >= 0) close(fd); }
    bool acquire() const {
        if (fd < 0) return false;
        int status;
        do { status = flock(fd, LOCK_EX); } while (status != 0 && errno == EINTR);
        return status == 0;
    }
};

struct WorkDir {
    std::string path;
    ~WorkDir() { removeTree(path); }
};
std::string gappsDir(const Layout& layout) { return join(layout.dataHome, "apklayer/google-apps"); }  // a reserved instance name (cli/manage.cpp)
int cli(const Layout& layout, const std::vector<std::string>& args) {
    Spawn s;
    s.argv = {layout.cliBinary()};
    s.argv.insert(s.argv.end(), args.begin(), args.end());
    s.env = currentEnv();
    return runAndWait(s);
}
// Migration: an earlier build copied these XMLs into the runtime's android-root; a removal takes any such copy out, or microG's GmsCore (same package
// name) would stay a privileged system app. Nothing writes there any more; a read-only package runtime has none and unlink just fails.
void removeCopiedConfig(const Layout& layout, const std::string& from, const std::string& rel = "") {
    for (const std::string& name : listDir(join(from, rel))) {
        const std::string sub = rel.empty() ? name : join(rel, name);
        if (isDir(join(from, sub))) removeCopiedConfig(layout, from, sub);
        else unlink(join(join(layout.native, "android-root/system/etc"), sub).c_str());
    }
}
bool hasPayload(const std::string& path) {
    struct stat file {};
    return lstat(path.c_str(), &file) == 0 && S_ISREG(file.st_mode) && file.st_size > 0;
}

bool hasGappsConfig(const std::string& etc) {
    for (const char* kind : {"permissions", "sysconfig", "default-permissions"}) {
        const std::string dir = join(etc, kind);
        for (const auto& name : listDir(dir))
            if (name.size() > 4 && name.compare(name.size() - 4, 4, ".xml") == 0 && hasPayload(join(dir, name))) return true;
    }
    return false;
}

std::string identityError(const std::string& pkg) {
    return "Could not identify the installed provider for " + pkg + " from its signing certificate";
}

std::string removeProvider(const Layout& layout, const Instance& instance, const std::string& pkg, Provider provider,
                           const std::function<void(const std::string&)>& report) {
    const Provider installed = installedProvider(layout, pkg);
    if (installed == Provider::Unknown) return identityError(pkg);
    if (installed != provider) return "";
    report("Removing " + pkg + (provider == Provider::Microg ? " (microG)" : " (Google)"));
    if (cli(layout, {"--instance", instance.id, "uninstall", pkg}) != 0) return "Could not remove " + pkg;
    return "";
}
}  // namespace

std::string loadDownloads(const Layout& layout, manager::Downloads& d) {
    std::string base = join(layout.root, "downloads.json");
    if (!exists(base)) base = join(layout.root, "data/downloads.json");
    return d.load(base, manager::userDownloadsFile());
}

bool gappsInstalled(const Layout& layout) { return exists(appDir(layout, "com.google.android.gsf")); }

// gapps.h: where the allowlists are read from, so nothing has to be written into the runtime tree
std::string gappsEtcDir(const Layout& layout) {
    const std::string etc = join(gappsDir(layout), "etc");
    return isDir(etc) && gappsInstalled(layout) ? etc : "";
}

std::string installMicrog(const Layout& layout, const Instance& instance, const std::string& indexUrl,
                          const std::vector<std::string>& packages, const std::function<void(const std::string&)>& report,
                          const manager::Progress& progress) {
    SwitchLock lock(layout);
    if (!lock.acquire()) return "Could not lock Google provider setup";
    if (std::find(packages.begin(), packages.end(), kServices) == packages.end()
        || std::find(packages.begin(), packages.end(), kStore) == packages.end()) return "The microG source must include Services and Companion";

    WorkDir work{join(layout.dataHome, "apklayer/.microg-work")};
    removeTree(work.path);
    makeDirs(work.path);
    const std::string index = join(work.path, "index.json");
    if (std::string e = manager::download(indexUrl, index, nullptr); !e.empty()) return e;
    const std::string contents = readFile(index).value_or("");
    struct Pending { std::string package, file; };
    std::vector<Pending> pending;
    for (const auto& pkg : packages) {
        std::string error;
        const auto apk = manager::pickApk(contents, pkg, manager::androidAbi(), error);
        if (!error.empty()) return error;
        const Provider provider = installedProvider(layout, pkg);
        const bool shared = pkg == kServices || pkg == kStore;
        if (shared && provider == Provider::Unknown) return identityError(pkg);
        const auto have = property(join(appDir(layout, pkg), "info.properties"), "versionCode");
        if ((!shared || provider == Provider::Microg) && !have.empty() && atoll(have.c_str()) >= apk.versionCode) {
            report(pkg + " is up to date");
            continue;
        }
        const std::string file = join(work.path, std::to_string(pending.size()) + ".apk");
        report("Downloading " + pkg);
        if (std::string e = manager::fetch(apk.url, apk.sha256, file, progress); !e.empty()) return e;
        pending.push_back({pkg, file});
    }

    for (const auto& apk : pending) {
        if (apk.package == kServices || apk.package == kStore) {
            if (std::string e = removeProvider(layout, instance, apk.package, Provider::Google, report); !e.empty()) return e;
        }
        report("Installing " + apk.package);
        int rc = cli(layout, {"--instance", instance.id, "install", apk.file});
        if (rc != 0 && rc != kInstallDowngrade) return "Installing " + apk.package + " failed";
        if ((apk.package == kServices || apk.package == kStore)
            && installedProvider(layout, apk.package) != Provider::Microg)
            return "The installed APK is not the expected microG package " + apk.package;
    }

    // Google-only packages can wait until both replacements are in place.
    for (auto it = std::rbegin(kApks); it != std::rend(kApks); ++it) {
        const std::string pkg = it->package;
        if (pkg == kServices || pkg == kStore || installedProvider(layout, pkg) != Provider::Google) continue;
        if (std::string e = removeProvider(layout, instance, pkg, Provider::Google, report); !e.empty()) return e;
    }
    const std::string dir = gappsDir(layout);
    removeCopiedConfig(layout, join(dir, "etc"));
    removeTree(dir);
    return "";
}

std::string installGapps(const Layout& layout, const Instance& instance, const std::function<void(const std::string&)>& report, const manager::Progress& progress) {
    SwitchLock lock(layout);
    if (!lock.acquire()) return "Could not lock Google provider setup";
    manager::Downloads sources;
    if (std::string e = loadDownloads(layout, sources); !e.empty()) return e;
    const std::string cpu = manager::cpuNames()[0];
    Zip zip{sources.str({"gapps", cpu, "url"}), sources.str({"gapps", cpu, "sha256"})};
    zip.url = getenvOr("APKLAYER_GAPPS_URL", zip.url);
    zip.sha256 = getenvOr("APKLAYER_GAPPS_SHA256", zip.sha256);
    if (zip.url.empty()) return "No Google apps build for this CPU";
    const std::string dir = gappsDir(layout), marker = join(dir, "installed");
    bool complete = hasGappsConfig(join(dir, "etc"));
    for (const auto& apk : kApks) {
        const Provider installed = installedProvider(layout, apk.package);
        if (apk.required && installed == Provider::Unknown) return identityError(apk.package);
        if (apk.required && installed != Provider::Google) complete = false;
    }
    if (complete && zip.sha256.size() == 64 && trim(readFile(marker).value_or("")) == zip.sha256) {
        report("Up to date");
        return "";
    }
    const std::string file = join(dir, ".bundle.zip");
    WorkDir staged{join(dir, ".work")};
    const std::string& work = staged.path;
    makeDirs(dir);
    report("Downloading Google apps (about 350 MB)");
    if (std::string e = manager::fetch(zip.url, zip.sha256, file, progress); !e.empty()) return e;
    report("Unpacking");
    progress(-1);
    Spawn unpack;
    // <share>/tools/gapps.sh installed (meson.build), tools/release/gapps.sh in a source tree
    std::string script = join(layout.root, "tools/gapps.sh");
    if (!exists(script)) script = join(layout.root, "tools/release/gapps.sh");
    unpack.argv = {"sh", script, file, work};
    unpack.env = currentEnv();
    int rc = runAndWait(unpack);
    unlink(file.c_str());
    if (rc != 0) return "Unpacking the Google apps failed";
    for (const auto& apk : kApks) {
        const auto path = join(work, std::string("apks/") + apk.file);
        if (!hasPayload(path)) {
            if (apk.required || exists(path)) return std::string("The Google apps bundle is missing ") + apk.file;
            continue;
        }
        if (installedProvider(layout, apk.package) == Provider::Unknown) return identityError(apk.package);
    }
    if (!hasGappsConfig(join(work, "etc"))) return "The Google apps bundle has no supported configuration XML";
    if (unlink(marker.c_str()) != 0 && errno != ENOENT) return "Could not clear the Google apps completion marker";
    removeTree(join(dir, "etc"));
    if (rename(join(work, "etc").c_str(), join(dir, "etc").c_str()) != 0) return "Could not keep the Google apps' configuration in " + dir;
    for (const auto& apk : kApks) {
        const auto path = join(work, std::string("apks/") + apk.file);
        if (!exists(path)) continue;
        if (std::string e = removeProvider(layout, instance, apk.package, Provider::Microg, report); !e.empty()) return e;
        report("Installing " + std::string(apk.file));
        int r = cli(layout, {"--instance", instance.id, "install", "--system", path});
        if (r != 0 && r != kInstallDowngrade) return "Installing " + std::string(apk.file) + " failed";
        if (installedProvider(layout, apk.package) != Provider::Google)
            return "The installed APK is not the expected Google package " + std::string(apk.package);
    }
    if (!writeFile(marker, zip.sha256 + "\n")) return "Could not write the Google apps completion marker";
    return "";
}

}  // namespace apklayer
