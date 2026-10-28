// Where things are: the install root, the staged native runtime, per-user data, and the per-instance state and runtime directories.
#pragma once

#include <string>

#include "util.h"

namespace apklayer {

struct Layout {
    std::string root;         // contains bridge/, scripts/, aosp/, runtime/ (the repo, or the installed share directory)
    std::string build;        // Meson build directory: shell, converter, host HAL libraries
    std::string native;       // the staged native runtime (native-runtime.jar, aosp/, android-root/, bin/btload...)
    std::string androidLibs;  // the device build's libraries
    std::string bionic;       // bionic_translation's build directory
    std::string deps;         // pinned Java dependencies
    std::string dataHome;     // ~/.local/share
    std::string appsDir;      // <dataHome>/apklayer/apps/<package>: code, converted classes, info.properties
    std::string stateHome;    // ~/.local/state
    std::string runtimeHome;  // $XDG_RUNTIME_DIR

    static Layout detect();
    std::string btload() const { return join(native, "bin/btload"); }
    std::string hostClasses() const { return join(native, "aosp/host-classes"); }
    std::string frameworkDir() const { return join(native, "aosp/android-all"); }
    std::string dex2jvmJar() const { return join(build, "runtime/dex2jvm/dex2jvm.jar"); }
    std::string shellBinary() const { return join(build, "bridge/shell/apklayer-shell"); }
    std::string shellSocket() const { return join(runtimeHome, "apklayer/shell.sock"); }
    std::string cliBinary() const;  // this tool, for the Java side (-Dapklayer.cli)
};

struct Instance {
    std::string id;
    std::string stateDir;    // logs, pid and ready markers, locks: on disk, never in tmpfs
    std::string runtimeDir;  // properties store and sockets of this instance
    static Instance open(const Layout& layout, const std::string& id);
    std::string logs() const { return stateDir; }
    std::string pidFile() const { return join(stateDir, "stack.pid"); }
};

}  // namespace apklayer
