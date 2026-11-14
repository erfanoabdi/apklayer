// The Settings page and the app's own settings: the rows of `apklayer config list [--app PKG]` (cli/config.cpp holds the only table of them).
#pragma once

#include <memory>
#include <string>
#include <vector>

#include "app.h"
#include "proc.h"

namespace manager {

// rows: key, kind, default, value, choices (comma separated), title, help, per-app, own
std::shared_ptr<slint::VectorModel<ConfigEntry>> configModel(const Rows& rows);
// `config set|unset` of a key (value "": unset), for one app when package is not empty
std::vector<std::string> configArgs(const std::string& package, const std::string& key, const std::string& value);

}  // namespace manager
