// Launching an app (or its headless component host) and the instance hub.
#pragma once

#include "apps.h"

namespace apklayer {

// Spare: a component host JVM before it has a package (the hub hands it one: HubSpare); package is empty then
enum class RunMode { App, Component, Spare };
int runApp(const Layout& layout, const Instance& instance, const std::string& package, RunMode mode);
int printPackageArgs(const Layout& layout, const Instance& instance, const std::string& package);
int waitHub(const Layout& layout, const Instance& instance);
int runHub(const Layout& layout, const Instance& instance);
// PNG of what SurfaceFlinger shows on an app window (by package or layer stack); 0 when the file was written.
int screenshot(const Layout& layout, const Instance& instance, const std::string& target, std::string out);

}  // namespace apklayer
