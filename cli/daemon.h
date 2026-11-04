// apklayerd (`apklayer daemon`, tools/host-setup/apklayerd.service): the user's one daemon. While it runs, every CLI command (the manager's too) runs as
// its child, so the instances' stacks, hubs and app processes are its descendants; the sandbox's apps reach the user's side only through it.
#pragma once

#include "layout.h"

namespace apklayer {

std::string daemonSocket(const Layout& layout);
// The daemon: serves until stopped; onDemand (started by a sandboxed command): ends once nothing it started runs any more.
int runDaemon(const Layout& layout, bool onDemand);
// The client side: runs args (the command line after the program name) in the daemon and returns true with its exit status; false: no daemon,
// run it here. With the sandbox on, a missing daemon is started first.
bool forwardToDaemon(const Layout& layout, const std::vector<std::string>& args, int* status);

}  // namespace apklayer
