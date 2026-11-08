#include "flatpak.h"

#include "util.h"

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

}  // namespace apklayer
