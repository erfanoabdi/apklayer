// What an update needs to know: releases.json (the pages branch, written by the release workflow), this host's OS codename and CPU, version
// order, and the APK an F-Droid repository index offers for a package. No UI and no downloads here.
#pragma once

#include <string>
#include <vector>

namespace manager {

struct Asset {
    std::string url, sha256;
    bool found() const { return !url.empty(); }
};

struct Release {
    std::string version, date, notesUrl, runtimeVersion;
    int converterAbi = 0, dataFormat = 0;
    Asset deb, runtime;  // for this host's codename and CPU (empty when the release has none)
};

// $APKLAYER_RELEASES_URL, else the project's page
std::string releasesUrl();
// VERSION_CODENAME of os-release (the file is a parameter for the tests)
std::string osCodename(const std::string& osRelease = "/etc/os-release");
// this CPU's names in release keys: Debian's first (arm64, amd64), then the kernel's (aarch64, x86_64) when they differ
std::vector<std::string> cpuNames();
// Android's name of this CPU (arm64-v8a, x86_64)
std::string androidAbi();
// releases.json's latest release (or the newest of its list), with the deb and runtime of codename/cpus; error says what is missing
Release parseLatest(const std::string& json, const std::string& codename, const std::vector<std::string>& cpus, std::string& error);
// -1, 0, 1 as a sorts before, with, after b: dotted numbers ("0.10" after "0.9"), and a version with a suffix ("0.2.0-rc1") before the same without
int compareVersions(const std::string& a, const std::string& b);

// The APK of package in an F-Droid repository index (index-v1.json): the suggested version, else the newest, for this ABI (arm64-v8a...).
struct IndexApk {
    std::string url, sha256;
    long long versionCode = 0;
};
IndexApk pickApk(const std::string& indexJson, const std::string& package, const std::string& abi, std::string& error);

}  // namespace manager
