// Google's own apps (LiteGapps "lite", the framework's SDK 37) as the instance's system apps, in place of microG: the zip is downloaded and checked,
// tools/gapps.sh takes its APKs and XMLs out, the XMLs go into the runtime's android-root (the privileged allowlist that makes GmsCore a privileged
// system app holding its signature permissions, sysconfig, default grants) and each APK is installed `--system` as the bundle's system apps are.
// No Google code is in apklayer: LiteGapps' official SourceForge files, pinned by sha256 (APKLAYER_GAPPS_URL and APKLAYER_GAPPS_SHA256 override).
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
Zip zipFor(const std::string& cpu) {
    const std::string base = "https://sourceforge.net/projects/litegapps/files/litegapps/";
    if (cpu == "arm64") return {base + "arm64/37/lite/2026-10-01/LiteGapps-arm64-17.0-20261001-official.zip/download", "cef8224d3451b03e9e640fd489492b4a664e98c1c704dc0cd83bc6244e00820c"};
    if (cpu == "amd64") return {base + "x86_64/37/lite/2026-10-01/LiteGapps-x86_64-17.0-20261001-official.zip/download", "61e891cbb826b7fbdc8839ac925de323147dba66c39b8ae34f1e9c3a12fc21fa"};
    return {};
}
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
// <data>/apklayer/google-apps/etc into the runtime's android-root
void copyConfig(const Layout& layout) {
    const std::string etc = join(gappsDir(layout), "etc");
    if (!isDir(etc)) return;
    Spawn s;
    s.argv = {"cp", "-r", etc + "/.", join(layout.native, "android-root/system/etc") + "/"};
    s.env = currentEnv();
    runAndWait(s);
}
}  // namespace

// only while Google's own apps are installed (the Services Framework is theirs alone): after a removal a new runtime stays without them
void applyGappsConfig(const Layout& layout) {
    if (exists(appDir(layout, "com.google.android.gsf"))) copyConfig(layout);
}

std::string installGapps(const Layout& layout, const Instance& instance, const std::function<void(const std::string&)>& report, const manager::Progress& progress) {
    Zip zip = zipFor(manager::cpuNames()[0]);
    zip.url = getenvOr("APKLAYER_GAPPS_URL", zip.url);
    zip.sha256 = getenvOr("APKLAYER_GAPPS_SHA256", zip.sha256);
    if (zip.url.empty()) return "No Google apps build for this CPU";
    const std::string dir = gappsDir(layout), marker = join(dir, "installed");
    const bool gms = exists(appDir(layout, "com.google.android.gms"));
    if (gms && trim(readFile(marker).value_or("")) == zip.sha256) {
        copyConfig(layout);
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
    copyConfig(layout);
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
