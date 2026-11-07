// The instance's spare, zygote's role: a JVM that has built its sandbox and loaded the native runtime (~0.7 s of every process start) before the
// launch it becomes is known, held by `apklayer app-spare` and handed the next app launch (`apklayer run`) or component host start (`apklayer
// component-host`, the hub's) of the same runtime build and environment. One per instance, refilled once the process it became is up.
#pragma once

#include <string>
#include <vector>

#include "layout.h"

namespace apklayer {

// A launch through the instance's spare: the target (JVM options and files, one per line; the launch's own environment, kLaunchEnv, as
// -Dapklayer.env.<name>=<value>), this process's stdout and stderr and the launch's started pipe (startAppSpare) go to it. Returns the JVM's pid,
// *conn the connection its exit status comes on (awaitAppSpare); -1 when no spare of this key waits.
int takeAppSpare(const Instance& inst, const std::string& key, const std::vector<std::string>& target, const Env& env, int startedFd, int* conn);
// The spare JVM's exit status (128+signal when killed); SIGINT, SIGTERM and SIGHUP to this launcher are passed on to it meanwhile. The JVM
// outlives its holder: when the holder ends first, this waits for the JVM itself (its status is then unknown: 1).
int awaitAppSpare(int conn, int jvmPid);
// What a spare and a launch must share: the JVM's command line, its environment (but what is the launch's own), the runtime build.
// sandboxPackage: under the sandbox, the one app whose process a spare is (its uid and view are fixed before the JVM starts); "" otherwise.
std::string appSpareKey(const Layout& layout, const Instance& instance, const std::vector<std::string>& argv, const Env& env, const std::string& sandboxPackage);
// Starts `apklayer app-spare` detached; it ends at once when the instance has a spare already, else prepares one once a byte or EOF comes on afterFd
// (the launch's started pipe: AppSpare.launchStarted).
void startAppSpare(const Layout& layout, const Instance& instance, int afterFd, const std::string& sandboxPackage = "");
// `apklayer app-spare` (the hub at its start): a spare preparing now, also when the instance's holder still waits for its launch to be up.
void wakeAppSpare(const Layout& layout, const Instance& instance);
// The holder: starts the spare JVM, hands it the first launch that matches its key, then waits for it and tells that launch its exit status.
int runAppSpare(const Layout& layout, const Instance& instance, int afterFd, const std::string& sandboxPackage = "");

}  // namespace apklayer
