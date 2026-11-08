// Running inside the Flatpak (flatpak/io.github.erfanoabdi.Apklayer.yml): what the host sees of the app's files and of the user's directories.
#pragma once

#include <string>

namespace apklayer {

bool inFlatpak();

// A path under /app as the host sees it (the app's deployed files, /.flatpak-info app-path): commands the user runs on the host name these
std::string hostPath(const std::string& path);

// The host's XDG directory (var: XDG_DATA_HOME, XDG_CONFIG_HOME; fallback under the home): Flatpak points the app's own at ~/.var/app/<id>
std::string hostXdg(const char* var, const std::string& fallback);

// How a launcher on the host runs the CLI: the CLI binary itself, or `flatpak run --command=apklayer <id>`
std::string hostCli(const std::string& cli);

// The names of the launchers, the submenu's merge file and directory entry this install writes at the host's XDG paths: "apklayer-", the
// Flatpak's "<app id>." (a deb beside it keeps its own; neither one's remove-all or uninstall touches the other's)
std::string launcherPrefix();

}  // namespace apklayer
