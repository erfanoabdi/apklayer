// apklayer's data (<data home>/apklayer: runtimes, apps, instances) on another file system, a symlink in its place: a phone whose home is on its
// small system partition (Sailfish OS on the Volla Quintus: 6.8 GB) keeps it on /userdata (docs/HALIUM.md "apklayer on Sailfish OS").
#pragma once

#include "layout.h"

namespace apklayer {

// /userdata/apklayer/<user>/share when /userdata/apklayer is writable (the Sailfish RPM's %post makes it) and another file system than the data
// home's; else ""
std::string bigDataDir(const Layout& layout);
// the data home's file system is a small one (below 16 GB: a phone's system partition) and bigDataDir has a place: the first setup moves there
bool smallDataHome(const Layout& layout);
// <data home>/apklayer copied into dir, then replaced by a symlink to it (no instance may run); "" when it worked, else what failed
std::string moveData(const Layout& layout, const std::string& dir);

}  // namespace apklayer
