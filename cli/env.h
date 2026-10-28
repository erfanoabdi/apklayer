// The environment of every process that runs Android's own libraries (services, app JVMs, helpers): what scripts/native-env.sh did.
#pragma once

#include <string>
#include <vector>

#include "layout.h"

namespace apklayer {

struct NativeEnv {
    Env env;           // complete environment for a child
    std::string java;  // the arm64 JVM
};

// The JVM command line up to (not including) the app's own options: java, tuning flags, jvm.args, runtime properties. Also sets ANDROID_ROOT & co in env.
std::vector<std::string> hostJvmArgs(const Layout& layout, const Instance& instance, NativeEnv& env);

NativeEnv nativeEnv(const Layout& layout, const Instance& instance);

}  // namespace apklayer
