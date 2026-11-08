// apklayerd's file broker: a sandboxed app's uid is not the user's, so a file the user may open but the app's uid may not (a GPU node the seat's
// ACL or the video group opens, a file of the granted home that its "other" bits close) is opened here, as the user, and handed over as an fd.
#pragma once

#include "layout.h"

#include <sys/socket.h>

namespace apklayer {

// One request of bridge/native/broker.c on an apklayerd connection: "open\0<path>\0<flags>\0<mode>\0"; answers "ok" with the fd or "err <errno>"
void brokerOpen(const Layout& layout, int conn, const std::vector<std::string>& request, const ucred& peer, const std::string& instance);

}  // namespace apklayer
