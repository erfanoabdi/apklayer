// An instance's Android services (servicemanager, HALs, SurfaceFlinger, media, audio) as host processes, supervised by a daemon.
#pragma once

#include "env.h"

namespace apklayer {

// Makes sure the instance's stack is up and ready (starts the daemon on demand). Returns false with a message on failure.
bool ensureStack(const Layout& layout, const Instance& instance);
// The daemon body (`apklayer stack-daemon <id>`): starts the services, signals readiness, ends as soon as one exits.
int runStackDaemon(const Layout& layout, const Instance& instance);
// Stops the instance's stack (its daemon stops its own services).
void stopStack(const Layout& layout, const Instance& instance);
// Starts the session's window shell when none listens on its socket.
void ensureShell(const Layout& layout, const NativeEnv& env);

}  // namespace apklayer
