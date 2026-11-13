// The build.prop files of the device an instance presents, written for apps that read them as files (cli/build_props.cpp).
#pragma once

#include "layout.h"

namespace apklayer {

// /system, /vendor, /odm, /product, /system_ext build.prop and /default.prop in the instance's own android-root, from the live property store of the
// stack (aprop dump), so the files say what getprop and SystemProperties say. Call after the properties are loaded.
void writeBuildProps(const Layout& layout, const Instance& instance, const std::vector<std::pair<std::string, std::string>>& store);

// The store of the instance as (name, value) rows (aprop dump)
std::vector<std::pair<std::string, std::string>> dumpStore(const Layout& layout, const Instance& instance);

}  // namespace apklayer
