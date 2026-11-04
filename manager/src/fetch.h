// Downloads with a SHA256 check, the Android runtime bundle's atomic unpack, the deb's install. Blocking: the UI calls these on worker threads.
#pragma once

#include <functional>
#include <string>

#include "backend.h"

namespace manager {

using Progress = std::function<void(double)>;  // 0..1, or -1 while the size is unknown

// $XDG_CACHE_HOME/apklayer/downloads; $XDG_DATA_HOME/apklayer/runtime (<version>/ per runtime, current -> the one in use)
std::string downloadsDir();
std::string runtimeRoot();
// url (http(s) through wget, file:// copied) to dest, written as dest.part and renamed when complete, then checked against sha256 (a mismatch
// removes it). Empty when it worked, else what failed.
std::string fetch(const std::string& url, const std::string& sha256, const std::string& dest, const Progress& progress);
std::string sha256File(const std::string& path);
// the version current points at, "" when no runtime is installed
std::string installedRuntime(const std::string& root);
// bundle (a .tar.zst with runtime.json, at its top or in one directory) into root/<version>: unpacked beside it, renamed into place, then
// current switched (a symlink renamed over the old one); the previous version is kept, older ones removed. Empty when it worked.
std::string unpackRuntime(const std::string& bundle, const std::string& root, const std::string& version);
// PackageKit (pkcon install-local, its InstallFiles) when installed, else pkexec apt-get install: both ask the desktop user through polkit
Result installDeb(const std::string& path);

}  // namespace manager
