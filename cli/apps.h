// Installed apps: conversion, description, desktop entries, uninstall, list.
#pragma once

#include "stack.h"

namespace apklayer {

// Converts (cached), describes and installs; returns the package name.
std::string installApp(const Layout& layout, const Instance& instance, const std::string& apk);
void listApps(const Layout& layout);
void uninstallApp(const Layout& layout, const std::string& package);
std::string appDir(const Layout& layout, const std::string& package);
void ensureUid(const Layout& layout, const std::string& dir);
// The classpath of a JVM running the host: the staged runtime, host classes, the tree's Robolectric, then the converter.
std::string hostClasspath(const Layout& layout, const std::string& appClasses);

}  // namespace apklayer
