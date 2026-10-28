// Launching an app (or its headless component host) and the instance hub.
#pragma once

#include "apps.h"

namespace apklayer {

enum class RunMode { App, Component };
int runApp(const Layout& layout, const Instance& instance, const std::string& package, RunMode mode);
int runHub(const Layout& layout, const Instance& instance);

}  // namespace apklayer
