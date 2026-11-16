#include "downloads.h"

#include <cstdlib>
#include <fstream>
#include <sstream>

#include "json.h"

namespace manager {

namespace {
bool read(const std::string& file, std::string& out) {
    std::ifstream in(file, std::ios::binary);
    if (!in) return false;
    std::stringstream ss;
    ss << in.rdbuf();
    out = ss.str();
    return true;
}
}  // namespace

std::string userDownloadsFile() {
    const char* xdg = getenv("XDG_CONFIG_HOME");
    const char* home = getenv("HOME");
    return (xdg && *xdg ? std::string(xdg) : std::string(home ? home : "") + "/.config") + "/apklayer/downloads.json";
}

std::string Downloads::load(const std::string& base, const std::string& user) {
    texts_[0].clear();
    texts_[1].clear();
    if (!read(base, texts_[1])) return "Cannot read " + base + " (the download sources of apklayer)";
    if (json::members(texts_[1]).empty()) return base + " is not a JSON object with download sources";
    if (!user.empty() && read(user, texts_[0]) && json::members(texts_[0]).empty()) {
        texts_[0].clear();
        return user + " is not a JSON object (fix it or remove it)";
    }
    return "";
}

std::string_view Downloads::find(const std::vector<std::string>& path) const {
    for (const std::string& text : texts_) {
        std::string_view v = text;
        for (const std::string& key : path) v = json::member(v, key);
        if (!v.empty()) return v;
    }
    return {};
}

std::string Downloads::str(const std::vector<std::string>& path) const { return json::str(find(path)); }

std::vector<std::string> Downloads::strings(const std::vector<std::string>& path) const {
    std::vector<std::string> out;
    for (std::string_view e : json::elements(find(path))) out.push_back(json::str(e));
    return out;
}

}  // namespace manager
