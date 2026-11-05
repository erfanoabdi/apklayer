// The manager's only way to apklayer: its command line tool (cli/), whose managing commands print tab-separated rows. No UI code here.
#pragma once

#include <functional>
#include <string>
#include <vector>

#include "proc.h"

namespace manager {

class Cli {
public:
    explicit Cli(std::string exe) : exe_(std::move(exe)) {}
    // $APKLAYER_CLI, else bin/apklayer of the tree this program was built in (walking up from it), else `apklayer` from PATH
    static std::string locate();
    const std::string& exe() const { return exe_; }

    std::string instance = "default";
    // `apklayer --instance <instance> args...` to completion
    Result run(const std::vector<std::string>& args) const;
    Rows rows(const std::vector<std::string>& args) const { return parseRows(run(args).out); }
    // the same, each output line (stdout and stderr) to onLine as it comes (the first run's `setup` progress); returns the exit status
    int runLines(const std::vector<std::string>& args, const std::function<void(const std::string&)>& onLine) const;
    // `apklayer run <package>` in its own session, outliving the manager: each output line to onLine (from a reader thread; apklayer's
    // start-up trace is on, so "app jvm spawned" marks the app's start), then onExit with the status
    void launch(const std::string& package, std::function<void(const std::string&)> onLine, std::function<void(int)> onExit) const;

private:
    std::string exe_;
};

// the path the portal's file chooser (bridge/portal/file-chooser.py open) printed, "" when none was chosen
std::string chosenFile(const Result& chooser);
// `providers set <domain> <backend> key=value...` (options with an empty value left out: the backend's default), `providers unset <domain>` for backend ""
std::vector<std::string> providerArgs(const std::string& domain, const std::string& backend, const std::vector<std::pair<std::string, std::string>>& options);

}  // namespace manager
