// `apklayer setup --gapps` (cli/gapps.cpp): Google's own Play services, Services Framework, Play Store and sync adapters as system apps, in place of microG.
#pragma once

#include <functional>

#include "downloads.h"
#include "fetch.h"
#include "layout.h"

namespace apklayer {

// The download sources (docs/DOWNLOADS.md): <root>/downloads.json (data/ in a source tree) with the user's file over it. "" or the error naming the file.
std::string loadDownloads(const Layout& layout, manager::Downloads& d);

// Installs (or updates) the LiteGapps build of this CPU; report names what it does. "" when it worked, else what failed.
std::string installGapps(const Layout& layout, const Instance& instance, const std::function<void(const std::string&)>& report, const manager::Progress& progress);
// Google's apps installed (the Services Framework is theirs alone; microG has none)
bool gappsInstalled(const Layout& layout);
// Stages microG's repository APKs, replaces the confirmed Google counterparts, then removes Google-only packages.
std::string installMicrog(const Layout& layout, const Instance& instance, const std::string& indexUrl,
                          const std::vector<std::string>& packages, const std::function<void(const std::string&)>& report,
                          const manager::Progress& progress);
// <data>/apklayer/google-apps/etc: Google's permission allowlists, sysconfig and default grants, where `setup --gapps` leaves them. The readers are
// pointed at them there (APKLAYER_EXTRA_ETC in cli/env.cpp, apklayer.host.HostSystemConfig, cli/manage.cpp) rather than a copy being made into the
// runtime's android-root, which a package build serves read-only. "" unless Google's apps are installed, so a removal takes effect at once.
std::string gappsEtcDir(const Layout& layout);

}  // namespace apklayer
