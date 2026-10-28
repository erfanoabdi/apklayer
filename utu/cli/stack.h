// An instance's Android services (servicemanager, HALs, SurfaceFlinger, media, audio) as host processes, supervised by a daemon.
#pragma once

#include "env.h"

namespace apklayer {

// Makes sure the instance's stack is up and ready (starts the daemon on demand). Returns false with a message on failure.
bool ensureStack(const Layout& layout, const Instance& instance);
// The daemon body (`apklayer stack-daemon <id>`): starts the services, signals readiness, ends as soon as one exits.
// With `tool` (a staged library and its arguments) the daemon also runs that tool, as the test harness does: it ends when the tool does.
int runStackDaemon(const Layout& layout, const Instance& instance, const std::vector<std::string>& tool = {});
// Runs one staged library (btload ... --main args) in the instance's environment, in the foreground.
int execTool(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);
// Stops the instance's stack (its daemon stops its own services).
void stopStack(const Layout& layout, const Instance& instance);
// The instance's property store (Android's readiness mechanism): set a property, or block until it has a value (false if deathPidfd became readable first).
void setProperty(const Layout& layout, const NativeEnv& env, const char* name, const char* value);
bool waitForProperty(const Layout& layout, const NativeEnv& env, const char* name, const char* value, int deathPidfd);
// Starts the session's window shell when none listens on its socket.
void ensureShell(const Layout& layout, const Instance& instance, const NativeEnv& env);

}  // namespace apklayer
