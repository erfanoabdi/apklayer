// `apklayer setup --gapps` (cli/gapps.cpp): Google's own Play services, Services Framework, Play Store and sync adapters as system apps, in place of microG.
#pragma once

#include <functional>

#include "fetch.h"
#include "layout.h"

namespace apklayer {

// Installs (or updates) the LiteGapps build of this CPU; report names what it does. "" when it worked, else what failed.
std::string installGapps(const Layout& layout, const Instance& instance, const std::function<void(const std::string&)>& report, const manager::Progress& progress);
// Google's permission allowlists and sysconfig, kept in <data>/apklayer/google-apps/etc, into the runtime's android-root (again after a runtime update)
void applyGappsConfig(const Layout& layout);

}  // namespace apklayer
