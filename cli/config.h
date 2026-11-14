// The user's settings, in one place: ~/.config/apklayer/config (lines `key=value`, or `<package>:key=value` for one app), written by
// `apklayer config` and the manager's Settings page, read by every apklayer command. The table in config.cpp is the only list of them (the CLI
// and the manager's page both take it from `config list`); a setting is handed to the runtime as the environment variable it names.
// Debugging switches stay plain environment variables (docs/ENV-VARS.md); a variable already set in the environment wins over the file.
#pragma once

#include <string>
#include <vector>

namespace apklayer {

// Exports the global settings (variables already in the environment stay), and with a package its own on top of them.
void applySettings(const std::string& package = "");

// config list|get|set|unset [--app <package>]
int configCommand(const std::vector<std::string>& args);

}  // namespace apklayer
