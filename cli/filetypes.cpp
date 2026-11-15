#include "filetypes.h"

#include <algorithm>

#include "flatpak.h"
#include "layout.h"
#include "util.h"

namespace apklayer {

namespace {
// A wildcard family is claimed only by a genuine viewer, and the desktops match concrete types: the families' common members
const std::vector<std::pair<std::string, std::vector<std::string>>> kFamilies = {
    {"image/*", {"image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp", "image/svg+xml", "image/tiff", "image/avif", "image/heif"}},
    {"video/*", {"video/mp4", "video/x-matroska", "video/webm", "video/ogg", "video/mpeg", "video/quicktime", "video/x-msvideo", "video/3gpp"}},
    {"audio/*", {"audio/mpeg", "audio/ogg", "audio/flac", "audio/x-wav", "audio/mp4", "audio/x-m4a", "audio/aac", "audio/opus", "audio/x-vorbis+ogg"}},
    {"text/*", {"text/plain", "text/markdown", "text/csv", "text/html", "text/xml"}},
};

// the system's own shared-mime-info data (not the user's: that holds the packages this file's output makes)
std::vector<std::string> systemDirs() {
    std::vector<std::string> dirs;
    for (const std::string& d : split(getenvOr("XDG_DATA_DIRS", "/usr/local/share:/usr/share"), ':')) if (!d.empty()) dirs.push_back(d);
    return dirs;
}

// a type the system defines, or the user's own packages other than apklayer's
bool known(const std::string& mime) {
    for (const std::string& d : systemDirs()) if (exists(join(d, "mime/" + mime + ".xml"))) return true;
    std::string packages = join(hostXdg("XDG_DATA_HOME", ".local/share"), "mime/packages");
    for (const std::string& f : listDir(packages))
        if (f.rfind("apklayer-", 0) != 0 && readFile(join(packages, f)).value_or("").find("type=\"" + mime + "\"") != std::string::npos) return true;
    return false;
}

// the type the system's globs give "*.ext", "" when none
std::string typeOfExtension(const std::string& ext) {
    for (const std::string& d : systemDirs())
        for (const std::string& line : split(readFile(join(d, "mime/globs2")).value_or(""), '\n')) {
            std::vector<std::string> f = split(line, ':');  // weight:type:glob
            if (f.size() >= 3 && f[2] == "*." + ext) return f[1];
        }
    return "";
}

// Android-internal types (vnd.android.document/root, DocumentsUI's) have no registered top-level: update-desktop-database rejects the entry's line
bool validTop(const std::string& mime) {
    static const char* tops[] = {"application", "audio", "font", "image", "inode", "message", "model", "multipart", "text", "video"};
    std::string top = mime.substr(0, mime.find('/'));
    return std::find_if(std::begin(tops), std::end(tops), [&](const char* t) { return top == t; }) != std::end(tops);
}

std::string escape(const std::string& s) {
    std::string out;
    for (char c : s) out += c == '&' ? "&amp;" : c == '<' ? "&lt;" : c == '>' ? "&gt;" : c == '"' ? "&quot;" : std::string(1, c);
    return out;
}

void add(std::vector<std::string>& v, const std::string& s) { if (std::find(v.begin(), v.end(), s) == v.end()) v.push_back(s); }
}  // namespace

FileTypes fileTypes(const std::string& info, const std::string& package, const std::string& label) {
    FileTypes out;
    std::vector<std::pair<std::string, std::string>> defined;  // type, glob: the ones the system lacks
    for (const std::string& line : split(readFile(info).value_or(""), '\n')) {
        if (line.rfind("open.", 0) != 0 || line.find('=') == std::string::npos) continue;
        std::vector<std::string> f = split(line.substr(line.find('=') + 1), ';');  // mimeTypes,... ; extensions,...
        std::vector<std::string> mimes = split(f.empty() ? "" : f[0], ','), exts = split(f.size() > 1 ? f[1] : "", ',');
        std::string concrete;
        for (const std::string& m : mimes) {
            if (m.empty() || m == "*/*" || m == "*") continue;  // every file: an app that claims all of them is a viewer of none
            bool family = false;
            for (const auto& [wild, members] : kFamilies) if (m == wild) { family = true; for (const std::string& t : members) add(out.mimes, t); }
            if (family || m.find('*') != std::string::npos || m.find('/') == std::string::npos || !validTop(m)) continue;
            add(out.mimes, m);
            if (concrete.empty()) concrete = m;
        }
        for (const std::string& ext : exts) {
            if (ext.empty()) continue;
            std::string type = typeOfExtension(ext);
            if (type.empty()) {
                type = !concrete.empty() && !known(concrete) ? concrete : "application/x-apklayer-" + ext;
                defined.emplace_back(type, "*." + ext);
            }
            add(out.mimes, type);
        }
    }
    std::string types;
    for (const auto& [type, glob] : defined)
        types += "  <mime-type type=\"" + escape(type) + "\">\n    <comment>" + escape(label) + " file (" + escape(glob) + ")</comment>\n    <glob pattern=\"" + escape(glob) + "\"/>\n  </mime-type>\n";
    if (!types.empty())
        out.xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- apklayer: the file types of " + escape(package) + " -->\n<mime-info xmlns=\"http://www.freedesktop.org/standards/shared-mime-info\">\n" + types + "</mime-info>\n";
    return out;
}

}  // namespace apklayer
