// `apklayer remove-all [--yes]`: everything apklayer keeps for the current user (every instance's apps and data, the runtime bundles, logs,
// caches, desktop entries, the Apklayer submenu, systemd user units). The package itself is apt's (`sudo apt purge apklayer`).
#pragma once
#include <string>
#include <vector>

#include "layout.h"

namespace apklayer {
int removeAllCommand(const Layout& layout, const std::vector<std::string>& args);
}  // namespace apklayer
