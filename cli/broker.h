// apklayerd's file broker: a sandboxed app's uid is not the user's, so a file the user may open but the app's uid may not (a GPU node the seat's
// ACL or the video group opens, a file of the granted home that its "other" bits close) is opened here, as the user, and handed over as an fd;
// in the granted home also mkdir, unlink, rmdir, rename and access.
#pragma once

#include "layout.h"

#include <sys/socket.h>

namespace apklayer {

// A request of bridge/native/broker.c on an apklayerd connection (NUL separated): open <path> <flags> <mode> | mkdir <path> <mode> | unlink <path> |
// rmdir <path> | rename <from> <to> | access <path> <mode>
bool brokerRequest(const std::vector<std::string>& request);
// Answers it: "ok" (with the fd for open) or "err <errno>"
void brokerAnswer(const Layout& layout, int conn, const std::vector<std::string>& request, const ucred& peer, const std::string& instance);

}  // namespace apklayer
