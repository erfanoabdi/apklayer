// Google's own apps (LiteGapps "lite", the framework's SDK 37) as the instance's system apps, in place of microG: the zip is downloaded and checked,
// tools/gapps.sh takes its APKs and XMLs out, the XMLs stay in <data>/apklayer/google-apps/etc -- the privileged allowlist that makes GmsCore a
// privileged system app holding its signature permissions, sysconfig and default grants, read from there (gappsEtcDir) rather than copied into the
// runtime's android-root, which a package build serves read-only -- and each APK is installed `--system` as the bundle's system apps are.
// No Google code is in apklayer: LiteGapps' official SourceForge files, pinned by sha256 in downloads.json (APKLAYER_GAPPS_URL and APKLAYER_GAPPS_SHA256 override).
#include "gapps.h"

#include <cstdio>
#include <iterator>
#include <unistd.h>

#include "apps.h"
#include "releases.h"
#include "util.h"

namespace apklayer {

namespace {
struct Zip { std::string url, sha256; };
// the install order: the Services Framework before Play services (its gservices provider), then the Play Store, then the sync adapters
const char* const kApks[] = {"GoogleServicesFramework.apk", "GmsCore.apk", "Phonesky.apk", "GoogleContactsSyncAdapter.apk", "GoogleCalendarSyncAdapter.apk"};
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
// Google's packages, the reverse of the install order (the Services Framework last: the others use its provider)
const char* const kPackages[] = {"com.google.android.syncadapters.calendar", "com.google.android.syncadapters.contacts", "com.android.vending",
                                 "com.google.android.gms", "com.google.android.gsf"};
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

std::string removeGapps(const Layout& layout, const Instance& instance, const std::function<void(const std::string&)>& report) {
    for (const char* pkg : kPackages) {
        if (!exists(appDir(layout, pkg))) continue;
        report(std::string("Removing Google's ") + pkg);
        if (cli(layout, {"--instance", instance.id, "uninstall", pkg}) != 0) return std::string("Could not remove Google's ") + pkg;
    }
    if (isDir(join(gappsDir(layout), "etc"))) removeCopiedConfig(layout, join(gappsDir(layout), "etc"));
    // the allowlists and the installed build's marker (a later --gapps downloads again)
    removeTree(gappsDir(layout));
    return "";
}

std::string installGapps(const Layout& layout, const Instance& instance, const std::function<void(const std::string&)>& report, const manager::Progress& progress) {
    manager::Downloads sources;
    if (std::string e = loadDownloads(layout, sources); !e.empty()) return e;
    const std::string cpu = manager::cpuNames()[0];
    Zip zip{sources.str({"gapps", cpu, "url"}), sources.str({"gapps", cpu, "sha256"})};
    zip.url = getenvOr("APKLAYER_GAPPS_URL", zip.url);
    zip.sha256 = getenvOr("APKLAYER_GAPPS_SHA256", zip.sha256);
    if (zip.url.empty()) return "No Google apps build for this CPU";
    const std::string dir = gappsDir(layout), marker = join(dir, "installed");
    const bool gms = exists(appDir(layout, "com.google.android.gms"));
    if (gms && trim(readFile(marker).value_or("")) == zip.sha256) {
        report("Up to date");
        return "";
    }
    // microG's Services and Companion carry these package names under another signer: they go first (with their data). Google's own come with
    // the Services Framework, microG has none
    if (gms && !exists(appDir(layout, "com.google.android.gsf"))) {
        for (const char* pkg : {"com.google.android.gms", "com.android.vending"}) {
            if (!exists(appDir(layout, pkg))) continue;
            report(std::string("Removing microG (") + pkg + ")");
            if (cli(layout, {"--instance", instance.id, "uninstall", pkg}) != 0) return std::string("Could not remove microG's ") + pkg;
        }
    }
    const std::string file = manager::downloadsDir() + "/litegapps-" + zip.sha256.substr(0, 12) + ".zip", work = join(dir, ".work");
    makeDirs(dir);
    report("Downloading Google apps (about 250 MB)");
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
    if (rc != 0) { removeTree(work); return "Unpacking the Google apps failed"; }
    removeTree(join(dir, "etc"));
    if (rename(join(work, "etc").c_str(), join(dir, "etc").c_str()) != 0) { removeTree(work); return "Could not keep the Google apps' configuration in " + dir; }
    std::string error;
    int n = 0;
    for (const char* apk : kApks) {
        const std::string path = join(work, std::string("apks/") + apk);
        if (!exists(path)) continue;
        report("Installing " + std::string(apk) + " (" + std::to_string(++n) + " of " + std::to_string(std::size(kApks)) + ")");
        int r = cli(layout, {"--instance", instance.id, "install", "--system", path});
        if (r != 0 && r != kInstallDowngrade && error.empty()) error = "Installing " + std::string(apk) + " failed";
    }
    removeTree(work);
    if (error.empty()) writeFile(marker, zip.sha256 + "\n");
    return error;
}

}  // namespace apklayer
