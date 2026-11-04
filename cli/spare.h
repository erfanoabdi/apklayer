// The app spare, zygote's role for window JVMs: a JVM that has built its sandbox and loaded the native runtime (~0.7 s of every app start) before
// the launch it becomes is known, held by `apklayer app-spare` and handed the next `apklayer run` of the same runtime build and environment.
#pragma once

#include <string>
#include <vector>

#include "layout.h"

namespace apklayer {

// A launch through the instance's spare: the target (JVM options and files, one per line), this process's stdout and stderr and the launch's started
// pipe (startAppSpare) go to it. Returns the JVM's pid, *conn the connection its exit status comes on (awaitAppSpare); -1 when no spare of this key waits.
int takeAppSpare(const Instance& inst, const std::string& key, const std::vector<std::string>& target, int startedFd, int* conn);
// The spare JVM's exit status (128+signal when killed); SIGINT, SIGTERM and SIGHUP to this launcher are passed on to it meanwhile.
int awaitAppSpare(int conn, int jvmPid);
// What a spare and a launch must share: the JVM's command line, its environment (but the launch's own JOPTS and activation token), the runtime build.
std::string appSpareKey(const Layout& layout, const Instance& instance, const std::vector<std::string>& argv, const Env& env);
// Starts `apklayer app-spare` detached; it ends at once when the instance has a spare already, else prepares one once a byte or EOF comes on afterFd
// (the launch's started pipe: AppSpare.launchStarted).
void startAppSpare(const Layout& layout, const Instance& instance, int afterFd);
// The holder: starts the spare JVM, hands it the first launch that matches its key, then waits for it and tells that launch its exit status.
int runAppSpare(const Layout& layout, const Instance& instance, int afterFd);

}  // namespace apklayer
