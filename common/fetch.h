// Downloads with a SHA256 check, the Android runtime bundle's atomic unpack, the deb's install. Blocking: the UI calls these on worker threads.
#pragma once

#include <functional>
#include <string>

#include "proc.h"

namespace manager {

using Progress = std::function<void(double)>;  // 0..1, or -1 while the size is unknown

// $XDG_CACHE_HOME/apklayer/downloads; $XDG_DATA_HOME/apklayer/runtime (<version>/ per runtime, current -> the one in use)
std::string downloadsDir();
std::string runtimeRoot();
// url (http(s) through wget, file:// copied) to dest, written as dest.part and renamed when complete, then checked against sha256 (a mismatch
// removes it). Empty when it worked, else what failed.
std::string fetch(const std::string& url, const std::string& sha256, const std::string& dest, const Progress& progress);
// the same without a checksum: an index (releases.json, a repository's), whose own entries carry the checksums
std::string download(const std::string& url, const std::string& dest, const Progress& progress);
std::string sha256File(const std::string& path);
// the version current points at, "" when no runtime is installed
std::string installedRuntime(const std::string& root);
// bytes this user may still write on path's file system (statvfs: f_bavail * f_frsize), -1 when unknown; the tests put a fake here
extern long long (*freeBytes)(const std::string& path);
// "1.6 GB"
std::string sizeText(long long bytes);
// a bundle's unpacked size: its zstd frame's recorded content size (runtime-bundle.sh records it), else counted (one decompression); -1 unreadable
long long unpackedSize(const std::string& bundle);
// "" when root's file system has need bytes free. When only removing the runtimes other than the current one makes room (an update removes
// them anyway after it switched; current is never touched), they are removed and note says so. Else what is needed, what is free, and where.
std::string runtimeRoom(const std::string& root, long long need, std::string* note = nullptr);
// bundle (a .tar.zst with runtime.json, at its top or in one directory) into root/<version>: its room checked first (runtimeRoom), unpacked
// beside it, renamed into place, then current switched (a symlink renamed over the old one); the previous version is kept, older ones removed
// (dot names stay: .downloads, a bundle's download). Empty when it worked; note: what runtimeRoom removed.
std::string unpackRuntime(const std::string& bundle, const std::string& root, const std::string& version, std::string* note = nullptr);
// PackageKit (pkcon install-local, its InstallFiles) when installed, else pkexec apt-get install: both ask the desktop user through polkit
Result installDeb(const std::string& path);

}  // namespace manager
