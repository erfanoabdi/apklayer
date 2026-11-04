// The launch's log file, `apklayer logs` and `apklayer report` (a bug report's archive).
#pragma once
#include <string>
#include <vector>

#include "layout.h"

namespace apklayer {
// <logs>/app-<package>.log: the file the launch's output also goes to (the previous launch's is kept as .1).
std::string appLogPath(const Instance& inst, const std::string& package);
// From here on this process's stdout and stderr, and those of every child it starts, also go to that file.
void teeLaunchOutput(const Instance& inst, const std::string& package);
// logs [<package>] [--previous]: the app's last launch log; without a package, the instance's log files.
int logsCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args);
// report <package> [out.tar.gz]: versions, host, the app's info and logs and the instance's service logs in one archive.
int reportCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args);
}  // namespace apklayer
