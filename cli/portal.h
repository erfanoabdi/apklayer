// `apklayer portal pick|open`: the desktop's file dialog and link opener on the user's side, for a sandboxed app (through apklayerd), which can
// reach neither the session bus nor the user's files: the picked file or folder is made visible in the asking app's view.
#pragma once

#include "layout.h"

namespace apklayer {

int portalCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);

}  // namespace apklayer
