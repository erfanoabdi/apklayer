// `apklayer setup --gapps` (cli/gapps.cpp): Google's own Play services, Services Framework, Play Store and sync adapters as system apps, in place of microG.
#pragma once

#include <functional>

#include "fetch.h"
#include "layout.h"

namespace apklayer {

// Installs (or updates) the LiteGapps build of this CPU; report names what it does. "" when it worked, else what failed.
std::string installGapps(const Layout& layout, const Instance& instance, const std::function<void(const std::string&)>& report, const manager::Progress& progress);
// Google's apps installed (the Services Framework is theirs alone; microG has none)
bool gappsInstalled(const Layout& layout);
// Uninstalls Google's apps (with their data) and takes their allowlists out of the runtime, before microG goes in (`setup --microg`). "" or what failed.
std::string removeGapps(const Layout& layout, const Instance& instance, const std::function<void(const std::string&)>& report);
// Google's permission allowlists and sysconfig, kept in <data>/apklayer/google-apps/etc, into the runtime's android-root (again after a runtime update)
void applyGappsConfig(const Layout& layout);

}  // namespace apklayer
