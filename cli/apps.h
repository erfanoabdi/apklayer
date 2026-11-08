// Installed apps: conversion, description, desktop entries, uninstall, list.
#pragma once

#include "stack.h"

namespace apklayer {

// Converts (cached), describes and installs one package (an APK, a base APK and its splits, or an .apks/.xapk/.apkm); returns the package name.
// An update keeps the app's data and its system mark (system: mark it a system app, FLAG_SYSTEM); another signer exits kInstallIncompatible, a lower versionCode kInstallDowngrade unless allowDowngrade, an unsigned APK kInstallUnsigned, a required <uses-library> the build does not know kInstallMissingLibrary, native libraries of no runnable ABI kInstallNoMatchingAbis.
inline constexpr int kInstallIncompatible = 3, kInstallDowngrade = 4, kInstallUnsigned = 5, kInstallMissingLibrary = 6, kInstallNoMatchingAbis = 7;
// uninstall: a provider of a static or SDK library an installed app uses exits kUninstallUsedLibrary (DELETE_FAILED_USED_SHARED_LIBRARY)
inline constexpr int kUninstallUsedLibrary = 8;
std::string installApp(const Layout& layout, const Instance& instance, const std::vector<std::string>& apks, bool allowDowngrade = false, bool system = false);
// `reconvert <package> | --all-stale`: install again from the kept base.apk and splits (data, uid, system mark kept); a failing app keeps its old
// install and the others still convert (exit status 1 names the failed ones)
int reconvert(const Layout& layout, const Instance& instance, const std::string& target);
// converted with an older converter ABI (cli/version.h) than this apklayer's
bool isStale(const std::string& info);
void listApps(const Layout& layout);
void uninstallApp(const Layout& layout, const std::string& package);
std::string appDir(const Layout& layout, const std::string& package);
// The app's own files in the instance (moved there from the shared app directory for the default instance).
std::string appDataDir(const Layout& layout, const Instance& instance, const std::string& package);
// `ime enable|disable <package or ime id>`: the user's input method choice, applied by the hub at the instance's next start.
int inputMethod(const Layout& layout, const Instance& instance, const std::string& action, const std::string& name);
void ensureUid(const Layout& layout, const std::string& dir);
// The classpath of a JVM running the host: the staged runtime, host classes, the tree's Robolectric, then the converter.
std::string hostClasspath(const Layout& layout);

}  // namespace apklayer
