// Launching an app (or its headless component host) and the instance hub.
#pragma once

#include "apps.h"

namespace apklayer {

// Component: a package's headless component host (the hub starts it); both kinds take the instance's spare JVM when one waits (cli/spare.cpp)
enum class RunMode { App, Component };
int runApp(const Layout& layout, const Instance& instance, const std::string& package, RunMode mode);
// A host JVM's command line up to apklayer.host.Main without the launch's own options (package, window, runms; sets env as hostJvmArgs): what the spare starts with.
std::vector<std::string> windowJvmArgs(const Layout& layout, const Instance& instance, NativeEnv& env);
int waitHub(const Layout& layout, const Instance& instance);
int runHub(const Layout& layout, const Instance& instance);
// PNG of what SurfaceFlinger shows on an app window (by package or layer stack); 0 when the file was written.
int screenshot(const Layout& layout, const Instance& instance, const std::string& target, std::string out);

}  // namespace apklayer
