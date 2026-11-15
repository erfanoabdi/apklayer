// `apklayer open <package> [file]`: what a desktop's "open with" runs (the app's .desktop entry has Exec=... open <package> %f when it opens files)
#pragma once

#include "layout.h"

namespace apklayer {

// args: the package, then the host file to hand the app as ACTION_VIEW data (none: a plain launch, as `run`)
int openCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);

}  // namespace apklayer
