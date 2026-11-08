#include "flatpak.h"

#include "util.h"

#include <unistd.h>

namespace apklayer {

bool inFlatpak() { return exists("/.flatpak-info"); }

std::string hostPath(const std::string& path) {
    if (!inFlatpak() || path.rfind("/app/", 0) != 0) return path;
    std::string app = property("/.flatpak-info", "app-path");  // the [Instance] key; no other section has it
    return app.empty() ? path : app + path.substr(4);
}

std::string hostXdg(const char* var, const std::string& fallback) {
    if (!inFlatpak()) return getenvOr(var, join(homeDir(), fallback));
    return getenvOr((std::string("HOST_") + var).c_str(), join(homeDir(), fallback));  // Flatpak sets HOST_* when the host has them set
}

std::string hostCli(const std::string& cli) {
    return inFlatpak() ? "flatpak run --command=apklayer " + getenvOr("FLATPAK_ID", "io.github.erfanoabdi.Apklayer") : cli;
}

std::string launcherPrefix() { return inFlatpak() ? getenvOr("FLATPAK_ID", "io.github.erfanoabdi.Apklayer") + "." : "apklayer-"; }

void shareRuntimeDir(const std::string& runtimeHome) {
    if (!inFlatpak()) return;
    const std::string shared = join(runtimeHome, "app/" + getenvOr("FLATPAK_ID", "io.github.erfanoabdi.Apklayer"));
    if (!isDir(shared)) return;
    for (const char* dir : {"apklayer", "apklayer-binder"}) makeDirs(join(shared, dir));
    for (const char* name : {"apklayer", "apklayer-binder", "apklayer-compositor-gpu", "apklayer-host-gles"})
        (void)!symlink(join(shared, name).c_str(), join(runtimeHome, name).c_str());  // already there: this sandbox linked it before
}

}  // namespace apklayer
