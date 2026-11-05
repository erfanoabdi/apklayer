// `apklayer setup [--check] [--update] [--bundle <file|file://|URL>] [--microg] [--fdroid]` (cli/setup.cpp)
#pragma once

#include "layout.h"

namespace apklayer {

int setupCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);

}  // namespace apklayer
