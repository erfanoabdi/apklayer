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
// archive: the name of this JVM's class data sharing archive in the instance's cds/ directory (one per command line: an app, its component host, the hub), empty for none.
std::vector<std::string> hostJvmArgs(const Layout& layout, const Instance& instance, NativeEnv& env, const std::string& archive = "");

NativeEnv nativeEnv(const Layout& layout, const Instance& instance);

}  // namespace apklayer
