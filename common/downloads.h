// downloads.json: where the optional downloads come from (Google apps zip + sha256 per CPU, the microG and F-Droid indexes and their packages).
// The installed <share>/apklayer/downloads.json, with ~/.config/apklayer/downloads.json over it: a value is taken from the first file that has it.
#pragma once

#include <string>
#include <string_view>
#include <vector>

namespace manager {

class Downloads {
public:
    // base (required) and user (optional: ignored when missing); "" or an error naming the file that is not a JSON object
    std::string load(const std::string& base, const std::string& user);
    // the value at a path of keys ({"gapps", "arm64", "url"}), as the JSON text of it; empty when neither file has it
    std::string_view find(const std::vector<std::string>& path) const;
    std::string str(const std::vector<std::string>& path) const;
    std::vector<std::string> strings(const std::vector<std::string>& path) const;  // an array of strings
private:
    std::string texts_[2];  // user, base
};

// <XDG_CONFIG_HOME or ~/.config>/apklayer/downloads.json
std::string userDownloadsFile();

}  // namespace manager
