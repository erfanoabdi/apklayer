// `apklayer setup --gapps` (cli/gapps.cpp): Google's own Play services, Services Framework, Play Store and sync adapters as system apps, in place of microG.
#pragma once

#include <functional>

#include "fetch.h"
#include "layout.h"

namespace apklayer {

// Installs (or updates) the LiteGapps build of this CPU; report names what it does. "" when it worked, else what failed.
std::string installGapps(const Layout& layout, const Instance& instance, const std::function<void(const std::string&)>& report, const manager::Progress& progress);
// <data>/apklayer/google-apps/etc: Google's permission allowlists, sysconfig and default grants, where `setup --gapps` leaves them. The readers are
// pointed at them there (APKLAYER_EXTRA_ETC in cli/env.cpp, apklayer.host.HostSystemConfig, cli/manage.cpp) rather than a copy being made into the
// runtime's android-root, which a package build serves read-only. "" unless Google's own apps are installed: the Services Framework is theirs alone
// (microG ships none), so a removal stops the allowlists applying at once.
std::string gappsEtcDir(const Layout& layout);

}  // namespace apklayer
